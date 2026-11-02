package com.smartstock.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API tải ảnh lên (ảnh sản phẩm, ảnh đại diện).
 *
 * <p>Trả về đường dẫn ảnh; frontend lưu đường dẫn đó vào trường {@code imageUrl} hoặc
 * {@code avatarUrl} khi lưu biểu mẫu. Tách thành hai bước (tải ảnh trước, lưu dữ liệu sau)
 * giúp người dùng xem trước ảnh ngay mà chưa cần bấm Lưu.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "16. Tải tệp", description = "Tải ảnh sản phẩm và ảnh đại diện")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Tải ảnh lên",
            description = "Chấp nhận JPG/PNG/WebP, tối đa 2 MB. Hệ thống kiểm tra cả đuôi file, "
                    + "kiểu MIME và chữ ký nội dung tệp.")
    public Map<String, String> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "others") String folder) {

        String url = fileStorageService.store(file, folder);
        return Map.of("url", url, "storageType", fileStorageService.getStorageType());
    }
}
