package com.smartstock.issue;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.catalog.Product;
import com.smartstock.catalog.ProductService;
import com.smartstock.common.BusinessException;
import com.smartstock.common.ForbiddenException;
import com.smartstock.common.InsufficientStockException.ShortageDetail;
import com.smartstock.common.MoneyUtils;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import com.smartstock.document.DocumentDtos.DocumentDetailResponse;
import com.smartstock.document.DocumentDtos.DocumentItemResponse;
import com.smartstock.document.DocumentDtos.DocumentSummaryResponse;
import com.smartstock.document.DocumentDtos.SaveIssueItemRequest;
import com.smartstock.document.DocumentDtos.SaveIssueRequest;
import com.smartstock.document.DocumentDtos.StockCheckResponse;
import com.smartstock.document.DocumentDtos.StockShortageRow;
import com.smartstock.document.DocumentNotifier;
import com.smartstock.document.DocumentSequenceService;
import com.smartstock.document.DocumentStatus;
import com.smartstock.inventory.InventoryService;
import com.smartstock.inventory.InventoryService.StockMovement;
import com.smartstock.inventory.RefType;
import com.smartstock.inventory.StockTransaction;
import com.smartstock.inventory.StockTransactionRepository;
import com.smartstock.inventory.TransactionType;
import com.smartstock.inventory.WarehouseScope;
import com.smartstock.notification.NotificationService;
import com.smartstock.notification.NotificationType;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import com.smartstock.security.WarehouseAccessGuard;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ phiếu xuất kho.
 *
 * <p><b>Quy tắc quan trọng nhất: KHÔNG CHO XUẤT VƯỢT TỒN.</b> Hệ thống kiểm tra ba lần
 * với mục đích khác nhau:
 *
 * <ol>
 *   <li><b>Khi lưu nháp</b> - chỉ CẢNH BÁO (trả về danh sách dòng thiếu nhưng vẫn lưu),
 *       vì người lập có thể đang chuẩn bị phiếu cho lô hàng sắp về.</li>
 *   <li><b>Khi gửi duyệt</b> - CHẶN, để quản lý không phải xem phiếu chắc chắn sai.</li>
 *   <li><b>Khi hoàn tất</b> - CHẶN, và đây là lần kiểm tra có tính quyết định: nó diễn ra
 *       bên trong {@code InventoryService.applyIssue} sau khi dòng tồn đã bị khóa, nên
 *       con số so sánh chắc chắn là mới nhất.</li>
 * </ol>
 *
 * <p>Nhờ lần kiểm tra thứ ba, hai phiếu xuất được duyệt đồng thời trên cùng một sản phẩm
 * mà tổng lượng vượt tồn thì chỉ một phiếu thành công - có integration test chứng minh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockIssueService {

    private static final String ENTITY_NAME = "StockIssue";
    private static final String DOCUMENT_TYPE_NAME = "Phiếu xuất kho";

    private final StockIssueRepository issueRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final DocumentSequenceService sequenceService;
    private final WarehouseAccessGuard warehouseAccessGuard;
    private final NotificationService notificationService;
    private final DocumentNotifier documentNotifier;
    private final AuditService auditService;

    // ============================== Truy vấn ==============================

    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> search(
            Long warehouseId,
            IssueType issueType,
            DocumentStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Long createdBy,
            String keyword,
            Pageable pageable) {

        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        return PageResponse.from(
                issueRepository.search(
                        scope.all(),
                        scope.ids(),
                        issueType,
                        status,
                        fromDate,
                        toDate,
                        createdBy,
                        normalize(keyword),
                        pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse getById(Long id) {
        StockIssue issue = findOrThrow(id);
        warehouseAccessGuard.requireAccess(issue.getWarehouse().getId());
        return toDetail(issue);
    }

    /**
     * Kiểm tra tồn trước khi lưu/gửi duyệt, phục vụ cảnh báo tức thời trên màn hình lập phiếu.
     *
     * <p>Không khóa dòng tồn: kết quả chỉ mang tính tham khảo tại thời điểm gọi.
     */
    @Transactional(readOnly = true)
    public StockCheckResponse checkStock(Long warehouseId, List<SaveIssueItemRequest> items) {
        Warehouse warehouse = warehouseAccessGuard.requireAccessibleWarehouse(warehouseId);

        List<StockMovement> movements = items.stream()
                .map(item -> new StockMovement(
                        productService.requireSelectableProduct(item.productId()),
                        warehouse,
                        MoneyUtils.quantity(item.quantity()),
                        BigDecimal.ZERO,
                        RefType.ISSUE,
                        0L,
                        "",
                        null,
                        null))
                .toList();

        List<ShortageDetail> shortages = inventoryService.checkAvailability(movements);
        return new StockCheckResponse(
                shortages.isEmpty(),
                shortages.stream()
                        .map(s -> new StockShortageRow(
                                s.productId(), s.sku(), s.productName(), s.requested(), s.available()))
                        .toList());
    }

    // ============================== Lập và sửa ==============================

    /**
     * Tạo phiếu xuất ở trạng thái nháp.
     *
     * <p>Thiếu tồn ở bước này KHÔNG chặn việc lưu - giao diện sẽ tô đỏ các dòng thiếu để
     * người lập xử lý. Việc chặn diễn ra khi gửi duyệt và khi hoàn tất.
     */
    @Transactional
    public DocumentDetailResponse create(SaveIssueRequest request, boolean submitImmediately) {
        Warehouse warehouse = warehouseAccessGuard.requireAccessibleWarehouse(request.warehouseId());
        validatePartnerAndReason(request);

        StockIssue issue = StockIssue.builder()
                .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_ISSUE, request.documentDate()))
                .warehouse(warehouse)
                .issueType(request.issueType())
                .documentDate(request.documentDate())
                .customer(request.customerId() == null ? null : documentNotifier.requireCustomer(request.customerId()))
                .supplier(request.supplierId() == null ? null : documentNotifier.requireSupplier(request.supplierId()))
                .reason(emptyToNull(request.reason()))
                .note(emptyToNull(request.note()))
                .status(DocumentStatus.DRAFT)
                .build();

        applyItems(issue, request.items());
        StockIssue saved = issueRepository.save(issue);

        auditService.logChange(
                AuditAction.CREATE, ENTITY_NAME, saved.getId(), "Lập phiếu xuất " + saved.getCode(), null, toDetail(saved));

        if (submitImmediately) {
            return submit(saved.getId());
        }
        return toDetail(saved);
    }

    @Transactional
    public DocumentDetailResponse update(Long id, SaveIssueRequest request) {
        StockIssue issue = findOrThrow(id);
        requireEditable(issue);
        validatePartnerAndReason(request);

        DocumentDetailResponse before = toDetail(issue);
        Warehouse warehouse = warehouseAccessGuard.requireAccessibleWarehouse(request.warehouseId());

        issue.setWarehouse(warehouse);
        issue.setIssueType(request.issueType());
        issue.setDocumentDate(request.documentDate());
        issue.setCustomer(request.customerId() == null ? null : documentNotifier.requireCustomer(request.customerId()));
        issue.setSupplier(request.supplierId() == null ? null : documentNotifier.requireSupplier(request.supplierId()));
        issue.setReason(emptyToNull(request.reason()));
        issue.setNote(emptyToNull(request.note()));

        issue.clearItems();
        applyItems(issue, request.items());

        if (issue.getStatus() == DocumentStatus.REJECTED) {
            issue.setStatus(DocumentStatus.DRAFT);
            issue.setRejectReason(null);
        }

        StockIssue saved = issueRepository.save(issue);
        auditService.logChange(
                AuditAction.UPDATE, ENTITY_NAME, id, "Sửa phiếu xuất " + saved.getCode(), before, toDetail(saved));
        return toDetail(saved);
    }

    @Transactional
    public void delete(Long id) {
        StockIssue issue = findOrThrow(id);
        requireEditable(issue);

        String code = issue.getCode();
        issueRepository.delete(issue);
        auditService.log(AuditAction.DELETE, ENTITY_NAME, id, "Xóa phiếu xuất nháp " + code);
    }

    // ============================== Luồng duyệt ==============================

    /** Gửi duyệt. Kiểm tra tồn và CHẶN nếu thiếu, kèm thông báo nêu rõ sản phẩm nào thiếu bao nhiêu. */
    @Transactional
    public DocumentDetailResponse submit(Long id) {
        StockIssue issue = findOrThrow(id);
        requireEditable(issue);

        if (issue.getItems().isEmpty()) {
            throw new BusinessException("Phiếu phải có ít nhất một dòng hàng trước khi gửi duyệt.", "EMPTY_DOCUMENT");
        }

        List<ShortageDetail> shortages = inventoryService.checkAvailability(buildMovements(issue));
        if (!shortages.isEmpty()) {
            throw new com.smartstock.common.InsufficientStockException(shortages);
        }

        issue.setStatus(DocumentStatus.PENDING);
        issue.setSubmittedAt(LocalDateTime.now());
        issue.setRejectReason(null);
        StockIssue saved = issueRepository.save(issue);

        documentNotifier.notifyPendingApproval(
                saved.getWarehouse(),
                NotificationType.ISSUE_PENDING,
                DOCUMENT_TYPE_NAME,
                saved.getCode(),
                "/issues/" + saved.getId());

        auditService.log(AuditAction.SUBMIT, ENTITY_NAME, id, "Gửi duyệt phiếu xuất " + saved.getCode());
        return toDetail(saved);
    }

    /**
     * DUYỆT phiếu xuất: trừ tồn và ghi sổ giao dịch.
     *
     * <p>Giá vốn tại thời điểm xuất được ghi vào từng dòng phiếu, nhờ vậy báo cáo giá trị
     * xuất kho về sau luôn đúng kể cả khi giá vốn đã thay đổi vì các lần nhập tiếp theo.
     */
    @Transactional
    public DocumentDetailResponse approve(Long id) {
        StockIssue issue = findOrThrow(id);
        requireApprovalRight(issue);

        if (issue.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException(
                    "Chỉ duyệt được phiếu đang ở trạng thái Chờ duyệt. Phiếu này đang ở trạng thái "
                            + issue.getStatus().getDisplayName() + ".",
                    "INVALID_STATUS");
        }

        // Lấy giá vốn TRƯỚC khi trừ tồn: sau khi trừ, giá vốn vẫn không đổi nhưng đọc
        // trước giúp mã rõ ràng là "ghi lại giá vốn tại thời điểm xuất"
        for (StockIssueItem item : issue.getItems()) {
            BigDecimal costAtIssue = inventoryService.getAverageCost(
                    item.getProduct().getId(), issue.getWarehouse().getId());
            item.setUnitCost(costAtIssue);
            item.recalculateAmount();
        }

        // Ném InsufficientStockException nếu không đủ tồn -> toàn bộ giao dịch rollback
        inventoryService.applyIssue(buildMovements(issue));

        User approver = currentUserEntity();
        issue.setStatus(DocumentStatus.COMPLETED);
        issue.setApprovedBy(approver);
        issue.setApprovedAt(LocalDateTime.now());
        issue.recalculateTotal();
        StockIssue saved = issueRepository.save(issue);

        notifyCreator(
                saved,
                NotificationType.DOCUMENT_APPROVED,
                "Phiếu xuất đã được duyệt",
                "Phiếu xuất " + saved.getCode() + " đã được " + approver.getFullName() + " duyệt. Tồn kho đã cập nhật.");

        // Xuất hàng xong, nhiều sản phẩm có thể rơi xuống dưới mức tồn tối thiểu
        documentNotifier.checkLowStockAfterChange(saved.getWarehouse(), productsOf(saved));

        auditService.log(AuditAction.APPROVE, ENTITY_NAME, id, "Duyệt phiếu xuất " + saved.getCode());
        return toDetail(saved);
    }

    @Transactional
    public DocumentDetailResponse reject(Long id, String reason) {
        StockIssue issue = findOrThrow(id);
        requireApprovalRight(issue);

        if (issue.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Chỉ từ chối được phiếu đang ở trạng thái Chờ duyệt.", "INVALID_STATUS");
        }

        issue.setStatus(DocumentStatus.REJECTED);
        issue.setRejectReason(reason.trim());
        StockIssue saved = issueRepository.save(issue);

        notifyCreator(
                saved,
                NotificationType.DOCUMENT_REJECTED,
                "Phiếu xuất bị từ chối",
                "Phiếu xuất " + saved.getCode() + " bị từ chối. Lý do: " + reason.trim());

        auditService.log(
                AuditAction.REJECT, ENTITY_NAME, id, "Từ chối phiếu xuất " + saved.getCode() + ". Lý do: " + reason);
        return toDetail(saved);
    }

    /**
     * HỦY phiếu xuất đã hoàn tất: ghi giao dịch đảo ngược làm TĂNG tồn trở lại.
     *
     * <p>Khác với hủy phiếu nhập, thao tác này luôn thực hiện được vì chỉ cộng thêm tồn.
     */
    @Transactional
    public DocumentDetailResponse cancel(Long id, String reason) {
        StockIssue issue = findOrThrow(id);
        requireApprovalRight(issue);

        if (issue.getStatus() != DocumentStatus.COMPLETED) {
            throw new BusinessException(
                    "Chỉ hủy được phiếu đã hoàn tất. Phiếu nháp hoặc chờ duyệt thì xóa hoặc từ chối.",
                    "INVALID_STATUS");
        }

        List<StockTransaction> originals =
                stockTransactionRepository.findByRefTypeAndRefIdOrderByIdAsc(RefType.ISSUE, issue.getId()).stream()
                        .filter(t -> t.getType() == TransactionType.ISSUE)
                        .toList();

        if (originals.isEmpty()) {
            throw new BusinessException(
                    "Không tìm thấy giao dịch kho của phiếu này nên không thể hủy.", "NO_TRANSACTIONS");
        }

        inventoryService.reverseTransactions(originals, reason);

        User canceller = currentUserEntity();
        issue.setStatus(DocumentStatus.CANCELLED);
        issue.setCancelledBy(canceller);
        issue.setCancelledAt(LocalDateTime.now());
        issue.setCancelReason(reason.trim());
        StockIssue saved = issueRepository.save(issue);

        notifyCreator(
                saved,
                NotificationType.DOCUMENT_CANCELLED,
                "Phiếu xuất đã bị hủy",
                "Phiếu xuất " + saved.getCode() + " đã bị hủy. Lý do: " + reason.trim());

        auditService.log(
                AuditAction.CANCEL, ENTITY_NAME, id, "Hủy phiếu xuất " + saved.getCode() + ". Lý do: " + reason);
        return toDetail(saved);
    }

    /** Lấy entity cho chức năng in PDF. */
    @Transactional(readOnly = true)
    public StockIssue getEntityForPrint(Long id) {
        StockIssue issue = findOrThrow(id);
        warehouseAccessGuard.requireAccess(issue.getWarehouse().getId());
        issue.getItems().size();
        return issue;
    }

    // ============================== Hàm hỗ trợ ==============================

    private List<StockMovement> buildMovements(StockIssue issue) {
        List<StockMovement> movements = new ArrayList<>();
        for (StockIssueItem item : issue.getItems()) {
            movements.add(new StockMovement(
                    item.getProduct(),
                    issue.getWarehouse(),
                    item.getQuantity(),
                    item.getUnitCost(),
                    RefType.ISSUE,
                    issue.getId(),
                    issue.getCode(),
                    item.getNote(),
                    issue.getDocumentDate().atStartOfDay()));
        }
        return movements;
    }

    private void applyItems(StockIssue issue, List<SaveIssueItemRequest> itemRequests) {
        for (SaveIssueItemRequest itemRequest : itemRequests) {
            Product product = productService.requireSelectableProduct(itemRequest.productId());

            StockIssueItem item = StockIssueItem.builder()
                    .product(product)
                    .quantity(MoneyUtils.quantity(itemRequest.quantity()))
                    .unitCost(BigDecimal.ZERO) // điền khi hoàn tất phiếu
                    .note(emptyToNull(itemRequest.note()))
                    .build();
            item.recalculateAmount();
            issue.addItem(item);
        }
        issue.recalculateTotal();
    }

    private void validatePartnerAndReason(SaveIssueRequest request) {
        IssueType type = request.issueType();
        if (type.isCustomerRequired() && request.customerId() == null) {
            throw new BusinessException("Phiếu xuất bán bắt buộc chọn khách hàng.", "CUSTOMER_REQUIRED");
        }
        if (type.isSupplierRequired() && request.supplierId() == null) {
            throw new BusinessException("Phiếu trả hàng nhà cung cấp bắt buộc chọn nhà cung cấp.", "SUPPLIER_REQUIRED");
        }
        if (type.isReasonRequired() && !StringUtils.hasText(request.reason())) {
            throw new BusinessException(
                    "Phiếu " + type.getDisplayName().toLowerCase() + " bắt buộc ghi lý do.", "REASON_REQUIRED");
        }
        if (request.documentDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Ngày chứng từ không được ở tương lai.", "INVALID_DOCUMENT_DATE");
        }
    }

    private void requireEditable(StockIssue issue) {
        warehouseAccessGuard.requireAccess(issue.getWarehouse().getId());
        AppUserDetails current = SecurityUtils.getCurrentUser();

        if (!issue.getStatus().isEditable()) {
            throw new BusinessException(
                    "Phiếu đang ở trạng thái " + issue.getStatus().getDisplayName()
                            + " nên không sửa được. Chỉ phiếu Nháp hoặc Bị từ chối mới sửa được.",
                    "NOT_EDITABLE");
        }
        boolean isOwner = current.getUserId().equals(issue.getCreatedBy());
        if (!isOwner && !current.isAdmin()) {
            throw new ForbiddenException("Bạn chỉ sửa được phiếu do chính mình lập.");
        }
    }

    private void requireApprovalRight(StockIssue issue) {
        warehouseAccessGuard.requireAccess(issue.getWarehouse().getId());
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (!current.canApprove()) {
            throw new ForbiddenException("Bạn không có quyền duyệt phiếu. Chức năng này dành cho quản lý kho.");
        }
    }

    private void notifyCreator(StockIssue issue, NotificationType type, String title, String content) {
        if (issue.getCreatedBy() != null) {
            notificationService.notifyUser(issue.getCreatedBy(), type, title, content, "/issues/" + issue.getId());
        }
    }

    private List<Product> productsOf(StockIssue issue) {
        return issue.getItems().stream().map(StockIssueItem::getProduct).toList();
    }

    private User currentUserEntity() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("người dùng", userId));
    }

    private StockIssue findOrThrow(Long id) {
        return issueRepository.findWithItemsById(id).orElseThrow(() -> new NotFoundException("phiếu xuất", id));
    }

    // ============================== Chuyển đổi DTO ==============================

    private DocumentSummaryResponse toSummary(StockIssue issue) {
        boolean canViewAmount = canViewAmount(issue);
        String partnerName = issue.getCustomer() != null
                ? issue.getCustomer().getName()
                : (issue.getSupplier() != null ? issue.getSupplier().getName() : null);

        return new DocumentSummaryResponse(
                issue.getId(),
                issue.getCode(),
                DOCUMENT_TYPE_NAME,
                issue.getWarehouse().getId(),
                issue.getWarehouse().getName(),
                issue.getIssueType().name(),
                issue.getIssueType().getDisplayName(),
                issue.getDocumentDate(),
                partnerName,
                issue.getStatus(),
                issue.getStatus().getDisplayName(),
                canViewAmount ? issue.getTotalAmount() : null,
                issue.getItems().size(),
                totalQuantity(issue),
                issue.getCreatedBy(),
                documentNotifier.userDisplayName(issue.getCreatedBy()),
                issue.getApprovedBy() == null ? null : issue.getApprovedBy().getFullName(),
                issue.getCreatedAt(),
                issue.getApprovedAt());
    }

    private DocumentDetailResponse toDetail(StockIssue issue) {
        AppUserDetails current = SecurityUtils.getCurrentUserOrNull();
        boolean canViewAmount = canViewAmount(issue);

        List<DocumentItemResponse> items = issue.getItems().stream()
                .map(item -> new DocumentItemResponse(
                        item.getId(),
                        item.getLineNo(),
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getBarcode(),
                        item.getProduct().getName(),
                        item.getProduct().getUnit().getName(),
                        item.getQuantity(),
                        canViewAmount ? item.getUnitCost() : null,
                        canViewAmount ? item.getAmount() : null,
                        inventoryService.getQuantityOnHand(
                                item.getProduct().getId(), issue.getWarehouse().getId()),
                        item.getNote()))
                .toList();

        boolean isOwner = current != null && current.getUserId().equals(issue.getCreatedBy());
        Long partnerId = issue.getCustomer() != null
                ? issue.getCustomer().getId()
                : (issue.getSupplier() != null ? issue.getSupplier().getId() : null);
        String partnerCode = issue.getCustomer() != null
                ? issue.getCustomer().getCode()
                : (issue.getSupplier() != null ? issue.getSupplier().getCode() : null);
        String partnerName = issue.getCustomer() != null
                ? issue.getCustomer().getName()
                : (issue.getSupplier() != null ? issue.getSupplier().getName() : null);

        return new DocumentDetailResponse(
                issue.getId(),
                issue.getCode(),
                DOCUMENT_TYPE_NAME,
                issue.getWarehouse().getId(),
                issue.getWarehouse().getCode(),
                issue.getWarehouse().getName(),
                issue.getIssueType().name(),
                issue.getIssueType().getDisplayName(),
                issue.getDocumentDate(),
                partnerId,
                partnerCode,
                partnerName,
                issue.getReason(),
                issue.getNote(),
                issue.getStatus(),
                issue.getStatus().getDisplayName(),
                canViewAmount ? issue.getTotalAmount() : null,
                items,
                issue.getCreatedBy(),
                documentNotifier.userDisplayName(issue.getCreatedBy()),
                issue.getCreatedAt(),
                issue.getSubmittedAt(),
                issue.getApprovedBy() == null ? null : issue.getApprovedBy().getFullName(),
                issue.getApprovedAt(),
                issue.getRejectReason(),
                issue.getCancelledBy() == null ? null : issue.getCancelledBy().getFullName(),
                issue.getCancelledAt(),
                issue.getCancelReason(),
                issue.getStatus().isEditable() && (isOwner || (current != null && current.isAdmin())),
                current != null && current.canApprove() && issue.getStatus() == DocumentStatus.PENDING,
                current != null && current.canApprove() && issue.getStatus() == DocumentStatus.COMPLETED);
    }

    /** STAFF chỉ thấy giá vốn trên phiếu do chính mình lập. */
    private boolean canViewAmount(StockIssue issue) {
        AppUserDetails current = SecurityUtils.getCurrentUserOrNull();
        if (current == null) {
            return false;
        }
        return current.canViewCostPrice() || current.getUserId().equals(issue.getCreatedBy());
    }

    private BigDecimal totalQuantity(StockIssue issue) {
        return issue.getItems().stream()
                .map(StockIssueItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
