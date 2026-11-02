package com.smartstock.stocktake;

import com.smartstock.auth.AuthDtos.MessageResponse;
import com.smartstock.common.PageResponse;
import com.smartstock.document.DocumentDtos.ReasonRequest;
import com.smartstock.document.DocumentStatus;
import com.smartstock.stocktake.StocktakeDtos.CreateStocktakeRequest;
import com.smartstock.stocktake.StocktakeDtos.SaveCountRequest;
import com.smartstock.stocktake.StocktakeDtos.StocktakeDetailResponse;
import com.smartstock.stocktake.StocktakeDtos.StocktakeSummaryResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API kiểm kê kho. */
@RestController
@RequestMapping("/api/stocktakes")
@RequiredArgsConstructor
@Tag(name = "10. Kiểm kê kho", description = "Tạo đợt kiểm kê, nhập số thực tế và duyệt điều chỉnh tồn")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @GetMapping
    @Operation(summary = "Danh sách đợt kiểm kê")
    public PageResponse<StocktakeSummaryResponse> search(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return stocktakeService.search(warehouseId, status, fromDate, toDate, pageable);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Chi tiết đợt kiểm kê",
            description = "Kèm cảnh báo nếu có phiếu nhập/xuất hoàn tất sau khi đợt kiểm kê được tạo")
    public StocktakeDetailResponse getById(@PathVariable Long id) {
        return stocktakeService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Tạo đợt kiểm kê",
            description = "Hệ thống chụp lại số tồn và giá vốn hiện tại của từng sản phẩm trong kho")
    public StocktakeDetailResponse create(@Valid @RequestBody CreateStocktakeRequest request) {
        return stocktakeService.create(request);
    }

    @PutMapping("/{id}/count")
    @Operation(
            summary = "Lưu số đếm thực tế",
            description = "Gọi được nhiều lần, mỗi lần gửi một phần danh sách (phù hợp khi đếm trên điện thoại)")
    public StocktakeDetailResponse saveCount(@PathVariable Long id, @Valid @RequestBody SaveCountRequest request) {
        return stocktakeService.saveCount(id, request);
    }

    @PutMapping("/{id}/submit")
    @Operation(summary = "Gửi duyệt kiểm kê", description = "Bắt buộc đã nhập số thực tế cho mọi mặt hàng")
    public StocktakeDetailResponse submit(@PathVariable Long id) {
        return stocktakeService.submit(id);
    }

    @PutMapping("/{id}/approve")
    @Operation(
            summary = "Duyệt kiểm kê",
            description = "Điều chỉnh tồn kho về đúng số thực tế, ghi giao dịch loại ADJUSTMENT")
    public StocktakeDetailResponse approve(@PathVariable Long id) {
        return stocktakeService.approve(id);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Từ chối kiểm kê", description = "Bắt buộc nhập lý do")
    public StocktakeDetailResponse reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return stocktakeService.reject(id, request.reason());
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Hủy đợt kiểm kê chưa duyệt")
    public MessageResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        stocktakeService.cancel(id, request.reason());
        return new MessageResponse("Đã hủy đợt kiểm kê.");
    }
}
