package com.smartstock.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn nhà cung cấp. */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(String code, Long id);

    List<Supplier> findAllByDeletedAtIsNullOrderByNameAsc();

    @Query(
            """
            SELECT s FROM Supplier s
            WHERE s.deletedAt IS NULL
              AND (:keyword IS NULL OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Supplier> search(@Param("keyword") String keyword, Pageable pageable);
}
