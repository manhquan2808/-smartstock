package com.smartstock.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartstock.document.DocumentDtos.DocumentDetailResponse;
import com.smartstock.document.DocumentDtos.DocumentItemResponse;
import com.smartstock.inventory.InventoryDtos.StockCardResponse;
import com.smartstock.report.ReportDtos.InventoryReportResponse;
import com.smartstock.report.ReportDtos.InventoryReportRowResponse;
import com.smartstock.report.ReportDtos.SlowMovingReportResponse;
import com.smartstock.report.ReportDtos.SlowMovingRowResponse;
import com.smartstock.stocktake.StocktakeDtos.StocktakeDetailResponse;
import com.smartstock.stocktake.StocktakeDtos.StocktakeItemResponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Xuất phiếu và báo cáo ra file PDF bằng OpenPDF.
 *
 * <p>Toàn bộ chữ dùng font Noto Sans nhúng sẵn (xem {@link PdfFontProvider}) nên tiếng
 * Việt có dấu hiển thị đúng trên mọi máy, kể cả máy chưa cài font đó.
 *
 * <p>Phiếu in theo mẫu quen thuộc của doanh nghiệp Việt Nam: tiêu đề giữa trang, thông
 * tin đối tác, bảng chi tiết hàng, dòng tổng tiền kèm số tiền bằng chữ, và khu vực ký tên
 * cho người lập - người giao - người nhận - thủ kho.
 */
@Service
@RequiredArgsConstructor
public class PdfExportService {

    /** Màu xanh dương chủ đạo của hệ thống (#1677ff). */
    private static final Color PRIMARY_COLOR = new Color(22, 119, 255);

    private static final Color HEADER_BACKGROUND = new Color(240, 245, 255);
    private static final Color TOTAL_BACKGROUND = new Color(245, 245, 245);

    private final PdfFontProvider fonts;

    /**
     * In phiếu nhập hoặc phiếu xuất.
     *
     * <p>Dùng chung một hàm vì hai loại phiếu có cùng cấu trúc; nhãn thay đổi theo
     * {@code documentTypeName} đã có trong DTO.
     */
    public byte[] exportDocument(DocumentDetailResponse document, String companyName) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(pdf, output);
            pdf.open();

            // ---------- Phần đầu: tên đơn vị và tiêu đề phiếu ----------
            pdf.add(paragraph(companyName.toUpperCase(), fonts.bold(11), Element.ALIGN_LEFT));
            pdf.add(paragraph("Kho: " + document.warehouseName(), fonts.normal(10), Element.ALIGN_LEFT));
            pdf.add(spacer(12));

            pdf.add(paragraph(document.documentTypeName().toUpperCase(), fonts.bold(17, PRIMARY_COLOR), Element.ALIGN_CENTER));
            pdf.add(paragraph("Số: " + document.code(), fonts.normal(11), Element.ALIGN_CENTER));
            pdf.add(paragraph(
                    "Ngày " + document.documentDate().getDayOfMonth()
                            + " tháng " + document.documentDate().getMonthValue()
                            + " năm " + document.documentDate().getYear(),
                    fonts.normal(10),
                    Element.ALIGN_CENTER));
            pdf.add(spacer(14));

            // ---------- Thông tin phiếu ----------
            PdfPTable infoTable = new PdfPTable(new float[] {1.2f, 3f});
            infoTable.setWidthPercentage(100);
            addInfoRow(infoTable, "Loại phiếu:", document.typeName());
            if (document.partnerName() != null) {
                addInfoRow(
                        infoTable,
                        "Đối tác:",
                        document.partnerName() + (document.partnerCode() == null ? "" : " (" + document.partnerCode() + ")"));
            }
            if (document.reason() != null && !document.reason().isBlank()) {
                addInfoRow(infoTable, "Lý do:", document.reason());
            }
            addInfoRow(infoTable, "Người lập:", nullToDash(document.createdByName()));
            addInfoRow(infoTable, "Trạng thái:", document.statusName());
            if (document.approvedByName() != null) {
                addInfoRow(
                        infoTable,
                        "Người duyệt:",
                        document.approvedByName() + " - " + VietnameseFormat.dateTime(document.approvedAt()));
            }
            if (document.note() != null && !document.note().isBlank()) {
                addInfoRow(infoTable, "Ghi chú:", document.note());
            }
            pdf.add(infoTable);
            pdf.add(spacer(12));

