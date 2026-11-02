package com.smartstock.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartstock.catalog.Product;
import com.smartstock.common.InsufficientStockException;
import com.smartstock.inventory.InventoryService.StockMovement;
import com.smartstock.support.AuthTestSupport;
import com.smartstock.support.TestDataFactory;
import com.smartstock.support.TestProfileResolver;
import com.smartstock.user.Role;
import com.smartstock.user.User;
import com.smartstock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test tích hợp cho {@link InventoryService} - lớp duy nhất được sửa tồn kho.
 *
 * <p>Đây là bộ test quan trọng nhất của dự án. Nó kiểm chứng toàn bộ các quy tắc được
 * nêu trong đặc tả mục 8:
 *
 * <ul>
 *   <li>Nhập kho cộng tồn, xuất kho trừ tồn.</li>
 *   <li>Chặn xuất vượt tồn, kèm thông báo nêu rõ sản phẩm thiếu.</li>
 *   <li>Giá vốn bình quân gia quyền được cập nhật đúng khi nhập.</li>
 *   <li>Hủy phiếu sinh giao dịch đảo ngược.</li>
 *   <li>Kiểm kê điều chỉnh tồn về số thực tế.</li>
 *   <li><b>Bất biến:</b> tồn kho = tổng các giao dịch trong sổ kho.</li>
 * </ul>
 *
 * <p>Chạy trên cơ sở dữ liệu H2 trong bộ nhớ (hồ sơ {@code test}) nên không cần cài MySQL.
 * Để chạy lại trên MySQL thật: {@code ./mvnw test -Dspring.profiles.active=mysqltest}.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver.class)
