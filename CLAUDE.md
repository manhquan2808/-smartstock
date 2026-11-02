# CLAUDE.md - Quy ước dự án SmartStock

File này mô tả quy ước, lệnh và cấu trúc của dự án. Đọc file này trước khi sửa mã.

---

## 1. Tổng quan

| Mục | Nội dung |
|---|---|
| Tên hệ thống | SmartStock - website quản lý kho đa chi nhánh |
| Backend | Java 21 LTS + Spring Boot 3.3, Maven, MySQL 8, Flyway |
| Frontend | React 18 + Vite + TypeScript, Ant Design 5, TanStack Query |
| Kiến trúc | REST API, phân lớp `controller → service → repository` |
| Ngôn ngữ | Tên biến/hàm/bảng/API: **tiếng Anh**. Giao diện, thông báo lỗi, chú thích mã: **tiếng Việt** |

---

## 2. Lệnh thường dùng

Máy phát triển là Windows, các lệnh dưới đây viết cho **PowerShell**.

### 2.1. Khởi động hạ tầng (MySQL + Mailpit)

```powershell
# Chỉ chạy MySQL và Mailpit, phù hợp máy RAM 8 GB
docker compose --profile dev up -d

# Xem Mailpit (hộp thư ảo) tại http://localhost:8025
# Dừng lại:
docker compose --profile dev down
```

### 2.2. Backend

```powershell
cd backend

# Chạy ở chế độ phát triển
.\mvnw spring-boot:run -D"spring-boot.run.profiles=dev"

# Chạy kèm sinh dữ liệu demo (150 sản phẩm, 300+ phiếu)
.\mvnw spring-boot:run -D"spring-boot.run.profiles=demo"

# Biên dịch
.\mvnw clean compile

# Chạy toàn bộ test
.\mvnw test

# Chạy một test cụ thể
.\mvnw test -D"test=InventoryServiceIntegrationTest"

# Chạy test trên MySQL thật thay vì H2
.\mvnw test -D"testDb=mysql"

# Đóng gói thành JAR
.\mvnw clean package
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

### 2.3. Frontend

```powershell
cd frontend

npm install          # cài thư viện (chỉ lần đầu)
npm run dev          # chạy ở http://localhost:5173
npm run build        # đóng gói production vào dist/
npm run typecheck    # kiểm tra kiểu TypeScript
npm run lint         # kiểm tra ESLint
npm run lint:fix     # tự sửa lỗi lint
npm run format       # định dạng mã bằng Prettier
```

### 2.4. Chạy toàn bộ bằng Docker

```powershell
docker compose --profile full up -d --build
# Frontend: http://localhost:3000
# Backend : http://localhost:8080
```

---

## 3. Cấu trúc thư mục

```
smartstock/
├── backend/
│   ├── src/main/java/com/smartstock/
│   │   ├── common/        # Lớp cha entity, ngoại lệ, xử lý lỗi tập trung, tiện ích số
│   │   ├── config/        # AppProperties, Async, JPA Auditing, OpenAPI, WebConfig
│   │   ├── security/      # JWT, phân quyền, kiểm tra quyền theo kho
│   │   ├── auth/          # Đăng nhập, refresh token, quên/đặt lại mật khẩu
│   │   ├── user/          # Người dùng, hồ sơ cá nhân
│   │   ├── warehouse/     # Kho / chi nhánh
│   │   ├── catalog/       # Loại hàng, ĐVT, sản phẩm, NCC, khách hàng
│   │   ├── inventory/     # LÕI KHO: tồn kho, sổ giao dịch, thẻ kho
│   │   ├── document/      # Trạng thái phiếu, sinh mã phiếu, DTO dùng chung
│   │   ├── receipt/       # Phiếu nhập kho
│   │   ├── issue/         # Phiếu xuất kho
│   │   ├── stocktake/     # Kiểm kê kho
│   │   ├── notification/  # Thông báo trong web + kênh SSE
│   │   ├── mail/          # Gửi email
│   │   ├── report/        # Báo cáo, dashboard, gợi ý nhập hàng, xuất Excel/PDF
│   │   ├── audit/         # Nhật ký hoạt động
│   │   ├── setting/       # Cấu hình hệ thống
│   │   ├── storage/       # Lưu ảnh (local hoặc Cloudinary)
│   │   └── seed/          # Sinh dữ liệu demo
│   └── src/main/resources/
│       ├── db/migration/  # Flyway: V1..V7
│       ├── fonts/         # Noto Sans để xuất PDF tiếng Việt
│       └── templates/mail/
├── frontend/src/
│   ├── api/               # client.ts (axios + refresh token), endpoints.ts
│   ├── types/             # Kiểu dữ liệu phản chiếu DTO backend
│   ├── contexts/          # Auth, Theme, Warehouse
│   ├── components/        # Layout và các component dùng chung
│   ├── pages/             # 25 trang
│   ├── hooks/             # useUrlFilters, useNotificationStream
│   └── utils/             # format.ts, constants.ts, form.ts
└── docs/                  # Tài liệu (xem docs/README nếu có)
```

---

## 4. Quy ước BẮT BUỘC về nghiệp vụ

Đây là các quy tắc không được vi phạm. Vi phạm sẽ làm sai số liệu kho.

### 4.1. Chỉ `InventoryService` được sửa tồn kho

```java
// ĐÚNG: đi qua InventoryService
inventoryService.applyIssue(movements);

