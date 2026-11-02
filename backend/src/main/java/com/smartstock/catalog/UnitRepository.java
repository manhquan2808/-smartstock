package com.smartstock.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn đơn vị tính. */
public interface UnitRepository extends JpaRepository<Unit, Long> {

    Optional<Unit> findByIdAndDeletedAtIsNull(Long id);

    Optional<Unit> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(String code, Long id);

    List<Unit> findAllByDeletedAtIsNullOrderByNameAsc();

    @Query(
            """
            SELECT u FROM Unit u
            WHERE u.deletedAt IS NULL
              AND (:keyword IS NULL OR LOWER(u.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Unit> search(@Param("keyword") String keyword, Pageable pageable);
}
