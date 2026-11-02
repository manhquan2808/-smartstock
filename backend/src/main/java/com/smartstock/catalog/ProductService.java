package com.smartstock.catalog;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.catalog.CatalogDtos.ProductDetailResponse;
import com.smartstock.catalog.CatalogDtos.ProductLookupResponse;
import com.smartstock.catalog.CatalogDtos.ProductResponse;
import com.smartstock.catalog.CatalogDtos.ProductStockRow;
import com.smartstock.catalog.CatalogDtos.SaveProductRequest;
import com.smartstock.common.BusinessException;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import com.smartstock.inventory.Inventory;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ quản lý sản phẩm.
 *
 * <p>Các quy tắc quan trọng:
 *
 * <ul>
 *   <li>SKU duy nhất; mã vạch nếu có cũng phải duy nhất (dùng để quét mã).</li>
 *   <li>CHẶN xóa sản phẩm còn tồn ở bất kỳ kho nào - nếu không, số lượng hàng thật sẽ
 *       biến mất khỏi mọi báo cáo.</li>
 *   <li>Xóa sản phẩm đã có giao dịch chỉ là xóa mềm, để thẻ kho và phiếu cũ vẫn đọc được.</li>
 *   <li>STAFF không được xem giá vốn và giá trị tồn: các trường đó trả về {@code null}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String ENTITY_NAME = "Product";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(
            String keyword, Long categoryId, RecordStatus status, Pageable pageable) {
        return PageResponse.from(
                productRepository.search(normalize(keyword), categoryId, status, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    /**
     * Chi tiết sản phẩm kèm tồn kho ở từng kho.
     *
     * <p>Người dùng chỉ thấy các kho mình được gán; giá vốn và giá trị tồn bị lược bỏ
     * nếu vai trò là STAFF.
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse getDetail(Long id) {
        Product product = findOrThrow(id);
        AppUserDetails current = SecurityUtils.getCurrentUser();
        boolean canViewCost = current.canViewCostPrice();

        List<Inventory> inventories = inventoryRepository.findByProductWithWarehouse(id).stream()
                .filter(inventory -> current.canAccessWarehouse(inventory.getWarehouse().getId()))
                .toList();

        List<ProductStockRow> stocks = inventories.stream()
                .map(inventory -> new ProductStockRow(
                        inventory.getWarehouse().getId(),
                        inventory.getWarehouse().getCode(),
                        inventory.getWarehouse().getName(),
                        inventory.getQuantity(),
                        canViewCost ? inventory.getAvgCost() : null,
                        canViewCost ? inventory.getStockValue() : null))
                .toList();

        BigDecimal totalQuantity = stocks.stream()
                .map(ProductStockRow::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProductDetailResponse(ProductResponse.from(product), stocks, totalQuantity);
    }

    /**
     * Tìm sản phẩm cho ô chọn khi lập phiếu, kèm tồn hiện có tại kho đang làm việc.
     *
     * @param warehouseId kho đang làm việc; dùng để hiển thị tồn cạnh từng dòng
     * @param limit số kết quả tối đa, giữ nhỏ để ô tìm kiếm phản hồi nhanh
     */
    @Transactional(readOnly = true)
    public List<ProductLookupResponse> lookup(String keyword, Long warehouseId, int limit) {
        AppUserDetails current = SecurityUtils.getCurrentUser();
        boolean canViewCost = current.canViewCostPrice();
        if (warehouseId != null && !current.canAccessWarehouse(warehouseId)) {
            throw new com.smartstock.common.ForbiddenException("Bạn không được phân công làm việc tại kho này.");
        }

        Pageable pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 50));
        return productRepository.findSelectable(normalize(keyword), pageable).stream()
                .map(product -> toLookup(product, warehouseId, canViewCost))
                .toList();
    }

    /**
     * Tìm sản phẩm theo mã vạch - phục vụ quét mã bằng camera hoặc máy quét USB.
     *
     * <p>Nếu không khớp mã vạch, thử khớp SKU: nhiều doanh nghiệp in tem bằng chính SKU.
     */
    @Transactional(readOnly = true)
    public ProductLookupResponse findByBarcode(String code, Long warehouseId) {
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (warehouseId != null && !current.canAccessWarehouse(warehouseId)) {
            throw new com.smartstock.common.ForbiddenException("Bạn không được phân công làm việc tại kho này.");
        }

        String normalized = code == null ? "" : code.trim();
        Product product = productRepository
                .findByBarcodeAndDeletedAtIsNull(normalized)
                .or(() -> productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull(normalized))
                .orElseThrow(() -> new NotFoundException(
                        "Không tìm thấy sản phẩm với mã \"" + normalized + "\". Vui lòng kiểm tra lại mã vạch."));

        if (!product.isSelectable()) {
            throw new BusinessException(
                    "Sản phẩm \"" + product.getName() + "\" đã ngừng kinh doanh, không thể chọn vào phiếu mới.",
                    "PRODUCT_DISCONTINUED");
        }

        return toLookup(product, warehouseId, current.canViewCostPrice());
    }

    @Transactional
    public ProductResponse create(SaveProductRequest request) {
        if (productRepository.existsBySkuIgnoreCaseAndDeletedAtIsNull(request.sku())) {
            throw new BusinessException("Mã sản phẩm đã tồn tại.", "PRODUCT_SKU_EXISTS");
        }
        String barcode = emptyToNull(request.barcode());
        if (barcode != null && productRepository.existsByBarcodeAndDeletedAtIsNull(barcode)) {
            throw new BusinessException("Mã vạch đã được dùng cho sản phẩm khác.", "PRODUCT_BARCODE_EXISTS");
        }

        Product product = Product.builder()
                .sku(request.sku().trim().toUpperCase())
                .barcode(barcode)
                .name(request.name().trim())
                .category(findCategory(request.categoryId()))
                .unit(findUnit(request.unitId()))
                .purchasePrice(request.purchasePrice())
                .salePrice(request.salePrice())
                .minStock(request.minStock())
                .imageUrl(emptyToNull(request.imageUrl()))
                .description(emptyToNull(request.description()))
                .status(request.status() == null ? RecordStatus.ACTIVE : request.status())
                .build();

        Product saved = productRepository.save(product);
        auditService.logChange(
                AuditAction.CREATE,
                ENTITY_NAME,
                saved.getId(),
                "Thêm sản phẩm " + saved.getSku(),
                null,
                ProductResponse.from(saved));
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(Long id, SaveProductRequest request) {
        Product product = findOrThrow(id);
        ProductResponse before = ProductResponse.from(product);

        if (productRepository.existsBySkuIgnoreCaseAndIdNotAndDeletedAtIsNull(request.sku(), id)) {
            throw new BusinessException("Mã sản phẩm đã tồn tại.", "PRODUCT_SKU_EXISTS");
        }
        String barcode = emptyToNull(request.barcode());
        if (barcode != null && productRepository.existsByBarcodeAndIdNotAndDeletedAtIsNull(barcode, id)) {
            throw new BusinessException("Mã vạch đã được dùng cho sản phẩm khác.", "PRODUCT_BARCODE_EXISTS");
        }

        product.setSku(request.sku().trim().toUpperCase());
        product.setBarcode(barcode);
        product.setName(request.name().trim());
        product.setCategory(findCategory(request.categoryId()));
        product.setUnit(findUnit(request.unitId()));
        product.setPurchasePrice(request.purchasePrice());
        product.setSalePrice(request.salePrice());
        product.setMinStock(request.minStock());
        product.setDescription(emptyToNull(request.description()));
        if (request.imageUrl() != null) {
            product.setImageUrl(emptyToNull(request.imageUrl()));
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }

        Product saved = productRepository.save(product);
        auditService.logChange(
                AuditAction.UPDATE,
                ENTITY_NAME,
                id,
                "Cập nhật sản phẩm " + saved.getSku(),
                before,
                ProductResponse.from(saved));
        return ProductResponse.from(saved);
    }

    /**
     * Xóa mềm sản phẩm.
     *
     * <p>Chặn hoàn toàn nếu sản phẩm còn tồn ở bất kỳ kho nào: xóa khi còn hàng sẽ làm
     * lệch báo cáo và mất dấu số hàng thật đang nằm trong kho.
     */
    @Transactional
    public void delete(Long id) {
        Product product = findOrThrow(id);

        if (inventoryRepository.existsByProductIdAndQuantityGreaterThan(id, BigDecimal.ZERO)) {
            throw new BusinessException(
                    "Không thể xóa sản phẩm đang còn tồn kho. Hãy xuất hết hàng hoặc chuyển sản phẩm sang "
                            + "trạng thái Ngừng kinh doanh.",
                    "PRODUCT_HAS_STOCK");
        }

        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
        auditService.log(AuditAction.DELETE, ENTITY_NAME, id, "Xóa sản phẩm " + product.getSku());
    }

    /** Đổi trạng thái kinh doanh (Đang kinh doanh / Ngừng kinh doanh). */
    @Transactional
    public ProductResponse changeStatus(Long id, RecordStatus status) {
        Product product = findOrThrow(id);
        RecordStatus before = product.getStatus();
        product.setStatus(status);
        Product saved = productRepository.save(product);
        auditService.logChange(
                AuditAction.UPDATE,
                ENTITY_NAME,
                id,
                "Đổi trạng thái sản phẩm " + saved.getSku() + " sang " + status.getDisplayName(),
                before,
                status);
        return ProductResponse.from(saved);
    }

    /** Lấy entity sản phẩm cho các service khác dùng (phiếu nhập/xuất/kiểm kê). */
    @Transactional(readOnly = true)
    public Product requireSelectableProduct(Long id) {
        Product product = findOrThrow(id);
        if (!product.isSelectable()) {
            throw new BusinessException(
                    "Sản phẩm \"" + product.getName() + "\" đã ngừng kinh doanh, không thể chọn vào phiếu mới.",
                    "PRODUCT_DISCONTINUED");
        }
        return product;
    }

    private ProductLookupResponse toLookup(Product product, Long warehouseId, boolean canViewCost) {
        BigDecimal quantityOnHand = BigDecimal.ZERO;
        BigDecimal avgCost = null;
        if (warehouseId != null) {
            Inventory inventory = inventoryRepository
                    .findByProductIdAndWarehouseId(product.getId(), warehouseId)
                    .orElse(null);
            if (inventory != null) {
                quantityOnHand = inventory.getQuantity();
                avgCost = canViewCost ? inventory.getAvgCost() : null;
            }
        }
        return new ProductLookupResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getUnit().getName(),
                product.getPurchasePrice(),
                product.getSalePrice(),
                quantityOnHand,
                avgCost);
    }

    private Product findOrThrow(Long id) {
        return productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("sản phẩm", id));
    }

    private Category findCategory(Long id) {
        return categoryRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("loại hàng", id));
    }

    private Unit findUnit(Long id) {
        return unitRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("đơn vị tính", id));
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
