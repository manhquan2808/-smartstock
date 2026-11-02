package com.smartstock.notification;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Kênh thông báo realtime bằng Server-Sent Events (SSE).
 *
 * <p><b>SSE là gì:</b> trình duyệt mở MỘT kết nối HTTP và giữ nó mở; máy chủ đẩy dữ liệu
 * xuống bất cứ lúc nào. Khác WebSocket, SSE chỉ truyền một chiều (máy chủ → trình duyệt),
 * đúng nhu cầu của thông báo và đơn giản hơn nhiều.
 *
 * <p><b>Cách lưu kết nối:</b> một người dùng có thể mở nhiều tab, nên mỗi userId ánh xạ
 * tới một DANH SÁCH emitter. Dùng {@code ConcurrentHashMap} và {@code CopyOnWriteArrayList}
 * vì nhiều luồng request cùng đọc/ghi cấu trúc này.
 *
 * <p><b>Lưu ý khi triển khai:</b> bộ nhớ này nằm trong từng tiến trình backend. Nếu chạy
 * nhiều bản backend song song, thông báo chỉ tới được các tab đang kết nối vào đúng bản
 * đó - khi cần mở rộng thì bổ sung Redis Pub/Sub. Phạm vi đồ án chạy một bản nên không
 * cần thiết, và giao diện vẫn có đường dự phòng là tải lại số đếm khi chuyển trang.
 */
@Slf4j
@Service
public class NotificationStreamService {

    /** Thời gian sống của một kết nối SSE: 30 phút, sau đó trình duyệt tự kết nối lại. */
    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /**
     * Mở một kết nối SSE cho người dùng.
     *
     * @return đối tượng emitter để controller trả về cho trình duyệt
     */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        emittersByUser
                .computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>())
                .add(emitter);

        // Dọn emitter khỏi danh sách khi kết nối kết thúc, tránh rò rỉ bộ nhớ
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(throwable -> remove(userId, emitter));

        try {
            // Gửi ngay một sự kiện chào để trình duyệt biết kết nối đã sẵn sàng
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "Đã kết nối kênh thông báo")));
        } catch (IOException ex) {
            remove(userId, emitter);
            log.debug("Không gửi được sự kiện chào tới người dùng {}: {}", userId, ex.getMessage());
        }

        log.debug("Người dùng {} mở kênh thông báo, tổng số kết nối: {}", userId, countConnections());
        return emitter;
    }

    /** Đẩy một thông báo tới mọi tab đang mở của người dùng. */
    public void push(Long userId, Object payload) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return; // người dùng không online: thông báo đã lưu trong CSDL, sẽ thấy khi vào lại
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (IOException | IllegalStateException ex) {
                // Kết nối đã đóng phía trình duyệt: loại bỏ khỏi danh sách
                remove(userId, emitter);
            }
        }
    }

    /**
     * Gửi tín hiệu "còn sống" mỗi 25 giây.
     *
     * <p>Nhiều proxy (Render, Nginx) tự đóng kết nối không có dữ liệu sau khoảng 30-60 giây.
     * Một dòng bình luận định kỳ giúp kết nối SSE không bị cắt.
     */
    @Scheduled(fixedDelay = 25_000)
    public void sendHeartbeat() {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException ex) {
                    remove(userId, emitter);
                }
            }
        });
    }

    /** Tổng số kết nối đang mở (dùng khi theo dõi vận hành). */
    public int countConnections() {
        return emittersByUser.values().stream().mapToInt(List::size).sum();
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByUser.remove(userId);
            }
        }
    }
}
