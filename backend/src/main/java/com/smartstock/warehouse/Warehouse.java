package com.smartstock.warehouse;

import com.smartstock.common.AuditableEntity;
import com.smartstock.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kho / chi nhánh. Mọi số tồn, phiếu nhập, phiếu xuất đều gắn với một kho cụ thể.
 */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Warehouse extends AuditableEntity {

    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    /** Người quản lý chính của kho: nhận email và thông báo khi có phiếu chờ duyệt. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    /** Kho ngừng hoạt động thì không chọn được khi lập phiếu mới. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
