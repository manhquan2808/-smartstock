package com.smartstock.inventory.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lượng nhập và lượng xuất của một ngày (biểu đồ nhập - xuất trên dashboard). */
public record DailyFlowRow(LocalDate date, BigDecimal receivedQuantity, BigDecimal issuedQuantity) {

    public static DailyFlowRow from(Object[] row) {
        return new DailyFlowRow(
                ProjectionMapper.toLocalDate(row[0]),
                ProjectionMapper.toBigDecimal(row[1]),
                ProjectionMapper.toBigDecimal(row[2]));
    }
}
