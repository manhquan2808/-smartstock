package com.smartstock.notification;

/** Loại thông báo trong website. Mỗi loại ứng với một biểu tượng/màu trên giao diện. */
public enum NotificationType {
    RECEIPT_PENDING("Phiếu nhập chờ duyệt"),
    ISSUE_PENDING("Phiếu xuất chờ duyệt"),
    STOCKTAKE_PENDING("Phiếu kiểm kê chờ duyệt"),
    DOCUMENT_APPROVED("Phiếu đã được duyệt"),
    DOCUMENT_REJECTED("Phiếu bị từ chối"),
    DOCUMENT_CANCELLED("Phiếu đã bị hủy"),
    LOW_STOCK("Cảnh báo tồn thấp");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
