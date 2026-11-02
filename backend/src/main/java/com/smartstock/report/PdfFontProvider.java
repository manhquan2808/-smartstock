package com.smartstock.report;

import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;
import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Cung cấp font tiếng Việt cho mọi file PDF do hệ thống xuất ra.
 *
 * <p><b>Vấn đề:</b> thư viện OpenPDF chỉ có sẵn 14 font cơ bản (Helvetica, Times...) và
 * các font này KHÔNG chứa ký tự tiếng Việt có dấu. Nếu dùng chúng, "Phiếu nhập kho" sẽ
 * in ra thành "Phi?u nh?p kho".
 *
 * <p><b>Cách giải quyết:</b> nhúng font Noto Sans (có đầy đủ dấu tiếng Việt) với chế độ
 * mã hóa {@code IDENTITY_H} - mã hóa Unicode 2 byte, bắt buộc để hiển thị ký tự ngoài
 * bảng Latin cơ bản. Tham số {@code EMBEDDED} nhúng luôn font vào file PDF nên mở trên
 * máy khác vẫn đúng chữ.
 *
 * <p>Font được giải nén ra thư mục tạm khi khởi động, vì OpenPDF cần đường dẫn file thật
 * (không đọc được trực tiếp từ bên trong file JAR).
 */
@Slf4j
@Component
public class PdfFontProvider {

    private static final String REGULAR_RESOURCE = "fonts/NotoSans-Regular.ttf";
    private static final String BOLD_RESOURCE = "fonts/NotoSans-Bold.ttf";

    private BaseFont regularBaseFont;
    private BaseFont boldBaseFont;

    @PostConstruct
    void loadFonts() {
        try {
            Path regularPath = extractToTempFile(REGULAR_RESOURCE, "smartstock-noto-regular", ".ttf");
            Path boldPath = extractToTempFile(BOLD_RESOURCE, "smartstock-noto-bold", ".ttf");

            regularBaseFont = BaseFont.createFont(
                    regularPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            boldBaseFont = BaseFont.createFont(boldPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            log.info("Đã nạp font Noto Sans cho xuất PDF (hỗ trợ tiếng Việt có dấu)");
        } catch (Exception ex) {
            // Không chặn khởi động ứng dụng: các chức năng khác vẫn dùng được,
            // chỉ riêng PDF sẽ báo lỗi rõ ràng khi người dùng bấm xuất file
            log.error("Không nạp được font tiếng Việt cho PDF: {}", ex.getMessage());
        }
    }

    /** Font thường. */
    public Font normal(float size) {
        return font(regularBaseFont, size, Font.NORMAL, Color.BLACK);
    }

    /** Font đậm, dùng cho tiêu đề và dòng tổng cộng. */
    public Font bold(float size) {
        return font(boldBaseFont, size, Font.BOLD, Color.BLACK);
    }

    /** Font thường với màu chỉ định (ví dụ màu đỏ cho số âm). */
    public Font normal(float size, Color color) {
        return font(regularBaseFont, size, Font.NORMAL, color);
    }

    public Font bold(float size, Color color) {
        return font(boldBaseFont, size, Font.BOLD, color);
    }

    private Font font(BaseFont baseFont, float size, int style, Color color) {
        if (baseFont == null) {
            // Dự phòng khi nạp font thất bại: PDF vẫn tạo được nhưng có thể mất dấu
            return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
        }
        return new Font(baseFont, size, style, color);
    }

    /**
     * Giải nén file font từ classpath ra thư mục tạm của hệ điều hành.
     *
     * <p>Chỉ làm một lần khi khởi động. Nếu file đã tồn tại và đủ dung lượng thì dùng lại.
     */
    private Path extractToTempFile(String resourcePath, String prefix, String suffix) throws IOException {
        Path target = Path.of(System.getProperty("java.io.tmpdir"), prefix + suffix);

        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("Không tìm thấy font trong classpath: " + resourcePath);
        }

        if (Files.exists(target) && Files.size(target) == resource.contentLength()) {
            return target; // đã giải nén ở lần chạy trước
        }

        try (InputStream input = resource.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
