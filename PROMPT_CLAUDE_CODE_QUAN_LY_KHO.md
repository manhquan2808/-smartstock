# PROMPT XÂY DỰNG WEBSITE QUẢN LÝ KHO – SMARTSTOCK

> Dán toàn bộ nội dung dưới đây vào Claude Code, mở trong một thư mục trống (ví dụ `D:\smartstock`).

---

## 0. Vai trò và cách làm việc

Bạn là kỹ sư phần mềm full-stack. Nhiệm vụ: xây dựng **hoàn chỉnh** một website quản lý kho hàng chạy được thật, phục vụ báo cáo **thực tập doanh nghiệp** ngành Công nghệ phần mềm, do **một sinh viên** thực hiện trong **6–8 tuần**.

Nguyên tắc làm việc:

1. **Bước đầu tiên:** đọc kỹ toàn bộ prompt, sau đó tạo:
   - `docs/SPEC.md`: đặc tả yêu cầu, chép lại các mục 1–9 của prompt này.
   - `CLAUDE.md` ở thư mục gốc: quy ước dự án, lệnh build/test/run, cấu trúc thư mục.
2. Thực hiện **toàn bộ dự án theo thứ tự các giai đoạn ở mục 10**, chạy liên tục, không dừng lại hỏi trừ khi thật sự bị chặn (thiếu thông tin không thể tự suy ra). Gặp điểm chưa rõ thì chọn phương án đơn giản, đúng nghiệp vụ và ghi vào `docs/GIA_DINH.md`.
3. **Sau mỗi giai đoạn:**
   - Build backend và frontend thành công, chạy toàn bộ test và sửa đến khi pass.
   - Commit Git theo từng chức năng (Conventional Commits, ví dụ `feat(receipt): duyệt phiếu nhập`).
   - Viết tài liệu giải thích cho giai đoạn đó trong `docs/giai-thich/` (xem mục 10).
4. Mọi chức năng đều kết nối cơ sở dữ liệu thật. Không dùng dữ liệu giả cứng (hard-code/mock) trong mã chạy thật.
5. Chỉ dùng thư viện mã nguồn mở, miễn phí, phiên bản LTS/ổn định.
6. Người thực hiện **mới quen PHP**, chưa quen Java/React. Vì vậy:
   - Mã nguồn rõ ràng, theo mẫu nhất quán, không dùng kỹ thuật “ảo diệu” khi chưa cần.
   - Chú thích bằng tiếng Việt ở các hàm nghiệp vụ quan trọng.
7. Tài liệu (README, docs) viết theo văn phong kỹ thuật trung tính, bằng tiếng Việt.

---

## 1. Tổng quan dự án

| Mục | Nội dung |
|---|---|
| Tên đề tài | Xây dựng website quản lý kho đa chi nhánh tích hợp quét mã vạch/QR |
| Tên hệ thống | **SmartStock** (có thể đổi trong file cấu hình) |
| Lĩnh vực | Quản lý kho cho doanh nghiệp thương mại vừa và nhỏ, có nhiều kho/chi nhánh |
| Bối cảnh | Doanh nghiệp đang ghi sổ tay/Excel, nhiều file rời rạc nên dễ sai số liệu và khó tổng hợp. Không biết chính xác tồn kho hiện tại, thường hết hàng hoặc tồn đọng. Khó truy vết ai nhập/xuất, khi nào, dẫn đến thất thoát. Kiểm kê thủ công tốn thời gian. Không theo dõi được hạn sử dụng. |
| Mục tiêu | (1) Tin học hóa nghiệp vụ nhập – xuất – tồn, thay thế sổ sách/Excel. (2) Theo dõi tồn kho chính xác theo thời gian thực, cảnh báo hàng sắp hết. |
| Tham khảo giao diện | Zoho Inventory, Sortly: hiện đại, **gọn nhẹ, dễ dùng** |

**Tiêu chí thành công:**
- Quy trình nhập kho, xuất kho đầy đủ; tồn kho cập nhật tự động và chính xác.
- Phân quyền đúng theo vai trò; dữ liệu không sai lệch khi nhiều người thao tác cùng lúc.
- Dashboard và báo cáo nhập – xuất – tồn theo khoảng thời gian, xuất được Excel.
- Kiểm kê kho và điều chỉnh chênh lệch.
- Website triển khai trực tuyến, demo được đầy đủ kịch bản.

---

## 2. Công nghệ bắt buộc

