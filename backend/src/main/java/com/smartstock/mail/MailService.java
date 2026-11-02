package com.smartstock.mail;

import com.smartstock.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Gửi email bằng SMTP.
 *
 * <p>Hai nguyên tắc quan trọng:
 *
 * <ol>
 *   <li><b>Gửi bất đồng bộ</b> ({@code @Async}): người dùng không phải chờ SMTP phản hồi.</li>
 *   <li><b>Lỗi gửi mail không được làm hỏng nghiệp vụ</b>: mọi ngoại lệ được bắt và ghi log.
 *       Phiếu vẫn được duyệt thành công dù máy chủ mail đang lỗi.</li>
 * </ol>
 *
 * <p>Môi trường dev gửi vào Mailpit (xem tại http://localhost:8025), môi trường thật
 * dùng Gmail App Password đặt trong biến môi trường.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    /**
     * Gửi email HTML dựng từ template Thymeleaf.
     *
     * @param to địa chỉ người nhận
     * @param subject tiêu đề (tiếng Việt có dấu, encode UTF-8)
     * @param templateName tên file trong {@code resources/templates/mail/}
     * @param variables các biến truyền vào template
     */
    @Async
    public void sendHtml(String to, String subject, String templateName, Map<String, Object> variables) {
        if (!appProperties.mail().enabled()) {
            log.debug("Chức năng gửi mail đang tắt, bỏ qua email '{}' tới {}", subject, to);
            return;
        }
        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("appName", appProperties.name());
            context.setVariable("frontendUrl", appProperties.frontendUrl());
            String html = templateEngine.process("mail/" + templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_NO, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Đã gửi email '{}' tới {}", subject, to);
        } catch (Exception ex) {
            // Chỉ ghi log: nghiệp vụ gọi hàm này không được thất bại vì lỗi SMTP
            log.error("Không gửi được email '{}' tới {}: {}", subject, to, ex.getMessage());
        }
    }

    /** Email chứa link đặt lại mật khẩu (hạn 30 phút, dùng một lần). */
    @Async
    public void sendPasswordResetEmail(String to, String fullName, String resetLink, int expiryMinutes) {
        sendHtml(
                to,
                "[SmartStock] Đặt lại mật khẩu",
                "password-reset.html",
                Map.of(
                        "fullName", fullName,
                        "resetLink", resetLink,
                        "expiryMinutes", expiryMinutes));
    }

    /** Email thông báo quản lý có phiếu đang chờ duyệt. */
    @Async
    public void sendPendingDocumentEmail(
            String to, String fullName, String documentType, String documentCode, String warehouseName, String link) {
        sendHtml(
                to,
                "[SmartStock] " + documentType + " " + documentCode + " chờ duyệt",
                "pending-document.html",
                Map.of(
                        "fullName", fullName,
                        "documentType", documentType,
                        "documentCode", documentCode,
                        "warehouseName", warehouseName,
                        "link", link));
    }

    /**
     * Email báo cáo hàng tồn thấp, gửi 07:00 hằng ngày.
     *
     * @param items danh sách dòng cảnh báo đã định dạng sẵn
     */
    @Async
    public void sendLowStockReportEmail(String to, String fullName, String reportDate, List<LowStockMailRow> items) {
        sendHtml(
                to,
                "[SmartStock] Báo cáo hàng tồn thấp ngày " + reportDate,
                "low-stock-report.html",
                Map.of(
                        "fullName", fullName,
                        "reportDate", reportDate,
                        "items", items,
                        "totalItems", items.size()));
    }

    /**
     * Một dòng trong email báo cáo tồn thấp.
     *
     * @param quantity số tồn hiện tại, đã định dạng theo tiếng Việt
     * @param minStock mức tồn tối thiểu, đã định dạng
     */
    public record LowStockMailRow(
            String sku, String productName, String warehouseName, String quantity, String minStock, String unitName) {}
}
