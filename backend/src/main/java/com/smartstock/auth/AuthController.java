package com.smartstock.auth;

import com.smartstock.auth.AuthDtos.ChangePasswordRequest;
import com.smartstock.auth.AuthDtos.CurrentUser;
import com.smartstock.auth.AuthDtos.ForgotPasswordRequest;
import com.smartstock.auth.AuthDtos.LoginRequest;
import com.smartstock.auth.AuthDtos.LoginResponse;
import com.smartstock.auth.AuthDtos.MessageResponse;
import com.smartstock.auth.AuthDtos.ResetPasswordRequest;
import com.smartstock.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API xác thực.
 *
 * <p>Controller chỉ làm ba việc: nhận dữ liệu, gọi service, trả kết quả. Toàn bộ quy tắc
 * nghiệp vụ nằm trong {@link AuthService} - đây là nguyên tắc phân lớp
 * {@code controller -> service -> repository} áp dụng cho mọi phân hệ của dự án.
 *
 * <p>Riêng việc đặt cookie refresh token được làm ở đây vì nó thuộc tầng HTTP.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "01. Xác thực", description = "Đăng nhập, làm mới token, quên và đổi mật khẩu")
public class AuthController {

    private final AuthService authService;
    private final AppProperties appProperties;

    @PostMapping("/login")
    @SecurityRequirements // endpoint công khai: không yêu cầu token trên Swagger
    @Operation(
            summary = "Đăng nhập",
            description = "Trả về access token (15 phút) và đặt refresh token vào cookie HttpOnly (7 ngày)")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthService.AuthResult result =
                authService.login(request, httpRequest.getHeader(HttpHeaders.USER_AGENT), clientIp(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.rawRefreshToken()).toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Làm mới access token",
            description = "Dùng refresh token trong cookie. Token cũ bị thu hồi và thay bằng token mới.")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        AuthService.AuthResult result =
                authService.refresh(refreshToken, httpRequest.getHeader(HttpHeaders.USER_AGENT), clientIp(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.rawRefreshToken()).toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Đăng xuất", description = "Thu hồi refresh token hiện tại và xóa cookie")
    public ResponseEntity<MessageResponse> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        // Đặt cookie rỗng với maxAge = 0 để trình duyệt xóa cookie ngay
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildClearedRefreshCookie().toString())
                .body(new MessageResponse("Đã đăng xuất."));
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin người đang đăng nhập")
    public CurrentUser me() {
        return authService.getCurrentUser();
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(
            summary = "Quên mật khẩu",
            description = "Gửi email chứa liên kết đặt lại mật khẩu, hạn 30 phút. "
                    + "Luôn trả về thành công để không tiết lộ email nào đang tồn tại.")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return new MessageResponse(
                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu. "
                        + "Vui lòng kiểm tra hộp thư.");
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Đặt lại mật khẩu bằng mã trong email")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.");
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu khi đang đăng nhập")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return new MessageResponse("Đổi mật khẩu thành công.");
    }

    /**
     * Tạo cookie chứa refresh token.
     *
     * <ul>
     *   <li>{@code httpOnly}: JavaScript không đọc được -> giảm rủi ro XSS.</li>
     *   <li>{@code secure} + {@code sameSite=None}: bắt buộc khi frontend (Netlify) và
     *       backend (Render) ở hai domain khác nhau.</li>
     *   <li>{@code path=/api/auth}: cookie chỉ được gửi tới nhóm endpoint xác thực.</li>
     * </ul>
     */
    private ResponseCookie buildRefreshCookie(String rawToken) {
        return ResponseCookie.from(appProperties.cookie().refreshTokenName(), rawToken)
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .sameSite(appProperties.cookie().sameSite())
                .path("/api/auth")
                .maxAge(Duration.ofDays(7))
                .build();
    }

    private ResponseCookie buildClearedRefreshCookie() {
        return ResponseCookie.from(appProperties.cookie().refreshTokenName(), "")
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .sameSite(appProperties.cookie().sameSite())
                .path("/api/auth")
                .maxAge(0)
                .build();
    }

    /** Lấy IP thật của client khi chạy sau proxy của dịch vụ hosting. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
