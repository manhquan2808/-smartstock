package com.smartstock.report;

import com.smartstock.catalog.ProductRepository;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.inventory.StockTransactionRepository;
import com.smartstock.inventory.WarehouseScope;
import com.smartstock.issue.StockIssueRepository;
import com.smartstock.receipt.StockReceiptRepository;
import com.smartstock.report.ReportDtos.DailyFlowItem;
import com.smartstock.report.ReportDtos.DashboardResponse;
import com.smartstock.report.ReportDtos.LowStockItem;
import com.smartstock.report.ReportDtos.TopProductItem;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import com.smartstock.security.WarehouseAccessGuard;
import com.smartstock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tổng hợp số liệu cho trang Dashboard.
 *
 * <p>Phân quyền theo đặc tả mục 3: vai trò STAFF chỉ thấy "dashboard rút gọn" - các số
 * liệu về GIÁ TRỊ (tổng giá trị tồn) bị lược bỏ, chỉ còn số lượng và số phiếu.
 *
 * <p>Về hiệu năng: mỗi thẻ chỉ số là một truy vấn đếm/tổng riêng, có chỉ mục hỗ trợ.
 * Cách này đơn giản và đủ nhanh ở quy mô đề bài (khoảng 20 người dùng đồng thời).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Số ngày hiển thị trên biểu đồ nhập - xuất. */
    private static final int CHART_DAYS = 30;

    /** Số mặt hàng trong danh sách Top xuất nhiều nhất. */
    private static final int TOP_PRODUCT_LIMIT = 10;

    /** Số mặt hàng sắp hết hiển thị trên dashboard. */
    private static final int LOW_STOCK_LIMIT = 10;

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockIssueRepository issueRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseAccessGuard warehouseAccessGuard;

    /**
     * Dữ liệu dashboard.
     *
     * @param warehouseId null nghĩa là gộp mọi kho mà người dùng được xem
     */
    @Transactional(readOnly = true)
    public DashboardResponse build(Long warehouseId) {
        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        AppUserDetails current = SecurityUtils.getCurrentUser();
        boolean canViewValue = current.canViewCostPrice();

        LocalDate today = LocalDate.now();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        LocalDateTime chartFrom = today.minusDays(CHART_DAYS - 1L).atStartOfDay();
        LocalDateTime chartTo = today.atTime(LocalTime.MAX);

        // ---------- Thẻ chỉ số ----------
        long totalProducts = productRepository.countByDeletedAtIsNull();
        long productsInStock = inventoryRepository.countProductsInStock(scope.all(), scope.ids());
        long lowStockCount = inventoryRepository.countLowStock(scope.all(), scope.ids());
        BigDecimal totalStockValue = inventoryRepository.sumStockValue(scope.all(), scope.ids());

        long receiptsThisMonth =
                receiptRepository.countCompletedBetween(firstDayOfMonth, today, scope.all(), scope.ids());
        long issuesThisMonth =
                issueRepository.countCompletedBetween(firstDayOfMonth, today, scope.all(), scope.ids());
        long pendingDocuments = receiptRepository.countPending(scope.all(), scope.ids())
                + issueRepository.countPending(scope.all(), scope.ids());

        // ---------- Danh sách hàng sắp hết ----------
        List<LowStockItem> lowStockItems = inventoryRepository.findLowStock(scope.all(), scope.ids()).stream()
                .limit(LOW_STOCK_LIMIT)
                .map(inventory -> new LowStockItem(
                        inventory.getProduct().getId(),
                        inventory.getProduct().getSku(),
                        inventory.getProduct().getName(),
                        inventory.getProduct().getUnit().getName(),
                        inventory.getWarehouse().getName(),
                        inventory.getQuantity(),
                        inventory.getProduct().getMinStock()))
                .toList();

        // ---------- Biểu đồ nhập - xuất theo ngày ----------
        List<DailyFlowItem> dailyFlow = stockTransactionRepository
                .findDailyFlow(chartFrom, chartTo, scope.all(), scope.ids())
                .stream()
                .map(row -> new DailyFlowItem(row.date(), row.receivedQuantity(), row.issuedQuantity()))
                .toList();

        // ---------- Top 10 sản phẩm xuất nhiều nhất ----------
        List<TopProductItem> topProducts = stockTransactionRepository
                .findTopIssuedProducts(
                        chartFrom, chartTo, scope.all(), scope.ids(), PageRequest.of(0, TOP_PRODUCT_LIMIT))
                .stream()
                .map(row -> new TopProductItem(
                        row.productId(), row.sku(), row.productName(), row.unitName(), row.quantity()))
                .toList();

        return new DashboardResponse(
                totalProducts,
                productsInStock,
                // STAFF không được thấy số liệu giá trị
                canViewValue ? totalStockValue : null,
                lowStockCount,
                receiptsThisMonth,
                issuesThisMonth,
                pendingDocuments,
                lowStockItems,
                dailyFlow,
                topProducts,
                describeWarehouse(warehouseId));
    }

    private String describeWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return "Tất cả kho";
        }
        return warehouseRepository
                .findById(warehouseId)
                .map(warehouse -> warehouse.getName())
                .orElse("Tất cả kho");
    }
}