| Thành phần | Công nghệ |
|---|---|
| Backend | **Java 21 LTS + Spring Boot 3.x** (Spring Web, Spring Data JPA, Spring Security, Validation, Spring Mail), Maven |
| Kiến trúc backend | REST API, phân lớp `controller → service → repository`, DTO + mapper (MapStruct), xử lý lỗi tập trung bằng `@RestControllerAdvice` |
| CSDL | **MySQL 8**, quản lý schema bằng **Flyway** (không dùng `ddl-auto=update`) |
| Frontend | **React 18 + Vite + TypeScript**, **Ant Design 5**, React Router, TanStack Query, Axios, Recharts (biểu đồ) |
| Xác thực | JWT: access token ngắn hạn giữ trong bộ nhớ; refresh token lưu trong cookie httpOnly. Mật khẩu băm bằng BCrypt |
| Tài liệu API | springdoc-openapi (Swagger UI tại `/swagger-ui.html`) |
| Thông báo realtime | Server-Sent Events (SSE). Frontend dùng `@microsoft/fetch-event-source` để gửi được header JWT |
| Email | Spring Mail qua SMTP. Môi trường dev dùng **Mailpit** trong Docker; production dùng Gmail App Password (biến môi trường) |
| Xuất file | Excel: Apache POI. PDF: OpenPDF, **nhúng font hỗ trợ tiếng Việt** (Noto Sans/Roboto) để không lỗi dấu |
| Mã vạch/QR | Quét bằng camera: `html5-qrcode`. In tem: `JsBarcode` (Code128) + `qrcode` |
| Kiểm thử | JUnit 5 + Mockito (unit), Spring Boot Test (integration), Postman collection |
| Hạ tầng | Git + GitHub, Docker Compose |
| Máy phát triển | **Windows 10/11, RAM 8 GB, VS Code**. Viết lệnh cho PowerShell; có file `.ps1` hoặc hướng dẫn tương đương cho mọi script |

**Lưu ý RAM 8 GB:** `docker-compose.yml` có 2 profile:
- `dev`: chỉ chạy MySQL + Mailpit. Backend chạy bằng `mvnw spring-boot:run`, frontend chạy bằng `npm run dev`.
- `full`: chạy toàn bộ bằng container.

Đặt giới hạn bộ nhớ hợp lý cho JVM và MySQL.

**Cấu trúc thư mục:**

```
smartstock/
├── backend/              # Spring Boot
├── frontend/             # React + Vite
├── docs/                 # SPEC, giải thích, sơ đồ, hướng dẫn, test case, postman
│   ├── diagrams/         # PlantUML (.puml) + Mermaid (.md)
│   ├── giai-thich/
│   └── postman/
├── docker-compose.yml
├── .env.example
├── CLAUDE.md
└── README.md
```

Quy ước: tên biến, hàm, bảng và API bằng **tiếng Anh**; giao diện và thông báo lỗi bằng **tiếng Việt**; chú thích mã bằng **tiếng Việt**. Frontend dùng ESLint + Prettier.

---

## 3. Vai trò và phân quyền

Hệ thống nội bộ, **bắt buộc đăng nhập**, không cho tự đăng ký. Mỗi tài khoản có **một vai trò** và được gán **một hoặc nhiều kho**.

| Chức năng | ADMIN | MANAGER (Quản lý kho) | STAFF (Nhân viên kho) |
|---|---|---|---|
| Quản lý người dùng, gán vai trò, gán kho, khóa/mở khóa | ✔ | ✘ | ✘ |
| CRUD kho | ✔ | ✘ (chỉ xem) | ✘ (chỉ xem kho được gán) |
| CRUD loại hàng, đơn vị tính, sản phẩm, NCC, khách hàng | ✔ | ✔ | Chỉ xem |
| Tạo/sửa/xóa phiếu **nháp của mình**, gửi duyệt | ✔ | ✔ | ✔ (chỉ kho được gán) |
| Duyệt/từ chối phiếu, hủy phiếu đã hoàn tất | ✔ | ✔ (kho được gán) | ✘ |
| Tạo phiếu kiểm kê, nhập số lượng thực tế | ✔ | ✔ | ✔ |
| Duyệt kết quả kiểm kê | ✔ | ✔ | ✘ |
| Xem tồn kho | Tất cả kho | Kho được gán | Kho được gán |
| Xem giá nhập, giá vốn, giá trị tồn | ✔ | ✔ | ✘ (chỉ thấy đơn giá trên phiếu do mình lập) |
| Dashboard, báo cáo, xuất Excel/PDF | ✔ | ✔ | Dashboard rút gọn (không có số liệu giá trị) |
| Nhật ký hoạt động | ✔ | ✘ | ✘ |

