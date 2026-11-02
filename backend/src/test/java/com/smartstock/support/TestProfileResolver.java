package com.smartstock.support;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Chọn hồ sơ cấu hình cho test lúc chạy, thay vì cố định bằng {@code @ActiveProfiles("test")}.
 *
 * <p><b>Vì sao cần lớp này:</b> annotation {@code @ActiveProfiles("test")} ghi cứng hồ sơ
 * trong mã nguồn và **ghi đè** mọi tham số dòng lệnh. Nghĩa là
 * {@code mvnw test -Dspring.profiles.active=mysqltest} sẽ KHÔNG có tác dụng - bộ test vẫn
 * chạy trên H2 dù người chạy tưởng đang kiểm thử trên MySQL. Đó là loại nhầm lẫn nguy
 * hiểm: ta tin là đã kiểm chứng trên MySQL thật trong khi chưa.
 *
 * <p>Lớp này đọc tham số lúc chạy và trả về hồ sơ tương ứng:
 *
 * <ul>
 *   <li>Mặc định: hồ sơ {@code test} - H2 trong bộ nhớ, chạy ngay không cần cài gì.</li>
 *   <li>Khi có {@code -DtestDb=mysql} hoặc biến môi trường {@code TEST_DB=mysql}:
 *       hồ sơ {@code mysqltest} - MySQL thật, Flyway tạo schema, kiểm chứng được hành vi
 *       khóa dòng {@code SELECT ... FOR UPDATE}.</li>
 * </ul>
 *
 * <p>Cách chạy trên MySQL thật:
 *
 * <pre>
 * # Bước 1: khởi động MySQL và tạo CSDL test
 * docker compose --profile dev up -d
 * docker compose exec mysql mysql -uroot -proot123 -e "CREATE DATABASE IF NOT EXISTS smartstock_test CHARACTER SET utf8mb4; GRANT ALL ON smartstock_test.* TO 'smartstock'@'%';"
 *
 * # Bước 2: chạy test
 * .\mvnw test -D"testDb=mysql"
 * </pre>
 */
public class TestProfileResolver implements ActiveProfilesResolver {

    private static final String MYSQL_PROFILE = "mysqltest";
    private static final String DEFAULT_PROFILE = "test";

    @Override
    public String[] resolve(Class<?> testClass) {
        // Ưu tiên tham số -D trên dòng lệnh, sau đó tới biến môi trường
        String requested = System.getProperty("testDb");
        if (requested == null || requested.isBlank()) {
            requested = System.getenv("TEST_DB");
        }

        if (requested != null && "mysql".equalsIgnoreCase(requested.trim())) {
            return new String[] {MYSQL_PROFILE};
        }
        return new String[] {DEFAULT_PROFILE};
    }
}
