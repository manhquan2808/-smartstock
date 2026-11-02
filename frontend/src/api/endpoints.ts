import { apiClient } from './client';
import type {
  AuditLogResponse,
  CategoryResponse,
  CurrentUser,
  DashboardResponse,
  DocumentDetail,
  DocumentStatus,
  DocumentSummary,
  InventoryReportResponse,
  InventoryRow,
  IssueTrendResponse,
  IssueType,
  LoginResponse,
  LowStockRow,
  MessageResponse,
  PageResponse,
  PartnerResponse,
  ProductDetailResponse,
  ProductLookupResponse,
  ProductResponse,
  ReceiptType,
  RecordStatus,
  ReplenishmentResponse,
  Role,
  SaveIssueRequest,
  SaveReceiptRequest,
  SettingResponse,
  SlowMovingReportResponse,
  StockCardResponse,
  StockCheckResponse,
  StockState,
  StocktakeDetail,
  StocktakeSummary,
  UnitResponse,
  UserResponse,
  UserStatus,
  WarehouseResponse,
  NotificationResponse,
} from '@/types';

/**
 * Tập hợp mọi lệnh gọi API, nhóm theo phân hệ.
 *
 * Vì sao gom vào một file: mọi đường dẫn API nằm cùng một chỗ nên khi backend đổi đường
 * dẫn, ta chỉ sửa ở đây thay vì tìm trong hàng chục file giao diện. Các trang chỉ gọi
 * hàm đã đặt tên rõ nghĩa, không tự viết chuỗi URL.
 */

// ======================================================================
// Xác thực
// ======================================================================

export const authApi = {
  login: (username: string, password: string) =>
    apiClient.post<LoginResponse>('/api/auth/login', { username, password }).then((r) => r.data),

  refresh: () => apiClient.post<LoginResponse>('/api/auth/refresh').then((r) => r.data),

  logout: () => apiClient.post<MessageResponse>('/api/auth/logout').then((r) => r.data),

  me: () => apiClient.get<CurrentUser>('/api/auth/me').then((r) => r.data),

  forgotPassword: (email: string) =>
    apiClient.post<MessageResponse>('/api/auth/forgot-password', { email }).then((r) => r.data),

  resetPassword: (token: string, newPassword: string) =>
    apiClient
      .post<MessageResponse>('/api/auth/reset-password', { token, newPassword })
      .then((r) => r.data),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient
      .post<MessageResponse>('/api/auth/change-password', { currentPassword, newPassword })
      .then((r) => r.data),
};

// ======================================================================
// Hồ sơ cá nhân và người dùng
// ======================================================================

export interface SaveUserRequest {
  username?: string;
  email: string;
  password?: string;
  fullName: string;
  phone?: string;
  role: Role;
  warehouseIds?: number[];
  mustChangePassword?: boolean;
}

export const profileApi = {
  get: () => apiClient.get<UserResponse>('/api/profile').then((r) => r.data),

  update: (data: { fullName: string; email: string; phone?: string; avatarUrl?: string }) =>
    apiClient.put<UserResponse>('/api/profile', data).then((r) => r.data),
};

export const userApi = {
  search: (params: {
    keyword?: string;
    role?: Role;
    status?: UserStatus;
    warehouseId?: number;
    page?: number;
    size?: number;
  }) => apiClient.get<PageResponse<UserResponse>>('/api/users', { params }).then((r) => r.data),

  getById: (id: number) => apiClient.get<UserResponse>(`/api/users/${id}`).then((r) => r.data),

  create: (data: SaveUserRequest) => apiClient.post<UserResponse>('/api/users', data).then((r) => r.data),

  update: (id: number, data: SaveUserRequest) =>
    apiClient.put<UserResponse>(`/api/users/${id}`, data).then((r) => r.data),

  lock: (id: number) => apiClient.put<UserResponse>(`/api/users/${id}/lock`).then((r) => r.data),

  unlock: (id: number) => apiClient.put<UserResponse>(`/api/users/${id}/unlock`).then((r) => r.data),

  resetPassword: (id: number, newPassword: string, mustChangePassword = true) =>
    apiClient
      .put<MessageResponse>(`/api/users/${id}/reset-password`, { newPassword, mustChangePassword })
      .then((r) => r.data),
};

