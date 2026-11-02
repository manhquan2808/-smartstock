package com.smartstock.common;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Xuất kho vượt tồn. Lỗi này liệt kê rõ TỪNG sản phẩm thiếu, tồn hiện có và
 * số lượng yêu cầu để người dùng biết phải sửa dòng nào.
 */
@Getter
public class InsufficientStockException extends BusinessException {

    private final transient List<ShortageDetail> shortages;

    public InsufficientStockException(List<ShortageDetail> shortages) {
        super(buildMessage(shortages), "INSUFFICIENT_STOCK", HttpStatus.BAD_REQUEST);
        this.shortages = shortages;
    }

    private static String buildMessage(List<ShortageDetail> shortages) {
        StringBuilder sb = new StringBuilder("Không đủ tồn kho để xuất: ");
        for (int i = 0; i < shortages.size(); i++) {
            ShortageDetail s = shortages.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(String.format(
                    "%s (%s) cần %s nhưng chỉ còn %s",
                    s.productName(), s.sku(), strip(s.requested()), strip(s.available())));
        }
        return sb.append('.').toString();
    }

    /** Bỏ các số 0 vô nghĩa ở cuối phần thập phân: 5.000 -> 5 */
    private static String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Chi tiết một sản phẩm bị thiếu tồn. */
    public record ShortageDetail(
            Long productId, String sku, String productName, BigDecimal requested, BigDecimal available) {}
}
