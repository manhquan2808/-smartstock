package com.smartstock.inventory.projection;

import java.math.BigDecimal;

/** Sản phẩm xuất nhiều nhất trong kỳ (Top 10 trên dashboard). */
public record TopProductRow(Long productId, String sku, String productName, String unitName, BigDecimal quantity) {

    public static TopProductRow from(Object[] row) {
        return new TopProductRow(
                ProjectionMapper.toLong(row[0]),
                ProjectionMapper.toText(row[1]),
                ProjectionMapper.toText(row[2]),
                ProjectionMapper.toText(row[3]),
                ProjectionMapper.toBigDecimal(row[4]));
    }
}
