package com.smartstock.report;

import com.smartstock.inventory.Inventory;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.inventory.StockTransactionRepository;
import com.smartstock.inventory.WarehouseScope;
import com.smartstock.inventory.projection.InventoryReportRow;
import com.smartstock.inventory.projection.LastIssueRow;
import com.smartstock.report.ReportDtos.InventoryReportResponse;
import com.smartstock.report.ReportDtos.InventoryReportRowResponse;
import com.smartstock.report.ReportDtos.SlowMovingReportResponse;
import com.smartstock.report.ReportDtos.SlowMovingRowResponse;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import com.smartstock.security.WarehouseAccessGuard;
import com.smartstock.setting.SettingKey;
import com.smartstock.setting.SettingService;
import com.smartstock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ báo cáo.
 *
 * <p><b>Nguyên tắc tính toán:</b> mọi con số đều lấy từ bảng {@code stock_transactions},
 * không lấy từ bảng tồn kho. Lý do: sổ giao dịch lưu đủ lịch sử nên tính được số liệu
 * của BẤT KỲ kỳ nào trong quá khứ, còn bảng tồn kho chỉ biết hiện tại.
 *
 * <p>Nhờ nguyên tắc đó, báo cáo nhập - xuất - tồn luôn khớp với thẻ kho:
 *
 * <pre>
 *   tồn đầu kỳ  = tổng mọi giao dịch TRƯỚC ngày bắt đầu
 *   nhập trong kỳ = tổng giao dịch dương trong kỳ
 *   xuất trong kỳ = tổng giao dịch âm trong kỳ (lấy trị tuyệt đối)
 *   tồn cuối kỳ  = tồn đầu kỳ + nhập - xuất
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final StockTransactionRepository stockTransactionRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseAccessGuard warehouseAccessGuard;
    private final SettingService settingService;

    /**
     * BÁO CÁO NHẬP - XUẤT - TỒN theo khoảng thời gian và theo kho.
     *
     * @param warehouseId null nghĩa là tất cả kho mà người dùng được xem
     * @param categoryId null nghĩa là mọi loại hàng
     */
    @Transactional(readOnly = true)
    public InventoryReportResponse buildInventoryReport(
            LocalDate fromDate, LocalDate toDate, Long warehouseId, Long categoryId) {

        validateDateRange(fromDate, toDate);
        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        AppUserDetails current = SecurityUtils.getCurrentUser();
        boolean canViewValue = current.canViewCostPrice();

        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);

        List<InventoryReportRow> rawRows =
                stockTransactionRepository.buildInventoryReport(from, to, scope.all(), scope.ids(), categoryId);

        // Giá vốn hiện tại để tính giá trị tồn cuối kỳ.
        // Lưu ý: đây là giá vốn hiện tại, không phải giá vốn tại thời điểm cuối kỳ -
        // đủ chính xác cho mục đích quản trị và được ghi rõ trên giao diện.
        Map<Long, BigDecimal> avgCostByProduct = loadAvgCosts(scope);

        List<InventoryReportRowResponse> rows = new ArrayList<>();
        BigDecimal totalOpening = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        BigDecimal totalIssued = BigDecimal.ZERO;
        BigDecimal totalClosing = BigDecimal.ZERO;
        BigDecimal totalClosingValue = BigDecimal.ZERO;

        for (InventoryReportRow raw : rawRows) {
            BigDecimal closing = raw.closingQuantity();
            BigDecimal avgCost = avgCostByProduct.getOrDefault(raw.productId(), BigDecimal.ZERO);
            BigDecimal closingValue = closing.multiply(avgCost).setScale(2, java.math.RoundingMode.HALF_UP);

            rows.add(new InventoryReportRowResponse(
                    raw.productId(),
                    raw.sku(),
                    raw.productName(),
                    raw.unitName(),
                    raw.categoryName(),
                    raw.openingQuantity(),
                    raw.receivedQuantity(),
                    raw.issuedQuantity(),
                    closing,
                    canViewValue ? avgCost : null,
                    canViewValue ? closingValue : null));

            totalOpening = totalOpening.add(raw.openingQuantity());
            totalReceived = totalReceived.add(raw.receivedQuantity());
            totalIssued = totalIssued.add(raw.issuedQuantity());
            totalClosing = totalClosing.add(closing);
            totalClosingValue = totalClosingValue.add(closingValue);
        }

        return new InventoryReportResponse(
                fromDate,
                toDate,
                describeWarehouse(warehouseId),
                categoryId == null ? "Tất cả loại hàng" : rows.isEmpty() ? "" : rows.get(0).categoryName(),
                rows,
                totalOpening,
                totalReceived,
                totalIssued,
                totalClosing,
                canViewValue ? totalClosingValue : null,
                current.getUsername(),
                LocalDateTime.now());
    }

    /**
     * BÁO CÁO HÀNG TỒN LÂU: hàng còn tồn nhưng không phát sinh xuất trong N ngày.
     *
     * <p>Hai nhóm được đưa vào báo cáo:
     *
     * <ul>
     *   <li>Hàng có lần xuất cuối cách đây quá N ngày.</li>
     *   <li>Hàng CHƯA TỪNG xuất (nhập về rồi để đó) - nhóm này thường bị bỏ sót nếu chỉ
     *       dựa vào ngày xuất cuối.</li>
     * </ul>
     *
     * @param thresholdDays số ngày ngưỡng; null thì lấy theo cấu hình hệ thống (mặc định 90)
     */
    @Transactional(readOnly = true)
    public SlowMovingReportResponse buildSlowMovingReport(Long warehouseId, Integer thresholdDays) {
        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        AppUserDetails current = SecurityUtils.getCurrentUser();
        boolean canViewValue = current.canViewCostPrice();

        int threshold = thresholdDays != null
                ? thresholdDays
                : settingService.getInt(SettingKey.SLOW_MOVING_DAYS, 90);

        // Lập bảng tra "ngày xuất cuối" theo cặp (sản phẩm, kho)
        Map<String, LocalDateTime> lastIssueByKey = new HashMap<>();
        for (LastIssueRow row : stockTransactionRepository.findLastIssueDates(scope.all(), scope.ids())) {
            lastIssueByKey.put(key(row.productId(), row.warehouseId()), row.lastIssueDate());
        }

        LocalDate today = LocalDate.now();
        List<SlowMovingRowResponse> rows = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        // Quét các dòng tồn còn hàng; dùng searchInventory với trạng thái IN_STOCK
        List<Inventory> inventories = inventoryRepository
                .searchInventory(
                        scope.all(),
                        scope.ids(),
                        null,
                        null,
                        "ALL",
                        org.springframework.data.domain.PageRequest.of(0, 5000))
                .getContent();

        for (Inventory inventory : inventories) {
            if (inventory.getQuantity().signum() <= 0) {
                continue; // hết hàng thì không gọi là tồn lâu
            }

            LocalDateTime lastIssue = lastIssueByKey.get(
                    key(inventory.getProduct().getId(), inventory.getWarehouse().getId()));

            Long daysSince = lastIssue == null
                    ? null
                    : ChronoUnit.DAYS.between(lastIssue.toLocalDate(), today);

            // Giữ lại: chưa từng xuất, hoặc lần xuất cuối đã quá ngưỡng
            boolean isSlowMoving = daysSince == null || daysSince >= threshold;
            if (!isSlowMoving) {
                continue;
            }

            BigDecimal stockValue = inventory.getStockValue();
            rows.add(new SlowMovingRowResponse(
                    inventory.getProduct().getId(),
                    inventory.getProduct().getSku(),
                    inventory.getProduct().getName(),
                    inventory.getProduct().getUnit().getName(),
                    inventory.getWarehouse().getId(),
                    inventory.getWarehouse().getName(),
                    inventory.getQuantity(),
                    canViewValue ? inventory.getAvgCost() : null,
                    canViewValue ? stockValue : null,
                    lastIssue == null ? null : lastIssue.toLocalDate(),
                    daysSince));
            totalValue = totalValue.add(stockValue);
        }

        // Ưu tiên hiển thị hàng để lâu nhất lên đầu; hàng chưa từng xuất xếp trước
        rows.sort((a, b) -> {
            if (a.daysSinceLastIssue() == null && b.daysSinceLastIssue() == null) {
                return 0;
            }
            if (a.daysSinceLastIssue() == null) {
                return -1;
            }
            if (b.daysSinceLastIssue() == null) {
                return 1;
            }
            return Long.compare(b.daysSinceLastIssue(), a.daysSinceLastIssue());
        });

        return new SlowMovingReportResponse(
                threshold,
                describeWarehouse(warehouseId),
                rows,
                canViewValue ? totalValue : null,
                current.getUsername(),
                LocalDateTime.now());
    }

    /** Giá vốn hiện tại của từng sản phẩm trong phạm vi kho đang xem. */
    private Map<Long, BigDecimal> loadAvgCosts(WarehouseScope scope) {
        Map<Long, BigDecimal> result = new HashMap<>();
        inventoryRepository
                .searchInventory(
                        scope.all(),
                        scope.ids(),
                        null,
                        null,
                        "ALL",
                        org.springframework.data.domain.PageRequest.of(0, 5000))
                .getContent()
                .forEach(inventory -> {
                    // Một sản phẩm có thể có tồn ở nhiều kho với giá vốn khác nhau;
                    // khi báo cáo gộp nhiều kho, lấy giá vốn lớn nhất để không báo thiếu giá trị
                    Long productId = inventory.getProduct().getId();
                    BigDecimal existing = result.get(productId);
                    if (existing == null || inventory.getAvgCost().compareTo(existing) > 0) {
                        result.put(productId, inventory.getAvgCost());
                    }
                });
        return result;
    }

    private String key(Long productId, Long warehouseId) {
        return productId + ":" + warehouseId;
    }

    private String describeWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return "Tất cả kho";
        }
        return warehouseRepository
                .findById(warehouseId)
                .map(warehouse -> warehouse.getName())
                .orElse("Tất cả kho");
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new com.smartstock.common.BusinessException(
                    "Vui lòng chọn khoảng thời gian báo cáo.", "DATE_RANGE_REQUIRED");
        }
        if (fromDate.isAfter(toDate)) {
            throw new com.smartstock.common.BusinessException(
                    "Ngày bắt đầu không được sau ngày kết thúc.", "INVALID_DATE_RANGE");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > 366) {
            throw new com.smartstock.common.BusinessException(
                    "Khoảng thời gian báo cáo tối đa là 1 năm. Vui lòng chia nhỏ kỳ báo cáo.",
                    "DATE_RANGE_TOO_LARGE");
        }
    }
}
