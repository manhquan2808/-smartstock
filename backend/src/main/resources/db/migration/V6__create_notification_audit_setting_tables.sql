-- ======================================================================
-- V6: Thông báo trong web, nhật ký hoạt động và cấu hình hệ thống.
-- ======================================================================

-- ----------------------------------------------------------------------
-- notifications: thông báo gửi tới từng người dùng.
-- read_at = NULL nghĩa là chưa đọc (dùng để đếm số trên biểu tượng chuông).
-- ----------------------------------------------------------------------
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL COMMENT 'RECEIPT_PENDING | ISSUE_PENDING | DOCUMENT_APPROVED | DOCUMENT_REJECTED | LOW_STOCK | STOCKTAKE_PENDING',
    title      VARCHAR(200) NOT NULL,
    content    VARCHAR(500) NULL,
    link       VARCHAR(255) NULL COMMENT 'Đường dẫn frontend để bấm vào xem',
    read_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Chỉ mục ghép: lấy thông báo của một người theo thời gian giảm dần
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at);

-- ----------------------------------------------------------------------
-- audit_logs: nhật ký hoạt động (chỉ ADMIN xem được).
-- before_json / after_json lưu trạng thái dữ liệu trước và sau khi đổi.
-- ----------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NULL COMMENT 'NULL khi đăng nhập thất bại với tên không tồn tại',
    username    VARCHAR(50)  NULL COMMENT 'Lưu kèm tên đăng nhập để tra cứu nhanh',
    action      VARCHAR(50)  NOT NULL COMMENT 'LOGIN_SUCCESS | LOGIN_FAILED | CREATE | UPDATE | DELETE | SUBMIT | APPROVE | REJECT | CANCEL | LOCK_USER | UNLOCK_USER',
    entity_type VARCHAR(50)  NULL,
    entity_id   BIGINT       NULL,
    description VARCHAR(500) NULL,
    before_json TEXT         NULL,
    after_json  TEXT         NULL,
    ip_address  VARCHAR(45)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- ----------------------------------------------------------------------
-- app_settings: cấu hình thay đổi được trong lúc chạy (ADMIN sửa trên web).
-- Lưu dạng khóa - giá trị để thêm cấu hình mới không cần migration.
-- ----------------------------------------------------------------------
CREATE TABLE app_settings (
    setting_key   VARCHAR(50)  NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    value_type    VARCHAR(20)  NOT NULL DEFAULT 'INTEGER' COMMENT 'INTEGER | DECIMAL | STRING | BOOLEAN',
    description   VARCHAR(255) NULL,
    updated_at    DATETIME(6)  NOT NULL,
    updated_by    BIGINT       NULL,
    CONSTRAINT pk_app_settings PRIMARY KEY (setting_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