// SAI: không bao giờ làm thế này
inventory.setQuantity(newQuantity);
inventoryRepository.save(inventory);
```

Lý do: mọi thay đổi tồn phải kèm một dòng trong `stock_transactions`. Nếu sửa trực tiếp,
bất biến `tồn = tổng giao dịch` bị phá và báo cáo sẽ sai.

### 4.2. Bất biến cốt lõi

```
inventories.quantity == SUM(stock_transactions.quantity_change)
```

cho mỗi cặp (product_id, warehouse_id). Có test tự động kiểm tra
(`InventoryServiceIntegrationTest.batBien_tonBangTongGiaoDich`).

Truy vấn đối chiếu bằng tay: xem `docs/TRUY_VAN_KIEM_TRA.sql`.

### 4.3. Khóa dòng theo thứ tự product_id tăng dần

Khi một phiếu có nhiều dòng, luôn sắp xếp theo `product_id` trước khi khóa
(`InventoryService.sortByProductId`). Nếu không, hai giao dịch có thể khóa chéo nhau
và gây deadlock.

### 4.4. Phân quyền kiểm tra ở BACKEND

Giao diện ẩn menu/nút chỉ để gọn gàng. Mọi API phải tự kiểm tra:

```java
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")   // kiểm tra vai trò
warehouseAccessGuard.requireAccess(warehouseId); // kiểm tra kho được gán
```

Và DTO trả về cho STAFF phải **lược bỏ** giá vốn, giá trị tồn (trả `null`).

### 4.5. Phiếu COMPLETED không được sửa

Muốn điều chỉnh thì **hủy kèm lý do**; hệ thống ghi giao dịch đảo ngược
(`TransactionType.REVERSAL`), không sửa và không xóa giao dịch cũ.

---

## 5. Quy ước mã nguồn

### 5.1. Backend

- Phân lớp rõ: controller chỉ nhận/trả dữ liệu, nghiệp vụ nằm trong service.
- DTO là `record`, gom theo phân hệ trong một lớp vỏ (ví dụ `CatalogDtos`).
- Tiền: `BigDecimal` scale 2. Số lượng: `BigDecimal` scale 3. **Không dùng `double`**.
- Lỗi nghiệp vụ: ném `BusinessException` (hoặc lớp con) với thông điệp **tiếng Việt**
  hiển thị được trực tiếp cho người dùng.
- Truy vấn: dùng tham số hóa (`:param`), không nối chuỗi SQL.
- Tránh N+1: dùng `@EntityGraph` hoặc `JOIN FETCH` cho danh sách.
- Lọc theo kho: dùng cặp tham số `allWarehouses` + `warehouseIds` (xem `WarehouseScope`).
- Chú thích tiếng Việt ở các hàm nghiệp vụ quan trọng, nêu **lý do** chứ không kể lại mã.

### 5.2. Frontend

- Component là function, props khai bằng `interface`.
- Gọi API qua `src/api/endpoints.ts`, không viết URL rải rác trong trang.
- Dữ liệu từ server: dùng TanStack Query (`useQuery`/`useMutation`), không tự `useEffect` + `fetch`.
- Bộ lọc và phân trang lưu trên **URL** (hook `useUrlFilters`).
- Không có `console.log` trong mã nguồn (ESLint chặn).
- Định dạng số/tiền/ngày: dùng hàm trong `utils/format.ts`, không tự viết lại.

---

## 6. Cơ sở dữ liệu

- Schema do **Flyway** quản lý. Không bao giờ dùng `ddl-auto=update`.
- Thêm bảng/cột: tạo file mới `V{n}__mo_ta.sql`, **không sửa file migration đã chạy**.
- Sau khi sửa entity, chạy lại ứng dụng: `ddl-auto=validate` sẽ báo lỗi nếu entity
  không khớp schema.

Bảng quan trọng nhất:

| Bảng | Vai trò |
|---|---|
| `inventories` | Tồn kho hiện tại (sản phẩm × kho), có `avg_cost` và `version` |
| `stock_transactions` | Sổ giao dịch kho, **chỉ ghi thêm**, là nguồn sự thật cho mọi báo cáo |
| `document_sequences` | Bộ đếm sinh mã phiếu, khóa dòng `SELECT ... FOR UPDATE` |

---

## 7. Kiểm thử

| Loại | Vị trí | Chạy bằng |
|---|---|---|
| Unit (công thức giá vốn) | `InventoryCostCalculationTest` | `.\mvnw test` |
| Tích hợp lõi kho | `InventoryServiceIntegrationTest` | `.\mvnw test` |
| Đồng thời (2 phiếu xuất) | `ConcurrentIssueIntegrationTest` | `.\mvnw test` |
| Phân quyền | `PermissionIntegrationTest` | `.\mvnw test` |
| Luồng duyệt phiếu | `DocumentWorkflowIntegrationTest` | `.\mvnw test` |

Mặc định test chạy trên **H2** (chế độ tương thích MySQL) nên chạy được ngay, không cần
cài MySQL hay Docker.

Để chạy lại cùng bộ test trên **MySQL thật** (kiểm chứng hành vi khóa dòng
`SELECT ... FOR UPDATE`, kiểu `DECIMAL`, ràng buộc `CHECK`):

```powershell
# Bước 1: khởi động MySQL và tạo cơ sở dữ liệu test
docker compose --profile dev up -d
docker compose exec mysql mysql -uroot -proot123 -e "CREATE DATABASE IF NOT EXISTS smartstock_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL ON smartstock_test.* TO 'smartstock'@'%'; FLUSH PRIVILEGES;"

