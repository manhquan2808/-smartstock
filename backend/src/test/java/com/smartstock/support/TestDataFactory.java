package com.smartstock.support;

import com.smartstock.catalog.Category;
import com.smartstock.catalog.CategoryRepository;
import com.smartstock.catalog.Customer;
import com.smartstock.catalog.CustomerRepository;
import com.smartstock.catalog.Product;
import com.smartstock.catalog.ProductRepository;
import com.smartstock.catalog.Supplier;
import com.smartstock.catalog.SupplierRepository;
import com.smartstock.catalog.Unit;
import com.smartstock.catalog.UnitRepository;
import com.smartstock.user.Role;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.user.UserStatus;
import com.smartstock.warehouse.Warehouse;
import com.smartstock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo nhanh dữ liệu mẫu cho các test tích hợp.
 *
 * <p>Mỗi lần gọi sinh mã duy nhất (SP-1, SP-2...) nên nhiều test chạy trên cùng một
 * cơ sở dữ liệu vẫn không trùng khóa unique.
 */
@TestComponent
@RequiredArgsConstructor
public class TestDataFactory {

    /** Bộ đếm để sinh mã duy nhất trong cùng một lần chạy test. */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final WarehouseRepository warehouseRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private int next() {
        return COUNTER.incrementAndGet();
    }

    @Transactional
    public Warehouse createWarehouse(String name) {
        int id = next();
        return warehouseRepository.save(Warehouse.builder()
                .code("KHO" + id)
                .name(name)
                .address("Địa chỉ thử nghiệm " + id)
                .active(true)
                .build());
    }

    @Transactional
    public Category createCategory() {
        int id = next();
        return categoryRepository.save(
                Category.builder().code("LH" + id).name("Loại hàng " + id).build());
    }

    @Transactional
    public Unit createUnit() {
        int id = next();
        return unitRepository.save(Unit.builder().code("DVT" + id).name("Thùng").build());
    }

    /** Tạo sản phẩm với mức tồn tối thiểu bằng 0 (không sinh cảnh báo tồn thấp). */
    @Transactional
    public Product createProduct(String name) {
        return createProduct(name, BigDecimal.ZERO);
    }

    @Transactional
    public Product createProduct(String name, BigDecimal minStock) {
        int id = next();
        return productRepository.save(Product.builder()
                .sku("SP" + id)
                .barcode("89360000" + String.format("%05d", id))
                .name(name)
                .category(createCategory())
                .unit(createUnit())
                .purchasePrice(new BigDecimal("100000"))
                .salePrice(new BigDecimal("150000"))
                .minStock(minStock)
                .status(com.smartstock.catalog.RecordStatus.ACTIVE)
                .build());
    }

    @Transactional
    public Supplier createSupplier() {
        int id = next();
        return supplierRepository.save(Supplier.builder()
                .code("NCC" + id)
                .name("Nhà cung cấp " + id)
                .phone("0900000000")
                .build());
    }

    @Transactional
    public Customer createCustomer() {
        int id = next();
        return customerRepository.save(Customer.builder()
                .code("KH" + id)
                .name("Khách hàng " + id)
                .phone("0900000000")
                .build());
    }

    /**
     * Tạo tài khoản với vai trò và danh sách kho chỉ định.
     *
     * @param warehouses để trống với ADMIN (ADMIN thấy mọi kho)
     */
    @Transactional
    public User createUser(Role role, String rawPassword, Warehouse... warehouses) {
        int id = next();
        return userRepository.save(User.builder()
                .username(role.name().toLowerCase() + id)
                .email(role.name().toLowerCase() + id + "@test.local")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName("Người dùng " + id)
                .role(role)
                .status(UserStatus.ACTIVE)
                .warehouses(Set.of(warehouses))
                .build());
    }
}
