package com.smartstock.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ: dữ liệu hợp lệ về mặt kiểu nhưng vi phạm quy tắc nghiệp vụ
 * (ví dụ: xuất vượt tồn, sửa phiếu đã hoàn tất).
 *
 * <p>Thông điệp của lớp này được trả nguyên văn cho người dùng nên phải viết
 * bằng tiếng Việt, rõ ràng, nêu đúng nguyên nhân.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(String message) {
        this(message, "BUSINESS_RULE_VIOLATED", HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, String code) {
        this(message, code, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
