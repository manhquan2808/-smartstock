import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import LoadingBlock from './LoadingBlock';

/**
 * Chặn các trang cần đăng nhập.
 *
 * Giải thích cho người quen PHP: component này tương đương middleware `auth` của Laravel,
 * nhưng chạy ở phía trình duyệt. Nó CHỈ lo phần điều hướng giao diện - việc bảo vệ dữ liệu
 * thật nằm ở backend. Người dùng có thể gõ tay đường dẫn nhưng mọi API đều trả 401/403 nếu
 * không đủ quyền.
 *
 * Trong lúc ứng dụng đang thử phục hồi phiên (gọi /api/auth/refresh), ta hiển thị vòng
 * xoay chờ chứ không chuyển ngay về trang đăng nhập - nếu không, người dùng bấm F5 sẽ bị
 * đẩy ra trang login dù phiên vẫn còn hiệu lực.
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isInitializing, user } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return (
      <LoadingBlock message="Đang kiểm tra phiên đăng nhập..." minHeight={'100vh'} />
    );
  }

  if (!isAuthenticated) {
    // Ghi nhớ trang người dùng muốn vào để sau khi đăng nhập quay lại đúng chỗ
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  // Tài khoản vừa được admin đặt lại mật khẩu: buộc đổi mật khẩu trước khi dùng tiếp
  if (user?.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }

  return <>{children}</>;
}
