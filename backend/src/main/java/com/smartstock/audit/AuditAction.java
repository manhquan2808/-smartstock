package com.smartstock.audit;

/** Các loại sự kiện được ghi vào nhật ký hoạt động. */
public enum AuditAction {
    LOGIN_SUCCESS("Đăng nhập thành công"),
    LOGIN_FAILED("Đăng nhập thất bại"),
    LOGOUT("Đăng xuất"),
    PASSWORD_RESET_REQUEST("Yêu cầu đặt lại mật khẩu"),
    PASSWORD_RESET("Đặt lại mật khẩu"),
    PASSWORD_CHANGE("Đổi mật khẩu"),
    CREATE("Thêm mới"),
    UPDATE("Cập nhật"),
    DELETE("Xóa"),
    SUBMIT("Gửi duyệt"),
    APPROVE("Duyệt"),
    REJECT("Từ chối"),
    CANCEL("Hủy"),
    LOCK_USER("Khóa tài khoản"),
    UNLOCK_USER("Mở khóa tài khoản"),
    RESET_USER_PASSWORD("Đặt lại mật khẩu người dùng");

    private final String displayName;

    AuditAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
