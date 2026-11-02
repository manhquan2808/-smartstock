import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Cấu hình Vite.
 *
 * Giải thích cho người quen PHP: Vite là công cụ đóng gói (bundler) kiêm máy chủ phát
 * triển. Khi chạy `npm run dev`, Vite mở máy chủ ở cổng 5173 và tự nạp lại trang mỗi khi
 * ta sửa mã. Khi chạy `npm run build`, Vite nén toàn bộ mã React thành các file tĩnh
 * HTML/CSS/JS trong thư mục `dist/` - đem thư mục đó lên Netlify là xong.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Đường dẫn rút gọn '@/...' trỏ tới thư mục src.
      // Lưu ý: khai báo trong tsconfig.json chỉ giúp TypeScript hiểu khi kiểm tra kiểu;
      // Vite/Rollup cần khai báo riêng ở đây mới gom được mã lúc đóng gói.
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // Mở cho máy khác trong mạng LAN truy cập, tiện khi thử trên điện thoại thật
    host: true,
    proxy: {
      // Chuyển các request /api và /uploads sang backend.
      //
      // Vì trình duyệt gọi cùng origin (localhost:5173) nên không phát sinh CORS ở phía
      // trình duyệt. Tuy nhiên proxy vẫn chuyển tiếp header `Origin` sang backend, và
      // Spring Security sẽ từ chối nếu origin đó không nằm trong danh sách cho phép -
      // điều này xảy ra ngay khi Vite phải đổi sang cổng khác (5174) vì 5173 bị chiếm.
      //
      // Xóa header `Origin` khiến backend coi đây là request thường (không phải CORS),
      // nhờ vậy môi trường phát triển chạy được ở bất kỳ cổng nào mà không phải sửa
      // cấu hình backend.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Tách các thư viện lớn thành file riêng để trình duyệt lưu đệm tốt hơn
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-antd': ['antd'],
          'vendor-icons': ['@ant-design/icons'],
          'vendor-chart': ['recharts'],
          'vendor-scan': ['html5-qrcode', 'jsbarcode', 'qrcode'],
        },
      },
    },
    chunkSizeWarningLimit: 1200,
  },
});
