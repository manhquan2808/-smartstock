package com.smartstock.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartstock.catalog.Product;
import com.smartstock.inventory.InventoryDtos.StockCardEntryResponse;
import com.smartstock.inventory.InventoryDtos.StockCardResponse;
import com.smartstock.inventory.InventoryService.StockMovement;
import com.smartstock.support.AuthTestSupport;
import com.smartstock.support.TestDataFactory;
import com.smartstock.support.TestProfileResolver;
import com.smartstock.user.Role;
import com.smartstock.user.User;
import com.smartstock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test THẺ KHO.
 *
 * <p>Lý do có bộ test này: thẻ kho từng báo sai tồn cuối kỳ. Cột {@code balance_after} trong
 * {@code stock_transactions} là số dư lũy kế theo thứ tự GHI (id tăng dần), nhưng thẻ kho sắp xếp
 * theo NGÀY CHỨNG TỪ. Hai thứ tự này lệch nhau khi một phiếu được lập lùi ngày, hoặc khi hủy một
 * phiếu cũ sinh giao dịch đảo ngược mang ngày hôm nay. Khi đó lấy {@code balance_after} của dòng
 * cuối cùng theo ngày làm tồn cuối kỳ sẽ ra một con số không liên quan.
 *
 * <p>Đây là lỗi nguy hiểm vì nó âm thầm: màn hình vẫn hiện số, chỉ là số sai, và sai lệch với
 * chính bảng tồn kho. Các test dưới đây khóa lại hành vi đúng.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver.class)
