package com.smartstock.inventory;

import com.smartstock.inventory.projection.DailyFlowRow;
import com.smartstock.inventory.projection.DailyQuantityRow;
import com.smartstock.inventory.projection.InventoryReportRow;
import com.smartstock.inventory.projection.LastIssueRow;
import com.smartstock.inventory.projection.ProductQuantityRow;
import com.smartstock.inventory.projection.TopProductRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn sổ giao dịch kho.
 *
 * <p>Đây là nguồn dữ liệu cho thẻ kho, báo cáo nhập - xuất - tồn, biểu đồ dashboard và
 * dự báo nhu cầu nhập hàng.
 *
 * <p><b>Quy ước về truy vấn báo cáo:</b> các truy vấn gộp nhóm trả về {@code Object[]}
 * thay vì dùng cú pháp {@code SELECT new Record(...)}. Lý do: biểu thức
 * {@code SUM(CASE WHEN ...)} kết hợp {@code COALESCE} khiến Hibernate không suy ra chắc
 * chắn kiểu trả về, dẫn tới lỗi "Missing constructor" khi khởi động. Các phương thức
 * {@code default} ngay bên dưới lo việc chuyển mảng thô thành record - tường minh và
 * không phụ thuộc suy luận kiểu.
 */
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    /** Các giao dịch gốc của một phiếu, dùng khi hủy phiếu để sinh giao dịch đảo ngược. */
    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<StockTransaction> findByRefTypeAndRefIdOrderByIdAsc(RefType refType, Long refId);

    /** Kiểm tra một phiếu đã từng bị đảo ngược chưa (tránh hủy hai lần). */
    boolean existsByRefTypeAndRefIdAndType(RefType refType, Long refId, TransactionType type);

    /**
     * THẺ KHO: toàn bộ biến động của một sản phẩm tại một kho trong khoảng thời gian.
     *
     * <p>Sắp xếp theo ngày rồi theo id để thứ tự luôn ổn định khi nhiều giao dịch cùng ngày.
     */
    @EntityGraph(attributePaths = {"product", "warehouse"})
    @Query(
            """
            SELECT t FROM StockTransaction t
            WHERE t.product.id = :productId
              AND t.warehouse.id = :warehouseId
              AND (:fromDate IS NULL OR t.transactionDate >= :fromDate)
              AND (:toDate IS NULL OR t.transactionDate <= :toDate)
            ORDER BY t.transactionDate ASC, t.id ASC
            """)
    List<StockTransaction> findStockCard(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    /** Phân trang thẻ kho (dùng cho sản phẩm có rất nhiều biến động). */
    @EntityGraph(attributePaths = {"product", "warehouse"})
    @Query(
            """
            SELECT t FROM StockTransaction t
            WHERE t.product.id = :productId
              AND (:allWarehouses = TRUE OR t.warehouse.id IN (:warehouseIds))
              AND (:fromDate IS NULL OR t.transactionDate >= :fromDate)
              AND (:toDate IS NULL OR t.transactionDate <= :toDate)
            """)
    Page<StockTransaction> findStockCardPaged(
            @Param("productId") Long productId,
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    /**
     * TỒN ĐẦU KỲ: tổng mọi biến động TRƯỚC ngày bắt đầu kỳ báo cáo.
     *
     * <p>Cách tính này luôn đúng vì sổ giao dịch là nguồn sự thật duy nhất: tồn tại một
     * thời điểm bất kỳ = tổng mọi giao dịch trước thời điểm đó.
     */
    @Query(
            """
            SELECT COALESCE(SUM(t.quantityChange), 0) FROM StockTransaction t
            WHERE t.product.id = :productId
              AND (:allWarehouses = TRUE OR t.warehouse.id IN (:warehouseIds))
              AND t.transactionDate < :fromDate
            """)
    BigDecimal sumQuantityBefore(
            @Param("productId") Long productId,
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("fromDate") LocalDateTime fromDate);

    // ==================================================================
    // Các truy vấn báo cáo (trả về Object[], map bằng phương thức default)
    // ==================================================================

    @Query(
            """
            SELECT p.id, p.sku, p.name, u.name, c.name,
                COALESCE(SUM(CASE WHEN t.transactionDate < :fromDate THEN t.quantityChange ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN t.transactionDate >= :fromDate AND t.transactionDate <= :toDate
                                   AND t.quantityChange > 0 THEN t.quantityChange ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN t.transactionDate >= :fromDate AND t.transactionDate <= :toDate
                                   AND t.quantityChange < 0 THEN -t.quantityChange ELSE 0 END), 0)
            FROM StockTransaction t
              JOIN t.product p
              JOIN p.unit u
              JOIN p.category c
            WHERE t.transactionDate <= :toDate
              AND (:allWarehouses = TRUE OR t.warehouse.id IN (:warehouseIds))
              AND (:categoryId IS NULL OR c.id = :categoryId)
            GROUP BY p.id, p.sku, p.name, u.name, c.name
            ORDER BY p.sku
            """)
    List<Object[]> buildInventoryReportRaw(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("categoryId") Long categoryId);

    /**
     * BÁO CÁO NHẬP - XUẤT - TỒN cho toàn bộ sản phẩm trong một kỳ.
     *
     * <p>Một truy vấn duy nhất trả về tồn đầu kỳ, nhập trong kỳ và xuất trong kỳ của từng
     * sản phẩm. Tồn cuối kỳ được tính trong {@link InventoryReportRow#closingQuantity()}.
     */
    default List<InventoryReportRow> buildInventoryReport(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            boolean allWarehouses,
            List<Long> warehouseIds,
            Long categoryId) {
        return buildInventoryReportRaw(fromDate, toDate, allWarehouses, warehouseIds, categoryId).stream()
                .map(InventoryReportRow::from)
                .toList();
    }

    /**
     * Tổng lượng XUẤT của một sản phẩm trong N ngày gần nhất.
     *
     * <p>Chỉ tính giao dịch loại ISSUE (không tính điều chỉnh kiểm kê, vì đó không phải
     * nhu cầu thật của thị trường).
     */
    @Query(
            """
            SELECT COALESCE(SUM(-t.quantityChange), 0) FROM StockTransaction t
            WHERE t.product.id = :productId
              AND t.warehouse.id = :warehouseId
              AND t.type = com.smartstock.inventory.TransactionType.ISSUE
              AND t.transactionDate >= :fromDate
            """)
    BigDecimal sumIssuedQuantitySince(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId,
            @Param("fromDate") LocalDateTime fromDate);

    @Query(
            """
            SELECT t.product.id, COALESCE(SUM(-t.quantityChange), 0)
            FROM StockTransaction t
            WHERE t.warehouse.id = :warehouseId
              AND t.type = com.smartstock.inventory.TransactionType.ISSUE
              AND t.transactionDate >= :fromDate
            GROUP BY t.product.id
            """)
    List<Object[]> sumIssuedQuantityByProductRaw(
            @Param("warehouseId") Long warehouseId, @Param("fromDate") LocalDateTime fromDate);

    /** Lượng xuất theo từng sản phẩm của một kho trong N ngày (một truy vấn cho cả trang gợi ý). */
    default List<ProductQuantityRow> sumIssuedQuantityByProduct(Long warehouseId, LocalDateTime fromDate) {
        return sumIssuedQuantityByProductRaw(warehouseId, fromDate).stream()
                .map(ProductQuantityRow::from)
                .toList();
    }

    @Query(
            """
            SELECT CAST(t.transactionDate AS date), COALESCE(SUM(-t.quantityChange), 0)
            FROM StockTransaction t
            WHERE t.product.id = :productId
              AND t.warehouse.id = :warehouseId
              AND t.type = com.smartstock.inventory.TransactionType.ISSUE
              AND t.transactionDate >= :fromDate
            GROUP BY CAST(t.transactionDate AS date)
            ORDER BY CAST(t.transactionDate AS date)
            """)
    List<Object[]> findDailyIssuedQuantityRaw(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId,
            @Param("fromDate") LocalDateTime fromDate);

    /** Lượng xuất theo NGÀY của một sản phẩm (vẽ biểu đồ trên trang gợi ý nhập hàng). */
    default List<DailyQuantityRow> findDailyIssuedQuantity(
            Long productId, Long warehouseId, LocalDateTime fromDate) {
        return findDailyIssuedQuantityRaw(productId, warehouseId, fromDate).stream()
                .map(DailyQuantityRow::from)
                .toList();
    }

    @Query(
            """
            SELECT CAST(t.transactionDate AS date),
                COALESCE(SUM(CASE WHEN t.quantityChange > 0 THEN t.quantityChange ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN t.quantityChange < 0 THEN -t.quantityChange ELSE 0 END), 0)
            FROM StockTransaction t
            WHERE t.transactionDate >= :fromDate AND t.transactionDate <= :toDate
              AND (:allWarehouses = TRUE OR t.warehouse.id IN (:warehouseIds))
            GROUP BY CAST(t.transactionDate AS date)
            ORDER BY CAST(t.transactionDate AS date)
            """)
    List<Object[]> findDailyFlowRaw(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds);

    /** Biểu đồ nhập - xuất theo ngày cho dashboard. */
    default List<DailyFlowRow> findDailyFlow(
            LocalDateTime fromDate, LocalDateTime toDate, boolean allWarehouses, List<Long> warehouseIds) {
        return findDailyFlowRaw(fromDate, toDate, allWarehouses, warehouseIds).stream()
                .map(DailyFlowRow::from)
                .toList();
    }

    @Query(
            """
            SELECT p.id, p.sku, p.name, u.name, COALESCE(SUM(-t.quantityChange), 0)
            FROM StockTransaction t JOIN t.product p JOIN p.unit u
            WHERE t.type = com.smartstock.inventory.TransactionType.ISSUE
              AND t.transactionDate >= :fromDate AND t.transactionDate <= :toDate
              AND (:allWarehouses = TRUE OR t.warehouse.id IN (:warehouseIds))
            GROUP BY p.id, p.sku, p.name, u.name
            ORDER BY SUM(-t.quantityChange) DESC
            """)
    List<Object[]> findTopIssuedProductsRaw(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            Pageable pageable);

    /** Top sản phẩm xuất nhiều nhất trong kỳ (dashboard). */
    default List<TopProductRow> findTopIssuedProducts(
            LocalDateTime fromDate,
            LocalDateTime toDate,
            boolean allWarehouses,
            List<Long> warehouseIds,
            Pageable pageable) {
        return findTopIssuedProductsRaw(fromDate, toDate, allWarehouses, warehouseIds, pageable).stream()
                .map(TopProductRow::from)
                .toList();
    }

    @Query(
            """
            SELECT t.product.id, t.warehouse.id, MAX(t.transactionDate)
            FROM StockTransaction t
            WHERE t.type = com.smartstock.inventory.TransactionType.ISSUE
              AND (:allWarehouses = TRUE OR t.warehouse.id IN (:warehouseIds))
            GROUP BY t.product.id, t.warehouse.id
            """)
    List<Object[]> findLastIssueDatesRaw(
            @Param("allWarehouses") boolean allWarehouses, @Param("warehouseIds") List<Long> warehouseIds);

    /**
     * BÁO CÁO HÀNG TỒN LÂU: ngày phát sinh XUẤT gần nhất của từng sản phẩm theo kho.
     *
     * <p>Service so ngày này với ngưỡng N ngày (mặc định 90) để lọc hàng chậm luân chuyển.
     * Sản phẩm chưa từng xuất không xuất hiện ở đây nên service xử lý riêng.
     */
    default List<LastIssueRow> findLastIssueDates(boolean allWarehouses, List<Long> warehouseIds) {
        return findLastIssueDatesRaw(allWarehouses, warehouseIds).stream()
                .map(LastIssueRow::from)
                .toList();
    }
}
