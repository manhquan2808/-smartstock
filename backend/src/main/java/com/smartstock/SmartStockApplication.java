package com.smartstock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động của backend SmartStock.
 *
 * <p>Giải thích cho người quen PHP: lớp này tương đương file {@code public/index.php}
 * của Laravel - nơi framework được nạp lên và bắt đầu nhận request. Khác biệt là
 * Spring Boot tự chạy một web server (Tomcat) nhúng sẵn bên trong, không cần Apache/Nginx.
 *
 * <ul>
 *   <li>{@code @SpringBootApplication}: bật tự động cấu hình và quét các bean trong package con.</li>
 *   <li>{@code @EnableAsync}: cho phép chạy phương thức nền (gửi email không chặn request).</li>
 *   <li>{@code @EnableScheduling}: cho phép hẹn giờ (email báo cáo tồn thấp 07:00 hằng ngày).</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class SmartStockApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartStockApplication.class, args);
    }
}
