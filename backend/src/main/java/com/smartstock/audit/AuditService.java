package com.smartstock.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi nhật ký hoạt động.
 *
 * <p>Điểm thiết kế quan trọng: mỗi lần ghi nhật ký chạy trong GIAO DỊCH RIÊNG
 * ({@code Propagation.REQUIRES_NEW}). Lý do:
 *
 * <ul>
 *   <li>Đăng nhập thất bại cần được ghi lại dù không có giao dịch nghiệp vụ nào.</li>
 *   <li>Nếu giao dịch nghiệp vụ bị rollback (ví dụ xuất vượt tồn), ta vẫn muốn giữ
 *       dấu vết "người này đã thử làm việc đó".</li>
 *   <li>Ngược lại, lỗi khi ghi nhật ký KHÔNG được làm hỏng nghiệp vụ chính - nên mọi
 *       lỗi ở đây chỉ ghi log cảnh báo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /** Ghi một sự kiện đơn giản (không kèm dữ liệu trước/sau). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String entityType, Long entityId, String description) {
        AppUserDetails current = SecurityUtils.getCurrentUserOrNull();
        save(AuditLog.builder()
                .userId(current == null ? null : current.getUserId())
                .username(current == null ? null : current.getUsername())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .ipAddress(resolveClientIp())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Ghi sự kiện thay đổi dữ liệu kèm trạng thái trước và sau.
     *
     * @param before đối tượng trước khi đổi (null khi thêm mới)
     * @param after đối tượng sau khi đổi (null khi xóa)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChange(
            AuditAction action, String entityType, Long entityId, String description, Object before, Object after) {
        AppUserDetails current = SecurityUtils.getCurrentUserOrNull();
        save(AuditLog.builder()
                .userId(current == null ? null : current.getUserId())
                .username(current == null ? null : current.getUsername())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .beforeJson(toJson(before))
                .afterJson(toJson(after))
                .ipAddress(resolveClientIp())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Ghi sự kiện đăng nhập, kể cả khi tài khoản không tồn tại (userId = null). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthentication(AuditAction action, Long userId, String username, String description) {
        save(AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .entityType("User")
                .entityId(userId)
                .description(description)
                .ipAddress(resolveClientIp())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /**
     * Lưu một dòng nhật ký. Là phương thức private nên giao dịch REQUIRES_NEW phải được
     * khai báo ở các phương thức công khai phía trên: gọi nội bộ (this.save) không đi qua
     * proxy của Spring nên annotation đặt tại đây sẽ không có hiệu lực.
     */
    private void save(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // Không bao giờ để lỗi ghi nhật ký làm hỏng nghiệp vụ chính
            log.warn("Không ghi được nhật ký hoạt động ({}): {}", entry.getAction(), ex.getMessage());
        }
    }

    /** Chuyển đối tượng sang JSON để lưu; lỗi chuyển đổi không được làm gián đoạn nghiệp vụ. */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            // Giới hạn độ dài để một bản ghi bất thường không làm phình bảng nhật ký
            return json.length() > 8000 ? json.substring(0, 8000) + "...(đã cắt)" : json;
        } catch (Exception ex) {
            log.debug("Không chuyển được dữ liệu sang JSON để ghi nhật ký: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Lấy địa chỉ IP của người gọi.
     *
     * <p>Khi chạy sau proxy (Render, Netlify) thì IP thật nằm trong header
     * {@code X-Forwarded-For}, phần tử đầu tiên là client gốc.
     */
    private String resolveClientIp() {
        try {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                return null; // tác vụ hẹn giờ hoặc seed: không có request
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
