package com.smartstock.security;

import com.smartstock.config.AppProperties;
import com.smartstock.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tạo và kiểm tra JSON Web Token (JWT).
 *
 * <p>Cách hệ thống dùng token:
 *
 * <ul>
 *   <li><b>Access token</b> (hạn 15 phút): frontend giữ trong BIẾN BỘ NHỚ, gửi kèm
 *       header {@code Authorization: Bearer ...} ở mỗi request. Không lưu vào
 *       localStorage để giảm rủi ro bị đánh cắp qua XSS.</li>
 *   <li><b>Refresh token</b> (hạn 7 ngày): là một chuỗi ngẫu nhiên, đặt trong cookie
 *       {@code HttpOnly} nên JavaScript không đọc được. Trong CSDL chỉ lưu bản BĂM
 *       SHA-256 của nó.</li>
 * </ul>
 *
 * <p>Giải thích cho người quen PHP: đây là phần tương đương thư viện tymon/jwt-auth
 * hoặc Laravel Sanctum, nhưng viết tay để kiểm soát rõ thời hạn và nội dung token.
 */
@Slf4j
@Service
public class JwtService {

    /** Tên các trường (claim) tự định nghĩa trong access token. */
    private static final String CLAIM_USER_ID = "uid";

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_WAREHOUSES = "whs";

    private final SecretKey secretKey;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public JwtService(AppProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // Thuật toán HS256 yêu cầu khóa tối thiểu 256 bit = 32 byte
            throw new IllegalStateException(
                    "Cấu hình JWT_SECRET phải có tối thiểu 32 ký tự. Vui lòng kiểm tra biến môi trường.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = properties.jwt().accessTokenMinutes();
        this.refreshTokenDays = properties.jwt().refreshTokenDays();
    }

    /**
     * Sinh access token cho người dùng.
     *
     * <p>Token chứa sẵn vai trò và danh sách kho để mỗi request không phải truy vấn CSDL.
     * Hệ quả cần nhớ: nếu admin đổi quyền, thay đổi chỉ có hiệu lực sau khi access token
     * cũ hết hạn (tối đa 15 phút) hoặc khi người dùng làm mới token.
     */
    public String generateAccessToken(Long userId, String username, Role role, Set<Long> warehouseIds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_WAREHOUSES, warehouseIds.stream().map(String::valueOf).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Đọc token và dựng lại thông tin người dùng.
     *
     * @return thông tin người dùng, hoặc {@code null} nếu token sai/hết hạn
     */
    public AppUserDetails parseAccessToken(String token) {
        try {
            Claims claims =
                    Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

            Long userId = claims.get(CLAIM_USER_ID, Number.class).longValue();
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));

            @SuppressWarnings("unchecked")
            List<String> rawWarehouses = claims.get(CLAIM_WAREHOUSES, List.class);
            Set<Long> warehouseIds = rawWarehouses == null
                    ? Set.of()
                    : rawWarehouses.stream().map(Long::valueOf).collect(Collectors.toUnmodifiableSet());

            return new AppUserDetails(userId, claims.getSubject(), role, warehouseIds);
        } catch (ExpiredJwtException ex) {
            // Hết hạn là tình huống bình thường: frontend sẽ tự gọi /api/auth/refresh
            log.debug("Access token đã hết hạn");
            return null;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Access token không hợp lệ: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Sinh refresh token: chuỗi ngẫu nhiên 256 bit dạng UUID ghép đôi.
     *
     * <p>Refresh token KHÔNG phải JWT - nó chỉ là một khóa tra cứu trong bảng
     * {@code refresh_tokens}, nhờ vậy có thể thu hồi ngay lập tức khi đăng xuất.
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Băm refresh token bằng SHA-256 trước khi lưu vào CSDL.
     *
     * <p>Không dùng BCrypt ở đây vì ta cần tra cứu token theo khóa duy nhất (BCrypt sinh
     * chuỗi khác nhau mỗi lần băm). Token vốn đã là chuỗi ngẫu nhiên 256 bit nên SHA-256
     * là đủ an toàn cho mục đích này.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Môi trường Java không hỗ trợ SHA-256", ex);
        }
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }

    /** Thời điểm hết hạn của refresh token tính từ bây giờ. */
    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS);
    }
}