Yêu cầu:
- Kiểm tra quyền ở **cả backend** (`@PreAuthorize` và kiểm tra kho được gán trong service) **và frontend** (ẩn menu/nút).
- Backend không bao giờ tin vào frontend: DTO trả về cho STAFF phải **lược bỏ** các trường giá vốn/giá trị.
- Không xóa người dùng, chỉ khóa, để giữ lịch sử phiếu.

---

## 4. Nghiệp vụ chi tiết

### 4.1 Phiếu nhập kho (mã `PN-yyyyMMdd-xxx`)

- Loại phiếu nhập: `PURCHASE` (nhập mua từ NCC – bắt buộc chọn NCC), `CUSTOMER_RETURN` (khách trả lại – bắt buộc chọn khách hàng), `OTHER` (nhập khác – bắt buộc ghi lý do).
- Thông tin phiếu: kho, ngày chứng từ, đối tác, ghi chú. Danh sách dòng gồm sản phẩm, số lượng (> 0), đơn giá (≥ 0), thành tiền. Tổng tiền tính tự động.
- Khi **hoàn tất**:
  - Cộng tồn kho của kho tương ứng.
  - Cập nhật **giá vốn bình quân gia quyền** của sản phẩm tại kho đó:
    `giá vốn mới = (tồn cũ × giá vốn cũ + SL nhập × đơn giá nhập) / (tồn cũ + SL nhập)`.
  - Ghi một dòng vào sổ giao dịch kho (`stock_transactions`) cho mỗi dòng phiếu.

### 4.2 Phiếu xuất kho (mã `PX-yyyyMMdd-xxx`)

- Loại phiếu xuất: `SALE` (xuất bán – chọn khách hàng), `SUPPLIER_RETURN` (trả NCC – chọn NCC), `INTERNAL` (xuất dùng nội bộ), `DISPOSAL` (xuất hủy). Hai loại cuối bắt buộc ghi lý do.
- **Không cho xuất vượt tồn.** Kiểm tra khi lưu nháp (cảnh báo), khi gửi duyệt và **kiểm tra lại khi hoàn tất** (chặn). Lỗi trả về phải nêu rõ sản phẩm nào thiếu, tồn hiện có bao nhiêu.
- Khi hoàn tất: trừ tồn, ghi giá vốn tại thời điểm xuất vào dòng phiếu, ghi `stock_transactions`.

### 4.3 Trạng thái phiếu và luồng duyệt (áp dụng cho phiếu nhập và phiếu xuất)

```
DRAFT (Nháp) ──gửi duyệt──► PENDING (Chờ duyệt) ──duyệt──► COMPLETED (Hoàn tất)
   ▲                              │
   └──────── từ chối (lý do) ◄────┘   REJECTED → người lập sửa và gửi lại
COMPLETED ──hủy (bắt buộc lý do)──► CANCELLED
```

- Chỉ phiếu `DRAFT` hoặc `REJECTED` mới được sửa. Phiếu `PENDING` không ai được sửa.
- Phiếu `COMPLETED` **không được sửa hay xóa**. Muốn điều chỉnh thì **hủy kèm lý do**: hệ thống tạo giao dịch đảo ngược trong `stock_transactions`. Hủy phiếu nhập phải kiểm tra tồn đủ để trừ lại, nếu không đủ thì chặn.
- Lưu người lập, người duyệt, thời điểm duyệt, lý do từ chối/hủy.

### 4.4 Kiểm kê (mã `KK-yyyyMMdd-xxx`)

1. Tạo đợt kiểm kê cho một kho: chọn toàn bộ sản phẩm hoặc lọc theo loại hàng. Hệ thống chụp lại số tồn hệ thống tại thời điểm tạo.
2. Nhân viên nhập số lượng thực tế (hỗ trợ quét mã để tìm sản phẩm nhanh trên điện thoại).
3. Hệ thống tính chênh lệch = thực tế − hệ thống, và giá trị chênh lệch theo giá vốn.
4. Gửi duyệt. Quản lý duyệt thì tồn kho được điều chỉnh về số thực tế; ghi `stock_transactions` loại `ADJUSTMENT`.

Trong lúc đợt kiểm kê đang mở, cảnh báo nếu có phiếu nhập/xuất hoàn tất tại kho đó.

### 4.5 Mã phiếu tự sinh

Định dạng `{TIỀN_TỐ}-{yyyyMMdd}-{3 chữ số tăng dần theo ngày}`. Sinh mã an toàn khi nhiều người tạo phiếu cùng lúc: dùng bảng `document_sequences` khóa dòng `SELECT … FOR UPDATE`, cộng với ràng buộc UNIQUE.

### 4.6 Tồn kho và tính nhất quán (quan trọng nhất)

