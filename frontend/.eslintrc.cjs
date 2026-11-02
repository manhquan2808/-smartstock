/**
 * Cấu hình ESLint cho frontend.
 *
 * Mục tiêu: bắt các lỗi React thường gặp ngay khi viết mã, đặc biệt là sai quy tắc hook
 * (gọi hook trong điều kiện, thiếu phụ thuộc trong useEffect) - loại lỗi rất khó phát hiện
 * khi chạy thử bằng tay.
 */
module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
  ],
  ignorePatterns: ['dist', 'node_modules', '.eslintrc.cjs', 'vite.config.ts'],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: { jsx: true },
  },
  plugins: ['@typescript-eslint', 'react-refresh'],
  rules: {
    // Cảnh báo khi export thêm thứ khác ngoài component, vì sẽ làm mất hot-reload
    'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

    // Thiếu phụ thuộc trong useEffect là nguồn gốc của lỗi dữ liệu cũ (stale state)
    'react-hooks/exhaustive-deps': 'warn',
    'react-hooks/rules-of-hooks': 'error',

    // Không dùng kiểu any trong mã ứng dụng
    '@typescript-eslint/no-explicit-any': 'error',

    // Biến không dùng: cho phép tham số bắt đầu bằng dấu _ (quy ước "cố ý bỏ qua")
    '@typescript-eslint/no-unused-vars': [
      'error',
      { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
    ],

    // Không để sót lệnh ghi log gỡ lỗi trong mã nguồn
    'no-console': ['error', { allow: ['warn', 'error'] }],

    'no-debugger': 'error',
    eqeqeq: ['error', 'always'],
    'prefer-const': 'error',
  },
  overrides: [
    {
      /**
       * Các file context cố ý export cả Provider (component) và hook dùng nó
       * (ví dụ `AuthProvider` + `useAuth`). Đây là cách tổ chức phổ biến và được
       * khuyến nghị của React: hook nằm cạnh context mà nó đọc.
       *
       * Đánh đổi duy nhất là tính năng hot-reload phải tải lại cả trang khi sửa file
       * context - chấp nhận được, vì các file này rất ít thay đổi.
       */
      files: ['src/contexts/*.tsx'],
      rules: { 'react-refresh/only-export-components': 'off' },
    },
  ],
};
