package com.smartstock.warehouse;

import com.smartstock.common.PageResponse;
import com.smartstock.warehouse.WarehouseDtos.SaveWarehouseRequest;
import com.smartstock.warehouse.WarehouseDtos.WarehouseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
 * API kho / chi nhánh.
 *
 * <p>Chỉ ADMIN được thêm/sửa/ngừng hoạt động (đã chặn theo đường dẫn trong
 * {@code SecurityConfig}). MANAGER và STAFF gọi được các API xem.
 */
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "06. Kho", description = "Quản lý kho và chi nhánh")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @Operation(summary = "Danh sách kho", description = "Người dùng không phải ADMIN chỉ thấy kho được gán")
    public PageResponse<WarehouseResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return warehouseService.search(keyword, active, pageable);
    }

    @GetMapping("/my")
    @Operation(
            summary = "Các kho tôi được làm việc",
            description = "Dùng cho bộ chọn kho đang làm việc ở thanh tiêu đề")
    public List<WarehouseResponse> listMyWarehouses() {
        return warehouseService.listMyWarehouses();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết kho")
    public WarehouseResponse getById(@PathVariable Long id) {
        return warehouseService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm kho (chỉ ADMIN)")
    public WarehouseResponse create(@Valid @RequestBody SaveWarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa kho (chỉ ADMIN)")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody SaveWarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Ngừng hoạt động kho (chỉ ADMIN)",
            description = "Không xóa cứng vì phiếu cũ còn tham chiếu. Bị chặn nếu kho còn tồn kho.")
    public void deactivate(@PathVariable Long id) {
        warehouseService.deactivate(id);
    }
}
