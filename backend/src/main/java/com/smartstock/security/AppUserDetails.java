package com.smartstock.security;

import com.smartstock.user.Role;
import com.smartstock.user.User;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Thông tin người đang đăng nhập mà Spring Security giữ trong bộ nhớ cho mỗi request.
 *
 * <p>Ngoài username/password/quyền theo chuẩn của Spring, lớp này mang thêm hai thứ
 * quan trọng với nghiệp vụ kho:
 *
 * <ul>
 *   <li>{@code userId}: để ghi người lập/người duyệt phiếu.</li>
 *   <li>{@code warehouseIds}: danh sách kho được gán, dùng cho phân quyền theo kho.</li>
 * </ul>
 *
 * <p>Nhờ vậy service không phải truy vấn lại bảng users ở mỗi lần kiểm tra quyền.
 */
@Getter
public class AppUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final Set<Long> warehouseIds;
    private final boolean active;
    private final boolean mustChangePassword;

    public AppUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.warehouseIds =
                user.getWarehouses().stream().map(w -> w.getId()).collect(Collectors.toUnmodifiableSet());
        this.active = user.isActive();
        this.mustChangePassword = user.isMustChangePassword();
    }

    /** Dùng khi dựng lại thông tin người dùng từ access token (không truy vấn CSDL). */
    public AppUserDetails(Long userId, String username, Role role, Set<Long> warehouseIds) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = null;
        this.fullName = null;
        this.role = role;
        this.warehouseIds = Set.copyOf(warehouseIds);
        this.active = true;
        this.mustChangePassword = false;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Quyền duyệt phiếu thuộc về ADMIN và MANAGER. */
    public boolean canApprove() {
        return role == Role.ADMIN || role == Role.MANAGER;
    }

    /** STAFF không được xem giá vốn, giá trị tồn. */
    public boolean canViewCostPrice() {
        return role == Role.ADMIN || role == Role.MANAGER;
    }

    /** ADMIN thấy mọi kho; các vai trò khác chỉ thấy kho được gán. */
    public boolean canAccessWarehouse(Long warehouseId) {
        return warehouseId != null && (isAdmin() || warehouseIds.contains(warehouseId));
    }
}
