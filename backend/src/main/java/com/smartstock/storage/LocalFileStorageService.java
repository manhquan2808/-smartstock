package com.smartstock.storage;

import com.smartstock.common.BusinessException;
import com.smartstock.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lưu file vào thư mục trên máy chủ (mặc định {@code uploads/}).
 *
 * <p>Bean này chỉ được tạo khi KHÔNG có biến môi trường {@code CLOUDINARY_URL}
 * (xem {@code @ConditionalOnProperty} với {@code matchIfMissing = true}).
 *
 * <p><b>Kiểm tra an toàn khi nhận file tải lên:</b>
 *
 * <ol>
 *   <li>Giới hạn dung lượng 2 MB (cũng đã chặn ở tầng Spring servlet).</li>
 *   <li>Kiểm tra CẢ phần mở rộng lẫn kiểu MIME - kẻ tấn công có thể đổi tên
 *       {@code virus.exe} thành {@code anh.jpg}, nên chỉ xét đuôi file là không đủ.</li>
 *   <li>Đọc 12 byte đầu để xác minh đây đúng là ảnh (chữ ký định dạng file).</li>
 *   <li>Đặt lại tên file bằng UUID: tránh ghi đè file cũ và chặn kiểu tấn công
 *       path traversal (tên file dạng {@code ../../etc/passwd}).</li>
 * </ol>
 */
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    /** Chỉ cho phép ba định dạng ảnh theo đặc tả. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final AppProperties appProperties;

    private Path rootDirectory;

    /** Bean này được {@code StorageConfig} khởi tạo khi không cấu hình Cloudinary. */
    public LocalFileStorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    void init() {
        try {
            rootDirectory = Path.of(appProperties.storage().localDir()).toAbsolutePath().normalize();
            Files.createDirectories(rootDirectory);
            log.info("Lưu trữ file: thư mục local tại {}", rootDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("Không tạo được thư mục lưu file: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String store(MultipartFile file, String folder) {
        validate(file);

        String extension = extensionOf(file.getOriginalFilename());
        // Phân thư mục theo năm/tháng để một thư mục không chứa quá nhiều file
        LocalDate today = LocalDate.now();
        String relativeFolder = String.format(
                "%s/%d/%02d", sanitizeFolder(folder), today.getYear(), today.getMonthValue());

        try {
            Path targetFolder = rootDirectory.resolve(relativeFolder).normalize();
            // Chốt an toàn: đường dẫn đích phải nằm trong thư mục gốc
            if (!targetFolder.startsWith(rootDirectory)) {
                throw new BusinessException("Đường dẫn lưu file không hợp lệ.", "INVALID_PATH");
            }
            Files.createDirectories(targetFolder);

            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path targetPath = targetFolder.resolve(fileName);

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // URL công khai: /uploads/products/2026/10/abc123.jpg
            return "/uploads/" + relativeFolder + "/" + fileName;
        } catch (IOException ex) {
            log.error("Lỗi khi lưu file tải lên: {}", ex.getMessage());
            throw new BusinessException("Không lưu được tệp. Vui lòng thử lại.", "FILE_STORE_FAILED");
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            return; // không phải file do hệ thống này lưu
        }
        try {
            Path target = rootDirectory
                    .resolve(fileUrl.substring("/uploads/".length()))
                    .normalize();
            if (target.startsWith(rootDirectory)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ex) {
            // Xóa file thất bại không ảnh hưởng nghiệp vụ, chỉ còn file rác trên đĩa
            log.warn("Không xóa được file {}: {}", fileUrl, ex.getMessage());
        }
    }

    @Override
    public String getStorageType() {
        return "LOCAL";
    }

    /** Kiểm tra dung lượng, phần mở rộng, kiểu MIME và chữ ký nội dung file. */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Vui lòng chọn tệp để tải lên.", "EMPTY_FILE");
        }
        if (file.getSize() > appProperties.storage().maxFileSizeBytes()) {
            throw new BusinessException(
                    "Tệp vượt quá dung lượng cho phép (tối đa 2 MB).", "FILE_TOO_LARGE");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "Chỉ chấp nhận ảnh định dạng JPG, PNG hoặc WebP.", "INVALID_FILE_EXTENSION");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(
                    "Kiểu tệp không hợp lệ. Chỉ chấp nhận ảnh JPG, PNG hoặc WebP.", "INVALID_MIME_TYPE");
        }

        if (!hasImageSignature(file)) {
            throw new BusinessException(
                    "Nội dung tệp không phải là ảnh hợp lệ.", "INVALID_FILE_CONTENT");
        }
    }

    /**
     * Đọc các byte đầu tiên để xác minh đây thật sự là ảnh.
     *
     * <p>Mọi định dạng ảnh đều có "chữ ký" cố định ở đầu file:
     *
     * <ul>
     *   <li>JPEG: {@code FF D8 FF}</li>
     *   <li>PNG: {@code 89 50 4E 47}</li>
     *   <li>WebP: {@code RIFF....WEBP}</li>
     * </ul>
     */
    private boolean hasImageSignature(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (header.length < 4) {
                return false;
            }

            boolean isJpeg = (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            boolean isPng = (header[0] & 0xFF) == 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G';
            boolean isWebp = header.length >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P';

            return isJpeg || isPng || isWebp;
        } catch (IOException ex) {
            return false;
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** Chỉ cho phép tên thư mục đơn giản, chặn ký tự điều hướng đường dẫn. */
    private String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "others";
        }
        String cleaned = folder.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isBlank() ? "others" : cleaned;
    }
}
