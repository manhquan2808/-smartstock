# SmartStock - Website quản lý kho đa chi nhánh

Hệ thống quản lý kho cho doanh nghiệp thương mại vừa và nhỏ có nhiều kho/chi nhánh, tích
hợp quét mã vạch/QR. Đề tài thực tập doanh nghiệp ngành Công nghệ phần mềm.

![Java](https://img.shields.io/badge/Java-21_LTS-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green)
![React](https://img.shields.io/badge/React-18-blue)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)

---

## 1. Giới thiệu

SmartStock giải quyết ba vấn đề thường gặp khi quản lý kho bằng Excel:

| Vấn đề với Excel | Cách SmartStock xử lý |
|---|---|
| Số tồn sai khi nhiều người cùng sửa file | Mọi thay đổi tồn đi qua một service duy nhất, có khóa dòng ở cơ sở dữ liệu |
| Không biết ai sửa gì, lúc nào | Nhật ký hoạt động ghi người thực hiện, thời điểm, IP, dữ liệu trước/sau |
| Xuất bán quá số hàng thực có | Hệ thống chặn xuất vượt tồn, kiểm tra lại ngay trước khi ghi nhận |

**Chức năng chính:**

- Quy trình nhập kho, xuất kho có luồng duyệt (nháp → chờ duyệt → hoàn tất).
- Tồn kho theo từng kho, giá vốn bình quân gia quyền, thẻ kho chi tiết.
- Kiểm kê kho và điều chỉnh chênh lệch.
- Phân quyền ba vai trò, giới hạn theo kho được gán.
- Dashboard, báo cáo nhập - xuất - tồn, hàng tồn lâu; xuất Excel và PDF.
- Gợi ý nhập hàng dựa trên lịch sử xuất.
- Quét mã vạch/QR bằng camera điện thoại hoặc máy quét USB; in tem mã vạch.
- Thông báo trong web (realtime) và email; email báo cáo tồn thấp hằng ngày.

---

## 2. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21 LTS, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Mail), Maven |
| Cơ sở dữ liệu | MySQL 8, quản lý schema bằng Flyway |
| Frontend | React 18, Vite, TypeScript, Ant Design 5, React Router, TanStack Query, Axios, Recharts |
| Xác thực | JWT (access token 15 phút trong bộ nhớ, refresh token 7 ngày trong cookie HttpOnly), mật khẩu băm BCrypt |
| Tài liệu API | springdoc-openapi (Swagger UI) |
| Realtime | Server-Sent Events (SSE) |
| Email | Spring Mail; dev dùng Mailpit, production dùng Gmail App Password |
| Xuất file | Apache POI (Excel), OpenPDF + font Noto Sans (PDF tiếng Việt có dấu) |
| Mã vạch/QR | html5-qrcode (quét), JsBarcode + qrcode (in tem) |
| Kiểm thử | JUnit 5, Mockito, Spring Boot Test, AssertJ |
| Hạ tầng | Git, Docker Compose |

---

## 3. Yêu cầu cài đặt (Windows 10/11)

| Phần mềm | Phiên bản | Ghi chú |
|---|---|---|
| JDK | 21 LTS | [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21) - chọn bản `.msi` |
| Node.js | 20 trở lên | [nodejs.org](https://nodejs.org) |
| Docker Desktop | mới nhất | Để chạy MySQL và Mailpit |
| Git | mới nhất | |
| VS Code | mới nhất | Khuyến nghị cài extension *Extension Pack for Java* và *ESLint* |

Kiểm tra sau khi cài (mở PowerShell):

```powershell
java -version    # phải hiện 21.x
node -v          # phải hiện v20 trở lên
docker --version
```

> **Không cần cài Maven**: dự án có sẵn Maven Wrapper (`mvnw`), tự tải Maven khi chạy lần đầu.

---

## 4. Chạy trên máy cá nhân

### Bước 1. Tải mã nguồn và tạo file cấu hình

```powershell
git clone <địa-chỉ-repository> smartstock
cd smartstock
Copy-Item .env.example .env
```

Mở file `.env`, đổi `JWT_SECRET` thành một chuỗi riêng (tối thiểu 32 ký tự). Các giá trị
khác để nguyên là chạy được ở môi trường phát triển.

### Bước 2. Khởi động MySQL và Mailpit

```powershell
docker compose --profile dev up -d

# Kiểm tra: cả hai container phải ở trạng thái "healthy"/"running"
docker compose ps
```

Profile `dev` chỉ chạy MySQL + Mailpit (khoảng 600 MB RAM), phù hợp máy 8 GB.

### Bước 3. Chạy backend

```powershell
cd backend
.\mvnw spring-boot:run -D"spring-boot.run.profiles=demo"
```

Profile `demo` tự tạo dữ liệu mẫu ở lần chạy đầu: 3 kho, 150 sản phẩm, hơn 300 phiếu
nhập/xuất trong 6 tháng gần nhất, 2 đợt kiểm kê. Quá trình này mất khoảng 15-30 giây.

Khi thấy dòng `Started SmartStockApplication`, backend đã sẵn sàng:

- API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

> Lần đầu chạy sẽ mất vài phút để Maven tải thư viện.

### Bước 4. Chạy frontend

Mở **cửa sổ PowerShell thứ hai**:

```powershell
cd smartstock\frontend
npm install
npm run dev
```

Mở <http://localhost:5173> và đăng nhập bằng tài khoản demo ở mục 6.

### Cách khác: chạy tất cả bằng Docker

```powershell
docker compose --profile full up -d --build
```

- Frontend: <http://localhost:3000>
- Backend: <http://localhost:8080>

Cách này tiện để demo nhưng tốn RAM hơn và không có tự nạp lại khi sửa mã.

---

## 5. Biến môi trường

Tất cả được khai báo trong `.env.example`. Những biến quan trọng:

| Biến | Mặc định (dev) | Ý nghĩa |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/smartstock...` | Chuỗi kết nối MySQL |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | `smartstock` / `smartstock123` | Tài khoản CSDL |
| `JWT_SECRET` | (phải đổi) | Khóa ký JWT, **tối thiểu 32 ký tự** |
| `JWT_ACCESS_TOKEN_MINUTES` | `15` | Hạn access token |
| `JWT_REFRESH_TOKEN_DAYS` | `7` | Hạn refresh token |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Domain frontend được phép gọi API |
| `APP_COOKIE_SAME_SITE` | `lax` | Đặt `none` khi frontend/backend khác domain |
| `APP_COOKIE_SECURE` | `false` | Đặt `true` khi dùng HTTPS |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | SMTP (Mailpit khi dev) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | (trống) | Gmail App Password khi chạy thật |
| `APP_FRONTEND_URL` | `http://localhost:5173` | Dùng để tạo link trong email |
| `CLOUDINARY_URL` | (trống) | Có giá trị thì tự lưu ảnh lên Cloudinary |
| `VITE_API_BASE_URL` | (trống) | Địa chỉ backend cho frontend; để trống khi dev |

---

## 6. Tài khoản demo

Có sẵn sau khi chạy profile `demo`:

| Tên đăng nhập | Mật khẩu | Vai trò | Kho phụ trách |
|---|---|---|---|
| `admin` | `Admin@123` | Quản trị hệ thống | Tất cả kho |
| `quanly` | `Quanly@123` | Quản lý kho | Hà Nội, Đà Nẵng |
| `nhanvien` | `Nhanvien@123` | Nhân viên kho | Hà Nội |

Ba tài khoản này cho thấy rõ sự khác biệt về phân quyền: `nhanvien` không thấy giá vốn,
không duyệt được phiếu và không truy cập được kho TP.HCM.

Đọc email hệ thống gửi (đặt lại mật khẩu, phiếu chờ duyệt) tại Mailpit:
<http://localhost:8025>

---

## 7. Phân quyền

| Chức năng | ADMIN | MANAGER | STAFF |
|---|---|---|---|
| Quản lý người dùng, gán vai trò, gán kho, khóa/mở khóa | ✔ | ✘ | ✘ |
| CRUD kho | ✔ | Chỉ xem | Chỉ xem kho được gán |
| CRUD loại hàng, ĐVT, sản phẩm, NCC, khách hàng | ✔ | ✔ | Chỉ xem |
| Lập/sửa/xóa phiếu nháp của mình, gửi duyệt | ✔ | ✔ | ✔ (kho được gán) |
| Duyệt/từ chối phiếu, hủy phiếu đã hoàn tất | ✔ | ✔ (kho được gán) | ✘ |
| Tạo phiếu kiểm kê, nhập số thực tế | ✔ | ✔ | ✔ |
| Duyệt kết quả kiểm kê | ✔ | ✔ | ✘ |
| Xem tồn kho | Tất cả kho | Kho được gán | Kho được gán |
| Xem giá nhập, giá vốn, giá trị tồn | ✔ | ✔ | ✘ (chỉ thấy đơn giá trên phiếu mình lập) |
| Dashboard, báo cáo, xuất Excel/PDF | ✔ | ✔ | Dashboard rút gọn |
| Nhật ký hoạt động | ✔ | ✘ | ✘ |

Phân quyền được kiểm tra ở **cả hai phía**: backend chặn mọi API và lược bỏ dữ liệu giá
vốn trong DTO; frontend ẩn menu và nút để giao diện gọn gàng.

---

## 8. Triển khai trực tuyến (miễn phí)

Kiến trúc triển khai: **Netlify** (frontend) + **Render** (backend) + **Aiven for MySQL** (CSDL).

### 8.1. Cơ sở dữ liệu - Aiven for MySQL

1. Tạo tài khoản tại [aiven.io](https://aiven.io), chọn **MySQL** gói **Free**.
2. Chờ service chuyển sang trạng thái *Running* (khoảng 3-5 phút).
3. Vào tab **Overview**, lấy Host, Port, Database, User, Password.
4. Dựng chuỗi JDBC (lưu lại để dùng ở bước sau):

```
jdbc:mysql://<host>:<port>/<database>?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Ho_Chi_Minh&sslMode=REQUIRED
```

> Aiven bắt buộc dùng SSL, nên phải có `sslMode=REQUIRED`. Flyway sẽ tự tạo toàn bộ bảng
> ở lần khởi động đầu tiên của backend.

### 8.2. Backend - Render

1. Tạo tài khoản [render.com](https://render.com), chọn **New + → Web Service**.
2. Kết nối repository, cấu hình:
   - **Runtime**: Docker
   - **Root Directory**: `backend`
   - **Health Check Path**: `/actuator/health`
   - **Instance Type**: Free
3. Thêm các biến môi trường (tab **Environment**):

| Biến | Giá trị |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | chuỗi JDBC ở bước 8.1 |
| `SPRING_DATASOURCE_USERNAME` | user của Aiven |
| `SPRING_DATASOURCE_PASSWORD` | password của Aiven |
| `JWT_SECRET` | chuỗi ngẫu nhiên tối thiểu 32 ký tự |
| `APP_CORS_ALLOWED_ORIGINS` | `https://<ten-site>.netlify.app` |
| `APP_FRONTEND_URL` | `https://<ten-site>.netlify.app` |
| `APP_COOKIE_SAME_SITE` | `none` |
| `APP_COOKIE_SECURE` | `true` |
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_SMTP_AUTH` | `true` |
| `MAIL_SMTP_STARTTLS` | `true` |
| `MAIL_USERNAME` | email Gmail của bạn |
| `MAIL_PASSWORD` | [App Password](https://myaccount.google.com/apppasswords) 16 ký tự |
| `MAIL_FROM` | email Gmail của bạn |
| `CLOUDINARY_URL` | `cloudinary://<api_key>:<api_secret>@<cloud_name>` |

4. Bấm **Create Web Service**. Lần build đầu mất khoảng 5-8 phút.

> Có thể dùng **New + → Blueprint** và trỏ tới file `render.yaml` có sẵn trong repo để
> Render tự dựng cấu hình.

### 8.3. Frontend - Netlify

1. Tạo tài khoản [netlify.com](https://netlify.com), chọn **Add new site → Import an existing project**.
2. Chọn repository. Netlify tự đọc `frontend/netlify.toml` nên không cần cấu hình build.
3. Vào **Site settings → Environment variables**, thêm:
   - `VITE_API_BASE_URL` = `https://<ten-dich-vu>.onrender.com`
4. Bấm **Deploy**. Sau khi xong, quay lại Render cập nhật `APP_CORS_ALLOWED_ORIGINS`
   và `APP_FRONTEND_URL` thành domain Netlify thật, rồi deploy lại backend.

### 8.4. Tạo dữ liệu demo trên môi trường trực tuyến

Đổi `SPRING_PROFILES_ACTIVE` thành `demo`, deploy lại một lần để sinh dữ liệu, sau đó
đổi về `prod` để tránh seed lại.

### 8.5. Lưu ý quan trọng về gói miễn phí của Render

Dịch vụ **"ngủ" sau 15 phút không có request**. Lần gọi đầu sau khi ngủ mất khoảng
30-60 giây.

**Trước buổi demo**, hãy mở đường dẫn sau trước 2-3 phút để dịch vụ thức hẳn:

```
https://<ten-dich-vu>.onrender.com/actuator/health
```

Khi thấy trả về `{"status":"UP"}` là có thể bắt đầu demo.

Ngoài ra, ổ đĩa của Render là tạm: **ảnh tải lên sẽ mất sau mỗi lần khởi động lại**.
Vì vậy nên đặt `CLOUDINARY_URL` để hệ thống tự lưu ảnh lên Cloudinary (gói miễn phí
25 GB băng thông/tháng, đủ cho đồ án).

---

## 9. Tài liệu trong thư mục `docs/`

| File | Nội dung |
|---|---|
| `SPEC.md` | Đặc tả yêu cầu đầy đủ của hệ thống |
| `GIA_DINH.md` | Các giả định và quyết định thiết kế kèm lý do |
| `KICH_BAN_DEMO.md` | Kịch bản demo 8 bước, có câu dẫn cho người trình bày |
| `TEST_CASES.md` | 56 test case chức năng (TC01–TC56), có đánh dấu test case nào đã tự động hóa |
| `DAC_TA_USE_CASE.md` | Đặc tả 15 use case dạng bảng |
| `HUONG_DAN_SU_DUNG.md` | Hướng dẫn sử dụng theo từng vai trò, kèm 27 ảnh chụp màn hình |
| `TRUY_VAN_KIEM_TRA.sql` | Truy vấn SQL đối chiếu tính đúng đắn của dữ liệu kho |
| `BAO_CAO_THUC_TAP.md` | Tài liệu nguồn để trích vào báo cáo thực tập |
| `giai-thich/` | 8 tài liệu giải thích theo giai đoạn cho người mới chuyển từ PHP sang Java/React, kèm 41 câu hỏi hội đồng |
| `diagrams/` | 10 sơ đồ (use case, activity, sequence, ERD, kiến trúc), mỗi sơ đồ có bản PlantUML và bản Mermaid, kèm hướng dẫn xuất PNG |
| `postman/` | 86 request có assertion, chạy được bằng Newman |
| `screenshots/` | Script Playwright tự chụp 27 màn hình từ dữ liệu demo |

---

## 10. Cấu trúc thư mục

```
smartstock/
├── backend/              # Spring Boot (Java 21)
│   ├── src/main/java/com/smartstock/
│   ├── src/main/resources/db/migration/   # Flyway V1..V7
│   ├── src/test/java/                     # JUnit 5
│   └── Dockerfile
├── frontend/             # React + Vite + TypeScript
│   ├── src/
│   ├── Dockerfile, nginx.conf, netlify.toml
│   └── package.json
├── docs/                 # Tài liệu, sơ đồ, test case, Postman
├── docker-compose.yml    # Profile dev (MySQL + Mailpit) và full
├── render.yaml           # Cấu hình triển khai Render
├── .env.example
├── CLAUDE.md             # Quy ước dự án dành cho người phát triển
└── README.md
```

Chi tiết cấu trúc mã nguồn và quy ước phát triển: xem [CLAUDE.md](CLAUDE.md).

---

## 11. Xử lý sự cố thường gặp

| Hiện tượng | Nguyên nhân và cách xử lý |
|---|---|
| `Port 8080 was already in use` | Có ứng dụng khác chiếm cổng. Tìm và tắt: `Get-NetTCPConnection -LocalPort 8080` rồi `Stop-Process -Id <PID>` |
| `Communications link failure` khi khởi động backend | MySQL chưa chạy. Kiểm tra `docker compose ps`, chạy lại `docker compose --profile dev up -d` |
| `Cấu hình JWT_SECRET phải có tối thiểu 32 ký tự` | Mở `.env`, đặt `JWT_SECRET` dài hơn 32 ký tự |
| Frontend báo lỗi CORS | `APP_CORS_ALLOWED_ORIGINS` chưa có domain frontend. Khi dev, Vite đã proxy sẵn nên thường không gặp |
| Vite chạy ở cổng 5174 thay vì 5173 | Cổng 5173 đang bị chiếm. Không ảnh hưởng: proxy của Vite đã xử lý, vẫn đăng nhập được bình thường |
| Không mở được camera khi quét mã | Trình duyệt chỉ cho dùng camera trên HTTPS hoặc localhost. Dùng tab "Nhập mã bằng tay" hoặc máy quét USB |
| Email không thấy trong Mailpit | Kiểm tra `MAIL_HOST=localhost`, `MAIL_PORT=1025` và container mailpit đang chạy |
| Xuất Excel/PDF lỗi trên server | Ảnh Docker thiếu `fontconfig`. Dockerfile của dự án đã cài sẵn gói này |
| Schema-validation error khi khởi động | Entity không khớp schema. Tạo migration Flyway mới cho thay đổi, không sửa file cũ |

---

## 12. Giấy phép và ghi nhận

Dự án dùng hoàn toàn thư viện mã nguồn mở, miễn phí:

- Spring Boot, Spring Security (Apache 2.0)
- Ant Design, React (MIT)
- Apache POI (Apache 2.0), OpenPDF (LGPL/MPL)
- Noto Sans (SIL Open Font License 1.1)
- html5-qrcode, JsBarcode, qrcode (MIT)