@Import(TestDataFactory.class)
@DisplayName("Thẻ kho")
class StockCardIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryQueryService inventoryQueryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private TestDataFactory dataFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Warehouse warehouse;
    private Product product;
    private User manager;

    @BeforeEach
    void setUp() {
        warehouse = dataFactory.createWarehouse("Kho kiểm thử thẻ kho");
        product = dataFactory.createProduct("Sữa chua có đường");
        manager = dataFactory.createUser(Role.MANAGER, "Test@1234", warehouse);
        AuthTestSupport.loginAs(manager);
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.clear();
    }

    @Test
    @DisplayName("Tồn cuối kỳ khớp tồn kho thật kể cả khi phiếu được ghi lùi ngày")
    void ghiLuiNgay_tonCuoiKyVanDung() {
        // Ghi theo thứ tự này (id tăng dần) nhưng NGÀY CHỨNG TỪ thì không tăng dần:
        //   id 1: 10/01 nhập 100  -> balance_after = 100
        //   id 2: 20/01 xuất  30  -> balance_after =  70
        //   id 3: 15/01 nhập   5  -> balance_after =  75   (lập lùi ngày)
        nhap(new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001", ngay(10));
        xuat(new BigDecimal("30"), "PX-TEST-001", ngay(20));
        nhap(new BigDecimal("5"), new BigDecimal("50000"), "PN-TEST-002", ngay(15));

        BigDecimal tonThat = tonKhoThat();
        assertThat(tonThat).isEqualByComparingTo("75");

        StockCardResponse theKho = inventoryQueryService.getStockCard(
                product.getId(), warehouse.getId(), null, null);

        assertThat(theKho.entries()).hasSize(3);
        assertThat(theKho.totalIn()).isEqualByComparingTo("105");
        assertThat(theKho.totalOut()).isEqualByComparingTo("30");

        // Điểm mấu chốt: tồn cuối kỳ phải bằng tồn kho thật, không phải balance_after của
        // dòng cuối cùng theo ngày (dòng 20/01, balance_after = 70).
        assertThat(theKho.closingQuantity())
                .as("Tồn cuối kỳ trên thẻ kho phải khớp với bảng inventories")
                .isEqualByComparingTo(tonThat);
    }

    @Test
    @DisplayName("Cột tồn sau giao dịch cộng trừ khớp nhau khi đọc dọc xuống")
    void cotTonSauGiaoDich_congTruKhop() {
        nhap(new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001", ngay(10));
        xuat(new BigDecimal("30"), "PX-TEST-001", ngay(20));
        nhap(new BigDecimal("5"), new BigDecimal("50000"), "PN-TEST-002", ngay(15));

        StockCardResponse theKho = inventoryQueryService.getStockCard(
                product.getId(), warehouse.getId(), null, null);

        // Thẻ kho sắp theo ngày: 10/01 (+100), 15/01 (+5), 20/01 (-30)
        List<StockCardEntryResponse> dong = theKho.entries();
        assertThat(dong.get(0).refCode()).isEqualTo("PN-TEST-001");
        assertThat(dong.get(1).refCode()).isEqualTo("PN-TEST-002");
        assertThat(dong.get(2).refCode()).isEqualTo("PX-TEST-001");

        // Đọc dọc xuống phải cộng trừ khớp: 100 -> 105 -> 75
        assertThat(dong.get(0).balanceAfter()).isEqualByComparingTo("100");
        assertThat(dong.get(1).balanceAfter()).isEqualByComparingTo("105");
        assertThat(dong.get(2).balanceAfter()).isEqualByComparingTo("75");

        // Và tổng quát: mỗi dòng = dòng trước + nhập - xuất
        BigDecimal truoc = theKho.openingQuantity();
        for (StockCardEntryResponse d : dong) {
            BigDecimal vao = d.quantityIn() == null ? BigDecimal.ZERO : d.quantityIn();
            BigDecimal ra = d.quantityOut() == null ? BigDecimal.ZERO : d.quantityOut();
            assertThat(d.balanceAfter())
                    .as("Dòng %s phải bằng dòng trước cộng nhập trừ xuất", d.refCode())
                    .isEqualByComparingTo(truoc.add(vao).subtract(ra));
            truoc = d.balanceAfter();
        }
    }

    @Test
    @DisplayName("Lọc theo khoảng ngày: tồn cuối kỳ = tồn đầu kỳ + nhập - xuất trong kỳ")
    void locTheoNgay_tonDauVaTonCuoiKhop() {
        nhap(new BigDecimal("100"), new BigDecimal("50000"), "PN-TEST-001", ngay(5));
        xuat(new BigDecimal("20"), "PX-TEST-001", ngay(12));
        nhap(new BigDecimal("40"), new BigDecimal("50000"), "PN-TEST-002", ngay(18));

        // Chỉ lấy kỳ 10/01 - 15/01: trong kỳ chỉ có một dòng xuất 20
        StockCardResponse theKho = inventoryQueryService.getStockCard(
                product.getId(), warehouse.getId(), ngayThang(10), ngayThang(15));

        assertThat(theKho.entries()).hasSize(1);
        assertThat(theKho.openingQuantity()).isEqualByComparingTo("100");
        assertThat(theKho.totalIn()).isEqualByComparingTo("0");
        assertThat(theKho.totalOut()).isEqualByComparingTo("20");
        assertThat(theKho.closingQuantity()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("Hủy phiếu cũ: giao dịch đảo ngược mang ngày hôm nay vẫn cho tồn cuối đúng")
    void huyPhieuCu_daoNguocMangNgayHomNay() {
        // Tình huống thật đã gặp: phiếu nhập cũ bị hủy, giao dịch REVERSAL mang ngày hôm nay
        // nên sắp cuối cùng theo ngày, trong khi các phiếu xuất cũ hơn lại được ghi sau nó.
        nhap(new BigDecimal("185"), new BigDecimal("50000"), "PN-TEST-001", ngay(1));
        nhap(new BigDecimal("10"), new BigDecimal("50000"), "PN-TEST-002", ngay(10));
        // Hủy phiếu PN-TEST-002: ghi đảo ngược, mang ngày HÔM NAY
        xuat(new BigDecimal("10"), "PN-TEST-002", LocalDateTime.now());
        // Phiếu xuất cũ nhưng được ghi SAU cùng
        xuat(new BigDecimal("166"), "PX-TEST-001", ngay(5));

        BigDecimal tonThat = tonKhoThat();
        assertThat(tonThat).isEqualByComparingTo("19");

        StockCardResponse theKho = inventoryQueryService.getStockCard(
                product.getId(), warehouse.getId(), null, null);

        assertThat(theKho.closingQuantity()).isEqualByComparingTo(tonThat);
        assertThat(theKho.entries().get(theKho.entries().size() - 1).balanceAfter())
                .as("Dòng cuối cùng trên thẻ kho phải bằng tồn cuối kỳ")
                .isEqualByComparingTo(tonThat);
    }

    // ======================= Tiện ích =======================

    /** Ngày thứ {@code ngayTrongThang} của tháng trước, để chắc chắn nằm trong quá khứ. */
    private LocalDateTime ngay(int ngayTrongThang) {
        return ngayThang(ngayTrongThang).atTime(9, 0);
    }

    private LocalDate ngayThang(int ngayTrongThang) {
        return LocalDate.now().minusMonths(1).withDayOfMonth(ngayTrongThang);
    }

    private void nhap(BigDecimal soLuong, BigDecimal donGia, String maPhieu, LocalDateTime thoiDiem) {
        transactionTemplate.execute(status -> inventoryService.applyReceipt(
                List.of(new StockMovement(
                        product, warehouse, soLuong, donGia, RefType.RECEIPT, 1L, maPhieu, null, thoiDiem))));
    }

    private void xuat(BigDecimal soLuong, String maPhieu, LocalDateTime thoiDiem) {
        transactionTemplate.execute(status -> inventoryService.applyIssue(
                List.of(new StockMovement(
                        product,
                        warehouse,
                        soLuong,
                        BigDecimal.ZERO,
                        maPhieu.startsWith("PN") ? RefType.RECEIPT : RefType.ISSUE,
                        1L,
                        maPhieu,
                        null,
                        thoiDiem))));
    }

    private BigDecimal tonKhoThat() {
        return inventoryRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseThrow(() -> new AssertionError("Chưa có dòng tồn kho nào được tạo"))
                .getQuantity();
    }
}
