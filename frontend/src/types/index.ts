/**
 * Các kiểu dữ liệu dùng chung, phản chiếu đúng DTO của backend.
 *
 * Giải thích cho người quen PHP: TypeScript kiểm tra kiểu dữ liệu ngay khi ta viết mã.
 * Nếu backend trả về trường `quantity` mà ở đây khai là `qty`, trình biên dịch báo lỗi
 * ngay thay vì để lỗi xuất hiện lúc chạy trên trình duyệt. Đây là lợi thế lớn khi dự án
 * có hàng chục trang cùng dùng một kiểu dữ liệu.
 *
 * Quy ước: tên trường giữ nguyên tiếng Anh như backend; chỉ phần hiển thị mới dịch sang
 * tiếng Việt.
 */

// ======================================================================
// Khuôn dạng chung
// ======================================================================

/** Dữ liệu phân trang trả về từ backend. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Khuôn dạng lỗi thống nhất của API. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  /** Lỗi theo từng trường, dùng để hiển thị cạnh ô nhập trên biểu mẫu. */
  fieldErrors?: Record<string, string>;
  details?: {
    shortages?: StockShortage[];
    [key: string]: unknown;
  };
}

/** Một dòng thiếu tồn khi xuất kho. */
export interface StockShortage {
  productId: number;
  sku: string;
  productName: string;
  requested: number;
  available: number;
}

export interface MessageResponse {
  message: string;
}

// ======================================================================
// Xác thực và người dùng
// ======================================================================

export type Role = 'ADMIN' | 'MANAGER' | 'STAFF';
export type UserStatus = 'ACTIVE' | 'LOCKED';

export interface WarehouseBrief {
  id: number;
  code: string;
  name: string;
}

