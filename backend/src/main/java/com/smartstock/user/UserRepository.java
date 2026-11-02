package com.smartstock.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn dữ liệu người dùng. */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm theo tên đăng nhập, nạp luôn danh sách kho được gán.
     *
     * <p>{@code @EntityGraph} khiến Hibernate dùng JOIN FETCH để lấy cả kho trong MỘT
     * câu truy vấn. Nếu không có nó, mỗi lần đọc {@code user.getWarehouses()} sẽ sinh
     * thêm một truy vấn - đó chính là lỗi N+1 kinh điển.
     */
    @EntityGraph(attributePaths = "warehouses")
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = "warehouses")
    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "warehouses")
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithWarehouses(@Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Danh sách người dùng có phân trang, lọc theo vai trò / trạng thái / kho. */
    @Query(
            """
            SELECT DISTINCT u FROM User u LEFT JOIN u.warehouses w
            WHERE (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:warehouseId IS NULL OR w.id = :warehouseId)
            """)
    Page<User> search(
            @Param("keyword") String keyword,
            @Param("role") Role role,
            @Param("status") UserStatus status,
            @Param("warehouseId") Long warehouseId,
            Pageable pageable);

    /**
     * Tìm những người cần nhận thông báo "có phiếu chờ duyệt" của một kho:
     * tất cả ADMIN đang hoạt động, cộng các MANAGER được gán kho đó.
     */
    @Query(
            """
            SELECT DISTINCT u FROM User u LEFT JOIN u.warehouses w
            WHERE u.status = com.smartstock.user.UserStatus.ACTIVE
              AND (u.role = com.smartstock.user.Role.ADMIN
                   OR (u.role = com.smartstock.user.Role.MANAGER AND w.id = :warehouseId))
            """)
    List<User> findApproversOfWarehouse(@Param("warehouseId") Long warehouseId);

    /** Tất cả ADMIN và MANAGER đang hoạt động (dùng cho email báo cáo tồn thấp hằng ngày). */
    @Query(
            """
            SELECT DISTINCT u FROM User u
            WHERE u.status = com.smartstock.user.UserStatus.ACTIVE
              AND u.role IN (com.smartstock.user.Role.ADMIN, com.smartstock.user.Role.MANAGER)
            """)
    List<User> findActiveManagersAndAdmins();

    long countByStatus(UserStatus status);
}
