package com.smartstock.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn sản phẩm.
 *
 * <p>Mọi truy vấn đều loại bỏ bản ghi đã xóa mềm ({@code deletedAt IS NULL}), trừ khi
 * tên phương thức nói rõ ngược lại.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Nạp kèm loại hàng và đơn vị tính trong một truy vấn để tránh lỗi N+1. */
    @EntityGraph(attributePaths = {"category", "unit"})
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"category", "unit"})
    Optional<Product> findBySkuIgnoreCaseAndDeletedAtIsNull(String sku);

    /** Tìm theo mã vạch - dùng cho chức năng quét mã bằng camera hoặc máy quét USB. */
    @EntityGraph(attributePaths = {"category", "unit"})
    Optional<Product> findByBarcodeAndDeletedAtIsNull(String barcode);

    boolean existsBySkuIgnoreCaseAndDeletedAtIsNull(String sku);

    boolean existsBySkuIgnoreCaseAndIdNotAndDeletedAtIsNull(String sku, Long id);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    boolean existsByBarcodeAndIdNotAndDeletedAtIsNull(String barcode, Long id);

    boolean existsByCategoryIdAndDeletedAtIsNull(Long categoryId);

    boolean existsByUnitIdAndDeletedAtIsNull(Long unitId);

    long countByDeletedAtIsNull();

    /**
     * Tìm kiếm sản phẩm có phân trang.
     *
     * <p>Tìm theo mã SKU, tên hoặc mã vạch; lọc theo loại hàng và trạng thái kinh doanh.
     * Việc lọc theo tình trạng tồn (còn hàng / sắp hết / hết hàng) được làm ở truy vấn
     * tồn kho ({@code InventoryRepository.searchInventory}) vì nó phụ thuộc vào kho.
     */
    @Query(
            value =
                    """
                    SELECT p FROM Product p JOIN FETCH p.category c JOIN FETCH p.unit u
                    WHERE p.deletedAt IS NULL
                      AND (:keyword IS NULL OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:categoryId IS NULL OR c.id = :categoryId)
                      AND (:status IS NULL OR p.status = :status)
                    """,
            countQuery =
                    """
                    SELECT COUNT(p) FROM Product p
                    WHERE p.deletedAt IS NULL
                      AND (:keyword IS NULL OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                            OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:categoryId IS NULL OR p.category.id = :categoryId)
                      AND (:status IS NULL OR p.status = :status)
                    """)
    Page<Product> search(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("status") RecordStatus status,
            Pageable pageable);

    /**
     * Danh sách gọn để chọn nhanh khi lập phiếu (ô tìm kiếm sản phẩm).
     *
     * <p>Chỉ trả sản phẩm đang kinh doanh, giới hạn số dòng qua {@code Pageable}.
     */
    @Query(
            """
            SELECT p FROM Product p JOIN FETCH p.unit u
            WHERE p.deletedAt IS NULL
              AND p.status = com.smartstock.catalog.RecordStatus.ACTIVE
              AND (:keyword IS NULL OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY p.name
            """)
    List<Product> findSelectable(@Param("keyword") String keyword, Pageable pageable);

    /** Toàn bộ sản phẩm đang kinh doanh (dùng khi tạo phiếu kiểm kê toàn kho và khi seed). */
    @EntityGraph(attributePaths = {"category", "unit"})
    @Query(
            """
            SELECT p FROM Product p
            WHERE p.deletedAt IS NULL
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            ORDER BY p.sku
            """)
    List<Product> findAllActive(@Param("categoryId") Long categoryId);

    /** Lấy nhiều sản phẩm theo id, nạp kèm đơn vị tính - dùng khi lưu phiếu nhiều dòng. */
    @EntityGraph(attributePaths = {"unit", "category"})
    @Query("SELECT p FROM Product p WHERE p.id IN (:ids) AND p.deletedAt IS NULL")
    List<Product> findAllByIdsAndNotDeleted(@Param("ids") List<Long> ids);
}
