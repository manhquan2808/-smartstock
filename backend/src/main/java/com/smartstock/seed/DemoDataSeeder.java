package com.smartstock.seed;

import com.smartstock.catalog.Category;
import com.smartstock.catalog.CategoryRepository;
import com.smartstock.catalog.Customer;
import com.smartstock.catalog.CustomerRepository;
import com.smartstock.catalog.Product;
import com.smartstock.catalog.ProductRepository;
import com.smartstock.catalog.RecordStatus;
import com.smartstock.catalog.Supplier;
import com.smartstock.catalog.SupplierRepository;
import com.smartstock.catalog.Unit;
import com.smartstock.catalog.UnitRepository;
import com.smartstock.document.DocumentSequenceService;
import com.smartstock.document.DocumentStatus;
import com.smartstock.inventory.InventoryService;
import com.smartstock.inventory.InventoryService.StockMovement;
import com.smartstock.inventory.RefType;
import com.smartstock.issue.IssueType;
import com.smartstock.issue.StockIssue;
import com.smartstock.issue.StockIssueItem;
import com.smartstock.issue.StockIssueRepository;
import com.smartstock.receipt.ReceiptType;
import com.smartstock.receipt.StockReceipt;
import com.smartstock.receipt.StockReceiptItem;
import com.smartstock.receipt.StockReceiptRepository;
import com.smartstock.security.AppUserDetails;
import com.smartstock.stocktake.Stocktake;
import com.smartstock.stocktake.StocktakeItem;
import com.smartstock.stocktake.StocktakeRepository;
import com.smartstock.user.Role;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.user.UserStatus;
import com.smartstock.warehouse.Warehouse;
import com.smartstock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sinh dữ liệu DEMO cho buổi trình bày và kiểm thử.
 *
 * <p>Chỉ hoạt động ở profile {@code demo} ({@code --spring.profiles.active=demo}) nên
 * không bao giờ chạy nhầm trên môi trường thật.
 *
 * <p><b>Nguyên tắc quan trọng nhất:</b> mọi thay đổi tồn kho đều đi qua
 * {@link InventoryService}, KHÔNG chèn thẳng số tồn vào bảng {@code inventories}. Nhờ vậy:
 *
 * <ul>
 *   <li>Tồn kho, giá vốn bình quân và thẻ kho của dữ liệu demo luôn khớp nhau.</li>
 *   <li>Báo cáo nhập - xuất - tồn trên dữ liệu demo cho ra số liệu đúng.</li>
 *   <li>Bất biến "tồn = tổng giao dịch" vẫn đúng trên toàn bộ dữ liệu demo.</li>
 * </ul>
 *
 * <p>Dữ liệu sinh ra: 3 kho, 10 loại hàng, 8 đơn vị tính, 150 sản phẩm, 10 NCC,
 * 20 khách hàng, 3 tài khoản demo, hơn 300 phiếu nhập/xuất rải đều 6 tháng gần nhất với
 * đủ các trạng thái, và 2 đợt kiểm kê.
 *
 * <p>Dùng {@link Random} với HẠT GIỐNG CỐ ĐỊNH để mỗi lần chạy lại cho cùng một bộ dữ
 * liệu - điều này giúp các ảnh chụp màn hình trong tài liệu luôn khớp với hệ thống.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder {

    /** Hạt giống cố định để dữ liệu demo tái lập được giữa các lần chạy. */
    private static final long RANDOM_SEED = 20261031L;

    /** Số tháng dữ liệu lịch sử cần sinh. */
    private static final int HISTORY_MONTHS = 6;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;

    private final WarehouseRepository warehouseRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockIssueRepository issueRepository;
    private final StocktakeRepository stocktakeRepository;
    private final InventoryService inventoryService;
    private final DocumentSequenceService sequenceService;

    private final Random random = new Random(RANDOM_SEED);

    /**
     * Chạy sau khi ứng dụng khởi động xong.
     *
     * <p>Dùng {@code ApplicationReadyEvent} thay vì {@code @PostConstruct} để bảo đảm
     * Flyway đã chạy xong migration và toàn bộ bean đã sẵn sàng.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            // Nếu đã có dữ liệu demo (đủ 150 sản phẩm) thì bỏ qua, tránh nhân đôi dữ liệu
            long productCount = productRepository.countByDeletedAtIsNull();
            if (productCount >= 150) {
                log.info("Dữ liệu demo đã tồn tại ({} sản phẩm), bỏ qua bước sinh dữ liệu", productCount);
                return;
            }

            log.info("=== Bắt đầu sinh dữ liệu demo ===");
            long startTime = System.currentTimeMillis();

            clearBusinessData();

            List<Warehouse> warehouses = seedWarehouses();
            List<Category> categories = seedCategories();
            List<Unit> units = seedUnits();
            List<Product> products = seedProducts(categories, units);
            List<Supplier> suppliers = seedSuppliers();
            List<Customer> customers = seedCustomers();
            List<User> users = seedUsers(warehouses);

            // Đăng nhập dưới danh nghĩa quản lý kho để các bản ghi có người lập/người duyệt hợp lý
            User seedActor = users.stream()
                    .filter(user -> user.getRole() == Role.MANAGER)
                    .findFirst()
                    .orElse(users.get(0));
            loginAs(seedActor);

            seedDocuments(warehouses, products, suppliers, customers, users);
            seedStocktakes(warehouses, products);
            markSomeProductsDiscontinued(products);

            SecurityContextHolder.clearContext();

            long seconds = (System.currentTimeMillis() - startTime) / 1000;
            log.info(
                    "=== Hoàn tất sinh dữ liệu demo trong {} giây: {} sản phẩm, {} phiếu nhập, {} phiếu xuất, {} kiểm kê ===",
                    seconds,
                    productRepository.countByDeletedAtIsNull(),
                    receiptRepository.count(),
                    issueRepository.count(),
                    stocktakeRepository.count());
            log.info("Tài khoản demo: admin/Admin@123 | quanly/Quanly@123 | nhanvien/Nhanvien@123");
        } catch (Exception ex) {
            // Lỗi seed không được làm sập ứng dụng: người dùng vẫn đăng nhập được bằng tài khoản admin
            log.error("Sinh dữ liệu demo thất bại: {}", ex.getMessage(), ex);
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Xóa toàn bộ dữ liệu nghiệp vụ để chạy lại được nhiều lần.
     *
     * <p>Xóa theo thứ tự ngược với quan hệ khóa ngoại. Dùng SQL thẳng vì nhanh hơn nhiều
     * so với tải entity lên rồi xóa từng bản ghi.
     *
     * <p>Giữ lại tài khoản {@code admin} do migration V7 tạo ra.
     */
    @Transactional
    protected void clearBusinessData() {
        log.info("Xóa dữ liệu nghiệp vụ cũ...");
        List<String> tablesInDeleteOrder = List.of(
                "stock_transactions",
                "stocktake_items",
                "stocktakes",
                "stock_issue_items",
                "stock_issues",
                "stock_receipt_items",
                "stock_receipts",
                "inventories",
                "notifications",
                "audit_logs",
                "document_sequences",
                "refresh_tokens",
                "password_reset_tokens",
                "user_warehouses",
                "products",
                "categories",
                "units",
                "suppliers",
                "customers");

        for (String table : tablesInDeleteOrder) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        // Xóa mọi tài khoản trừ admin gốc
        jdbcTemplate.update("DELETE FROM users WHERE username <> 'admin'");
        // Bỏ liên kết người quản lý trước khi xóa kho
        jdbcTemplate.update("UPDATE warehouses SET manager_id = NULL");
        jdbcTemplate.update("DELETE FROM warehouses");
    }

    private List<Warehouse> seedWarehouses() {
        List<Warehouse> warehouses = new ArrayList<>();
        for (String[] data : DemoDataProvider.WAREHOUSES) {
            warehouses.add(warehouseRepository.save(Warehouse.builder()
                    .code(data[0])
                    .name(data[1])
                    .address(data[2])
                    .phone(data[3])
                    .active(true)
                    .build()));
        }
        log.info("Đã tạo {} kho", warehouses.size());
        return warehouses;
    }

    private List<Category> seedCategories() {
        List<Category> categories = new ArrayList<>();
        for (String[] data : DemoDataProvider.CATEGORIES) {
            categories.add(categoryRepository.save(
                    Category.builder().code(data[0]).name(data[1]).build()));
        }
        log.info("Đã tạo {} loại hàng", categories.size());
        return categories;
    }

    private List<Unit> seedUnits() {
        List<Unit> units = new ArrayList<>();
        for (String[] data : DemoDataProvider.UNITS) {
            units.add(unitRepository.save(Unit.builder().code(data[0]).name(data[1]).build()));
        }
        log.info("Đã tạo {} đơn vị tính", units.size());
        return units;
    }

    private List<Product> seedProducts(List<Category> categories, List<Unit> units) {
        List<Product> products = new ArrayList<>();
        int index = 1;

        for (Object[] data : DemoDataProvider.PRODUCTS) {
            int categoryIndex = (int) data[0];
            int unitIndex = (int) data[1];
            String name = (String) data[2];

            products.add(productRepository.save(Product.builder()
                    .sku(String.format("SP%04d", index))
                    .barcode(generateEan13(index))
                    .name(name)
                    .category(categories.get(categoryIndex % categories.size()))
                    .unit(units.get(unitIndex % units.size()))
                    .purchasePrice(BigDecimal.valueOf((int) data[3]))
                    .salePrice(BigDecimal.valueOf((int) data[4]))
                    .minStock(BigDecimal.valueOf((int) data[5]))
                    .status(RecordStatus.ACTIVE)
                    .description("Hàng tiêu dùng nhanh - " + name)
                    .build()));
            index++;
        }
        log.info("Đã tạo {} sản phẩm", products.size());
        return products;
    }

    /**
     * Sinh mã vạch EAN-13 HỢP LỆ.
     *
     * <p>EAN-13 gồm 12 chữ số dữ liệu cộng 1 chữ số kiểm tra. Chữ số kiểm tra được tính
     * bằng cách nhân luân phiên 1 và 3 các chữ số, cộng lại, rồi lấy phần bù của 10.
     * Nhờ tính đúng, mã vạch in ra quét được bằng mọi máy quét thật - đây là chi tiết
     * giúp phần demo quét mã thuyết phục.
     *
     * <p>Tiền tố 893 là mã quốc gia Việt Nam do GS1 cấp.
     */
    private String generateEan13(int sequence) {
        String base = String.format("893%09d", sequence);

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = base.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return base + checkDigit;
    }

    private List<Supplier> seedSuppliers() {
        List<Supplier> suppliers = new ArrayList<>();
        for (String[] data : DemoDataProvider.SUPPLIERS) {
            suppliers.add(supplierRepository.save(Supplier.builder()
                    .code(data[0])
                    .name(data[1])
                    .phone(data[2])
                    .address(data[3])
                    .taxCode(data[4])
                    .email(data[0].toLowerCase() + "@nhacungcap.vn")
                    .build()));
        }
        log.info("Đã tạo {} nhà cung cấp", suppliers.size());
        return suppliers;
    }

    private List<Customer> seedCustomers() {
        List<Customer> customers = new ArrayList<>();
        for (String[] data : DemoDataProvider.CUSTOMERS) {
            customers.add(customerRepository.save(Customer.builder()
                    .code(data[0])
                    .name(data[1])
                    .phone(data[2])
                    .address(data[3])
                    .build()));
        }
        log.info("Đã tạo {} khách hàng", customers.size());
        return customers;
    }

    /**
     * Tạo ba tài khoản demo theo đúng bảng trong đặc tả.
     *
     * <ul>
     *   <li>{@code admin} / Admin@123 - ADMIN, thấy mọi kho.</li>
     *   <li>{@code quanly} / Quanly@123 - MANAGER, kho Hà Nội và Đà Nẵng.</li>
     *   <li>{@code nhanvien} / Nhanvien@123 - STAFF, kho Hà Nội.</li>
     * </ul>
     */
    private List<User> seedUsers(List<Warehouse> warehouses) {
        List<User> users = new ArrayList<>();

        // Tài khoản admin đã có từ migration V7; cập nhật lại mật khẩu cho chắc chắn
        User admin = userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(User.builder()
                .username("admin")
                .email("admin@smartstock.local")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .fullName("Quản trị hệ thống")
                .phone("0900000000")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build()));
        admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
        admin.setMustChangePassword(false);
        users.add(userRepository.save(admin));

        User manager = userRepository.save(User.builder()
                .username("quanly")
                .email("quanly@smartstock.local")
                .passwordHash(passwordEncoder.encode("Quanly@123"))
                .fullName("Trần Thị Quản Lý")
                .phone("0911222333")
                .role(Role.MANAGER)
                .status(UserStatus.ACTIVE)
                // Kho Hà Nội và Đà Nẵng
                .warehouses(new HashSet<>(List.of(warehouses.get(0), warehouses.get(1))))
                .build());
        users.add(manager);

        User staff = userRepository.save(User.builder()
                .username("nhanvien")
                .email("nhanvien@smartstock.local")
                .passwordHash(passwordEncoder.encode("Nhanvien@123"))
                .fullName("Nguyễn Văn Nhân Viên")
                .phone("0944555666")
                .role(Role.STAFF)
                .status(UserStatus.ACTIVE)
                // Chỉ kho Hà Nội
                .warehouses(new HashSet<>(List.of(warehouses.get(0))))
                .build());
        users.add(staff);

        // Thêm vài tài khoản để danh sách người dùng trông thực tế hơn
        users.add(userRepository.save(User.builder()
                .username("quanly.hcm")
                .email("quanly.hcm@smartstock.local")
                .passwordHash(passwordEncoder.encode("Quanly@123"))
                .fullName("Lê Văn Hồ Chí Minh")
                .phone("0977888999")
                .role(Role.MANAGER)
                .status(UserStatus.ACTIVE)
                .warehouses(new HashSet<>(List.of(warehouses.get(2))))
                .build()));
        users.add(userRepository.save(User.builder()
                .username("nhanvien.dn")
                .email("nhanvien.dn@smartstock.local")
                .passwordHash(passwordEncoder.encode("Nhanvien@123"))
                .fullName("Phạm Thị Đà Nẵng")
                .phone("0933444555")
                .role(Role.STAFF)
                .status(UserStatus.ACTIVE)
                .warehouses(new HashSet<>(List.of(warehouses.get(1))))
                .build()));

        // Một tài khoản đã bị khóa để minh họa chức năng khóa tài khoản
        users.add(userRepository.save(User.builder()
                .username("nhanvien.cu")
                .email("nhanvien.cu@smartstock.local")
                .passwordHash(passwordEncoder.encode("Nhanvien@123"))
                .fullName("Hoàng Văn Đã Nghỉ")
                .phone("0922333444")
                .role(Role.STAFF)
                .status(UserStatus.LOCKED)
                .warehouses(new HashSet<>(List.of(warehouses.get(0))))
                .build()));

        log.info("Đã tạo {} tài khoản người dùng", users.size());
        return users;
    }

    /**
     * Sinh hơn 300 phiếu nhập/xuất rải đều 6 tháng gần nhất.
     *
     * <p>Chiến lược sinh dữ liệu:
     *
     * <ol>
     *   <li><b>Giai đoạn mồi</b>: mỗi kho có một loạt phiếu nhập đầu kỳ để mọi sản phẩm
     *       đều có tồn, nếu không thì các phiếu xuất sau đó sẽ bị chặn vì thiếu hàng.</li>
     *   <li><b>Giai đoạn vận hành</b>: xen kẽ nhập và xuất theo thời gian tăng dần, với
     *       lượng xuất ngẫu nhiên nhưng luôn nhỏ hơn tồn hiện có.</li>
     *   <li><b>Các trạng thái khác</b>: một số phiếu để ở nháp, chờ duyệt, bị từ chối hoặc
     *       đã hủy để giao diện có đủ dữ liệu minh họa mọi trạng thái.</li>
     * </ol>
     */
    private void seedDocuments(
            List<Warehouse> warehouses,
            List<Product> products,
            List<Supplier> suppliers,
            List<Customer> customers,
            List<User> users) {

        LocalDate startDate = LocalDate.now().minusMonths(HISTORY_MONTHS);
        int receiptCount = 0;
        int issueCount = 0;

        // ---------- Giai đoạn 1: nhập mồi cho từng kho ----------
        for (Warehouse warehouse : warehouses) {
            // Chia 150 sản phẩm thành các phiếu khoảng 15 dòng để giống thực tế
            for (int batchStart = 0; batchStart < products.size(); batchStart += 15) {
                int batchEnd = Math.min(batchStart + 15, products.size());
                List<Product> batch = products.subList(batchStart, batchEnd);

                LocalDate documentDate = startDate.plusDays(random.nextInt(10));
                createCompletedReceipt(
                        warehouse,
                        batch,
                        suppliers.get(random.nextInt(suppliers.size())),
                        documentDate,
                        users,
                        true);
                receiptCount++;
            }
        }

        // ---------- Giai đoạn 2: vận hành hằng ngày ----------
        // Mỗi ngày sinh 1-2 phiếu xuất và có thể thêm phiếu nhập, để tổng số phiếu
        // vượt mốc 300 như yêu cầu của đặc tả (mục 6.2)
        int operationalDays = (int) java.time.temporal.ChronoUnit.DAYS.between(startDate.plusDays(10), LocalDate.now());

        for (int day = 0; day < operationalDays; day++) {
            LocalDate documentDate = startDate.plusDays(10L + day);

            // Bỏ qua ngày Chủ nhật để dữ liệu giống nhịp làm việc thật
            if (documentDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                continue;
            }

            int issuesToday = 1 + random.nextInt(2);
            for (int i = 0; i < issuesToday; i++) {
                Warehouse warehouse = warehouses.get(random.nextInt(warehouses.size()));
                List<Product> issueProducts = pickSellableProducts(products, 2 + random.nextInt(6));
                if (createCompletedIssue(
                        warehouse,
                        issueProducts,
                        customers.get(random.nextInt(customers.size())),
                        documentDate,
                        users)) {
                    issueCount++;
                }
            }

            // Xác suất 35%: nhập bổ sung để kho không cạn hàng
            if (random.nextInt(100) < 35) {
                Warehouse warehouse = warehouses.get(random.nextInt(warehouses.size()));
                List<Product> receiptProducts = pickSellableProducts(products, 3 + random.nextInt(8));
                createCompletedReceipt(
                        warehouse,
                        receiptProducts,
                        suppliers.get(random.nextInt(suppliers.size())),
                        documentDate,
                        users,
                        false);
                receiptCount++;
            }
        }

        // ---------- Giai đoạn 3: phiếu ở các trạng thái khác ----------
        seedDocumentsInOtherStatuses(warehouses, products, suppliers, customers, users);

        // ---------- Giai đoạn 4: tạo tình huống minh họa cho demo ----------
        driveSomeProductsBelowMinStock(warehouses.get(0), products, customers, users);

        log.info("Đã tạo {} phiếu nhập và {} phiếu xuất hoàn tất", receiptCount, issueCount);
    }

    /**
     * Tạo một phiếu nhập đã HOÀN TẤT.
     *
     * <p>Điểm then chốt: sau khi lưu phiếu, hàm gọi {@link InventoryService#applyReceipt}
     * để cộng tồn và ghi sổ giao dịch - đúng như khi người dùng bấm Duyệt trên giao diện.
     *
     * @param isInitial true là phiếu nhập mồi đầu kỳ (số lượng lớn hơn)
     */
    private void createCompletedReceipt(
            Warehouse warehouse,
            List<Product> products,
            Supplier supplier,
            LocalDate documentDate,
            List<User> users,
            boolean isInitial) {

        transactionTemplate.executeWithoutResult(status -> {
            User creator = pickStaffOf(warehouse, users);
            User approver = pickManagerOf(warehouse, users);
            loginAs(creator);

            StockReceipt receipt = StockReceipt.builder()
                    .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_RECEIPT, documentDate))
                    .warehouse(warehouse)
                    .receiptType(ReceiptType.PURCHASE)
                    .documentDate(documentDate)
                    .supplier(supplier)
                    .note("Nhập mua theo đơn hàng tháng " + documentDate.getMonthValue())
                    .status(DocumentStatus.COMPLETED)
                    .approvedBy(approver)
                    .approvedAt(documentDate.atTime(9, 30))
                    .submittedAt(documentDate.atTime(8, 15))
                    .build();

            List<StockMovement> movements = new ArrayList<>();
            for (Product product : products) {
                // Phiếu mồi nhập nhiều để các phiếu xuất sau không bị thiếu hàng
                int quantity = isInitial ? 80 + random.nextInt(120) : 20 + random.nextInt(60);

                // Giá nhập dao động +/- 5% quanh giá tham chiếu, giống biến động thực tế
                BigDecimal priceFactor = BigDecimal.valueOf(0.95 + random.nextDouble() * 0.10);
                BigDecimal unitPrice = product.getPurchasePrice()
                        .multiply(priceFactor)
                        .setScale(0, RoundingMode.HALF_UP);

                StockReceiptItem item = StockReceiptItem.builder()
                        .product(product)
                        .quantity(BigDecimal.valueOf(quantity))
                        .unitPrice(unitPrice)
                        .build();
                item.recalculateAmount();
                receipt.addItem(item);

                movements.add(new StockMovement(
                        product,
                        warehouse,
                        BigDecimal.valueOf(quantity),
                        unitPrice,
                        RefType.RECEIPT,
                        0L, // cập nhật lại sau khi có id phiếu
                        receipt.getCode(),
                        null,
                        documentDate.atTime(9, 30)));
            }
            receipt.recalculateTotal();
            StockReceipt saved = receiptRepository.save(receipt);

            // Gắn id phiếu thật vào các giao dịch rồi áp dụng qua InventoryService
            List<StockMovement> finalMovements = movements.stream()
                    .map(movement -> new StockMovement(
                            movement.product(),
                            movement.warehouse(),
                            movement.quantity(),
                            movement.unitPrice(),
                            movement.refType(),
                            saved.getId(),
                            movement.refCode(),
                            movement.note(),
                            movement.transactionDate()))
                    .toList();

            inventoryService.applyReceipt(finalMovements);
        });
    }

    /**
     * Tạo một phiếu xuất đã HOÀN TẤT.
     *
     * @return false nếu không đủ tồn để xuất (bỏ qua phiếu này)
     */
    private boolean createCompletedIssue(
            Warehouse warehouse,
            List<Product> products,
            Customer customer,
            LocalDate documentDate,
            List<User> users) {

        try {
            transactionTemplate.executeWithoutResult(status -> {
                User creator = pickStaffOf(warehouse, users);
                User approver = pickManagerOf(warehouse, users);
                loginAs(creator);

                StockIssue issue = StockIssue.builder()
                        .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_ISSUE, documentDate))
                        .warehouse(warehouse)
                        .issueType(IssueType.SALE)
                        .documentDate(documentDate)
                        .customer(customer)
                        .note("Xuất bán cho " + customer.getName())
                        .status(DocumentStatus.COMPLETED)
                        .approvedBy(approver)
                        .approvedAt(documentDate.atTime(15, 0))
                        .submittedAt(documentDate.atTime(14, 0))
                        .build();

                List<StockMovement> movements = new ArrayList<>();
                for (Product product : products) {
                    BigDecimal onHand = inventoryService.getQuantityOnHand(product.getId(), warehouse.getId());
                    if (onHand.compareTo(BigDecimal.TEN) <= 0) {
                        continue; // còn quá ít thì không xuất sản phẩm này
                    }

                    // Xuất tối đa 30% tồn hiện có để kho không bị cạn
                    int maxQuantity = onHand.multiply(BigDecimal.valueOf(0.3)).intValue();
                    int quantity = 1 + random.nextInt(Math.max(maxQuantity, 1));

                    BigDecimal cost = inventoryService.getAverageCost(product.getId(), warehouse.getId());
                    StockIssueItem item = StockIssueItem.builder()
                            .product(product)
                            .quantity(BigDecimal.valueOf(quantity))
                            .unitCost(cost)
                            .build();
                    item.recalculateAmount();
                    issue.addItem(item);

                    movements.add(new StockMovement(
                            product,
                            warehouse,
                            BigDecimal.valueOf(quantity),
                            cost,
                            RefType.ISSUE,
                            0L,
                            issue.getCode(),
                            null,
                            documentDate.atTime(15, 0)));
                }

                if (movements.isEmpty()) {
                    status.setRollbackOnly();
                    return;
                }

                issue.recalculateTotal();
                StockIssue saved = issueRepository.save(issue);

                List<StockMovement> finalMovements = movements.stream()
                        .map(movement -> new StockMovement(
                                movement.product(),
                                movement.warehouse(),
                                movement.quantity(),
                                movement.unitPrice(),
                                movement.refType(),
                                saved.getId(),
                                movement.refCode(),
                                movement.note(),
                                movement.transactionDate()))
                        .toList();

                inventoryService.applyIssue(finalMovements);
            });
            return true;
        } catch (RuntimeException ex) {
            log.debug("Bỏ qua một phiếu xuất demo vì không đủ tồn: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Tạo các phiếu ở trạng thái NHÁP, CHỜ DUYỆT, BỊ TỪ CHỐI và ĐÃ HỦY.
     *
     * <p>Cần thiết để giao diện danh sách phiếu và bộ lọc trạng thái có dữ liệu minh họa,
     * và để người trình bày demo được luồng duyệt ngay mà không phải tự lập phiếu.
     */
    private void seedDocumentsInOtherStatuses(
            List<Warehouse> warehouses,
            List<Product> products,
            List<Supplier> suppliers,
            List<Customer> customers,
            List<User> users) {

        LocalDate today = LocalDate.now();

        // 6 phiếu nhập chờ duyệt (2 phiếu mỗi kho) - để demo chức năng duyệt
        for (Warehouse warehouse : warehouses) {
            for (int i = 0; i < 2; i++) {
                createDocumentWithStatus(
                        warehouse,
                        pickRandomProducts(products, 3 + random.nextInt(4)),
                        suppliers.get(random.nextInt(suppliers.size())),
                        today.minusDays(i),
                        users,
                        DocumentStatus.PENDING);
            }
        }

        // 3 phiếu nhập nháp
        for (Warehouse warehouse : warehouses) {
            createDocumentWithStatus(
                    warehouse,
                    pickRandomProducts(products, 2 + random.nextInt(3)),
                    suppliers.get(random.nextInt(suppliers.size())),
                    today,
                    users,
                    DocumentStatus.DRAFT);
        }

        // 2 phiếu bị từ chối
        for (int i = 0; i < 2; i++) {
            createDocumentWithStatus(
                    warehouses.get(i),
                    pickRandomProducts(products, 3),
                    suppliers.get(random.nextInt(suppliers.size())),
                    today.minusDays(3L + i),
                    users,
                    DocumentStatus.REJECTED);
        }

        // 3 phiếu xuất chờ duyệt
        for (Warehouse warehouse : warehouses) {
            transactionTemplate.executeWithoutResult(status -> {
                User creator = pickStaffOf(warehouse, users);
                loginAs(creator);

                StockIssue issue = StockIssue.builder()
                        .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_ISSUE, today))
                        .warehouse(warehouse)
                        .issueType(IssueType.SALE)
                        .documentDate(today)
                        .customer(customers.get(random.nextInt(customers.size())))
                        .note("Phiếu xuất đang chờ quản lý duyệt")
                        .status(DocumentStatus.PENDING)
                        .submittedAt(LocalDateTime.now().minusHours(2))
                        .build();

                for (Product product : pickRandomProducts(products, 2 + random.nextInt(3))) {
                    BigDecimal onHand = inventoryService.getQuantityOnHand(product.getId(), warehouse.getId());
                    if (onHand.compareTo(BigDecimal.valueOf(5)) <= 0) {
                        continue;
                    }
                    StockIssueItem item = StockIssueItem.builder()
                            .product(product)
                            .quantity(BigDecimal.valueOf(1 + random.nextInt(5)))
                            .unitCost(BigDecimal.ZERO)
                            .build();
                    item.recalculateAmount();
                    issue.addItem(item);
                }

                if (issue.getItems().isEmpty()) {
                    status.setRollbackOnly();
                    return;
                }
                issueRepository.save(issue);
            });
        }

        // 2 phiếu nhập đã hoàn tất rồi bị HỦY (có giao dịch đảo ngược)
        for (int i = 0; i < 2; i++) {
            final int index = i;
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Warehouse warehouse = warehouses.get(index);
                    User creator = pickStaffOf(warehouse, users);
                    User approver = pickManagerOf(warehouse, users);
                    loginAs(approver);

                    List<Product> batch = pickRandomProducts(products, 2);
                    LocalDate documentDate = today.minusDays(5L + index);

                    StockReceipt receipt = StockReceipt.builder()
                            .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_RECEIPT, documentDate))
                            .warehouse(warehouse)
                            .receiptType(ReceiptType.PURCHASE)
                            .documentDate(documentDate)
                            .supplier(suppliers.get(0))
                            .note("Phiếu nhập bị hủy do nhập sai nhà cung cấp")
                            .status(DocumentStatus.COMPLETED)
                            .approvedBy(approver)
                            .approvedAt(documentDate.atTime(10, 0))
                            .build();

                    List<StockMovement> movements = new ArrayList<>();
                    for (Product product : batch) {
                        StockReceiptItem item = StockReceiptItem.builder()
                                .product(product)
                                .quantity(BigDecimal.valueOf(10))
                                .unitPrice(product.getPurchasePrice())
                                .build();
                        item.recalculateAmount();
                        receipt.addItem(item);
                    }
                    receipt.recalculateTotal();
                    StockReceipt saved = receiptRepository.save(receipt);

                    for (StockReceiptItem item : saved.getItems()) {
                        movements.add(new StockMovement(
                                item.getProduct(),
                                warehouse,
                                item.getQuantity(),
                                item.getUnitPrice(),
                                RefType.RECEIPT,
                                saved.getId(),
                                saved.getCode(),
                                null,
                                documentDate.atTime(10, 0)));
                    }
                    var transactions = inventoryService.applyReceipt(movements);

                    // Hủy ngay: sinh giao dịch đảo ngược đúng như nghiệp vụ thật
                    inventoryService.reverseTransactions(transactions, "Nhập sai nhà cung cấp, hủy để lập lại");
                    saved.setStatus(DocumentStatus.CANCELLED);
                    saved.setCancelledBy(approver);
                    saved.setCancelledAt(LocalDateTime.now());
                    saved.setCancelReason("Nhập sai nhà cung cấp, hủy để lập lại");
                    receiptRepository.save(saved);
                });
            } catch (RuntimeException ex) {
                log.debug("Bỏ qua một phiếu hủy demo: {}", ex.getMessage());
            }
        }
    }

    /** Tạo phiếu nhập ở trạng thái chưa hoàn tất (không tác động tồn kho). */
    private void createDocumentWithStatus(
            Warehouse warehouse,
            List<Product> products,
            Supplier supplier,
            LocalDate documentDate,
            List<User> users,
            DocumentStatus targetStatus) {

        transactionTemplate.executeWithoutResult(status -> {
            User creator = pickStaffOf(warehouse, users);
            loginAs(creator);

            StockReceipt receipt = StockReceipt.builder()
                    .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_RECEIPT, documentDate))
                    .warehouse(warehouse)
                    .receiptType(ReceiptType.PURCHASE)
                    .documentDate(documentDate)
                    .supplier(supplier)
                    .status(targetStatus)
                    .note(switch (targetStatus) {
                        case DRAFT -> "Phiếu nháp, đang chờ nhập đủ thông tin";
                        case PENDING -> "Phiếu đang chờ quản lý duyệt";
                        case REJECTED -> "Phiếu bị từ chối, cần sửa lại";
                        default -> null;
                    })
                    .submittedAt(targetStatus == DocumentStatus.DRAFT ? null : documentDate.atTime(8, 0))
                    .rejectReason(
                            targetStatus == DocumentStatus.REJECTED
                                    ? "Đơn giá nhập cao hơn giá hợp đồng, vui lòng kiểm tra lại với nhà cung cấp"
                                    : null)
                    .build();

            for (Product product : products) {
                StockReceiptItem item = StockReceiptItem.builder()
                        .product(product)
                        .quantity(BigDecimal.valueOf(10 + random.nextInt(40)))
                        .unitPrice(product.getPurchasePrice())
                        .build();
                item.recalculateAmount();
                receipt.addItem(item);
            }
            receipt.recalculateTotal();
            receiptRepository.save(receipt);
        });
    }

    /**
     * Tạo 2 đợt kiểm kê: một đợt đã duyệt (có điều chỉnh tồn) và một đợt đang chờ duyệt.
     */
    private void seedStocktakes(List<Warehouse> warehouses, List<Product> products) {
        // Đợt 1: đã hoàn tất tại kho Hà Nội, có chênh lệch thực tế
        transactionTemplate.executeWithoutResult(status -> {
            Warehouse warehouse = warehouses.get(0);
            LocalDate documentDate = LocalDate.now().minusDays(20);

            Stocktake stocktake = Stocktake.builder()
                    .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_STOCKTAKE, documentDate))
                    .warehouse(warehouse)
                    .documentDate(documentDate)
                    .note("Kiểm kê định kỳ cuối tháng")
                    .status(DocumentStatus.COMPLETED)
                    .approvedAt(documentDate.atTime(17, 0))
                    .submittedAt(documentDate.atTime(16, 0))
                    .build();

            List<StockMovement> adjustments = new ArrayList<>();
            for (Product product : pickRandomProducts(products, 20)) {
                BigDecimal systemQuantity = inventoryService.getQuantityOnHand(product.getId(), warehouse.getId());
                BigDecimal unitCost = inventoryService.getAverageCost(product.getId(), warehouse.getId());

                // 25% mặt hàng có chênh lệch nhỏ, giống thực tế kiểm kê
                BigDecimal actualQuantity = systemQuantity;
                if (random.nextInt(100) < 25 && systemQuantity.compareTo(BigDecimal.valueOf(5)) > 0) {
                    int difference = random.nextInt(5) - 2; // từ -2 đến +2
                    actualQuantity = systemQuantity.add(BigDecimal.valueOf(difference));
                    if (actualQuantity.signum() < 0) {
                        actualQuantity = BigDecimal.ZERO;
                    }
                }

                stocktake.addItem(StocktakeItem.builder()
                        .product(product)
                        .systemQuantity(systemQuantity)
                        .actualQuantity(actualQuantity)
                        .unitCost(unitCost)
                        .note(actualQuantity.compareTo(systemQuantity) == 0 ? null : "Lệch khi đếm thực tế")
                        .build());

                if (actualQuantity.compareTo(systemQuantity) != 0) {
                    adjustments.add(new StockMovement(
                            product,
                            warehouse,
                            actualQuantity,
                            unitCost,
                            RefType.STOCKTAKE,
                            0L,
                            stocktake.getCode(),
                            "Điều chỉnh theo kiểm kê",
                            documentDate.atTime(17, 0)));
                }
            }

            Stocktake saved = stocktakeRepository.save(stocktake);

            if (!adjustments.isEmpty()) {
                List<StockMovement> finalAdjustments = adjustments.stream()
                        .map(movement -> new StockMovement(
                                movement.product(),
                                movement.warehouse(),
                                movement.quantity(),
                                movement.unitPrice(),
                                movement.refType(),
                                saved.getId(),
                                movement.refCode(),
                                movement.note(),
                                movement.transactionDate()))
                        .toList();
                inventoryService.applyStocktakeAdjustment(finalAdjustments);
            }
        });

        // Đợt 2: đang chờ duyệt tại kho Đà Nẵng - để demo chức năng duyệt kiểm kê
        transactionTemplate.executeWithoutResult(status -> {
            Warehouse warehouse = warehouses.get(1);
            LocalDate documentDate = LocalDate.now().minusDays(1);

            Stocktake stocktake = Stocktake.builder()
                    .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_STOCKTAKE, documentDate))
                    .warehouse(warehouse)
                    .documentDate(documentDate)
                    .note("Kiểm kê đột xuất, chờ quản lý duyệt")
                    .status(DocumentStatus.PENDING)
                    .submittedAt(LocalDateTime.now().minusHours(3))
                    .build();

            for (Product product : pickRandomProducts(products, 15)) {
                BigDecimal systemQuantity = inventoryService.getQuantityOnHand(product.getId(), warehouse.getId());
                BigDecimal unitCost = inventoryService.getAverageCost(product.getId(), warehouse.getId());

                BigDecimal actualQuantity = systemQuantity;
                if (random.nextInt(100) < 30 && systemQuantity.compareTo(BigDecimal.valueOf(3)) > 0) {
                    actualQuantity = systemQuantity.subtract(BigDecimal.valueOf(1 + random.nextInt(3)));
                }

                stocktake.addItem(StocktakeItem.builder()
                        .product(product)
                        .systemQuantity(systemQuantity)
                        .actualQuantity(actualQuantity)
                        .unitCost(unitCost)
                        .build());
            }
            stocktakeRepository.save(stocktake);
        });

        log.info("Đã tạo 2 đợt kiểm kê");
    }

    /**
     * Đánh dấu một vài sản phẩm là NGỪNG KINH DOANH.
     *
     * <p>Đặc tả yêu cầu dữ liệu demo có sản phẩm ngừng kinh doanh để minh họa rằng chúng
     * không chọn được khi lập phiếu mới.
     */
    @Transactional
    protected void markSomeProductsDiscontinued(List<Product> products) {
        for (int i = 0; i < 5; i++) {
            Product product = products.get(products.size() - 1 - i);
            product.setStatus(RecordStatus.DISCONTINUED);
            productRepository.save(product);
        }
        log.info("Đã đánh dấu 5 sản phẩm ngừng kinh doanh");
    }

    /**
     * Xuất hàng mạnh cho một số sản phẩm để tồn rơi xuống DƯỚI mức tối thiểu.
     *
     * <p>Đặc tả (mục 6.2) yêu cầu dữ liệu demo có sẵn sản phẩm dưới mức tồn tối thiểu, để
     * trang Dashboard, danh sách cảnh báo tồn thấp và trang Gợi ý nhập hàng đều có số liệu
     * minh họa ngay khi mở lần đầu.
     */
    private void driveSomeProductsBelowMinStock(
            Warehouse warehouse, List<Product> products, List<Customer> customers, List<User> users) {

        int affected = 0;
        // Chọn các sản phẩm ở giữa danh sách (tránh nhóm hàng tồn lâu ở đầu)
        for (int i = 40; i < 60 && affected < 8; i++) {
            Product product = products.get(i);
            if (product.getMinStock().signum() <= 0) {
                continue;
            }

            BigDecimal onHand = inventoryService.getQuantityOnHand(product.getId(), warehouse.getId());
            // Xuất để còn lại khoảng một nửa mức tồn tối thiểu
            BigDecimal targetRemaining = product.getMinStock().divide(BigDecimal.valueOf(2), 0, RoundingMode.FLOOR);
            BigDecimal quantityToIssue = onHand.subtract(targetRemaining);

            if (quantityToIssue.signum() <= 0) {
                continue;
            }

            final BigDecimal issueQuantity = quantityToIssue;
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    User creator = pickStaffOf(warehouse, users);
                    User approver = pickManagerOf(warehouse, users);
                    loginAs(creator);

                    LocalDate documentDate = LocalDate.now().minusDays(2);
                    StockIssue issue = StockIssue.builder()
                            .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_ISSUE, documentDate))
                            .warehouse(warehouse)
                            .issueType(IssueType.SALE)
                            .documentDate(documentDate)
                            .customer(customers.get(random.nextInt(customers.size())))
                            .note("Đơn hàng lớn, tồn kho xuống dưới mức tối thiểu")
                            .status(DocumentStatus.COMPLETED)
                            .approvedBy(approver)
                            .approvedAt(documentDate.atTime(16, 0))
                            .submittedAt(documentDate.atTime(15, 0))
                            .build();

                    BigDecimal cost = inventoryService.getAverageCost(product.getId(), warehouse.getId());
                    StockIssueItem item = StockIssueItem.builder()
                            .product(product)
                            .quantity(issueQuantity)
                            .unitCost(cost)
                            .build();
                    item.recalculateAmount();
                    issue.addItem(item);
                    issue.recalculateTotal();

                    StockIssue saved = issueRepository.save(issue);
                    inventoryService.applyIssue(List.of(new StockMovement(
                            product,
                            warehouse,
                            issueQuantity,
                            cost,
                            RefType.ISSUE,
                            saved.getId(),
                            saved.getCode(),
                            null,
                            documentDate.atTime(16, 0))));
                });
                affected++;
            } catch (RuntimeException ex) {
                log.debug("Bỏ qua phiếu xuất tạo tồn thấp: {}", ex.getMessage());
            }
        }
        log.info("Đã tạo {} sản phẩm có tồn dưới mức tối thiểu để minh họa cảnh báo", affected);
    }

    /**
     * Chọn sản phẩm để xuất, LOẠI TRỪ nhóm hàng tồn lâu.
     *
     * <p>20 sản phẩm đầu danh sách được giữ nguyên không xuất sau giai đoạn nhập mồi, nhằm
     * tạo dữ liệu cho báo cáo "hàng tồn lâu" (không phát sinh xuất trong hơn 90 ngày).
     */
    private List<Product> pickSellableProducts(List<Product> products, int count) {
        List<Product> sellable = products.subList(20, products.size());
        return pickRandomProducts(sellable, count);
    }

    /** Chọn ngẫu nhiên một số sản phẩm, không trùng lặp trong cùng một phiếu. */
    private List<Product> pickRandomProducts(List<Product> products, int count) {
        Set<Integer> indexes = new HashSet<>();
        int limit = Math.min(count, products.size());
        while (indexes.size() < limit) {
            indexes.add(random.nextInt(products.size()));
        }
        return indexes.stream().map(products::get).toList();
    }

    /** Lấy một nhân viên của kho để làm người lập phiếu. */
    private User pickStaffOf(Warehouse warehouse, List<User> users) {
        return users.stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRole() == Role.STAFF)
                .filter(user -> user.canAccessWarehouse(warehouse.getId()))
                .findFirst()
                .orElseGet(() -> pickManagerOf(warehouse, users));
    }

    /** Lấy một quản lý của kho để làm người duyệt. */
    private User pickManagerOf(Warehouse warehouse, List<User> users) {
        return users.stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRole() == Role.MANAGER)
                .filter(user -> user.canAccessWarehouse(warehouse.getId()))
                .findFirst()
                .orElse(users.get(0)); // dự phòng: tài khoản admin
    }

    /**
     * Đặt người dùng hiện tại vào SecurityContext.
     *
     * <p>Cần thiết vì {@code InventoryService} và JPA Auditing đọc người đang đăng nhập để
     * ghi {@code created_by}. Nhờ vậy dữ liệu demo có người lập, người duyệt hợp lý thay vì
     * để trống.
     */
    private void loginAs(User user) {
        AppUserDetails details = new AppUserDetails(user);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
