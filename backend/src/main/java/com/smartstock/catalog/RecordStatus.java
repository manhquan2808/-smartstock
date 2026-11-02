package com.smartstock.catalog;

/** Trạng thái kinh doanh của sản phẩm. Sản phẩm DISCONTINUED không chọn được khi lập phiếu mới. */
public enum RecordStatus {
    ACTIVE("Đang kinh doanh"),
    DISCONTINUED("Ngừng kinh doanh");

    private final String displayName;

    RecordStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
