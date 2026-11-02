package com.smartstock.common;

import org.springframework.http.HttpStatus;

/** Không tìm thấy bản ghi theo mã/khóa chính đã cho. Trả về HTTP 404. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(message, "NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    /** Ví dụ: {@code new NotFoundException("Sản phẩm", 12)} -> "Không tìm thấy Sản phẩm có mã 12". */
    public NotFoundException(String entityName, Object id) {
        super("Không tìm thấy " + entityName + " có mã " + id, "NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
