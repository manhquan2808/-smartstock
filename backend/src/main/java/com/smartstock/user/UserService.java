package com.smartstock.user;

import com.smartstock.audit.AuditAction;
import com.smartstock.audit.AuditService;
import com.smartstock.auth.RefreshTokenRepository;
import com.smartstock.common.BusinessException;
import com.smartstock.common.NotFoundException;
import com.smartstock.common.PageResponse;
import com.smartstock.security.SecurityUtils;
import com.smartstock.user.UserDtos.CreateUserRequest;
import com.smartstock.user.UserDtos.ResetUserPasswordRequest;
import com.smartstock.user.UserDtos.UpdateProfileRequest;
import com.smartstock.user.UserDtos.UpdateUserRequest;
import com.smartstock.user.UserDtos.UserResponse;
import com.smartstock.warehouse.Warehouse;
import com.smartstock.warehouse.WarehouseRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ quản lý người dùng.
 *
 * <p>Các quy tắc nghiệp vụ được bảo đảm ở đây:
 *
 * <ul>
 *   <li>Tên đăng nhập và email không được trùng.</li>
 *   <li>MANAGER và STAFF buộc phải được gán ít nhất một kho, còn ADMIN thì không cần
 *       (ADMIN mặc định thấy mọi kho).</li>
 *   <li>Không xóa tài khoản - chỉ khóa, để phiếu cũ vẫn truy được người lập.</li>
 *   <li>Không ai tự khóa chính mình, và phải còn ít nhất một ADMIN đang hoạt động.</li>
 *   <li>Khi khóa tài khoản, thu hồi toàn bộ refresh token để người đó bị đăng xuất ngay.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String ENTITY_NAME = "User";

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Danh sách người dùng, phân trang và lọc phía server. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(
            String keyword, Role role, UserStatus status, Long warehouseId, Pageable pageable) {
        Page<User> page = userRepository.search(normalize(keyword), role, status, warehouseId, pageable);
        return PageResponse.from(page, UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findUserOrThrow(id));
    }

    /** Tạo tài khoản mới. Mật khẩu được băm BCrypt trước khi lưu. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("Tên đăng nhập đã được sử dụng.", "USERNAME_EXISTS");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("Email đã được sử dụng cho tài khoản khác.", "EMAIL_EXISTS");
        }

        Set<Warehouse> warehouses = resolveWarehouses(request.role(), request.warehouseIds());

        User user = User.builder()
                .username(request.username().trim())
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phone(emptyToNull(request.phone()))
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .mustChangePassword(Boolean.TRUE.equals(request.mustChangePassword()))
                .warehouses(warehouses)
                .build();

        User saved = userRepository.save(user);
        auditService.logChange(
                AuditAction.CREATE,
                ENTITY_NAME,
                saved.getId(),
                "Tạo tài khoản " + saved.getUsername(),
                null,
                UserResponse.from(saved));
        return UserResponse.from(saved);
    }

    /** Cập nhật thông tin, vai trò và danh sách kho của một tài khoản. */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findUserOrThrow(id);
        UserResponse before = UserResponse.from(user);

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new BusinessException("Email đã được sử dụng cho tài khoản khác.", "EMAIL_EXISTS");
        }

        // Hạ quyền ADMIN cuối cùng sẽ khiến không còn ai quản trị được hệ thống
        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN) {
            requireAnotherActiveAdminExists(user.getId());
        }

        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName().trim());
        user.setPhone(emptyToNull(request.phone()));
        user.setRole(request.role());
        user.setWarehouses(resolveWarehouses(request.role(), request.warehouseIds()));

        User saved = userRepository.save(user);
        auditService.logChange(
                AuditAction.UPDATE,
                ENTITY_NAME,
                saved.getId(),
                "Cập nhật tài khoản " + saved.getUsername(),
                before,
                UserResponse.from(saved));
        return UserResponse.from(saved);
    }

    /**
     * Khóa hoặc mở khóa tài khoản.
     *
     * @param locked true = khóa, false = mở khóa
     */
    @Transactional
    public UserResponse setLocked(Long id, boolean locked) {
        User user = findUserOrThrow(id);

        if (locked && id.equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("Bạn không thể tự khóa tài khoản của mình.", "CANNOT_LOCK_SELF");
        }
        if (locked && user.getRole() == Role.ADMIN) {
            requireAnotherActiveAdminExists(user.getId());
        }

        UserStatus before = user.getStatus();
        user.setStatus(locked ? UserStatus.LOCKED : UserStatus.ACTIVE);
        if (!locked) {
            user.setFailedLoginAttempts(0);
        }
        User saved = userRepository.save(user);

        if (locked) {
            // Thu hồi mọi phiên đang mở để người bị khóa không dùng tiếp token cũ
            refreshTokenRepository.revokeAllByUser(id, LocalDateTime.now());
        }

        auditService.logChange(
                locked ? AuditAction.LOCK_USER : AuditAction.UNLOCK_USER,
                ENTITY_NAME,
                id,
                (locked ? "Khóa" : "Mở khóa") + " tài khoản " + user.getUsername(),
                before,
                saved.getStatus());
        return UserResponse.from(saved);
    }

    /** ADMIN đặt lại mật khẩu cho người dùng (dùng khi nhân viên quên mật khẩu và không truy cập được email). */
    @Transactional
    public void resetPassword(Long id, ResetUserPasswordRequest request) {
        User user = findUserOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Mặc định buộc đổi mật khẩu ở lần đăng nhập kế tiếp
        user.setMustChangePassword(!Boolean.FALSE.equals(request.mustChangePassword()));
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUser(id, LocalDateTime.now());
        auditService.log(
                AuditAction.RESET_USER_PASSWORD,
                ENTITY_NAME,
                id,
                "Đặt lại mật khẩu cho tài khoản " + user.getUsername());
    }

    /** Người dùng tự cập nhật hồ sơ cá nhân. Vai trò và kho phụ trách chỉ xem, không sửa. */
    @Transactional
    public UserResponse updateMyProfile(UpdateProfileRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = findUserOrThrow(currentUserId);
        UserResponse before = UserResponse.from(user);

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), currentUserId)) {
            throw new BusinessException("Email đã được sử dụng cho tài khoản khác.", "EMAIL_EXISTS");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(emptyToNull(request.phone()));
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(emptyToNull(request.avatarUrl()));
        }

        User saved = userRepository.save(user);
        auditService.logChange(
                AuditAction.UPDATE, ENTITY_NAME, saved.getId(), "Cập nhật hồ sơ cá nhân", before, UserResponse.from(saved));
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getMyProfile() {
        return UserResponse.from(findUserOrThrow(SecurityUtils.getCurrentUserId()));
    }

    /**
     * Kiểm tra: phải tồn tại ít nhất một ADMIN đang hoạt động KHÁC người đang bị tác động.
     * Ngăn tình huống khóa/hạ quyền hết ADMIN rồi không ai vào quản trị được nữa.
     */
    private void requireAnotherActiveAdminExists(Long excludedUserId) {
        boolean hasOtherAdmin = userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() == Role.ADMIN
                        && u.getStatus() == UserStatus.ACTIVE
                        && !u.getId().equals(excludedUserId));
        if (!hasOtherAdmin) {
            throw new BusinessException(
                    "Hệ thống phải có ít nhất một tài khoản quản trị đang hoạt động.", "LAST_ACTIVE_ADMIN");
        }
    }

    /**
     * Chuyển danh sách id kho thành tập entity, kèm kiểm tra nghiệp vụ.
     *
     * <p>ADMIN không cần gán kho (mặc định thấy tất cả), nên nếu có gửi lên cũng bỏ qua.
     */
    private Set<Warehouse> resolveWarehouses(Role role, Set<Long> warehouseIds) {
        if (role == Role.ADMIN) {
            return new HashSet<>();
        }
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            throw new BusinessException(
                    "Vui lòng gán ít nhất một kho cho vai trò " + role.getDisplayName() + ".", "WAREHOUSE_REQUIRED");
        }
        List<Warehouse> found = warehouseRepository.findAllById(warehouseIds);
        if (found.size() != warehouseIds.size()) {
            throw new BusinessException("Danh sách kho được gán có mã không tồn tại.", "WAREHOUSE_NOT_FOUND");
        }
        return new HashSet<>(found);
    }

    private User findUserOrThrow(Long id) {
        return userRepository
                .findByIdWithWarehouses(id)
                .orElseThrow(() -> new NotFoundException("tài khoản", id));
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
