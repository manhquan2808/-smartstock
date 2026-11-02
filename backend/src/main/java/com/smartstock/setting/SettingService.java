package com.smartstock.setting;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.common.BusinessException;
import com.smartstock.security.SecurityUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc và ghi tham số cấu hình hệ thống.
 *
 * <p>Mỗi lần đọc đều truy vấn CSDL (không dùng bộ nhớ đệm) để ADMIN sửa tham số là có
 * hiệu lực ngay. Các tham số này chỉ được đọc ở màn hình báo cáo và tác vụ hẹn giờ nên
 * tần suất truy vấn rất thấp, không gây vấn đề hiệu năng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingService {

    private final AppSettingRepository settingRepository;
    private final AuditService auditService;

    /**
     * Đọc một tham số dạng số nguyên.
     *
     * @param defaultValue giá trị dùng khi tham số chưa có trong CSDL hoặc sai định dạng
     */
    @Transactional(readOnly = true)
    public int getInt(SettingKey key, int defaultValue) {
        return settingRepository
                .findById(key.getKey())
                .map(setting -> parseInt(setting.getSettingValue(), key, defaultValue))
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(SettingKey key, boolean defaultValue) {
        return settingRepository
                .findById(key.getKey())
                .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
                .orElse(defaultValue);
    }

    /** Toàn bộ tham số, dùng cho trang Cấu hình hệ thống. */
    @Transactional(readOnly = true)
    public List<SettingResponse> listAll() {
        return settingRepository.findAllByOrderBySettingKeyAsc().stream()
                .map(SettingResponse::from)
                .toList();
    }

    /**
     * Cập nhật nhiều tham số một lần.
     *
     * <p>Chỉ cho sửa các khóa đã khai báo trong {@link SettingKey} - tránh việc tạo ra
     * các khóa rác mà không chỗ nào trong hệ thống đọc tới.
     */
    @Transactional
    public List<SettingResponse> updateAll(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            SettingKey key = SettingKey.fromKey(entry.getKey());
            AppSetting setting = settingRepository
                    .findById(key.getKey())
                    .orElseGet(() -> AppSetting.builder()
                            .settingKey(key.getKey())
                            .valueType(key.getValueType())
                            .description(key.getDescription())
                            .build());

            String oldValue = setting.getSettingValue();
            String newValue = validate(key, entry.getValue());

            setting.setSettingValue(newValue);
            setting.setValueType(key.getValueType());
            setting.setDescription(key.getDescription());
            setting.setUpdatedAt(LocalDateTime.now());
            setting.setUpdatedBy(SecurityUtils.getCurrentUserIdOrNull());
            settingRepository.save(setting);

            auditService.logChange(
                    AuditAction.UPDATE,
                    "AppSetting",
                    null,
                    "Cập nhật cấu hình " + key.getKey(),
                    oldValue,
                    newValue);
        }
        return listAll();
    }

    /** Kiểm tra giá trị hợp lệ theo từng loại tham số. */
    private String validate(SettingKey key, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessException("Giá trị của " + key.getKey() + " không được để trống.", "EMPTY_SETTING");
        }
        String value = rawValue.trim();

        if ("BOOLEAN".equals(key.getValueType())) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new BusinessException(
                        "Giá trị của " + key.getKey() + " phải là true hoặc false.", "INVALID_SETTING");
            }
            return value.toLowerCase();
        }

        if ("INTEGER".equals(key.getValueType())) {
            int number;
            try {
                number = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new BusinessException(
                        "Giá trị của " + key.getKey() + " phải là số nguyên.", "INVALID_SETTING");
            }
            if (number < key.getMinValue() || number > key.getMaxValue()) {
                throw new BusinessException(
                        String.format(
                                "Giá trị của %s phải nằm trong khoảng %d đến %d.",
                                key.getKey(), key.getMinValue(), key.getMaxValue()),
                        "INVALID_SETTING");
            }
            return value;
        }

        return value;
    }

    private int parseInt(String value, SettingKey key, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Tham số {} có giá trị không hợp lệ '{}', dùng mặc định {}", key.getKey(), value, defaultValue);
            return defaultValue;
        }
    }

    /** Tham số cấu hình trả về cho giao diện. */
    public record SettingResponse(
            String key, String value, String valueType, String description, LocalDateTime updatedAt) {

        public static SettingResponse from(AppSetting setting) {
            return new SettingResponse(
                    setting.getSettingKey(),
                    setting.getSettingValue(),
                    setting.getValueType(),
                    setting.getDescription(),
                    setting.getUpdatedAt());
        }
    }
}