- Bảng `inventories` (sản phẩm × kho): `quantity`, `avg_cost`, `version`.
- Mọi thay đổi tồn đi qua **một service duy nhất** (`InventoryService`), trong `@Transactional`.
- Khóa dòng tồn bằng `PESSIMISTIC_WRITE`, theo thứ tự `product_id` tăng dần để tránh deadlock.
- Không bao giờ sửa trực tiếp `inventories` từ nơi khác.
- **Bất biến:** tồn hiện tại = tổng các giao dịch trong `stock_transactions`. Viết test xác nhận điều này.
- Mức tồn tối thiểu đặt theo sản phẩm. Khi tồn ≤ mức tối thiểu sau giao dịch, tạo cảnh báo.

### 4.7 Thông báo

- **Trong website** (biểu tượng chuông + số chưa đọc, cập nhật realtime qua SSE):
  - Phiếu mới chờ duyệt → gửi quản lý của kho đó.
  - Phiếu bị từ chối hoặc đã được duyệt → gửi người lập.
  - Sản phẩm xuống dưới mức tồn tối thiểu → gửi quản lý.
- **Email:**
  - Gửi quản lý khi có phiếu chờ duyệt.
  - Gửi báo cáo hàng tồn thấp lúc **07:00 hằng ngày** (`@Scheduled`, múi giờ `Asia/Ho_Chi_Minh`).
- Gửi email bất đồng bộ (`@Async`); lỗi gửi mail không được làm hỏng giao dịch nghiệp vụ.

### 4.8 Dự báo nhu cầu nhập hàng (điểm nhấn)

Trang “Gợi ý nhập hàng”, áp dụng cho từng kho:

- Lượng xuất trung bình/ngày = tổng xuất trong N ngày gần nhất / N (N = 30, cho phép chọn 30/60/90).
- Số ngày còn đủ hàng = tồn hiện tại / lượng xuất trung bình mỗi ngày.
- Số lượng nên nhập = trung bình/ngày × (thời gian chờ hàng + số ngày dự trữ) + tồn tối thiểu − tồn hiện tại, làm tròn lên và không âm. Thời gian chờ hàng mặc định 7 ngày, số ngày dự trữ mặc định 14 ngày; cả hai cấu hình được.
- Danh sách sắp xếp theo số ngày còn đủ hàng tăng dần, có nút **“Tạo phiếu nhập từ gợi ý”** (tạo phiếu nháp).
- Hiển thị biểu đồ lượng xuất theo ngày của sản phẩm được chọn.
- Giải thích công thức ngay trên giao diện (tooltip) để dễ trình bày khi bảo vệ.

### 4.9 Nhật ký hoạt động

Ghi các sự kiện: đăng nhập (thành công/thất bại), tạo/gửi duyệt/duyệt/từ chối/hủy phiếu, thêm/sửa/xóa danh mục, khóa/mở tài khoản. Nội dung ghi gồm người thực hiện, thời điểm, IP, đối tượng, dữ liệu trước/sau (JSON).

### 4.10 Xóa dữ liệu

- Danh mục đã phát sinh giao dịch thì **xóa mềm** (`deleted_at`).
- **Chặn xóa sản phẩm còn tồn** ở bất kỳ kho nào.
- Sản phẩm có trạng thái Đang kinh doanh / Ngừng kinh doanh. Sản phẩm ngừng kinh doanh không chọn được khi lập phiếu mới.

---

## 5. Danh sách chức năng

### 5.1 Bắt buộc (MVP)

- Đăng nhập, đăng xuất, làm mới token.
- Quên mật khẩu qua email (link có hạn 30 phút, dùng một lần), đổi mật khẩu.
- Quản lý người dùng: tạo tài khoản, gán vai trò, gán kho, khóa/mở khóa, đặt lại mật khẩu.
- Hồ sơ cá nhân: họ tên, email, số điện thoại, ảnh đại diện; kho phụ trách chỉ xem; đổi mật khẩu.
- Danh mục: kho, loại hàng, đơn vị tính, sản phẩm, nhà cung cấp, khách hàng.
- Phiếu nhập, phiếu xuất: lập, sửa nháp, gửi duyệt, duyệt, từ chối, hủy, **in PDF**.
- Tồn kho theo kho/sản phẩm; cảnh báo tồn thấp.
- Thẻ kho: lịch sử biến động của một sản phẩm.
- Dashboard và báo cáo (mục 5.3).
- Thông báo trong web + email + realtime.
- Nhật ký hoạt động.

### 5.2 Mở rộng (làm ngay sau MVP, vẫn thuộc phạm vi bàn giao)

