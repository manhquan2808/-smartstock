package com.smartstock.inventory;

import com.smartstock.catalog.Product;
import com.smartstock.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một dòng trong sổ giao dịch kho.
 *
 * <p>Bảng này CHỈ GHI THÊM: không sửa, không xóa. Muốn đảo một giao dịch thì ghi thêm
 * một dòng {@link TransactionType#REVERSAL} với lượng ngược dấu. Nhờ nguyên tắc này:
 *
 * <ul>
 *   <li>Thẻ kho và báo cáo nhập - xuất - tồn luôn tái hiện được chính xác.</li>
 *   <li>Có thể đối chiếu: tổng {@code quantityChange} = số tồn trong bảng inventories.</li>
 * </ul>
 */
@Entity
@Table(name = "stock_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 20)
    private RefType refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    /** Mã phiếu gốc (PN-/PX-/KK-), lưu kèm để tra cứu nhanh mà không cần join. */
    @Column(name = "ref_code", nullable = false, length = 30)
    private String refCode;

    /** Dương = tăng tồn, âm = giảm tồn. */
    @Column(name = "quantity_change", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityChange;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    /** Số tồn ngay SAU giao dịch này, giúp dựng cột "tồn lũy kế" của thẻ kho. */
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "note", length = 500)
    private String note;

    /** Ngày hiệu lực kế toán kho (lấy theo ngày chứng từ của phiếu). */
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Giá trị của giao dịch = |số lượng| x giá vốn. */
    public BigDecimal getValue() {
        return quantityChange.abs().multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
