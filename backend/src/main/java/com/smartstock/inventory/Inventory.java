package com.smartstock.inventory;

import com.smartstock.catalog.Product;
import com.smartstock.common.BaseEntity;
import com.smartstock.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tồn kho của MỘT sản phẩm tại MỘT kho.
 *
 * <p>Quy tắc bất di bất dịch của dự án: chỉ {@code InventoryService} được phép sửa
 * {@code quantity} và {@code avgCost}. Không service nào khác được ghi trực tiếp vào
 * bảng này - nhờ vậy mọi thay đổi tồn luôn kèm một dòng trong sổ giao dịch kho.
 *
 * <p>{@code @Version} bật khóa lạc quan: nếu hai giao dịch cùng sửa một dòng, giao dịch
 * sau sẽ nhận lỗi thay vì ghi đè âm thầm. Bên cạnh đó service còn dùng khóa bi quan
 * ({@code PESSIMISTIC_WRITE}) để xếp hàng các thao tác ghi.
 */
@Entity
@Table(
        name = "inventories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventories_product_warehouse",
                columnNames = {"product_id", "warehouse_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Số tồn hiện tại, không bao giờ âm (có ràng buộc CHECK ở CSDL). */
    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Giá vốn bình quân gia quyền của sản phẩm tại kho này. */
    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /** Giá trị tồn = số lượng x giá vốn bình quân. */
    public BigDecimal getStockValue() {
        return quantity.multiply(avgCost).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
