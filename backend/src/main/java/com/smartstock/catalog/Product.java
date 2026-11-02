package com.smartstock.catalog;

import com.smartstock.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * Sản phẩm (mặt hàng) trong kho.
 *
 * <p>Lưu ý: entity này KHÔNG chứa số tồn. Tồn kho phụ thuộc vào từng kho nên nằm ở bảng
 * riêng {@code inventories} (một sản phẩm có nhiều dòng tồn, mỗi kho một dòng). Tương tự,
 * {@code purchasePrice} chỉ là giá nhập tham chiếu để gợi ý khi lập phiếu - giá vốn thực
 * tế là {@code avgCost} của từng kho.
 *
 * <p>Quy tắc xóa:
 *
 * <ul>
 *   <li>Còn tồn ở bất kỳ kho nào thì CHẶN xóa.</li>
 *   <li>Đã phát sinh giao dịch thì chỉ xóa mềm ({@code deletedAt}).</li>
 *   <li>Muốn ngừng bán mà vẫn giữ lịch sử thì đổi {@code status} sang DISCONTINUED;
 *       sản phẩm đó không chọn được khi lập phiếu mới.</li>
 * </ul>
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends AuditableEntity {

    /** Mã nội bộ do doanh nghiệp tự đặt, duy nhất toàn hệ thống. */
    @Column(name = "sku", nullable = false, length = 50, unique = true)
    private String sku;

    /** Mã vạch EAN-13 in trên bao bì. Có thể rỗng, nhưng nếu có thì phải duy nhất. */
    @Column(name = "barcode", length = 50, unique = true)
    private String barcode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /** Giá nhập tham chiếu, dùng để điền sẵn đơn giá khi lập phiếu nhập. */
    @Column(name = "purchase_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "sale_price", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal salePrice = BigDecimal.ZERO;

    /** Mức tồn tối thiểu: tồn xuống bằng hoặc dưới mức này thì sinh cảnh báo. */
    @Column(name = "min_stock", nullable = false, precision = 18, scale = 3)
    @Builder.Default
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Sản phẩm được phép chọn khi lập phiếu mới. */
    public boolean isSelectable() {
        return deletedAt == null && status == RecordStatus.ACTIVE;
    }
}
