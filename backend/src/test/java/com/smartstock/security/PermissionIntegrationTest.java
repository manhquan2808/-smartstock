package com.smartstock.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartstock.catalog.Product;
import com.smartstock.catalog.ProductService;
import com.smartstock.common.ForbiddenException;
import com.smartstock.document.DocumentDtos.SaveReceiptItemRequest;
import com.smartstock.document.DocumentDtos.SaveReceiptRequest;
import com.smartstock.inventory.InventoryQueryService;
import com.smartstock.receipt.ReceiptType;
import com.smartstock.receipt.StockReceiptService;
import com.smartstock.report.DashboardService;
import com.smartstock.report.ReportService;
import com.smartstock.support.AuthTestSupport;
import com.smartstock.support.TestDataFactory;
import com.smartstock.support.TestProfileResolver;
import com.smartstock.user.Role;
import com.smartstock.user.User;
import com.smartstock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test PHÂN QUYỀN - yêu cầu bắt buộc ở mục 8 của đặc tả.
 *
 * <p>Điểm quan trọng nhất mà bộ test này chứng minh: việc che giá vốn được thực hiện ở
 * BACKEND, trong tầng service khi dựng DTO - KHÔNG phải chỉ ẩn cột trên giao diện. Vì vậy
 * nhân viên gọi API trực tiếp (ví dụ qua Swagger hoặc curl) cũng không lấy được giá vốn.
 *
 * <p>Ba nhóm kiểm tra:
 *
 * <ol>
 *   <li>STAFF không duyệt được phiếu.</li>
 *   <li>STAFF không thấy giá vốn, giá trị tồn ở mọi nơi (tồn kho, thẻ kho, dashboard, báo cáo).</li>
 *   <li>Không ai truy cập được dữ liệu của kho mình không được gán.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver.class)
@Import(TestDataFactory.class)
@DisplayName("Phân quyền theo vai trò và theo kho")
class PermissionIntegrationTest {

    @Autowired
    private StockReceiptService receiptService;

    @Autowired
    private InventoryQueryService inventoryQueryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private TestDataFactory dataFactory;

    private Warehouse hanoi;
    private Warehouse danang;
    private Product product;
    private User admin;
    private User manager;
    private User staff;

    @BeforeEach
    void setUp() {
        hanoi = dataFactory.createWarehouse("Kho Hà Nội");
        danang = dataFactory.createWarehouse("Kho Đà Nẵng");
        product = dataFactory.createProduct("Bia Saigon Lager (thùng 24 lon)");

        // ADMIN không gán kho: mặc định thấy mọi kho
        admin = dataFactory.createUser(Role.ADMIN, "Test@1234");
        manager = dataFactory.createUser(Role.MANAGER, "Test@1234", hanoi);
        staff = dataFactory.createUser(Role.STAFF, "Test@1234", hanoi);

        // Tạo dữ liệu tồn kho để có giá vốn mà kiểm tra
        AuthTestSupport.loginAs(manager);
        var receipt = receiptService.create(
                new SaveReceiptRequest(
                        hanoi.getId(),
                        ReceiptType.PURCHASE,
                        LocalDate.now(),
                        dataFactory.createSupplier().getId(),
                        null,
                        null,
                        null,
                        List.of(new SaveReceiptItemRequest(
                                product.getId(), new BigDecimal("100"), new BigDecimal("265000"), null))),
                true);
        receiptService.approve(receipt.id());
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.clear();
    }

    // ==================================================================
    // 1. STAFF không được duyệt phiếu
    // ==================================================================

    @Test
    @DisplayName("STAFF không duyệt được phiếu nhập")
    void staff_khongDuyetDuocPhieu() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(
                new SaveReceiptRequest(
                        hanoi.getId(),
                        ReceiptType.PURCHASE,
                        LocalDate.now(),
                        dataFactory.createSupplier().getId(),
                        null,
                        null,
                        null,
                        List.of(new SaveReceiptItemRequest(
                                product.getId(), BigDecimal.TEN, new BigDecimal("265000"), null))),
                true);