- **Kiểm kê kho** (mục 4.4).
- **Quét mã vạch/QR bằng camera** để chọn sản phẩm khi lập phiếu và khi kiểm kê. Trên máy tính cho phép nhập mã bằng máy quét USB (đầu đọc gõ như bàn phím + Enter).
- **In tem mã vạch** sản phẩm: chọn nhiều sản phẩm và số lượng tem, xuất trang in A4 dạng lưới.
- **Gợi ý nhập hàng** (mục 4.8).

### 5.3 Dashboard và báo cáo

- **Dashboard:**
  - Thẻ chỉ số: tổng số mặt hàng, tổng giá trị tồn (ADMIN/MANAGER), số phiếu nhập/xuất trong tháng, số phiếu chờ duyệt.
  - Danh sách hàng sắp hết.
  - Biểu đồ nhập – xuất theo ngày/tháng.
  - Top 10 sản phẩm xuất nhiều nhất.
  - Lọc theo kho.
- **Báo cáo nhập – xuất – tồn** theo khoảng thời gian và theo kho. Các cột: mã, tên, ĐVT, tồn đầu kỳ, nhập trong kỳ, xuất trong kỳ, tồn cuối kỳ, giá trị tồn cuối kỳ. Tính từ `stock_transactions`.
- **Thẻ kho** của một sản phẩm: ngày, mã chứng từ, diễn giải, nhập, xuất, tồn lũy kế.
- **Báo cáo hàng tồn lâu:** không phát sinh xuất trong N ngày (mặc định 90).
- Mọi báo cáo **xuất được Excel và PDF** (tiếng Việt có dấu, có tiêu đề, kỳ báo cáo, người xuất, thời gian xuất).

### 5.4 Ngoài phạm vi (không làm)

Kế toán, công nợ, hóa đơn điện tử; bán hàng/POS; thanh toán trực tuyến; ứng dụng di động riêng (chỉ web responsive); tích hợp sàn TMĐT/đơn vị vận chuyển; chuyển kho giữa các kho; quản lý vị trí khu – kệ – tầng – ô.

---

## 6. Dữ liệu

### 6.1 Các bảng chính (thiết kế chuẩn hóa, có khóa ngoại, chỉ mục cho cột lọc/tìm kiếm)

| Bảng | Nội dung chính |
|---|---|
| `users` | username (unique), email (unique), password_hash, full_name, phone, avatar_url, role, status (ACTIVE/LOCKED), must_change_password, timestamps |
| `user_warehouses` | user_id, warehouse_id |
| `warehouses` | code (unique), name, address, phone, manager_id, active |
| `categories`, `units` | code, name, mô tả, deleted_at |
| `products` | sku (unique), barcode (unique, có thể rỗng), name, category_id, unit_id, purchase_price, sale_price, min_stock, image_url, description, status, deleted_at |
| `suppliers`, `customers` | code (unique), name, phone, email, address, tax_code, note, deleted_at |
| `stock_receipts` + `stock_receipt_items` | phiếu nhập và chi tiết |
| `stock_issues` + `stock_issue_items` | phiếu xuất và chi tiết (lưu `unit_cost` khi hoàn tất) |
| `stocktakes` + `stocktake_items` | phiếu kiểm kê, số hệ thống, số thực tế, chênh lệch |
| `inventories` | product_id, warehouse_id (unique cặp), quantity, avg_cost, version |
| `stock_transactions` | product_id, warehouse_id, type (RECEIPT/ISSUE/ADJUSTMENT/REVERSAL), ref_type, ref_id, ref_code, quantity_change (±), unit_cost, balance_after, created_by, created_at |
| `document_sequences` | prefix, date, last_number |
| `notifications` | user_id, type, title, content, link, read_at, created_at |
| `audit_logs` | user_id, action, entity_type, entity_id, before_json, after_json, ip, created_at |
| `refresh_tokens`, `password_reset_tokens` | token đã băm, hạn dùng, trạng thái thu hồi |
| `app_settings` | các cấu hình: thời gian chờ hàng, số ngày dự trữ, số ngày tồn lâu, giờ gửi báo cáo |

- Tiền dùng `DECIMAL(18,2)`; số lượng dùng `DECIMAL(18,3)` để hỗ trợ đơn vị kg/lít.
- Mọi bảng có `created_at`, `updated_at`; bảng nghiệp vụ có `created_by`, `updated_by`.

### 6.2 Dữ liệu mẫu (seed)

Ngành hàng mẫu: **hàng tiêu dùng – tạp hóa (FMCG)**, tên sản phẩm và NCC tiếng Việt, thực tế.

