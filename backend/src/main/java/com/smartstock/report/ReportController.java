package com.smartstock.report;

import com.smartstock.config.AppProperties;
import com.smartstock.inventory.InventoryDtos.StockCardResponse;
import com.smartstock.inventory.InventoryQueryService;
import com.smartstock.report.ReportDtos.DashboardResponse;
import com.smartstock.report.ReportDtos.InventoryReportResponse;
import com.smartstock.report.ReportDtos.IssueTrendResponse;
import com.smartstock.report.ReportDtos.ReplenishmentResponse;
import com.smartstock.report.ReportDtos.SlowMovingReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API báo cáo, dashboard và gợi ý nhập hàng, kèm chức năng xuất Excel/PDF.
 *
 * <p>Các endpoint xuất file trả về mảng byte với header {@code Content-Disposition} nên
 * trình duyệt tự tải về. Tên file có dấu được mã hóa theo chuẩn RFC 5987 để không bị lỗi
 * font trên Windows.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "11. Báo cáo", description = "Dashboard, báo cáo nhập-xuất-tồn, hàng tồn lâu, gợi ý nhập hàng")
public class ReportController {

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;
    private final DashboardService dashboardService;
    private final ReplenishmentService replenishmentService;
    private final InventoryQueryService inventoryQueryService;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;
    private final AppProperties appProperties;

    // ============================== Dashboard ==============================

    @GetMapping("/dashboard")
    @Operation(
            summary = "Số liệu dashboard",
            description = "Thẻ chỉ số, hàng sắp hết, biểu đồ nhập-xuất 30 ngày, Top 10 sản phẩm xuất nhiều. "
                    + "Vai trò STAFF không nhận được tổng giá trị tồn.")
    public DashboardResponse dashboard(@RequestParam(required = false) Long warehouseId) {
        return dashboardService.build(warehouseId);
    }

    // ===================== Báo cáo nhập - xuất - tồn =====================

