package com.smartstock.notification;

import com.smartstock.common.PageResponse;
import com.smartstock.security.SecurityUtils;
import com.smartstock.user.User;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tạo và đọc thông báo trong website.
 *
 * <p><b>Điểm thiết kế quan trọng:</b> sự kiện realtime (SSE) chỉ được đẩy SAU KHI giao
 * dịch nghiệp vụ commit thành công. Nếu đẩy ngay lúc tạo thông báo, người quản lý có thể
 * nhận chuông "có phiếu chờ duyệt" rồi bấm vào lại không thấy phiếu nào - vì giao dịch
 * sau đó bị rollback. Cơ chế {@code TransactionSynchronizationManager} giúp hoãn việc
 * đẩy tới đúng thời điểm commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationStreamService streamService;

    /**
     * Tạo một thông báo cho một người dùng.
     *
     * @param link đường dẫn frontend để bấm vào xem, ví dụ {@code /receipts/12}
     */
    @Transactional
    public void notifyUser(Long userId, NotificationType type, String title, String content, String link) {
        Notification notification = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .link(link)
                .createdAt(LocalDateTime.now())
                .build());

        pushAfterCommit(userId, notification);
    }

    /** Tạo cùng một thông báo cho nhiều người (ví dụ: mọi quản lý của một kho). */
    @Transactional
    public void notifyUsers(
            List<User> recipients, NotificationType type, String title, String content, String link) {
        for (User recipient : recipients) {
            notifyUser(recipient.getId(), type, title, content, link);
        }
    }

    /** Danh sách thông báo của người đang đăng nhập. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMyNotifications(boolean unreadOnly, Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        var page = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, NotificationResponse::from);
    }

    /** Số thông báo chưa đọc, hiển thị trên biểu tượng chuông. */
    @Transactional(readOnly = true)
    public long countMyUnread() {
        return notificationRepository.countByUserIdAndReadAtIsNull(SecurityUtils.getCurrentUserId());
    }

    /** Đánh dấu một thông báo là đã đọc. Chỉ chủ sở hữu mới đánh dấu được. */
    @Transactional
    public void markAsRead(Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationRepository
                .findById(notificationId)
                .filter(notification -> notification.getUserId().equals(userId))
                .ifPresent(notification -> {
                    if (notification.getReadAt() == null) {
                        notification.setReadAt(LocalDateTime.now());
                        notificationRepository.save(notification);
                    }
                });
    }

    @Transactional
    public int markAllAsRead() {
        return notificationRepository.markAllAsRead(SecurityUtils.getCurrentUserId(), LocalDateTime.now());
    }

    /**
     * Hoãn việc đẩy sự kiện SSE tới sau khi giao dịch commit.
     *
     * <p>Khi không có giao dịch nào đang mở (ví dụ gọi từ tác vụ hẹn giờ) thì đẩy ngay.
     */
    private void pushAfterCommit(Long userId, Notification notification) {
        NotificationResponse payload = NotificationResponse.from(notification);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            streamService.push(userId, payload);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                streamService.push(userId, payload);
            }
        });
    }

    /** Dữ liệu thông báo trả về cho giao diện và đẩy qua SSE. */
    public record NotificationResponse(
            Long id,
            NotificationType type,
            String typeName,
            String title,
            String content,
            String link,
            boolean read,
            LocalDateTime createdAt) {

        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    notification.getType().getDisplayName(),
                    notification.getTitle(),
                    notification.getContent(),
                    notification.getLink(),
                    notification.isRead(),
                    notification.getCreatedAt());
        }
    }
}
