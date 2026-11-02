package com.smartstock.document;

import com.smartstock.catalog.Customer;
import com.smartstock.catalog.PartnerService;
import com.smartstock.catalog.Product;
import com.smartstock.catalog.Supplier;
import com.smartstock.config.AppProperties;
import com.smartstock.inventory.Inventory;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.mail.MailService;
import com.smartstock.notification.NotificationService;
import com.smartstock.notification.NotificationType;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.warehouse.Warehouse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các việc phụ trợ dùng chung cho phiếu nhập, phiếu xuất và phiếu kiểm kê:
 * gửi thông báo chờ duyệt, cảnh báo tồn thấp, tra cứu đối tác và tên người dùng.
 *
 * <p>Tách riêng để ba service phiếu không phải lặp lại cùng một đoạn mã, và để các
 * service phiếu tập trung vào nghiệp vụ chính của chúng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentNotifier {

    private final UserRepository userRepository;
    private final PartnerService partnerService;
    private final InventoryRepository inventoryRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final AppProperties appProperties;

    /** Bộ nhớ đệm tên người dùng để danh sách phiếu không phải truy vấn lặp lại. */
    private final Map<Long, String> userNameCache = new ConcurrentHashMap<>();

    /**
     * Thông báo "có phiếu chờ duyệt" tới mọi người có quyền duyệt của kho:
     * thông báo trong web (kèm realtime qua SSE) và email.
     *
     * @param link đường dẫn frontend tới phiếu, ví dụ {@code /receipts/12}
     */
    @Transactional
    public void notifyPendingApproval(
            Warehouse warehouse, NotificationType type, String documentTypeName, String documentCode, String link) {

        List<User> approvers = userRepository.findApproversOfWarehouse(warehouse.getId());
        if (approvers.isEmpty()) {
            log.warn("Kho {} chưa có người duyệt nào đang hoạt động", warehouse.getCode());
            return;
        }

        String title = documentTypeName + " chờ duyệt";
        String content = documentTypeName + " " + documentCode + " tại " + warehouse.getName() + " đang chờ duyệt.";

        for (User approver : approvers) {
            notificationService.notifyUser(approver.getId(), type, title, content, link);
            // Gửi email bất đồng bộ: lỗi SMTP không ảnh hưởng tới việc gửi duyệt
            mailService.sendPendingDocumentEmail(
                    approver.getEmail(),
                    approver.getFullName(),
                    documentTypeName,
                    documentCode,
                    warehouse.getName(),
                    appProperties.frontendUrl() + link);
        }
    }

    /**
     * Sau khi tồn kho thay đổi, kiểm tra các sản phẩm vừa biến động xem có xuống dưới
     * mức tồn tối thiểu không; nếu có thì cảnh báo cho người duyệt của kho.
     *
     * <p>Chỉ kiểm tra các sản phẩm vừa thay đổi (không quét toàn kho) để không làm chậm
     * thao tác duyệt phiếu.
     */
    @Transactional
    public void checkLowStockAfterChange(Warehouse warehouse, List<Product> products) {
        List<User> approvers = userRepository.findApproversOfWarehouse(warehouse.getId());
        if (approvers.isEmpty()) {
            return;
        }

        for (Product product : products) {
            if (product.getMinStock() == null || product.getMinStock().signum() <= 0) {
                continue; // sản phẩm không đặt mức tồn tối thiểu thì bỏ qua
            }
            Inventory inventory = inventoryRepository
                    .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                    .orElse(null);
            if (inventory == null || inventory.getQuantity().compareTo(product.getMinStock()) > 0) {
                continue;
            }

            String title = "Cảnh báo tồn thấp";
            String content = String.format(
                    "Sản phẩm %s (%s) tại %s chỉ còn %s %s, bằng hoặc dưới mức tối thiểu %s.",
                    product.getName(),
                    product.getSku(),
                    warehouse.getName(),
                    inventory.getQuantity().stripTrailingZeros().toPlainString(),
                    product.getUnit().getName(),
                    product.getMinStock().stripTrailingZeros().toPlainString());

            for (User approver : approvers) {
                notificationService.notifyUser(
                        approver.getId(),
                        NotificationType.LOW_STOCK,
                        title,
                        content,
                        "/inventory?warehouseId=" + warehouse.getId() + "&stockState=LOW");
            }
        }
    }

    /** Tên người dùng để hiển thị trên phiếu; trả về "-" nếu tài khoản không còn. */
    @Transactional(readOnly = true)
    public String userDisplayName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userNameCache.computeIfAbsent(
                userId, id -> userRepository.findById(id).map(User::getFullName).orElse("-"));
    }

    /** Xóa bộ nhớ đệm tên người dùng (gọi khi cập nhật hồ sơ). */
    public void clearUserNameCache() {
        userNameCache.clear();
    }

    public Supplier requireSupplier(Long id) {
        return partnerService.requireSupplier(id);
    }

    public Customer requireCustomer(Long id) {
        return partnerService.requireCustomer(id);
    }
}
