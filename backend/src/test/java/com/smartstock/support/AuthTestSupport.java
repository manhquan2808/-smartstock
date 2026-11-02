package com.smartstock.support;

import com.smartstock.security.AppUserDetails;
import com.smartstock.user.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Giả lập người dùng đang đăng nhập cho các test gọi trực tiếp vào tầng service.
 *
 * <p>Vì sao cần: các service gọi {@code SecurityUtils.getCurrentUser()} để biết ai đang
 * thao tác (ghi người lập phiếu, kiểm tra quyền theo kho). Khi test gọi service mà không
 * đi qua HTTP thì không có ai đăng nhập, nên phải tự đặt thông tin vào SecurityContext.
 *
 * <p>Nhớ gọi {@link #clear()} trong {@code @AfterEach} để không rò rỉ trạng thái
 * sang test kế tiếp.
 */
public final class AuthTestSupport {

    private AuthTestSupport() {}

    /** Đặt người dùng đang đăng nhập là tài khoản đã cho. */
    public static void loginAs(User user) {
        AppUserDetails details = new AppUserDetails(user);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    /** Xóa thông tin đăng nhập giả lập. */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