export interface CurrentUser {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone?: string;
  avatarUrl?: string;
  role: Role;
  roleName: string;
  mustChangePassword: boolean;
  warehouses: WarehouseBrief[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: CurrentUser;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  phone?: string;
  avatarUrl?: string;
  role: Role;
  roleName: string;
  status: UserStatus;
  statusName: string;
  mustChangePassword: boolean;
  lastLoginAt?: string;
  createdAt: string;
  warehouses: WarehouseBrief[];
}

// ======================================================================
// Danh mục
// ======================================================================

export type RecordStatus = 'ACTIVE' | 'DISCONTINUED';

export interface CategoryResponse {
  id: number;
  code: string;
  name: string;
  description?: string;
  createdAt: string;
}

export interface UnitResponse {
  id: number;
  code: string;
  name: string;
  description?: string;
  createdAt: string;
}

export interface PartnerResponse {
  id: number;
  code: string;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  taxCode?: string;
  note?: string;
  createdAt: string;
}

export interface WarehouseResponse {
  id: number;
  code: string;
  name: string;
  address?: string;
  phone?: string;
  managerId?: number;
  managerName?: string;
  active: boolean;
  createdAt: string;
}

export interface ProductResponse {
  id: number;
  sku: string;
  barcode?: string;
  name: string;
  categoryId: number;
  categoryName: string;
  unitId: number;
  unitName: string;
  purchasePrice: number;
  salePrice: number;
  minStock: number;
  imageUrl?: string;
  description?: string;
  status: RecordStatus;
  statusName: string;
  createdAt: string;
}

/** Sản phẩm dạng gọn khi tra cứu để lập phiếu; `avgCost` là null với vai trò STAFF. */
export interface ProductLookupResponse {
  id: number;
  sku: string;
  barcode?: string;
  name: string;
  unitName: string;
  purchasePrice: number;
  salePrice: number;
  quantityOnHand: number;
  avgCost?: number | null;
}

export interface ProductStockRow {
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  quantity: number;
  avgCost?: number | null;
  stockValue?: number | null;
}

export interface ProductDetailResponse {
  product: ProductResponse;
  stocks: ProductStockRow[];
  totalQuantity: number;
}

// ======================================================================
// Tồn kho và thẻ kho
// ======================================================================

export type StockState = 'ALL' | 'IN_STOCK' | 'LOW' | 'OUT';

export interface InventoryRow {
  productId: number;
  sku: string;
  barcode?: string;
  productName: string;
  categoryName: string;
  unitName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  quantity: number;
  minStock: number;
  avgCost?: number | null;
  stockValue?: number | null;
  lowStock: boolean;
  outOfStock: boolean;
}

export interface LowStockRow {
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  warehouseId: number;
  warehouseName: string;
  quantity: number;
  minStock: number;
  outOfStock: boolean;
}

export type TransactionType = 'RECEIPT' | 'ISSUE' | 'ADJUSTMENT' | 'REVERSAL';

export interface StockCardEntry {
  id: number;
  transactionDate: string;
  refCode: string;
  type: TransactionType;
  typeName: string;
  description: string;
  quantityIn?: number | null;
  quantityOut?: number | null;
  balanceAfter: number;
  unitCost?: number | null;
  note?: string;
}

export interface StockCardResponse {
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  warehouseId: number;
  warehouseName: string;
  fromDate?: string;
  toDate?: string;
  openingQuantity: number;
  totalIn: number;
  totalOut: number;
  closingQuantity: number;
  entries: StockCardEntry[];
}

// ======================================================================
// Phiếu nhập, phiếu xuất
// ======================================================================

export type DocumentStatus = 'DRAFT' | 'PENDING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';
export type ReceiptType = 'PURCHASE' | 'CUSTOMER_RETURN' | 'OTHER';
export type IssueType = 'SALE' | 'SUPPLIER_RETURN' | 'INTERNAL' | 'DISPOSAL';

export interface DocumentSummary {
  id: number;
  code: string;
  documentTypeName: string;
  warehouseId: number;
  warehouseName: string;
  typeCode: string;
  typeName: string;
  documentDate: string;
  partnerName?: string;
  status: DocumentStatus;
  statusName: string;
  totalAmount?: number | null;
  itemCount: number;
  totalQuantity: number;
  createdById?: number;
  createdByName?: string;
  approvedByName?: string;
  createdAt: string;
  approvedAt?: string;
}

export interface DocumentItem {
  id?: number;
  lineNo: number;
  productId: number;
  sku: string;
  barcode?: string;
  productName: string;
  unitName: string;
  quantity: number;
  unitPrice?: number | null;
  amount?: number | null;
  quantityOnHand: number;
  note?: string;
}

export interface DocumentDetail {
  id: number;
  code: string;
  documentTypeName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  typeCode: string;
  typeName: string;
  documentDate: string;
  partnerId?: number;
  partnerCode?: string;
  partnerName?: string;
  reason?: string;
  note?: string;
  status: DocumentStatus;
  statusName: string;
  totalAmount?: number | null;
  items: DocumentItem[];
  createdById?: number;
  createdByName?: string;
  createdAt: string;
  submittedAt?: string;
  approvedByName?: string;
  approvedAt?: string;
  rejectReason?: string;
  cancelledByName?: string;
  cancelledAt?: string;
  cancelReason?: string;
  /** Frontend dùng ba cờ này để ẩn/hiện các nút hành động. */
  canEdit: boolean;
  canApprove: boolean;
  canCancel: boolean;
}

/** Dữ liệu gửi lên khi lưu phiếu nhập. */
export interface SaveReceiptRequest {
  warehouseId: number;
  receiptType: ReceiptType;
  documentDate: string;
  supplierId?: number | null;
  customerId?: number | null;
  reason?: string;
  note?: string;
  items: Array<{ productId: number; quantity: number; unitPrice: number; note?: string }>;
}

/** Dữ liệu gửi lên khi lưu phiếu xuất (không có đơn giá - hệ thống dùng giá vốn). */
export interface SaveIssueRequest {
  warehouseId: number;
  issueType: IssueType;
  documentDate: string;
  customerId?: number | null;
  supplierId?: number | null;
  reason?: string;
  note?: string;
  items: Array<{ productId: number; quantity: number; note?: string }>;
}

export interface StockCheckResponse {
  sufficient: boolean;
  shortages: StockShortage[];
}

// ======================================================================
// Kiểm kê
// ======================================================================

export interface StocktakeSummary {
  id: number;
  code: string;
  warehouseId: number;
  warehouseName: string;
  documentDate: string;
  categoryName: string;
  status: DocumentStatus;
  statusName: string;
  totalItems: number;
  countedItems: number;
  differenceItems: number;
  totalDifferenceValue?: number | null;
  createdByName?: string;
  approvedByName?: string;
  createdAt: string;
  approvedAt?: string;
}

export interface StocktakeItem {
  id: number;
  lineNo: number;
  productId: number;
  sku: string;
  barcode?: string;
  productName: string;
  unitName: string;
  systemQuantity: number;
  actualQuantity?: number | null;
  difference?: number | null;
  unitCost?: number | null;
  differenceValue?: number | null;
  counted: boolean;
  note?: string;
}

export interface StocktakeDetail {
  id: number;
  code: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  documentDate: string;
  categoryId?: number;
  categoryName: string;
  note?: string;
  status: DocumentStatus;
  statusName: string;
  items: StocktakeItem[];
  totalItems: number;
  countedItems: number;
  differenceItems: number;
  totalDifferenceValue?: number | null;
  createdByName?: string;
  createdAt: string;
  submittedAt?: string;
  approvedByName?: string;
  approvedAt?: string;
  rejectReason?: string;
  cancelReason?: string;
  canEdit: boolean;
  canApprove: boolean;
  /** Mã phiếu nhập/xuất hoàn tất sau khi tạo đợt kiểm kê - cảnh báo số liệu có thể lệch. */
  warningDocumentCodes: string[];
}

// ======================================================================
// Dashboard và báo cáo
// ======================================================================

export interface DashboardResponse {
  totalProducts: number;
  productsInStock: number;
  /** null với vai trò STAFF (dashboard rút gọn, không có số liệu giá trị). */
  totalStockValue?: number | null;
  lowStockCount: number;
  receiptsThisMonth: number;
  issuesThisMonth: number;
  pendingDocuments: number;
  lowStockItems: Array<{
    productId: number;
    sku: string;
    productName: string;
    unitName: string;
    warehouseName: string;
    quantity: number;
    minStock: number;
  }>;
  dailyFlow: Array<{ date: string; receivedQuantity: number; issuedQuantity: number }>;
  topIssuedProducts: Array<{
    productId: number;
    sku: string;
    productName: string;
    unitName: string;
    quantity: number;
  }>;
  warehouseName: string;
}

export interface InventoryReportRow {
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  categoryName: string;
  openingQuantity: number;
  receivedQuantity: number;
  issuedQuantity: number;
  closingQuantity: number;
  avgCost?: number | null;
  closingValue?: number | null;
}

export interface InventoryReportResponse {
  fromDate: string;
  toDate: string;
  warehouseName: string;
  categoryName: string;
  rows: InventoryReportRow[];
  totalOpeningQuantity: number;
  totalReceivedQuantity: number;
  totalIssuedQuantity: number;
  totalClosingQuantity: number;
  totalClosingValue?: number | null;
  exportedBy: string;
  exportedAt: string;
}

export interface SlowMovingRow {
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  warehouseId: number;
  warehouseName: string;
  quantity: number;
  avgCost?: number | null;
  stockValue?: number | null;
  lastIssueDate?: string | null;
  daysSinceLastIssue?: number | null;
}

export interface SlowMovingReportResponse {
  thresholdDays: number;
  warehouseName: string;
  rows: SlowMovingRow[];
  totalValue?: number | null;
  exportedBy: string;
  exportedAt: string;
}

// ======================================================================
// Gợi ý nhập hàng
// ======================================================================

export type Urgency = 'OUT_OF_STOCK' | 'CRITICAL' | 'WARNING' | 'NORMAL';

export interface ReplenishmentRow {
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  quantityOnHand: number;
  minStock: number;
  totalIssued: number;
  averageDailyIssue: number;
  /** null nghĩa là chưa từng xuất nên không dự báo được. */
  daysOfStockLeft?: number | null;
  suggestedQuantity: number;
  purchasePrice: number;
  estimatedCost: number;
  urgency: Urgency;
}

export interface ReplenishmentResponse {
  warehouseId: number;
  warehouseName: string;
  windowDays: number;
  leadTimeDays: number;
  safetyStockDays: number;
  formulaExplanation: string;
  rows: ReplenishmentRow[];
}

export interface IssueTrendResponse {
  productId: number;
  productName: string;
  windowDays: number;
  points: Array<{ date: string; quantity: number }>;
}

// ======================================================================
// Thông báo, nhật ký, cấu hình
// ======================================================================

export type NotificationType =
  | 'RECEIPT_PENDING'
  | 'ISSUE_PENDING'
  | 'STOCKTAKE_PENDING'
  | 'DOCUMENT_APPROVED'
  | 'DOCUMENT_REJECTED'
  | 'DOCUMENT_CANCELLED'
  | 'LOW_STOCK';

export interface NotificationResponse {
  id: number;
  type: NotificationType;
  typeName: string;
  title: string;
  content?: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export interface AuditLogResponse {
  id: number;
  userId?: number;
  username?: string;
  action: string;
  actionName: string;
  entityType?: string;
  entityId?: number;
  description?: string;
  beforeJson?: string;
  afterJson?: string;
  ipAddress?: string;
  createdAt: string;
}

export interface SettingResponse {
  key: string;
  value: string;
  valueType: 'INTEGER' | 'DECIMAL' | 'STRING' | 'BOOLEAN';
  description?: string;
  updatedAt: string;
}
