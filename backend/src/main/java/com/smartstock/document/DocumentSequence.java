package com.smartstock.document;

import com.smartstock.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bộ đếm số thứ tự phiếu theo từng cặp (tiền tố, ngày).
 *
 * <p>Ví dụ ngày 31/10/2026 có 3 phiếu nhập thì bảng này giữ một dòng
 * {@code (PN, 2026-10-31, 3)} và phiếu tiếp theo sẽ mang mã {@code PN-20261031-004}.
 */
@Entity
@Table(
        name = "document_sequences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_sequences",
                columnNames = {"prefix", "sequence_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSequence extends BaseEntity {

    /** PN = phiếu nhập, PX = phiếu xuất, KK = phiếu kiểm kê. */
    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix;

    @Column(name = "sequence_date", nullable = false)
    private LocalDate sequenceDate;

    @Column(name = "last_number", nullable = false)
    @Builder.Default
    private Integer lastNumber = 0;
}
