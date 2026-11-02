package com.smartstock.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.smartstock.common.BusinessException;
import com.smartstock.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lưu ảnh lên Cloudinary (gói miễn phí).
 *
 * <p>Bean này chỉ được tạo khi có biến môi trường {@code CLOUDINARY_URL}. Khi đó Spring
 * chọn bean này thay cho {@link LocalFileStorageService}, nên phần nghiệp vụ không cần
 * biết ảnh đang được lưu ở đâu.
 *
 * <p><b>Vì sao cần:</b> ổ đĩa của Render (gói miễn phí) bị xóa sạch mỗi lần dịch vụ khởi
 * động lại hoặc ngủ rồi thức. Nếu lưu ảnh vào đĩa, sau một đêm mọi ảnh sản phẩm sẽ mất
 * hết. Cloudinary giữ ảnh lâu dài và phục vụ qua CDN.
 *
 * <p>Chuỗi {@code CLOUDINARY_URL} có dạng
 * {@code cloudinary://<api_key>:<api_secret>@<cloud_name>} - lấy trong trang quản trị
 * Cloudinary, đặt vào biến môi trường của dịch vụ hosting, KHÔNG commit vào Git.
 */
@Slf4j
public class CloudinaryFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final AppProperties appProperties;

    private Cloudinary cloudinary;

    /** Bean này được {@code StorageConfig} khởi tạo khi có biến môi trường CLOUDINARY_URL. */
    public CloudinaryFileStorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    void init() {
        cloudinary = new Cloudinary(appProperties.storage().cloudinaryUrl());
        // Bắt buộc dùng HTTPS cho đường dẫn ảnh trả về
        cloudinary.config.secure = true;
        log.info("Lưu trữ file: Cloudinary (cloud: {})", cloudinary.config.cloudName);
    }

    @Override
    public String store(MultipartFile file, String folder) {
        validate(file);

        try (InputStream input = file.getInputStream()) {
            Map<?, ?> result = cloudinary
                    .uploader()
                    .upload(
                            input.readAllBytes(),
                            ObjectUtils.asMap(
                                    "folder", "smartstock/" + sanitizeFolder(folder),
                                    "resource_type", "image",
                                    // Cloudinary tự đặt tên duy nhất, tránh ghi đè ảnh cũ
                                    "unique_filename", true,
                                    "overwrite", false));

            String secureUrl = (String) result.get("secure_url");
            if (secureUrl == null) {
                throw new BusinessException("Cloudinary không trả về đường dẫn ảnh.", "UPLOAD_FAILED");
            }
            return secureUrl;
        } catch (IOException ex) {
            log.error("Lỗi khi tải ảnh lên Cloudinary: {}", ex.getMessage());
            throw new BusinessException("Không tải được ảnh lên. Vui lòng thử lại.", "UPLOAD_FAILED");
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("cloudinary.com")) {
            return;
        }
        try {
            String publicId = extractPublicId(fileUrl);
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            }
        } catch (Exception ex) {
            log.warn("Không xóa được ảnh trên Cloudinary {}: {}", fileUrl, ex.getMessage());
        }
    }

    @Override
    public String getStorageType() {
        return "CLOUDINARY";
    }

    /**
     * Lấy public_id từ URL của Cloudinary để gọi lệnh xóa.
     *
     * <p>URL có dạng
     * {@code https://res.cloudinary.com/<cloud>/image/upload/v1234567/smartstock/products/abc.jpg};
     * public_id là phần {@code smartstock/products/abc} (bỏ tiền tố, bỏ phiên bản và đuôi file).
     */
    private String extractPublicId(String fileUrl) {
        int uploadIndex = fileUrl.indexOf("/upload/");
        if (uploadIndex < 0) {
            return null;
        }
        String path = fileUrl.substring(uploadIndex + "/upload/".length());

        // Bỏ phần phiên bản dạng v1234567/
        if (path.startsWith("v") && path.contains("/")) {
            String possibleVersion = path.substring(1, path.indexOf('/'));
            if (possibleVersion.chars().allMatch(Character::isDigit)) {
                path = path.substring(path.indexOf('/') + 1);
            }
        }

        int dotIndex = path.lastIndexOf('.');
        return dotIndex > 0 ? path.substring(0, dotIndex) : path;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Vui lòng chọn tệp để tải lên.", "EMPTY_FILE");
        }
        if (file.getSize() > appProperties.storage().maxFileSizeBytes()) {
            throw new BusinessException("Tệp vượt quá dung lượng cho phép (tối đa 2 MB).", "FILE_TOO_LARGE");
        }

        String fileName = file.getOriginalFilename();
        String extension = fileName == null || !fileName.contains(".")
                ? ""
                : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Chỉ chấp nhận ảnh định dạng JPG, PNG hoặc WebP.", "INVALID_FILE_EXTENSION");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(
                    "Kiểu tệp không hợp lệ. Chỉ chấp nhận ảnh JPG, PNG hoặc WebP.", "INVALID_MIME_TYPE");
        }
    }

    private String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "others";
        }
        String cleaned = folder.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isBlank() ? "others" : cleaned;
    }
}
