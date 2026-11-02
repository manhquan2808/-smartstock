package com.smartstock.common;

import org.springframework.http.HttpStatus;

/**
 * Người dùng đã đăng nhập nhưng không có quyền với tài nguyên này
 * (ví dụ: STAFF cố duyệt phiếu, hoặc truy cập kho không được gán). Trả về HTTP 403.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", HttpStatus.FORBIDDEN);
    }
}
