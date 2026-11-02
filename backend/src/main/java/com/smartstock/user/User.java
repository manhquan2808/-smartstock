package com.smartstock.user;

import com.smartstock.common.AuditableEntity;
import com.smartstock.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tài khoản người dùng hệ thống.
 *
 * <p>Giải thích cho người quen PHP: lớp này tương đương Model {@code User} của Laravel
 * (Eloquent). Khác biệt chính: các annotation {@code @Entity}, {@code @Column} mô tả
 * tường minh việc ánh xạ sang bảng/cột, thay vì Laravel suy luận theo quy ước tên.
 *
 * <p>Lưu ý nghiệp vụ:
 *
 * <ul>
 *   <li>Mật khẩu chỉ lưu dạng băm BCrypt, không bao giờ lưu bản rõ.</li>
 *   <li>ADMIN truy cập mọi kho nên tập {@code warehouses} thường để rỗng.</li>
 *   <li>Không xóa tài khoản, chỉ đặt {@code status = LOCKED}.</li>
 * </ul>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends AuditableEntity {

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /** Mật khẩu đã băm bằng BCrypt. Không trả trường này ra API trong mọi trường hợp. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /** Bật khi admin đặt lại mật khẩu: lần đăng nhập sau người dùng phải tự đổi. */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    /** Đếm số lần đăng nhập sai liên tiếp, phục vụ nhật ký và phát hiện dò mật khẩu. */
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Các kho mà người dùng được phép làm việc.
     *
     * <p>{@code FetchType.LAZY} nghĩa là chỉ truy vấn bảng kho khi code thực sự đọc tới
     * tập hợp này, tránh tải dữ liệu không cần thiết ở mọi truy vấn người dùng.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_warehouses",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    @Builder.Default
    private Set<Warehouse> warehouses = new HashSet<>();

    /** Tài khoản đang hoạt động và chưa bị khóa. */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Kiểm tra người dùng có quyền làm việc với kho này không.
     *
     * <p>Đây là quy tắc phân quyền theo kho, được gọi lại ở tầng service cho mọi
     * thao tác đọc/ghi liên quan tới một kho cụ thể. ADMIN luôn được phép.
     */
    public boolean canAccessWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return false;
        }
        if (isAdmin()) {
            return true;
        }
        return warehouses.stream().anyMatch(w -> warehouseId.equals(w.getId()));
    }
}
