package com.smartstock.user;

import com.smartstock.auth.AuthDtos.WarehouseBrief;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** DTO của phân hệ quản lý người dùng (chỉ ADMIN được gọi các API này). */
public final class UserDtos {

    private UserDtos() {}

    /**
     * Dữ liệu tạo tài khoản mới.
     *
     * <p>Không có chức năng tự đăng ký: chỉ ADMIN tạo tài khoản cho nhân sự nội bộ.
     *
     * @param warehouseIds danh sách kho được gán; bắt buộc với MANAGER và STAFF
     */
    public record CreateUserRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập")
                    @Size(min = 3, max = 50, message = "Tên đăng nhập phải có từ 3 đến 50 ký tự")
                    @Pattern(
                            regexp = "^[a-zA-Z0-9._-]+$",
                            message = "Tên đăng nhập chỉ gồm chữ không dấu, số và các ký tự . _ -")
                    String username,
            @NotBlank(message = "Vui lòng nhập email")
                    @Email(message = "Email không đúng định dạng")
                    @Size(max = 150, message = "Email tối đa 150 ký tự")
                    String email,
            @NotBlank(message = "Vui lòng nhập mật khẩu")
                    @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
                    @Pattern(
                            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                            message = "Mật khẩu phải có chữ thường, chữ in hoa và chữ số")
                    String password,
            @NotBlank(message = "Vui lòng nhập họ tên")
                    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
                    String fullName,
            @Pattern(regexp = "^$|^[0-9+\\s.()-]{8,20}$", message = "Số điện thoại không đúng định dạng") String phone,
            @NotNull(message = "Vui lòng chọn vai trò") Role role,
            Set<Long> warehouseIds,
            Boolean mustChangePassword) {}

    /** Dữ liệu cập nhật tài khoản. Không cho đổi tên đăng nhập để giữ dấu vết trên phiếu cũ. */
    public record UpdateUserRequest(
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") String email,
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 150) String fullName,
            @Pattern(regexp = "^$|^[0-9+\\s.()-]{8,20}$", message = "Số điện thoại không đúng định dạng") String phone,
            @NotNull(message = "Vui lòng chọn vai trò") Role role,
            Set<Long> warehouseIds) {}

    /** ADMIN đặt lại mật khẩu cho người dùng khác. */
    public record ResetUserPasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu mới")
                    @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
                    @Pattern(
                            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                            message = "Mật khẩu phải có chữ thường, chữ in hoa và chữ số")
                    String newPassword,
            /** Mặc định true: buộc người dùng tự đổi mật khẩu ở lần đăng nhập kế tiếp. */
            Boolean mustChangePassword) {}

    /** Người dùng tự cập nhật hồ sơ cá nhân (không đổi được vai trò và kho). */
    public record UpdateProfileRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 150) String fullName,
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") String email,
            @Pattern(regexp = "^$|^[0-9+\\s.()-]{8,20}$", message = "Số điện thoại không đúng định dạng") String phone,
            @Size(max = 500) String avatarUrl) {}

    /** Thông tin tài khoản trả về cho ADMIN. Không bao giờ chứa mật khẩu băm. */
    public record UserResponse(
            Long id,
            String username,
            String email,
            String fullName,
            String phone,
            String avatarUrl,
            Role role,
            String roleName,
            UserStatus status,
            String statusName,
            boolean mustChangePassword,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            List<WarehouseBrief> warehouses) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getPhone(),
                    user.getAvatarUrl(),
                    user.getRole(),
                    user.getRole().getDisplayName(),
                    user.getStatus(),
                    user.getStatus().getDisplayName(),
                    user.isMustChangePassword(),
                    user.getLastLoginAt(),
                    user.getCreatedAt(),
                    user.getWarehouses().stream()
                            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                            .map(w -> new WarehouseBrief(w.getId(), w.getCode(), w.getName()))
                            .toList());
        }
    }
}
