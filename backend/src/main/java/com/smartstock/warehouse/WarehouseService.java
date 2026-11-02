package com.smartstock.warehouse;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.common.BusinessException;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import com.smartstock.inventory.InventoryRepository;
import com.smartstock.security.AppUserDetails;
import com.smartstock.security.SecurityUtils;
import com.smartstock.user.Role;
import com.smartstock.user.User;
import com.smartstock.user.UserRepository;
import com.smartstock.warehouse.WarehouseDtos.SaveWarehouseRequest;
import com.smartstock.warehouse.WarehouseDtos.WarehouseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ quản lý kho / chi nhánh.
 *
 * <p>Phân quyền: chỉ ADMIN được thêm/sửa/xóa (đặt ở {@code SecurityConfig}). MANAGER và
 * STAFF chỉ xem, và chỉ thấy các kho được gán cho mình.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final String ENTITY_NAME = "Warehouse";

    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditService auditService;

    /**
     * Danh sách kho có phân trang.
     *
     * <p>Kết quả được lọc theo quyền: người không phải ADMIN chỉ thấy kho của mình.
     */
    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> search(String keyword, Boolean active, Pageable pageable) {
        AppUserDetails current = SecurityUtils.getCurrentUser();
        PageResponse<WarehouseResponse> all = PageResponse.from(
                warehouseRepository.search(normalize(keyword), active, pageable), WarehouseResponse::from);

        if (current.isAdmin()) {
            return all;
        }
        // Lọc lại theo kho được gán. Danh mục kho có số lượng nhỏ (vài chục) nên lọc
        // trong bộ nhớ là đủ nhanh và giữ mã đơn giản.
        List<WarehouseResponse> visible = all.content().stream()
                .filter(w -> current.canAccessWarehouse(w.id()))
                .toList();
        return new PageResponse<>(
                visible, all.page(), all.size(), visible.size(), 1, true, true);
    }

    /** Danh sách kho đang hoạt động mà người dùng được phép làm việc (dùng cho bộ chọn kho). */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> listMyWarehouses() {
        AppUserDetails current = SecurityUtils.getCurrentUser();
        List<Warehouse> warehouses = current.isAdmin()
                ? warehouseRepository.findAllByActiveTrueOrderByNameAsc()
                : warehouseRepository.findAssignedWarehouses(current.getUserId());
        return warehouses.stream().map(WarehouseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getById(Long id) {
        Warehouse warehouse = findOrThrow(id);
        AppUserDetails current = SecurityUtils.getCurrentUser();
        if (!current.canAccessWarehouse(id)) {
            throw new com.smartstock.common.ForbiddenException("Bạn không được phân công làm việc tại kho này.");
        }
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    public WarehouseResponse create(SaveWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new BusinessException("Mã kho đã tồn tại.", "WAREHOUSE_CODE_EXISTS");
        }

        Warehouse warehouse = Warehouse.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .address(emptyToNull(request.address()))
                .phone(emptyToNull(request.phone()))
                .manager(resolveManager(request.managerId()))
                .active(!Boolean.FALSE.equals(request.active()))
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);
        auditService.logChange(
                AuditAction.CREATE,
                ENTITY_NAME,
                saved.getId(),
                "Thêm kho " + saved.getCode(),
                null,
                WarehouseResponse.from(saved));
        return WarehouseResponse.from(saved);
    }

    @Transactional
    public WarehouseResponse update(Long id, SaveWarehouseRequest request) {
        Warehouse warehouse = findOrThrow(id);
        WarehouseResponse before = WarehouseResponse.from(warehouse);

        if (warehouseRepository.existsByCodeAndIdNot(request.code().trim().toUpperCase(), id)) {
            throw new BusinessException("Mã kho đã tồn tại.", "WAREHOUSE_CODE_EXISTS");
        }

        warehouse.setCode(request.code().trim().toUpperCase());
        warehouse.setName(request.name().trim());
        warehouse.setAddress(emptyToNull(request.address()));
        warehouse.setPhone(emptyToNull(request.phone()));
        warehouse.setManager(resolveManager(request.managerId()));
        if (request.active() != null) {
            warehouse.setActive(request.active());
        }

        Warehouse saved = warehouseRepository.save(warehouse);
        auditService.logChange(
                AuditAction.UPDATE,
                ENTITY_NAME,
                saved.getId(),
                "Cập nhật kho " + saved.getCode(),
                before,
                WarehouseResponse.from(saved));
        return WarehouseResponse.from(saved);
    }

    /**
     * Ngừng hoạt động một kho.
     *
     * <p>Không xóa cứng vì các phiếu và giao dịch cũ vẫn trỏ tới kho này. Nếu kho còn
     * tồn kho thì chặn hẳn, tránh làm "mất" hàng khỏi mọi báo cáo.
     */
    @Transactional
    public void deactivate(Long id) {
        Warehouse warehouse = findOrThrow(id);

        if (inventoryRepository.existsByWarehouseIdAndQuantityGreaterThan(id, java.math.BigDecimal.ZERO)) {
            throw new BusinessException(
                    "Không thể ngừng hoạt động kho đang còn tồn kho. Vui lòng xuất hết hàng trước.",
                    "WAREHOUSE_HAS_STOCK");
        }

        warehouse.setActive(false);
        warehouseRepository.save(warehouse);
        auditService.log(AuditAction.DELETE, ENTITY_NAME, id, "Ngừng hoạt động kho " + warehouse.getCode());
    }

    /** Người quản lý kho phải là tài khoản có vai trò ADMIN hoặc MANAGER. */
    private User resolveManager(Long managerId) {
        if (managerId == null) {
            return null;
        }
        User manager = userRepository
                .findById(managerId)
                .orElseThrow(() -> new NotFoundException("người quản lý", managerId));
        if (manager.getRole() == Role.STAFF) {
            throw new BusinessException(
                    "Người quản lý kho phải có vai trò Quản trị hệ thống hoặc Quản lý kho.", "INVALID_MANAGER_ROLE");
        }
        return manager;
    }

    private Warehouse findOrThrow(Long id) {
        return warehouseRepository.findById(id).orElseThrow(() -> new NotFoundException("kho", id));
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
