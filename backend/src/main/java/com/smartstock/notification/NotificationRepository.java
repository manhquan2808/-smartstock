package com.smartstock.notification;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** Đánh dấu đã đọc toàn bộ thông báo của một người. */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Dọn thông báo cũ đã đọc (gọi theo lịch hằng tuần). */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.createdAt < :threshold")
    int deleteOldRead(@Param("threshold") LocalDateTime threshold);
}
