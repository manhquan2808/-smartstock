package com.smartstock.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.config.AppProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cấu hình bảo mật trung tâm: ai được gọi endpoint nào, mã hóa mật khẩu, CORS.
 *
 * <p>Những quyết định chính:
 *
 * <ul>
 *   <li><b>Không dùng session</b> ({@code STATELESS}): mỗi request tự mang access token.
 *       Nhờ vậy backend có thể chạy nhiều bản song song mà không cần chia sẻ session.</li>
 *   <li><b>Tắt CSRF</b>: an toàn ở đây vì API xác thực bằng header Bearer chứ không
 *       dựa vào cookie cho các thao tác ghi. Endpoint {@code /api/auth/refresh} dùng
 *       cookie nhưng chỉ xoay token, không thay đổi dữ liệu nghiệp vụ.</li>
 *   <li><b>BCrypt</b> cho mật khẩu: thuật toán băm chậm có muối, chống dò bảng tra.</li>
 *   <li><b>Phân quyền hai lớp</b>: lớp URL ở đây + lớp {@code @PreAuthorize} trên từng
 *       phương thức service/controller + kiểm tra kho được gán trong service.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // bật @PreAuthorize trên các phương thức
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppUserDetailsService userDetailsService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    /** Thuật toán băm mật khẩu. Cost 10 là mức cân bằng giữa an toàn và tốc độ. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Giữ nguyên thông báo lỗi chung "sai tên đăng nhập hoặc mật khẩu" để không
        // tiết lộ tên đăng nhập nào tồn tại trong hệ thống
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ---------- Endpoint công khai ----------
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password")
                        .permitAll()
                        // Tài liệu API và kiểm tra sức khỏe dịch vụ
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info")
                        .permitAll()
                        // Ảnh sản phẩm / ảnh đại diện đã tải lên: cho phép xem công khai
                        .requestMatchers(HttpMethod.GET, "/uploads/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        // ---------- Nhóm endpoint chỉ dành cho ADMIN ----------
                        .requestMatchers("/api/users/**", "/api/audit-logs/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/warehouses")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/warehouses/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/warehouses/**")
                        .hasRole("ADMIN")
                        // ---------- Mọi endpoint còn lại đều phải đăng nhập ----------
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        // Chưa đăng nhập -> 401 kèm JSON (không chuyển hướng tới trang login HTML)
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                        "Bạn chưa đăng nhập hoặc phiên đã hết hạn."))
                        // Đã đăng nhập nhưng không đủ quyền -> 403
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "Bạn không có quyền thực hiện chức năng này.")))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Ghi lỗi dạng JSON để frontend xử lý thống nhất với các lỗi khác. */
    private void writeError(
            jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                java.util.Map.of(
                        "status", status.value(),
                        "code", code,
                        "message", message));
    }

    /**
     * Cấu hình CORS: chỉ cho phép các origin khai báo trong biến môi trường
     * {@code APP_CORS_ALLOWED_ORIGINS}. Bắt buộc {@code allowCredentials = true}
     * để trình duyệt gửi kèm cookie refresh token.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