- 3 kho/chi nhánh: Hà Nội, Đà Nẵng, TP.HCM.
- 10 loại hàng, 8 đơn vị tính, **150 sản phẩm** (có SKU, mã vạch EAN-13 hợp lệ, giá, mức tồn tối thiểu), 10 NCC, 20 khách hàng.
- **300+ phiếu nhập/xuất trải đều trong 6 tháng gần nhất**, đủ các trạng thái, một số phiếu bị hủy hoặc từ chối.
- 2 đợt kiểm kê.
- Một số sản phẩm cố ý nằm dưới mức tồn tối thiểu; một số sản phẩm không xuất trong hơn 90 ngày.
- **Seed phải đi qua chính `InventoryService`** (không chèn thẳng số tồn) để tồn kho, giá vốn và thẻ kho khớp nhau.
- Seed chạy bằng profile `demo` (`--spring.profiles.active=demo`) và có thể chạy lại (xóa và tạo lại).

Tài khoản demo (ghi trong README):

| Tên đăng nhập | Vai trò | Kho | Mật khẩu |
|---|---|---|---|
| `admin` | ADMIN | Tất cả | `Admin@123` |
| `quanly` | MANAGER | Hà Nội, Đà Nẵng | `Quanly@123` |
| `nhanvien` | STAFF | Hà Nội | `Nhanvien@123` |

---

## 7. Giao diện

- Phong cách **dashboard tối giản, chuyên nghiệp, gọn nhẹ**; màu chủ đạo **xanh dương** (`#1677ff` của Ant Design); có **chế độ tối** (dark mode, nhớ lựa chọn).
- **Tiếng Việt** toàn bộ: dùng locale `vi_VN` của Ant Design; định dạng ngày `dd/MM/yyyy`, tiền `1.250.000 ₫`.
- Ưu tiên màn hình máy tính, **responsive** cho điện thoại/máy tính bảng. Các màn hình lập phiếu, kiểm kê, quét mã phải dùng tốt trên điện thoại.
- Bố cục:
  - Thanh menu trái thu gọn được.
  - Header gồm bộ chọn kho đang làm việc, chuông thông báo, avatar, nút đổi sáng/tối.
  - Có breadcrumb.

**Danh sách trang:**
- Đăng nhập, Quên mật khẩu, Đặt lại mật khẩu, Dashboard.
- Sản phẩm (danh sách, thêm/sửa, chi tiết kèm thẻ kho), Loại hàng, Đơn vị tính, Kho, Nhà cung cấp, Khách hàng.
- Phiếu nhập, Phiếu xuất, Kiểm kê (danh sách, tạo/sửa, chi tiết, in).
- Tồn kho, Gợi ý nhập hàng, Báo cáo, In tem mã vạch.
- Người dùng, Hồ sơ cá nhân, Thông báo, Nhật ký hoạt động.

**Tìm kiếm – lọc – phân trang:**
- Sản phẩm: tìm theo mã/tên/mã vạch; lọc theo loại, kho, trạng thái tồn (còn hàng / sắp hết / hết hàng).
- Phiếu: lọc theo loại, trạng thái, kho, khoảng ngày, người lập; tìm theo mã phiếu.
- **Phân trang và sắp xếp phía server** cho mọi danh sách. Lưu bộ lọc trên URL.

**Màn hình lập phiếu:**
- Thêm dòng bằng ô tìm sản phẩm hoặc nút **quét mã**. Quét trùng sản phẩm thì tăng số lượng.
- Hiển thị tồn hiện có cạnh từng dòng; tô đỏ dòng xuất vượt tồn.
- Tổng tiền cập nhật ngay.

**Trải nghiệm:**
- Thông báo dạng toast.
- Hộp xác nhận trước khi xóa/hủy/duyệt/từ chối.
- Lỗi validation hiển thị ngay cạnh trường, bằng tiếng Việt.
- Skeleton khi tải dữ liệu; trạng thái rỗng có hướng dẫn.

**Tải ảnh:** ảnh sản phẩm và avatar định dạng JPG/PNG/WebP, tối đa 2 MB, kiểm tra cả MIME lẫn đuôi file. Lưu qua interface `FileStorageService`:
- Mặc định lưu thư mục local `uploads/`.
- Khi có biến môi trường Cloudinary thì tự chuyển sang Cloudinary (gói miễn phí), vì ổ đĩa của dịch vụ hosting miễn phí sẽ bị xóa khi khởi động lại.

---

## 8. Bảo mật, hiệu năng, kiểm thử

**Bảo mật:**
- BCrypt; JWT access token 15 phút; refresh token 7 ngày, xoay vòng mỗi lần dùng, lưu bản băm trong CSDL, thu hồi khi đăng xuất hoặc khi bị khóa tài khoản.
- Cookie refresh: `HttpOnly`, `Secure`, `SameSite=None` khi triển khai khác domain.
- Phân quyền theo vai trò và theo kho ở backend và frontend.
- Chống SQL Injection (JPA/tham số hóa, không nối chuỗi truy vấn), XSS (không render HTML thô), cấu hình CORS theo biến môi trường.
- Bắt buộc HTTPS khi triển khai.
- Mọi bí mật đặt trong biến môi trường; có `.env.example`. Không commit `.env`.

