package com.smartstock.security;

import com.smartstock.common.ForbiddenException;
import com.smartstock.warehouse.Warehouse;
import com.smartstock.warehouse.WarehouseRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra phân quyền THEO KHO ở tầng service.
 *
 * <p>Vì sao cần tách riêng: {@code @PreAuthorize("hasRole('MANAGER')")} chỉ trả lời được
 * "có phải quản lý không", chứ không trả lời được "có phải quản lý CỦA KHO NÀY không".
 * Backend không bao giờ tin tham số kho do frontend gửi lên, nên mọi service thao tác
 * trên một kho đều phải gọi {@link #requireAccess(Long)} trước.
 */
@Component
@RequiredArgsConstructor
public class WarehouseAccessGuard {

    private final WarehouseRepository warehouseRepository;

    /**
     * Chặn nếu người đang đăng nhập không được gán kho này.
     *
     * @throws ForbiddenException khi không có quyền với kho
     */
    public void requireAccess(Long warehouseId) {
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (!current.canAccessWarehouse(warehouseId)) {
            throw new ForbiddenException("Bạn không được phân công làm việc tại kho này.");
        }
    }

    /**
     * Danh sách id kho mà người đang đăng nhập được xem.
     *
     * @return rỗng nghĩa là "mọi kho" (trường hợp ADMIN)
     */
    @Transactional(readOnly = true)
    public Set<Long> accessibleWarehouseIds() {
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (current.isAdmin()) {
            return Set.of();
        }
        return current.getWarehouseIds();
    }

    /**
     * Chuẩn hóa tham số lọc theo kho cho các API danh sách/báo cáo.
     *
     * <p>Nếu người dùng yêu cầu một kho cụ thể thì kiểm tra quyền rồi trả về chính kho đó.
     * Nếu không chỉ định kho: ADMIN được xem tất cả (trả về danh sách rỗng = không lọc),
     * còn MANAGER/STAFF bị giới hạn trong các kho được gán.
     */
    public List<Long> resolveWarehouseFilter(Long requestedWarehouseId) {
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (requestedWarehouseId != null) {
            requireAccess(requestedWarehouseId);
            return List.of(requestedWarehouseId);
        }
        if (current.isAdmin()) {
            return List.of();
        }
        if (current.getWarehouseIds().isEmpty()) {
            // Tài khoản chưa được gán kho nào: không thấy dữ liệu nào thay vì thấy tất cả
            throw new ForbiddenException(
                    "Tài khoản của bạn chưa được gán kho nào. Vui lòng liên hệ quản trị viên.");
        }
        return List.copyOf(current.getWarehouseIds());
    }

    /** Lấy entity kho kèm kiểm tra quyền truy cập. */
    @Transactional(readOnly = true)
    public Warehouse requireAccessibleWarehouse(Long warehouseId) {
        requireAccess(warehouseId);
        return warehouseRepository
                .findById(warehouseId)
                .orElseThrow(() -> new com.smartstock.common.NotFoundException("kho", warehouseId));
    }
}
