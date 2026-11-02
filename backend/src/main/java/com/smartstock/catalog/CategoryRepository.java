package com.smartstock.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn loại hàng. Luôn bỏ qua bản ghi đã xóa mềm. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndDeletedAtIsNull(Long id);

    Optional<Category> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(String code, Long id);

    List<Category> findAllByDeletedAtIsNullOrderByNameAsc();

    @Query(
            """
            SELECT c FROM Category c
            WHERE c.deletedAt IS NULL
              AND (:keyword IS NULL OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Category> search(@Param("keyword") String keyword, Pageable pageable);
}