        assertThatThrownBy(() -> receiptService.approve(created.id()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("không có quyền duyệt phiếu");
    }

    @Test
    @DisplayName("STAFF không từ chối được phiếu")
    void staff_khongTuChoiDuocPhieu() {
        AuthTestSupport.loginAs(manager);
        var created = receiptService.create(
                new SaveReceiptRequest(
                        hanoi.getId(),
                        ReceiptType.PURCHASE,
                        LocalDate.now(),
                        dataFactory.createSupplier().getId(),
                        null,
                        null,
                        null,
                        List.of(new SaveReceiptItemRequest(
                                product.getId(), BigDecimal.TEN, new BigDecimal("265000"), null))),
                true);

        AuthTestSupport.loginAs(staff);
        assertThatThrownBy(() -> receiptService.reject(created.id(), "Thử từ chối"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("MANAGER duyệt được phiếu trong kho được gán")
    void manager_duyetDuocPhieuKhoDuocGan() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(
                new SaveReceiptRequest(
                        hanoi.getId(),
                        ReceiptType.PURCHASE,
                        LocalDate.now(),
                        dataFactory.createSupplier().getId(),
                        null,
                        null,
                        null,
                        List.of(new SaveReceiptItemRequest(
                                product.getId(), BigDecimal.TEN, new BigDecimal("265000"), null))),
                true);

        AuthTestSupport.loginAs(manager);
        var approved = receiptService.approve(created.id());
        assertThat(approved.status()).isEqualTo(com.smartstock.document.DocumentStatus.COMPLETED);
    }

    // ==================================================================
    // 2. STAFF không thấy giá vốn / giá trị tồn
    // ==================================================================

    @Test
    @DisplayName("Tồn kho: STAFF nhận avgCost và stockValue bằng null, MANAGER nhận giá trị thật")
    void tonKho_staffKhongThayGiaVon() {
        // STAFF
        AuthTestSupport.loginAs(staff);
        var staffView = inventoryQueryService.search(hanoi.getId(), null, null, "ALL", PageRequest.of(0, 10));

        assertThat(staffView.content()).isNotEmpty();
        var staffRow = staffView.content().get(0);
        assertThat(staffRow.quantity()).isEqualByComparingTo("100"); // số lượng vẫn thấy
        assertThat(staffRow.avgCost()).as("STAFF không được thấy giá vốn").isNull();
        assertThat(staffRow.stockValue()).as("STAFF không được thấy giá trị tồn").isNull();

        // MANAGER
        AuthTestSupport.loginAs(manager);
        var managerView = inventoryQueryService.search(hanoi.getId(), null, null, "ALL", PageRequest.of(0, 10));
        var managerRow = managerView.content().get(0);
        assertThat(managerRow.avgCost()).isEqualByComparingTo("265000");
        assertThat(managerRow.stockValue()).isEqualByComparingTo("26500000");
    }

    @Test
    @DisplayName("Thẻ kho: STAFF nhận unitCost bằng null")
    void theKho_staffKhongThayGiaVon() {
        AuthTestSupport.loginAs(staff);
        var staffCard = inventoryQueryService.getStockCard(product.getId(), hanoi.getId(), null, null);

        assertThat(staffCard.entries()).isNotEmpty();
        assertThat(staffCard.entries().get(0).unitCost()).isNull();
        // Số lượng và tồn lũy kế vẫn xem được
        assertThat(staffCard.closingQuantity()).isEqualByComparingTo("100");

        AuthTestSupport.loginAs(manager);
        var managerCard = inventoryQueryService.getStockCard(product.getId(), hanoi.getId(), null, null);
        assertThat(managerCard.entries().get(0).unitCost()).isEqualByComparingTo("265000");
    }

    @Test
    @DisplayName("Chi tiết sản phẩm: STAFF không thấy giá vốn của từng kho")
    void chiTietSanPham_staffKhongThayGiaVon() {
        AuthTestSupport.loginAs(staff);
        var staffDetail = productService.getDetail(product.getId());

        assertThat(staffDetail.stocks()).isNotEmpty();
        assertThat(staffDetail.stocks().get(0).avgCost()).isNull();
        assertThat(staffDetail.stocks().get(0).stockValue()).isNull();

        AuthTestSupport.loginAs(manager);
        var managerDetail = productService.getDetail(product.getId());
        assertThat(managerDetail.stocks().get(0).avgCost()).isEqualByComparingTo("265000");
    }

    @Test
    @DisplayName("Dashboard: STAFF nhận totalStockValue bằng null (dashboard rút gọn)")
    void dashboard_staffKhongThayTongGiaTri() {
        AuthTestSupport.loginAs(staff);
        var staffDashboard = dashboardService.build(hanoi.getId());

        assertThat(staffDashboard.totalStockValue()).as("STAFF chỉ thấy dashboard rút gọn").isNull();
        // Các số liệu không liên quan tiền vẫn thấy
        assertThat(staffDashboard.totalProducts()).isPositive();

        AuthTestSupport.loginAs(manager);
        var managerDashboard = dashboardService.build(hanoi.getId());
        assertThat(managerDashboard.totalStockValue()).isNotNull();
        assertThat(managerDashboard.totalStockValue()).isEqualByComparingTo("26500000");
    }

    @Test
    @DisplayName("Báo cáo nhập-xuất-tồn: STAFF không thấy giá trị tồn")
    void baoCao_staffKhongThayGiaTri() {
        AuthTestSupport.loginAs(staff);
        var staffReport = reportService.buildInventoryReport(
                LocalDate.now().minusDays(7), LocalDate.now(), hanoi.getId(), null);

        assertThat(staffReport.totalClosingValue()).isNull();
        assertThat(staffReport.rows()).isNotEmpty();
        assertThat(staffReport.rows().get(0).closingValue()).isNull();
        assertThat(staffReport.rows().get(0).avgCost()).isNull();
        // Số lượng nhập/xuất/tồn vẫn xem được
        assertThat(staffReport.rows().get(0).receivedQuantity()).isEqualByComparingTo("100");

        AuthTestSupport.loginAs(manager);
        var managerReport = reportService.buildInventoryReport(
                LocalDate.now().minusDays(7), LocalDate.now(), hanoi.getId(), null);
        assertThat(managerReport.totalClosingValue()).isNotNull();
        assertThat(managerReport.rows().get(0).closingValue()).isNotNull();
    }

    @Test
    @DisplayName("Phiếu do chính STAFF lập thì họ VẪN thấy đơn giá (theo đúng đặc tả)")
    void phieuDoStaffLap_staffThayDonGia() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(
                new SaveReceiptRequest(
                        hanoi.getId(),
                        ReceiptType.PURCHASE,
                        LocalDate.now(),
                        dataFactory.createSupplier().getId(),
                        null,
                        null,
                        null,
                        List.of(new SaveReceiptItemRequest(
                                product.getId(), BigDecimal.TEN, new BigDecimal("265000"), null))),
                false);

        var detail = receiptService.getById(created.id());
        assertThat(detail.totalAmount()).as("STAFF thấy tổng tiền phiếu do chính mình lập").isNotNull();
        assertThat(detail.items().get(0).unitPrice()).isEqualByComparingTo("265000");
    }

    @Test
    @DisplayName("Phiếu do người khác lập thì STAFF không thấy đơn giá")
    void phieuNguoiKhacLap_staffKhongThayDonGia() {
        // MANAGER lập phiếu
        AuthTestSupport.loginAs(manager);
        var created = receiptService.create(
                new SaveReceiptRequest(
                        hanoi.getId(),
                        ReceiptType.PURCHASE,
                        LocalDate.now(),
                        dataFactory.createSupplier().getId(),
                        null,
                        null,
                        null,
                        List.of(new SaveReceiptItemRequest(
                                product.getId(), BigDecimal.TEN, new BigDecimal("265000"), null))),
                false);

        // STAFF xem phiếu đó
        AuthTestSupport.loginAs(staff);
        var detail = receiptService.getById(created.id());

        assertThat(detail.totalAmount()).as("STAFF không thấy tổng tiền phiếu của người khác").isNull();
        assertThat(detail.items().get(0).unitPrice()).isNull();
        // Nhưng vẫn thấy sản phẩm và số lượng để làm việc
        assertThat(detail.items().get(0).quantity()).isEqualByComparingTo("10");
    }

    // ==================================================================
    // 3. Phân quyền theo kho
    // ==================================================================

    @Test
    @DisplayName("STAFF không truy cập được tồn kho của kho không được gán")
    void staff_khongTruyCapKhoKhac() {
        AuthTestSupport.loginAs(staff);

        assertThatThrownBy(() ->
                        inventoryQueryService.search(danang.getId(), null, null, "ALL", PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("không được phân công làm việc tại kho này");
    }

    @Test
    @DisplayName("STAFF không xem được thẻ kho của kho không được gán")
    void staff_khongXemTheKhoKhoKhac() {
        AuthTestSupport.loginAs(staff);

        assertThatThrownBy(() -> inventoryQueryService.getStockCard(product.getId(), danang.getId(), null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("STAFF không lập được phiếu ở kho không được gán")
    void staff_khongLapPhieuKhoKhac() {
        AuthTestSupport.loginAs(staff);
        var request = new SaveReceiptRequest(
                danang.getId(), // kho không được gán
                ReceiptType.PURCHASE,
                LocalDate.now(),
                dataFactory.createSupplier().getId(),
                null,
                null,
                null,
                List.of(new SaveReceiptItemRequest(product.getId(), BigDecimal.TEN, new BigDecimal("265000"), null)));

        assertThatThrownBy(() -> receiptService.create(request, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("MANAGER không truy cập được kho không được gán")
    void manager_khongTruyCapKhoKhac() {
        AuthTestSupport.loginAs(manager);

        assertThatThrownBy(() ->
                        inventoryQueryService.search(danang.getId(), null, null, "ALL", PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("ADMIN truy cập được mọi kho mà không cần gán kho")
    void admin_truyCapMoiKho() {
        AuthTestSupport.loginAs(admin);

        // Kho Hà Nội
        var hanoiView = inventoryQueryService.search(hanoi.getId(), null, null, "ALL", PageRequest.of(0, 10));
        assertThat(hanoiView.content()).isNotEmpty();
        assertThat(hanoiView.content().get(0).avgCost()).isNotNull();

        // Kho Đà Nẵng - không có dữ liệu nhưng vẫn truy cập được, không bị chặn
        var danangView = inventoryQueryService.search(danang.getId(), null, null, "ALL", PageRequest.of(0, 10));
        assertThat(danangView.content()).isEmpty();

        // Không chỉ định kho: xem gộp tất cả
        var allView = inventoryQueryService.search(null, null, null, "ALL", PageRequest.of(0, 10));
        assertThat(allView.content()).isNotEmpty();
    }

    @Test
    @DisplayName("Không chỉ định kho: MANAGER chỉ thấy dữ liệu của kho được gán")
    void manager_khongChiDinhKho_chiThayKhoDuocGan() {
        // Tạo tồn ở kho Đà Nẵng bằng tài khoản ADMIN
        AuthTestSupport.loginAs(admin);
        var danangReceipt = receiptService.create(
                new SaveReceiptRequest(
                        danang.getId(),
                        ReceiptType.PURCHASE,
                        LocalDate.now(),
                        dataFactory.createSupplier().getId(),
                        null,
                        null,
                        null,
                        List.of(new SaveReceiptItemRequest(
                                product.getId(), new BigDecimal("50"), new BigDecimal("270000"), null))),
                true);
        receiptService.approve(danangReceipt.id());

        // MANAGER chỉ được gán kho Hà Nội -> không thấy dòng tồn của Đà Nẵng
        AuthTestSupport.loginAs(manager);
        var view = inventoryQueryService.search(null, null, null, "ALL", PageRequest.of(0, 50));

        assertThat(view.content()).isNotEmpty();
        assertThat(view.content())
                .as("Mọi dòng trả về phải thuộc kho được gán")
                .allMatch(row -> row.warehouseId().equals(hanoi.getId()));
    }

    @Test
    @DisplayName("Tài khoản chưa được gán kho nào thì bị báo lỗi rõ ràng, không trả danh sách rỗng")
    void chuaGanKho_baoLoiRoRang() {
        // Tạo tài khoản STAFF không gán kho nào (trường hợp admin quên gán)
        User orphanStaff = dataFactory.createUser(Role.STAFF, "Test@1234");
        AuthTestSupport.loginAs(orphanStaff);

        assertThatThrownBy(() -> inventoryQueryService.search(null, null, null, "ALL", PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("chưa được gán kho nào");
    }
}
