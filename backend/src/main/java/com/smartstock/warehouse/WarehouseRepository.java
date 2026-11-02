package com.smartstock.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn dữ liệu kho.
 *
 * <p>Giải thích cho người quen PHP: interface này tương đương lớp Repository/Model
 * truy vấn trong Laravel, nhưng ta KHÔNG phải viết thân hàm - Spring Data tự sinh câu
 * SQL từ tên phương thức (ví dụ {@code findByCode} -> {@code WHERE code = ?}).
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Warehouse> findAllByActiveTrueOrderByNameAsc();

    /**
     * Tìm kiếm có phân trang theo mã hoặc tên kho.
     *
     * <p>{@code :keyword} là tham số hóa (không nối chuỗi) nên an toàn trước SQL Injection.
     */
    @Query(
            """
            SELECT w FROM Warehouse w
            WHERE (:keyword IS NULL OR LOWER(w.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                     OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:active IS NULL OR w.active = :active)
            """)
    Page<Warehouse> search(
            @Param("keyword") String keyword, @Param("active") Boolean active, Pageable pageable);

    /** Danh sách kho được gán cho một người dùng (dùng cho bộ chọn kho ở header). */
    @Query(
            """
            SELECT w FROM User u JOIN u.warehouses w
            WHERE u.id = :userId AND w.active = true
            ORDER BY w.name
            """)
    List<Warehouse> findAssignedWarehouses(@Param("userId") Long userId);
}
