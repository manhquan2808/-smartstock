-- ======================================================================
-- V5: Kiểm kê kho (mã KK-yyyyMMdd-xxx).
--
-- Quy trình: tạo đợt (chụp số tồn hệ thống) -> nhập số thực tế
--            -> gửi duyệt -> duyệt (điều chỉnh tồn về số thực tế).
-- ======================================================================

CREATE TABLE stocktakes (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    code               VARCHAR(30)   NOT NULL,
    warehouse_id       BIGINT        NOT NULL,
    document_date      DATE          NOT NULL,
    category_id        BIGINT        NULL COMMENT 'Lọc theo loại hàng, NULL = toàn bộ sản phẩm',
    note               VARCHAR(1000) NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | PENDING | COMPLETED | REJECTED | CANCELLED',
    submitted_at       DATETIME(6)   NULL,
    approved_by_id     BIGINT        NULL,
    approved_at        DATETIME(6)   NULL,
    reject_reason      VARCHAR(500)  NULL,
    cancel_reason      VARCHAR(500)  NULL,
    cancelled_by_id    BIGINT        NULL,
    cancelled_at       DATETIME(6)   NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    created_by         BIGINT        NULL,
    updated_by         BIGINT        NULL,
    CONSTRAINT pk_stocktakes PRIMARY KEY (id),
    CONSTRAINT uk_stocktakes_code UNIQUE (code),
    CONSTRAINT fk_stocktakes_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_stocktakes_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_stocktakes_approver FOREIGN KEY (approved_by_id) REFERENCES users (id),
    CONSTRAINT fk_stocktakes_canceller FOREIGN KEY (cancelled_by_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_stocktakes_warehouse_status ON stocktakes (warehouse_id, status);
CREATE INDEX idx_stocktakes_document_date ON stocktakes (document_date);

-- ----------------------------------------------------------------------
-- stocktake_items:
--   system_quantity  : số tồn hệ thống, chụp lại lúc TẠO đợt kiểm kê
--   actual_quantity  : số đếm thực tế, nhân viên nhập (NULL = chưa đếm)
--   difference       : actual - system (cột sinh tự động)
--   unit_cost        : giá vốn lúc tạo đợt, dùng tính giá trị chênh lệch
-- ----------------------------------------------------------------------
CREATE TABLE stocktake_items (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    stocktake_id     BIGINT         NOT NULL,
    product_id       BIGINT         NOT NULL,
    line_no          INT            NOT NULL DEFAULT 1,
    system_quantity  DECIMAL(18, 3) NOT NULL DEFAULT 0,
    actual_quantity  DECIMAL(18, 3) NULL,
    unit_cost        DECIMAL(18, 2) NOT NULL DEFAULT 0,
    note             VARCHAR(500)   NULL,
    CONSTRAINT pk_stocktake_items PRIMARY KEY (id),
    CONSTRAINT uk_stocktake_items UNIQUE (stocktake_id, product_id),
    CONSTRAINT fk_stocktake_items_stocktake FOREIGN KEY (stocktake_id) REFERENCES stocktakes (id) ON DELETE CASCADE,
    CONSTRAINT fk_stocktake_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_stocktake_items_actual_non_negative CHECK (actual_quantity IS NULL OR actual_quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_stocktake_items_stocktake ON stocktake_items (stocktake_id);
CREATE INDEX idx_stocktake_items_product ON stocktake_items (product_id);
