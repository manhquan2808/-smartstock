package com.smartstock.security;

import com.smartstock.common.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Lấy nhanh thông tin người đang đăng nhập ở bất kỳ đâu trong tầng service.
 *
 * <p>Giải thích cho người quen PHP: tương đương helper {@code auth()->user()} của Laravel.
 * Spring lưu thông tin này trong {@code SecurityContextHolder} - một biến gắn theo luồng
 * (thread-local) được {@code JwtAuthenticationFilter} đặt vào ở đầu mỗi request.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** @return người đang đăng nhập, hoặc null nếu request không có token hợp lệ */
    public static AppUserDetails getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AppUserDetails details) {
            return details;
        }
        return null;
    }

    /**
     * @return người đang đăng nhập
     * @throws ForbiddenException nếu chưa đăng nhập (dùng ở các API bắt buộc xác thực)
     */
    public static AppUserDetails getCurrentUser() {
        AppUserDetails current = getCurrentUserOrNull();
        if (current == null) {
            throw new ForbiddenException("Bạn cần đăng nhập để thực hiện chức năng này.");
        }
        return current;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    /** Dùng cho JPA Auditing: trả null khi không có ai đăng nhập (seed, tác vụ hẹn giờ). */
    public static Long getCurrentUserIdOrNull() {
        AppUserDetails current = getCurrentUserOrNull();
        return current == null ? null : current.getUserId();
    }
}
