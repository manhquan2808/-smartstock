package com.smartstock.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một tham số cấu hình hệ thống, lưu dạng khóa - giá trị.
 *
 * <p>Vì sao dùng khóa - giá trị thay vì mỗi tham số một cột: thêm tham số mới chỉ cần
 * chèn một dòng, không phải viết migration đổi cấu trúc bảng. ADMIN sửa được các tham số
 * này ngay trên giao diện mà không cần khởi động lại ứng dụng.
 *
 * <p>Các khóa đang dùng (xem {@link SettingKey}): thời gian chờ hàng, số ngày dự trữ,
 * số ngày tồn lâu, giờ gửi báo cáo tồn thấp.
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 50)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    /** INTEGER | DECIMAL | STRING | BOOLEAN - giúp giao diện chọn đúng kiểu ô nhập. */
    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "INTEGER";

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
