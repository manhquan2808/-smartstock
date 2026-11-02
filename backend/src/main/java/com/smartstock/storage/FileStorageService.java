package com.smartstock.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Giao diện lưu trữ file (ảnh sản phẩm, ảnh đại diện).
 *
 * <p><b>Vì sao cần interface thay vì gọi thẳng vào thư mục đĩa:</b> các dịch vụ hosting
 * miễn phí như Render dùng ổ đĩa tạm - mỗi lần ứng dụng khởi động lại là toàn bộ file đã
 * tải lên bị xóa. Nhờ tách interface, hệ thống chạy local thì lưu vào thư mục
 * {@code uploads/}, còn khi triển khai thật chỉ cần đặt biến môi trường
 * {@code CLOUDINARY_URL} là tự chuyển sang lưu trên Cloudinary mà không sửa một dòng
 * nghiệp vụ nào.
 *
 * <p>Đây là ví dụ cụ thể của nguyên tắc "phụ thuộc vào trừu tượng, không phụ thuộc vào
 * cách làm cụ thể". Trong Laravel, phần tương đương là Storage facade với các driver
 * {@code local} và {@code s3}.
 */
public interface FileStorageService {

    /**
     * Lưu một file và trả về đường dẫn công khai để hiển thị trên web.
     *
     * @param file file do người dùng tải lên
     * @param folder thư mục con để phân loại, ví dụ {@code products} hoặc {@code avatars}
     * @return URL dùng được trong thẻ {@code <img src="...">}
     */
    String store(MultipartFile file, String folder);

    /**
     * Xóa file theo đường dẫn đã lưu.
     *
     * <p>Không ném lỗi nếu file không tồn tại: xóa một file đã mất không phải là sự cố.
     */
    void delete(String fileUrl);

    /** Tên cách lưu trữ đang dùng, để ghi log và hiển thị ở trang cấu hình. */
    String getStorageType();
}
