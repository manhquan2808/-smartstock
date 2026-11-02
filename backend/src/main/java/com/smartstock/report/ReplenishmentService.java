package com.smartstock.report;

import com.smartstock.catalog.Product;
import com.smartstock.catalog.ProductRepository;
import com.smartstock.common.NotFoundException;
import com.smartstock.inventory.Inventory;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.inventory.StockTransactionRepository;
import com.smartstock.inventory.projection.ProductQuantityRow;
import com.smartstock.report.ReportDtos.DailyQuantityItem;
import com.smartstock.report.ReportDtos.IssueTrendResponse;
import com.smartstock.report.ReportDtos.ReplenishmentResponse;
import com.smartstock.report.ReportDtos.ReplenishmentRowResponse;
import com.smartstock.security.WarehouseAccessGuard;
import com.smartstock.setting.SettingKey;
import com.smartstock.setting.SettingService;
import com.smartstock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DỰ BÁO NHU CẦU NHẬP HÀNG - trang "Gợi ý nhập hàng".
 *
 * <p><b>Bài toán:</b> người quản lý cần biết mặt hàng nào sắp hết và nên nhập bao nhiêu,
 * thay vì chờ tới khi hết hàng mới đặt.
 *
 * <p><b>Công thức</b> (áp dụng cho từng sản phẩm tại từng kho):
 *
 * <pre>
 *   (1) Lượng xuất trung bình/ngày = tổng xuất trong N ngày gần nhất / N
 *       N cấu hình được: 30 / 60 / 90 ngày.
 *
 *   (2) Số ngày còn đủ hàng = tồn hiện tại / lượng xuất trung bình mỗi ngày
 *       Nếu chưa từng xuất thì không dự báo được (hiển thị dấu gạch).
 *
 *   (3) Số lượng nên nhập = trung bình/ngày x (thời gian chờ hàng + số ngày dự trữ)
 *                           + tồn tối thiểu - tồn hiện tại
 *       Làm tròn LÊN và không bao giờ âm.
 * </pre>
 *
 * <p><b>Ý nghĩa công thức (3):</b> lượng nhập phải đủ bán trong thời gian chờ hàng về
 * (lead time) cộng thêm một khoảng dự trữ an toàn, đồng thời bù cho mức tồn tối thiểu
 * mà doanh nghiệp muốn luôn giữ trong kho. Trừ đi tồn hiện tại vì phần đó đã có sẵn.
 *
 * <p>Ví dụ: bán trung bình 10 thùng/ngày, chờ hàng 7 ngày, dự trữ 14 ngày, tồn tối thiểu
 * 20, tồn hiện tại 50 thì nên nhập: 10 x (7 + 14) + 20 - 50 = 180 thùng.
 */
@Service
@RequiredArgsConstructor
public class ReplenishmentService {

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;
    private final WarehouseAccessGuard warehouseAccessGuard;
    private final SettingService settingService;