# Bước 2: chạy test
cd backend
.\mvnw test -D"testDb=mysql"
```

Cách hoạt động: lớp `TestProfileResolver` đọc tham số `testDb` lúc chạy để chọn hồ sơ
`test` (H2) hoặc `mysqltest` (MySQL). **Không dùng** `@ActiveProfiles("test")` cố định,
vì annotation đó ghi đè tham số dòng lệnh và sẽ khiến bộ test âm thầm chạy trên H2 dù
người chạy tưởng đang kiểm thử trên MySQL.

**Khi sửa `InventoryService`, bắt buộc chạy lại toàn bộ test trước khi commit.**

---

## 8. Quy ước commit

Theo Conventional Commits, mô tả bằng tiếng Việt:

```
feat(receipt): duyệt phiếu nhập
fix(inventory): chặn xuất vượt tồn khi nhiều người duyệt cùng lúc
docs(readme): hướng dẫn triển khai Render
test(inventory): bổ sung test giá vốn bình quân
chore(deps): nâng Spring Boot lên 3.3.5
```

Phạm vi (scope) dùng tên phân hệ: `auth`, `user`, `catalog`, `inventory`, `receipt`,
`issue`, `stocktake`, `report`, `frontend`, `docs`, `deploy`.

---

## 9. Biến môi trường

Copy `.env.example` thành `.env` rồi điền. **Không commit `.env`.**

Bắt buộc khi triển khai thật:

| Biến | Ý nghĩa |
|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Kết nối MySQL |
| `JWT_SECRET` | Khóa HS256, **tối thiểu 32 ký tự** |
| `APP_CORS_ALLOWED_ORIGINS` | Domain frontend được phép gọi API |
| `APP_COOKIE_SAME_SITE=none` + `APP_COOKIE_SECURE=true` | Khi frontend/backend khác domain |
| `MAIL_*` | SMTP (Gmail App Password khi chạy thật) |
| `CLOUDINARY_URL` | Lưu ảnh lâu dài trên hosting miễn phí |

---

## 10. Những điều cần tránh

- Sửa trực tiếp bảng `inventories` ngoài `InventoryService`.
- Sửa file migration Flyway đã chạy.
- Dùng `double` cho tiền hoặc số lượng.
- Trả giá vốn/giá trị tồn cho vai trò STAFF.
- Tin vào tham số `warehouseId` do frontend gửi mà không kiểm tra quyền.
- Để lỗi gửi email làm rollback giao dịch nghiệp vụ (phải `@Async` và bắt lỗi).
- Viết `console.log` trong mã frontend.
