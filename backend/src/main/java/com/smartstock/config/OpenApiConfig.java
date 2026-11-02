package com.smartstock.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tài liệu API (Swagger UI tại {@code /swagger-ui.html}).
 *
 * <p>Khai báo scheme "bearerAuth" để trên Swagger UI có nút Authorize: dán access token
 * vào đó là gọi thử được các API cần đăng nhập.
 */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "SmartStock API",
                        version = "1.0.0",
                        description =
                                """
                                API hệ thống quản lý kho đa chi nhánh SmartStock.

                                Cách dùng nhanh:
                                1. Gọi POST /api/auth/login với tài khoản demo (admin / Admin@123).
                                2. Copy giá trị accessToken trong kết quả.
                                3. Bấm nút Authorize ở góc trên và dán token vào.
                                """,
                        contact = @Contact(name = "Nhóm phát triển SmartStock")),
        servers = {@Server(url = "/", description = "Máy chủ hiện tại")},
        security = {@SecurityRequirement(name = "bearerAuth")})
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "Dán access token lấy từ API đăng nhập")
public class OpenApiConfig {}
