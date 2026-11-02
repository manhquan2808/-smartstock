package com.smartstock.inventory;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn tồn kho.
 *
 * <p>Quy ước lọc theo kho dùng chung cho toàn dự án: mỗi truy vấn nhận một cặp tham số
 * {@code allWarehouses} + {@code warehouseIds}.
 *
 * <ul>
 *   <li>{@code allWarehouses = true}: không lọc theo kho (trường hợp ADMIN xem tất cả).</li>
 *   <li>{@code allWarehouses = false}: chỉ lấy dữ liệu của các kho trong {@code warehouseIds}.</li>
 * </ul>
 *
 * <p>Cách này tránh phải truyền danh sách rỗng vào mệnh đề {@code IN} (gây lỗi SQL ở một
 * số cơ sở dữ liệu). Lớp {@code WarehouseScope} lo việc dựng cặp tham số này.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Lấy dòng tồn và KHÓA GHI dòng đó cho tới khi giao dịch kết thúc.
     *
     * <p>{@code PESSIMISTIC_WRITE} sinh câu lệnh {@code SELECT ... FOR UPDATE}: giao dịch
     * khác muốn đọc/ghi cùng dòng này phải xếp hàng chờ. Đây là cách hệ thống bảo đảm
     * hai phiếu xuất cùng lúc không thể cùng trừ trên một số tồn đã cũ.
     *
     * <p>Chỉ dùng trong {@code InventoryService} và luôn gọi theo thứ tự product_id tăng
     * dần để tránh deadlock (hai giao dịch khóa chéo nhau).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.warehouse.id = :warehouseId")
    Optional<Inventory> findForUpdate(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    /** Đọc tồn mà KHÔNG khóa dòng, dùng cho các truy vấn chỉ xem. */
    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    boolean existsByWarehouseIdAndQuantityGreaterThan(Long warehouseId, BigDecimal quantity);

    /** Dùng khi chặn xóa sản phẩm còn tồn ở bất kỳ kho nào. */
    boolean existsByProductIdAndQuantityGreaterThan(Long productId, BigDecimal quantity);

    List<Inventory> findAllByProductId(Long productId);

    /**
     * Danh sách tồn kho có phân trang, lọc theo kho / loại hàng / tình trạng tồn.
     *
     * <p>{@code stockState}: ALL (tất cả), IN_STOCK (còn hàng), LOW (sắp hết: tồn &gt; 0
     * và &le; mức tối thiểu), OUT (hết hàng).
     *
     * <p>Dùng {@code JOIN FETCH} để lấy sẵn sản phẩm, đơn vị tính, loại hàng và kho trong
     * một truy vấn, tránh lỗi N+1 khi hiển thị danh sách lớn.
     */
    @Query(
            value =
                    """
                    SELECT i FROM Inventory i
                      JOIN FETCH i.product p
                      JOIN FETCH p.unit u
                      JOIN FETCH p.category c
                      JOIN FETCH i.warehouse w
                    WHERE p.deletedAt IS NULL
                      AND (:allWarehouses = TRUE OR w.id IN (:warehouseIds))
                      AND (:categoryId IS NULL OR c.id = :categoryId)
                      AND (:keyword IS NULL OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:stockState = 'ALL'
                           OR (:stockState = 'IN_STOCK' AND i.quantity > p.minStock)
                           OR (:stockState = 'LOW' AND i.quantity > 0 AND i.quantity <= p.minStock)
                           OR (:stockState = 'OUT' AND i.quantity <= 0))
                    """,
            countQuery =
                    """
                    SELECT COUNT(i) FROM Inventory i
                      JOIN i.product p
                      JOIN i.warehouse w
                    WHERE p.deletedAt IS NULL
                      AND (:allWarehouses = TRUE OR w.id IN (:warehouseIds))
                      AND (:categoryId IS NULL OR p.category.id = :categoryId)
                      AND (:keyword IS NULL OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:stockState = 'ALL'
                           OR (:stockState = 'IN_STOCK' AND i.quantity > p.minStock)
                           OR (:stockState = 'LOW' AND i.quantity > 0 AND i.quantity <= p.minStock)
                           OR (:stockState = 'OUT' AND i.quantity <= 0))
                    """)
    Page<Inventory> searchInventory(
            @Param("allWarehouses") boolean allWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("stockState") String stockState,
            Pageable pageable);

    /** Các dòng tồn đang ở mức bằng hoặc dưới mức tồn tối thiểu (cảnh báo tồn thấp). */
    @EntityGraph(attributePaths = {"product", "product.unit", "warehouse"})
    @Query(
            """
            SELECT i FROM Inventory i
            WHERE i.product.deletedAt IS NULL
              AND i.product.status = com.smartstock.catalog.RecordStatus.ACTIVE
              AND i.product.minStock > 0
              AND i.quantity <= i.product.minStock
              AND (:allWarehouses = TRUE OR i.warehouse.id IN (:warehouseIds))
            ORDER BY i.quantity ASC
            """)
    List<Inventory> findLowStock(
            @Param("allWarehouses") boolean allWarehouses, @Param("warehouseIds") List<Long> warehouseIds);

    /** Tổng giá trị tồn (dùng cho thẻ chỉ số trên dashboard). */
    @Query(
            """
            SELECT COALESCE(SUM(i.quantity * i.avgCost), 0) FROM Inventory i
            WHERE i.product.deletedAt IS NULL
              AND (:allWarehouses = TRUE OR i.warehouse.id IN (:warehouseIds))
            """)
    BigDecimal sumStockValue(
            @Param("allWarehouses") boolean allWarehouses, @Param("warehouseIds") List<Long> warehouseIds);

    /** Số mặt hàng đang có tồn lớn hơn 0. */
    @Query(
            """
            SELECT COUNT(DISTINCT i.product.id) FROM Inventory i
            WHERE i.product.deletedAt IS NULL AND i.quantity > 0
              AND (:allWarehouses = TRUE OR i.warehouse.id IN (:warehouseIds))
            """)
    long countProductsInStock(
            @Param("allWarehouses") boolean allWarehouses, @Param("warehouseIds") List<Long> warehouseIds);

    /** Số dòng tồn đang ở mức tồn thấp. */
    @Query(
            """
            SELECT COUNT(i) FROM Inventory i
            WHERE i.product.deletedAt IS NULL
              AND i.product.status = com.smartstock.catalog.RecordStatus.ACTIVE
              AND i.product.minStock > 0
              AND i.quantity <= i.product.minStock
              AND (:allWarehouses = TRUE OR i.warehouse.id IN (:warehouseIds))
            """)
    long countLowStock(
            @Param("allWarehouses") boolean allWarehouses, @Param("warehouseIds") List<Long> warehouseIds);

    /** Tồn kho của một sản phẩm tại tất cả các kho (dùng cho trang chi tiết sản phẩm). */
    @EntityGraph(attributePaths = {"warehouse"})
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId ORDER BY i.warehouse.name")
    List<Inventory> findByProductWithWarehouse(@Param("productId") Long productId);

    /** Toàn bộ dòng tồn của một kho, nạp sẵn sản phẩm - dùng khi tạo phiếu kiểm kê. */
    @EntityGraph(attributePaths = {"product", "product.unit"})
    @Query(
            """
            SELECT i FROM Inventory i
            WHERE i.warehouse.id = :warehouseId
              AND i.product.deletedAt IS NULL
              AND (:categoryId IS NULL OR i.product.category.id = :categoryId)
            ORDER BY i.product.sku
            """)
    List<Inventory> findByWarehouseForStocktake(
            @Param("warehouseId") Long warehouseId, @Param("categoryId") Long categoryId);

    /**
     * Truy vấn ĐỐI CHIẾU BẤT BIẾN: so sánh số tồn với tổng giao dịch trong sổ kho.
     *
     * <p>Trả về các cặp (sản phẩm, kho) bị lệch. Hệ thống đúng thì kết quả luôn rỗng.
     * Có test tự động gọi truy vấn này sau mỗi bộ kịch bản nghiệp vụ.
     */
    @Query(
            """
            SELECT i.product.id, i.warehouse.id, i.quantity,
                   COALESCE((SELECT SUM(t.quantityChange) FROM StockTransaction t
                             WHERE t.product.id = i.product.id AND t.warehouse.id = i.warehouse.id), 0)
            FROM Inventory i
            WHERE i.quantity <> COALESCE((SELECT SUM(t.quantityChange) FROM StockTransaction t
                                          WHERE t.product.id = i.product.id AND t.warehouse.id = i.warehouse.id), 0)
            """)
    List<Object[]> findInventoryMismatches();
}
