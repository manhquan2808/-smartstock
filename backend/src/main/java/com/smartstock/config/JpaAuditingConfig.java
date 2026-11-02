package com.smartstock.config;

import com.smartstock.security.SecurityUtils;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật tính năng tự điền created_at / updated_at / created_by / updated_by.
 *
 * <p>{@code AuditorAware} cho Spring Data biết "ai đang thao tác" để điền vào
 * {@code created_by} và {@code updated_by}. Khi không có ai đăng nhập (ví dụ lúc
 * chạy seed hoặc tác vụ hẹn giờ) thì trả về rỗng và cột để NULL.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> Optional.ofNullable(SecurityUtils.getCurrentUserIdOrNull());
    }
}