**Hiệu năng:**
- Đáp ứng khoảng 20 người dùng đồng thời.
- Trang danh sách phản hồi < 2 giây với 10.000 bản ghi: dùng chỉ mục, phân trang server, tránh N+1 (fetch join/`@EntityGraph`), dùng projection cho báo cáo.

**Kiểm thử:**
- Unit test cho `InventoryService` và các service phiếu: nhập cộng tồn, xuất trừ tồn, chặn xuất vượt tồn, giá vốn bình quân, hủy phiếu sinh giao dịch đảo ngược, kiểm kê điều chỉnh đúng, bất biến tồn = tổng giao dịch.
- **Integration test đồng thời:** 2 phiếu xuất cùng hoàn tất trên cùng sản phẩm mà tổng vượt tồn thì chỉ 1 phiếu thành công.
- Test phân quyền: STAFF không duyệt được phiếu, không thấy giá vốn, không truy cập được kho khác.
- Postman collection + environment trong `docs/postman/`, có script lấy token tự động.
- `docs/TEST_CASES.md`: bảng test case chức năng (mã TC, chức năng, tiền điều kiện, các bước, dữ liệu, kết quả mong đợi, kết quả thực tế), tối thiểu 40 ca, bao phủ các luồng chính và ngoại lệ.

---

## 9. Triển khai và demo

**Chạy local:**
- `docker compose --profile dev up -d`, sau đó chạy backend và frontend.
- Hoặc `docker compose --profile full up -d --build` để chạy toàn bộ bằng container.

**Triển khai trực tuyến miễn phí:**
- Frontend: **Netlify** (có file `netlify.toml`, cấu hình redirect cho SPA).
- Backend: **Render** (Web Service từ Dockerfile).
- MySQL: **Aiven for MySQL** (gói free).

README có mục hướng dẫn từng bước, các biến môi trường cần đặt, và cách xử lý việc Render free “ngủ” sau 15 phút không dùng (mở trước khi demo).

**Kịch bản demo** (ghi vào `docs/KICH_BAN_DEMO.md`):
1. `nhanvien` đăng nhập → tạo phiếu nhập bằng cách quét mã → gửi duyệt.
2. `quanly` nhận thông báo realtime → duyệt → tồn kho tăng, thẻ kho có dòng mới.
3. `nhanvien` tạo phiếu xuất vượt tồn → bị chặn kèm thông báo rõ ràng → sửa lại → xuất hợp lệ.
4. Tồn xuống dưới mức tối thiểu → cảnh báo trên chuông và dashboard.
5. Kiểm kê → chênh lệch → duyệt → tồn điều chỉnh.
6. Gợi ý nhập hàng → tạo phiếu nhập từ gợi ý.
7. Báo cáo nhập – xuất – tồn → xuất Excel/PDF.
8. `admin` xem nhật ký hoạt động, khóa một tài khoản.

---

## 10. Thứ tự thực hiện

Làm liên tục từ giai đoạn 1 đến giai đoạn 8. Mỗi giai đoạn phải đạt điều kiện “Xong khi” trước khi sang giai đoạn sau.

| # | Giai đoạn | Xong khi |
|---|---|---|
| 1 | Khung dự án: cấu trúc thư mục, Docker Compose (MySQL + Mailpit), Spring Boot + Flyway, React + Vite + Ant Design, layout chính, `CLAUDE.md`, `docs/SPEC.md`, README sơ bộ | `docker compose --profile dev up` + backend + frontend chạy được; Swagger mở được |
| 2 | Schema CSDL đầy đủ (migration Flyway), entity, repository | Migration chạy sạch trên CSDL trống |
| 3 | Xác thực và phân quyền: đăng nhập, refresh, đăng xuất, quên/đặt lại/đổi mật khẩu, quản lý người dùng, gán kho, hồ sơ cá nhân, nhật ký đăng nhập | Test phân quyền pass; email đặt lại mật khẩu hiển thị trong Mailpit |
| 4 | Danh mục: kho, loại hàng, ĐVT, sản phẩm (ảnh, mã vạch), NCC, khách hàng; tìm kiếm/lọc/phân trang; xóa mềm | CRUD đầy đủ trên giao diện, validation hai phía |
| 5 | **Lõi kho:** `InventoryService`, phiếu nhập, phiếu xuất, luồng duyệt, hủy, mã phiếu, tồn kho, thẻ kho, in phiếu PDF, thông báo (web + SSE + email), nhật ký | Toàn bộ unit test và test đồng thời pass |
| 6 | Seed dữ liệu demo; dashboard; báo cáo nhập – xuất – tồn, tồn lâu; xuất Excel/PDF; email tồn thấp hằng ngày | Số liệu báo cáo khớp với thẻ kho trên dữ liệu seed |
| 7 | Mở rộng: kiểm kê, quét mã bằng camera, in tem mã vạch, gợi ý nhập hàng, dark mode, rà soát responsive | Chạy trọn kịch bản demo trên trình duyệt điện thoại (giả lập) |
| 8 | Hoàn thiện: Dockerfile, profile `full`, cấu hình Netlify/Render/Aiven, Postman, test case, sơ đồ, hướng dẫn sử dụng, README hoàn chỉnh; rà soát bảo mật và hiệu năng | Toàn bộ mục kiểm tra cuối (mục 12) đạt |

