package com.smartstock.user;

/**
 * Trạng thái tài khoản. Hệ thống KHÔNG xóa tài khoản (để giữ lịch sử phiếu đã lập),
 * chỉ chuyển sang {@link #LOCKED}.
 */
public enum UserStatus {
    ACTIVE("Đang hoạt động"),
    LOCKED("Đã khóa");

    private final String displayName;

    UserStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
