package com.smartstock.inventory.projection;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Chuyển đổi dữ liệu thô của truy vấn gộp nhóm sang kiểu Java mong muốn.
 *
 * <p><b>Vì sao cần lớp này:</b> các truy vấn báo cáo dùng {@code SUM(CASE WHEN ... END)}
 * và {@code COALESCE(..., 0)}. Với những biểu thức đó, Hibernate không suy ra được kiểu
 * trả về một cách chắc chắn (có thể là Long, Double hay BigDecimal tùy câu lệnh), nên
 * cú pháp {@code SELECT new MyRecord(...)} sẽ báo lỗi "Missing constructor".
 *
 * <p>Giải pháp: truy vấn trả về mảng {@code Object[]}, rồi các record projection tự
 * chuyển đổi qua các hàm ở đây. Cách này tường minh và không phụ thuộc vào suy luận kiểu
 * của Hibernate.
 */
public final class ProjectionMapper {

    private ProjectionMapper() {}

    /** Chuyển một ô dữ liệu bất kỳ sang BigDecimal; null được coi là 0. */
    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    public static String toText(Object value) {
        return value == null ? null : value.toString();
    }

    /** Chuyển ô ngày sang LocalDate, chấp nhận cả java.sql.Date và LocalDateTime. */
    public static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    /** Chuyển ô thời điểm sang LocalDateTime. */
    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
}
