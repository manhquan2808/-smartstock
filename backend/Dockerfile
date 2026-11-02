# ======================================================================
# Dockerfile backend SmartStock - build nhiều tầng (multi-stage).
#
# Tầng 1 (builder): biên dịch mã nguồn thành file JAR.
# Tầng 2 (runtime) : chỉ chứa JRE và file JAR -> ảnh nhỏ hơn nhiều so với
#                    việc đóng gói cả Maven và mã nguồn.
# ======================================================================

# ---------------------- Tầng 1: biên dịch ----------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml trước và tải thư viện riêng một bước.
# Nhờ vậy khi chỉ sửa mã nguồn (pom.xml không đổi), Docker dùng lại lớp cache
# thư viện đã tải, build lại nhanh hơn rất nhiều.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Bỏ qua test khi đóng gói ảnh: test đã chạy ở bước CI/local trước đó
RUN mvn -B -ntp clean package -DskipTests

# ---------------------- Tầng 2: chạy ----------------------
FROM eclipse-temurin:21-jre-jammy

# Cài fontconfig: Apache POI và OpenPDF cần thư viện font của hệ điều hành
# khi đo chữ. Thiếu gói này, chức năng xuất Excel/PDF sẽ lỗi trên ảnh gọn.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig tzdata curl \
    && rm -rf /var/lib/apt/lists/*

# Chạy bằng người dùng không phải root để giảm thiệt hại nếu ứng dụng bị khai thác
RUN groupadd --system smartstock && useradd --system --gid smartstock --create-home smartstock

WORKDIR /app

COPY --from=builder /build/target/smartstock-backend.jar app.jar

# Thư mục lưu ảnh tải lên (khi không dùng Cloudinary)
RUN mkdir -p /app/uploads && chown -R smartstock:smartstock /app

USER smartstock

ENV TZ=Asia/Ho_Chi_Minh \
    SPRING_PROFILES_ACTIVE=prod \
    # Giới hạn heap theo 70% RAM container, phù hợp cả gói hosting miễn phí 512 MB
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080

# Kiểm tra sức khỏe: dịch vụ hosting dùng endpoint này để biết ứng dụng đã sẵn sàng
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Dùng shell form để biến $JAVA_OPTS được thay thế
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
