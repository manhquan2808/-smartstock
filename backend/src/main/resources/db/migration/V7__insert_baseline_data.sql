-- ======================================================================
-- V7: Dữ liệu nền tối thiểu để hệ thống chạy được trên CSDL trống.
--
-- Khác với dữ liệu DEMO (150 sản phẩm, 300+ phiếu - do lớp DemoDataSeeder
-- tạo ở profile `demo`), migration này chỉ tạo:
--   * các tham số cấu hình mặc định,
--   * một tài khoản ADMIN để đăng nhập lần đầu.
-- ======================================================================

-- ----------------------------------------------------------------------
-- Tham số cấu hình mặc định (ADMIN sửa được trên giao diện)
-- ----------------------------------------------------------------------
INSERT INTO app_settings (setting_key, setting_value, value_type, description, updated_at) VALUES
    ('LEAD_TIME_DAYS',          '7',     'INTEGER', 'Thời gian chờ hàng về (ngày) - dùng cho gợi ý nhập hàng', NOW(6)),
    ('SAFETY_STOCK_DAYS',       '14',    'INTEGER', 'Số ngày dự trữ an toàn - dùng cho gợi ý nhập hàng',       NOW(6)),
    ('FORECAST_WINDOW_DAYS',    '30',    'INTEGER', 'Số ngày lấy lịch sử xuất để tính bình quân/ngày',        NOW(6)),
    ('SLOW_MOVING_DAYS',        '90',    'INTEGER', 'Không phát sinh xuất quá N ngày thì coi là tồn lâu',     NOW(6)),
    ('LOW_STOCK_REPORT_HOUR',   '7',     'INTEGER', 'Giờ gửi email báo cáo hàng tồn thấp hằng ngày',          NOW(6)),
    ('LOW_STOCK_REPORT_ENABLED','true',  'BOOLEAN', 'Bật/tắt email báo cáo tồn thấp hằng ngày',               NOW(6));

-- ----------------------------------------------------------------------
-- Tài khoản quản trị đầu tiên.
--   Tên đăng nhập : admin
--   Mật khẩu      : Admin@123   (băm BCrypt cost 10)
-- Đổi mật khẩu ngay sau lần đăng nhập đầu tiên khi triển khai thật.
-- ----------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, full_name, phone, role, status, must_change_password, failed_login_attempts, created_at, updated_at)
VALUES ('admin',
        'admin@smartstock.local',
        '$2a$10$VNKAREIzOJ8agtjxV.RWIeA8fsgF17wdLKLfFjYDsiX7cFaGDPumO',
        'Quản trị hệ thống',
        '0900000000',
        'ADMIN',
        'ACTIVE',
        0,
        0,
        NOW(6), NOW(6));
