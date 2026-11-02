package com.smartstock.document;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn bộ đếm mã phiếu. */
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    /**
     * Lấy bộ đếm và KHÓA GHI dòng đó ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Nhờ khóa này, hai người tạo phiếu cùng lúc sẽ được cấp số lần lượt, không trùng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentSequence s WHERE s.prefix = :prefix AND s.sequenceDate = :date")
    Optional<DocumentSequence> findForUpdate(@Param("prefix") String prefix, @Param("date") LocalDate date);
}
