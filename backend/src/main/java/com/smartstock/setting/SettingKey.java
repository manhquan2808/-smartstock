package com.smartstock.setting;

import com.smartstock.common.BusinessException;
import java.util.Arrays;

/**
 * Danh sách các tham số cấu hình hợp lệ của hệ thống.
 *
 * <p>Khai báo tường minh ở đây (thay vì để tự do thêm khóa bất kỳ) giúp: biết ngay mỗi
 * tham số dùng ở đâu, kiểm tra được khoảng giá trị hợp lệ, và tránh khóa rác trong CSDL.
 */
public enum SettingKey {

    /** Thời gian chờ hàng về sau khi đặt (ngày) - dùng cho công thức gợi ý nhập hàng. */
    LEAD_TIME_DAYS("LEAD_TIME_DAYS", "INTEGER", "Thời gian chờ hàng về (ngày)", 1, 180, "7"),

    /** Số ngày dự trữ an toàn - đệm thêm để không hết hàng giữa hai lần nhập. */
    SAFETY_STOCK_DAYS("SAFETY_STOCK_DAYS", "INTEGER", "Số ngày dự trữ an toàn", 0, 365, "14"),

    /** Số ngày lịch sử dùng để tính lượng xuất trung bình mỗi ngày (30/60/90). */
    FORECAST_WINDOW_DAYS("FORECAST_WINDOW_DAYS", "INTEGER", "Số ngày tính bình quân xuất", 7, 365, "30"),

    /** Không phát sinh xuất quá N ngày thì coi là hàng tồn lâu. */
    SLOW_MOVING_DAYS("SLOW_MOVING_DAYS", "INTEGER", "Ngưỡng hàng tồn lâu (ngày)", 30, 730, "90"),

    /** Giờ gửi email báo cáo hàng tồn thấp hằng ngày (0-23). */
    LOW_STOCK_REPORT_HOUR("LOW_STOCK_REPORT_HOUR", "INTEGER", "Giờ gửi báo cáo tồn thấp", 0, 23, "7"),

    /** Bật/tắt email báo cáo tồn thấp hằng ngày. */
    LOW_STOCK_REPORT_ENABLED(
            "LOW_STOCK_REPORT_ENABLED", "BOOLEAN", "Bật email báo cáo tồn thấp hằng ngày", 0, 1, "true");

    private final String key;
    private final String valueType;
    private final String description;
    private final int minValue;
    private final int maxValue;
    private final String defaultValue;

    SettingKey(String key, String valueType, String description, int minValue, int maxValue, String defaultValue) {
        this.key = key;
        this.valueType = valueType;
        this.description = description;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /** Tra cứu tham số theo tên khóa; báo lỗi rõ ràng nếu khóa không tồn tại. */
    public static SettingKey fromKey(String key) {
        return Arrays.stream(values())
                .filter(setting -> setting.key.equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Tham số cấu hình không hợp lệ: " + key, "UNKNOWN_SETTING_KEY"));
    }
}
