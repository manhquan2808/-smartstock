package com.smartstock.report;

import com.smartstock.inventory.InventoryDtos.StockCardResponse;
import com.smartstock.report.ReportDtos.InventoryReportResponse;
import com.smartstock.report.ReportDtos.InventoryReportRowResponse;
import com.smartstock.report.ReportDtos.ReplenishmentResponse;
import com.smartstock.report.ReportDtos.ReplenishmentRowResponse;
import com.smartstock.report.ReportDtos.SlowMovingReportResponse;
import com.smartstock.report.ReportDtos.SlowMovingRowResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Xuất báo cáo ra file Excel (.xlsx) bằng Apache POI.
 *
 * <p>Mọi file đều có phần đầu thống nhất theo yêu cầu của đặc tả: tên báo cáo, kỳ báo cáo,
 * kho, người xuất và thời điểm xuất. Tiếng Việt có dấu hiển thị đúng vì file .xlsx dùng
 * UTF-8 sẵn trong định dạng.
 *
 * <p>Số được ghi dưới dạng SỐ THẬT (không phải chuỗi) kèm định dạng {@code #,##0} nên
 * người dùng mở file lên vẫn cộng trừ, lọc và vẽ biểu đồ được trên Excel.
 */
@Service
public class ExcelExportService {

    /** Xuất báo cáo nhập - xuất - tồn. */
    public byte[] exportInventoryReport(InventoryReportResponse report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Nhap-Xuat-Ton");
            Styles styles = new Styles(workbook);
            boolean showValue = report.totalClosingValue() != null;

            int rowIndex = writeReportHeader(
                    sheet,
                    styles,
                    "BÁO CÁO NHẬP - XUẤT - TỒN",
                    "Kỳ báo cáo: " + VietnameseFormat.date(report.fromDate()) + " - "
                            + VietnameseFormat.date(report.toDate()),
                    "Kho: " + report.warehouseName(),
                    report.exportedBy(),
                    report.exportedAt(),
                    showValue ? 10 : 8);

            // Dòng tiêu đề cột
            List<String> headers = showValue
                    ? List.of(
                            "STT", "Mã SKU", "Tên sản phẩm", "ĐVT", "Loại hàng",
                            "Tồn đầu kỳ", "Nhập trong kỳ", "Xuất trong kỳ", "Tồn cuối kỳ", "Giá trị tồn cuối")
                    : List.of(
                            "STT", "Mã SKU", "Tên sản phẩm", "ĐVT", "Loại hàng",
                            "Tồn đầu kỳ", "Nhập trong kỳ", "Xuất trong kỳ", "Tồn cuối kỳ");

            Row headerRow = sheet.createRow(rowIndex++);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.tableHeader);
            }

            int order = 1;
            for (InventoryReportRowResponse row : report.rows()) {
                Row dataRow = sheet.createRow(rowIndex++);
                int col = 0;
                writeNumber(dataRow, col++, BigDecimal.valueOf(order++), styles.integerCell);
                writeText(dataRow, col++, row.sku(), styles.textCell);
                writeText(dataRow, col++, row.productName(), styles.textCell);
                writeText(dataRow, col++, row.unitName(), styles.textCell);
                writeText(dataRow, col++, row.categoryName(), styles.textCell);
                writeNumber(dataRow, col++, row.openingQuantity(), styles.quantityCell);
                writeNumber(dataRow, col++, row.receivedQuantity(), styles.quantityCell);
                writeNumber(dataRow, col++, row.issuedQuantity(), styles.quantityCell);
                writeNumber(dataRow, col++, row.closingQuantity(), styles.quantityCell);
                if (showValue) {
                    writeNumber(dataRow, col, row.closingValue(), styles.moneyCell);
                }
            }

            // Dòng tổng cộng
            Row totalRow = sheet.createRow(rowIndex);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("TỔNG CỘNG");
            totalLabel.setCellStyle(styles.totalText);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 4));
            for (int i = 1; i <= 4; i++) {
                totalRow.createCell(i).setCellStyle(styles.totalText);
            }
            writeNumber(totalRow, 5, report.totalOpeningQuantity(), styles.totalQuantity);
            writeNumber(totalRow, 6, report.totalReceivedQuantity(), styles.totalQuantity);
            writeNumber(totalRow, 7, report.totalIssuedQuantity(), styles.totalQuantity);
            writeNumber(totalRow, 8, report.totalClosingQuantity(), styles.totalQuantity);
            if (showValue) {
                writeNumber(totalRow, 9, report.totalClosingValue(), styles.totalMoney);
            }

            // STT, SKU, Tên SP, ĐVT, Loại hàng, tồn đầu, nhập, xuất, tồn cuối, giá trị
            setColumnWidths(sheet, 6, 12, 42, 10, 20, 14, 14, 14, 14, 18);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /** Xuất thẻ kho của một sản phẩm. */
    public byte[] exportStockCard(StockCardResponse card) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("The-kho");
            Styles styles = new Styles(workbook);
            boolean showCost = card.entries().stream().anyMatch(entry -> entry.unitCost() != null);

            int rowIndex = writeReportHeader(
                    sheet,
                    styles,
                    "THẺ KHO",
                    "Sản phẩm: " + card.sku() + " - " + card.productName() + " (" + card.unitName() + ")",
                    "Kho: " + card.warehouseName(),
                    null,
                    java.time.LocalDateTime.now(),
                    showCost ? 8 : 7);

            List<String> headers = showCost
                    ? List.of("Ngày", "Mã chứng từ", "Diễn giải", "Nhập", "Xuất", "Tồn lũy kế", "Giá vốn", "Ghi chú")
                    : List.of("Ngày", "Mã chứng từ", "Diễn giải", "Nhập", "Xuất", "Tồn lũy kế", "Ghi chú");

            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.tableHeader);
            }

            // Dòng tồn đầu kỳ
            Row openingRow = sheet.createRow(rowIndex++);
            writeText(openingRow, 0, "", styles.textCell);
            writeText(openingRow, 1, "", styles.textCell);
            writeText(openingRow, 2, "Tồn đầu kỳ", styles.boldText);
            writeText(openingRow, 3, "", styles.textCell);
            writeText(openingRow, 4, "", styles.textCell);
            writeNumber(openingRow, 5, card.openingQuantity(), styles.totalQuantity);

            for (var entry : card.entries()) {
                Row dataRow = sheet.createRow(rowIndex++);
                int col = 0;
                writeText(dataRow, col++, VietnameseFormat.dateTime(entry.transactionDate()), styles.textCell);
                writeText(dataRow, col++, entry.refCode(), styles.textCell);
                writeText(dataRow, col++, entry.description(), styles.textCell);
                writeNumber(dataRow, col++, entry.quantityIn(), styles.quantityCell);
                writeNumber(dataRow, col++, entry.quantityOut(), styles.quantityCell);
                writeNumber(dataRow, col++, entry.balanceAfter(), styles.quantityCell);
                if (showCost) {
                    writeNumber(dataRow, col++, entry.unitCost(), styles.moneyCell);
                }
                writeText(dataRow, col, entry.note(), styles.textCell);
            }

            // Dòng tổng
            Row totalRow = sheet.createRow(rowIndex);
            writeText(totalRow, 2, "TỔNG CỘNG", styles.totalText);
            writeNumber(totalRow, 3, card.totalIn(), styles.totalQuantity);
            writeNumber(totalRow, 4, card.totalOut(), styles.totalQuantity);
            writeNumber(totalRow, 5, card.closingQuantity(), styles.totalQuantity);

            setColumnWidths(sheet, 18, 20, 42, 12, 12, 14, 14, 24);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /** Xuất báo cáo hàng tồn lâu. */
    public byte[] exportSlowMovingReport(SlowMovingReportResponse report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Hang-ton-lau");
            Styles styles = new Styles(workbook);
            boolean showValue = report.totalValue() != null;

            int rowIndex = writeReportHeader(
                    sheet,
                    styles,
                    "BÁO CÁO HÀNG TỒN LÂU",
                    "Không phát sinh xuất trong " + report.thresholdDays() + " ngày",
                    "Kho: " + report.warehouseName(),
                    report.exportedBy(),
                    report.exportedAt(),
                    showValue ? 9 : 7);

            List<String> headers = showValue
                    ? List.of(
                            "STT", "Mã SKU", "Tên sản phẩm", "ĐVT", "Kho",
                            "Tồn hiện tại", "Ngày xuất cuối", "Số ngày không xuất", "Giá trị tồn")
                    : List.of(
                            "STT", "Mã SKU", "Tên sản phẩm", "ĐVT", "Kho",
                            "Tồn hiện tại", "Ngày xuất cuối", "Số ngày không xuất");

            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.tableHeader);
            }

            int order = 1;
            for (SlowMovingRowResponse row : report.rows()) {
                Row dataRow = sheet.createRow(rowIndex++);
                int col = 0;
                writeNumber(dataRow, col++, BigDecimal.valueOf(order++), styles.integerCell);
                writeText(dataRow, col++, row.sku(), styles.textCell);
                writeText(dataRow, col++, row.productName(), styles.textCell);
                writeText(dataRow, col++, row.unitName(), styles.textCell);
                writeText(dataRow, col++, row.warehouseName(), styles.textCell);
                writeNumber(dataRow, col++, row.quantity(), styles.quantityCell);
                writeText(
                        dataRow,
                        col++,
                        row.lastIssueDate() == null ? "Chưa từng xuất" : VietnameseFormat.date(row.lastIssueDate()),
                        styles.textCell);
                writeText(
                        dataRow,
                        col++,
                        row.daysSinceLastIssue() == null ? "-" : String.valueOf(row.daysSinceLastIssue()),
                        styles.textCell);
                if (showValue) {
                    writeNumber(dataRow, col, row.stockValue(), styles.moneyCell);
                }
            }

            if (showValue) {
                Row totalRow = sheet.createRow(rowIndex);
                writeText(totalRow, 0, "TỔNG GIÁ TRỊ TỒN LÂU", styles.totalText);
                sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 7));
                writeNumber(totalRow, 8, report.totalValue(), styles.totalMoney);
            }

            setColumnWidths(sheet, 6, 12, 42, 10, 22, 14, 16, 18, 18);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /** Xuất danh sách gợi ý nhập hàng. */
    public byte[] exportReplenishment(ReplenishmentResponse report) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Goi-y-nhap-hang");
            Styles styles = new Styles(workbook);

            int rowIndex = writeReportHeader(
                    sheet,
                    styles,
                    "GỢI Ý NHẬP HÀNG",
                    String.format(
                            "Dựa trên %d ngày gần nhất | Thời gian chờ hàng: %d ngày | Dự trữ an toàn: %d ngày",
                            report.windowDays(), report.leadTimeDays(), report.safetyStockDays()),
                    "Kho: " + report.warehouseName(),
                    null,
                    java.time.LocalDateTime.now(),
                    10);

            // Ghi rõ công thức vào file để người đọc báo cáo hiểu cách tính
            Row formulaRow = sheet.createRow(rowIndex++);
            Cell formulaCell = formulaRow.createCell(0);
            formulaCell.setCellValue("Công thức: " + report.formulaExplanation());
            formulaCell.setCellStyle(styles.subTitle);
            sheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 9));
            rowIndex++;

            List<String> headers = List.of(
                    "STT", "Mã SKU", "Tên sản phẩm", "ĐVT", "Tồn hiện tại", "Tồn tối thiểu",
                    "Xuất BQ/ngày", "Số ngày còn đủ", "SL nên nhập", "Ước tính tiền");

            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.tableHeader);
            }

            int order = 1;
            for (ReplenishmentRowResponse row : report.rows()) {
                Row dataRow = sheet.createRow(rowIndex++);
                int col = 0;
                writeNumber(dataRow, col++, BigDecimal.valueOf(order++), styles.integerCell);
                writeText(dataRow, col++, row.sku(), styles.textCell);
                writeText(dataRow, col++, row.productName(), styles.textCell);
                writeText(dataRow, col++, row.unitName(), styles.textCell);
                writeNumber(dataRow, col++, row.quantityOnHand(), styles.quantityCell);
                writeNumber(dataRow, col++, row.minStock(), styles.quantityCell);
                writeNumber(dataRow, col++, row.averageDailyIssue(), styles.quantityCell);
                writeText(
                        dataRow,
                        col++,
                        row.daysOfStockLeft() == null ? "-" : VietnameseFormat.quantity(row.daysOfStockLeft()),
                        styles.textCell);
                writeNumber(dataRow, col++, row.suggestedQuantity(), styles.totalQuantity);
                writeNumber(dataRow, col, row.estimatedCost(), styles.moneyCell);
            }

            setColumnWidths(sheet, 6, 12, 42, 10, 14, 14, 14, 16, 14, 18);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    // ==================================================================
    // Hàm hỗ trợ
    // ==================================================================

    /**
     * Ghi phần đầu chung của mọi báo cáo.
     *
     * @return chỉ số dòng tiếp theo còn trống
     */
    private int writeReportHeader(
            Sheet sheet,
            Styles styles,
            String title,
            String period,
            String warehouse,
            String exportedBy,
            java.time.LocalDateTime exportedAt,
            int columnCount) {

        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnCount - 1));

        Row periodRow = sheet.createRow(rowIndex++);
        Cell periodCell = periodRow.createCell(0);
        periodCell.setCellValue(period);
        periodCell.setCellStyle(styles.subTitle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columnCount - 1));

        Row warehouseRow = sheet.createRow(rowIndex++);
        Cell warehouseCell = warehouseRow.createCell(0);
        warehouseCell.setCellValue(warehouse);
        warehouseCell.setCellStyle(styles.subTitle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, columnCount - 1));

        Row metaRow = sheet.createRow(rowIndex++);
        Cell metaCell = metaRow.createCell(0);
        String meta = "Thời điểm xuất: " + VietnameseFormat.dateTimeWithSeconds(exportedAt);
        if (exportedBy != null && !exportedBy.isBlank()) {
            meta = "Người xuất: " + exportedBy + " | " + meta;
        }
        metaCell.setCellValue(meta);
        metaCell.setCellStyle(styles.subTitle);
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, columnCount - 1));

        rowIndex++; // một dòng trống trước bảng dữ liệu
        return rowIndex;
    }

    private void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void writeNumber(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    /**
     * Đặt độ rộng cột theo số ký tự.
     *
     * <p><b>Vì sao KHÔNG dùng {@code sheet.autoSizeColumn}:</b> hàm đó phải đo chiều rộng
     * chữ thật nên cần thư viện vẽ font của hệ điều hành (freetype/fontconfig). Trên các
     * ảnh Docker gọn nhẹ (ví dụ JRE Alpine dùng khi triển khai) các thư viện này không có
     * sẵn, và việc xuất Excel sẽ lỗi {@code UnsatisfiedLinkError} ngay trên môi trường
     * thật dù máy phát triển vẫn chạy tốt. Đặt độ rộng tường minh vừa chắc chắn hoạt động
     * mọi nơi, vừa nhanh hơn nhiều với bảng dài.
     *
     * <p>POI tính độ rộng theo đơn vị 1/256 ký tự, nên phải nhân 256.
     */
    private void setColumnWidths(Sheet sheet, int... charWidths) {
        for (int i = 0; i < charWidths.length; i++) {
            sheet.setColumnWidth(i, charWidths[i] * 256);
        }
        // Giữ phần tiêu đề cố định khi cuộn xuống xem dữ liệu
        sheet.createFreezePane(0, 6);
    }

    /**
     * Tập hợp các định dạng ô dùng lại nhiều lần.
     *
     * <p>Apache POI giới hạn khoảng 64.000 style mỗi workbook, nên phải tạo một lần rồi
     * dùng lại thay vì tạo style mới cho từng ô.
     */
    private static final class Styles {

        private final CellStyle title;
        private final CellStyle subTitle;
        private final CellStyle tableHeader;
        private final CellStyle textCell;
        private final CellStyle boldText;
        private final CellStyle integerCell;
        private final CellStyle quantityCell;
        private final CellStyle moneyCell;
        private final CellStyle totalText;
        private final CellStyle totalQuantity;
        private final CellStyle totalMoney;

        Styles(Workbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            subTitle = workbook.createCellStyle();
            subTitle.setAlignment(HorizontalAlignment.CENTER);

            tableHeader = workbook.createCellStyle();
            tableHeader.setFont(headerFont);
            // Màu xanh dương chủ đạo của hệ thống (#1677ff)
            tableHeader.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tableHeader.setAlignment(HorizontalAlignment.CENTER);
            tableHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            tableHeader.setWrapText(true);
            applyBorders(tableHeader);

            textCell = workbook.createCellStyle();
            applyBorders(textCell);

            boldText = workbook.createCellStyle();
            boldText.setFont(boldFont);
            applyBorders(boldText);

            integerCell = workbook.createCellStyle();
            integerCell.setAlignment(HorizontalAlignment.CENTER);
            applyBorders(integerCell);

            quantityCell = workbook.createCellStyle();
            quantityCell.setDataFormat(workbook.createDataFormat().getFormat(VietnameseFormat.EXCEL_QUANTITY_PATTERN));
            quantityCell.setAlignment(HorizontalAlignment.RIGHT);
            applyBorders(quantityCell);

            moneyCell = workbook.createCellStyle();
            moneyCell.setDataFormat(workbook.createDataFormat().getFormat(VietnameseFormat.EXCEL_MONEY_PATTERN));
            moneyCell.setAlignment(HorizontalAlignment.RIGHT);
            applyBorders(moneyCell);

            totalText = workbook.createCellStyle();
            totalText.setFont(boldFont);
            totalText.setAlignment(HorizontalAlignment.CENTER);
            totalText.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalText.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorders(totalText);

            totalQuantity = workbook.createCellStyle();
            totalQuantity.setFont(boldFont);
            totalQuantity.setDataFormat(
                    workbook.createDataFormat().getFormat(VietnameseFormat.EXCEL_QUANTITY_PATTERN));
            totalQuantity.setAlignment(HorizontalAlignment.RIGHT);
            totalQuantity.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalQuantity.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorders(totalQuantity);

            totalMoney = workbook.createCellStyle();
            totalMoney.setFont(boldFont);
            totalMoney.setDataFormat(workbook.createDataFormat().getFormat(VietnameseFormat.EXCEL_MONEY_PATTERN));
            totalMoney.setAlignment(HorizontalAlignment.RIGHT);
            totalMoney.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalMoney.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyBorders(totalMoney);
        }

        private void applyBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
