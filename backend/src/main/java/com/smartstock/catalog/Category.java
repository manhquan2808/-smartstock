package com.smartstock.catalog;

import com.smartstock.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Loại hàng (ví dụ: Nước giải khát, Bánh kẹo, Gia vị).
 *
 * <p>Dùng XÓA MỀM: khi loại hàng đã có sản phẩm phát sinh giao dịch, ta chỉ đóng dấu
 * {@code deletedAt} thay vì xóa hẳn, để các phiếu cũ vẫn hiển thị đầy đủ thông tin.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends AuditableEntity {

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Khác null nghĩa là đã bị xóa mềm. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
