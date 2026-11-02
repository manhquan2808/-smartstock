package com.smartstock.catalog;

import com.smartstock.catalog.CatalogDtos.CategoryResponse;
import com.smartstock.catalog.CatalogDtos.PartnerResponse;
import com.smartstock.catalog.CatalogDtos.SavePartnerRequest;
import com.smartstock.catalog.CatalogDtos.SaveSimpleCatalogRequest;
import com.smartstock.catalog.CatalogDtos.UnitResponse;
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

/**
 * API danh mục: loại hàng, đơn vị tính, nhà cung cấp, khách hàng.
 *
 * <p>Phân quyền: STAFF chỉ xem (các phương thức GET), còn thêm/sửa/xóa dành cho
 * ADMIN và MANAGER - khai báo bằng {@code @PreAuthorize} trên từng phương thức ghi.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "04. Danh mục", description = "Loại hàng, đơn vị tính, nhà cung cấp, khách hàng")
public class CatalogController {

    /** Quyền được phép thêm/sửa/xóa danh mục. */
    private static final String CAN_EDIT = "hasAnyRole('ADMIN','MANAGER')";

    private final CategoryService categoryService;
    private final PartnerService partnerService;

    // ================================ Loại hàng ================================

    @GetMapping("/categories")
    @Operation(summary = "Danh sách loại hàng (phân trang)")
    public PageResponse<CategoryResponse> searchCategories(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return categoryService.searchCategories(keyword, pageable);
    }

    @GetMapping("/categories/all")
    @Operation(summary = "Toàn bộ loại hàng", description = "Dùng cho ô chọn trên giao diện")
    public List<CategoryResponse> listCategories() {
        return categoryService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Thêm loại hàng")
    public CategoryResponse createCategory(@Valid @RequestBody SaveSimpleCatalogRequest request) {
        return categoryService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Sửa loại hàng")
    public CategoryResponse updateCategory(
            @PathVariable Long id, @Valid @RequestBody SaveSimpleCatalogRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Xóa loại hàng", description = "Xóa mềm; bị chặn nếu còn sản phẩm đang dùng")
    public void deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }

    // ============================== Đơn vị tính ==============================

    @GetMapping("/units")
    @Operation(summary = "Danh sách đơn vị tính (phân trang)")
    public PageResponse<UnitResponse> searchUnits(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return categoryService.searchUnits(keyword, pageable);
    }

    @GetMapping("/units/all")
    @Operation(summary = "Toàn bộ đơn vị tính")
    public List<UnitResponse> listUnits() {
        return categoryService.listUnits();
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Thêm đơn vị tính")
    public UnitResponse createUnit(@Valid @RequestBody SaveSimpleCatalogRequest request) {
        return categoryService.createUnit(request);
    }

    @PutMapping("/units/{id}")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Sửa đơn vị tính")
    public UnitResponse updateUnit(@PathVariable Long id, @Valid @RequestBody SaveSimpleCatalogRequest request) {
        return categoryService.updateUnit(id, request);
    }

    @DeleteMapping("/units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Xóa đơn vị tính")
    public void deleteUnit(@PathVariable Long id) {
        categoryService.deleteUnit(id);
    }

    // ============================== Nhà cung cấp ==============================

    @GetMapping("/suppliers")
    @Operation(summary = "Danh sách nhà cung cấp (phân trang)")
    public PageResponse<PartnerResponse> searchSuppliers(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return partnerService.searchSuppliers(keyword, pageable);
    }

    @GetMapping("/suppliers/all")
    @Operation(summary = "Toàn bộ nhà cung cấp")
    public List<PartnerResponse> listSuppliers() {
        return partnerService.listSuppliers();
    }

    @GetMapping("/suppliers/{id}")
    @Operation(summary = "Chi tiết nhà cung cấp")
    public PartnerResponse getSupplier(@PathVariable Long id) {
        return partnerService.getSupplier(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Thêm nhà cung cấp")
    public PartnerResponse createSupplier(@Valid @RequestBody SavePartnerRequest request) {
        return partnerService.createSupplier(request);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Sửa nhà cung cấp")
    public PartnerResponse updateSupplier(@PathVariable Long id, @Valid @RequestBody SavePartnerRequest request) {
        return partnerService.updateSupplier(id, request);
    }

    @DeleteMapping("/suppliers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Xóa nhà cung cấp", description = "Xóa mềm để các phiếu cũ vẫn hiển thị đúng")
    public void deleteSupplier(@PathVariable Long id) {
        partnerService.deleteSupplier(id);
    }

    // =============================== Khách hàng ===============================

    @GetMapping("/customers")
    @Operation(summary = "Danh sách khách hàng (phân trang)")
    public PageResponse<PartnerResponse> searchCustomers(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return partnerService.searchCustomers(keyword, pageable);
    }

    @GetMapping("/customers/all")
    @Operation(summary = "Toàn bộ khách hàng")
    public List<PartnerResponse> listCustomers() {
        return partnerService.listCustomers();
    }

    @GetMapping("/customers/{id}")
    @Operation(summary = "Chi tiết khách hàng")
    public PartnerResponse getCustomer(@PathVariable Long id) {
        return partnerService.getCustomer(id);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Thêm khách hàng")
    public PartnerResponse createCustomer(@Valid @RequestBody SavePartnerRequest request) {
        return partnerService.createCustomer(request);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Sửa khách hàng")
    public PartnerResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody SavePartnerRequest request) {
        return partnerService.updateCustomer(id, request);
    }

    @DeleteMapping("/customers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_EDIT)
    @Operation(summary = "Xóa khách hàng")
    public void deleteCustomer(@PathVariable Long id) {
        partnerService.deleteCustomer(id);
    }
}