            // ---------- Bảng chi tiết hàng ----------
            boolean showPrice = document.totalAmount() != null;
            PdfPTable itemTable = showPrice
                    ? new PdfPTable(new float[] {0.6f, 1.6f, 3.4f, 0.9f, 1.1f, 1.5f, 1.7f})
                    : new PdfPTable(new float[] {0.6f, 1.6f, 4.5f, 0.9f, 1.2f});
            itemTable.setWidthPercentage(100);
            itemTable.setHeaderRows(1);

            addHeaderCell(itemTable, "STT");
            addHeaderCell(itemTable, "Mã SKU");
            addHeaderCell(itemTable, "Tên sản phẩm");
            addHeaderCell(itemTable, "ĐVT");
            addHeaderCell(itemTable, "Số lượng");
            if (showPrice) {
                addHeaderCell(itemTable, "Đơn giá");
                addHeaderCell(itemTable, "Thành tiền");
            }

            for (DocumentItemResponse item : document.items()) {
                addBodyCell(itemTable, String.valueOf(item.lineNo()), Element.ALIGN_CENTER);
                addBodyCell(itemTable, item.sku(), Element.ALIGN_LEFT);
                addBodyCell(itemTable, item.productName(), Element.ALIGN_LEFT);
                addBodyCell(itemTable, item.unitName(), Element.ALIGN_CENTER);
                addBodyCell(itemTable, VietnameseFormat.quantity(item.quantity()), Element.ALIGN_RIGHT);
                if (showPrice) {
                    addBodyCell(itemTable, VietnameseFormat.money(item.unitPrice()), Element.ALIGN_RIGHT);
                    addBodyCell(itemTable, VietnameseFormat.money(item.amount()), Element.ALIGN_RIGHT);
                }
            }

            // Dòng tổng cộng
            if (showPrice) {
                PdfPCell totalLabel = new PdfPCell(new Phrase("TỔNG CỘNG", fonts.bold(10)));
                totalLabel.setColspan(6);
                totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalLabel.setBackgroundColor(TOTAL_BACKGROUND);
                totalLabel.setPadding(6);
                itemTable.addCell(totalLabel);

                PdfPCell totalValue = new PdfPCell(
                        new Phrase(VietnameseFormat.money(document.totalAmount()), fonts.bold(10)));
                totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalValue.setBackgroundColor(TOTAL_BACKGROUND);
                totalValue.setPadding(6);
                itemTable.addCell(totalValue);
            }

            pdf.add(itemTable);

            // Số tiền bằng chữ - yêu cầu quen thuộc trên chứng từ kho của Việt Nam
            if (showPrice) {
                pdf.add(spacer(8));
                pdf.add(paragraph(
                        "Bằng chữ: " + VietnameseFormat.moneyToWords(document.totalAmount()),
                        fonts.normal(10),
                        Element.ALIGN_LEFT));
            }

            // Thông tin hủy phiếu (nếu có)
            if (document.cancelReason() != null) {
                pdf.add(spacer(8));
                pdf.add(paragraph(
                        "PHIẾU ĐÃ HỦY - Lý do: " + document.cancelReason(),
                        fonts.bold(10, new Color(207, 19, 34)),
                        Element.ALIGN_LEFT));
            }

