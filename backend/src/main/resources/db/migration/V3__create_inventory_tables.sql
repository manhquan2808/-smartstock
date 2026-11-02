-- ======================================================================
-- V3: Lõi kho - tồn kho, sổ giao dịch kho, bộ sinh mã phiếu.
--
-- Bất biến quan trọng nhất của hệ thống:
--     inventories.quantity = SUM(stock_transactions.quantity_change)
--     cho cùng cặp (product_id, warehouse_id).
-- Có test tự động kiểm tra điều này (InventoryInvariantTest).
-- ======================================================================

-- ----------------------------------------------------------------------
-- inventories: tồn kho theo cặp (sản phẩm x kho).
--   * quantity : số tồn hiện tại
--   * avg_cost : giá vốn bình quân gia quyền tại kho đó
--   * version  : cột khóa lạc quan của JPA (@Version)
-- Mọi thay đổi tồn chỉ được thực hiện qua InventoryService.
-- ----------------------------------------------------------------------
CREATE TABLE inventories (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    product_id    BIGINT         NOT NULL,
    warehouse_id  BIGINT         NOT NULL,
    quantity      DECIMAL(18, 3) NOT NULL DEFAULT 0,
    avg_cost      DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT 'Giá vốn bình quân gia quyền',
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    CONSTRAINT pk_inventories PRIMARY KEY (id),
    -- Mỗi sản phẩm chỉ có đúng một dòng tồn trong một kho
    CONSTRAINT uk_inventories_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT fk_inventories_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventories_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    -- Tồn kho không bao giờ được âm
    CONSTRAINT ck_inventories_quantity_non_negative CHECK (quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_inventories_warehouse ON inventories (warehouse_id);
CREATE INDEX idx_inventories_quantity ON inventories (quantity);

-- ----------------------------------------------------------------------
-- stock_transactions: sổ giao dịch kho (append-only, không sửa, không xóa).
-- Đây là nguồn dữ liệu duy nhất cho thẻ kho và báo cáo nhập - xuất - tồn.
--   * type            : RECEIPT | ISSUE | ADJUSTMENT | REVERSAL
--   * quantity_change : dương khi tăng tồn, âm khi giảm tồn
--   * balance_after   : số tồn ngay sau giao dịch (giúp dựng thẻ kho nhanh)
-- ----------------------------------------------------------------------
CREATE TABLE stock_transactions (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    product_id      BIGINT         NOT NULL,
    warehouse_id    BIGINT         NOT NULL,
    type            VARCHAR(20)    NOT NULL COMMENT 'RECEIPT | ISSUE | ADJUSTMENT | REVERSAL',
    ref_type        VARCHAR(20)    NOT NULL COMMENT 'RECEIPT | ISSUE | STOCKTAKE',
    ref_id          BIGINT         NOT NULL COMMENT 'Khóa chính của phiếu gốc',
    ref_code        VARCHAR(30)    NOT NULL COMMENT 'Mã phiếu để tra cứu nhanh',
    quantity_change DECIMAL(18, 3) NOT NULL COMMENT 'Dương = nhập, âm = xuất',
    unit_cost       DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT 'Giá vốn tại thời điểm giao dịch',
    balance_after   DECIMAL(18, 3) NOT NULL,
    note            VARCHAR(500)   NULL,
    transaction_date DATETIME(6)   NOT NULL COMMENT 'Ngày hiệu lực kế toán kho',
    created_by      BIGINT         NULL,
    created_at      DATETIME(6)    NOT NULL,
    CONSTRAINT pk_stock_transactions PRIMARY KEY (id),
    CONSTRAINT fk_stock_transactions_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_transactions_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Chỉ mục ghép phục vụ thẻ kho và báo cáo theo khoảng thời gian
CREATE INDEX idx_st_product_warehouse_date ON stock_transactions (product_id, warehouse_id, transaction_date);
CREATE INDEX idx_st_warehouse_date ON stock_transactions (warehouse_id, transaction_date);
CREATE INDEX idx_st_ref ON stock_transactions (ref_type, ref_id);
CREATE INDEX idx_st_type ON stock_transactions (type);

-- ----------------------------------------------------------------------
-- document_sequences: sinh số thứ tự phiếu theo (tiền tố, ngày).
-- Khi nhiều người tạo phiếu cùng lúc, service khóa dòng bằng
-- SELECT ... FOR UPDATE nên không thể sinh trùng mã.
-- ----------------------------------------------------------------------
CREATE TABLE document_sequences (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    prefix        VARCHAR(10) NOT NULL COMMENT 'PN | PX | KK',
    sequence_date DATE        NOT NULL,
    last_number   INT         NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    CONSTRAINT pk_document_sequences PRIMARY KEY (id),
    CONSTRAINT uk_document_sequences UNIQUE (prefix, sequence_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
