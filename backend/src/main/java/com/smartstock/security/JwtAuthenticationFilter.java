package com.smartstock.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bộ lọc đọc access token từ header {@code Authorization: Bearer ...} và đặt thông tin
 * người dùng vào {@code SecurityContext} cho request hiện tại.
 *
 * <p>Giải thích cho người quen PHP: tương đương một middleware trong Laravel
 * ({@code app/Http/Middleware}). Nó chạy TRƯỚC controller ở mọi request.
 *
 * <p>Điểm quan trọng: nếu token thiếu hoặc sai, bộ lọc KHÔNG tự trả lỗi mà cứ cho request
 * đi tiếp. Việc chặn do cấu hình {@code SecurityConfig} quyết định, nhờ vậy các endpoint
 * công khai (đăng nhập, Swagger) vẫn hoạt động bình thường.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            AppUserDetails userDetails = jwtService.parseAccessToken(token);
            if (userDetails != null) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Lấy token từ header, hoặc từ tham số {@code ?token=} khi là kết nối SSE.
     *
     * <p>Thư viện EventSource gốc của trình duyệt không gửi được header, nên frontend dùng
     * {@code @microsoft/fetch-event-source}. Tham số query chỉ để dự phòng.
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        if (request.getRequestURI().startsWith("/api/notifications/stream")) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }
        return null;
    }
}
