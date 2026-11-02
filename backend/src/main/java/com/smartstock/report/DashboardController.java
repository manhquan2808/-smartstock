package com.smartstock.report;

import com.smartstock.report.ReportDtos.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API dashboard, đặt ở đường dẫn riêng {@code /api/dashboard} cho frontend gọi gọn hơn.
 *
 * <p>Cùng dữ liệu với {@code /api/reports/dashboard}; giữ cả hai để tài liệu API rõ ràng
 * theo từng phân hệ.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "11. Báo cáo", description = "Dashboard, báo cáo nhập-xuất-tồn, hàng tồn lâu, gợi ý nhập hàng")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Số liệu dashboard theo kho đang chọn")
    public DashboardResponse dashboard(@RequestParam(required = false) Long warehouseId) {
        return dashboardService.build(warehouseId);
    }
}
