-- ======================================================================
-- V2: Danh mục nền - loại hàng, đơn vị tính, sản phẩm, NCC, khách hàng.
--
-- Các bảng danh mục dùng XÓA MỀM: cột deleted_at khác NULL nghĩa là
-- bản ghi đã bị xóa nhưng vẫn giữ lại để các phiếu cũ còn tham chiếu được.
-- ======================================================================

-- ----------------------------------------------------------------------
-- categories: loại hàng (ví dụ: Nước giải khát, Bánh kẹo...)
-- ----------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    deleted_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_by  BIGINT       NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_categories_name ON categories (name);
CREATE INDEX idx_categories_deleted_at ON categories (deleted_at);

-- ----------------------------------------------------------------------
-- units: đơn vị tính (Thùng, Lon, Kg, Lít...)
-- ----------------------------------------------------------------------
CREATE TABLE units (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    deleted_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_by  BIGINT       NULL,
    CONSTRAINT pk_units PRIMARY KEY (id),
    CONSTRAINT uk_units_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_units_deleted_at ON units (deleted_at);

-- ----------------------------------------------------------------------
-- products: sản phẩm.
--   * barcode có thể rỗng nhưng nếu có thì phải duy nhất (EAN-13).
--   * min_stock: mức tồn tối thiểu, dùng để sinh cảnh báo tồn thấp.
--   * status ngừng kinh doanh thì không chọn được khi lập phiếu mới.
-- ----------------------------------------------------------------------
CREATE TABLE products (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    sku             VARCHAR(50)    NOT NULL COMMENT 'Mã nội bộ sản phẩm',
    barcode         VARCHAR(50)    NULL COMMENT 'Mã vạch EAN-13, có thể rỗng',
    name            VARCHAR(255)   NOT NULL,
    category_id     BIGINT         NOT NULL,
    unit_id         BIGINT         NOT NULL,
    purchase_price  DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT 'Giá nhập tham chiếu',
    sale_price      DECIMAL(18, 2) NOT NULL DEFAULT 0,
    min_stock       DECIMAL(18, 3) NOT NULL DEFAULT 0 COMMENT 'Mức tồn tối thiểu',
    image_url       VARCHAR(500)   NULL,
    description     VARCHAR(1000)  NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | DISCONTINUED',
    deleted_at      DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    created_by      BIGINT         NULL,
    updated_by      BIGINT         NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT uk_products_barcode UNIQUE (barcode),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_unit FOREIGN KEY (unit_id) REFERENCES units (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Chỉ mục phục vụ tìm kiếm theo tên và lọc theo loại/trạng thái
CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_status ON products (status);
CREATE INDEX idx_products_deleted_at ON products (deleted_at);

-- ----------------------------------------------------------------------
-- suppliers: nhà cung cấp
-- ----------------------------------------------------------------------
CREATE TABLE suppliers (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(20)  NULL,
    email       VARCHAR(150) NULL,
    address     VARCHAR(255) NULL,
    tax_code    VARCHAR(30)  NULL,
    note        VARCHAR(500) NULL,
    deleted_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_by  BIGINT       NULL,
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT uk_suppliers_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_suppliers_name ON suppliers (name);
CREATE INDEX idx_suppliers_deleted_at ON suppliers (deleted_at);

-- ----------------------------------------------------------------------
-- customers: khách hàng
-- ----------------------------------------------------------------------
CREATE TABLE customers (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(20)  NULL,
    email       VARCHAR(150) NULL,
    address     VARCHAR(255) NULL,
    tax_code    VARCHAR(30)  NULL,
    note        VARCHAR(500) NULL,
    deleted_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_by  BIGINT       NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uk_customers_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_customers_name ON customers (name);
CREATE INDEX idx_customers_deleted_at ON customers (deleted_at);
