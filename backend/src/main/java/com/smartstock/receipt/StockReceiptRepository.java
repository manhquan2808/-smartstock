package com.smartstock.receipt;

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

/** Truy vấn phiếu nhập kho. */
public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    /**
     * Lấy phiếu kèm toàn bộ dòng hàng, sản phẩm và các thông tin liên quan.
     *
     * <p>Dùng {@code @EntityGraph} để Hibernate JOIN FETCH sẵn, tránh N+1 khi hiển thị
     * chi tiết phiếu có nhiều dòng.
     */
    @EntityGraph(
            attributePaths = {
                "items",
                "items.product",
                "items.product.unit",
                "warehouse",
                "supplier",
                "customer",
                "approvedBy",
                "cancelledBy"
            })
    Optional<StockReceipt> findWithItemsById(Long id);

    Optional<StockReceipt> findByCode(String code);

    /**
     * Danh sách phiếu nhập có phân trang.
     *
     * <p>Lọc theo kho (bắt buộc theo quyền), loại phiếu, trạng thái, khoảng ngày chứng từ,
     * người lập, và tìm theo mã phiếu.
     */
    @Query(
            value =
                    """
                    SELECT r FROM StockReceipt r
                      JOIN FETCH r.warehouse w
                      LEFT JOIN FETCH r.supplier s
                      LEFT JOIN FETCH r.customer c
                    WHERE (:allWarehouses = TRUE OR w.id IN (:warehouseIds))
                      AND (:receiptType IS NULL OR r.receiptType = :receiptType)
                      AND (:status IS NULL OR r.status = :status)
                      AND (:fromDate IS NULL OR r.documentDate >= :fromDate)
                      AND (:toDate IS NULL OR r.documentDate <= :toDate)
                      AND (:createdBy IS NULL OR r.createdBy = :createdBy)
                      AND (:keyword IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """,
            countQuery =
                    """
                    SELECT COUNT(r) FROM StockReceipt r
                    WHERE (:allWarehouses = TRUE OR r.warehouse.id IN (:warehouseIds))
                      AND (:receiptType IS NULL OR r.receiptType = :receiptType)
                      AND (:status IS NULL OR r.status = :status)
                      AND (:fromDate IS NULL OR r.documentDate >= :fromDate)
                      AND (:toDate IS NULL OR r.documentDate <= :toDate)
                      AND (:createdBy IS NULL OR r.createdBy = :createdBy)
                      AND (:keyword IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """)
    Page<StockReceipt> search(
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("receiptType") ReceiptType receiptType,
            @Param("status") DocumentStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("createdBy") Long createdBy,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** Số phiếu đang chờ duyệt (thẻ chỉ số trên dashboard). */
    @Query(
            """
            SELECT COUNT(r) FROM StockReceipt r
            WHERE r.status = com.smartstock.document.DocumentStatus.PENDING
              AND (:allWarehouses = TRUE OR r.warehouse.id IN (:warehouseIds))
            """)
    long countPending(
            @Param("allWarehouses") boolean allWarehouses, @Param("warehouseIds") List<Long> warehouseIds);

    /** Số phiếu nhập hoàn tất trong khoảng thời gian (thẻ chỉ số trên dashboard). */
    @Query(
            """
            SELECT COUNT(r) FROM StockReceipt r
            WHERE r.status = com.smartstock.document.DocumentStatus.COMPLETED
              AND r.documentDate >= :fromDate AND r.documentDate <= :toDate
              AND (:allWarehouses = TRUE OR r.warehouse.id IN (:warehouseIds))
            """)
    long countCompletedBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds);

    /**
     * Phiếu nhập đã hoàn tất tại một kho sau một mốc thời gian.
     *
     * <p>Dùng để cảnh báo khi có phiếu hoàn tất trong lúc một đợt kiểm kê đang mở - lúc đó
     * số tồn hệ thống đã chụp lúc tạo đợt không còn khớp thực tế nữa.
     */
    @Query(
            """
            SELECT r.code FROM StockReceipt r
            WHERE r.warehouse.id = :warehouseId
              AND r.status = com.smartstock.document.DocumentStatus.COMPLETED
              AND r.approvedAt >= :since
            ORDER BY r.approvedAt
            """)
    List<String> findCompletedCodesSince(
            @Param("warehouseId") Long warehouseId, @Param("since") java.time.LocalDateTime since);
}
