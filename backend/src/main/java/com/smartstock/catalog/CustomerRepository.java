package com.smartstock.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn khách hàng. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(String code, Long id);

    List<Customer> findAllByDeletedAtIsNullOrderByNameAsc();

    @Query(
            """
            SELECT c FROM Customer c
            WHERE c.deletedAt IS NULL
              AND (:keyword IS NULL OR LOWER(c.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                    OR LOWER(COALESCE(c.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Customer> search(@Param("keyword") String keyword, Pageable pageable);
}
