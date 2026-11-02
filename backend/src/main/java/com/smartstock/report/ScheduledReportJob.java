package com.smartstock.report;

import com.smartstock.auth.PasswordResetTokenRepository;
import com.smartstock.auth.RefreshTokenRepository;
import com.smartstock.inventory.Inventory;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.mail.MailService;
import com.smartstock.mail.MailService.LowStockMailRow;
import com.smartstock.notification.NotificationRepository;
import com.smartstock.setting.SettingKey;
import com.smartstock.setting.SettingService;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.warehouse.Warehouse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các tác vụ chạy theo lịch.
 *
 * <p>Giải thích cho người quen PHP: đây là phần tương đương Laravel Scheduler
 * ({@code app/Console/Kernel.php}) nhưng KHÔNG cần cron của hệ điều hành - Spring tự quản
 * lý bộ hẹn giờ ngay trong tiến trình ứng dụng.
 *
 * <p>Múi giờ được khai báo tường minh là {@code Asia/Ho_Chi_Minh} nên lịch chạy đúng 07:00
 * giờ Việt Nam kể cả khi máy chủ (ví dụ Render) đặt ở múi giờ UTC.
 *
 * <p><b>Lưu ý khi chạy nhiều bản backend song song:</b> mỗi bản sẽ tự chạy lịch của mình
 * nên email có thể bị gửi trùng. Phạm vi đồ án chạy một bản nên chưa cần xử lý; nếu mở
 * rộng thì dùng ShedLock để khóa theo tên tác vụ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledReportJob {

    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final SettingService settingService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Gửi email báo cáo hàng tồn thấp lúc 07:00 hằng ngày.
     *
     * <p>Biểu thức cron có 6 phần: {@code giây phút giờ ngày tháng thứ}. Ở đây
     * {@code "0 0 7 * * *"} nghĩa là 0 giây, 0 phút, 7 giờ, mọi ngày.
     *
     * <p>Mỗi người nhận chỉ thấy hàng tồn thấp của KHO MÌNH PHỤ TRÁCH: quản lý kho Hà Nội
     * không nhận danh sách của kho Đà Nẵng. ADMIN nhận toàn bộ.
     */
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional(readOnly = true)
    public void sendDailyLowStockReport() {
        if (!settingService.getBoolean(SettingKey.LOW_STOCK_REPORT_ENABLED, true)) {
            log.info("Cấu hình đang tắt email báo cáo tồn thấp, bỏ qua tác vụ hôm nay");
            return;
        }

        List<User> recipients = userRepository.findActiveManagersAndAdmins();
        if (recipients.isEmpty()) {
            log.warn("Không có người nhận nào cho báo cáo tồn thấp");
            return;
        }

        String reportDate = VietnameseFormat.date(LocalDate.now());
        int sentCount = 0;

        for (User recipient : recipients) {
            // Xác định phạm vi kho của người nhận
            Set<Long> warehouseIds = recipient.getWarehouses().stream()
                    .map(Warehouse::getId)
                    .collect(Collectors.toSet());
            boolean allWarehouses = recipient.isAdmin();

            if (!allWarehouses && warehouseIds.isEmpty()) {
                continue; // chưa được gán kho nào thì không có gì để báo
            }

            List<Inventory> lowStockItems = inventoryRepository.findLowStock(
                    allWarehouses, allWarehouses ? List.of(-1L) : List.copyOf(warehouseIds));

            if (lowStockItems.isEmpty()) {
                continue; // không có hàng tồn thấp thì không làm phiền người nhận
            }

            List<LowStockMailRow> rows = new ArrayList<>();
            for (Inventory inventory : lowStockItems) {
                rows.add(new LowStockMailRow(
                        inventory.getProduct().getSku(),
                        inventory.getProduct().getName(),
                        inventory.getWarehouse().getName(),
                        VietnameseFormat.quantity(inventory.getQuantity()),
                        VietnameseFormat.quantity(inventory.getProduct().getMinStock()),
                        inventory.getProduct().getUnit().getName()));
            }

            mailService.sendLowStockReportEmail(recipient.getEmail(), recipient.getFullName(), reportDate, rows);
            sentCount++;
        }

        log.info("Đã gửi báo cáo tồn thấp ngày {} cho {} người nhận", reportDate, sentCount);
    }

    /**
     * Dọn dữ liệu hết hạn lúc 03:00 hằng ngày: token đã hết hạn và thông báo cũ đã đọc.
     *
     * <p>Giữ cho các bảng này không phình to vô hạn theo thời gian sử dụng.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanUpExpiredData() {
        LocalDateTime now = LocalDateTime.now();

        int deletedRefreshTokens = refreshTokenRepository.deleteExpired(now);
        int deletedResetTokens = passwordResetTokenRepository.deleteExpired(now);
        // Thông báo đã đọc quá 60 ngày thì không còn giá trị tra cứu
        int deletedNotifications = notificationRepository.deleteOldRead(now.minusDays(60));

        log.info(
                "Dọn dữ liệu hết hạn: {} refresh token, {} mã đặt lại mật khẩu, {} thông báo cũ",
                deletedRefreshTokens,
                deletedResetTokens,
                deletedNotifications);
    }
}
