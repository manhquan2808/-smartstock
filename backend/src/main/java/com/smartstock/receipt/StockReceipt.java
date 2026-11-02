package com.smartstock.receipt;

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
 * Phiếu nhập kho, mã dạng {@code PN-yyyyMMdd-xxx}.
 *
 * <p>Vòng đời: xem {@link DocumentStatus}. Chỉ khi phiếu chuyển sang
 * {@code COMPLETED} thì tồn kho mới thay đổi và giá vốn mới được tính lại.
 *
 * <p>Quan hệ với các dòng chi tiết dùng {@code cascade = ALL} và
 * {@code orphanRemoval = true}: lưu phiếu là lưu luôn các dòng, xóa một dòng khỏi danh
 * sách trong Java thì dòng đó cũng bị xóa khỏi CSDL. Nhờ vậy việc sửa phiếu nháp rất gọn.
 */
@Entity
@Table(name = "stock_receipts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReceipt extends AuditableEntity {

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_type", nullable = false, length = 20)
    private ReceiptType receiptType;

    /** Ngày chứng từ: ngày hiệu lực kế toán kho, có thể khác ngày tạo phiếu trên hệ thống. */
    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Lý do nhập, bắt buộc với loại {@link ReceiptType#OTHER}. */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    /** Tổng tiền = tổng thành tiền các dòng, hệ thống tự tính, không nhận từ frontend. */
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

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<StockReceiptItem> items = new ArrayList<>();

    /** Thêm một dòng vào phiếu và giữ hai chiều quan hệ luôn nhất quán. */
    public void addItem(StockReceiptItem item) {
        item.setReceipt(this);
        item.setLineNo(items.size() + 1);
        items.add(item);
    }

    /** Xóa toàn bộ dòng (dùng khi sửa phiếu nháp: xóa hết rồi thêm lại theo dữ liệu mới). */
    public void clearItems() {
        items.clear();
    }

    /** Tính lại tổng tiền từ các dòng. */
    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(StockReceiptItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
