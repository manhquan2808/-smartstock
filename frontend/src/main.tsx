import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { AuthProvider } from './contexts/AuthContext';
import { ThemeProvider } from './contexts/ThemeContext';
import './styles/global.css';

/**
 * Điểm khởi động của ứng dụng React.
 *
 * Giải thích cho người quen PHP: file này tương đương `index.php` ở phía trình duyệt -
 * nơi toàn bộ ứng dụng được gắn vào thẻ `<div id="root">` trong index.html.
 *
 * Các lớp bọc (provider) xếp từ ngoài vào trong:
 * 1. QueryClientProvider - bộ đệm dữ liệu từ API (TanStack Query)
 * 2. ThemeProvider       - chế độ sáng/tối
 * 3. BrowserRouter       - điều hướng theo URL
 * 4. AuthProvider        - trạng thái đăng nhập (cần Router để chuyển trang khi hết phiên)
 */

/**
 * Cấu hình bộ đệm dữ liệu.
 *
 * TanStack Query làm thay ta rất nhiều việc: nhớ kết quả đã tải, tự tải lại khi dữ liệu
 * cũ, gộp các request trùng nhau. Tương đương việc tự viết cache + loading state cho từng
 * trang trong jQuery/PHP, nhưng không phải viết tay.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Dữ liệu được coi là còn mới trong 30 giây -> chuyển qua lại giữa các trang
      // không gọi lại API liên tục
      staleTime: 30 * 1000,
      // Không tải lại khi người dùng chỉ chuyển sang tab khác rồi quay lại
      refetchOnWindowFocus: false,
      // Thử lại một lần khi lỗi mạng; lỗi nghiệp vụ (4xx) thì không thử lại
      retry: (failureCount, error) => {
        const status = (error as { response?: { status?: number } })?.response?.status;
        if (status && status >= 400 && status < 500) {
          return false;
        }
        return failureCount < 1;
      },
    },
  },
});

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
