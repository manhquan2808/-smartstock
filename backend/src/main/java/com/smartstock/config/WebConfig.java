package com.smartstock.config;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình phục vụ file tĩnh: cho phép trình duyệt xem ảnh đã tải lên.
 *
 * <p>Ánh xạ đường dẫn web {@code /uploads/**} tới thư mục thật trên đĩa. Ví dụ ảnh lưu
 * tại {@code uploads/products/2026/10/abc.jpg} sẽ xem được ở
 * {@code http://localhost:8080/uploads/products/2026/10/abc.jpg}.
 *
 * <p>Khi dùng Cloudinary, đường dẫn ảnh là URL tuyệt đối tới CDN nên không đi qua đây.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path uploadDirectory =
                Path.of(appProperties.storage().localDir()).toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDirectory + "/")
                // Ảnh không đổi nội dung nên cho trình duyệt lưu đệm 7 ngày
                .setCachePeriod(604800);
    }
}
