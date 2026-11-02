package com.smartstock.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * Lớp cha cho các entity nghiệp vụ cần ghi lại NGƯỜI tạo và NGƯỜI sửa cuối.
 *
 * <p>Giá trị được lấy tự động từ người đang đăng nhập, xem {@code JpaAuditingConfig}.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
