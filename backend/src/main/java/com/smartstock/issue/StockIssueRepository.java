package com.smartstock.issue;

import com.smartstock.document.DocumentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn phiếu xuất kho. */
public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {

    @EntityGraph(
            attributePaths = {
                "items",
                "items.product",
                "items.product.unit",
                "warehouse",
                "customer",
                "supplier",
                "approvedBy",
                "cancelledBy"
            })
    Optional<StockIssue> findWithItemsById(Long id);

    Optional<StockIssue> findByCode(String code);

    @Query(
            value =
                    """
                    SELECT i FROM StockIssue i
                      JOIN FETCH i.warehouse w
                      LEFT JOIN FETCH i.customer c
                      LEFT JOIN FETCH i.supplier s
                    WHERE (:allWarehouses = TRUE OR w.id IN (:warehouseIds))
                      AND (:issueType IS NULL OR i.issueType = :issueType)
                      AND (:status IS NULL OR i.status = :status)
                      AND (:fromDate IS NULL OR i.documentDate >= :fromDate)
                      AND (:toDate IS NULL OR i.documentDate <= :toDate)
                      AND (:createdBy IS NULL OR i.createdBy = :createdBy)
                      AND (:keyword IS NULL OR LOWER(i.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """,
            countQuery =
                    """
                    SELECT COUNT(i) FROM StockIssue i
                    WHERE (:allWarehouses = TRUE OR i.warehouse.id IN (:warehouseIds))
                      AND (:issueType IS NULL OR i.issueType = :issueType)
                      AND (:status IS NULL OR i.status = :status)
                      AND (:fromDate IS NULL OR i.documentDate >= :fromDate)
                      AND (:toDate IS NULL OR i.documentDate <= :toDate)
                      AND (:createdBy IS NULL OR i.createdBy = :createdBy)
                      AND (:keyword IS NULL OR LOWER(i.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """)
    Page<StockIssue> search(
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("issueType") IssueType issueType,
            @Param("status") DocumentStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("createdBy") Long createdBy,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(
            """
            SELECT COUNT(i) FROM StockIssue i
            WHERE i.status = com.smartstock.document.DocumentStatus.PENDING
              AND (:allWarehouses = TRUE OR i.warehouse.id IN (:warehouseIds))
            """)
    long countPending(
            @Param("allWarehouses") boolean allWarehouses, @Param("warehouseIds") List<Long> warehouseIds);

    @Query(
            """
            SELECT COUNT(i) FROM StockIssue i
            WHERE i.status = com.smartstock.document.DocumentStatus.COMPLETED
              AND i.documentDate >= :fromDate AND i.documentDate <= :toDate
              AND (:allWarehouses = TRUE OR i.warehouse.id IN (:warehouseIds))
            """)
    long countCompletedBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds);

    /** Mã các phiếu xuất hoàn tất tại một kho sau mốc thời gian (cảnh báo khi đang kiểm kê). */
    @Query(
            """
            SELECT i.code FROM StockIssue i
            WHERE i.warehouse.id = :warehouseId
              AND i.status = com.smartstock.document.DocumentStatus.COMPLETED
              AND i.approvedAt >= :since
            ORDER BY i.approvedAt
            """)
    List<String> findCompletedCodesSince(
            @Param("warehouseId") Long warehouseId, @Param("since") java.time.LocalDateTime since);
}