// ======================================================================
// Kho
// ======================================================================

export interface SaveWarehouseRequest {
  code: string;
  name: string;
  address?: string;
  phone?: string;
  managerId?: number | null;
  active?: boolean;
}

export const warehouseApi = {
  search: (params: { keyword?: string; active?: boolean; page?: number; size?: number }) =>
    apiClient.get<PageResponse<WarehouseResponse>>('/api/warehouses', { params }).then((r) => r.data),

  listMine: () => apiClient.get<WarehouseResponse[]>('/api/warehouses/my').then((r) => r.data),

  getById: (id: number) => apiClient.get<WarehouseResponse>(`/api/warehouses/${id}`).then((r) => r.data),

  create: (data: SaveWarehouseRequest) =>
    apiClient.post<WarehouseResponse>('/api/warehouses', data).then((r) => r.data),

  update: (id: number, data: SaveWarehouseRequest) =>
    apiClient.put<WarehouseResponse>(`/api/warehouses/${id}`, data).then((r) => r.data),

  deactivate: (id: number) => apiClient.delete<void>(`/api/warehouses/${id}`).then((r) => r.data),
};

// ======================================================================
// Danh mục: loại hàng, đơn vị tính, NCC, khách hàng
// ======================================================================

export interface SaveSimpleCatalogRequest {
  code: string;
  name: string;
  description?: string;
}

export interface SavePartnerRequest {
  code: string;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  taxCode?: string;
  note?: string;
}

export const categoryApi = {
  search: (params: { keyword?: string; page?: number; size?: number }) =>
    apiClient.get<PageResponse<CategoryResponse>>('/api/categories', { params }).then((r) => r.data),
  listAll: () => apiClient.get<CategoryResponse[]>('/api/categories/all').then((r) => r.data),
  create: (data: SaveSimpleCatalogRequest) =>
    apiClient.post<CategoryResponse>('/api/categories', data).then((r) => r.data),
  update: (id: number, data: SaveSimpleCatalogRequest) =>
    apiClient.put<CategoryResponse>(`/api/categories/${id}`, data).then((r) => r.data),
  remove: (id: number) => apiClient.delete<void>(`/api/categories/${id}`).then((r) => r.data),
};

export const unitApi = {
  search: (params: { keyword?: string; page?: number; size?: number }) =>
    apiClient.get<PageResponse<UnitResponse>>('/api/units', { params }).then((r) => r.data),
  listAll: () => apiClient.get<UnitResponse[]>('/api/units/all').then((r) => r.data),
  create: (data: SaveSimpleCatalogRequest) =>
    apiClient.post<UnitResponse>('/api/units', data).then((r) => r.data),
  update: (id: number, data: SaveSimpleCatalogRequest) =>
    apiClient.put<UnitResponse>(`/api/units/${id}`, data).then((r) => r.data),
  remove: (id: number) => apiClient.delete<void>(`/api/units/${id}`).then((r) => r.data),
};

export const supplierApi = {
  search: (params: { keyword?: string; page?: number; size?: number }) =>
    apiClient.get<PageResponse<PartnerResponse>>('/api/suppliers', { params }).then((r) => r.data),
  listAll: () => apiClient.get<PartnerResponse[]>('/api/suppliers/all').then((r) => r.data),
  create: (data: SavePartnerRequest) =>
    apiClient.post<PartnerResponse>('/api/suppliers', data).then((r) => r.data),
  update: (id: number, data: SavePartnerRequest) =>
    apiClient.put<PartnerResponse>(`/api/suppliers/${id}`, data).then((r) => r.data),
  remove: (id: number) => apiClient.delete<void>(`/api/suppliers/${id}`).then((r) => r.data),
};