@Import(TestDataFactory.class)
@DisplayName("Lõi kho - InventoryService")
class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private TestDataFactory dataFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Warehouse warehouse;
    private Product product;
    private User manager;

    @BeforeEach
    void setUp() {
        warehouse = dataFactory.createWarehouse("Kho kiểm thử");
        product = dataFactory.createProduct("Nước ngọt Coca 330ml");
        manager = dataFactory.createUser(Role.MANAGER, "Test@1234", warehouse);
        AuthTestSupport.loginAs(manager);
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.clear();
    }

    @Test
    @DisplayName("Nhập kho làm tăng tồn và đặt giá vốn bằng đơn giá nhập")
    void nhapKho_tangTon() {
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");

        Inventory inventory = findInventory();
        assertThat(inventory.getQuantity()).isEqualByComparingTo("100");
        assertThat(inventory.getAvgCost()).isEqualByComparingTo("50000");

        // Mỗi dòng phiếu phải sinh đúng một dòng trong sổ giao dịch
        List<StockTransaction> transactions = allTransactions();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.RECEIPT);
        assertThat(transactions.get(0).getQuantityChange()).isEqualByComparingTo("100");
        assertThat(transactions.get(0).getBalanceAfter()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Nhập hai lần với giá khác nhau thì giá vốn là bình quân gia quyền")
    void nhapHaiLan_giaVonBinhQuan() {
        applyReceipt(product, new BigDecimal("10"), new BigDecimal("100000"), "PN-TEST-001");
        applyReceipt(product, new BigDecimal("10"), new BigDecimal("120000"), "PN-TEST-002");

        Inventory inventory = findInventory();
        assertThat(inventory.getQuantity()).isEqualByComparingTo("20");
        // (10 x 100.000 + 10 x 120.000) / 20 = 110.000
        assertThat(inventory.getAvgCost()).isEqualByComparingTo("110000");
    }

    @Test
    @DisplayName("Xuất kho làm giảm tồn, giá vốn không đổi")
    void xuatKho_giamTon() {
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");
        applyIssue(product, new BigDecimal("30"), "PX-TEST-001");

        Inventory inventory = findInventory();
        assertThat(inventory.getQuantity()).isEqualByComparingTo("70");
        assertThat(inventory.getAvgCost()).isEqualByComparingTo("50000");

        List<StockTransaction> transactions = allTransactions();
        assertThat(transactions).hasSize(2);
        StockTransaction issueTransaction = transactions.get(1);
        assertThat(issueTransaction.getType()).isEqualTo(TransactionType.ISSUE);
        // Giao dịch xuất mang dấu âm
        assertThat(issueTransaction.getQuantityChange()).isEqualByComparingTo("-30");
        assertThat(issueTransaction.getBalanceAfter()).isEqualByComparingTo("70");
        // Giá vốn tại thời điểm xuất được ghi lại để phục vụ báo cáo
        assertThat(issueTransaction.getUnitCost()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("Chặn xuất vượt tồn và nêu rõ sản phẩm nào thiếu, còn bao nhiêu")
    void chanXuatVuotTon() {
        applyReceipt(product, new BigDecimal("10"), new BigDecimal("50000"), "PN-TEST-001");

        assertThatThrownBy(() -> applyIssue(product, new BigDecimal("15"), "PX-TEST-001"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining(product.getName())
                .hasMessageContaining(product.getSku())
                .hasMessageContaining("cần 15")
                .hasMessageContaining("chỉ còn 10");

        // Giao dịch bị rollback: tồn giữ nguyên và không có giao dịch xuất nào được ghi
        assertThat(findInventory().getQuantity()).isEqualByComparingTo("10");
        assertThat(allTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("Xuất đúng bằng số tồn thì được phép, tồn về 0")
    void xuatDungBangTon_duocPhep() {
        applyReceipt(product, new BigDecimal("10"), new BigDecimal("50000"), "PN-TEST-001");
        applyIssue(product, new BigDecimal("10"), "PX-TEST-001");

        assertThat(findInventory().getQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Chặn xuất khi sản phẩm chưa từng có tồn ở kho")
    void chanXuatKhiChuaCoTon() {
        assertThatThrownBy(() -> applyIssue(product, new BigDecimal("1"), "PX-TEST-001"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("chỉ còn 0");
    }

    @Test
    @DisplayName("Hủy phiếu nhập sinh giao dịch đảo ngược làm giảm tồn")
    void huyPhieuNhap_daoNguoc() {
        List<StockTransaction> receiptTransactions = transactionTemplate.execute(status -> inventoryService.applyReceipt(
                List.of(movement(product, new BigDecimal("50"), new BigDecimal("50000"), "PN-TEST-001"))));

        transactionTemplate.execute(
                status -> inventoryService.reverseTransactions(receiptTransactions, "Nhập sai số lượng"));

        assertThat(findInventory().getQuantity()).isEqualByComparingTo("0");

        List<StockTransaction> transactions = allTransactions();
        assertThat(transactions).hasSize(2);
        StockTransaction reversal = transactions.get(1);
        assertThat(reversal.getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversal.getQuantityChange()).isEqualByComparingTo("-50");
        assertThat(reversal.getNote()).contains("Nhập sai số lượng");
    }

    @Test
    @DisplayName("Chặn hủy phiếu nhập khi hàng đã xuất đi, không còn đủ để trừ lại")
    void chanHuyPhieuNhapKhiDaXuatHang() {
        List<StockTransaction> receiptTransactions = transactionTemplate.execute(status -> inventoryService.applyReceipt(
                List.of(movement(product, new BigDecimal("50"), new BigDecimal("50000"), "PN-TEST-001"))));

        // Xuất 40 -> chỉ còn 10, không thể đảo ngược phiếu nhập 50
        applyIssue(product, new BigDecimal("40"), "PX-TEST-001");

        assertThatThrownBy(() -> transactionTemplate.execute(
                        status -> inventoryService.reverseTransactions(receiptTransactions, "Hủy thử")))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("chỉ còn 10");

        assertThat(findInventory().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Hủy phiếu xuất sinh giao dịch đảo ngược làm tăng tồn trở lại")
    void huyPhieuXuat_daoNguoc() {
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");
        List<StockTransaction> issueTransactions = transactionTemplate.execute(status ->
                inventoryService.applyIssue(List.of(movement(product, new BigDecimal("30"), null, "PX-TEST-001"))));

        transactionTemplate.execute(
                status -> inventoryService.reverseTransactions(issueTransactions, "Khách đổi ý"));

        assertThat(findInventory().getQuantity()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Kiểm kê điều chỉnh tồn về đúng số thực tế (thiếu hàng)")
    void kiemKe_dieuChinhGiam() {
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");

        // Đếm thực tế chỉ còn 95 -> điều chỉnh giảm 5
        transactionTemplate.execute(status -> inventoryService.applyStocktakeAdjustment(
                List.of(movement(product, new BigDecimal("95"), new BigDecimal("50000"), "KK-TEST-001"))));

        assertThat(findInventory().getQuantity()).isEqualByComparingTo("95");

        StockTransaction adjustment = allTransactions().get(1);
        assertThat(adjustment.getType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(adjustment.getQuantityChange()).isEqualByComparingTo("-5");
    }

    @Test
    @DisplayName("Kiểm kê điều chỉnh tồn về đúng số thực tế (thừa hàng)")
    void kiemKe_dieuChinhTang() {
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");

        transactionTemplate.execute(status -> inventoryService.applyStocktakeAdjustment(
                List.of(movement(product, new BigDecimal("103"), new BigDecimal("50000"), "KK-TEST-001"))));

        assertThat(findInventory().getQuantity()).isEqualByComparingTo("103");
        assertThat(allTransactions().get(1).getQuantityChange()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("Kiểm kê không lệch thì không ghi giao dịch nào")
    void kiemKeKhongLech_khongGhiGiaoDich() {
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");

        transactionTemplate.execute(status -> inventoryService.applyStocktakeAdjustment(
                List.of(movement(product, new BigDecimal("100"), new BigDecimal("50000"), "KK-TEST-001"))));

        assertThat(allTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("BẤT BIẾN: tồn kho luôn bằng tổng các giao dịch trong sổ kho")
    void batBien_tonBangTongGiaoDich() {
        // Dựng một chuỗi nghiệp vụ dài, trộn đủ các loại giao dịch
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");
        applyReceipt(product, new BigDecimal("50"), new BigDecimal("60000"), "PN-TEST-002");
        applyIssue(product, new BigDecimal("30"), "PX-TEST-001");
        applyIssue(product, new BigDecimal("20"), "PX-TEST-002");

        List<StockTransaction> issueToCancel = transactionTemplate.execute(status ->
                inventoryService.applyIssue(List.of(movement(product, new BigDecimal("10"), null, "PX-TEST-003"))));
        transactionTemplate.execute(status -> inventoryService.reverseTransactions(issueToCancel, "Hủy thử"));

        transactionTemplate.execute(status -> inventoryService.applyStocktakeAdjustment(
                List.of(movement(product, new BigDecimal("98"), new BigDecimal("55000"), "KK-TEST-001"))));

        BigDecimal sumOfTransactions = allTransactions().stream()
                .map(StockTransaction::getQuantityChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(findInventory().getQuantity())
                .as("Tồn kho phải bằng tổng mọi biến động đã ghi trong sổ giao dịch")
                .isEqualByComparingTo(sumOfTransactions);

        // Truy vấn đối chiếu của hệ thống cũng không được phát hiện sai lệch nào
        assertThat(inventoryRepository.findInventoryMismatches()).isEmpty();
    }

    @Test
    @DisplayName("Mỗi kho có số tồn độc lập cho cùng một sản phẩm")
    void tonKhoDocLapTheoKho() {
        Warehouse secondWarehouse = dataFactory.createWarehouse("Kho thứ hai");

        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");
        transactionTemplate.execute(status -> inventoryService.applyReceipt(List.of(new StockMovement(
                product,
                secondWarehouse,
                new BigDecimal("40"),
                new BigDecimal("70000"),
                RefType.RECEIPT,
                2L,
                "PN-TEST-002",
                null,
                LocalDateTime.now()))));

        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("100");
        assertThat(inventoryService.getQuantityOnHand(product.getId(), secondWarehouse.getId()))
                .isEqualByComparingTo("40");
        // Giá vốn cũng tính riêng theo từng kho
        assertThat(inventoryService.getAverageCost(product.getId(), secondWarehouse.getId()))
                .isEqualByComparingTo("70000");
    }

    @Test
    @DisplayName("Phiếu nhiều dòng: thiếu tồn ở một dòng thì cả phiếu bị chặn")
    void thieuTonMotDong_chanCaPhieu() {
        Product secondProduct = dataFactory.createProduct("Bánh quy AFC 200g");
        applyReceipt(product, new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001");
        applyReceipt(secondProduct, new BigDecimal("5"), new BigDecimal("30000"), "PN-TEST-002");

        assertThatThrownBy(() -> transactionTemplate.execute(status -> inventoryService.applyIssue(List.of(
                        movement(product, new BigDecimal("10"), null, "PX-TEST-001"),
                        movement(secondProduct, new BigDecimal("10"), null, "PX-TEST-001")))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining(secondProduct.getSku());

        // Dòng đủ hàng cũng không bị trừ, vì cả giao dịch đã rollback
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("100");
        assertThat(inventoryService.getQuantityOnHand(secondProduct.getId(), warehouse.getId()))
                .isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Kiểm tra tồn (checkAvailability) báo đúng danh sách dòng thiếu mà không thay đổi dữ liệu")
    void kiemTraTon_khongThayDoiDuLieu() {
        applyReceipt(product, new BigDecimal("8"), new BigDecimal("50000"), "PN-TEST-001");

        var shortages = inventoryService.checkAvailability(
                List.of(movement(product, new BigDecimal("10"), null, "PX-TEST-001")));

        assertThat(shortages).hasSize(1);
        assertThat(shortages.get(0).sku()).isEqualTo(product.getSku());
        assertThat(shortages.get(0).requested()).isEqualByComparingTo("10");
        assertThat(shortages.get(0).available()).isEqualByComparingTo("8");

        // Chỉ kiểm tra, không trừ tồn
        assertThat(findInventory().getQuantity()).isEqualByComparingTo("8");
    }

    // ============================== Hàm hỗ trợ ==============================

    /**
     * Gọi applyReceipt trong một giao dịch riêng.
     *
     * <p>Cần {@code TransactionTemplate} vì {@code InventoryService} khai báo
     * {@code Propagation.MANDATORY} - nó bắt buộc phải được gọi từ bên trong một giao dịch
     * đang mở, đúng như khi service phiếu gọi tới.
     */
    private void applyReceipt(Product target, BigDecimal quantity, BigDecimal unitPrice, String refCode) {
        transactionTemplate.execute(
                status -> inventoryService.applyReceipt(List.of(movement(target, quantity, unitPrice, refCode))));
    }

    private void applyIssue(Product target, BigDecimal quantity, String refCode) {
        transactionTemplate.execute(
                status -> inventoryService.applyIssue(List.of(movement(target, quantity, null, refCode))));
    }

    private StockMovement movement(Product target, BigDecimal quantity, BigDecimal unitPrice, String refCode) {
        RefType refType = refCode.startsWith("PN")
                ? RefType.RECEIPT
                : (refCode.startsWith("PX") ? RefType.ISSUE : RefType.STOCKTAKE);
        return new StockMovement(
                target,
                warehouse,
                quantity,
                unitPrice == null ? BigDecimal.ZERO : unitPrice,
                refType,
                1L,
                refCode,
                null,
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    private Inventory findInventory() {
        return inventoryRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseThrow(() -> new AssertionError("Chưa có dòng tồn kho nào được tạo"));
    }

    private List<StockTransaction> allTransactions() {
        return stockTransactionRepository.findStockCard(product.getId(), warehouse.getId(), null, null);
    }
}
