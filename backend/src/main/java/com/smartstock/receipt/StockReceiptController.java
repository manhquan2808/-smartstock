package com.smartstock.receipt;

import com.smartstock.common.PageResponse;
import com.smartstock.document.DocumentDtos.DocumentDetailResponse;
import com.smartstock.document.DocumentDtos.DocumentSummaryResponse;
import com.smartstock.document.DocumentDtos.ReasonRequest;
import com.smartstock.document.DocumentDtos.SaveReceiptRequest;
import com.smartstock.document.DocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API phiếu nhập kho.
 *
 * <p>Phân quyền được kiểm tra trong service (vì phụ thuộc kho được gán, không chỉ vai trò).
 */
@RestController
@RequestMapping("/api/stock-receipts")
@RequiredArgsConstructor
@Tag(name = "07. Phiếu nhập kho", description = "Lập, gửi duyệt, duyệt, từ chối và hủy phiếu nhập")
public class StockReceiptController {

    private final StockReceiptService receiptService;

    @GetMapping
    @Operation(
            summary = "Danh sách phiếu nhập",
            description = "Lọc theo kho, loại phiếu, trạng thái, khoảng ngày chứng từ, người lập; tìm theo mã phiếu")
    public PageResponse<DocumentSummaryResponse> search(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) ReceiptType receiptType,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return receiptService.search(
                warehouseId, receiptType, status, fromDate, toDate, createdBy, keyword, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu nhập")
    public DocumentDetailResponse getById(@PathVariable Long id) {
        return receiptService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Lập phiếu nhập",
            description = "Mặc định lưu ở trạng thái Nháp. Đặt submit=true để gửi duyệt ngay.")
    public DocumentDetailResponse create(
            @Valid @RequestBody SaveReceiptRequest request,
            @RequestParam(defaultValue = "false") boolean submit) {
        return receiptService.create(request, submit);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa phiếu nhập", description = "Chỉ phiếu Nháp hoặc Bị từ chối, và chỉ người lập mới sửa")
    public DocumentDetailResponse update(@PathVariable Long id, @Valid @RequestBody SaveReceiptRequest request) {
        return receiptService.update(id, request);
    }

    @PutMapping("/{id}/submit")
    @Operation(summary = "Gửi duyệt", description = "Thông báo và gửi email cho quản lý của kho")
    public DocumentDetailResponse submit(@PathVariable Long id) {
        return receiptService.submit(id);
    }

    @PutMapping("/{id}/approve")
    @Operation(
            summary = "Duyệt phiếu nhập",
            description = "Cộng tồn kho, cập nhật giá vốn bình quân và ghi sổ giao dịch. Chỉ ADMIN/MANAGER.")
    public DocumentDetailResponse approve(@PathVariable Long id) {
        return receiptService.approve(id);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Từ chối phiếu nhập", description = "Bắt buộc nhập lý do")
    public DocumentDetailResponse reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return receiptService.reject(id, request.reason());
    }

    @PutMapping("/{id}/cancel")
    @Operation(
            summary = "Hủy phiếu nhập đã hoàn tất",
            description = "Bắt buộc nhập lý do. Hệ thống sinh giao dịch đảo ngược; "
                    + "bị chặn nếu tồn không còn đủ để trừ lại.")
    public DocumentDetailResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return receiptService.cancel(id, request.reason());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa phiếu nháp", description = "Phiếu đã gửi duyệt hoặc hoàn tất không xóa được")
    public void delete(@PathVariable Long id) {
        receiptService.delete(id);
    }
}
