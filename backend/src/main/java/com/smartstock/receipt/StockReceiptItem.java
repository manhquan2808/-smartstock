package com.smartstock.receipt;

import com.smartstock.catalog.Product;
import com.smartstock.common.MoneyUtils;
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

/** Một dòng hàng trong phiếu nhập kho. */
@Entity
@Table(name = "stock_receipt_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private StockReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Số thứ tự dòng trên phiếu in, bắt đầu từ 1. */
    @Column(name = "line_no", nullable = false)
    @Builder.Default
    private Integer lineNo = 1;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Thành tiền = số lượng x đơn giá. Hệ thống tự tính, không nhận giá trị từ frontend. */
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "note", length = 500)
    private String note;

    /** Tính lại thành tiền sau khi đổi số lượng hoặc đơn giá. */
    public void recalculateAmount() {
        this.amount = MoneyUtils.amount(quantity, unitPrice);
    }
}
