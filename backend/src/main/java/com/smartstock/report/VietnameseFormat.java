package com.smartstock.report;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Định dạng số, tiền và ngày theo quy ước Việt Nam, dùng cho file Excel và PDF.
 *
 * <p>Quy ước:
 *
 * <ul>
 *   <li>Ngày: {@code 31/10/2026}; thời điểm: {@code 31/10/2026 14:30}.</li>
 *   <li>Tiền: {@code 1.250.000 ₫} (dấu chấm phân cách nghìn, khác với tiếng Anh).</li>
 *   <li>Số lượng: {@code 1.250} hoặc {@code 2,5} - bỏ phần thập phân nếu bằng 0.</li>
 * </ul>
 *
 * <p>Frontend định dạng riêng bằng locale {@code vi_VN} của Ant Design; lớp này phục vụ
 * các file xuất ra từ backend để hai bên nhìn giống nhau.
 */
public final class VietnameseFormat {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Mẫu định dạng tiền dùng trong file Excel (Excel tự áp dụng khi mở). */
    public static final String EXCEL_MONEY_PATTERN = "#,##0";

    public static final String EXCEL_QUANTITY_PATTERN = "#,##0.###";

    private static final DecimalFormatSymbols VIETNAMESE_SYMBOLS = createSymbols();

    private VietnameseFormat() {}

    private static DecimalFormatSymbols createSymbols() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("vi", "VN"));
        // Đặt tường minh để không phụ thuộc cấu hình vùng của máy chủ
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return symbols;
    }

    /** Ví dụ: {@code 1250000} -> {@code "1.250.000"} */
    public static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return new DecimalFormat("#,##0", VIETNAMESE_SYMBOLS).format(value);
    }

    /** Ví dụ: {@code 1250000} -> {@code "1.250.000 ₫"} */
    public static String currency(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return money(value) + " ₫";
    }

    /** Số lượng: bỏ các chữ số 0 vô nghĩa ở cuối. Ví dụ {@code 2.500} -> {@code "2,5"} */
    public static String quantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return new DecimalFormat("#,##0.###", VIETNAMESE_SYMBOLS).format(value);
    }

    public static String date(LocalDate value) {
        return value == null ? "" : value.format(DATE);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    public static String dateTimeWithSeconds(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_SECONDS);
    }

    /**
     * Đọc số tiền thành chữ tiếng Việt, dùng ở dòng "Bằng chữ" trên phiếu in.
     *
     * <p>Ví dụ: {@code 1250000} -> {@code "Một triệu hai trăm năm mươi nghìn đồng"}.
     */
    public static String moneyToWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "Không đồng";
        }
        long value = amount.longValue();
        if (value < 0) {
            return "Âm " + moneyToWords(amount.negate().abs()).toLowerCase();
        }

        String words = readGroups(value).trim();
        if (words.isEmpty()) {
            return "Không đồng";
        }
        // Viết hoa chữ đầu câu
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " đồng";
    }

    private static final String[] DIGITS = {
        "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

    private static final String[] GROUP_NAMES = {"", " nghìn", " triệu", " tỷ"};

    /** Tách số thành các nhóm 3 chữ số rồi đọc từng nhóm kèm đơn vị nghìn/triệu/tỷ. */
    private static String readGroups(long value) {
        if (value == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int groupIndex = 0;
        boolean hasHigherGroup = false;

        // Đọc từ nhóm thấp lên cao rồi chèn vào đầu chuỗi
        while (value > 0) {
            int group = (int) (value % 1000);
            if (group > 0) {
                String groupText = readThreeDigits(group, hasHigherGroup || value >= 1000);
                result.insert(0, groupText + GROUP_NAMES[Math.min(groupIndex, GROUP_NAMES.length - 1)] + " ");
                hasHigherGroup = true;
            }
            value /= 1000;
            groupIndex++;
        }
        return result.toString();
    }

    /**
     * Đọc một nhóm 3 chữ số.
     *
     * @param readLeadingZeroHundred true thì đọc cả "không trăm" (ví dụ 1.025 -> "một
     *     nghìn không trăm hai mươi lăm")
     */
    private static String readThreeDigits(int number, boolean readLeadingZeroHundred) {
        int hundreds = number / 100;
        int tens = (number % 100) / 10;
        int units = number % 10;

        StringBuilder text = new StringBuilder();

        if (hundreds > 0) {
            text.append(DIGITS[hundreds]).append(" trăm");
        } else if (readLeadingZeroHundred && (tens > 0 || units > 0)) {
            text.append("không trăm");
        }

        if (tens > 1) {
            text.append(" ").append(DIGITS[tens]).append(" mươi");
            if (units == 1) {
                text.append(" mốt"); // 21 = hai mươi mốt (không phải "hai mươi một")
            } else if (units == 5) {
                text.append(" lăm"); // 25 = hai mươi lăm (không phải "hai mươi năm")
            } else if (units > 0) {
                text.append(" ").append(DIGITS[units]);
            }
        } else if (tens == 1) {
            text.append(" mười");
            if (units == 5) {
                text.append(" lăm"); // 15 = mười lăm
            } else if (units > 0) {
                text.append(" ").append(DIGITS[units]);
            }
        } else if (units > 0) {
            if (hundreds > 0 || text.length() > 0) {
                text.append(" lẻ"); // 105 = một trăm lẻ năm
            }
            text.append(" ").append(DIGITS[units]);
        }

        return text.toString().trim();
    }
}
