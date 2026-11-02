package com.smartstock.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Toàn bộ cấu hình riêng của ứng dụng, đọc từ nhánh {@code app.*} trong application.yml.
 *
 * <p>Dùng record nên giá trị là bất biến (immutable) sau khi khởi động. Spring Boot tự
 * kiểm tra kiểu dữ liệu khi nạp cấu hình nên sai cú pháp sẽ báo lỗi ngay lúc chạy máy chủ,
 * thay vì gây lỗi ngầm lúc đang dùng.
 *
 * <p>Tương đương file {@code config/app.php} + helper {@code config()} của Laravel,
 * nhưng có kiểm tra kiểu khi biên dịch.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name, String frontendUrl, Cors cors, Jwt jwt, Cookie cookie, Mail mail, Storage storage, Security security) {

    /** Danh sách origin được phép gọi API (cấu hình CORS). */
    public record Cors(List<String> allowedOrigins) {}

    /**
     * @param secret khóa bí mật HS256, tối thiểu 32 ký tự
     * @param accessTokenMinutes hạn access token, mặc định 15 phút
     * @param refreshTokenDays hạn refresh token, mặc định 7 ngày
     */
    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}

    /**
     * @param sameSite {@code lax} khi cùng domain, {@code none} khi khác domain (Netlify + Render)
     * @param secure bắt buộc {@code true} khi sameSite = none
     */
    public record Cookie(String refreshTokenName, String sameSite, boolean secure) {}

    /** @param enabled đặt false để tắt gửi mail (dùng khi chạy test) */
    public record Mail(String from, boolean enabled) {}

    /**
     * @param cloudinaryUrl nếu để rỗng thì lưu file vào thư mục local
     * @param maxFileSizeBytes giới hạn 2 MB cho ảnh sản phẩm và ảnh đại diện
     */
    public record Storage(String localDir, String cloudinaryUrl, long maxFileSizeBytes) {}

    /** @param passwordResetTokenMinutes hạn của link đặt lại mật khẩu (30 phút) */
    public record Security(int passwordResetTokenMinutes, int maxFailedLoginAttempts) {}
}
