package com.smartstock.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartstock.catalog.Product;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * TEST ĐỒNG THỜI: hai phiếu xuất cùng hoàn tất trên cùng một sản phẩm.
 *
 * <p>Đây là yêu cầu bắt buộc ở mục 8 của đặc tả: "2 phiếu xuất cùng hoàn tất trên cùng
 * sản phẩm mà tổng vượt tồn thì chỉ 1 phiếu thành công".
 *
 * <p><b>Vì sao test này quan trọng:</b> nếu không có khóa dòng, cả hai luồng cùng đọc
 * được số tồn 100, cùng thấy "đủ hàng cho 60", rồi cùng trừ - kết quả tồn bị âm hoặc
 * một lần trừ bị ghi đè. Lỗi này rất khó phát hiện bằng thao tác tay vì chỉ xuất hiện
 * khi hai người bấm duyệt gần như cùng lúc.
 *
 * <p><b>Cơ chế bảo vệ:</b> {@code InventoryRepository.findForUpdate} sinh câu lệnh
 * {@code SELECT ... FOR UPDATE}, buộc luồng thứ hai chờ tới khi luồng đầu commit. Khi
 * được chạy tiếp, luồng thứ hai đọc số tồn ĐÃ CẬP NHẬT nên phát hiện thiếu hàng và bị
 * chặn.
 *
 * <p>{@code CountDownLatch} được dùng để hai luồng khởi động gần như cùng một thời điểm,
 * tăng khả năng tái hiện tình huống tranh chấp.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver.class)
@Import(TestDataFactory.class)
@DisplayName("Xuất kho đồng thời")
class ConcurrentIssueIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private TestDataFactory dataFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Warehouse warehouse;
    private Product product;
    private User manager;

    @BeforeEach
    void setUp() {
        warehouse = dataFactory.createWarehouse("Kho đồng thời");
        product = dataFactory.createProduct("Dầu ăn Tường An 1L");
        manager = dataFactory.createUser(Role.MANAGER, "Test@1234", warehouse);
        AuthTestSupport.loginAs(manager);

        // Nhập 100 để bắt đầu
        transactionTemplate.execute(status -> inventoryService.applyReceipt(
                List.of(movement(new BigDecimal("100"), new BigDecimal("50000"), RefType.RECEIPT, "PN-CC-001"))));
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.clear();
    }

    @Test
    @DisplayName("Hai phiếu xuất 60 trên tồn 100: chỉ một phiếu thành công, tồn không âm")
    void haiPhieuXuatVuotTon_chiMotThanhCong() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch startSignal = new CountDownLatch(1);

        // Mỗi luồng cố xuất 60; tổng 120 > tồn 100 nên chỉ một luồng được phép
        Callable<Void> issueTask = () -> {
            AuthTestSupport.loginAs(manager); // mỗi luồng cần SecurityContext riêng
            startSignal.await(5, TimeUnit.SECONDS);
            try {
                transactionTemplate.execute(status -> inventoryService.applyIssue(
                        List.of(movement(new BigDecimal("60"), null, RefType.ISSUE, "PX-CC-001"))));
                successCount.incrementAndGet();
            } catch (RuntimeException ex) {
                failureCount.incrementAndGet();
            } finally {
                AuthTestSupport.clear();
            }
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(issueTask);
            Future<Void> second = executor.submit(issueTask);

            startSignal.countDown(); // bắn cờ cho hai luồng cùng chạy
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get())
                .as("Chỉ đúng một phiếu xuất được hoàn tất")
                .isEqualTo(1);
        assertThat(failureCount.get())
                .as("Phiếu còn lại phải bị chặn vì không đủ tồn")
                .isEqualTo(1);

        BigDecimal remaining = inventoryService.getQuantityOnHand(product.getId(), warehouse.getId());
        assertThat(remaining)
                .as("Tồn kho sau cùng phải là 100 - 60 = 40, không bao giờ âm")
                .isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("Hai phiếu xuất 40 trên tồn 100: cả hai thành công, tồn còn 20")
    void haiPhieuXuatVuaDu_caHaiThanhCong() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startSignal = new CountDownLatch(1);

        Callable<Void> issueTask = () -> {
            AuthTestSupport.loginAs(manager);
            startSignal.await(5, TimeUnit.SECONDS);
            try {
                transactionTemplate.execute(status -> inventoryService.applyIssue(
                        List.of(movement(new BigDecimal("40"), null, RefType.ISSUE, "PX-CC-002"))));
                successCount.incrementAndGet();
            } finally {
                AuthTestSupport.clear();
            }
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(issueTask);
            Future<Void> second = executor.submit(issueTask);
            startSignal.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(2);
        assertThat(inventoryService.getQuantityOnHand(product.getId(), warehouse.getId()))
                .isEqualByComparingTo("20");
    }

    private StockMovement movement(BigDecimal quantity, BigDecimal unitPrice, RefType refType, String refCode) {
        return new StockMovement(
                product,
                warehouse,
                quantity,
                unitPrice == null ? BigDecimal.ZERO : unitPrice,
                refType,
                1L,
                refCode,
                null,
                LocalDateTime.now());
    }
}