    @GetMapping("/inventory")
    @Operation(
            summary = "Báo cáo nhập - xuất - tồn",
            description = "Tính từ sổ giao dịch kho nên luôn khớp với thẻ kho")
    public InventoryReportResponse inventoryReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId) {
        return reportService.buildInventoryReport(fromDate, toDate, warehouseId, categoryId);
    }

    @GetMapping("/inventory/excel")
    @Operation(summary = "Xuất báo cáo nhập - xuất - tồn ra Excel")
    public ResponseEntity<byte[]> inventoryReportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId)
            throws IOException {

        InventoryReportResponse report =
                reportService.buildInventoryReport(fromDate, toDate, warehouseId, categoryId);
        byte[] content = excelExportService.exportInventoryReport(report);
        return fileResponse(content, "Bao-cao-nhap-xuat-ton.xlsx", EXCEL_CONTENT_TYPE);
    }

    @GetMapping("/inventory/pdf")
    @Operation(summary = "Xuất báo cáo nhập - xuất - tồn ra PDF")
    public ResponseEntity<byte[]> inventoryReportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId) {

        InventoryReportResponse report =
                reportService.buildInventoryReport(fromDate, toDate, warehouseId, categoryId);
        byte[] content = pdfExportService.exportInventoryReport(report, appProperties.name());
        return fileResponse(content, "Bao-cao-nhap-xuat-ton.pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    // ========================= Báo cáo hàng tồn lâu =========================

    @GetMapping("/slow-moving")
    @Operation(
            summary = "Báo cáo hàng tồn lâu",
            description = "Hàng còn tồn nhưng không phát sinh xuất trong N ngày (mặc định 90). "
                    + "Bao gồm cả hàng chưa từng xuất.")
    public SlowMovingReportResponse slowMovingReport(
            @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Integer days) {
        return reportService.buildSlowMovingReport(warehouseId, days);
    }

    @GetMapping("/slow-moving/excel")
    @Operation(summary = "Xuất báo cáo hàng tồn lâu ra Excel")
    public ResponseEntity<byte[]> slowMovingExcel(
            @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Integer days)
            throws IOException {
        SlowMovingReportResponse report = reportService.buildSlowMovingReport(warehouseId, days);
        return fileResponse(
                excelExportService.exportSlowMovingReport(report), "Bao-cao-hang-ton-lau.xlsx", EXCEL_CONTENT_TYPE);
    }

    @GetMapping("/slow-moving/pdf")
    @Operation(summary = "Xuất báo cáo hàng tồn lâu ra PDF")
    public ResponseEntity<byte[]> slowMovingPdf(
            @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) Integer days) {
        SlowMovingReportResponse report = reportService.buildSlowMovingReport(warehouseId, days);
        return fileResponse(
                pdfExportService.exportSlowMovingReport(report, appProperties.name()),
                "Bao-cao-hang-ton-lau.pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    // ============================== Thẻ kho ==============================

    @GetMapping("/stock-card/excel")
    @Operation(summary = "Xuất thẻ kho ra Excel")
    public ResponseEntity<byte[]> stockCardExcel(
            @RequestParam Long productId,
            @RequestParam Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate)
            throws IOException {

        StockCardResponse card = inventoryQueryService.getStockCard(productId, warehouseId, fromDate, toDate);
        return fileResponse(
                excelExportService.exportStockCard(card), "The-kho-" + card.sku() + ".xlsx", EXCEL_CONTENT_TYPE);
    }

    @GetMapping("/stock-card/pdf")
    @Operation(summary = "Xuất thẻ kho ra PDF")
    public ResponseEntity<byte[]> stockCardPdf(
            @RequestParam Long productId,
            @RequestParam Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        StockCardResponse card = inventoryQueryService.getStockCard(productId, warehouseId, fromDate, toDate);
        return fileResponse(
                pdfExportService.exportStockCard(card, appProperties.name()),
                "The-kho-" + card.sku() + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    // ========================= Gợi ý nhập hàng =========================

    @GetMapping("/replenishment")
    @Operation(
            summary = "Gợi ý nhập hàng",
            description = "SL nên nhập = xuất bình quân/ngày x (thời gian chờ hàng + số ngày dự trữ) "
                    + "+ tồn tối thiểu - tồn hiện tại. Sắp xếp theo số ngày còn đủ hàng tăng dần.")
    public ReplenishmentResponse replenishment(
            @RequestParam Long warehouseId, @RequestParam(required = false) Integer windowDays) {
        return replenishmentService.suggest(warehouseId, windowDays);
    }

    @GetMapping("/replenishment/excel")
    @Operation(summary = "Xuất danh sách gợi ý nhập hàng ra Excel")
    public ResponseEntity<byte[]> replenishmentExcel(
            @RequestParam Long warehouseId, @RequestParam(required = false) Integer windowDays) throws IOException {
        ReplenishmentResponse report = replenishmentService.suggest(warehouseId, windowDays);
        return fileResponse(
                excelExportService.exportReplenishment(report), "Goi-y-nhap-hang.xlsx", EXCEL_CONTENT_TYPE);
    }

    @GetMapping("/replenishment/trend")
    @Operation(
            summary = "Biểu đồ lượng xuất theo ngày của một sản phẩm",
            description = "Dùng để kiểm tra xem con số bình quân có đại diện cho nhu cầu thực tế hay không")
    public IssueTrendResponse issueTrend(
            @RequestParam Long productId,
            @RequestParam Long warehouseId,
            @RequestParam(required = false) Integer windowDays) {
        return replenishmentService.getIssueTrend(productId, warehouseId, windowDays);
    }

    // ============================== Hàm hỗ trợ ==============================

    /**
     * Trả về file để trình duyệt tải xuống.
     *
     * <p>Dùng cả {@code filename} (ASCII, cho trình duyệt cũ) và {@code filename*}
     * (UTF-8 theo RFC 5987) để tên file có dấu không bị lỗi trên mọi trình duyệt.
     */
    private ResponseEntity<byte[]> fileResponse(byte[] content, String fileName, String contentType) {
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedName)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                .body(content);
    }
}
