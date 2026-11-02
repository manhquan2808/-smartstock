package com.smartstock.catalog;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.catalog.CatalogDtos.CategoryResponse;
import com.smartstock.catalog.CatalogDtos.SaveSimpleCatalogRequest;
import com.smartstock.catalog.CatalogDtos.UnitResponse;
import com.smartstock.common.BusinessException;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ loại hàng và đơn vị tính.
 *
 * <p>Hai danh mục này rất giống nhau (mã, tên, mô tả, xóa mềm) nên gộp vào một service
 * để tránh lặp mã. Quy tắc xóa: nếu đã có sản phẩm tham chiếu thì CHẶN xóa, vì xóa đi
 * sẽ làm sản phẩm mất loại hàng / đơn vị tính.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final AuditService auditService;

    // ================================ Loại hàng ================================

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> searchCategories(String keyword, Pageable pageable) {
        return PageResponse.from(categoryRepository.search(normalize(keyword), pageable), CategoryResponse::from);
    }

    /** Danh sách đầy đủ, dùng cho các ô chọn (dropdown) trên giao diện. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(SaveSimpleCatalogRequest request) {
        if (categoryRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(request.code())) {
            throw new BusinessException("Mã loại hàng đã tồn tại.", "CATEGORY_CODE_EXISTS");
        }
        Category category = Category.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .description(emptyToNull(request.description()))
                .build();
        Category saved = categoryRepository.save(category);
        auditService.logChange(
                AuditAction.CREATE,
                "Category",
                saved.getId(),
                "Thêm loại hàng " + saved.getCode(),
                null,
                CategoryResponse.from(saved));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, SaveSimpleCatalogRequest request) {
        Category category = categoryRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("loại hàng", id));
        CategoryResponse before = CategoryResponse.from(category);

        if (categoryRepository.existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(request.code(), id)) {
            throw new BusinessException("Mã loại hàng đã tồn tại.", "CATEGORY_CODE_EXISTS");
        }
        category.setCode(request.code().trim().toUpperCase());
        category.setName(request.name().trim());
        category.setDescription(emptyToNull(request.description()));

        Category saved = categoryRepository.save(category);
        auditService.logChange(
                AuditAction.UPDATE,
                "Category",
                id,
                "Cập nhật loại hàng " + saved.getCode(),
                before,
                CategoryResponse.from(saved));
        return CategoryResponse.from(saved);
    }

    /** Xóa mềm loại hàng. Chặn nếu còn sản phẩm đang dùng. */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("loại hàng", id));

        if (productRepository.existsByCategoryIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(
                    "Không thể xóa loại hàng đang có sản phẩm. Vui lòng chuyển các sản phẩm sang loại khác trước.",
                    "CATEGORY_IN_USE");
        }

        category.setDeletedAt(LocalDateTime.now());
        categoryRepository.save(category);
        auditService.log(AuditAction.DELETE, "Category", id, "Xóa loại hàng " + category.getCode());
    }

    // ============================== Đơn vị tính ==============================

    @Transactional(readOnly = true)
    public PageResponse<UnitResponse> searchUnits(String keyword, Pageable pageable) {
        return PageResponse.from(unitRepository.search(normalize(keyword), pageable), UnitResponse::from);
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits() {
        return unitRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .map(UnitResponse::from)
                .toList();
    }

    @Transactional
    public UnitResponse createUnit(SaveSimpleCatalogRequest request) {
        if (unitRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(request.code())) {
            throw new BusinessException("Mã đơn vị tính đã tồn tại.", "UNIT_CODE_EXISTS");
        }
        Unit unit = Unit.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .description(emptyToNull(request.description()))
                .build();
        Unit saved = unitRepository.save(unit);
        auditService.logChange(
                AuditAction.CREATE,
                "Unit",
                saved.getId(),
                "Thêm đơn vị tính " + saved.getCode(),
                null,
                UnitResponse.from(saved));
        return UnitResponse.from(saved);
    }

    @Transactional
    public UnitResponse updateUnit(Long id, SaveSimpleCatalogRequest request) {
        Unit unit = unitRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("đơn vị tính", id));
        UnitResponse before = UnitResponse.from(unit);

        if (unitRepository.existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(request.code(), id)) {
            throw new BusinessException("Mã đơn vị tính đã tồn tại.", "UNIT_CODE_EXISTS");
        }
        unit.setCode(request.code().trim().toUpperCase());
        unit.setName(request.name().trim());
        unit.setDescription(emptyToNull(request.description()));

        Unit saved = unitRepository.save(unit);
        auditService.logChange(
                AuditAction.UPDATE, "Unit", id, "Cập nhật đơn vị tính " + saved.getCode(), before, UnitResponse.from(saved));
        return UnitResponse.from(saved);
    }

    @Transactional
    public void deleteUnit(Long id) {
        Unit unit = unitRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("đơn vị tính", id));

        if (productRepository.existsByUnitIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(
                    "Không thể xóa đơn vị tính đang được sản phẩm sử dụng.", "UNIT_IN_USE");
        }

        unit.setDeletedAt(LocalDateTime.now());
        unitRepository.save(unit);
        auditService.log(AuditAction.DELETE, "Unit", id, "Xóa đơn vị tính " + unit.getCode());
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