            // ---------- Khu vực ký tên ----------
            pdf.add(spacer(26));
            PdfPTable signatureTable = new PdfPTable(4);
            signatureTable.setWidthPercentage(100);
            addSignatureCell(signatureTable, "Người lập phiếu", document.createdByName());
            addSignatureCell(signatureTable, "Người giao hàng", null);
            addSignatureCell(signatureTable, "Người nhận hàng", null);
            addSignatureCell(signatureTable, "Thủ kho", document.approvedByName());
            pdf.add(signatureTable);

            pdf.add(spacer(10));
            pdf.add(paragraph(
                    "Phiếu in từ hệ thống SmartStock lúc " + VietnameseFormat.dateTimeWithSeconds(LocalDateTime.now()),
                    fonts.normal(8, Color.GRAY),
                    Element.ALIGN_CENTER));

            pdf.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new com.smartstock.common.BusinessException(
                    "Không tạo được file PDF: " + ex.getMessage(), "PDF_EXPORT_FAILED");
        }
    }

    /** In phiếu kiểm kê, kèm cột chênh lệch. */
    public byte[] exportStocktake(StocktakeDetailResponse stocktake, String companyName) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(pdf, output);
            pdf.open();

            pdf.add(paragraph(companyName.toUpperCase(), fonts.bold(11), Element.ALIGN_LEFT));
            pdf.add(paragraph("Kho: " + stocktake.warehouseName(), fonts.normal(10), Element.ALIGN_LEFT));
            pdf.add(spacer(12));

            pdf.add(paragraph("BIÊN BẢN KIỂM KÊ KHO", fonts.bold(17, PRIMARY_COLOR), Element.ALIGN_CENTER));
            pdf.add(paragraph("Số: " + stocktake.code(), fonts.normal(11), Element.ALIGN_CENTER));
            pdf.add(paragraph(
                    "Ngày kiểm kê: " + VietnameseFormat.date(stocktake.documentDate()),
                    fonts.normal(10),
                    Element.ALIGN_CENTER));
            pdf.add(spacer(14));

            PdfPTable infoTable = new PdfPTable(new float[] {1.2f, 3f});
            infoTable.setWidthPercentage(100);
            addInfoRow(infoTable, "Phạm vi:", stocktake.categoryName());
            addInfoRow(infoTable, "Người lập:", nullToDash(stocktake.createdByName()));
            addInfoRow(infoTable, "Trạng thái:", stocktake.statusName());
            addInfoRow(
                    infoTable,
                    "Số mặt hàng:",
                    stocktake.totalItems() + " (lệch: " + stocktake.differenceItems() + ")");
            if (stocktake.approvedByName() != null) {
                addInfoRow(
                        infoTable,
                        "Người duyệt:",
                        stocktake.approvedByName() + " - " + VietnameseFormat.dateTime(stocktake.approvedAt()));
            }
            pdf.add(infoTable);
            pdf.add(spacer(12));

            boolean showValue = stocktake.totalDifferenceValue() != null;
            PdfPTable itemTable = showValue
                    ? new PdfPTable(new float[] {0.6f, 1.5f, 3.2f, 0.9f, 1.2f, 1.2f, 1.1f, 1.5f})
                    : new PdfPTable(new float[] {0.6f, 1.5f, 3.8f, 0.9f, 1.3f, 1.3f, 1.2f});
            itemTable.setWidthPercentage(100);
            itemTable.setHeaderRows(1);

            addHeaderCell(itemTable, "STT");
            addHeaderCell(itemTable, "Mã SKU");
            addHeaderCell(itemTable, "Tên sản phẩm");
            addHeaderCell(itemTable, "ĐVT");
            addHeaderCell(itemTable, "Tồn hệ thống");
            addHeaderCell(itemTable, "Thực tế");
            addHeaderCell(itemTable, "Chênh lệch");
            if (showValue) {
                addHeaderCell(itemTable, "Giá trị lệch");
            }

            for (StocktakeItemResponse item : stocktake.items()) {
                addBodyCell(itemTable, String.valueOf(item.lineNo()), Element.ALIGN_CENTER);
                addBodyCell(itemTable, item.sku(), Element.ALIGN_LEFT);
                addBodyCell(itemTable, item.productName(), Element.ALIGN_LEFT);
                addBodyCell(itemTable, item.unitName(), Element.ALIGN_CENTER);
                addBodyCell(itemTable, VietnameseFormat.quantity(item.systemQuantity()), Element.ALIGN_RIGHT);
                addBodyCell(
                        itemTable,
                        item.actualQuantity() == null ? "-" : VietnameseFormat.quantity(item.actualQuantity()),
                        Element.ALIGN_RIGHT);

                // Chênh lệch âm (thiếu hàng) in màu đỏ cho dễ nhận ra
                BigDecimal difference = item.difference();
                PdfPCell differenceCell = new PdfPCell(new Phrase(
                        difference == null ? "-" : VietnameseFormat.quantity(difference),
                        difference != null && difference.signum() < 0
                                ? fonts.bold(9, new Color(207, 19, 34))
                                : fonts.normal(9)));
                differenceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                differenceCell.setPadding(5);
                itemTable.addCell(differenceCell);

                if (showValue) {
                    addBodyCell(
                            itemTable,
                            item.differenceValue() == null ? "-" : VietnameseFormat.money(item.differenceValue()),
                            Element.ALIGN_RIGHT);
                }
            }

            pdf.add(itemTable);

            if (showValue) {
                pdf.add(spacer(8));
                pdf.add(paragraph(
                        "Tổng giá trị chênh lệch: " + VietnameseFormat.currency(stocktake.totalDifferenceValue()),
                        fonts.bold(11),
                        Element.ALIGN_RIGHT));
            }

            pdf.add(spacer(26));
            PdfPTable signatureTable = new PdfPTable(3);
            signatureTable.setWidthPercentage(100);
            addSignatureCell(signatureTable, "Người kiểm kê", stocktake.createdByName());
            addSignatureCell(signatureTable, "Thủ kho", null);
            addSignatureCell(signatureTable, "Người duyệt", stocktake.approvedByName());
            pdf.add(signatureTable);

            pdf.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new com.smartstock.common.BusinessException(
                    "Không tạo được file PDF: " + ex.getMessage(), "PDF_EXPORT_FAILED");
        }
    }

    /** Xuất báo cáo nhập - xuất - tồn (khổ ngang để đủ cột). */
    public byte[] exportInventoryReport(InventoryReportResponse report, String companyName) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
            PdfWriter.getInstance(pdf, output);
            pdf.open();

            boolean showValue = report.totalClosingValue() != null;

            pdf.add(paragraph(companyName.toUpperCase(), fonts.bold(11), Element.ALIGN_LEFT));
            pdf.add(spacer(8));
            pdf.add(paragraph("BÁO CÁO NHẬP - XUẤT - TỒN", fonts.bold(16, PRIMARY_COLOR), Element.ALIGN_CENTER));
            pdf.add(paragraph(
                    "Kỳ báo cáo: " + VietnameseFormat.date(report.fromDate()) + " đến "
                            + VietnameseFormat.date(report.toDate()),
                    fonts.normal(10),
                    Element.ALIGN_CENTER));
            pdf.add(paragraph("Kho: " + report.warehouseName(), fonts.normal(10), Element.ALIGN_CENTER));
            pdf.add(paragraph(
                    "Người xuất: " + nullToDash(report.exportedBy()) + " | Thời điểm: "
                            + VietnameseFormat.dateTimeWithSeconds(report.exportedAt()),
                    fonts.normal(9, Color.DARK_GRAY),
                    Element.ALIGN_CENTER));
            pdf.add(spacer(12));

            PdfPTable table = showValue
                    ? new PdfPTable(new float[] {0.6f, 1.4f, 3.6f, 0.9f, 1.3f, 1.3f, 1.3f, 1.3f, 1.8f})
                    : new PdfPTable(new float[] {0.6f, 1.5f, 4.2f, 0.9f, 1.4f, 1.4f, 1.4f, 1.4f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            addHeaderCell(table, "STT");
            addHeaderCell(table, "Mã SKU");
            addHeaderCell(table, "Tên sản phẩm");
            addHeaderCell(table, "ĐVT");
            addHeaderCell(table, "Tồn đầu kỳ");
            addHeaderCell(table, "Nhập");
            addHeaderCell(table, "Xuất");
            addHeaderCell(table, "Tồn cuối kỳ");
            if (showValue) {
                addHeaderCell(table, "Giá trị tồn");
            }

            int order = 1;
            for (InventoryReportRowResponse row : report.rows()) {
                addBodyCell(table, String.valueOf(order++), Element.ALIGN_CENTER);
                addBodyCell(table, row.sku(), Element.ALIGN_LEFT);
                addBodyCell(table, row.productName(), Element.ALIGN_LEFT);
                addBodyCell(table, row.unitName(), Element.ALIGN_CENTER);
                addBodyCell(table, VietnameseFormat.quantity(row.openingQuantity()), Element.ALIGN_RIGHT);
                addBodyCell(table, VietnameseFormat.quantity(row.receivedQuantity()), Element.ALIGN_RIGHT);
                addBodyCell(table, VietnameseFormat.quantity(row.issuedQuantity()), Element.ALIGN_RIGHT);
                addBodyCell(table, VietnameseFormat.quantity(row.closingQuantity()), Element.ALIGN_RIGHT);
                if (showValue) {
                    addBodyCell(table, VietnameseFormat.money(row.closingValue()), Element.ALIGN_RIGHT);
                }
            }

            // Dòng tổng cộng
            PdfPCell totalLabel = new PdfPCell(new Phrase("TỔNG CỘNG", fonts.bold(9)));
            totalLabel.setColspan(4);
            totalLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalLabel.setBackgroundColor(TOTAL_BACKGROUND);
            totalLabel.setPadding(5);
            table.addCell(totalLabel);
            addTotalCell(table, VietnameseFormat.quantity(report.totalOpeningQuantity()));
            addTotalCell(table, VietnameseFormat.quantity(report.totalReceivedQuantity()));
            addTotalCell(table, VietnameseFormat.quantity(report.totalIssuedQuantity()));
            addTotalCell(table, VietnameseFormat.quantity(report.totalClosingQuantity()));
            if (showValue) {
                addTotalCell(table, VietnameseFormat.money(report.totalClosingValue()));
            }

            pdf.add(table);
            pdf.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new com.smartstock.common.BusinessException(
                    "Không tạo được file PDF: " + ex.getMessage(), "PDF_EXPORT_FAILED");
        }
    }

    /** Xuất thẻ kho. */
    public byte[] exportStockCard(StockCardResponse card, String companyName) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(pdf, output);
            pdf.open();

            pdf.add(paragraph(companyName.toUpperCase(), fonts.bold(11), Element.ALIGN_LEFT));
            pdf.add(spacer(8));
            pdf.add(paragraph("THẺ KHO", fonts.bold(17, PRIMARY_COLOR), Element.ALIGN_CENTER));
            pdf.add(paragraph(
                    card.sku() + " - " + card.productName() + " (" + card.unitName() + ")",
                    fonts.bold(11),
                    Element.ALIGN_CENTER));
            pdf.add(paragraph("Kho: " + card.warehouseName(), fonts.normal(10), Element.ALIGN_CENTER));
            if (card.fromDate() != null) {
                pdf.add(paragraph(
                        "Kỳ: " + VietnameseFormat.date(card.fromDate()) + " đến "
                                + VietnameseFormat.date(card.toDate()),
                        fonts.normal(10),
                        Element.ALIGN_CENTER));
            }
            pdf.add(spacer(12));

            PdfPTable table = new PdfPTable(new float[] {1.5f, 1.8f, 3.4f, 1.1f, 1.1f, 1.3f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            addHeaderCell(table, "Ngày");
            addHeaderCell(table, "Mã chứng từ");
            addHeaderCell(table, "Diễn giải");
            addHeaderCell(table, "Nhập");
            addHeaderCell(table, "Xuất");
            addHeaderCell(table, "Tồn lũy kế");

            // Dòng tồn đầu kỳ
            PdfPCell openingLabel = new PdfPCell(new Phrase("Tồn đầu kỳ", fonts.bold(9)));
            openingLabel.setColspan(5);
            openingLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            openingLabel.setPadding(5);
            table.addCell(openingLabel);
            addBodyCell(table, VietnameseFormat.quantity(card.openingQuantity()), Element.ALIGN_RIGHT);

            for (var entry : card.entries()) {
                addBodyCell(table, VietnameseFormat.dateTime(entry.transactionDate()), Element.ALIGN_CENTER);
                addBodyCell(table, entry.refCode(), Element.ALIGN_LEFT);
                addBodyCell(table, entry.description(), Element.ALIGN_LEFT);
                addBodyCell(
                        table,
                        entry.quantityIn() == null ? "" : VietnameseFormat.quantity(entry.quantityIn()),
                        Element.ALIGN_RIGHT);
                addBodyCell(
                        table,
                        entry.quantityOut() == null ? "" : VietnameseFormat.quantity(entry.quantityOut()),
                        Element.ALIGN_RIGHT);
                addBodyCell(table, VietnameseFormat.quantity(entry.balanceAfter()), Element.ALIGN_RIGHT);
            }

            PdfPCell totalLabel = new PdfPCell(new Phrase("TỔNG CỘNG", fonts.bold(9)));
            totalLabel.setColspan(3);
            totalLabel.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalLabel.setBackgroundColor(TOTAL_BACKGROUND);
            totalLabel.setPadding(5);
            table.addCell(totalLabel);
            addTotalCell(table, VietnameseFormat.quantity(card.totalIn()));
            addTotalCell(table, VietnameseFormat.quantity(card.totalOut()));
            addTotalCell(table, VietnameseFormat.quantity(card.closingQuantity()));

            pdf.add(table);
            pdf.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new com.smartstock.common.BusinessException(
                    "Không tạo được file PDF: " + ex.getMessage(), "PDF_EXPORT_FAILED");
        }
    }

    /** Xuất báo cáo hàng tồn lâu. */
    public byte[] exportSlowMovingReport(SlowMovingReportResponse report, String companyName) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
            PdfWriter.getInstance(pdf, output);
            pdf.open();

            boolean showValue = report.totalValue() != null;

            pdf.add(paragraph(companyName.toUpperCase(), fonts.bold(11), Element.ALIGN_LEFT));
            pdf.add(spacer(8));
            pdf.add(paragraph("BÁO CÁO HÀNG TỒN LÂU", fonts.bold(16, PRIMARY_COLOR), Element.ALIGN_CENTER));
            pdf.add(paragraph(
                    "Không phát sinh xuất trong " + report.thresholdDays() + " ngày | Kho: "
                            + report.warehouseName(),
                    fonts.normal(10),
                    Element.ALIGN_CENTER));
            pdf.add(spacer(12));

            PdfPTable table = showValue
                    ? new PdfPTable(new float[] {0.6f, 1.4f, 3.4f, 0.9f, 1.8f, 1.3f, 1.5f, 1.3f, 1.6f})
                    : new PdfPTable(new float[] {0.6f, 1.5f, 3.8f, 0.9f, 2.0f, 1.4f, 1.6f, 1.4f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            addHeaderCell(table, "STT");
            addHeaderCell(table, "Mã SKU");
            addHeaderCell(table, "Tên sản phẩm");
            addHeaderCell(table, "ĐVT");
            addHeaderCell(table, "Kho");
            addHeaderCell(table, "Tồn");
            addHeaderCell(table, "Xuất cuối");
            addHeaderCell(table, "Số ngày");
            if (showValue) {
                addHeaderCell(table, "Giá trị tồn");
            }

            int order = 1;
            for (SlowMovingRowResponse row : report.rows()) {
                addBodyCell(table, String.valueOf(order++), Element.ALIGN_CENTER);
                addBodyCell(table, row.sku(), Element.ALIGN_LEFT);
                addBodyCell(table, row.productName(), Element.ALIGN_LEFT);
                addBodyCell(table, row.unitName(), Element.ALIGN_CENTER);
                addBodyCell(table, row.warehouseName(), Element.ALIGN_LEFT);
                addBodyCell(table, VietnameseFormat.quantity(row.quantity()), Element.ALIGN_RIGHT);
                addBodyCell(
                        table,
                        row.lastIssueDate() == null ? "Chưa xuất" : VietnameseFormat.date(row.lastIssueDate()),
                        Element.ALIGN_CENTER);
                addBodyCell(
                        table,
                        row.daysSinceLastIssue() == null ? "-" : String.valueOf(row.daysSinceLastIssue()),
                        Element.ALIGN_CENTER);
                if (showValue) {
                    addBodyCell(table, VietnameseFormat.money(row.stockValue()), Element.ALIGN_RIGHT);
                }
            }

            pdf.add(table);

            if (showValue) {
                pdf.add(spacer(8));
                pdf.add(paragraph(
                        "Tổng giá trị hàng tồn lâu: " + VietnameseFormat.currency(report.totalValue()),
                        fonts.bold(11),
                        Element.ALIGN_RIGHT));
            }

            pdf.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new com.smartstock.common.BusinessException(
                    "Không tạo được file PDF: " + ex.getMessage(), "PDF_EXPORT_FAILED");
        }
    }

    // ==================================================================
    // Hàm hỗ trợ dựng phần tử PDF
    // ==================================================================

    private Paragraph paragraph(String text, com.lowagie.text.Font font, int alignment) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(alignment);
        return paragraph;
    }

    /**
     * Dòng trống để giãn cách.
     *
     * <p>Vẫn truyền font Noto Sans dù nội dung chỉ là dấu cách: nếu để OpenPDF dùng font
     * mặc định, file PDF sẽ chứa thêm tham chiếu tới Helvetica - một font không có dấu
     * tiếng Việt. Giữ toàn bộ tài liệu dùng một font giúp file gọn và không có rủi ro
     * phần tử nào vô tình rơi về font thiếu dấu.
     */
    private Paragraph spacer(float height) {
        Paragraph spacer = new Paragraph(" ", fonts.normal(9));
        spacer.setSpacingAfter(height);
        return spacer;
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, fonts.bold(10)));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "" : value, fonts.normal(10)));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(4);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, fonts.bold(9)));
        cell.setBackgroundColor(HEADER_BACKGROUND);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, fonts.normal(9)));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addTotalCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, fonts.bold(9)));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setBackgroundColor(TOTAL_BACKGROUND);
        cell.setPadding(5);
        table.addCell(cell);
    }

    /** Một ô ký tên: nhãn chức danh ở trên, chỗ trống để ký, tên người ở dưới. */
    private void addSignatureCell(PdfPTable table, String role, String name) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph roleParagraph = new Paragraph(role, fonts.bold(10));
        roleParagraph.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(roleParagraph);

        Paragraph hint = new Paragraph("(Ký, ghi rõ họ tên)", fonts.normal(8, Color.GRAY));
        hint.setAlignment(Element.ALIGN_CENTER);
        hint.setSpacingAfter(34); // chỗ trống để ký tay
        cell.addElement(hint);

        Paragraph nameParagraph = new Paragraph(name == null ? "" : name, fonts.normal(9));
        nameParagraph.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(nameParagraph);

        table.addCell(cell);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
