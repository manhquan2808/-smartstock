import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

/**
 * Quản lý chế độ sáng/tối (dark mode).
 *
 * Lựa chọn của người dùng được ghi vào localStorage nên mở lại trang vẫn giữ nguyên.
 * Nếu người dùng chưa từng chọn, hệ thống theo thiết lập của hệ điều hành
 * ({@code prefers-color-scheme}).
 */

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'smartstock-theme';

interface ThemeContextValue {
  mode: ThemeMode;
  isDark: boolean;
  toggleTheme: () => void;
  setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

/** Đọc lựa chọn đã lưu, hoặc lấy theo thiết lập hệ điều hành. */
function resolveInitialMode(): ThemeMode {
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(resolveInitialMode);

  // Gắn thuộc tính lên thẻ <html> để CSS tùy biến theo chế độ
  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, mode);
    document.documentElement.setAttribute('data-theme', mode);
    document.documentElement.style.colorScheme = mode;
  }, [mode]);

  const setMode = useCallback((next: ThemeMode) => setModeState(next), []);

  const toggleTheme = useCallback(() => {
    setModeState((current) => (current === 'light' ? 'dark' : 'light'));
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({ mode, isDark: mode === 'dark', toggleTheme, setMode }),
    [mode, toggleTheme, setMode],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useThemeMode(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useThemeMode phải được dùng bên trong ThemeProvider');
  }
  return context;
}
