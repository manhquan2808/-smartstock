package com.smartstock.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartstock.catalog.Customer;
import com.smartstock.catalog.Product;
import com.smartstock.catalog.Supplier;
import com.smartstock.common.BusinessException;
import com.smartstock.common.ForbiddenException;
import com.smartstock.common.InsufficientStockException;
import com.smartstock.document.DocumentDtos.SaveIssueItemRequest;
import com.smartstock.document.DocumentDtos.SaveIssueRequest;
import com.smartstock.document.DocumentDtos.SaveReceiptItemRequest;
import com.smartstock.document.DocumentDtos.SaveReceiptRequest;
import com.smartstock.inventory.InventoryService;
import com.smartstock.inventory.RefType;
import com.smartstock.inventory.StockTransactionRepository;
import com.smartstock.inventory.TransactionType;
import com.smartstock.issue.StockIssueService;
import com.smartstock.receipt.StockReceiptService;
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
import org.springframework.test.context.ActiveProfiles;

/**
 * Test tích hợp LUỒNG DUYỆT PHIẾU, đi qua tầng service như khi người dùng thao tác thật.
 *
 * <p>Kiểm chứng các quy tắc ở mục 4.3 của đặc tả:
 *
 * <ul>
 *   <li>Chỉ phiếu DRAFT/REJECTED mới sửa được; PENDING không ai sửa.</li>
 *   <li>Phiếu COMPLETED không sửa, không xóa - chỉ hủy kèm lý do.</li>
 *   <li>Hủy phiếu sinh giao dịch đảo ngược; hủy phiếu nhập bị chặn khi tồn không đủ.</li>
 *   <li>Ràng buộc đối tác/lý do theo từng loại phiếu.</li>
 *   <li>Tồn kho chỉ thay đổi khi phiếu chuyển sang COMPLETED.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver.class)
@Import(TestDataFactory.class)
@DisplayName("Luồng duyệt phiếu nhập và phiếu xuất")
class DocumentWorkflowIntegrationTest {

    @Autowired
    private StockReceiptService receiptService;

    @Autowired
    private StockIssueService issueService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private TestDataFactory dataFactory;

    private Warehouse warehouse;
    private Product product;
    private Supplier supplier;
    private Customer customer;
    private User staff;
    private User manager;

    @BeforeEach
    void setUp() {
        warehouse = dataFactory.createWarehouse("Kho nghiệp vụ");
        product = dataFactory.createProduct("Dầu ăn Simply 1L");
        supplier = dataFactory.createSupplier();
        customer = dataFactory.createCustomer();
        staff = dataFactory.createUser(Role.STAFF, "Test@1234", warehouse);
        manager = dataFactory.createUser(Role.MANAGER, "Test@1234", warehouse);
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.clear();
    }

    // ==================================================================
    // Phiếu nhập
    // ==================================================================

    @Test
    @DisplayName("Lập phiếu nháp KHÔNG làm thay đổi tồn kho")
    void luuNhap_khongDoiTonKho() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("100")), false);

        assertThat(created.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(created.code()).startsWith("PN-");
        // Điểm then chốt: phiếu nháp chưa tác động tồn kho
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Duyệt phiếu nhập mới làm tăng tồn kho và ghi sổ giao dịch")
    void duyetPhieuNhap_tangTon() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("100")), true);
        assertThat(created.status()).isEqualTo(DocumentStatus.PENDING);

        AuthTestSupport.loginAs(manager);
        var approved = receiptService.approve(created.id());

        assertThat(approved.status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(approved.approvedByName()).isEqualTo(manager.getFullName());
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("100");

        var transactions =
                stockTransactionRepository.findByRefTypeAndRefIdOrderByIdAsc(RefType.RECEIPT, created.id());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.RECEIPT);
    }

    @Test
    @DisplayName("Phiếu đang CHỜ DUYỆT thì không ai sửa được, kể cả người lập")
    void phieuChoDuyet_khongSuaDuoc() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("50")), true);

        assertThatThrownBy(() -> receiptService.update(created.id(), buildReceiptRequest(new BigDecimal("70"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không sửa được");
    }

    @Test
    @DisplayName("Phiếu đã HOÀN TẤT thì không sửa và không xóa được")
    void phieuHoanTat_khongSuaKhongXoa() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("50")), true);

        AuthTestSupport.loginAs(manager);
        receiptService.approve(created.id());

        assertThatThrownBy(() -> receiptService.update(created.id(), buildReceiptRequest(new BigDecimal("70"))))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> receiptService.delete(created.id()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Từ chối phiếu: lưu lý do và cho người lập sửa rồi gửi lại")
    void tuChoiPhieu_suaRoiGuiLai() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("50")), true);

        AuthTestSupport.loginAs(manager);
        var rejected = receiptService.reject(created.id(), "Đơn giá cao hơn hợp đồng");

        assertThat(rejected.status()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo("Đơn giá cao hơn hợp đồng");

        // Người lập sửa phiếu bị từ chối: phiếu quay về trạng thái nháp
        AuthTestSupport.loginAs(staff);
        var updated = receiptService.update(created.id(), buildReceiptRequest(new BigDecimal("40")));
        assertThat(updated.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(updated.rejectReason()).isNull();

        // Gửi lại và được duyệt
        receiptService.submit(created.id());
        AuthTestSupport.loginAs(manager);
        var approved = receiptService.approve(created.id());
        assertThat(approved.status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("Hủy phiếu nhập đã hoàn tất: sinh giao dịch đảo ngược và trừ tồn lại")
    void huyPhieuNhap_daoNguoc() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("100")), true);

        AuthTestSupport.loginAs(manager);
        receiptService.approve(created.id());
        var cancelled = receiptService.cancel(created.id(), "Nhập sai nhà cung cấp");

        assertThat(cancelled.status()).isEqualTo(DocumentStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Nhập sai nhà cung cấp");
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("0");

        // Giao dịch gốc KHÔNG bị xóa, chỉ có thêm giao dịch đảo ngược
        var transactions =
                stockTransactionRepository.findByRefTypeAndRefIdOrderByIdAsc(RefType.RECEIPT, created.id());
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.RECEIPT);
        assertThat(transactions.get(1).getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(transactions.get(1).getNote()).contains("Nhập sai nhà cung cấp");
    }

    @Test
    @DisplayName("Chặn hủy phiếu nhập khi hàng đã xuất đi, tồn không còn đủ để trừ lại")
    void chanHuyPhieuNhap_khiKhongDuTon() {
        // Nhập 100 rồi xuất 80 -> còn 20, không thể đảo ngược phiếu nhập 100
        AuthTestSupport.loginAs(staff);
        var receipt = receiptService.create(buildReceiptRequest(new BigDecimal("100")), true);
        AuthTestSupport.loginAs(manager);
        receiptService.approve(receipt.id());

        AuthTestSupport.loginAs(staff);
        var issue = issueService.create(buildIssueRequest(new BigDecimal("80")), true);
        AuthTestSupport.loginAs(manager);
        issueService.approve(issue.id());

        assertThatThrownBy(() -> receiptService.cancel(receipt.id(), "Thử hủy"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("chỉ còn 20");

        // Tồn kho không thay đổi vì giao dịch đã rollback
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Phiếu nhập mua bắt buộc chọn nhà cung cấp")
    void phieuNhapMua_batBuocNhaCungCap() {
        AuthTestSupport.loginAs(staff);
        var request = new SaveReceiptRequest(
                warehouse.getId(),
                com.smartstock.receipt.ReceiptType.PURCHASE,
                LocalDate.now(),
                null, // thiếu nhà cung cấp
                null,
                null,
                null,
                List.of(new SaveReceiptItemRequest(product.getId(), BigDecimal.TEN, new BigDecimal("1000"), null)));

        assertThatThrownBy(() -> receiptService.create(request, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bắt buộc chọn nhà cung cấp");
    }

    @Test
    @DisplayName("Phiếu nhập loại KHÁC bắt buộc ghi lý do")
    void phieuNhapKhac_batBuocLyDo() {
        AuthTestSupport.loginAs(staff);
        var request = new SaveReceiptRequest(
                warehouse.getId(),
                com.smartstock.receipt.ReceiptType.OTHER,
                LocalDate.now(),
                null,
                null,
                null, // thiếu lý do
                null,
                List.of(new SaveReceiptItemRequest(product.getId(), BigDecimal.TEN, new BigDecimal("1000"), null)));

        assertThatThrownBy(() -> receiptService.create(request, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bắt buộc ghi lý do");
    }

    @Test
    @DisplayName("Chặn ngày chứng từ ở tương lai")
    void chanNgayChungTuTuongLai() {
        AuthTestSupport.loginAs(staff);
        var request = new SaveReceiptRequest(
                warehouse.getId(),
                com.smartstock.receipt.ReceiptType.PURCHASE,
                LocalDate.now().plusDays(1),
                supplier.getId(),
                null,
                null,
                null,
                List.of(new SaveReceiptItemRequest(product.getId(), BigDecimal.TEN, new BigDecimal("1000"), null)));

        assertThatThrownBy(() -> receiptService.create(request, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tương lai");
    }

    @Test
    @DisplayName("Mã phiếu được sinh đúng định dạng PN-yyyyMMdd-xxx và tăng dần")
    void maPhieu_dungDinhDangVaTangDan() {
        AuthTestSupport.loginAs(staff);
        var first = receiptService.create(buildReceiptRequest(new BigDecimal("10")), false);
        var second = receiptService.create(buildReceiptRequest(new BigDecimal("10")), false);

        String datePart = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(first.code()).matches("PN-" + datePart + "-\\d{3,}");
        assertThat(second.code()).matches("PN-" + datePart + "-\\d{3,}");
        // Mã sau phải khác và lớn hơn mã trước
        assertThat(second.code()).isNotEqualTo(first.code());
        assertThat(second.code().compareTo(first.code())).isPositive();
    }

    // ==================================================================
    // Phiếu xuất
    // ==================================================================

    @Test
    @DisplayName("Lưu nháp phiếu xuất vẫn thành công khi thiếu tồn (chỉ cảnh báo)")
    void luuNhapPhieuXuat_thieuTonVanLuuDuoc() {
        AuthTestSupport.loginAs(staff);
        // Chưa có tồn nào mà vẫn lưu được phiếu nháp
        var created = issueService.create(buildIssueRequest(new BigDecimal("10")), false);

        assertThat(created.status()).isEqualTo(DocumentStatus.DRAFT);
    }

    @Test
    @DisplayName("Gửi duyệt phiếu xuất bị CHẶN khi thiếu tồn, nêu rõ sản phẩm thiếu")
    void guiDuyetPhieuXuat_chanKhiThieuTon() {
        AuthTestSupport.loginAs(staff);
        var created = issueService.create(buildIssueRequest(new BigDecimal("10")), false);

        assertThatThrownBy(() -> issueService.submit(created.id()))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining(product.getSku())
                .hasMessageContaining("chỉ còn 0");
    }

    @Test
    @DisplayName("Duyệt phiếu xuất: trừ tồn và ghi giá vốn tại thời điểm xuất vào dòng phiếu")
    void duyetPhieuXuat_truTonVaGhiGiaVon() {
        // Nhập 100 với giá 50.000 để có giá vốn
        AuthTestSupport.loginAs(staff);
        var receipt = receiptService.create(buildReceiptRequest(new BigDecimal("100")), true);
        AuthTestSupport.loginAs(manager);
        receiptService.approve(receipt.id());

        AuthTestSupport.loginAs(staff);
        var issue = issueService.create(buildIssueRequest(new BigDecimal("30")), true);
        AuthTestSupport.loginAs(manager);
        var approved = issueService.approve(issue.id());

        assertThat(approved.status()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("70");

        // Giá vốn được ghi vào dòng phiếu và tổng giá trị xuất được tính
        assertThat(approved.items()).hasSize(1);
        assertThat(approved.items().get(0).unitPrice()).isEqualByComparingTo("50000");
        assertThat(approved.totalAmount()).isEqualByComparingTo("1500000"); // 30 x 50.000
    }

    @Test
    @DisplayName("Hủy phiếu xuất đã hoàn tất: cộng tồn trở lại")
    void huyPhieuXuat_congTonLai() {
        AuthTestSupport.loginAs(staff);
        var receipt = receiptService.create(buildReceiptRequest(new BigDecimal("100")), true);
        AuthTestSupport.loginAs(manager);
        receiptService.approve(receipt.id());

        AuthTestSupport.loginAs(staff);
        var issue = issueService.create(buildIssueRequest(new BigDecimal("30")), true);
        AuthTestSupport.loginAs(manager);
        issueService.approve(issue.id());
        var cancelled = issueService.cancel(issue.id(), "Khách đổi ý");

        assertThat(cancelled.status()).isEqualTo(DocumentStatus.CANCELLED);
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Phiếu xuất bán bắt buộc chọn khách hàng")
    void phieuXuatBan_batBuocKhachHang() {
        AuthTestSupport.loginAs(staff);
        var request = new SaveIssueRequest(
                warehouse.getId(),
                com.smartstock.issue.IssueType.SALE,
                LocalDate.now(),
                null, // thiếu khách hàng
                null,
                null,
                null,
                List.of(new SaveIssueItemRequest(product.getId(), BigDecimal.TEN, null)));

        assertThatThrownBy(() -> issueService.create(request, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bắt buộc chọn khách hàng");
    }

    @Test
    @DisplayName("Phiếu xuất hủy bắt buộc ghi lý do")
    void phieuXuatHuy_batBuocLyDo() {
        AuthTestSupport.loginAs(staff);
        var request = new SaveIssueRequest(
                warehouse.getId(),
                com.smartstock.issue.IssueType.DISPOSAL,
                LocalDate.now(),
                null,
                null,
                null, // thiếu lý do
                null,
                List.of(new SaveIssueItemRequest(product.getId(), BigDecimal.TEN, null)));

        assertThatThrownBy(() -> issueService.create(request, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bắt buộc ghi lý do");
    }

    @Test
    @DisplayName("Kiểm tra tồn trước khi lưu: báo đúng dòng thiếu mà không thay đổi dữ liệu")
    void kiemTraTonTruocKhiLuu() {
        AuthTestSupport.loginAs(staff);
        var receipt = receiptService.create(buildReceiptRequest(new BigDecimal("8")), true);
        AuthTestSupport.loginAs(manager);
        receiptService.approve(receipt.id());

        AuthTestSupport.loginAs(staff);
        var result = issueService.checkStock(
                warehouse.getId(), List.of(new SaveIssueItemRequest(product.getId(), new BigDecimal("10"), null)));

        assertThat(result.sufficient()).isFalse();
        assertThat(result.shortages()).hasSize(1);
        assertThat(result.shortages().get(0).available()).isEqualByComparingTo("8");
        assertThat(result.shortages().get(0).requested()).isEqualByComparingTo("10");

        // Chỉ kiểm tra, không trừ tồn
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("8");
    }

    // ==================================================================
    // Phân quyền trong luồng duyệt
    // ==================================================================

    @Test
    @DisplayName("STAFF không duyệt được phiếu dù là phiếu trong kho của mình")
    void staffKhongDuyetDuocPhieu() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("50")), true);

        // Vẫn đang đăng nhập bằng tài khoản STAFF
        assertThatThrownBy(() -> receiptService.approve(created.id()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("không có quyền duyệt phiếu");
    }

    @Test
    @DisplayName("Người dùng không sửa được phiếu nháp do người khác lập")
    void khongSuaDuocPhieuCuaNguoiKhac() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("50")), false);

        // Một nhân viên khác cùng kho
        User otherStaff = dataFactory.createUser(Role.STAFF, "Test@1234", warehouse);
        AuthTestSupport.loginAs(otherStaff);

        assertThatThrownBy(() -> receiptService.update(created.id(), buildReceiptRequest(new BigDecimal("60"))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("chỉ sửa được phiếu do chính mình lập");
    }

    @Test
    @DisplayName("Không thao tác được trên phiếu thuộc kho mình không được gán")
    void khongThaoTacDuocPhieuKhoKhac() {
        AuthTestSupport.loginAs(staff);
        var created = receiptService.create(buildReceiptRequest(new BigDecimal("50")), true);

        // Quản lý của một kho khác
        Warehouse otherWarehouse = dataFactory.createWarehouse("Kho khác");
        User otherManager = dataFactory.createUser(Role.MANAGER, "Test@1234", otherWarehouse);
        AuthTestSupport.loginAs(otherManager);

        assertThatThrownBy(() -> receiptService.approve(created.id()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("không được phân công làm việc tại kho này");
    }

    // ==================================================================
    // Hàm hỗ trợ
    // ==================================================================

    private SaveReceiptRequest buildReceiptRequest(BigDecimal quantity) {
        return new SaveReceiptRequest(
                warehouse.getId(),
                com.smartstock.receipt.ReceiptType.PURCHASE,
                LocalDate.now(),
                supplier.getId(),
                null,
                null,
                "Phiếu nhập dùng cho kiểm thử",
                List.of(new SaveReceiptItemRequest(product.getId(), quantity, new BigDecimal("50000"), null)));
    }

    private SaveIssueRequest buildIssueRequest(BigDecimal quantity) {
        return new SaveIssueRequest(
                warehouse.getId(),
                com.smartstock.issue.IssueType.SALE,
                LocalDate.now(),
                customer.getId(),
                null,
                null,
                "Phiếu xuất dùng cho kiểm thử",
                List.of(new SaveIssueItemRequest(product.getId(), quantity, null)));
    }
}
