import dayjs from 'dayjs';
import 'dayjs/locale/vi';
import relativeTime from 'dayjs/plugin/relativeTime';

/**
 * Các hàm định dạng hiển thị theo quy ước Việt Nam.
 *
 * Quy ước thống nhất toàn hệ thống (khớp với backend):
 * - Ngày: 31/10/2026, thời điểm: 31/10/2026 14:30
 * - Tiền: 1.250.000 ₫ (dấu chấm phân cách nghìn)
 * - Số lượng: bỏ các chữ số 0 vô nghĩa (2,5 thay vì 2,500)
 */

dayjs.extend(relativeTime);
dayjs.locale('vi');

export const DATE_FORMAT = 'DD/MM/YYYY';
export const DATE_TIME_FORMAT = 'DD/MM/YYYY HH:mm';
/** Định dạng ISO dùng khi gửi lên API. */
export const API_DATE_FORMAT = 'YYYY-MM-DD';

/** Định dạng số tiền: 1250000 -> "1.250.000" */
export function formatMoney(value?: number | null): string {
  if (value === null || value === undefined) {
    return '';
  }
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value);
}

/** Định dạng tiền kèm đơn vị: 1250000 -> "1.250.000 ₫" */
export function formatCurrency(value?: number | null): string {
  if (value === null || value === undefined) {
    return '';
  }
  return `${formatMoney(value)} ₫`;
}

/**
 * Định dạng số lượng, bỏ phần thập phân bằng 0.
 *
 * Ví dụ: 2.500 -> "2,5"; 100.000 -> "100"
 */
export function formatQuantity(value?: number | null): string {
  if (value === null || value === undefined) {
    return '';
  }
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value);
}

/** Định dạng số nguyên có phân cách nghìn. */
export function formatNumber(value?: number | null): string {
  if (value === null || value === undefined) {
    return '';
  }
  return new Intl.NumberFormat('vi-VN').format(value);
}

export function formatDate(value?: string | null): string {
  return value ? dayjs(value).format(DATE_FORMAT) : '';
}

export function formatDateTime(value?: string | null): string {
  return value ? dayjs(value).format(DATE_TIME_FORMAT) : '';
}

/** Thời gian tương đối: "3 giờ trước", dùng cho danh sách thông báo. */
export function formatRelativeTime(value?: string | null): string {
  return value ? dayjs(value).fromNow() : '';
}

/** Ngày hôm nay theo định dạng API. */
export function todayForApi(): string {
  return dayjs().format(API_DATE_FORMAT);
}

/** Ngày đầu tháng hiện tại theo định dạng API (mặc định cho bộ lọc báo cáo). */
export function firstDayOfMonthForApi(): string {
  return dayjs().startOf('month').format(API_DATE_FORMAT);
}

/** Cắt chuỗi dài để hiển thị gọn trong bảng. */
export function truncate(value: string | undefined, maxLength: number): string {
  if (!value) {
    return '';
  }
  return value.length <= maxLength ? value : `${value.slice(0, maxLength)}...`;
}

/**
 * Định dạng JSON cho dễ đọc, dùng ở trang nhật ký hoạt động.
 *
 * Nhật ký lưu dữ liệu trước/sau dạng JSON một dòng; hàm này thêm thụt lề để người đọc
 * so sánh được nhanh.
 */
export function prettyJson(value?: string | null): string {
  if (!value) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    // Dữ liệu không phải JSON hợp lệ (ví dụ đã bị cắt vì quá dài): hiển thị nguyên văn
    return value;
  }
}

export { dayjs };
