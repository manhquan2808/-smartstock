package com.smartstock.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Nơi xử lý lỗi TẬP TRUNG cho toàn bộ API.
 *
 * <p>Nhờ lớp này, controller không cần try/catch: chỉ việc ném exception, lỗi sẽ được
 * chuyển thành JSON thống nhất. Tương đương {@code app/Exceptions/Handler.php} của Laravel.
 *
 * <p>Khuôn dạng JSON trả về:
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-31T10:00:00",
 *   "status": 400,
 *   "code": "INSUFFICIENT_STOCK",
 *   "message": "Không đủ tồn kho để xuất: ...",
 *   "path": "/api/stock-issues/5/approve",
 *   "fieldErrors": { "quantity": "Số lượng phải lớn hơn 0" }
 * }
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Lỗi nghiệp vụ do chính hệ thống chủ động ném ra (bao gồm 404, 403, xuất vượt tồn). */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        // Lỗi nghiệp vụ là tình huống bình thường -> chỉ ghi log mức WARN, không kèm stacktrace
        log.warn("Lỗi nghiệp vụ tại {}: {}", request.getRequestURI(), ex.getMessage());
        ApiError body = ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI());
        if (ex instanceof InsufficientStockException shortage) {
            body = body.withDetails(Map.of("shortages", shortage.getShortages()));
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** Dữ liệu gửi lên không đạt ràng buộc @Valid trên DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            // Giữ lại thông báo đầu tiên của mỗi trường để giao diện hiển thị cạnh ô nhập
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.of(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                "Dữ liệu không hợp lệ, vui lòng kiểm tra lại các trường được đánh dấu.",
                                request.getRequestURI())
                        .withFieldErrors(fieldErrors));
    }

    /** Ràng buộc @Validated trên tham số của controller/service. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fieldErrors.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.of(
                                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ.", request.getRequestURI())
                        .withFieldErrors(fieldErrors));
    }

    /** Vi phạm ràng buộc ở tầng CSDL: trùng khóa unique, xóa bản ghi đang được tham chiếu. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Vi phạm ràng buộc CSDL tại {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        String rawMessage = ex.getMostSpecificCause().getMessage();
        String message = "Dữ liệu vi phạm ràng buộc của hệ thống.";
        if (rawMessage != null) {
            String lower = rawMessage.toLowerCase();
            if (lower.contains("duplicate")) {
                message = "Giá trị đã tồn tại trong hệ thống. Vui lòng dùng mã khác.";
            } else if (lower.contains("foreign key")) {
                message = "Không thể thực hiện vì dữ liệu này đang được sử dụng ở nơi khác.";
            }
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "DATA_INTEGRITY", message, request.getRequestURI()));
    }

    /**
     * Hai người sửa cùng một dòng tồn kho: bản ghi đã bị người khác thay đổi.
     * Đây là cơ chế khóa lạc quan (@Version) của JPA.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT,
                        "CONCURRENT_MODIFICATION",
                        "Dữ liệu vừa được người khác cập nhật. Vui lòng tải lại trang và thử lại.",
                        request.getRequestURI()));
    }

    /** Sai tên đăng nhập hoặc mật khẩu. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(
                        HttpStatus.UNAUTHORIZED,
                        "BAD_CREDENTIALS",
                        "Tên đăng nhập hoặc mật khẩu không đúng.",
                        request.getRequestURI()));
    }

    /** Thiếu quyền theo @PreAuthorize. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "Bạn không có quyền thực hiện chức năng này.",
                        request.getRequestURI()));
    }

    /** File tải lên vượt quá 2 MB. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "FILE_TOO_LARGE",
                        "Tệp vượt quá dung lượng cho phép (tối đa 2 MB).",
                        request.getRequestURI()));
    }

    /** JSON gửi lên sai cú pháp, thiếu tham số, hoặc sai kiểu dữ liệu. */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("Request không hợp lệ tại {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(
                        HttpStatus.BAD_REQUEST,
                        "BAD_REQUEST",
                        "Yêu cầu không hợp lệ hoặc thiếu dữ liệu bắt buộc.",
                        request.getRequestURI()));
    }

    /**
     * Đường dẫn không tồn tại.
     *
     * <p>Phải bắt CẢ {@link NoResourceFoundException}: từ Spring 6, khi không có controller nào
     * khớp, request rơi xuống bộ xử lý tài nguyên tĩnh và bộ này ném ngoại lệ đó chứ không phải
     * {@code NoHandlerFoundException}. Nếu không bắt, mọi đường dẫn gõ sai sẽ rơi vào lưới an toàn
     * cuối cùng và trả 500 - người dùng tưởng hệ thống sập, còn log thì đầy ERROR vô nghĩa.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy đường dẫn yêu cầu.", request.getRequestURI()));
    }

    /** Lưới an toàn cuối cùng: lỗi không lường trước. Ghi log đầy đủ nhưng KHÔNG lộ chi tiết ra ngoài. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không mong đợi tại {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "Hệ thống gặp lỗi không mong đợi. Vui lòng thử lại hoặc liên hệ quản trị viên.",
                        request.getRequestURI()));
    }

    /**
     * Khuôn dạng lỗi JSON thống nhất của toàn bộ API.
     *
     * @param fieldErrors lỗi theo từng trường, dùng để hiển thị cạnh ô nhập trên giao diện
     * @param details dữ liệu bổ sung (ví dụ danh sách sản phẩm thiếu tồn)
     */
    public record ApiError(
            LocalDateTime timestamp,
            int status,
            String error,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors,
            Map<String, Object> details) {

        public static ApiError of(HttpStatus status, String code, String message, String path) {
            return new ApiError(
                    LocalDateTime.now(), status.value(), status.getReasonPhrase(), code, message, path, null, null);
        }

        public ApiError withFieldErrors(Map<String, String> fieldErrors) {
            return new ApiError(timestamp, status, error, code, message, path, fieldErrors, details);
        }

        public ApiError withDetails(Map<String, Object> details) {
            return new ApiError(timestamp, status, error, code, message, path, fieldErrors, details);
        }

        /** Dùng cho các chỗ cần trả danh sách lỗi dạng chuỗi. */
        public ApiError withMessages(List<String> messages) {
            return withDetails(Map.of("messages", messages));
        }
    }
}
