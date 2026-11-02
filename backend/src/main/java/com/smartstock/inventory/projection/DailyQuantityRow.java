package com.smartstock.inventory.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Số lượng xuất theo từng ngày (dữ liệu vẽ biểu đồ). */
public record DailyQuantityRow(LocalDate date, BigDecimal quantity) {

    public static DailyQuantityRow from(Object[] row) {
        return new DailyQuantityRow(ProjectionMapper.toLocalDate(row[0]), ProjectionMapper.toBigDecimal(row[1]));
    }
}
