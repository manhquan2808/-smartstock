package com.smartstock.auth;

import com.smartstock.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Các DTO của phân hệ xác thực.
 *
 * <p>Gom vào một lớp "vỏ" để dễ tra cứu: tất cả dữ liệu vào/ra của API đăng nhập đều ở đây.
 * Mỗi DTO là một {@code record} nên bất biến (không ai sửa được sau khi tạo).
 *
 * <p>Các annotation {@code @NotBlank}, {@code @Size}... là Bean Validation - tương đương
 * mảng {@code rules()} trong FormRequest của Laravel. Thông báo lỗi viết bằng tiếng Việt
 * để hiển thị trực tiếp cạnh ô nhập trên giao diện.
 */
public final class AuthDtos {

    private AuthDtos() {}

    /** Dữ liệu đăng nhập. */
    public record LoginRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập") String username,
            @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {}

    /**
     * Kết quả đăng nhập / làm mới token.
     *
     * <p>Refresh token KHÔNG nằm trong JSON này: nó được đặt vào cookie HttpOnly nên
     * JavaScript không đọc được, giảm thiệt hại nếu website bị tấn công XSS.
     */
    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, CurrentUser user) {

        public static LoginResponse of(String accessToken, long expiresInSeconds, CurrentUser user) {
            return new LoginResponse(accessToken, "Bearer", expiresInSeconds, user);
        }
    }

    /**
     * Thông tin người đang đăng nhập, frontend dùng để dựng menu và ẩn/hiện nút.
     *
     * @param warehouses danh sách kho được phép làm việc (ADMIN nhận toàn bộ kho đang hoạt động)
     * @param mustChangePassword true thì frontend buộc chuyển sang trang đổi mật khẩu
     */
    public record CurrentUser(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            String avatarUrl,
            Role role,
            String roleName,
            boolean mustChangePassword,
            List<WarehouseBrief> warehouses) {}

    /** Thông tin kho tối giản, dùng cho bộ chọn kho ở header. */
    public record WarehouseBrief(Long id, String code, String name) {}

    /** Yêu cầu gửi email đặt lại mật khẩu. */
    public record ForgotPasswordRequest(
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không đúng định dạng") String email) {}

    /** Đặt mật khẩu mới bằng mã nhận qua email. */
    public record ResetPasswordRequest(
            @NotBlank(message = "Thiếu mã xác thực") String token,
            @NotBlank(message = "Vui lòng nhập mật khẩu mới")
                    @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
                    @Pattern(
                            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                            message = "Mật khẩu phải có chữ thường, chữ in hoa và chữ số")
                    String newPassword) {}

    /** Người dùng tự đổi mật khẩu khi đã đăng nhập. */
    public record ChangePasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
            @NotBlank(message = "Vui lòng nhập mật khẩu mới")
                    @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
                    @Pattern(
                            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                            message = "Mật khẩu phải có chữ thường, chữ in hoa và chữ số")
                    String newPassword) {}

    /** Thông điệp đơn giản trả về cho các thao tác không có dữ liệu kèm theo. */
    public record MessageResponse(String message) {}
}
