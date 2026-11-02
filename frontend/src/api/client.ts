import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, LoginResponse } from '@/types';

/**
 * Lớp gọi API dùng chung cho toàn bộ ứng dụng.
 *
 * Hai điểm thiết kế quan trọng:
 *
 * 1. **Access token giữ trong BIẾN BỘ NHỚ**, không lưu localStorage. Nếu website bị tấn
 *    công XSS, mã độc không đọc được token từ localStorage. Hệ quả: tải lại trang (F5) là
 *    mất token, nhưng không sao - refresh token nằm trong cookie HttpOnly nên ứng dụng tự
 *    lấy lại access token mới khi khởi động.
 *
 * 2. **Tự động làm mới token**: khi một request bị 401, interceptor gọi
 *    `/api/auth/refresh` một lần rồi THỬ LẠI request ban đầu. Nhiều request cùng bị 401
 *    sẽ chờ chung một lần làm mới (hàng đợi `pendingRequests`) để không gọi refresh nhiều
 *    lần song song - điều đó sẽ làm xoay vòng token liên tục và đăng xuất oan người dùng.
 */

/** Địa chỉ backend. Để trống khi phát triển để dùng proxy của Vite. */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

/** Access token hiện tại, chỉ tồn tại trong bộ nhớ của tab đang mở. */
let accessToken: string | null = null;

/** Hàm được gọi khi phiên hết hiệu lực hoàn toàn (để AuthContext chuyển về trang đăng nhập). */
let onSessionExpired: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  // Bắt buộc để trình duyệt gửi kèm cookie refresh token
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

// ----------------------------------------------------------------------
// Interceptor cho request: tự gắn access token vào header
// ----------------------------------------------------------------------
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

/** Đang có một lần làm mới token diễn ra hay không. */
let isRefreshing = false;

/** Các request đang chờ token mới. */
let pendingRequests: Array<(token: string | null) => void> = [];

function notifyPendingRequests(token: string | null): void {
  pendingRequests.forEach((callback) => callback(token));
  pendingRequests = [];
}

/** Các endpoint không được thử làm mới token (tránh vòng lặp vô tận). */
const NO_RETRY_PATHS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

// ----------------------------------------------------------------------
// Interceptor cho response: xử lý 401 bằng cách làm mới token rồi thử lại
// ----------------------------------------------------------------------
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retried?: boolean };

    const isUnauthorized = error.response?.status === 401;
    const isRetryable =
      isUnauthorized &&
      originalRequest &&
      !originalRequest._retried &&
      !NO_RETRY_PATHS.some((path) => originalRequest.url?.includes(path));

    if (!isRetryable) {
      return Promise.reject(error);
    }

    originalRequest._retried = true;

    // Đã có một lần làm mới đang chạy: xếp hàng chờ kết quả
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        pendingRequests.push((token) => {
          if (token) {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            resolve(apiClient(originalRequest));
          } else {
            reject(error);
          }
        });
      });
    }

    isRefreshing = true;
    try {
      // Gọi refresh bằng axios gốc để không bị interceptor này bắt lại
      const { data } = await axios.post<LoginResponse>(
        `${API_BASE_URL}/api/auth/refresh`,
        {},
        { withCredentials: true },
      );

      setAccessToken(data.accessToken);
      notifyPendingRequests(data.accessToken);

      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
      return apiClient(originalRequest);
    } catch (refreshError) {
      // Làm mới thất bại: phiên đã hết hiệu lực thật
      setAccessToken(null);
      notifyPendingRequests(null);
      onSessionExpired?.();
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

/**
 * Lấy thông điệp lỗi tiếng Việt từ phản hồi của backend.
 *
 * Backend luôn trả về trường `message` đã viết sẵn bằng tiếng Việt, nên giao diện chỉ cần
 * hiển thị lại. Chỉ khi không kết nối được mới dùng câu thông báo mặc định ở đây.
 */
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    if (error.response?.data?.message) {
      return error.response.data.message;
    }
    if (error.code === 'ECONNABORTED') {
      return 'Yêu cầu quá thời gian chờ. Vui lòng thử lại.';
    }
    if (!error.response) {
      return 'Không kết nối được tới máy chủ. Vui lòng kiểm tra đường truyền.';
    }
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return 'Đã xảy ra lỗi không mong đợi.';
}

/** Lấy danh sách lỗi theo từng trường, dùng để hiển thị trên biểu mẫu Ant Design. */
export function getFieldErrors(error: unknown): Record<string, string> {
  if (axios.isAxiosError<ApiError>(error) && error.response?.data?.fieldErrors) {
    return error.response.data.fieldErrors;
  }
  return {};
}

/** Lấy danh sách sản phẩm thiếu tồn từ lỗi xuất kho. */
export function getStockShortages(error: unknown) {
  if (axios.isAxiosError<ApiError>(error)) {
    return error.response?.data?.details?.shortages ?? [];
  }
  return [];
}

/**
 * Tải file (Excel/PDF) từ backend và lưu về máy.
 *
 * Phải dùng `responseType: 'blob'` để axios không cố đọc nội dung file thành chuỗi.
 * Tên file được lấy từ header `Content-Disposition` do backend đặt.
 */
export async function downloadFile(url: string, fallbackFileName: string): Promise<void> {
  const response = await apiClient.get(url, { responseType: 'blob' });

  let fileName = fallbackFileName;
  const disposition = response.headers['content-disposition'] as string | undefined;
  if (disposition) {
    // Ưu tiên filename*=UTF-8'' vì nó giữ được dấu tiếng Việt
    const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    const plainMatch = /filename="([^"]+)"/i.exec(disposition);
    if (utf8Match) {
      fileName = decodeURIComponent(utf8Match[1]);
    } else if (plainMatch) {
      fileName = plainMatch[1];
    }
  }

  const blobUrl = window.URL.createObjectURL(response.data as Blob);
  const link = document.createElement('a');
  link.href = blobUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  // Giải phóng bộ nhớ của blob sau khi tải xong
  window.URL.revokeObjectURL(blobUrl);
}

/** Mở file PDF trong tab mới (dùng cho chức năng in phiếu). */
export async function openPdfInNewTab(url: string): Promise<void> {
  const response = await apiClient.get(url, { responseType: 'blob' });
  const blobUrl = window.URL.createObjectURL(
    new Blob([response.data as Blob], { type: 'application/pdf' }),
  );
  window.open(blobUrl, '_blank');
  // Chờ một nhịp cho tab mới kịp nạp file rồi mới thu hồi địa chỉ blob
  setTimeout(() => window.URL.revokeObjectURL(blobUrl), 60000);
}

export { API_BASE_URL };
