package com.smartstock.inventory.projection;

import java.math.BigDecimal;

/**
 * Một dòng của báo cáo NHẬP - XUẤT - TỒN.
 *
 * <p>Đây là "projection": truy vấn chỉ đọc đúng các cột cần thiết và gom nhóm ngay trong
 * cơ sở dữ liệu, nhờ vậy báo cáo vẫn nhanh khi dữ liệu lên tới hàng chục nghìn dòng.
 *
 * <p>Tồn cuối kỳ không nằm trong truy vấn mà tính tại đây:
 * {@code tồn cuối = tồn đầu + nhập - xuất}.
 */
public record InventoryReportRow(
        Long productId,
        String sku,
        String productName,
        String unitName,
        String categoryName,
        BigDecimal openingQuantity,
        BigDecimal receivedQuantity,
        BigDecimal issuedQuantity) {

    /** Dựng từ một dòng kết quả thô của truy vấn gộp nhóm. */
    public static InventoryReportRow from(Object[] row) {
        return new InventoryReportRow(
                ProjectionMapper.toLong(row[0]),
                ProjectionMapper.toText(row[1]),
                ProjectionMapper.toText(row[2]),
                ProjectionMapper.toText(row[3]),
                ProjectionMapper.toText(row[4]),
                ProjectionMapper.toBigDecimal(row[5]),
                ProjectionMapper.toBigDecimal(row[6]),
                ProjectionMapper.toBigDecimal(row[7]));
    }

    /** Tồn cuối kỳ = tồn đầu kỳ + nhập trong kỳ - xuất trong kỳ. */
    public BigDecimal closingQuantity() {
        return openingQuantity.add(receivedQuantity).subtract(issuedQuantity);
    }
}
