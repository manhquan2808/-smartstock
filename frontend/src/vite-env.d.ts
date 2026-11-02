/// <reference types="vite/client" />

/**
 * Khai báo các biến môi trường của Vite mà ứng dụng sử dụng.
 *
 * Nhờ khai báo này, TypeScript biết `import.meta.env.VITE_API_BASE_URL` có kiểu string,
 * và báo lỗi ngay nếu ta gõ sai tên biến.
 */
interface ImportMetaEnv {
  /** Địa chỉ gốc của backend. Để trống khi phát triển để dùng proxy của Vite. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
