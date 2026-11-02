package com.smartstock.report;

import com.smartstock.config.AppProperties;
import com.smartstock.issue.StockIssueService;
import com.smartstock.receipt.StockReceiptService;
import com.smartstock.stocktake.StocktakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API in phiếu ra PDF.
 *
 * <p>Tách thành controller riêng vì chức năng in thuộc phân hệ báo cáo (dùng chung
 * {@code PdfExportService} và font tiếng Việt), trong khi nghiệp vụ phiếu nằm ở các
 * service của từng loại phiếu.
 *
 * <p>Quyền xem phiếu đã được kiểm tra trong các service tương ứng: người dùng chỉ in
 * được phiếu của kho mình được gán.
 */
@RestController
@RequestMapping("/api/print")
@RequiredArgsConstructor
@Tag(name = "15. In phiếu", description = "Xuất phiếu nhập, phiếu xuất, biên bản kiểm kê ra PDF")
public class DocumentPrintController {

    private final StockReceiptService receiptService;
    private final StockIssueService issueService;
    private final StocktakeService stocktakeService;
    private final PdfExportService pdfExportService;
    private final AppProperties appProperties;

    @GetMapping("/stock-receipts/{id}")
    @Operation(summary = "In phiếu nhập kho (PDF)")
    public ResponseEntity<byte[]> printReceipt(@PathVariable Long id) {
        var document = receiptService.getById(id);
        byte[] content = pdfExportService.exportDocument(document, appProperties.name());
        return pdfResponse(content, document.code() + ".pdf");
    }

    @GetMapping("/stock-issues/{id}")
    @Operation(summary = "In phiếu xuất kho (PDF)")
    public ResponseEntity<byte[]> printIssue(@PathVariable Long id) {
        var document = issueService.getById(id);
        byte[] content = pdfExportService.exportDocument(document, appProperties.name());
        return pdfResponse(content, document.code() + ".pdf");
    }

    @GetMapping("/stocktakes/{id}")
    @Operation(summary = "In biên bản kiểm kê (PDF)")
    public ResponseEntity<byte[]> printStocktake(@PathVariable Long id) {
        var stocktake = stocktakeService.getById(id);
        byte[] content = pdfExportService.exportStocktake(stocktake, appProperties.name());
        return pdfResponse(content, stocktake.code() + ".pdf");
    }

    /**
     * Trả PDF dạng {@code inline} để trình duyệt mở xem ngay trong tab mới (thuận tiện khi
     * cần bấm In luôn), thay vì bắt tải file về trước.
     */
    private ResponseEntity<byte[]> pdfResponse(byte[] content, String fileName) {
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedName)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .body(content);
    }
}
