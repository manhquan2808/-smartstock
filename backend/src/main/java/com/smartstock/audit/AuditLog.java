package com.smartstock.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một dòng nhật ký hoạt động (chỉ ADMIN xem được).
 *
 * <p>Bảng này chỉ GHI THÊM, không sửa và không xóa, để giữ giá trị đối chiếu khi cần
 * truy vết ai đã làm gì. Vì vậy entity không kế thừa {@code BaseEntity} (không cần
 * {@code updated_at}) và tự khai báo {@code createdAt}.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Có thể null khi đăng nhập thất bại với tên tài khoản không tồn tại. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "description", length = 500)
    private String description;

    /** Trạng thái dữ liệu TRƯỚC khi thay đổi, dạng JSON. */
    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    /** Trạng thái dữ liệu SAU khi thay đổi, dạng JSON. */
    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
