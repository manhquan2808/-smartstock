package com.smartstock.notification;

import com.smartstock.auth.AuthDtos.MessageResponse;
import com.smartstock.common.PageResponse;
import com.smartstock.notification.NotificationService.NotificationResponse;
import com.smartstock.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API thông báo trong website.
 *
 * <p>Endpoint {@code /api/notifications/stream} là kênh realtime (SSE). Trình duyệt dùng
 * thư viện {@code @microsoft/fetch-event-source} để gọi được kèm header
 * {@code Authorization} - EventSource gốc của trình duyệt không hỗ trợ gửi header.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "12. Thông báo", description = "Thông báo trong web và kênh realtime SSE")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService streamService;

    @GetMapping
    @Operation(summary = "Danh sách thông báo của tôi")
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.listMyNotifications(unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc", description = "Hiển thị trên biểu tượng chuông")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.countMyUnread());
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu một thông báo là đã đọc")
    public MessageResponse markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return new MessageResponse("Đã đánh dấu đã đọc.");
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả là đã đọc")
    public MessageResponse markAllAsRead() {
        int updated = notificationService.markAllAsRead();
        return new MessageResponse("Đã đánh dấu " + updated + " thông báo là đã đọc.");
    }

    /**
     * Mở kênh nhận thông báo realtime.
     *
     * <p>Kết nối được giữ mở 30 phút, có tín hiệu "còn sống" mỗi 25 giây để proxy không
     * cắt kết nối. Trình duyệt tự kết nối lại khi hết hạn.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Kênh thông báo realtime (SSE)",
            description = "Trả về dòng sự kiện text/event-stream. Mỗi thông báo mới là một sự kiện tên 'notification'.")
    public SseEmitter stream() {
        return streamService.subscribe(SecurityUtils.getCurrentUserId());
    }
}
