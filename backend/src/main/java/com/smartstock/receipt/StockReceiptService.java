package com.smartstock.receipt;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.catalog.Product;
import com.smartstock.catalog.ProductService;
import com.smartstock.common.BusinessException;
import com.smartstock.common.ForbiddenException;
import com.smartstock.common.MoneyUtils;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import com.smartstock.document.DocumentDtos.DocumentDetailResponse;
import com.smartstock.document.DocumentDtos.DocumentItemResponse;
import com.smartstock.document.DocumentDtos.DocumentSummaryResponse;
import com.smartstock.document.DocumentDtos.SaveReceiptItemRequest;
import com.smartstock.document.DocumentDtos.SaveReceiptRequest;
import com.smartstock.document.DocumentNotifier;
import com.smartstock.document.DocumentSequenceService;
import com.smartstock.document.DocumentStatus;
import com.smartstock.inventory.InventoryService;
import com.smartstock.inventory.InventoryService.StockMovement;
import com.smartstock.inventory.RefType;
import com.smartstock.inventory.StockTransaction;
import com.smartstock.inventory.StockTransactionRepository;
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
 * Nghiệp vụ phiếu nhập kho: lập, sửa nháp, gửi duyệt, duyệt, từ chối, hủy.
 *
 * <p><b>Luồng xử lý một phiếu nhập điển hình:</b>
 *
 * <ol>
 *   <li>Nhân viên lập phiếu (DRAFT) - chưa ảnh hưởng tồn kho.</li>
 *   <li>Gửi duyệt (PENDING) - hệ thống thông báo + gửi email cho quản lý kho đó.</li>
 *   <li>Quản lý duyệt (COMPLETED) - lúc này mới gọi {@code InventoryService.applyReceipt}
 *       để cộng tồn, tính lại giá vốn bình quân và ghi sổ giao dịch kho.</li>
 *   <li>Nếu cần điều chỉnh: hủy phiếu kèm lý do - hệ thống sinh giao dịch đảo ngược.</li>
 * </ol>
 *
 * <p><b>Phân quyền:</b> mọi thao tác đều kiểm tra người dùng có được gán kho của phiếu
 * hay không (qua {@link WarehouseAccessGuard}); việc duyệt/từ chối/hủy thêm yêu cầu vai
 * trò ADMIN hoặc MANAGER.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockReceiptService {

    private static final String ENTITY_NAME = "StockReceipt";
    private static final String DOCUMENT_TYPE_NAME = "Phiếu nhập kho";

    private final StockReceiptRepository receiptRepository;
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

    /** Danh sách phiếu nhập, đã lọc theo quyền truy cập kho của người đăng nhập. */
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> search(
            Long warehouseId,
            ReceiptType receiptType,
            DocumentStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Long createdBy,
            String keyword,
            Pageable pageable) {

        WarehouseScope scope = WarehouseScope.of(warehouseAccessGuard.resolveWarehouseFilter(warehouseId));
        return PageResponse.from(
                receiptRepository.search(
                        scope.all(),
                        scope.ids(),
                        receiptType,
                        status,
                        fromDate,
                        toDate,
                        createdBy,
                        normalize(keyword),
                        pageable),
                this::toSummary);
    }

    /** Chi tiết một phiếu nhập. */
    @Transactional(readOnly = true)
    public DocumentDetailResponse getById(Long id) {
        StockReceipt receipt = findOrThrow(id);
        warehouseAccessGuard.requireAccess(receipt.getWarehouse().getId());
        return toDetail(receipt);
    }

    // ============================== Lập và sửa ==============================

    /**
     * Tạo phiếu nhập mới ở trạng thái nháp.
     *
     * @param submitImmediately true thì gửi duyệt luôn sau khi lưu
     */
    @Transactional
    public DocumentDetailResponse create(SaveReceiptRequest request, boolean submitImmediately) {
        Warehouse warehouse = warehouseAccessGuard.requireAccessibleWarehouse(request.warehouseId());
        validatePartnerAndReason(request);

        StockReceipt receipt = StockReceipt.builder()
                .code(sequenceService.nextCode(DocumentSequenceService.PREFIX_RECEIPT, request.documentDate()))
                .warehouse(warehouse)
                .receiptType(request.receiptType())
                .documentDate(request.documentDate())
                .supplier(request.supplierId() == null ? null : documentNotifier.requireSupplier(request.supplierId()))
                .customer(request.customerId() == null ? null : documentNotifier.requireCustomer(request.customerId()))
                .reason(emptyToNull(request.reason()))
                .note(emptyToNull(request.note()))
                .status(DocumentStatus.DRAFT)
                .build();

        applyItems(receipt, request.items());
        StockReceipt saved = receiptRepository.save(receipt);

        auditService.logChange(
                AuditAction.CREATE, ENTITY_NAME, saved.getId(), "Lập phiếu nhập " + saved.getCode(), null, toDetail(saved));

        if (submitImmediately) {
            return submit(saved.getId());
        }
        return toDetail(saved);
    }

    /** Sửa phiếu. Chỉ phiếu DRAFT hoặc REJECTED mới sửa được, và chỉ người lập mới sửa. */
    @Transactional
    public DocumentDetailResponse update(Long id, SaveReceiptRequest request) {
        StockReceipt receipt = findOrThrow(id);
        requireEditable(receipt);
        validatePartnerAndReason(request);

        DocumentDetailResponse before = toDetail(receipt);
        Warehouse warehouse = warehouseAccessGuard.requireAccessibleWarehouse(request.warehouseId());

        receipt.setWarehouse(warehouse);
        receipt.setReceiptType(request.receiptType());
        receipt.setDocumentDate(request.documentDate());
        receipt.setSupplier(
                request.supplierId() == null ? null : documentNotifier.requireSupplier(request.supplierId()));
        receipt.setCustomer(
                request.customerId() == null ? null : documentNotifier.requireCustomer(request.customerId()));
        receipt.setReason(emptyToNull(request.reason()));
        receipt.setNote(emptyToNull(request.note()));

        // Sửa phiếu = xóa hết dòng cũ rồi thêm lại theo dữ liệu mới.
        // Cách này đơn giản và đúng vì phiếu nháp chưa tác động tồn kho.
        receipt.clearItems();
        applyItems(receipt, request.items());

        // Phiếu bị từ chối, sau khi sửa sẽ quay về nháp để người lập chủ động gửi lại
        if (receipt.getStatus() == DocumentStatus.REJECTED) {
            receipt.setStatus(DocumentStatus.DRAFT);
            receipt.setRejectReason(null);
        }

        StockReceipt saved = receiptRepository.save(receipt);
        auditService.logChange(
                AuditAction.UPDATE, ENTITY_NAME, id, "Sửa phiếu nhập " + saved.getCode(), before, toDetail(saved));
        return toDetail(saved);
    }

    /** Xóa phiếu nháp. Phiếu đã gửi duyệt hoặc hoàn tất thì không xóa được. */
    @Transactional
    public void delete(Long id) {
        StockReceipt receipt = findOrThrow(id);
        requireEditable(receipt);

        String code = receipt.getCode();
        receiptRepository.delete(receipt);
        auditService.log(AuditAction.DELETE, ENTITY_NAME, id, "Xóa phiếu nhập nháp " + code);
    }

    // ============================== Luồng duyệt ==============================

    /** Gửi phiếu lên chờ duyệt, đồng thời thông báo và gửi email cho quản lý kho. */
    @Transactional
    public DocumentDetailResponse submit(Long id) {
        StockReceipt receipt = findOrThrow(id);
        requireEditable(receipt);

        if (receipt.getItems().isEmpty()) {
            throw new BusinessException("Phiếu phải có ít nhất một dòng hàng trước khi gửi duyệt.", "EMPTY_DOCUMENT");
        }

        receipt.setStatus(DocumentStatus.PENDING);
        receipt.setSubmittedAt(LocalDateTime.now());
        receipt.setRejectReason(null);
        StockReceipt saved = receiptRepository.save(receipt);

        documentNotifier.notifyPendingApproval(
                saved.getWarehouse(),
                NotificationType.RECEIPT_PENDING,
                DOCUMENT_TYPE_NAME,
                saved.getCode(),
                "/receipts/" + saved.getId());

        auditService.log(AuditAction.SUBMIT, ENTITY_NAME, id, "Gửi duyệt phiếu nhập " + saved.getCode());
        return toDetail(saved);
    }

    /**
     * DUYỆT phiếu: đây là bước duy nhất làm thay đổi tồn kho.
     *
     * <p>Các việc được thực hiện trong CÙNG MỘT giao dịch - hoặc tất cả thành công, hoặc
     * không có gì xảy ra:
     *
     * <ol>
     *   <li>Cộng tồn và cập nhật giá vốn bình quân cho từng dòng.</li>
     *   <li>Ghi một dòng vào sổ giao dịch kho cho mỗi dòng phiếu.</li>
     *   <li>Đổi trạng thái phiếu sang COMPLETED, ghi người duyệt và thời điểm duyệt.</li>
     *   <li>Thông báo cho người lập phiếu.</li>
     * </ol>
     */
    @Transactional
    public DocumentDetailResponse approve(Long id) {
        StockReceipt receipt = findOrThrow(id);
        requireApprovalRight(receipt);

        if (receipt.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException(
                    "Chỉ duyệt được phiếu đang ở trạng thái Chờ duyệt. Phiếu này đang ở trạng thái "
                            + receipt.getStatus().getDisplayName() + ".",
                    "INVALID_STATUS");
        }

        // Dựng danh sách thay đổi tồn rồi giao cho InventoryService thực hiện.
        // Service đó lo khóa dòng, cộng tồn, tính giá vốn và ghi sổ giao dịch.
        List<StockMovement> movements = new ArrayList<>();
        for (StockReceiptItem item : receipt.getItems()) {
            movements.add(new StockMovement(
                    item.getProduct(),
                    receipt.getWarehouse(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    RefType.RECEIPT,
                    receipt.getId(),
                    receipt.getCode(),
                    item.getNote(),
                    receipt.getDocumentDate().atStartOfDay()));
        }
        inventoryService.applyReceipt(movements);

        User approver = currentUserEntity();
        receipt.setStatus(DocumentStatus.COMPLETED);
        receipt.setApprovedBy(approver);
        receipt.setApprovedAt(LocalDateTime.now());
        StockReceipt saved = receiptRepository.save(receipt);

        notifyCreator(
                saved,
                NotificationType.DOCUMENT_APPROVED,
                "Phiếu nhập đã được duyệt",
                "Phiếu nhập " + saved.getCode() + " đã được " + approver.getFullName() + " duyệt. Tồn kho đã cập nhật.");

        // Sau khi nhập hàng, tồn có thể vẫn dưới mức tối thiểu -> kiểm tra lại để cảnh báo
        documentNotifier.checkLowStockAfterChange(saved.getWarehouse(), productsOf(saved));

        auditService.log(AuditAction.APPROVE, ENTITY_NAME, id, "Duyệt phiếu nhập " + saved.getCode());
        return toDetail(saved);
    }

    /** Từ chối phiếu kèm lý do; phiếu quay lại cho người lập sửa và gửi lại. */
    @Transactional
    public DocumentDetailResponse reject(Long id, String reason) {
        StockReceipt receipt = findOrThrow(id);
        requireApprovalRight(receipt);

        if (receipt.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Chỉ từ chối được phiếu đang ở trạng thái Chờ duyệt.", "INVALID_STATUS");
        }

        receipt.setStatus(DocumentStatus.REJECTED);
        receipt.setRejectReason(reason.trim());
        StockReceipt saved = receiptRepository.save(receipt);

        notifyCreator(
                saved,
                NotificationType.DOCUMENT_REJECTED,
                "Phiếu nhập bị từ chối",
                "Phiếu nhập " + saved.getCode() + " bị từ chối. Lý do: " + reason.trim());

        auditService.log(
                AuditAction.REJECT, ENTITY_NAME, id, "Từ chối phiếu nhập " + saved.getCode() + ". Lý do: " + reason);
        return toDetail(saved);
    }

    /**
     * HỦY phiếu đã hoàn tất, kèm lý do bắt buộc.
     *
     * <p>Hệ thống không sửa và không xóa giao dịch cũ, mà ghi thêm giao dịch ĐẢO NGƯỢC.
     * Vì hủy phiếu nhập làm GIẢM tồn, hệ thống kiểm tra tồn còn đủ để trừ lại - nếu hàng
     * đã xuất đi thì chặn và yêu cầu xử lý bằng phiếu xuất điều chỉnh.
     */
    @Transactional
    public DocumentDetailResponse cancel(Long id, String reason) {
        StockReceipt receipt = findOrThrow(id);
        requireApprovalRight(receipt);

        if (receipt.getStatus() != DocumentStatus.COMPLETED) {
            throw new BusinessException(
                    "Chỉ hủy được phiếu đã hoàn tất. Phiếu nháp hoặc chờ duyệt thì xóa hoặc từ chối.",
                    "INVALID_STATUS");
        }

        List<StockTransaction> originals =
                stockTransactionRepository.findByRefTypeAndRefIdOrderByIdAsc(RefType.RECEIPT, receipt.getId()).stream()
                        .filter(t -> t.getType() == com.smartstock.inventory.TransactionType.RECEIPT)
                        .toList();

        if (originals.isEmpty()) {
            throw new BusinessException(
                    "Không tìm thấy giao dịch kho của phiếu này nên không thể hủy.", "NO_TRANSACTIONS");
        }

        // InventoryService kiểm tra đủ tồn để trừ lại và ném lỗi rõ ràng nếu thiếu
        inventoryService.reverseTransactions(originals, reason);

        User canceller = currentUserEntity();
        receipt.setStatus(DocumentStatus.CANCELLED);
        receipt.setCancelledBy(canceller);
        receipt.setCancelledAt(LocalDateTime.now());
        receipt.setCancelReason(reason.trim());
        StockReceipt saved = receiptRepository.save(receipt);

        notifyCreator(
                saved,
                NotificationType.DOCUMENT_CANCELLED,
                "Phiếu nhập đã bị hủy",
                "Phiếu nhập " + saved.getCode() + " đã bị hủy. Lý do: " + reason.trim());

        auditService.log(
                AuditAction.CANCEL, ENTITY_NAME, id, "Hủy phiếu nhập " + saved.getCode() + ". Lý do: " + reason);
        return toDetail(saved);
    }

    /** Lấy entity cho chức năng in PDF. */
    @Transactional(readOnly = true)
    public StockReceipt getEntityForPrint(Long id) {
        StockReceipt receipt = findOrThrow(id);
        warehouseAccessGuard.requireAccess(receipt.getWarehouse().getId());
        receipt.getItems().size(); // chạm vào danh sách để nạp dữ liệu trước khi đóng giao dịch
        return receipt;
    }

    // ============================== Hàm hỗ trợ ==============================

    /** Dựng lại danh sách dòng hàng từ dữ liệu gửi lên, có kiểm tra sản phẩm hợp lệ. */
    private void applyItems(StockReceipt receipt, List<SaveReceiptItemRequest> itemRequests) {
        for (SaveReceiptItemRequest itemRequest : itemRequests) {
            Product product = productService.requireSelectableProduct(itemRequest.productId());

            StockReceiptItem item = StockReceiptItem.builder()
                    .product(product)
                    .quantity(MoneyUtils.quantity(itemRequest.quantity()))
                    .unitPrice(MoneyUtils.money(itemRequest.unitPrice()))
                    .note(emptyToNull(itemRequest.note()))
                    .build();
            item.recalculateAmount();
            receipt.addItem(item);
        }
        receipt.recalculateTotal();
    }

    /** Kiểm tra ràng buộc đối tác / lý do theo từng loại phiếu nhập. */
    private void validatePartnerAndReason(SaveReceiptRequest request) {
        ReceiptType type = request.receiptType();
        if (type.isSupplierRequired() && request.supplierId() == null) {
            throw new BusinessException(
                    "Phiếu nhập mua bắt buộc chọn nhà cung cấp.", "SUPPLIER_REQUIRED");
        }
        if (type.isCustomerRequired() && request.customerId() == null) {
            throw new BusinessException(
                    "Phiếu nhập hàng khách trả lại bắt buộc chọn khách hàng.", "CUSTOMER_REQUIRED");
        }
        if (type.isReasonRequired() && !StringUtils.hasText(request.reason())) {
            throw new BusinessException("Phiếu nhập khác bắt buộc ghi lý do.", "REASON_REQUIRED");
        }
        if (request.documentDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Ngày chứng từ không được ở tương lai.", "INVALID_DOCUMENT_DATE");
        }
    }

    /**
     * Chỉ người LẬP phiếu mới sửa được, và chỉ khi phiếu đang ở trạng thái sửa được.
     * ADMIN được sửa mọi phiếu nháp để hỗ trợ khi nhân viên nghỉ việc.
     */
    private void requireEditable(StockReceipt receipt) {
        warehouseAccessGuard.requireAccess(receipt.getWarehouse().getId());
        AppUserDetails current = SecurityUtils.getCurrentUser();

        if (!receipt.getStatus().isEditable()) {
            throw new BusinessException(
                    "Phiếu đang ở trạng thái " + receipt.getStatus().getDisplayName()
                            + " nên không sửa được. Chỉ phiếu Nháp hoặc Bị từ chối mới sửa được.",
                    "NOT_EDITABLE");
        }
        boolean isOwner = current.getUserId().equals(receipt.getCreatedBy());
        if (!isOwner && !current.isAdmin()) {
            throw new ForbiddenException("Bạn chỉ sửa được phiếu do chính mình lập.");
        }
    }

    /** Duyệt / từ chối / hủy: cần vai trò ADMIN hoặc MANAGER và được gán kho của phiếu. */
    private void requireApprovalRight(StockReceipt receipt) {
        warehouseAccessGuard.requireAccess(receipt.getWarehouse().getId());
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (!current.canApprove()) {
            throw new ForbiddenException("Bạn không có quyền duyệt phiếu. Chức năng này dành cho quản lý kho.");
        }
    }

    private void notifyCreator(StockReceipt receipt, NotificationType type, String title, String content) {
        if (receipt.getCreatedBy() != null) {
            notificationService.notifyUser(
                    receipt.getCreatedBy(), type, title, content, "/receipts/" + receipt.getId());
        }
    }

    private List<Product> productsOf(StockReceipt receipt) {
        return receipt.getItems().stream().map(StockReceiptItem::getProduct).toList();
    }

    private User currentUserEntity() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("người dùng", userId));
    }

    private StockReceipt findOrThrow(Long id) {
        return receiptRepository
                .findWithItemsById(id)
                .orElseThrow(() -> new NotFoundException("phiếu nhập", id));
    }

    // ============================== Chuyển đổi DTO ==============================

    private DocumentSummaryResponse toSummary(StockReceipt receipt) {
        boolean canViewAmount = canViewAmount(receipt);
        String partnerName = receipt.getSupplier() != null
                ? receipt.getSupplier().getName()
                : (receipt.getCustomer() != null ? receipt.getCustomer().getName() : null);

        return new DocumentSummaryResponse(
                receipt.getId(),
                receipt.getCode(),
                DOCUMENT_TYPE_NAME,
                receipt.getWarehouse().getId(),
                receipt.getWarehouse().getName(),
                receipt.getReceiptType().name(),
                receipt.getReceiptType().getDisplayName(),
                receipt.getDocumentDate(),
                partnerName,
                receipt.getStatus(),
                receipt.getStatus().getDisplayName(),
                canViewAmount ? receipt.getTotalAmount() : null,
                receipt.getItems().size(),
                totalQuantity(receipt),
                receipt.getCreatedBy(),
                documentNotifier.userDisplayName(receipt.getCreatedBy()),
                receipt.getApprovedBy() == null ? null : receipt.getApprovedBy().getFullName(),
                receipt.getCreatedAt(),
                receipt.getApprovedAt());
    }

    private DocumentDetailResponse toDetail(StockReceipt receipt) {
        AppUserDetails current = SecurityUtils.getCurrentUserOrNull();
        boolean canViewAmount = canViewAmount(receipt);

        List<DocumentItemResponse> items = receipt.getItems().stream()
                .map(item -> new DocumentItemResponse(
                        item.getId(),
                        item.getLineNo(),
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getBarcode(),
                        item.getProduct().getName(),
                        item.getProduct().getUnit().getName(),
                        item.getQuantity(),
                        canViewAmount ? item.getUnitPrice() : null,
                        canViewAmount ? item.getAmount() : null,
                        inventoryService.getQuantityOnHand(
                                item.getProduct().getId(), receipt.getWarehouse().getId()),
                        item.getNote()))
                .toList();

        boolean isOwner = current != null && current.getUserId().equals(receipt.getCreatedBy());
        boolean canApprove = current != null && current.canApprove() && receipt.getStatus() == DocumentStatus.PENDING;

        Long partnerId = receipt.getSupplier() != null
                ? receipt.getSupplier().getId()
                : (receipt.getCustomer() != null ? receipt.getCustomer().getId() : null);
        String partnerCode = receipt.getSupplier() != null
                ? receipt.getSupplier().getCode()
                : (receipt.getCustomer() != null ? receipt.getCustomer().getCode() : null);
        String partnerName = receipt.getSupplier() != null
                ? receipt.getSupplier().getName()
                : (receipt.getCustomer() != null ? receipt.getCustomer().getName() : null);

        return new DocumentDetailResponse(
                receipt.getId(),
                receipt.getCode(),
                DOCUMENT_TYPE_NAME,
                receipt.getWarehouse().getId(),
                receipt.getWarehouse().getCode(),
                receipt.getWarehouse().getName(),
                receipt.getReceiptType().name(),
                receipt.getReceiptType().getDisplayName(),
                receipt.getDocumentDate(),
                partnerId,
                partnerCode,
                partnerName,
                receipt.getReason(),
                receipt.getNote(),
                receipt.getStatus(),
                receipt.getStatus().getDisplayName(),
                canViewAmount ? receipt.getTotalAmount() : null,
                items,
                receipt.getCreatedBy(),
                documentNotifier.userDisplayName(receipt.getCreatedBy()),
                receipt.getCreatedAt(),
                receipt.getSubmittedAt(),
                receipt.getApprovedBy() == null ? null : receipt.getApprovedBy().getFullName(),
                receipt.getApprovedAt(),
                receipt.getRejectReason(),
                receipt.getCancelledBy() == null ? null : receipt.getCancelledBy().getFullName(),
                receipt.getCancelledAt(),
                receipt.getCancelReason(),
                receipt.getStatus().isEditable() && (isOwner || (current != null && current.isAdmin())),
                canApprove,
                current != null && current.canApprove() && receipt.getStatus() == DocumentStatus.COMPLETED);
    }

    /**
     * STAFF không được xem giá nhập và thành tiền, TRỪ phiếu do chính họ lập
     * (theo quy định phân quyền ở mục 3 của đặc tả).
     */
    private boolean canViewAmount(StockReceipt receipt) {
        AppUserDetails current = SecurityUtils.getCurrentUserOrNull();
        if (current == null) {
            return false;
        }
        return current.canViewCostPrice() || current.getUserId().equals(receipt.getCreatedBy());
    }

    private BigDecimal totalQuantity(StockReceipt receipt) {
        return receipt.getItems().stream()
                .map(StockReceiptItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
