import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '@/api/endpoints';
import { setAccessToken, setSessionExpiredHandler } from '@/api/client';
import type { CurrentUser, Role } from '@/types';

/**
 * Quản lý trạng thái đăng nhập của toàn ứng dụng.
 *
 * Giải thích cho người quen PHP: Context của React tương đương biến session dùng chung
 * cho mọi trang, nhưng nằm ở phía trình duyệt. Mọi component đều đọc được thông tin người
 * đăng nhập qua hook `useAuth()` mà không phải truyền tham số qua nhiều cấp.
 *
 * Luồng khởi động: khi người dùng tải lại trang (F5), access token trong bộ nhớ đã mất.
 * Ứng dụng thử gọi `/api/auth/refresh` một lần - nếu cookie refresh token còn hiệu lực
 * thì lấy lại được phiên, người dùng không phải đăng nhập lại.
 */

interface AuthContextValue {
  user: CurrentUser | null;
  /** true trong lúc đang kiểm tra phiên đăng nhập lúc khởi động. */
  isInitializing: boolean;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<CurrentUser>;
  logout: () => Promise<void>;
  /** Nạp lại thông tin người dùng (sau khi cập nhật hồ sơ). */
  refreshUser: () => Promise<void>;
  /** Kiểm tra người dùng có một trong các vai trò cho trước. */
  hasRole: (...roles: Role[]) => boolean;
  /** ADMIN và MANAGER được xem giá vốn, giá trị tồn. */
  canViewCost: boolean;
  /** ADMIN và MANAGER được duyệt phiếu. */
  canApprove: boolean;
  /** ADMIN và MANAGER được thêm/sửa danh mục. */
  canEditCatalog: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  /** Xóa trạng thái đăng nhập ở phía trình duyệt. */
  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
  }, []);

  // Khi interceptor phát hiện phiên hết hạn hoàn toàn, nó gọi hàm này
  useEffect(() => {
    setSessionExpiredHandler(clearSession);
  }, [clearSession]);

  // Thử phục hồi phiên đăng nhập một lần khi ứng dụng khởi động
  useEffect(() => {
    let cancelled = false;

    const restoreSession = async () => {
      try {
        const response = await authApi.refresh();
        if (!cancelled) {
          setAccessToken(response.accessToken);
          setUser(response.user);
        }
      } catch {
        // Không có cookie refresh hợp lệ: đây là trường hợp bình thường khi
        // người dùng chưa đăng nhập hoặc phiên đã hết hạn
        if (!cancelled) {
          clearSession();
        }
      } finally {
        if (!cancelled) {
          setIsInitializing(false);
        }
      }
    };

    void restoreSession();
    return () => {
      cancelled = true;
    };
  }, [clearSession]);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username, password);
    setAccessToken(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      // Dù gọi API thất bại vẫn phải xóa trạng thái ở trình duyệt
      clearSession();
    }
  }, [clearSession]);

  const refreshUser = useCallback(async () => {
    const current = await authApi.me();
    setUser(current);
  }, []);

  const hasRole = useCallback(
    (...roles: Role[]) => (user ? roles.includes(user.role) : false),
    [user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isInitializing,
      isAuthenticated: user !== null,
      login,
      logout,
      refreshUser,
      hasRole,
      canViewCost: user?.role === 'ADMIN' || user?.role === 'MANAGER',
      canApprove: user?.role === 'ADMIN' || user?.role === 'MANAGER',
      canEditCatalog: user?.role === 'ADMIN' || user?.role === 'MANAGER',
      isAdmin: user?.role === 'ADMIN',
    }),
    [user, isInitializing, login, logout, refreshUser, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Hook đọc thông tin phiên đăng nhập. Phải dùng bên trong AuthProvider. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth phải được dùng bên trong AuthProvider');
  }
  return context;
}
