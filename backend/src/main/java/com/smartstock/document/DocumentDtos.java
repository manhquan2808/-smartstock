package com.smartstock.document;

import com.smartstock.issue.IssueType;
import com.smartstock.receipt.ReceiptType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO dùng chung cho phiếu nhập và phiếu xuất.
 *
 * <p>Hai loại phiếu có cùng khuôn dạng dữ liệu (kho, ngày chứng từ, đối tác, danh sách
 * dòng hàng, luồng duyệt) nên gom chung ở đây, chỉ khác trường loại phiếu.
 */
public final class DocumentDtos {

    private DocumentDtos() {}

    // ======================= Dữ liệu gửi lên =======================

    /**
     * Dữ liệu tạo/sửa phiếu NHẬP kho.
     *
     * @param warehouseId kho nhận hàng; backend kiểm tra người dùng có được gán kho này không
     * @param documentDate ngày chứng từ (có thể lùi ngày so với ngày lập)
     */
    public record SaveReceiptRequest(
            @NotNull(message = "Vui lòng chọn kho") Long warehouseId,
            @NotNull(message = "Vui lòng chọn loại phiếu nhập") ReceiptType receiptType,
            @NotNull(message = "Vui lòng chọn ngày chứng từ") LocalDate documentDate,
            Long supplierId,
            Long customerId,
            @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason,
            @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String note,
            @NotEmpty(message = "Phiếu phải có ít nhất một dòng hàng")
                    @Valid
                    List<SaveReceiptItemRequest> items) {}

    /** Một dòng hàng của phiếu nhập. */
    public record SaveReceiptItemRequest(
            @NotNull(message = "Vui lòng chọn sản phẩm") Long productId,
            @NotNull(message = "Vui lòng nhập số lượng")
                    @DecimalMin(value = "0.001", message = "Số lượng phải lớn hơn 0")
                    @Digits(integer = 15, fraction = 3, message = "Số lượng tối đa 3 chữ số thập phân")
                    BigDecimal quantity,
            @NotNull(message = "Vui lòng nhập đơn giá")
                    @DecimalMin(value = "0", message = "Đơn giá không được âm")
                    @Digits(integer = 16, fraction = 2, message = "Đơn giá tối đa 2 chữ số thập phân")
                    BigDecimal unitPrice,
            @Size(max = 500) String note) {}

    /** Dữ liệu tạo/sửa phiếu XUẤT kho. Người lập không nhập đơn giá (hệ thống dùng giá vốn). */
    public record SaveIssueRequest(
            @NotNull(message = "Vui lòng chọn kho") Long warehouseId,
            @NotNull(message = "Vui lòng chọn loại phiếu xuất") IssueType issueType,
            @NotNull(message = "Vui lòng chọn ngày chứng từ") LocalDate documentDate,
            Long customerId,
            Long supplierId,
            @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason,
            @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String note,
            @NotEmpty(message = "Phiếu phải có ít nhất một dòng hàng") @Valid List<SaveIssueItemRequest> items) {}

    /** Một dòng hàng của phiếu xuất. */
    public record SaveIssueItemRequest(
            @NotNull(message = "Vui lòng chọn sản phẩm") Long productId,
            @NotNull(message = "Vui lòng nhập số lượng")
                    @DecimalMin(value = "0.001", message = "Số lượng phải lớn hơn 0")
                    @Digits(integer = 15, fraction = 3, message = "Số lượng tối đa 3 chữ số thập phân")
                    BigDecimal quantity,
            @Size(max = 500) String note) {}

    /** Lý do khi từ chối hoặc hủy phiếu - luôn bắt buộc để truy vết được về sau. */
    public record ReasonRequest(
            @NotNull(message = "Vui lòng nhập lý do")
                    @Size(min = 3, max = 500, message = "Lý do phải có từ 3 đến 500 ký tự")
                    String reason) {}

    // ======================= Dữ liệu trả về =======================

    /**
     * Phiếu dạng rút gọn cho màn hình danh sách.
     *
     * @param totalAmount {@code null} với vai trò STAFF khi phiếu không do họ lập
     */
    public record DocumentSummaryResponse(
            Long id,
            String code,
            String documentTypeName,
            Long warehouseId,
            String warehouseName,
            String typeCode,
            String typeName,
            LocalDate documentDate,
            String partnerName,
            DocumentStatus status,
            String statusName,
            BigDecimal totalAmount,
            int itemCount,
            BigDecimal totalQuantity,
            Long createdById,
            String createdByName,
            String approvedByName,
            LocalDateTime createdAt,
            LocalDateTime approvedAt) {}

    /**
     * Chi tiết đầy đủ của một phiếu.
     *
     * @param canEdit frontend dùng để ẩn/hiện nút Sửa
     * @param canApprove frontend dùng để ẩn/hiện nút Duyệt và Từ chối
     * @param canCancel frontend dùng để ẩn/hiện nút Hủy
     */
    public record DocumentDetailResponse(
            Long id,
            String code,
            String documentTypeName,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            String typeCode,
            String typeName,
            LocalDate documentDate,
            Long partnerId,
            String partnerCode,
            String partnerName,
            String reason,
            String note,
            DocumentStatus status,
            String statusName,
            BigDecimal totalAmount,
            List<DocumentItemResponse> items,
            Long createdById,
            String createdByName,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            String approvedByName,
            LocalDateTime approvedAt,
            String rejectReason,
            String cancelledByName,
            LocalDateTime cancelledAt,
            String cancelReason,
            boolean canEdit,
            boolean canApprove,
            boolean canCancel) {}

    /**
     * Một dòng hàng khi trả về giao diện.
     *
     * @param unitPrice đơn giá nhập, hoặc giá vốn với phiếu xuất; {@code null} nếu người
     *     xem là STAFF và phiếu không do họ lập
     * @param quantityOnHand tồn hiện tại, giúp giao diện tô đỏ dòng xuất vượt tồn
     */
    public record DocumentItemResponse(
            Long id,
            int lineNo,
            Long productId,
            String sku,
            String barcode,
            String productName,
            String unitName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            BigDecimal quantityOnHand,
            String note) {}

    /**
     * Kết quả kiểm tra tồn trước khi gửi duyệt phiếu xuất.
     *
     * @param sufficient true nghĩa là đủ hàng cho mọi dòng
     */
    public record StockCheckResponse(boolean sufficient, List<StockShortageRow> shortages) {}

    /** Một dòng thiếu tồn. */
    public record StockShortageRow(
            Long productId, String sku, String productName, BigDecimal requested, BigDecimal available) {}
}
