package com.smartstock.inventory;

import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import com.smartstock.inventory.InventoryDtos.InventoryRowResponse;
import com.smartstock.inventory.InventoryDtos.LowStockRowResponse;
import com.smartstock.inventory.InventoryDtos.StockCardEntryResponse;
import com.smartstock.inventory.InventoryDtos.StockCardResponse;
import com.smartstock.catalog.Product;
import com.smartstock.catalog.ProductRepository;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import com.smartstock.security.WarehouseAccessGuard;
import com.smartstock.warehouse.Warehouse;
import com.smartstock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Các truy vấn CHỈ ĐỌC về tồn kho: danh sách tồn, cảnh báo tồn thấp, thẻ kho.
 *
 * <p>Tách khỏi {@link InventoryService} một cách có chủ đích: {@code InventoryService}
 * chỉ chứa phần GHI (thay đổi tồn, có khóa dòng và giao dịch), còn lớp này chỉ ĐỌC.
 * Nhờ vậy ranh giới "ai được sửa tồn kho" rất rõ ràng khi đọc mã nguồn.
 *
 * <p>Phân quyền áp dụng ở mọi phương thức: người dùng chỉ thấy dữ liệu của kho được gán,
 * và vai trò STAFF không nhận được giá vốn / giá trị tồn.
 */
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseAccessGuard warehouseAccessGuard;

    /**
     * Danh sách tồn kho có phân trang.
     *
     * @param stockState ALL | IN_STOCK (còn hàng) | LOW (sắp hết) | OUT (hết hàng)
     */
    @Transactional(readOnly = true)
    public PageResponse<InventoryRowResponse> search(
            Long warehouseId, Long categoryId, String keyword, String stockState, Pageable pageable) {

        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        boolean canViewCost = SecurityUtils.getCurrentUser().canViewCostPrice();
        String state = StringUtils.hasText(stockState) ? stockState.toUpperCase() : "ALL";

        return PageResponse.from(
                inventoryRepository.searchInventory(
                        scope.all(), scope.ids(), categoryId, normalize(keyword), state, pageable),
                inventory -> toRow(inventory, canViewCost));
    }

    /** Danh sách hàng sắp hết / đã hết so với mức tồn tối thiểu. */
    @Transactional(readOnly = true)
    public List<LowStockRowResponse> listLowStock(Long warehouseId) {
        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        return inventoryRepository.findLowStock(scope.all(), scope.ids()).stream()
                .map(inventory -> new LowStockRowResponse(
                        inventory.getProduct().getId(),
                        inventory.getProduct().getSku(),
                        inventory.getProduct().getName(),
                        inventory.getProduct().getUnit().getName(),
                        inventory.getWarehouse().getId(),
                        inventory.getWarehouse().getName(),
                        inventory.getQuantity(),
                        inventory.getProduct().getMinStock(),
                        inventory.getQuantity().signum() <= 0))
                .toList();
    }

    /**
     * THẺ KHO: lịch sử biến động của một sản phẩm tại một kho.
     *
     * <p>Cột "tồn lũy kế" được lấy từ {@code balanceAfter} của từng giao dịch - giá trị
     * này đã được ghi lại tại thời điểm giao dịch nên luôn khớp với tồn thực tế.
     *
     * <p>Tồn đầu kỳ được tính bằng tổng mọi giao dịch TRƯỚC ngày bắt đầu, nên dù xem kỳ
     * nào thì số liệu vẫn liền mạch.
     */
    @Transactional(readOnly = true)
    public StockCardResponse getStockCard(Long productId, Long warehouseId, LocalDate fromDate, LocalDate toDate) {
        warehouseAccessGuard.requireAccess(warehouseId);
        boolean canViewCost = SecurityUtils.getCurrentUser().canViewCostPrice();

        Product product = productRepository
                .findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new NotFoundException("sản phẩm", productId));
        Warehouse warehouse = warehouseRepository
                .findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("kho", warehouseId));

        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.atTime(LocalTime.MAX);

        BigDecimal openingQuantity = from == null
                ? BigDecimal.ZERO
                : stockTransactionRepository.sumQuantityBefore(
                        productId, false, List.of(warehouseId), from);

        List<StockTransaction> transactions =
                stockTransactionRepository.findStockCard(productId, warehouseId, from, to);

        // Tồn sau mỗi dòng được TÍNH LẠI theo đúng thứ tự hiển thị, không lấy cột
        // balance_after đã lưu trong cơ sở dữ liệu.
        //
        // Lý do: balance_after là số dư lũy kế theo thứ tự GHI (id tăng dần), còn thẻ kho
        // sắp xếp theo NGÀY CHỨNG TỪ. Hai thứ tự này lệch nhau khi phiếu được lập lùi ngày,
        // hoặc khi hủy một phiếu cũ sinh giao dịch đảo ngược mang ngày hôm nay. Khi đó cột
        // balance_after đọc dọc xuống không cộng trừ khớp nhau, và dòng cuối cùng theo ngày
        // không phải tồn cuối kỳ.
        List<StockCardEntryResponse> entries = new ArrayList<>(transactions.size());
        BigDecimal soDuChay = openingQuantity;
        for (StockTransaction transaction : transactions) {
            soDuChay = soDuChay.add(transaction.getQuantityChange());
            entries.add(new StockCardEntryResponse(
                    transaction.getId(),
                    transaction.getTransactionDate(),
                    transaction.getRefCode(),
                    transaction.getType(),
                    transaction.getType().getDisplayName(),
                    describeTransaction(transaction),
                    // Dương là nhập, âm là xuất: tách thành hai cột cho dễ đọc
                    transaction.getQuantityChange().signum() > 0 ? transaction.getQuantityChange() : null,
                    transaction.getQuantityChange().signum() < 0
                            ? transaction.getQuantityChange().abs()
                            : null,
                    soDuChay,
                    canViewCost ? transaction.getUnitCost() : null,
                    transaction.getNote()));
        }

        BigDecimal totalIn = transactions.stream()
                .map(StockTransaction::getQuantityChange)
                .filter(quantity -> quantity.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOut = transactions.stream()
                .map(StockTransaction::getQuantityChange)
                .filter(quantity -> quantity.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Tồn cuối kỳ = tồn đầu kỳ + nhập - xuất. Công thức này không phụ thuộc thứ tự dòng
        // nên luôn khớp với bất biến "tồn kho = tổng giao dịch", và khi không lọc ngày thì
        // bằng đúng inventories.quantity.
        BigDecimal closingQuantity = openingQuantity.add(totalIn).subtract(totalOut);

        return new StockCardResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getUnit().getName(),
                warehouse.getId(),
                warehouse.getName(),
                fromDate,
                toDate,
                openingQuantity,
                totalIn,
                totalOut,
                closingQuantity,
                entries);
    }

    /** Diễn giải ngắn gọn cho một dòng thẻ kho. */
    private String describeTransaction(StockTransaction transaction) {
        return switch (transaction.getType()) {
            case RECEIPT -> "Nhập kho theo phiếu " + transaction.getRefCode();
            case ISSUE -> "Xuất kho theo phiếu " + transaction.getRefCode();
            case ADJUSTMENT -> "Điều chỉnh theo kiểm kê " + transaction.getRefCode();
            case REVERSAL -> "Đảo ngược do hủy phiếu " + transaction.getRefCode();
        };
    }

    private InventoryRowResponse toRow(Inventory inventory, boolean canViewCost) {
        Product product = inventory.getProduct();
        boolean isLow = product.getMinStock().signum() > 0
                && inventory.getQuantity().compareTo(product.getMinStock()) <= 0;

        return new InventoryRowResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getCategory().getName(),
                product.getUnit().getName(),
                inventory.getWarehouse().getId(),
                inventory.getWarehouse().getCode(),
                inventory.getWarehouse().getName(),
                inventory.getQuantity(),
                product.getMinStock(),
                canViewCost ? inventory.getAvgCost() : null,
                canViewCost ? inventory.getStockValue() : null,
                isLow,
                inventory.getQuantity().signum() <= 0);
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }
}
