package com.smartstock.catalog;

import com.smartstock.catalog.CatalogDtos.ProductDetailResponse;
import com.smartstock.catalog.CatalogDtos.ProductLookupResponse;
import com.smartstock.catalog.CatalogDtos.ProductResponse;
import com.smartstock.catalog.CatalogDtos.SaveProductRequest;
import com.smartstock.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** API sản phẩm, bao gồm tra cứu nhanh theo mã vạch để phục vụ quét mã. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "05. Sản phẩm", description = "Quản lý sản phẩm, tra cứu theo mã vạch")
public class ProductController {

    private static final String CAN_EDIT = "hasAnyRole('ADMIN','MANAGER')";

    private final ProductService productService;

    @GetMapping
    @Operation(
            summary = "Danh sách sản phẩm",
            description = "Tìm theo mã/tên/mã vạch, lọc theo loại hàng và trạng thái kinh doanh")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) RecordStatus status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return productService.search(keyword, categoryId, status, pageable);
    }

    @GetMapping("/lookup")
    @Operation(
            summary = "Tra cứu nhanh sản phẩm",
            description = "Dùng cho ô tìm sản phẩm khi lập phiếu. Trả kèm tồn hiện có tại kho đang làm việc.")
    public List<ProductLookupResponse> lookup(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "20") int limit) {
        return productService.lookup(keyword, warehouseId, limit);
    }

    @GetMapping("/by-barcode")
    @Operation(
            summary = "Tìm sản phẩm theo mã vạch",
            description = "Phục vụ quét mã bằng camera điện thoại hoặc máy quét USB. "
                    + "Nếu không khớp mã vạch, hệ thống thử khớp theo mã SKU.")
    public ProductLookupResponse findByBarcode(
            @RequestParam String code, @RequestParam(required = false) Long warehouseId) {
        return productService.findByBarcode(code, warehouseId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sản phẩm")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping("/{id}/detail")
    @Operation(
            summary = "Chi tiết sản phẩm kèm tồn kho từng kho",
            description = "Vai trò STAFF không nhận được giá vốn và giá trị tồn")
    public ProductDetailResponse getDetail(@PathVariable Long id) {
        return productService.getDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Thêm sản phẩm")
    public ProductResponse create(@Valid @RequestBody SaveProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Sửa sản phẩm")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody SaveProductRequest request) {
        return productService.update(id, request);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(CAN_EDIT)
    @Operation(
            summary = "Đổi trạng thái kinh doanh",
            description = "Sản phẩm Ngừng kinh doanh sẽ không chọn được khi lập phiếu mới")
    public ProductResponse changeStatus(@PathVariable Long id, @RequestParam RecordStatus status) {
        return productService.changeStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Xóa sản phẩm", description = "Xóa mềm; bị chặn nếu sản phẩm còn tồn kho")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
