package com.smartstock.stocktake;

import com.smartstock.document.DocumentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO của phân hệ kiểm kê kho. */
public final class StocktakeDtos {

    private StocktakeDtos() {}

    /**
     * Tạo đợt kiểm kê mới.
     *
     * @param categoryId lọc theo loại hàng; để trống nghĩa là kiểm kê toàn bộ sản phẩm của kho
     */
    public record CreateStocktakeRequest(
            @NotNull(message = "Vui lòng chọn kho") Long warehouseId,
            @NotNull(message = "Vui lòng chọn ngày kiểm kê") LocalDate documentDate,
            Long categoryId,
            @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String note) {}

    /** Nhập số đếm thực tế cho nhiều dòng cùng lúc (lưu theo lô khi đếm trên điện thoại). */
    public record SaveCountRequest(@Valid List<CountItemRequest> items) {}

    /**
     * Số đếm thực tế của một dòng.
     *
     * @param actualQuantity để trống nghĩa là xóa số đã đếm (quay về trạng thái chưa đếm)
     */
    public record CountItemRequest(
            @NotNull(message = "Thiếu mã dòng kiểm kê") Long itemId,
            @DecimalMin(value = "0", message = "Số lượng thực tế không được âm")
                    @Digits(integer = 15, fraction = 3, message = "Số lượng tối đa 3 chữ số thập phân")
                    BigDecimal actualQuantity,
            @Size(max = 500) String note) {}

    /** Đợt kiểm kê dạng rút gọn cho màn hình danh sách. */
    public record StocktakeSummaryResponse(
            Long id,
            String code,
            Long warehouseId,
            String warehouseName,
            LocalDate documentDate,
            String categoryName,
            DocumentStatus status,
            String statusName,
            int totalItems,
            int countedItems,
            int differenceItems,
            BigDecimal totalDifferenceValue,
            String createdByName,
            String approvedByName,
            LocalDateTime createdAt,
            LocalDateTime approvedAt) {}

    /**
     * Chi tiết đợt kiểm kê.
     *
     * @param warningDocumentCodes mã các phiếu nhập/xuất đã hoàn tất tại kho này SAU khi
     *     đợt kiểm kê được tạo - số tồn hệ thống đã chụp không còn phản ánh đúng thực tế
     */
    public record StocktakeDetailResponse(
            Long id,
            String code,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            LocalDate documentDate,
            Long categoryId,
            String categoryName,
            String note,
            DocumentStatus status,
            String statusName,
            List<StocktakeItemResponse> items,
            int totalItems,
            int countedItems,
            int differenceItems,
            BigDecimal totalDifferenceValue,
            String createdByName,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            String approvedByName,
            LocalDateTime approvedAt,
            String rejectReason,
            String cancelReason,
            boolean canEdit,
            boolean canApprove,
            List<String> warningDocumentCodes) {}

    /**
     * Một dòng kiểm kê.
     *
     * @param difference chênh lệch = thực tế - hệ thống
     * @param differenceValue giá trị chênh lệch; {@code null} với vai trò STAFF
     */
    public record StocktakeItemResponse(
            Long id,
            int lineNo,
            Long productId,
            String sku,
            String barcode,
            String productName,
            String unitName,
            BigDecimal systemQuantity,
            BigDecimal actualQuantity,
            BigDecimal difference,
            BigDecimal unitCost,
            BigDecimal differenceValue,
            boolean counted,
            String note) {}
}
