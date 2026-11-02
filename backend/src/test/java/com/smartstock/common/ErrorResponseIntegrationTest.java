package com.smartstock.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartstock.support.TestProfileResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test tầng web cho việc xử lý lỗi tập trung.
 *
 * <p>Lý do có bộ test này: một đường dẫn gõ sai từng trả về <b>500 Internal Server Error</b>. Từ
 * Spring 6, khi không controller nào khớp, request rơi xuống bộ xử lý tài nguyên tĩnh và bộ này ném
 * {@code NoResourceFoundException} - không phải {@code NoHandlerFoundException} như các phiên bản
 * trước. Vì {@code GlobalExceptionHandler} chỉ bắt loại thứ hai, mọi URL sai đều rơi vào lưới an
 * toàn cuối cùng: người dùng tưởng hệ thống sập, còn log thì đầy dòng ERROR vô nghĩa khiến lỗi thật
 * bị lẫn vào.
 *
 * <p>Những test này khóa lại hành vi đúng để lỗi đó không quay lại.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver.class)
@DisplayName("Xử lý lỗi ở tầng web")
class ErrorResponseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Đường dẫn không tồn tại trả 404, không phải 500")
    void duongDanKhongTonTai_tra404() throws Exception {
        mockMvc.perform(get("/api/khong-ton-tai"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy đường dẫn yêu cầu."));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Đường dẫn thiếu id (/api/products/) trả 404, không phải 500")
    void duongDanThieuId_tra404() throws Exception {
        // Trường hợp thực tế: biến id ở phía gọi bị rỗng nên URL thành "/api/products/"
        mockMvc.perform(delete("/api/products/")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Id không phải số trả 400 kèm thông điệp tiếng Việt")
    void idKhongPhaiSo_tra400() throws Exception {
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Yêu cầu không hợp lệ hoặc thiếu dữ liệu bắt buộc."));
    }

    @Test
    @WithMockUser(username = "nhanvien", roles = "STAFF")
    @DisplayName("Thiếu tham số bắt buộc trả 400, không phải 500")
    void thieuThamSoBatBuoc_tra400() throws Exception {
        // /api/inventory/stock-card bắt buộc có productId và warehouseId
        mockMvc.perform(get("/api/inventory/stock-card")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chưa đăng nhập trả 401 và không tiết lộ chi tiết kỹ thuật")
    void chuaDangNhap_tra401() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn chưa đăng nhập hoặc phiên đã hết hạn."));
    }
}
