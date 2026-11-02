package com.smartstock.user;

/**
 * Vai trò của tài khoản. Mỗi tài khoản có đúng MỘT vai trò.
 *
 * <p>Spring Security quy ước tên quyền có tiền tố {@code ROLE_}, nên khi dùng trong
 * {@code @PreAuthorize("hasRole('ADMIN')")} ta viết "ADMIN" còn hệ thống hiểu là "ROLE_ADMIN".
 */
public enum Role {
    /** Quản trị: toàn quyền, thấy mọi kho, quản lý người dùng, xem nhật ký hoạt động. */
    ADMIN("Quản trị hệ thống"),

    /** Quản lý kho: duyệt phiếu và xem báo cáo, nhưng chỉ trong các kho được gán. */
    MANAGER("Quản lý kho"),

    /** Nhân viên kho: lập phiếu và kiểm kê trong kho được gán, không thấy giá vốn. */
    STAFF("Nhân viên kho");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    /** Tên tiếng Việt để hiển thị trên giao diện. */
    public String getDisplayName() {
        return displayName;
    }

    /** Tên quyền theo quy ước Spring Security. */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
