package com.smartstock.user;

import com.smartstock.user.UserDtos.UpdateProfileRequest;
import com.smartstock.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API hồ sơ cá nhân: mọi vai trò đều gọi được, nhưng chỉ tác động lên chính mình.
 *
 * <p>Tách khỏi {@code UserController} vì {@code /api/users/**} bị giới hạn cho ADMIN.
 * Người dùng không đổi được vai trò hay kho phụ trách của bản thân.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "03. Hồ sơ cá nhân", description = "Xem và cập nhật thông tin của chính mình")
public class ProfileController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Xem hồ sơ cá nhân")
    public UserResponse getMyProfile() {
        return userService.getMyProfile();
    }

    @PutMapping
    @Operation(summary = "Cập nhật hồ sơ cá nhân", description = "Họ tên, email, số điện thoại, ảnh đại diện")
    public UserResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMyProfile(request);
    }
}
