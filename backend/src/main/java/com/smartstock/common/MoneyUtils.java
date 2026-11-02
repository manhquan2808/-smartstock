package com.smartstock.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Hàm tiện ích làm tròn số tiền và số lượng.
 *
 * <p>Toàn hệ thống dùng {@link BigDecimal} cho tiền và số lượng, KHÔNG dùng double
 * vì double làm tròn nhị phân gây sai lệch khi cộng dồn nhiều phiếu.
 *
 * <ul>
 *   <li>Tiền: 2 chữ số thập phân (khớp DECIMAL(18,2) trong CSDL).</li>
 *   <li>Số lượng: 3 chữ số thập phân (khớp DECIMAL(18,3), hỗ trợ kg/lít).</li>
 * </ul>
 */
public final class MoneyUtils {

    public static final int MONEY_SCALE = 2;
    public static final int QUANTITY_SCALE = 3;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtils() {}

    /** Làm tròn số tiền về 2 chữ số thập phân. */
    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : value.setScale(MONEY_SCALE, ROUNDING);
    }

    /** Làm tròn số lượng về 3 chữ số thập phân. */
    public static BigDecimal quantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : value.setScale(QUANTITY_SCALE, ROUNDING);
    }

    /** Trả về 0 nếu null, giúp tránh NullPointerException khi cộng dồn. */
    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    /** Thành tiền = số lượng x đơn giá, làm tròn 2 chữ số. */
    public static BigDecimal amount(BigDecimal quantity, BigDecimal unitPrice) {
        return money(zeroIfNull(quantity).multiply(zeroIfNull(unitPrice)));
    }
}
