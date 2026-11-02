package com.smartstock.auth;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.auth.AuthDtos.ChangePasswordRequest;
import com.smartstock.auth.AuthDtos.CurrentUser;
import com.smartstock.auth.AuthDtos.LoginRequest;
import com.smartstock.auth.AuthDtos.LoginResponse;
import com.smartstock.auth.AuthDtos.ResetPasswordRequest;
import com.smartstock.auth.AuthDtos.WarehouseBrief;
import com.smartstock.common.BusinessException;
import com.smartstock.common.ForbiddenException;
import com.smartstock.config.AppProperties;
import com.smartstock.mail.MailService;
import com.smartstock.security.JwtService;
import com.smartstock.security.SecurityUtils;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.warehouse.Warehouse;
import com.smartstock.warehouse.WarehouseRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ xác thực: đăng nhập, làm mới token, đăng xuất, quên/đặt lại/đổi mật khẩu.
 *
 * <p>Luồng đăng nhập đầy đủ:
 *
 * <ol>
 *   <li>Người dùng gửi tên đăng nhập + mật khẩu.</li>
 *   <li>Hệ thống so khớp mật khẩu với bản băm BCrypt trong CSDL.</li>
 *   <li>Nếu đúng: sinh access token (15 phút) + refresh token (7 ngày).</li>
 *   <li>Access token trả trong JSON; refresh token do controller đặt vào cookie HttpOnly.</li>
 *   <li>Ghi nhật ký đăng nhập (thành công hoặc thất bại) kèm địa chỉ IP.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    /**
     * Đăng nhập bằng tên đăng nhập và mật khẩu.
     *
     * @return access token kèm thông tin người dùng, và refresh token thô để controller
     *     đặt vào cookie
     */
    @Transactional
    public AuthResult login(LoginRequest request, String userAgent, String ipAddress) {
        User user = userRepository
                .findByUsernameIgnoreCase(request.username())
                .orElse(null);

        // Thông báo lỗi giống nhau cho mọi trường hợp sai, để không tiết lộ
        // tên đăng nhập nào có tồn tại trong hệ thống
        if (user == null) {
            auditService.logAuthentication(
                    AuditAction.LOGIN_FAILED, null, request.username(), "Tên đăng nhập không tồn tại");
            throw new BusinessException(
                    "Tên đăng nhập hoặc mật khẩu không đúng.", "BAD_CREDENTIALS", HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepository.save(user);
            auditService.logAuthentication(
                    AuditAction.LOGIN_FAILED,
                    user.getId(),
                    user.getUsername(),
                    "Sai mật khẩu (lần thứ " + user.getFailedLoginAttempts() + ")");
            throw new BusinessException(
                    "Tên đăng nhập hoặc mật khẩu không đúng.", "BAD_CREDENTIALS", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isActive()) {
            auditService.logAuthentication(
                    AuditAction.LOGIN_FAILED, user.getId(), user.getUsername(), "Tài khoản đã bị khóa");
            throw new BusinessException(
                    "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.",
                    "ACCOUNT_LOCKED",
                    HttpStatus.FORBIDDEN);
        }

        // Đăng nhập thành công: xóa bộ đếm sai mật khẩu và ghi lại thời điểm
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logAuthentication(
                AuditAction.LOGIN_SUCCESS, user.getId(), user.getUsername(), "Đăng nhập thành công");

        return issueTokens(user, userAgent, ipAddress);
    }

    /**
     * Làm mới access token bằng refresh token trong cookie.
     *
     * <p>Áp dụng "token rotation": refresh token cũ bị thu hồi ngay và một token mới
     * được phát hành. Nếu một token đã bị thu hồi lại được dùng tiếp (dấu hiệu bị đánh
     * cắp), hệ thống thu hồi TOÀN BỘ phiên của người đó.
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(
                    "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", "NO_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
        }

        String hash = jwtService.hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(
                        "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
                        "INVALID_REFRESH_TOKEN",
                        HttpStatus.UNAUTHORIZED));

        if (stored.getRevokedAt() != null) {
            // Token đã dùng rồi mà vẫn bị dùng lại -> nghi ngờ bị đánh cắp
            log.warn(
                    "Phát hiện dùng lại refresh token đã thu hồi của người dùng id={}. Thu hồi toàn bộ phiên.",
                    stored.getUser().getId());
            refreshTokenRepository.revokeAllByUser(stored.getUser().getId(), LocalDateTime.now());
            throw new BusinessException(
                    "Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.",
                    "REFRESH_TOKEN_REUSED",
                    HttpStatus.UNAUTHORIZED);
        }

        if (!stored.isUsable()) {
            throw new BusinessException(
                    "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
                    "REFRESH_TOKEN_EXPIRED",
                    HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository
                .findByIdWithWarehouses(stored.getUser().getId())
                .orElseThrow(() -> new ForbiddenException("Tài khoản không còn tồn tại."));

        if (!user.isActive()) {
            refreshTokenRepository.revokeAllByUser(user.getId(), LocalDateTime.now());
            throw new BusinessException(
                    "Tài khoản của bạn đã bị khóa.", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN);
        }

        // Thu hồi token cũ rồi phát hành token mới
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        return issueTokens(user, userAgent, ipAddress);
    }

    /** Đăng xuất: thu hồi refresh token hiện tại (các thiết bị khác vẫn đăng nhập bình thường). */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository
                .findByTokenHash(jwtService.hashToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                    auditService.logAuthentication(
                            AuditAction.LOGOUT,
                            token.getUser().getId(),
                            token.getUser().getUsername(),
                            "Đăng xuất");
                });
    }

    /**
     * Gửi email đặt lại mật khẩu.
     *
     * <p>Lưu ý bảo mật: dù email không tồn tại trong hệ thống, API vẫn trả về thành công.
     * Nếu trả lỗi "email không tồn tại" thì kẻ tấn công có thể dò xem ai đang dùng hệ thống.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (!user.isActive()) {
                log.info("Bỏ qua yêu cầu đặt lại mật khẩu của tài khoản đã khóa: {}", user.getUsername());
                return;
            }
            // Mỗi lần yêu cầu mới sẽ vô hiệu các link cũ
            passwordResetTokenRepository.invalidateAllByUser(user.getId(), LocalDateTime.now());

            int expiryMinutes = appProperties.security().passwordResetTokenMinutes();
            String rawToken = jwtService.generateRefreshTokenValue();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(jwtService.hashToken(rawToken))
                    .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                    .build());

            String resetLink = appProperties.frontendUrl() + "/reset-password?token=" + rawToken;
            mailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink, expiryMinutes);

            auditService.logAuthentication(
                    AuditAction.PASSWORD_RESET_REQUEST,
                    user.getId(),
                    user.getUsername(),
                    "Yêu cầu đặt lại mật khẩu qua email");
        });
    }

    /** Đặt mật khẩu mới bằng mã trong email. Mã chỉ dùng được một lần. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(jwtService.hashToken(request.token()))
                .orElseThrow(() -> new BusinessException(
                        "Liên kết đặt lại mật khẩu không hợp lệ. Vui lòng yêu cầu liên kết mới.",
                        "INVALID_RESET_TOKEN"));

        if (!token.isUsable()) {
            throw new BusinessException(
                    "Liên kết đặt lại mật khẩu đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu liên kết mới.",
                    "EXPIRED_RESET_TOKEN");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        // Đổi mật khẩu thì mọi phiên đang mở trên các thiết bị khác đều bị đăng xuất
        refreshTokenRepository.revokeAllByUser(user.getId(), LocalDateTime.now());

        auditService.logAuthentication(
                AuditAction.PASSWORD_RESET, user.getId(), user.getUsername(), "Đặt lại mật khẩu qua email");
    }

    /** Người dùng tự đổi mật khẩu khi đang đăng nhập. */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ForbiddenException("Tài khoản không còn tồn tại."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu hiện tại không đúng.", "WRONG_CURRENT_PASSWORD");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu mới phải khác mật khẩu hiện tại.", "SAME_PASSWORD");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        auditService.logAuthentication(
                AuditAction.PASSWORD_CHANGE, user.getId(), user.getUsername(), "Người dùng tự đổi mật khẩu");
    }

    /** Thông tin người đang đăng nhập (API {@code GET /api/auth/me}). */
    @Transactional(readOnly = true)
    public CurrentUser getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository
                .findByIdWithWarehouses(userId)
                .orElseThrow(() -> new ForbiddenException("Tài khoản không còn tồn tại."));
        return toCurrentUser(user);
    }

    /** Sinh cặp token mới và lưu bản băm của refresh token vào CSDL. */
    private AuthResult issueTokens(User user, String userAgent, String ipAddress) {
        Set<Long> warehouseIds =
                user.getWarehouses().stream().map(Warehouse::getId).collect(Collectors.toSet());

        String accessToken =
                jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole(), warehouseIds);

        String rawRefreshToken = jwtService.generateRefreshTokenValue();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(LocalDateTime.ofInstant(jwtService.refreshTokenExpiry(), ZoneId.systemDefault()))
                .userAgent(truncate(userAgent, 255))
                .ipAddress(truncate(ipAddress, 45))
                .build());

        LoginResponse response = LoginResponse.of(
                accessToken, jwtService.getAccessTokenMinutes() * 60, toCurrentUser(user));
        return new AuthResult(response, rawRefreshToken);
    }

    /**
     * Dựng DTO thông tin người dùng.
     *
     * <p>ADMIN được xem mọi kho nên trả về toàn bộ kho đang hoạt động; các vai trò khác
     * chỉ nhận danh sách kho được gán.
     */
    private CurrentUser toCurrentUser(User user) {
        List<WarehouseBrief> warehouses;
        if (user.isAdmin()) {
            warehouses = warehouseRepository.findAllByActiveTrueOrderByNameAsc().stream()
                    .map(w -> new WarehouseBrief(w.getId(), w.getCode(), w.getName()))
                    .toList();
        } else {
            warehouses = user.getWarehouses().stream()
                    .filter(Warehouse::isActive)
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .map(w -> new WarehouseBrief(w.getId(), w.getCode(), w.getName()))
                    .toList();
        }

        return new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getRole().getDisplayName(),
                user.isMustChangePassword(),
                warehouses);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Kết quả xác thực nội bộ: phần trả cho client và refresh token thô.
     *
     * @param rawRefreshToken giá trị thô, chỉ dùng để đặt cookie, không bao giờ ghi log
     */
    public record AuthResult(LoginResponse response, String rawRefreshToken) {}
}
