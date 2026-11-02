-- ======================================================================
-- V1: Người dùng, kho, phân quyền kho và các bảng token xác thực.
--
-- Quy ước chung cho toàn bộ migration:
--   * Tiền tệ      : DECIMAL(18,2)
--   * Số lượng     : DECIMAL(18,3)  (hỗ trợ đơn vị kg, lít)
--   * Thời điểm    : DATETIME(6)
--   * Mọi bảng có created_at / updated_at;
--     bảng nghiệp vụ có thêm created_by / updated_by.
-- ======================================================================

-- ----------------------------------------------------------------------
-- users: tài khoản đăng nhập. Không bao giờ xóa tài khoản, chỉ khóa
-- (status = LOCKED) để giữ nguyên lịch sử phiếu đã lập.
-- ----------------------------------------------------------------------
CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(50)  NOT NULL,
    email                 VARCHAR(150) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL COMMENT 'Mật khẩu băm bằng BCrypt',
    full_name             VARCHAR(150) NOT NULL,
    phone                 VARCHAR(20)  NULL,
    avatar_url            VARCHAR(500) NULL,
    role                  VARCHAR(20)  NOT NULL COMMENT 'ADMIN | MANAGER | STAFF',
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | LOCKED',
    must_change_password  TINYINT(1)   NOT NULL DEFAULT 0,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    last_login_at         DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            BIGINT       NULL,
    updated_by            BIGINT       NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_full_name ON users (full_name);

-- ----------------------------------------------------------------------
-- warehouses: kho / chi nhánh.
-- manager_id trỏ tới người quản lý chính của kho (dùng để gửi thông báo).
-- ----------------------------------------------------------------------
CREATE TABLE warehouses (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(255) NULL,
    phone       VARCHAR(20)  NULL,
    manager_id  BIGINT       NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_by  BIGINT       NULL,
    CONSTRAINT pk_warehouses PRIMARY KEY (id),
    CONSTRAINT uk_warehouses_code UNIQUE (code),
    CONSTRAINT fk_warehouses_manager FOREIGN KEY (manager_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_warehouses_active ON warehouses (active);

-- ----------------------------------------------------------------------
-- user_warehouses: một người dùng được gán một hoặc nhiều kho.
-- ADMIN truy cập mọi kho nên không cần dòng nào ở bảng này.
-- ----------------------------------------------------------------------
CREATE TABLE user_warehouses (
    user_id      BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    CONSTRAINT pk_user_warehouses PRIMARY KEY (user_id, warehouse_id),
    CONSTRAINT fk_user_warehouses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_warehouses_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_user_warehouses_warehouse ON user_warehouses (warehouse_id);

-- ----------------------------------------------------------------------
-- refresh_tokens: chỉ lưu BẢN BĂM (SHA-256) của refresh token.
-- Nếu cơ sở dữ liệu bị lộ, kẻ tấn công vẫn không dùng được token.
-- Mỗi lần làm mới token, dòng cũ bị thu hồi và sinh dòng mới (token rotation).
-- ----------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL COMMENT 'SHA-256 dạng hex của refresh token',
    expires_at  DATETIME(6)  NOT NULL,
    revoked_at  DATETIME(6)  NULL,
    user_agent  VARCHAR(255) NULL,
    ip_address  VARCHAR(45)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ----------------------------------------------------------------------
-- password_reset_tokens: link đặt lại mật khẩu, hạn 30 phút, dùng một lần.
-- ----------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
