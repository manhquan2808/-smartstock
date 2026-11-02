package com.smartstock.issue;

import com.smartstock.common.PageResponse;
import com.smartstock.document.DocumentDtos.DocumentDetailResponse;
import com.smartstock.document.DocumentDtos.DocumentSummaryResponse;
import com.smartstock.document.DocumentDtos.ReasonRequest;
import com.smartstock.document.DocumentDtos.SaveIssueItemRequest;
import com.smartstock.document.DocumentDtos.SaveIssueRequest;
import com.smartstock.document.DocumentDtos.StockCheckResponse;
import com.smartstock.document.DocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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

/** API phiếu xuất kho. */
@RestController
@RequestMapping("/api/stock-issues")
@RequiredArgsConstructor
@Tag(name = "08. Phiếu xuất kho", description = "Lập, gửi duyệt, duyệt, từ chối và hủy phiếu xuất")
public class StockIssueController {

    private final StockIssueService issueService;

    @GetMapping
    @Operation(summary = "Danh sách phiếu xuất")
    public PageResponse<DocumentSummaryResponse> search(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) IssueType issueType,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return issueService.search(warehouseId, issueType, status, fromDate, toDate, createdBy, keyword, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu xuất")
    public DocumentDetailResponse getById(@PathVariable Long id) {
        return issueService.getById(id);
    }

    @PostMapping("/check-stock")
    @Operation(
            summary = "Kiểm tra tồn trước khi lưu",
            description = "Dùng cho cảnh báo tức thời trên màn hình lập phiếu. Trả về danh sách dòng thiếu tồn.")
    public StockCheckResponse checkStock(
            @RequestParam Long warehouseId, @Valid @RequestBody List<SaveIssueItemRequest> items) {
        return issueService.checkStock(warehouseId, items);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Lập phiếu xuất",
            description = "Lưu nháp vẫn thành công khi thiếu tồn (chỉ cảnh báo). Đặt submit=true để gửi duyệt ngay "
                    + "- khi đó thiếu tồn sẽ bị chặn.")
    public DocumentDetailResponse create(
            @Valid @RequestBody SaveIssueRequest request, @RequestParam(defaultValue = "false") boolean submit) {
        return issueService.create(request, submit);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa phiếu xuất")
    public DocumentDetailResponse update(@PathVariable Long id, @Valid @RequestBody SaveIssueRequest request) {
        return issueService.update(id, request);
    }

    @PutMapping("/{id}/submit")
    @Operation(summary = "Gửi duyệt", description = "Bị chặn nếu có dòng vượt tồn, kèm danh sách sản phẩm thiếu")
    public DocumentDetailResponse submit(@PathVariable Long id) {
        return issueService.submit(id);
    }

    @PutMapping("/{id}/approve")
    @Operation(
            summary = "Duyệt phiếu xuất",
            description = "Trừ tồn, ghi giá vốn tại thời điểm xuất và ghi sổ giao dịch. "
                    + "Kiểm tra lại tồn trên dòng đã khóa nên không thể xuất âm.")
    public DocumentDetailResponse approve(@PathVariable Long id) {
        return issueService.approve(id);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Từ chối phiếu xuất", description = "Bắt buộc nhập lý do")
    public DocumentDetailResponse reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return issueService.reject(id, request.reason());
    }

    @PutMapping("/{id}/cancel")
    @Operation(
            summary = "Hủy phiếu xuất đã hoàn tất",
            description = "Bắt buộc nhập lý do. Hệ thống sinh giao dịch đảo ngược làm tăng tồn trở lại.")
    public DocumentDetailResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return issueService.cancel(id, request.reason());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa phiếu nháp")
    public void delete(@PathVariable Long id) {
        issueService.delete(id);
    }
}
