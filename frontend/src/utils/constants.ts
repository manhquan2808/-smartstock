import type { DocumentStatus, IssueType, ReceiptType, Role, StockState, Urgency } from '@/types';

/**
 * Các hằng số hiển thị: nhãn tiếng Việt và màu sắc tương ứng cho từng giá trị enum.
 *
 * Gom vào một chỗ để nhãn và màu của cùng một trạng thái luôn nhất quán trên mọi trang
 * (danh sách, chi tiết, bộ lọc, báo cáo).
 */

/** Màu chủ đạo của hệ thống - xanh dương của Ant Design. */
export const PRIMARY_COLOR = '#1677ff';

// ======================================================================
// Trạng thái phiếu
// ======================================================================

/** Màu thẻ trạng thái, dùng cho component Tag của Ant Design. */
export const DOCUMENT_STATUS_COLOR: Record<DocumentStatus, string> = {
  DRAFT: 'default',
  PENDING: 'processing',
  COMPLETED: 'success',
  REJECTED: 'error',
  CANCELLED: 'warning',
};

export const DOCUMENT_STATUS_LABEL: Record<DocumentStatus, string> = {
  DRAFT: 'Nháp',
  PENDING: 'Chờ duyệt',
  COMPLETED: 'Hoàn tất',
  REJECTED: 'Bị từ chối',
  CANCELLED: 'Đã hủy',
};

/** Danh sách cho ô chọn trạng thái ở bộ lọc. */
export const DOCUMENT_STATUS_OPTIONS = (Object.keys(DOCUMENT_STATUS_LABEL) as DocumentStatus[]).map(
  (status) => ({ value: status, label: DOCUMENT_STATUS_LABEL[status] }),
);

// ======================================================================
// Loại phiếu nhập
// ======================================================================

export const RECEIPT_TYPE_LABEL: Record<ReceiptType, string> = {
  PURCHASE: 'Nhập mua',
  CUSTOMER_RETURN: 'Khách trả lại',
  OTHER: 'Nhập khác',
};

export const RECEIPT_TYPE_OPTIONS = (Object.keys(RECEIPT_TYPE_LABEL) as ReceiptType[]).map((type) => ({
  value: type,
  label: RECEIPT_TYPE_LABEL[type],
}));

/**
 * Quy tắc bắt buộc theo từng loại phiếu nhập.
 *
 * Giao diện dùng bảng này để hiện/ẩn ô chọn đối tác và ô lý do. Backend cũng kiểm tra lại
 * các quy tắc này - frontend chỉ giúp người dùng đỡ nhập sai, không phải nơi quyết định.
 */
export const RECEIPT_TYPE_RULES: Record<
  ReceiptType,
  { supplierRequired: boolean; customerRequired: boolean; reasonRequired: boolean }
> = {
  PURCHASE: { supplierRequired: true, customerRequired: false, reasonRequired: false },
  CUSTOMER_RETURN: { supplierRequired: false, customerRequired: true, reasonRequired: false },
  OTHER: { supplierRequired: false, customerRequired: false, reasonRequired: true },
};

// ======================================================================
// Loại phiếu xuất
// ======================================================================

export const ISSUE_TYPE_LABEL: Record<IssueType, string> = {
  SALE: 'Xuất bán',
  SUPPLIER_RETURN: 'Trả nhà cung cấp',
  INTERNAL: 'Xuất dùng nội bộ',
  DISPOSAL: 'Xuất hủy',
};

export const ISSUE_TYPE_OPTIONS = (Object.keys(ISSUE_TYPE_LABEL) as IssueType[]).map((type) => ({
  value: type,
  label: ISSUE_TYPE_LABEL[type],
}));

export const ISSUE_TYPE_RULES: Record<
  IssueType,
  { supplierRequired: boolean; customerRequired: boolean; reasonRequired: boolean }
> = {
  SALE: { supplierRequired: false, customerRequired: true, reasonRequired: false },
  SUPPLIER_RETURN: { supplierRequired: true, customerRequired: false, reasonRequired: false },
  INTERNAL: { supplierRequired: false, customerRequired: false, reasonRequired: true },
  DISPOSAL: { supplierRequired: false, customerRequired: false, reasonRequired: true },
};

// ======================================================================
// Vai trò
// ======================================================================

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Quản trị hệ thống',
  MANAGER: 'Quản lý kho',
  STAFF: 'Nhân viên kho',
};

export const ROLE_COLOR: Record<Role, string> = {
  ADMIN: 'red',
  MANAGER: 'blue',
  STAFF: 'green',
};

export const ROLE_OPTIONS = (Object.keys(ROLE_LABEL) as Role[]).map((role) => ({
  value: role,
  label: ROLE_LABEL[role],
}));

// ======================================================================
// Tình trạng tồn kho
// ======================================================================

export const STOCK_STATE_LABEL: Record<StockState, string> = {
  ALL: 'Tất cả',
  IN_STOCK: 'Còn hàng',
  LOW: 'Sắp hết',
  OUT: 'Hết hàng',
};

export const STOCK_STATE_OPTIONS = (Object.keys(STOCK_STATE_LABEL) as StockState[]).map((state) => ({
  value: state,
  label: STOCK_STATE_LABEL[state],
}));

// ======================================================================
// Mức độ cấp thiết khi gợi ý nhập hàng
// ======================================================================

export const URGENCY_LABEL: Record<Urgency, string> = {
  OUT_OF_STOCK: 'Đã hết hàng',
  CRITICAL: 'Rất cấp thiết',
  WARNING: 'Cần chú ý',
  NORMAL: 'Bình thường',
};

export const URGENCY_COLOR: Record<Urgency, string> = {
  OUT_OF_STOCK: 'red',
  CRITICAL: 'volcano',
  WARNING: 'orange',
  NORMAL: 'default',
};

// ======================================================================
// Khác
// ======================================================================

/** Các lựa chọn số bản ghi mỗi trang. */
export const PAGE_SIZE_OPTIONS = ['10', '20', '50', '100'];

export const DEFAULT_PAGE_SIZE = 20;

/** Số ngày lấy lịch sử để tính bình quân xuất (trang gợi ý nhập hàng). */
export const FORECAST_WINDOW_OPTIONS = [
  { value: 30, label: '30 ngày gần nhất' },
  { value: 60, label: '60 ngày gần nhất' },
  { value: 90, label: '90 ngày gần nhất' },
];

/** Dung lượng ảnh tối đa khi tải lên (2 MB), khớp giới hạn của backend. */
export const MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024;

export const ACCEPTED_IMAGE_TYPES = 'image/jpeg,image/png,image/webp';
