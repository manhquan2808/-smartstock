package com.smartstock.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Khuôn dạng dữ liệu phân trang trả về cho frontend.
 *
 * <p>Không trả thẳng đối tượng {@code Page} của Spring vì cấu trúc JSON của nó
 * phụ thuộc phiên bản thư viện; dùng record riêng để hợp đồng API luôn ổn định.
 *
 * @param content dữ liệu của trang hiện tại
 * @param page số trang hiện tại, bắt đầu từ 0
 * @param size số bản ghi mỗi trang
 * @param totalElements tổng số bản ghi khớp điều kiện lọc
 * @param totalPages tổng số trang
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast());
    }

    /** Chuyển đổi kiểu phần tử (entity -> DTO) mà giữ nguyên thông tin phân trang. */
    public static <E, D> PageResponse<D> from(Page<E> source, java.util.function.Function<E, D> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast());
    }
}