**Tài liệu giải thích:** sau mỗi giai đoạn, viết `docs/giai-thich/0X-ten-giai-doan.md` (văn phong dễ hiểu cho người mới chuyển từ PHP sang Java/React) gồm:
- Mục tiêu của giai đoạn.
- Các file chính và vai trò của từng file.
- Luồng xử lý của một request tiêu biểu: frontend → controller → service → repository → CSDL.
- Các khái niệm mới (ví dụ `@Transactional`, JPA, JWT, React Query); khi phù hợp, đối chiếu với khái niệm tương đương trong PHP/Laravel.
- 3–5 câu hỏi hội đồng có thể hỏi về phần này, kèm gợi ý trả lời.

---

## 11. Tài liệu cần tạo trong `docs/`

- `diagrams/`: dạng **PlantUML (.puml)** và bản **Mermaid**, có lệnh/hướng dẫn xuất PNG:
  - Use case tổng quát và use case chi tiết cho từng phân hệ (người dùng, danh mục, nhập kho, xuất kho, kiểm kê, báo cáo).
  - Activity: nhập kho, xuất kho, kiểm kê.
  - Sequence: lập và duyệt phiếu nhập; xuất kho với kiểm tra tồn.
  - ERD và lược đồ CSDL (khớp với migration thực tế).
  - Sơ đồ kiến trúc hệ thống (client – API – DB – mail – hosting).
- `DAC_TA_USE_CASE.md`: đặc tả use case dạng bảng (tên, tác nhân, mô tả, tiền điều kiện, luồng chính, luồng thay thế/ngoại lệ, hậu điều kiện) cho các use case chính.
- `HUONG_DAN_SU_DUNG.md`: hướng dẫn cho từng vai trò, kèm ảnh chụp màn hình. Viết script Playwright `docs/screenshots/capture.ts` để tự chụp các màn hình từ dữ liệu demo.
- `TEST_CASES.md`, `KICH_BAN_DEMO.md`, `GIA_DINH.md`, thư mục `postman/`, thư mục `giai-thich/`.
- Tài liệu dùng cho báo cáo thực tập (tiếng Việt) theo các chương: Mở đầu – Cơ sở lý thuyết – Phân tích thiết kế hệ thống – Cài đặt và kiểm thử – Kết luận. Không cần viết báo cáo hoàn chỉnh, chỉ cần tài liệu nguồn đủ để trích vào báo cáo.

---

## 12. Kiểm tra cuối cùng trước khi kết thúc

- [ ] Build backend và frontend không lỗi, không cảnh báo ESLint nghiêm trọng; toàn bộ test pass.
- [ ] Chạy từ CSDL trống: migration + seed `demo` thành công; ba tài khoản demo đăng nhập được.
- [ ] Đi hết 8 bước kịch bản demo không lỗi, kể cả trên khung nhìn điện thoại.
- [ ] Tồn kho khớp tổng `stock_transactions` cho mọi sản phẩm × kho (viết truy vấn kiểm tra trong `docs/`).
- [ ] STAFF không xem được giá vốn qua cả giao diện lẫn gọi API trực tiếp.
- [ ] File Excel/PDF xuất ra hiển thị đúng tiếng Việt có dấu.
- [ ] README có: giới thiệu, công nghệ, yêu cầu cài đặt (Windows), các bước chạy local, biến môi trường, tài khoản demo, triển khai, cấu trúc thư mục.
- [ ] Không còn TODO, mã chết, `console.log` gỡ lỗi; không lộ bí mật trong repo.

Kết thúc bằng một bản tóm tắt: những gì đã làm, cách chạy, các giả định trong `docs/GIA_DINH.md`, và các hạn chế còn lại.
