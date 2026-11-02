package com.smartstock.inventory.projection;

import java.math.BigDecimal;

/** Tổng số lượng theo sản phẩm (dùng cho trang gợi ý nhập hàng). */
public record ProductQuantityRow(Long productId, BigDecimal quantity) {

    public static ProductQuantityRow from(Object[] row) {
        return new ProductQuantityRow(ProjectionMapper.toLong(row[0]), ProjectionMapper.toBigDecimal(row[1]));
    }
}
