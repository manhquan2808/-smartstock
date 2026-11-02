package com.smartstock.stocktake;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.catalog.Category;
import com.smartstock.catalog.CategoryRepository;
import com.smartstock.common.BusinessException;
import com.smartstock.common.ForbiddenException;
import com.smartstock.common.MoneyUtils;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import com.smartstock.document.DocumentNotifier;
import com.smartstock.document.DocumentSequenceService;
import com.smartstock.document.DocumentStatus;
import com.smartstock.inventory.Inventory;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.inventory.InventoryService;
import com.smartstock.inventory.InventoryService.StockMovement;
import com.smartstock.inventory.RefType;
import com.smartstock.inventory.WarehouseScope;
import com.smartstock.issue.StockIssueRepository;
import com.smartstock.notification.NotificationService;
import com.smartstock.notification.NotificationType;
import com.smartstock.receipt.StockReceiptRepository;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import com.smartstock.security.WarehouseAccessGuard;
import com.smartstock.stocktake.StocktakeDtos.CountItemRequest;
import com.smartstock.stocktake.StocktakeDtos.CreateStocktakeRequest;
import com.smartstock.stocktake.StocktakeDtos.SaveCountRequest;
import com.smartstock.stocktake.StocktakeDtos.StocktakeDetailResponse;
import com.smartstock.stocktake.StocktakeDtos.StocktakeItemResponse;
import com.smartstock.stocktake.StocktakeDtos.StocktakeSummaryResponse;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ kiểm kê kho.
 *
 * <p><b>Bốn bước của một đợt kiểm kê:</b>
 *
 * <ol>
 *   <li><b>Tạo đợt</b>: hệ thống chụp lại số tồn hiện tại và giá vốn của từng sản phẩm
 *       trong kho (có thể lọc theo loại hàng). Số chụp này là mốc để so sánh.</li>
 *   <li><b>Đếm</b>: nhân viên nhập số thực tế. Có thể quét mã để tìm nhanh sản phẩm trên
 *       điện thoại. Lưu nhiều lần, không cần đếm hết một lượt.</li>
 *   <li><b>Gửi duyệt</b>: bắt buộc đã đếm hết các dòng.</li>
 *   <li><b>Duyệt</b>: tồn kho được điều chỉnh về đúng số thực tế, mỗi dòng lệch sinh một
 *       giao dịch {@code ADJUSTMENT} trong sổ kho.</li>
 * </ol>
 *
 * <p><b>Cảnh báo quan trọng:</b> nếu trong lúc đợt kiểm kê đang mở mà có phiếu nhập/xuất
 * hoàn tất tại kho đó, số tồn hệ thống đã chụp sẽ không còn đúng. Hệ thống phát hiện và
 * hiển thị danh sách mã phiếu gây ảnh hưởng để người duyệt cân nhắc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeService {

    private static final String ENTITY_NAME = "Stocktake";

    private final StocktakeRepository stocktakeRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockIssueRepository issueRepository;
    private final InventoryService inventoryService;
    private final DocumentSequenceService sequenceService;
    private final WarehouseAccessGuard warehouseAccessGuard;
    private final NotificationService notificationService;
    private final DocumentNotifier documentNotifier;
    private final AuditService auditService;

    // ============================== Truy vấn ==============================

    @Transactional(readOnly = true)
    public PageResponse<StocktakeSummaryResponse> search(
            Long warehouseId, DocumentStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable) {

        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        return PageResponse.from(
                stocktakeRepository.search(scope.all(), scope.ids(), status, fromDate, toDate, pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public StocktakeDetailResponse getById(Long id) {
        Stocktake stocktake = findOrThrow(id);
        warehouseAccessGuard.requireAccess(stocktake.getWarehouse().getId());
        return toDetail(stocktake);
    }

    // ============================== Tạo và đếm ==============================

    /**
     * Tạo đợt kiểm kê và chụp số tồn hệ thống tại thời điểm này.
     *
     * <p>Chặn tạo đợt mới khi kho đang có một đợt kiểm kê chưa hoàn tất: hai đợt song song
     * trên cùng một kho sẽ điều chỉnh chồng chéo lên nhau.
     */
    @Transactional
    public StocktakeDetailResponse create(CreateStocktakeRequest request) {
        Warehouse warehouse = warehouseAccessGuard.requireAccessibleWarehouse(request.warehouseId());

        if (stocktakeRepository.existsOpenStocktake(warehouse.getId())) {
            throw new BusinessException(
                    "Kho này đang có một đợt kiểm kê chưa hoàn tất. Vui lòng hoàn tất hoặc hủy đợt đó trước.",
                    "STOCKTAKE_IN_PROGRESS");
        }

        Category category = request.categoryId() == null
                ? null
                : categoryRepository
                        .findByIdAndDeletedAtIsNull(request.categoryId())
                        .orElseThrow(() -> new NotFoundException("loại hàng", request.categoryId()));

        Stocktake stocktake = Stocktake.builder()
                .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_STOCKTAKE, request.documentDate()))
                .warehouse(warehouse)
                .documentDate(request.documentDate())
                .category(category)
                .note(emptyToNull(request.note()))
                .status(DocumentStatus.DRAFT)
                .build();

        // Chụp số tồn và giá vốn hiện tại của từng sản phẩm trong kho
        List<Inventory> inventories =
                inventoryRepository.findByWarehouseForStocktake(warehouse.getId(), request.categoryId());

        if (inventories.isEmpty()) {
            throw new BusinessException(
                    "Kho chưa có sản phẩm nào để kiểm kê. Vui lòng nhập hàng trước khi kiểm kê.",
                    "NO_PRODUCT_TO_COUNT");
        }

        for (Inventory inventory : inventories) {
            stocktake.addItem(StocktakeItem.builder()
                    .product(inventory.getProduct())
                    .systemQuantity(inventory.getQuantity())
                    .unitCost(inventory.getAvgCost())
                    .build());
        }

        Stocktake saved = stocktakeRepository.save(stocktake);
        auditService.log(
                AuditAction.CREATE,
                ENTITY_NAME,
                saved.getId(),
                "Tạo đợt kiểm kê " + saved.getCode() + " với " + inventories.size() + " mặt hàng");
        return toDetail(saved);
    }

    /**
     * Lưu số đếm thực tế. Gọi được nhiều lần, mỗi lần gửi một phần danh sách.
     *
     * <p>Đây là thao tác nhân viên dùng trên điện thoại khi đi đếm trong kho, nên API
     * nhận theo lô để giảm số lần gọi mạng.
     */
    @Transactional
    public StocktakeDetailResponse saveCount(Long id, SaveCountRequest request) {
        Stocktake stocktake = findOrThrow(id);
        warehouseAccessGuard.requireAccess(stocktake.getWarehouse().getId());

        if (stocktake.getStatus() != DocumentStatus.DRAFT && stocktake.getStatus() != DocumentStatus.REJECTED) {
            throw new BusinessException(
                    "Chỉ nhập được số thực tế khi đợt kiểm kê ở trạng thái Nháp hoặc Bị từ chối.", "NOT_EDITABLE");
        }

        Map<Long, StocktakeItem> itemsById =
                stocktake.getItems().stream().collect(java.util.stream.Collectors.toMap(StocktakeItem::getId, item -> item));

        for (CountItemRequest countRequest : request.items()) {
            StocktakeItem item = itemsById.get(countRequest.itemId());
            if (item == null) {
                throw new BusinessException(
                        "Dòng kiểm kê " + countRequest.itemId() + " không thuộc đợt kiểm kê này.", "ITEM_NOT_FOUND");
            }
            item.setActualQuantity(
                    countRequest.actualQuantity() == null ? null : MoneyUtils.quantity(countRequest.actualQuantity()));
            if (countRequest.note() != null) {
                item.setNote(emptyToNull(countRequest.note()));
            }
        }

        Stocktake saved = stocktakeRepository.save(stocktake);
        return toDetail(saved);
    }

    // ============================== Luồng duyệt ==============================

    /** Gửi duyệt. Bắt buộc đã đếm hết mọi dòng để tránh điều chỉnh nhầm thành 0. */
    @Transactional
    public StocktakeDetailResponse submit(Long id) {
        Stocktake stocktake = findOrThrow(id);
        warehouseAccessGuard.requireAccess(stocktake.getWarehouse().getId());

        if (stocktake.getStatus() != DocumentStatus.DRAFT && stocktake.getStatus() != DocumentStatus.REJECTED) {
            throw new BusinessException("Chỉ gửi duyệt được đợt kiểm kê ở trạng thái Nháp.", "INVALID_STATUS");
        }

        long notCounted = stocktake.getItems().stream()
                .filter(item -> !item.isCounted())
                .count();
        if (notCounted > 0) {
            throw new BusinessException(
                    "Còn " + notCounted + " mặt hàng chưa nhập số thực tế. Vui lòng đếm đủ trước khi gửi duyệt.",
                    "INCOMPLETE_COUNT");
        }

        stocktake.setStatus(DocumentStatus.PENDING);
        stocktake.setSubmittedAt(LocalDateTime.now());
        stocktake.setRejectReason(null);
        Stocktake saved = stocktakeRepository.save(stocktake);

        documentNotifier.notifyPendingApproval(
                saved.getWarehouse(),
                NotificationType.STOCKTAKE_PENDING,
                "Phiếu kiểm kê",
                saved.getCode(),
                "/stocktakes/" + saved.getId());

        auditService.log(AuditAction.SUBMIT, ENTITY_NAME, id, "Gửi duyệt kiểm kê " + saved.getCode());
        return toDetail(saved);
    }

    /**
     * DUYỆT kiểm kê: điều chỉnh tồn kho về đúng số đã đếm.
     *
     * <p>Mỗi dòng có chênh lệch sinh một giao dịch {@code ADJUSTMENT} với lượng bằng đúng
     * phần lệch; dòng không lệch thì không ghi gì. Giá vốn giữ nguyên vì hàng vẫn là hàng
     * cũ, chỉ số lượng được sửa cho khớp thực tế.
     */
    @Transactional
    public StocktakeDetailResponse approve(Long id) {
        Stocktake stocktake = findOrThrow(id);
        requireApprovalRight(stocktake);

        if (stocktake.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Chỉ duyệt được đợt kiểm kê đang Chờ duyệt.", "INVALID_STATUS");
        }

        // Chỉ gửi sang InventoryService các dòng thực sự lệch
        List<StockMovement> movements = new ArrayList<>();
        for (StocktakeItem item : stocktake.getItems()) {
            if (item.getActualQuantity() == null || MoneyUtils.isZero(item.getDifference())) {
                continue;
            }
            movements.add(new StockMovement(
                    item.getProduct(),
                    stocktake.getWarehouse(),
                    item.getActualQuantity(), // truyền SỐ THỰC TẾ, service tự tính phần lệch
                    item.getUnitCost(),
                    RefType.STOCKTAKE,
                    stocktake.getId(),
                    stocktake.getCode(),
                    "Điều chỉnh theo kiểm kê " + stocktake.getCode(),
                    stocktake.getDocumentDate().atStartOfDay()));
        }

        if (!movements.isEmpty()) {
            inventoryService.applyStocktakeAdjustment(movements);
        } else {
            log.info("Đợt kiểm kê {} không có chênh lệch, không phát sinh điều chỉnh tồn", stocktake.getCode());
        }

        User approver = currentUserEntity();
        stocktake.setStatus(DocumentStatus.COMPLETED);
        stocktake.setApprovedBy(approver);
        stocktake.setApprovedAt(LocalDateTime.now());
        Stocktake saved = stocktakeRepository.save(stocktake);

        if (saved.getCreatedBy() != null) {
            notificationService.notifyUser(
                    saved.getCreatedBy(),
                    NotificationType.DOCUMENT_APPROVED,
                    "Phiếu kiểm kê đã được duyệt",
                    "Kiểm kê " + saved.getCode() + " đã được duyệt. Tồn kho đã điều chỉnh theo số thực tế.",
                    "/stocktakes/" + saved.getId());
        }

        auditService.log(
                AuditAction.APPROVE,
                ENTITY_NAME,
                id,
                "Duyệt kiểm kê " + saved.getCode() + ", điều chỉnh " + movements.size() + " mặt hàng");
        return toDetail(saved);
    }

    @Transactional
    public StocktakeDetailResponse reject(Long id, String reason) {
        Stocktake stocktake = findOrThrow(id);
        requireApprovalRight(stocktake);

        if (stocktake.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Chỉ từ chối được đợt kiểm kê đang Chờ duyệt.", "INVALID_STATUS");
        }

        stocktake.setStatus(DocumentStatus.REJECTED);
        stocktake.setRejectReason(reason.trim());
        Stocktake saved = stocktakeRepository.save(stocktake);

        if (saved.getCreatedBy() != null) {
            notificationService.notifyUser(
                    saved.getCreatedBy(),
                    NotificationType.DOCUMENT_REJECTED,
                    "Phiếu kiểm kê bị từ chối",
                    "Kiểm kê " + saved.getCode() + " bị từ chối. Lý do: " + reason.trim(),
                    "/stocktakes/" + saved.getId());
        }

        auditService.log(AuditAction.REJECT, ENTITY_NAME, id, "Từ chối kiểm kê " + saved.getCode());
        return toDetail(saved);
    }

    /** Hủy đợt kiểm kê chưa duyệt (không tác động tồn kho vì chưa điều chỉnh gì). */
    @Transactional
    public void cancel(Long id, String reason) {
        Stocktake stocktake = findOrThrow(id);
        warehouseAccessGuard.requireAccess(stocktake.getWarehouse().getId());

        if (stocktake.getStatus() == DocumentStatus.COMPLETED) {
            throw new BusinessException(
                    "Không thể hủy đợt kiểm kê đã duyệt vì tồn kho đã được điều chỉnh. "
                            + "Vui lòng tạo đợt kiểm kê mới để chỉnh lại.",
                    "ALREADY_COMPLETED");
        }

        stocktake.setStatus(DocumentStatus.CANCELLED);
        stocktake.setCancelledBy(currentUserEntity());
        stocktake.setCancelledAt(LocalDateTime.now());
        stocktake.setCancelReason(reason == null ? null : reason.trim());
        stocktakeRepository.save(stocktake);

        auditService.log(AuditAction.CANCEL, ENTITY_NAME, id, "Hủy kiểm kê " + stocktake.getCode());
    }

    /** Lấy entity cho chức năng in PDF. */
    @Transactional(readOnly = true)
    public Stocktake getEntityForPrint(Long id) {
        Stocktake stocktake = findOrThrow(id);
        warehouseAccessGuard.requireAccess(stocktake.getWarehouse().getId());
        stocktake.getItems().size();
        return stocktake;
    }

    // ============================== Hàm hỗ trợ ==============================

    private void requireApprovalRight(Stocktake stocktake) {
        warehouseAccessGuard.requireAccess(stocktake.getWarehouse().getId());
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (!current.canApprove()) {
            throw new ForbiddenException("Bạn không có quyền duyệt kiểm kê. Chức năng này dành cho quản lý kho.");
        }
    }

    private User currentUserEntity() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("người dùng", userId));
    }

    private Stocktake findOrThrow(Long id) {
        return stocktakeRepository
                .findWithItemsById(id)
                .orElseThrow(() -> new NotFoundException("phiếu kiểm kê", id));
    }

    // ============================== Chuyển đổi DTO ==============================

    private StocktakeSummaryResponse toSummary(Stocktake stocktake) {
        boolean canViewCost = SecurityUtils.getCurrentUser().canViewCostPrice();
        List<StocktakeItem> items = stocktake.getItems();

        return new StocktakeSummaryResponse(
                stocktake.getId(),
                stocktake.getCode(),
                stocktake.getWarehouse().getId(),
                stocktake.getWarehouse().getName(),
                stocktake.getDocumentDate(),
                stocktake.getCategory() == null ? "Toàn bộ sản phẩm" : stocktake.getCategory().getName(),
                stocktake.getStatus(),
                stocktake.getStatus().getDisplayName(),
                items.size(),
                (int) items.stream().filter(StocktakeItem::isCounted).count(),
                (int) items.stream().filter(item -> !MoneyUtils.isZero(item.getDifference())).count(),
                canViewCost ? totalDifferenceValue(items) : null,
                documentNotifier.userDisplayName(stocktake.getCreatedBy()),
                stocktake.getApprovedBy() == null ? null : stocktake.getApprovedBy().getFullName(),
                stocktake.getCreatedAt(),
                stocktake.getApprovedAt());
    }

    private StocktakeDetailResponse toDetail(Stocktake stocktake) {
        AppUserDetails current = SecurityUtils.getCurrentUserOrNull();
        boolean canViewCost = current != null && current.canViewCostPrice();
        List<StocktakeItem> items = stocktake.getItems();

        List<StocktakeItemResponse> itemResponses = items.stream()
                .map(item -> new StocktakeItemResponse(
                        item.getId(),
                        item.getLineNo(),
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getBarcode(),
                        item.getProduct().getName(),
                        item.getProduct().getUnit().getName(),
                        item.getSystemQuantity(),
                        item.getActualQuantity(),
                        item.isCounted() ? item.getDifference() : null,
                        canViewCost ? item.getUnitCost() : null,
                        canViewCost && item.isCounted() ? item.getDifferenceValue() : null,
                        item.isCounted(),
                        item.getNote()))
                .toList();

        return new StocktakeDetailResponse(
                stocktake.getId(),
                stocktake.getCode(),
                stocktake.getWarehouse().getId(),
                stocktake.getWarehouse().getCode(),
                stocktake.getWarehouse().getName(),
                stocktake.getDocumentDate(),
                stocktake.getCategory() == null ? null : stocktake.getCategory().getId(),
                stocktake.getCategory() == null ? "Toàn bộ sản phẩm" : stocktake.getCategory().getName(),
                stocktake.getNote(),
                stocktake.getStatus(),
                stocktake.getStatus().getDisplayName(),
                itemResponses,
                items.size(),
                (int) items.stream().filter(StocktakeItem::isCounted).count(),
                (int) items.stream().filter(item -> !MoneyUtils.isZero(item.getDifference())).count(),
                canViewCost ? totalDifferenceValue(items) : null,
                documentNotifier.userDisplayName(stocktake.getCreatedBy()),
                stocktake.getCreatedAt(),
                stocktake.getSubmittedAt(),
                stocktake.getApprovedBy() == null ? null : stocktake.getApprovedBy().getFullName(),
                stocktake.getApprovedAt(),
                stocktake.getRejectReason(),
                stocktake.getCancelReason(),
                (stocktake.getStatus() == DocumentStatus.DRAFT || stocktake.getStatus() == DocumentStatus.REJECTED),
                current != null && current.canApprove() && stocktake.getStatus() == DocumentStatus.PENDING,
                findConflictingDocuments(stocktake));
    }

    /**
     * Tìm các phiếu nhập/xuất đã hoàn tất SAU khi đợt kiểm kê được tạo.
     *
     * <p>Những phiếu này làm số tồn hệ thống thay đổi so với lúc chụp, nên kết quả kiểm kê
     * có thể bị lệch. Hiển thị danh sách để người duyệt biết mà cân nhắc.
     */
    private List<String> findConflictingDocuments(Stocktake stocktake) {
        if (!stocktake.isOpen() || stocktake.getCreatedAt() == null) {
            return List.of();
        }
        Long warehouseId = stocktake.getWarehouse().getId();
        LocalDateTime since = stocktake.getCreatedAt();
        return Stream.concat(
                        receiptRepository.findCompletedCodesSince(warehouseId, since).stream(),
                        issueRepository.findCompletedCodesSince(warehouseId, since).stream())
                .toList();
    }

    private BigDecimal totalDifferenceValue(List<StocktakeItem> items) {
        return items.stream()
                .map(StocktakeItem::getDifferenceValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
