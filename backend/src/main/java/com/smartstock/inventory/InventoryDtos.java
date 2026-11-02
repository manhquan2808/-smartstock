package com.smartstock.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO của phân hệ tồn kho và thẻ kho.
 *
 * <p><b>Lưu ý phân quyền:</b> các trường {@code avgCost} và {@code stockValue} trả về
 * {@code null} khi người xem có vai trò STAFF. Việc lược bỏ được thực hiện ở BACKEND,
 * không phụ thuộc frontend ẩn cột - nên gọi API trực tiếp cũng không lấy được giá vốn.
 */
public final class InventoryDtos {

    private InventoryDtos() {}

    /**
     * Một dòng trong danh sách tồn kho (sản phẩm x kho).
     *
     * @param avgCost giá vốn bình quân; {@code null} với STAFF
     * @param stockValue giá trị tồn = số lượng x giá vốn; {@code null} với STAFF
     * @param lowStock true khi tồn bằng hoặc dưới mức tối thiểu
     */
    public record InventoryRowResponse(
            Long productId,
            String sku,
            String barcode,
            String productName,
            String categoryName,
            String unitName,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            BigDecimal quantity,
            BigDecimal minStock,
            BigDecimal avgCost,
            BigDecimal stockValue,
            boolean lowStock,
            boolean outOfStock) {}

    /** Một dòng cảnh báo tồn thấp (dùng cho dashboard và trang tồn kho). */
    public record LowStockRowResponse(
            Long productId,
            String sku,
            String productName,
            String unitName,
            Long warehouseId,
            String warehouseName,
            BigDecimal quantity,
            BigDecimal minStock,
            boolean outOfStock) {}

    /**
     * THẺ KHO của một sản phẩm tại một kho.
     *
     * @param openingQuantity tồn đầu kỳ, tính từ tổng giao dịch trước ngày bắt đầu
     * @param closingQuantity tồn cuối kỳ, lấy từ số dư sau giao dịch cuối cùng
     */
    public record StockCardResponse(
            Long productId,
            String sku,
            String productName,
            String unitName,
            Long warehouseId,
            String warehouseName,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingQuantity,
            BigDecimal totalIn,
            BigDecimal totalOut,
            BigDecimal closingQuantity,
            List<StockCardEntryResponse> entries) {}

    /**
     * Một dòng biến động trên thẻ kho.
     *
     * @param quantityIn số lượng nhập; {@code null} nếu dòng này là xuất
     * @param quantityOut số lượng xuất; {@code null} nếu dòng này là nhập
     * @param balanceAfter tồn lũy kế sau giao dịch
     * @param unitCost giá vốn tại thời điểm giao dịch; {@code null} với STAFF
     */
    public record StockCardEntryResponse(
            Long id,
            LocalDateTime transactionDate,
            String refCode,
            TransactionType type,
            String typeName,
            String description,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            BigDecimal balanceAfter,
            BigDecimal unitCost,
            String note) {}
}