export const customerApi = {
  search: (params: { keyword?: string; page?: number; size?: number }) =>
    apiClient.get<PageResponse<PartnerResponse>>('/api/customers', { params }).then((r) => r.data),
  listAll: () => apiClient.get<PartnerResponse[]>('/api/customers/all').then((r) => r.data),
  create: (data: SavePartnerRequest) =>
    apiClient.post<PartnerResponse>('/api/customers', data).then((r) => r.data),
  update: (id: number, data: SavePartnerRequest) =>
    apiClient.put<PartnerResponse>(`/api/customers/${id}`, data).then((r) => r.data),
  remove: (id: number) => apiClient.delete<void>(`/api/customers/${id}`).then((r) => r.data),
};

// ======================================================================
// Sản phẩm
// ======================================================================

export interface SaveProductRequest {
  sku: string;
  barcode?: string;
  name: string;
  categoryId: number;
  unitId: number;
  purchasePrice: number;
  salePrice: number;
  minStock: number;
  imageUrl?: string;
  description?: string;
  status?: RecordStatus;
}

export const productApi = {
  search: (params: {
    keyword?: string;
    categoryId?: number;
    status?: RecordStatus;
    page?: number;
    size?: number;
    sort?: string;
  }) => apiClient.get<PageResponse<ProductResponse>>('/api/products', { params }).then((r) => r.data),

  lookup: (params: { keyword?: string; warehouseId?: number; limit?: number }) =>
    apiClient.get<ProductLookupResponse[]>('/api/products/lookup', { params }).then((r) => r.data),

  /** Tra cứu theo mã vạch - dùng cho chức năng quét mã. */
  findByBarcode: (code: string, warehouseId?: number) =>
    apiClient
      .get<ProductLookupResponse>('/api/products/by-barcode', { params: { code, warehouseId } })
      .then((r) => r.data),

  getById: (id: number) => apiClient.get<ProductResponse>(`/api/products/${id}`).then((r) => r.data),

  getDetail: (id: number) =>
    apiClient.get<ProductDetailResponse>(`/api/products/${id}/detail`).then((r) => r.data),

  create: (data: SaveProductRequest) =>
    apiClient.post<ProductResponse>('/api/products', data).then((r) => r.data),

  update: (id: number, data: SaveProductRequest) =>
    apiClient.put<ProductResponse>(`/api/products/${id}`, data).then((r) => r.data),

  changeStatus: (id: number, status: RecordStatus) =>
    apiClient.put<ProductResponse>(`/api/products/${id}/status`, null, { params: { status } }).then((r) => r.data),

  remove: (id: number) => apiClient.delete<void>(`/api/products/${id}`).then((r) => r.data),
};

// ======================================================================
// Tồn kho
// ======================================================================

export const inventoryApi = {
  search: (params: {
    warehouseId?: number;
    categoryId?: number;
    keyword?: string;
    stockState?: StockState;
    page?: number;
    size?: number;
    sort?: string;
  }) => apiClient.get<PageResponse<InventoryRow>>('/api/inventory', { params }).then((r) => r.data),

  lowStock: (warehouseId?: number) =>
    apiClient.get<LowStockRow[]>('/api/inventory/low-stock', { params: { warehouseId } }).then((r) => r.data),

  stockCard: (params: { productId: number; warehouseId: number; fromDate?: string; toDate?: string }) =>
    apiClient.get<StockCardResponse>('/api/inventory/stock-card', { params }).then((r) => r.data),
};

// ======================================================================
// Phiếu nhập kho
// ======================================================================

