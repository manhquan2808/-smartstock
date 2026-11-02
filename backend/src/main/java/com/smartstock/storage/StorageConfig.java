package com.smartstock.storage;

import com.smartstock.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Chọn cách lưu trữ file khi khởi động ứng dụng.
 *
 * <p>Quy tắc: có biến môi trường {@code CLOUDINARY_URL} thì dùng Cloudinary, ngược lại
 * lưu vào thư mục local.
 *
 * <p><b>Vì sao quyết định bằng một phương thức {@code @Bean} thay vì
 * {@code @ConditionalOnProperty}:</b> annotation điều kiện chỉ kiểm tra tham số CÓ TỒN TẠI
 * hay không. Trong {@code application.yml} ta khai báo
 * {@code cloudinary-url: ${CLOUDINARY_URL:}} nên tham số luôn tồn tại, chỉ là rỗng khi
 * chạy local - khi đó annotation vẫn tạo bean Cloudinary và ứng dụng sập ngay lúc khởi
 * động vì chuỗi kết nối rỗng. Kiểm tra tường minh ở đây vừa đúng vừa dễ đọc.
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    public FileStorageService fileStorageService(AppProperties appProperties) {
        String cloudinaryUrl = appProperties.storage().cloudinaryUrl();

        if (StringUtils.hasText(cloudinaryUrl) && cloudinaryUrl.startsWith("cloudinary://")) {
            log.info("Chọn cách lưu trữ: Cloudinary (phát hiện biến môi trường CLOUDINARY_URL)");
            return new CloudinaryFileStorageService(appProperties);
        }

        if (StringUtils.hasText(cloudinaryUrl)) {
            // Cấu hình sai định dạng: cảnh báo rõ ràng rồi quay về lưu local,
            // tốt hơn là để ứng dụng không khởi động được
            log.warn(
                    "CLOUDINARY_URL không đúng định dạng (phải bắt đầu bằng 'cloudinary://'), "
                            + "tạm thời chuyển sang lưu file vào thư mục local");
        }

        log.info("Chọn cách lưu trữ: thư mục local '{}'", appProperties.storage().localDir());
        return new LocalFileStorageService(appProperties);
    }
}
