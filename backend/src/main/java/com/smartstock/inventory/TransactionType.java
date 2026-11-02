package com.smartstock.inventory;

/**
 * Loại giao dịch trong sổ kho ({@code stock_transactions}).
 *
 * <p>Mỗi dòng phiếu khi hoàn tất sinh ra một giao dịch. Tổng {@code quantity_change}
 * của mọi giao dịch luôn bằng số tồn hiện tại - đây là bất biến cốt lõi của hệ thống.
 */
public enum TransactionType {
    /** Nhập kho: làm tăng tồn. */
    RECEIPT("Nhập kho"),

    /** Xuất kho: làm giảm tồn. */
    ISSUE("Xuất kho"),

    /** Điều chỉnh sau kiểm kê: tăng hoặc giảm về số đếm thực tế. */
    ADJUSTMENT("Điều chỉnh kiểm kê"),

    /** Đảo ngược khi hủy một phiếu đã hoàn tất. */
    REVERSAL("Đảo ngược do hủy phiếu");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