    /**
     * Danh sách gợi ý nhập hàng cho một kho.
     *
     * @param warehouseId kho cần xem (bắt buộc, vì nhu cầu mỗi kho khác nhau)
     * @param windowDays số ngày lấy lịch sử xuất; null thì lấy theo cấu hình (mặc định 30)
     */
    @Transactional(readOnly = true)
    public ReplenishmentResponse suggest(Long warehouseId, Integer windowDays) {
        Warehouse warehouse = warehouseAccessGuard.requireAccessibleWarehouse(warehouseId);

        int window = windowDays != null && windowDays > 0
                ? windowDays
                : settingService.getInt(SettingKey.FORECAST_WINDOW_DAYS, 30);
        int leadTimeDays = settingService.getInt(SettingKey.LEAD_TIME_DAYS, 7);
        int safetyStockDays = settingService.getInt(SettingKey.SAFETY_STOCK_DAYS, 14);

        LocalDateTime from = LocalDate.now().minusDays(window).atStartOfDay();

        // Một truy vấn duy nhất lấy tổng lượng xuất của mọi sản phẩm trong kỳ
        Map<Long, BigDecimal> issuedByProduct = new HashMap<>();
        for (ProductQuantityRow row : stockTransactionRepository.sumIssuedQuantityByProduct(warehouseId, from)) {
            issuedByProduct.put(row.productId(), row.quantity());
        }

        // Lấy toàn bộ dòng tồn của kho (kèm sản phẩm) để tính cho từng mặt hàng
        List<Inventory> inventories = inventoryRepository.findByWarehouseForStocktake(warehouseId, null);

        BigDecimal windowDivisor = BigDecimal.valueOf(window);
        BigDecimal coverageDays = BigDecimal.valueOf(leadTimeDays + (long) safetyStockDays);

        List<ReplenishmentRowResponse> rows = new ArrayList<>();
        for (Inventory inventory : inventories) {
            Product product = inventory.getProduct();
            if (!product.isSelectable()) {
                continue; // sản phẩm ngừng kinh doanh thì không gợi ý nhập thêm
            }

            BigDecimal quantityOnHand = inventory.getQuantity();
            BigDecimal totalIssued = issuedByProduct.getOrDefault(product.getId(), BigDecimal.ZERO);

            // (1) Lượng xuất trung bình mỗi ngày
            BigDecimal averageDailyIssue = totalIssued.divide(windowDivisor, 3, RoundingMode.HALF_UP);

            // (2) Số ngày còn đủ hàng; chưa từng xuất thì không dự báo được
            BigDecimal daysOfStockLeft = averageDailyIssue.signum() > 0
                    ? quantityOnHand.divide(averageDailyIssue, 1, RoundingMode.HALF_UP)
                    : null;

            // (3) Số lượng nên nhập
            BigDecimal suggestedQuantity = averageDailyIssue
                    .multiply(coverageDays)
                    .add(product.getMinStock())
                    .subtract(quantityOnHand)
                    .setScale(0, RoundingMode.CEILING); // làm tròn LÊN
            if (suggestedQuantity.signum() < 0) {
                suggestedQuantity = BigDecimal.ZERO; // không gợi ý số âm
            }

            // Bỏ qua mặt hàng vừa không cần nhập, vừa không có dấu hiệu sắp hết
            boolean lowStock = product.getMinStock().signum() > 0
                    && quantityOnHand.compareTo(product.getMinStock()) <= 0;
            if (suggestedQuantity.signum() == 0 && !lowStock) {
                continue;
            }

            BigDecimal estimatedCost = suggestedQuantity
                    .multiply(product.getPurchasePrice())
                    .setScale(2, RoundingMode.HALF_UP);

            rows.add(new ReplenishmentRowResponse(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getUnit().getName(),
                    quantityOnHand,
                    product.getMinStock(),
                    totalIssued,
                    averageDailyIssue,
                    daysOfStockLeft,
                    suggestedQuantity,
                    product.getPurchasePrice(),
                    estimatedCost,
                    classifyUrgency(daysOfStockLeft, leadTimeDays, quantityOnHand)));
        }

        // Sắp xếp theo số ngày còn đủ hàng TĂNG DẦN: mặt hàng nguy cấp nhất lên đầu.
        // Mặt hàng chưa từng xuất (null) xếp cuối vì không có tín hiệu nhu cầu.
        rows.sort(Comparator.comparing(
                ReplenishmentRowResponse::daysOfStockLeft,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new ReplenishmentResponse(
                warehouse.getId(),
                warehouse.getName(),
                window,
                leadTimeDays,
                safetyStockDays,
                String.format(
                        "SL nên nhập = (xuất bình quân/ngày x (%d ngày chờ hàng + %d ngày dự trữ)) "
                                + "+ tồn tối thiểu - tồn hiện tại, làm tròn lên",
                        leadTimeDays, safetyStockDays),
                rows);
    }

    /**
     * Biểu đồ lượng xuất theo ngày của một sản phẩm.
     *
     * <p>Giúp người dùng kiểm tra xem con số bình quân có đại diện không: nếu lượng xuất
     * dồn vào vài ngày cao điểm thì bình quân sẽ gây nhầm lẫn.
     */
    @Transactional(readOnly = true)
    public IssueTrendResponse getIssueTrend(Long productId, Long warehouseId, Integer windowDays) {
        warehouseAccessGuard.requireAccess(warehouseId);

        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new NotFoundException("sản phẩm", productId));

        int window = windowDays != null && windowDays > 0
                ? windowDays
                : settingService.getInt(SettingKey.FORECAST_WINDOW_DAYS, 30);

        LocalDateTime from = LocalDate.now().minusDays(window).atStartOfDay();

        List<DailyQuantityItem> points = stockTransactionRepository
                .findDailyIssuedQuantity(productId, warehouseId, from)
                .stream()
                .map(row -> new DailyQuantityItem(row.date(), row.quantity()))
                .toList();

        return new IssueTrendResponse(product.getId(), product.getName(), window, points);
    }

    /**
     * Xếp loại mức độ cấp thiết, dùng để tô màu dòng trên giao diện.
     *
     * <ul>
     *   <li>{@code OUT_OF_STOCK}: đã hết hàng.</li>
     *   <li>{@code CRITICAL}: hàng sẽ hết trước khi lô mới kịp về.</li>
     *   <li>{@code WARNING}: còn đủ dưới hai lần thời gian chờ hàng.</li>
     *   <li>{@code NORMAL}: còn thoải mái.</li>
     * </ul>
     */
    private String classifyUrgency(BigDecimal daysOfStockLeft, int leadTimeDays, BigDecimal quantityOnHand) {
        if (quantityOnHand.signum() <= 0) {
            return "OUT_OF_STOCK";
        }
        if (daysOfStockLeft == null) {
            return "NORMAL";
        }
        if (daysOfStockLeft.compareTo(BigDecimal.valueOf(leadTimeDays)) <= 0) {
            return "CRITICAL";
        }
        if (daysOfStockLeft.compareTo(BigDecimal.valueOf(leadTimeDays * 2L)) <= 0) {
            return "WARNING";
        }
        return "NORMAL";
    }
}
