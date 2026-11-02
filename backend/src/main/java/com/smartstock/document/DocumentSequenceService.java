package com.smartstock.document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinh mã phiếu theo định dạng {@code {TIỀN_TỐ}-{yyyyMMdd}-{3 chữ số}}.
 *
 * <p>Ví dụ: {@code PN-20261031-001}, {@code PX-20261031-012}, {@code KK-20261031-002}.
 *
 * <p><b>Vấn đề cần giải quyết:</b> nếu hai người bấm "Lưu phiếu" cùng lúc, cả hai cùng
 * đọc được số cuối là 5 thì cả hai sẽ sinh ra mã ...-006 trùng nhau.
 *
 * <p><b>Cách xử lý - ba lớp bảo vệ:</b>
 *
 * <ol>
 *   <li>Khóa dòng bộ đếm bằng {@code SELECT ... FOR UPDATE}: người thứ hai phải chờ
 *       người thứ nhất ghi xong mới đọc được số mới.</li>
 *   <li>Ràng buộc UNIQUE trên cặp (prefix, sequence_date) của bảng bộ đếm, và UNIQUE
 *       trên cột {@code code} của bảng phiếu - lớp chặn cuối cùng ở tầng CSDL.</li>
 *   <li>Giao dịch riêng ({@code REQUIRES_NEW}): bộ đếm được tăng và ghi nhận ngay, nên
 *       nếu phiếu bị rollback thì số đó bị bỏ trống chứ không bị cấp lại cho phiếu khác.
 *       Mã phiếu có thể nhảy số (001, 003) nhưng không bao giờ trùng - đánh đổi này là
 *       có chủ đích vì trùng mã nguy hiểm hơn nhảy số.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class DocumentSequenceService {

    /** Tiền tố mã phiếu nhập kho. */
    public static final String PREFIX_RECEIPT = "PN";

    /** Tiền tố mã phiếu xuất kho. */
    public static final String PREFIX_ISSUE = "PX";

    /** Tiền tố mã phiếu kiểm kê. */
    public static final String PREFIX_STOCKTAKE = "KK";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DocumentSequenceRepository sequenceRepository;

    /**
     * Cấp mã phiếu mới cho ngày hôm nay.
     *
     * @param prefix một trong {@link #PREFIX_RECEIPT}, {@link #PREFIX_ISSUE}, {@link #PREFIX_STOCKTAKE}
     * @return mã phiếu đầy đủ, ví dụ {@code PN-20261031-001}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextCode(String prefix) {
        return nextCode(prefix, LocalDate.now());
    }

    /**
     * Cấp mã phiếu cho một ngày cụ thể (dùng khi seed dữ liệu demo trong quá khứ).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextCode(String prefix, LocalDate date) {
        DocumentSequence sequence = sequenceRepository
                .findForUpdate(prefix, date)
                .orElseGet(() -> createSequence(prefix, date));

        int next = sequence.getLastNumber() + 1;
        sequence.setLastNumber(next);
        sequenceRepository.save(sequence);

        // %03d: luôn đủ 3 chữ số (001, 012, 123). Quá 999 phiếu/ngày thì số tự dài ra.
        return String.format("%s-%s-%03d", prefix, date.format(DATE_FORMAT), next);
    }

    /**
     * Tạo dòng bộ đếm cho ngày mới.
     *
     * <p>Nếu hai yêu cầu cùng tạo dòng đầu tiên trong ngày, một bên sẽ nhận lỗi trùng
     * khóa UNIQUE; khi đó ta đọc lại dòng do bên kia vừa tạo (kèm khóa ghi) rồi dùng tiếp.
     */
    private DocumentSequence createSequence(String prefix, LocalDate date) {
        try {
            return sequenceRepository.saveAndFlush(DocumentSequence.builder()
                    .prefix(prefix)
                    .sequenceDate(date)
                    .lastNumber(0)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            return sequenceRepository
                    .findForUpdate(prefix, date)
                    .orElseThrow(() -> new IllegalStateException(
                            "Không cấp được mã phiếu cho tiền tố " + prefix + " ngày " + date, ex));
        }
    }
}
