package com.smartstock.issue;

import com.smartstock.catalog.Customer;
import com.smartstock.catalog.Supplier;
import com.smartstock.common.AuditableEntity;
import com.smartstock.document.DocumentStatus;
import com.smartstock.user.User;
import com.smartstock.warehouse.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phiếu xuất kho, mã dạng {@code PX-yyyyMMdd-xxx}.
 *
 * <p>Khác phiếu nhập ở hai điểm nghiệp vụ:
 *
 * <ul>
 *   <li>Không cho xuất vượt tồn. Hệ thống kiểm tra ba lần: khi lưu nháp (chỉ cảnh báo),
 *       khi gửi duyệt, và khi hoàn tất (chặn hẳn, kiểm tra trên số tồn đã khóa dòng).</li>
 *   <li>Người lập không nhập đơn giá. Giá trị phiếu được tính theo GIÁ VỐN tại thời điểm
 *       hoàn tất, nên {@code totalAmount} chỉ có ý nghĩa sau khi phiếu được duyệt.</li>
 * </ul>
 */
@Entity
@Table(name = "stock_issues")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockIssue extends AuditableEntity {

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 20)
    private IssueType issueType;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Lý do xuất, bắt buộc với loại INTERNAL và DISPOSAL. */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    /** Tổng giá trị xuất theo giá vốn, chỉ có giá trị sau khi phiếu hoàn tất. */
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<StockIssueItem> items = new ArrayList<>();

    public void addItem(StockIssueItem item) {
        item.setIssue(this);
        item.setLineNo(items.size() + 1);
        items.add(item);
    }

    public void clearItems() {
        items.clear();
    }

    /** Tính lại tổng giá trị từ các dòng (sau khi đã điền giá vốn lúc hoàn tất). */
    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(StockIssueItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
