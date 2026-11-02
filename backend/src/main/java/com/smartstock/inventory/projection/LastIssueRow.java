package com.smartstock.inventory.projection;

import java.time.LocalDateTime;

/** Ngày phát sinh xuất gần nhất của một sản phẩm tại một kho (báo cáo hàng tồn lâu). */
public record LastIssueRow(Long productId, Long warehouseId, LocalDateTime lastIssueDate) {

    public static LastIssueRow from(Object[] row) {
        return new LastIssueRow(
                ProjectionMapper.toLong(row[0]),
                ProjectionMapper.toLong(row[1]),
                ProjectionMapper.toLocalDateTime(row[2]));
    }
}
