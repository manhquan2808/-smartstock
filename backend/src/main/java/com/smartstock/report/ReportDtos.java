package com.smartstock.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO của phân hệ báo cáo và dashboard. */
public final class ReportDtos {

    private ReportDtos() {}

    // ==================================================================
    // Báo cáo nhập - xuất - tồn
    // ==================================================================

    /**
     * Báo cáo NHẬP - XUẤT - TỒN theo kỳ.
     *
     * @param warehouseName tên kho, hoặc "Tất cả kho" khi không lọc
     * @param exportedBy họ tên người xuất báo cáo (yêu cầu của đặc tả về file xuất ra)
     */
    public record InventoryReportResponse(
            LocalDate fromDate,
            LocalDate toDate,
            String warehouseName,
            String categoryName,
            List<InventoryReportRowResponse> rows,
            BigDecimal totalOpeningQuantity,
            BigDecimal totalReceivedQuantity,
            BigDecimal totalIssuedQuantity,
            BigDecimal totalClosingQuantity,
            BigDecimal totalClosingValue,
            String exportedBy,
            LocalDateTime exportedAt) {}

    /**
     * Một dòng báo cáo nhập - xuất - tồn.
     *
     * @param closingValue giá trị tồn cuối kỳ; {@code null} với vai trò STAFF
     */
    public record InventoryReportRowResponse(
            Long productId,
            String sku,
            String productName,
            String unitName,
            String categoryName,
            BigDecimal openingQuantity,
            BigDecimal receivedQuantity,
            BigDecimal issuedQuantity,
            BigDecimal closingQuantity,
            BigDecimal avgCost,
            BigDecimal closingValue) {}

    // ==================================================================
    // Báo cáo hàng tồn lâu
    // ==================================================================

    /** Báo cáo hàng không phát sinh xuất trong N ngày. */
    public record SlowMovingReportResponse(
            int thresholdDays,
            String warehouseName,
            List<SlowMovingRowResponse> rows,
            BigDecimal totalValue,
            String exportedBy,
            LocalDateTime exportedAt) {}

    /**
     * Một dòng hàng tồn lâu.
     *
     * @param lastIssueDate ngày xuất gần nhất; {@code null} nghĩa là chưa từng xuất
     * @param daysSinceLastIssue số ngày kể từ lần xuất cuối; {@code null} nếu chưa từng xuất
     */
    public record SlowMovingRowResponse(
            Long productId,
            String sku,
            String productName,
            String unitName,
            Long warehouseId,
            String warehouseName,
            BigDecimal quantity,
            BigDecimal avgCost,
            BigDecimal stockValue,
            LocalDate lastIssueDate,
            Long daysSinceLastIssue) {}

    // ==================================================================
    // Dashboard
    // ==================================================================

    /**
     * Dữ liệu trang Dashboard.
     *
     * @param totalStockValue tổng giá trị tồn; {@code null} với vai trò STAFF
     *     (đặc tả: STAFF chỉ thấy dashboard rút gọn, không có số liệu giá trị)
     */
    public record DashboardResponse(
            long totalProducts,
            long productsInStock,
            BigDecimal totalStockValue,
            long lowStockCount,
            long receiptsThisMonth,
            long issuesThisMonth,
            long pendingDocuments,
            List<LowStockItem> lowStockItems,
            List<DailyFlowItem> dailyFlow,
            List<TopProductItem> topIssuedProducts,
            String warehouseName) {}

    /** Một mặt hàng sắp hết trên dashboard. */
    public record LowStockItem(
            Long productId,
            String sku,
            String productName,
            String unitName,
            String warehouseName,
            BigDecimal quantity,
            BigDecimal minStock) {}

    /** Lượng nhập - xuất của một ngày (dữ liệu biểu đồ). */
    public record DailyFlowItem(LocalDate date, BigDecimal receivedQuantity, BigDecimal issuedQuantity) {}

    /** Một mặt hàng trong Top 10 xuất nhiều nhất. */
    public record TopProductItem(
            Long productId, String sku, String productName, String unitName, BigDecimal quantity) {}

    // ==================================================================
    // Gợi ý nhập hàng (dự báo nhu cầu)
    // ==================================================================

    /**
     * Kết quả trang "Gợi ý nhập hàng".
     *
     * <p>Các tham số của công thức được trả kèm để giao diện hiển thị tooltip giải thích,
     * giúp người trình bày thuyết minh được cách tính khi bảo vệ đồ án.
     */
    public record ReplenishmentResponse(
            Long warehouseId,
            String warehouseName,
            int windowDays,
            int leadTimeDays,
            int safetyStockDays,
            String formulaExplanation,
            List<ReplenishmentRowResponse> rows) {}

    /**
     * Một dòng gợi ý nhập hàng.
     *
     * @param averageDailyIssue lượng xuất trung bình mỗi ngày trong N ngày gần nhất
     * @param daysOfStockLeft số ngày còn đủ hàng; {@code null} nghĩa là chưa từng xuất
     *     (không dự báo được, hiển thị dấu gạch trên giao diện)
     * @param suggestedQuantity số lượng nên nhập, đã làm tròn lên và không âm
     */
    public record ReplenishmentRowResponse(
            Long productId,
            String sku,
            String productName,
            String unitName,
            BigDecimal quantityOnHand,
            BigDecimal minStock,
            BigDecimal totalIssued,
            BigDecimal averageDailyIssue,
            BigDecimal daysOfStockLeft,
            BigDecimal suggestedQuantity,
            BigDecimal purchasePrice,
            BigDecimal estimatedCost,
            String urgency) {}

    /** Dữ liệu biểu đồ lượng xuất theo ngày của một sản phẩm. */
    public record IssueTrendResponse(
            Long productId, String productName, int windowDays, List<DailyQuantityItem> points) {}

    /** Một điểm trên biểu đồ lượng xuất theo ngày. */
    public record DailyQuantityItem(LocalDate date, BigDecimal quantity) {}
}
