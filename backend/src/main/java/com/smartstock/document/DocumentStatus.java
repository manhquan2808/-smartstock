package com.smartstock.document;

/**
 * Trạng thái phiếu, dùng chung cho phiếu nhập, phiếu xuất và phiếu kiểm kê.
 *
 * <p>Luồng chuyển trạng thái:
 *
 * <pre>
 *   DRAFT ──gửi duyệt──► PENDING ──duyệt───► COMPLETED ──hủy (kèm lý do)──► CANCELLED
 *     ▲                     │
 *     └──────────────────── từ chối (kèm lý do) ──► REJECTED ──sửa, gửi lại──► PENDING
 * </pre>
 *
 * <p>Quy tắc bất biến:
 *
 * <ul>
 *   <li>Chỉ DRAFT và REJECTED được sửa. PENDING không ai sửa được (kể cả người lập).</li>
 *   <li>COMPLETED không được sửa hay xóa - muốn điều chỉnh thì hủy kèm lý do, hệ thống
 *       sinh giao dịch đảo ngược trong sổ kho.</li>
 *   <li>Chỉ COMPLETED mới tác động tới tồn kho.</li>
 * </ul>
 */
public enum DocumentStatus {
    DRAFT("Nháp"),
    PENDING("Chờ duyệt"),
    COMPLETED("Hoàn tất"),
    REJECTED("Bị từ chối"),
    CANCELLED("Đã hủy");

    private final String displayName;

    DocumentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Phiếu còn sửa được nội dung hay không. */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /** Phiếu đã tác động tới tồn kho hay chưa. */
    public boolean isAffectingStock() {
        return this == COMPLETED;
    }

    /** Phiếu đã kết thúc vòng đời (không thao tác gì thêm được). */
    public boolean isFinal() {
        return this == CANCELLED;
    }
}
