-- ======================================================================
-- V4: Phiếu nhập kho và phiếu xuất kho (cùng luồng duyệt).
--
-- Luồng trạng thái:
--   DRAFT --gửi duyệt--> PENDING --duyệt--> COMPLETED --hủy--> CANCELLED
--                            |
--                            +--từ chối--> REJECTED --sửa & gửi lại--> PENDING
-- ======================================================================

-- ----------------------------------------------------------------------
-- stock_receipts: phiếu nhập kho, mã dạng PN-yyyyMMdd-xxx
--   receipt_type = PURCHASE         -> bắt buộc supplier_id
--                  CUSTOMER_RETURN  -> bắt buộc customer_id
--                  OTHER            -> bắt buộc reason
-- ----------------------------------------------------------------------
CREATE TABLE stock_receipts (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    code             VARCHAR(30)    NOT NULL,
    warehouse_id     BIGINT         NOT NULL,
    receipt_type     VARCHAR(20)    NOT NULL COMMENT 'PURCHASE | CUSTOMER_RETURN | OTHER',
    document_date    DATE           NOT NULL COMMENT 'Ngày chứng từ',
    supplier_id      BIGINT         NULL,
    customer_id      BIGINT         NULL,
    reason           VARCHAR(500)   NULL COMMENT 'Lý do, bắt buộc với loại OTHER',
    note             VARCHAR(1000)  NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    total_amount     DECIMAL(18, 2) NOT NULL DEFAULT 0,
    submitted_at     DATETIME(6)    NULL,
    approved_by_id   BIGINT         NULL,
    approved_at      DATETIME(6)    NULL,
    reject_reason    VARCHAR(500)   NULL,
    cancelled_by_id  BIGINT         NULL,
    cancelled_at     DATETIME(6)    NULL,
    cancel_reason    VARCHAR(500)   NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       BIGINT         NULL COMMENT 'Người lập phiếu',
    updated_by       BIGINT         NULL,
    CONSTRAINT pk_stock_receipts PRIMARY KEY (id),
    CONSTRAINT uk_stock_receipts_code UNIQUE (code),
    CONSTRAINT fk_receipts_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_receipts_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_receipts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_receipts_approver FOREIGN KEY (approved_by_id) REFERENCES users (id),
    CONSTRAINT fk_receipts_canceller FOREIGN KEY (cancelled_by_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_receipts_warehouse_status ON stock_receipts (warehouse_id, status);
CREATE INDEX idx_receipts_document_date ON stock_receipts (document_date);
CREATE INDEX idx_receipts_created_by ON stock_receipts (created_by);
CREATE INDEX idx_receipts_status ON stock_receipts (status);

-- ----------------------------------------------------------------------
-- stock_receipt_items: chi tiết dòng phiếu nhập
-- ----------------------------------------------------------------------
CREATE TABLE stock_receipt_items (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    receipt_id  BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    line_no     INT            NOT NULL DEFAULT 1,
    quantity    DECIMAL(18, 3) NOT NULL,
    unit_price  DECIMAL(18, 2) NOT NULL DEFAULT 0,
    amount      DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT 'quantity * unit_price',
    note        VARCHAR(500)   NULL,
    CONSTRAINT pk_stock_receipt_items PRIMARY KEY (id),
    CONSTRAINT fk_receipt_items_receipt FOREIGN KEY (receipt_id) REFERENCES stock_receipts (id) ON DELETE CASCADE,
    CONSTRAINT fk_receipt_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_receipt_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_receipt_items_price_non_negative CHECK (unit_price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_receipt_items_receipt ON stock_receipt_items (receipt_id);
CREATE INDEX idx_receipt_items_product ON stock_receipt_items (product_id);

-- ----------------------------------------------------------------------
-- stock_issues: phiếu xuất kho, mã dạng PX-yyyyMMdd-xxx
--   issue_type = SALE             -> bắt buộc customer_id
--                SUPPLIER_RETURN  -> bắt buộc supplier_id
--                INTERNAL         -> bắt buộc reason
--                DISPOSAL         -> bắt buộc reason
-- ----------------------------------------------------------------------
CREATE TABLE stock_issues (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    code             VARCHAR(30)    NOT NULL,
    warehouse_id     BIGINT         NOT NULL,
    issue_type       VARCHAR(20)    NOT NULL COMMENT 'SALE | SUPPLIER_RETURN | INTERNAL | DISPOSAL',
    document_date    DATE           NOT NULL,
    customer_id      BIGINT         NULL,
    supplier_id      BIGINT         NULL,
    reason           VARCHAR(500)   NULL,
    note             VARCHAR(1000)  NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    total_amount     DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT 'Tổng giá trị theo giá vốn khi hoàn tất',
    submitted_at     DATETIME(6)    NULL,
    approved_by_id   BIGINT         NULL,
    approved_at      DATETIME(6)    NULL,
    reject_reason    VARCHAR(500)   NULL,
    cancelled_by_id  BIGINT         NULL,
    cancelled_at     DATETIME(6)    NULL,
    cancel_reason    VARCHAR(500)   NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    created_by       BIGINT         NULL,
    updated_by       BIGINT         NULL,
    CONSTRAINT pk_stock_issues PRIMARY KEY (id),
    CONSTRAINT uk_stock_issues_code UNIQUE (code),
    CONSTRAINT fk_issues_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_issues_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_issues_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_issues_approver FOREIGN KEY (approved_by_id) REFERENCES users (id),
    CONSTRAINT fk_issues_canceller FOREIGN KEY (cancelled_by_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_issues_warehouse_status ON stock_issues (warehouse_id, status);
CREATE INDEX idx_issues_document_date ON stock_issues (document_date);
CREATE INDEX idx_issues_created_by ON stock_issues (created_by);
CREATE INDEX idx_issues_status ON stock_issues (status);

-- ----------------------------------------------------------------------
-- stock_issue_items: chi tiết dòng phiếu xuất.
-- unit_cost được ghi lại tại thời điểm HOÀN TẤT phiếu (giá vốn bình quân
-- của sản phẩm tại kho lúc đó) nên báo cáo giá trị xuất luôn tái hiện được.
-- ----------------------------------------------------------------------
CREATE TABLE stock_issue_items (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    issue_id    BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    line_no     INT            NOT NULL DEFAULT 1,
    quantity    DECIMAL(18, 3) NOT NULL,
    unit_cost   DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT 'Giá vốn khi hoàn tất phiếu',
    amount      DECIMAL(18, 2) NOT NULL DEFAULT 0,
    note        VARCHAR(500)   NULL,
    CONSTRAINT pk_stock_issue_items PRIMARY KEY (id),
    CONSTRAINT fk_issue_items_issue FOREIGN KEY (issue_id) REFERENCES stock_issues (id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_issue_items_quantity_positive CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_issue_items_issue ON stock_issue_items (issue_id);
CREATE INDEX idx_issue_items_product ON stock_issue_items (product_id);
