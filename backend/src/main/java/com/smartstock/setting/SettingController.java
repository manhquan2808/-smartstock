package com.smartstock.setting;

import com.smartstock.setting.SettingService.SettingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API cấu hình hệ thống.
 *
 * <p>Ai cũng ĐỌC được (trang gợi ý nhập hàng cần biết thời gian chờ hàng để hiển thị
 * công thức), nhưng chỉ ADMIN mới SỬA được.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "14. Cấu hình hệ thống", description = "Tham số dùng cho gợi ý nhập hàng và báo cáo")
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    @Operation(summary = "Danh sách tham số cấu hình")
    public List<SettingResponse> listAll() {
        return settingService.listAll();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Cập nhật tham số (chỉ ADMIN)",
            description = "Gửi lên đối tượng dạng {\"LEAD_TIME_DAYS\": \"10\", \"SAFETY_STOCK_DAYS\": \"20\"}")
    public List<SettingResponse> update(@RequestBody Map<String, String> values) {
        return settingService.updateAll(values);
    }
}
