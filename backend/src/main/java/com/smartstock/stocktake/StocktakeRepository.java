package com.smartstock.stocktake;

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

/** Truy vấn phiếu kiểm kê. */
public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {

    @EntityGraph(
            attributePaths = {
                "items",
                "items.product",
                "items.product.unit",
                "warehouse",
                "category",
                "approvedBy",
                "cancelledBy"
            })
    Optional<Stocktake> findWithItemsById(Long id);

    @Query(
            value =
                    """
                    SELECT s FROM Stocktake s
                      JOIN FETCH s.warehouse w
                      LEFT JOIN FETCH s.category c
                      LEFT JOIN FETCH s.approvedBy a
                    WHERE (:allWarehouses = TRUE OR w.id IN (:warehouseIds))
                      AND (:status IS NULL OR s.status = :status)
                      AND (:fromDate IS NULL OR s.documentDate >= :fromDate)
                      AND (:toDate IS NULL OR s.documentDate <= :toDate)
                    """,
            countQuery =
                    """
                    SELECT COUNT(s) FROM Stocktake s
                    WHERE (:allWarehouses = TRUE OR s.warehouse.id IN (:warehouseIds))
                      AND (:status IS NULL OR s.status = :status)
                      AND (:fromDate IS NULL OR s.documentDate >= :fromDate)
                      AND (:toDate IS NULL OR s.documentDate <= :toDate)
                    """)
    Page<Stocktake> search(
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("status") DocumentStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    /**
     * Kho có đợt kiểm kê nào đang mở (chưa duyệt, chưa hủy) hay không.
     *
     * <p>Dùng để chặn tạo hai đợt song song trên cùng một kho - nếu không, hai đợt sẽ
     * điều chỉnh tồn chồng lên nhau và không ai biết số nào đúng.
     */
    @Query(
            """
            SELECT COUNT(s) > 0 FROM Stocktake s
            WHERE s.warehouse.id = :warehouseId
              AND s.status IN (com.smartstock.document.DocumentStatus.DRAFT,
                               com.smartstock.document.DocumentStatus.PENDING,
                               com.smartstock.document.DocumentStatus.REJECTED)
            """)
    boolean existsOpenStocktake(@Param("warehouseId") Long warehouseId);

    long countByStatus(DocumentStatus status);
}
