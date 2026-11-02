package com.smartstock.audit;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Tra cứu nhật ký theo người thực hiện, loại hành động và khoảng thời gian. */
    @Query(
            """
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:fromDate IS NULL OR a.createdAt >= :fromDate)
              AND (:toDate IS NULL OR a.createdAt <= :toDate)
              AND (:keyword IS NULL OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(a.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<AuditLog> search(
            @Param("userId") Long userId,
            @Param("action") AuditAction action,
            @Param("entityType") String entityType,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("keyword") String keyword,
            Pageable pageable);
}
