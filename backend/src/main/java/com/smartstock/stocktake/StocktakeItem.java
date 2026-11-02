package com.smartstock.stocktake;

import com.smartstock.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một dòng kiểm kê: một sản phẩm cần đếm.
 *
 * <ul>
 *   <li>{@code systemQuantity}: tồn hệ thống, chụp lại lúc TẠO đợt kiểm kê.</li>
 *   <li>{@code actualQuantity}: số đếm thực tế, null nghĩa là chưa đếm.</li>
 *   <li>{@code unitCost}: giá vốn lúc tạo đợt, dùng tính giá trị chênh lệch.</li>
 * </ul>
 */
@Entity
@Table(name = "stocktake_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StocktakeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "line_no", nullable = false)
    @Builder.Default
    private Integer lineNo = 1;

    @Column(name = "system_quantity", nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal systemQuantity = BigDecimal.ZERO;

    /** Null = nhân viên chưa đếm sản phẩm này. */
    @Column(name = "actual_quantity", precision = 18, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "note", length = 500)
    private String note;

    /** Chênh lệch = thực tế - hệ thống. Trả 0 khi chưa đếm. */
    public BigDecimal getDifference() {
        if (actualQuantity == null) {
            return BigDecimal.ZERO;
        }
        return actualQuantity.subtract(systemQuantity);
    }

    /** Giá trị chênh lệch = chênh lệch x giá vốn (âm nghĩa là thiếu hàng, tổn thất). */
    public BigDecimal getDifferenceValue() {
        return getDifference().multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isCounted() {
        return actualQuantity != null;
    }
}
