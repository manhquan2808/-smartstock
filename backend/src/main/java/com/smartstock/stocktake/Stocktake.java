package com.smartstock.stocktake;

import com.smartstock.catalog.Category;
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
 * Phiếu kiểm kê kho, mã dạng {@code KK-yyyyMMdd-xxx}.
 *
 * <p>Quy trình: tạo đợt (hệ thống CHỤP LẠI số tồn tại thời điểm đó) -> nhân viên đếm và
 * nhập số thực tế -> gửi duyệt -> quản lý duyệt thì tồn kho được điều chỉnh về số thực tế.
 *
 * <p>Vì số tồn hệ thống được chụp lúc tạo đợt, nếu trong lúc kiểm kê có phiếu nhập/xuất
 * hoàn tất tại kho đó thì số liệu sẽ lệch - hệ thống cảnh báo rõ việc này trên màn hình
 * chi tiết đợt kiểm kê.
 */
@Entity
@Table(name = "stocktakes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stocktake extends AuditableEntity {

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    /** Lọc theo loại hàng; null nghĩa là kiểm kê toàn bộ sản phẩm của kho. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

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

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<StocktakeItem> items = new ArrayList<>();

    public void addItem(StocktakeItem item) {
        item.setStocktake(this);
        item.setLineNo(items.size() + 1);
        items.add(item);
    }

    /** Đợt kiểm kê đang mở: chưa duyệt, chưa hủy - lúc này mới nhập được số thực tế. */
    public boolean isOpen() {
        return status == DocumentStatus.DRAFT || status == DocumentStatus.REJECTED || status == DocumentStatus.PENDING;
    }
}
