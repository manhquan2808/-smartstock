package com.smartstock.issue;

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

/**
 * Một dòng hàng trong phiếu xuất kho.
 *
 * <p>{@code unitCost} được điền tại thời điểm HOÀN TẤT phiếu - đó là giá vốn bình quân
 * của sản phẩm ở kho lúc đó. Lưu lại giúp báo cáo giá trị xuất kho về sau luôn tái hiện
 * đúng, kể cả khi giá vốn hiện tại đã thay đổi vì các lần nhập sau.
 */
@Entity
@Table(name = "stock_issue_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockIssueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private StockIssue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "line_no", nullable = false)
    @Builder.Default
    private Integer lineNo = 1;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    /** Giá vốn tại thời điểm hoàn tất phiếu. Bằng 0 khi phiếu chưa hoàn tất. */
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "note", length = 500)
    private String note;

    /** Tính lại giá trị dòng theo giá vốn đã ghi nhận. */
    public void recalculateAmount() {
        this.amount = MoneyUtils.amount(quantity, unitCost);
    }
}
