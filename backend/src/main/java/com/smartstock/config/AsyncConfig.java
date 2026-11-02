package com.smartstock.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Cấu hình luồng chạy nền cho {@code @Async} (chủ yếu để gửi email).
 *
 * <p>Vì sao cần: gửi email mất 1-3 giây. Nếu gửi ngay trong luồng xử lý request thì
 * người dùng phải chờ, và nếu SMTP lỗi thì giao dịch nghiệp vụ có thể bị rollback oan.
 * Chạy nền giúp "duyệt phiếu" vẫn thành công dù email thất bại.
 *
 * <p>Số luồng đặt nhỏ (2-4) cho phù hợp máy RAM 8 GB.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("smartstock-async-");
        // Khi hàng đợi đầy, luồng gọi tự chạy tác vụ thay vì loại bỏ -> không mất email
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    /** Lỗi trong tác vụ nền chỉ được ghi log, không làm hỏng luồng nghiệp vụ chính. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
