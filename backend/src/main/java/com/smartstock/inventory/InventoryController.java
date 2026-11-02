package com.smartstock.inventory;

import com.smartstock.common.PageResponse;
import com.smartstock.inventory.InventoryDtos.InventoryRowResponse;
import com.smartstock.inventory.InventoryDtos.LowStockRowResponse;
import com.smartstock.inventory.InventoryDtos.StockCardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tồn kho và thẻ kho (chỉ đọc).
 *
 * <p>Mọi kết quả đã được lọc theo kho mà người dùng được gán. Vai trò STAFF không nhận
 * được giá vốn và giá trị tồn - việc lược bỏ làm ở backend nên gọi API trực tiếp cũng
 * không lấy được.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "09. Tồn kho", description = "Xem tồn kho, cảnh báo tồn thấp và thẻ kho")
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;

    @GetMapping
    @Operation(
            summary = "Danh sách tồn kho",
            description = "Lọc theo kho, loại hàng, tình trạng tồn (ALL/IN_STOCK/LOW/OUT); "
                    + "tìm theo mã, tên hoặc mã vạch sản phẩm")
    public PageResponse<InventoryRowResponse> search(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "ALL") String stockState,
            @PageableDefault(size = 20) Pageable pageable) {
        return inventoryQueryService.search(warehouseId, categoryId, keyword, stockState, pageable);
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Danh sách hàng sắp hết", description = "Tồn bằng hoặc dưới mức tồn tối thiểu")
    public List<LowStockRowResponse> listLowStock(@RequestParam(required = false) Long warehouseId) {
        return inventoryQueryService.listLowStock(warehouseId);
    }

    @GetMapping("/stock-card")
    @Operation(
            summary = "Thẻ kho của một sản phẩm",
            description = "Lịch sử biến động theo thời gian, kèm tồn đầu kỳ và tồn lũy kế từng dòng")
    public StockCardResponse getStockCard(
            @RequestParam Long productId,
            @RequestParam Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return inventoryQueryService.getStockCard(productId, warehouseId, fromDate, toDate);
    }
}
