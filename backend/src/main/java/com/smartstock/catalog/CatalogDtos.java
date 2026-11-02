package com.smartstock.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO cho toàn bộ danh mục nền: loại hàng, đơn vị tính, sản phẩm, NCC, khách hàng.
 *
 * <p>Gom chung vào một file vì các DTO này rất ngắn và luôn được dùng cùng nhau trên
 * các màn hình danh mục. Mỗi nhóm được phân tách bằng tiêu đề để dễ tra cứu.
 */
public final class CatalogDtos {

    private CatalogDtos() {}

    // ==================================================================
    // Loại hàng và đơn vị tính (cùng khuôn dạng: mã, tên, mô tả)
    // ==================================================================

    /** Dữ liệu tạo/sửa loại hàng hoặc đơn vị tính. */
    public record SaveSimpleCatalogRequest(
            @NotBlank(message = "Vui lòng nhập mã")
                    @Size(max = 20, message = "Mã tối đa 20 ký tự")
                    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã chỉ gồm chữ không dấu, số và các ký tự _ -")
                    String code,
            @NotBlank(message = "Vui lòng nhập tên") @Size(max = 150, message = "Tên tối đa 150 ký tự") String name,
            @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String description) {}

    /** Loại hàng trả về giao diện. */
    public record CategoryResponse(
            Long id, String code, String name, String description, LocalDateTime createdAt) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getCode(),
                    category.getName(),
                    category.getDescription(),
                    category.getCreatedAt());
        }
    }

    /** Đơn vị tính trả về giao diện. */
    public record UnitResponse(Long id, String code, String name, String description, LocalDateTime createdAt) {

        public static UnitResponse from(Unit unit) {
            return new UnitResponse(
                    unit.getId(), unit.getCode(), unit.getName(), unit.getDescription(), unit.getCreatedAt());
        }
    }

    // ==================================================================
    // Sản phẩm
    // ==================================================================

    /**
     * Dữ liệu tạo/sửa sản phẩm.
     *
     * @param barcode mã vạch EAN-13; để rỗng nếu sản phẩm chưa có mã vạch
     * @param minStock mức tồn tối thiểu; 0 nghĩa là không cảnh báo tồn thấp
     */
    public record SaveProductRequest(
            @NotBlank(message = "Vui lòng nhập mã sản phẩm")
                    @Size(max = 50, message = "Mã sản phẩm tối đa 50 ký tự")
                    @Pattern(
                            regexp = "^[A-Za-z0-9_.-]+$",
                            message = "Mã sản phẩm chỉ gồm chữ không dấu, số và các ký tự . _ -")
                    String sku,
            @Size(max = 50, message = "Mã vạch tối đa 50 ký tự")
                    @Pattern(regexp = "^$|^[0-9]{8,14}$", message = "Mã vạch phải là dãy 8 đến 14 chữ số")
                    String barcode,
            @NotBlank(message = "Vui lòng nhập tên sản phẩm") @Size(max = 255, message = "Tên tối đa 255 ký tự")
                    String name,
            @NotNull(message = "Vui lòng chọn loại hàng") Long categoryId,
            @NotNull(message = "Vui lòng chọn đơn vị tính") Long unitId,
            @NotNull(message = "Vui lòng nhập giá nhập")
                    @DecimalMin(value = "0", message = "Giá nhập không được âm")
                    @Digits(integer = 16, fraction = 2, message = "Giá nhập tối đa 2 chữ số thập phân")
                    BigDecimal purchasePrice,
            @NotNull(message = "Vui lòng nhập giá bán")
                    @DecimalMin(value = "0", message = "Giá bán không được âm")
                    @Digits(integer = 16, fraction = 2, message = "Giá bán tối đa 2 chữ số thập phân")
                    BigDecimal salePrice,
            @NotNull(message = "Vui lòng nhập mức tồn tối thiểu")
                    @DecimalMin(value = "0", message = "Mức tồn tối thiểu không được âm")
                    @Digits(integer = 15, fraction = 3, message = "Mức tồn tối thiểu tối đa 3 chữ số thập phân")
                    BigDecimal minStock,
            @Size(max = 500) String imageUrl,
            @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự") String description,
            RecordStatus status) {}

    /**
     * Sản phẩm trả về giao diện.
     *
     * <p>Lưu ý phân quyền: {@code purchasePrice} là giá nhập tham chiếu nên vẫn trả cho
     * STAFF, nhưng GIÁ VỐN và GIÁ TRỊ TỒN (trong {@code InventoryDtos}) thì bị lược bỏ
     * với STAFF.
     */
    public record ProductResponse(
            Long id,
            String sku,
            String barcode,
            String name,
            Long categoryId,
            String categoryName,
            Long unitId,
            String unitName,
            BigDecimal purchasePrice,
            BigDecimal salePrice,
            BigDecimal minStock,
            String imageUrl,
            String description,
            RecordStatus status,
            String statusName,
            LocalDateTime createdAt) {

        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getSku(),
                    product.getBarcode(),
                    product.getName(),
                    product.getCategory().getId(),
                    product.getCategory().getName(),
                    product.getUnit().getId(),
                    product.getUnit().getName(),
                    product.getPurchasePrice(),
                    product.getSalePrice(),
                    product.getMinStock(),
                    product.getImageUrl(),
                    product.getDescription(),
                    product.getStatus(),
                    product.getStatus().getDisplayName(),
                    product.getCreatedAt());
        }
    }

    /**
     * Sản phẩm dạng gọn cho ô tìm kiếm khi lập phiếu và cho chức năng quét mã.
     *
     * @param quantityOnHand tồn hiện có tại kho đang làm việc, để hiển thị cạnh từng dòng phiếu
     * @param avgCost giá vốn; {@code null} với vai trò STAFF
     */
    public record ProductLookupResponse(
            Long id,
            String sku,
            String barcode,
            String name,
            String unitName,
            BigDecimal purchasePrice,
            BigDecimal salePrice,
            BigDecimal quantityOnHand,
            BigDecimal avgCost) {}

    /** Sản phẩm kèm tồn kho ở từng kho (trang chi tiết sản phẩm). */
    public record ProductDetailResponse(
            ProductResponse product, List<ProductStockRow> stocks, BigDecimal totalQuantity) {}

    /**
     * Một dòng tồn kho của sản phẩm.
     *
     * @param avgCost {@code null} với vai trò STAFF
     * @param stockValue {@code null} với vai trò STAFF
     */
    public record ProductStockRow(
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            BigDecimal quantity,
            BigDecimal avgCost,
            BigDecimal stockValue) {}

    // ==================================================================
    // Nhà cung cấp và khách hàng (cùng khuôn dạng)
    // ==================================================================

    /** Dữ liệu tạo/sửa nhà cung cấp hoặc khách hàng. */
    public record SavePartnerRequest(
            @NotBlank(message = "Vui lòng nhập mã")
                    @Size(max = 20, message = "Mã tối đa 20 ký tự")
                    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã chỉ gồm chữ không dấu, số và các ký tự _ -")
                    String code,
            @NotBlank(message = "Vui lòng nhập tên") @Size(max = 200, message = "Tên tối đa 200 ký tự") String name,
            @Pattern(regexp = "^$|^[0-9+\\s.()-]{8,20}$", message = "Số điện thoại không đúng định dạng") String phone,
            @Email(message = "Email không đúng định dạng") @Size(max = 150) String email,
            @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự") String address,
            @Size(max = 30, message = "Mã số thuế tối đa 30 ký tự") String taxCode,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {}

    /** Nhà cung cấp / khách hàng trả về giao diện (dùng chung một khuôn dạng). */
    public record PartnerResponse(
            Long id,
            String code,
            String name,
            String phone,
            String email,
            String address,
            String taxCode,
            String note,
            LocalDateTime createdAt) {

        public static PartnerResponse from(Supplier supplier) {
            return new PartnerResponse(
                    supplier.getId(),
                    supplier.getCode(),
                    supplier.getName(),
                    supplier.getPhone(),
                    supplier.getEmail(),
                    supplier.getAddress(),
                    supplier.getTaxCode(),
                    supplier.getNote(),
                    supplier.getCreatedAt());
        }

        public static PartnerResponse from(Customer customer) {
            return new PartnerResponse(
                    customer.getId(),
                    customer.getCode(),
                    customer.getName(),
                    customer.getPhone(),
                    customer.getEmail(),
                    customer.getAddress(),
                    customer.getTaxCode(),
                    customer.getNote(),
                    customer.getCreatedAt());
        }
    }
}
