package com.smartstock.user;

import com.smartstock.auth.AuthDtos.MessageResponse;
import com.smartstock.common.PageResponse;
import com.smartstock.user.UserDtos.CreateUserRequest;
import com.smartstock.user.UserDtos.ResetUserPasswordRequest;
import com.smartstock.user.UserDtos.UpdateUserRequest;
import com.smartstock.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý người dùng - chỉ ADMIN.
 *
 * <p>Phân quyền được đặt ở hai nơi (phòng thủ nhiều lớp):
 *
 * <ul>
 *   <li>{@code SecurityConfig}: chặn theo đường dẫn {@code /api/users/**}.</li>
 *   <li>{@code @PreAuthorize} trên lớp này: chặn theo vai trò ở tầng phương thức.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "02. Người dùng", description = "Quản lý tài khoản, vai trò và kho được gán (chỉ ADMIN)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách người dùng", description = "Phân trang, tìm kiếm và lọc phía server")
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.search(keyword, role, status, warehouseId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một tài khoản")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tài khoản mới", description = "Hệ thống nội bộ: không cho tự đăng ký")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật tài khoản", description = "Đổi được họ tên, email, vai trò và kho được gán")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PutMapping("/{id}/lock")
    @Operation(
            summary = "Khóa tài khoản",
            description = "Không xóa tài khoản để giữ lịch sử phiếu. Khóa sẽ thu hồi mọi phiên đăng nhập.")
    public UserResponse lock(@PathVariable Long id) {
        return userService.setLocked(id, true);
    }

    @PutMapping("/{id}/unlock")
    @Operation(summary = "Mở khóa tài khoản")
    public UserResponse unlock(@PathVariable Long id) {
        return userService.setLocked(id, false);
    }

    @PutMapping("/{id}/reset-password")
    @Operation(summary = "Đặt lại mật khẩu cho người dùng")
    public MessageResponse resetPassword(
            @PathVariable Long id, @Valid @RequestBody ResetUserPasswordRequest request) {
        userService.resetPassword(id, request);
        return new MessageResponse("Đã đặt lại mật khẩu. Người dùng cần đổi mật khẩu khi đăng nhập lần tới.");
    }
}
