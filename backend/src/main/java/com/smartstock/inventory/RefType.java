package com.smartstock.inventory;

/** Loại chứng từ gốc sinh ra giao dịch kho, dùng để truy ngược về phiếu. */
public enum RefType {
    RECEIPT("Phiếu nhập"),
    ISSUE("Phiếu xuất"),
    STOCKTAKE("Phiếu kiểm kê");

    private final String displayName;

    RefType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
