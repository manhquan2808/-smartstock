package com.smartstock.audit;

import com.smartstock.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API nhật ký hoạt động - chỉ ADMIN được xem. */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "13. Nhật ký hoạt động", description = "Truy vết thao tác của người dùng (chỉ ADMIN)")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @Operation(summary = "Tra cứu nhật ký hoạt động")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        // Chuyển ngày thành khoảng thời gian trọn vẹn: 00:00:00 đến 23:59:59.999999
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.atTime(LocalTime.MAX);

        return PageResponse.from(
                auditLogRepository.search(
                        userId,
                        action,
                        emptyToNull(entityType),
                        from,
                        to,
                        emptyToNull(keyword),
                        pageable),
                AuditLogResponse::from);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Dữ liệu nhật ký trả về cho giao diện. */
    public record AuditLogResponse(
            Long id,
            Long userId,
            String username,
            AuditAction action,
            String actionName,
            String entityType,
            Long entityId,
            String description,
            String beforeJson,
            String afterJson,
            String ipAddress,
            LocalDateTime createdAt) {

        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getUserId(),
                    log.getUsername(),
                    log.getAction(),
                    log.getAction().getDisplayName(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getDescription(),
                    log.getBeforeJson(),
                    log.getAfterJson(),
                    log.getIpAddress(),
                    log.getCreatedAt());
        }
    }
}