export interface DocumentSearchParams {
  warehouseId?: number;
  status?: DocumentStatus;
  fromDate?: string;
  toDate?: string;
  createdBy?: number;
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const receiptApi = {
  search: (params: DocumentSearchParams & { receiptType?: ReceiptType }) =>
    apiClient.get<PageResponse<DocumentSummary>>('/api/stock-receipts', { params }).then((r) => r.data),

  getById: (id: number) => apiClient.get<DocumentDetail>(`/api/stock-receipts/${id}`).then((r) => r.data),

  create: (data: SaveReceiptRequest, submit = false) =>
    apiClient.post<DocumentDetail>('/api/stock-receipts', data, { params: { submit } }).then((r) => r.data),

  update: (id: number, data: SaveReceiptRequest) =>
    apiClient.put<DocumentDetail>(`/api/stock-receipts/${id}`, data).then((r) => r.data),

  submit: (id: number) =>
    apiClient.put<DocumentDetail>(`/api/stock-receipts/${id}/submit`).then((r) => r.data),

  approve: (id: number) =>
    apiClient.put<DocumentDetail>(`/api/stock-receipts/${id}/approve`).then((r) => r.data),

  reject: (id: number, reason: string) =>
    apiClient.put<DocumentDetail>(`/api/stock-receipts/${id}/reject`, { reason }).then((r) => r.data),

  cancel: (id: number, reason: string) =>
    apiClient.put<DocumentDetail>(`/api/stock-receipts/${id}/cancel`, { reason }).then((r) => r.data),

  remove: (id: number) => apiClient.delete<void>(`/api/stock-receipts/${id}`).then((r) => r.data),
};

// ======================================================================
// Phiếu xuất kho
// ======================================================================

export const issueApi = {
  search: (params: DocumentSearchParams & { issueType?: IssueType }) =>
    apiClient.get<PageResponse<DocumentSummary>>('/api/stock-issues', { params }).then((r) => r.data),

  getById: (id: number) => apiClient.get<DocumentDetail>(`/api/stock-issues/${id}`).then((r) => r.data),

  /** Kiểm tra tồn trước khi lưu, để cảnh báo ngay trên màn hình lập phiếu. */
  checkStock: (warehouseId: number, items: Array<{ productId: number; quantity: number }>) =>
    apiClient
      .post<StockCheckResponse>('/api/stock-issues/check-stock', items, { params: { warehouseId } })
      .then((r) => r.data),

  create: (data: SaveIssueRequest, submit = false) =>
    apiClient.post<DocumentDetail>('/api/stock-issues', data, { params: { submit } }).then((r) => r.data),

  update: (id: number, data: SaveIssueRequest) =>
    apiClient.put<DocumentDetail>(`/api/stock-issues/${id}`, data).then((r) => r.data),

  submit: (id: number) => apiClient.put<DocumentDetail>(`/api/stock-issues/${id}/submit`).then((r) => r.data),

  approve: (id: number) =>
    apiClient.put<DocumentDetail>(`/api/stock-issues/${id}/approve`).then((r) => r.data),

  reject: (id: number, reason: string) =>
    apiClient.put<DocumentDetail>(`/api/stock-issues/${id}/reject`, { reason }).then((r) => r.data),

  cancel: (id: number, reason: string) =>
    apiClient.put<DocumentDetail>(`/api/stock-issues/${id}/cancel`, { reason }).then((r) => r.data),

  remove: (id: number) => apiClient.delete<void>(`/api/stock-issues/${id}`).then((r) => r.data),
};

// ======================================================================
// Kiểm kê
// ======================================================================

export const stocktakeApi = {
  search: (params: { warehouseId?: number; status?: DocumentStatus; fromDate?: string; toDate?: string; page?: number; size?: number }) =>
    apiClient.get<PageResponse<StocktakeSummary>>('/api/stocktakes', { params }).then((r) => r.data),

  getById: (id: number) => apiClient.get<StocktakeDetail>(`/api/stocktakes/${id}`).then((r) => r.data),

  create: (data: { warehouseId: number; documentDate: string; categoryId?: number | null; note?: string }) =>
    apiClient.post<StocktakeDetail>('/api/stocktakes', data).then((r) => r.data),

  saveCount: (id: number, items: Array<{ itemId: number; actualQuantity?: number | null; note?: string }>) =>
    apiClient.put<StocktakeDetail>(`/api/stocktakes/${id}/count`, { items }).then((r) => r.data),

  submit: (id: number) => apiClient.put<StocktakeDetail>(`/api/stocktakes/${id}/submit`).then((r) => r.data),

  approve: (id: number) => apiClient.put<StocktakeDetail>(`/api/stocktakes/${id}/approve`).then((r) => r.data),

  reject: (id: number, reason: string) =>
    apiClient.put<StocktakeDetail>(`/api/stocktakes/${id}/reject`, { reason }).then((r) => r.data),

  cancel: (id: number, reason: string) =>
    apiClient.put<MessageResponse>(`/api/stocktakes/${id}/cancel`, { reason }).then((r) => r.data),
};

// ======================================================================
// Dashboard, báo cáo, gợi ý nhập hàng
// ======================================================================

export const reportApi = {
  dashboard: (warehouseId?: number) =>
    apiClient.get<DashboardResponse>('/api/dashboard', { params: { warehouseId } }).then((r) => r.data),

  inventoryReport: (params: { fromDate: string; toDate: string; warehouseId?: number; categoryId?: number }) =>
    apiClient.get<InventoryReportResponse>('/api/reports/inventory', { params }).then((r) => r.data),

  slowMoving: (params: { warehouseId?: number; days?: number }) =>
    apiClient.get<SlowMovingReportResponse>('/api/reports/slow-moving', { params }).then((r) => r.data),

  replenishment: (params: { warehouseId: number; windowDays?: number }) =>
    apiClient.get<ReplenishmentResponse>('/api/reports/replenishment', { params }).then((r) => r.data),

  issueTrend: (params: { productId: number; warehouseId: number; windowDays?: number }) =>
    apiClient.get<IssueTrendResponse>('/api/reports/replenishment/trend', { params }).then((r) => r.data),
};

// ======================================================================
// Thông báo
// ======================================================================

export const notificationApi = {
  list: (params: { unreadOnly?: boolean; page?: number; size?: number }) =>
    apiClient.get<PageResponse<NotificationResponse>>('/api/notifications', { params }).then((r) => r.data),

  unreadCount: () =>
    apiClient.get<{ count: number }>('/api/notifications/unread-count').then((r) => r.data.count),

  markAsRead: (id: number) =>
    apiClient.put<MessageResponse>(`/api/notifications/${id}/read`).then((r) => r.data),

  markAllAsRead: () => apiClient.put<MessageResponse>('/api/notifications/read-all').then((r) => r.data),
};

// ======================================================================
// Nhật ký hoạt động và cấu hình
// ======================================================================

export const auditApi = {
  search: (params: {
    userId?: number;
    action?: string;
    entityType?: string;
    fromDate?: string;
    toDate?: string;
    keyword?: string;
    page?: number;
    size?: number;
  }) => apiClient.get<PageResponse<AuditLogResponse>>('/api/audit-logs', { params }).then((r) => r.data),
};

export const settingApi = {
  listAll: () => apiClient.get<SettingResponse[]>('/api/settings').then((r) => r.data),
  update: (values: Record<string, string>) =>
    apiClient.put<SettingResponse[]>('/api/settings', values).then((r) => r.data),
};

// ======================================================================
// Tải tệp
// ======================================================================

export const fileApi = {
  /**
   * Tải ảnh lên.
   *
   * Phải gửi dạng `multipart/form-data`; axios tự đặt header khi ta truyền FormData.
   */
  upload: (file: File, folder: string) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient
      .post<{ url: string; storageType: string }>('/api/files/upload', formData, {
        params: { folder },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },
};
