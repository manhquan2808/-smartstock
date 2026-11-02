import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { App as AntApp, ConfigProvider, theme } from 'antd';
import viVN from 'antd/locale/vi_VN';
import { useThemeMode } from './contexts/ThemeContext';
import { WarehouseProvider } from './contexts/WarehouseContext';
import AppLayout from './components/AppLayout';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import { PRIMARY_COLOR } from './utils/constants';
import LoadingBlock from './components/LoadingBlock';

/**
 * Khai báo toàn bộ đường dẫn của ứng dụng và cấu hình chủ đề Ant Design.
 *
 * Các trang được nạp theo kiểu "lazy": mã của một trang chỉ được tải khi người dùng mở
 * trang đó. Nhờ vậy lần vào đầu tiên tải nhanh hơn nhiều so với tải hết hơn 20 trang.
 */

// ---------- Trang công khai (không cần đăng nhập) ----------
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'));

// ---------- Trang trong hệ thống ----------
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ProductListPage = lazy(() => import('./pages/ProductListPage'));
const ProductFormPage = lazy(() => import('./pages/ProductFormPage'));
const ProductDetailPage = lazy(() => import('./pages/ProductDetailPage'));
const CategoryPage = lazy(() => import('./pages/CategoryPage'));
const UnitPage = lazy(() => import('./pages/UnitPage'));
const WarehousePage = lazy(() => import('./pages/WarehousePage'));
const SupplierPage = lazy(() => import('./pages/SupplierPage'));
const CustomerPage = lazy(() => import('./pages/CustomerPage'));
const ReceiptListPage = lazy(() => import('./pages/ReceiptListPage'));
const ReceiptFormPage = lazy(() => import('./pages/ReceiptFormPage'));
const ReceiptDetailPage = lazy(() => import('./pages/ReceiptDetailPage'));
const IssueListPage = lazy(() => import('./pages/IssueListPage'));
const IssueFormPage = lazy(() => import('./pages/IssueFormPage'));
const IssueDetailPage = lazy(() => import('./pages/IssueDetailPage'));
const StocktakeListPage = lazy(() => import('./pages/StocktakeListPage'));
const StocktakeDetailPage = lazy(() => import('./pages/StocktakeDetailPage'));
const InventoryPage = lazy(() => import('./pages/InventoryPage'));
const StockCardPage = lazy(() => import('./pages/StockCardPage'));
const ReplenishmentPage = lazy(() => import('./pages/ReplenishmentPage'));
const ReportPage = lazy(() => import('./pages/ReportPage'));
const BarcodeLabelPage = lazy(() => import('./pages/BarcodeLabelPage'));
const UserPage = lazy(() => import('./pages/UserPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const ChangePasswordPage = lazy(() => import('./pages/ChangePasswordPage'));
const NotificationPage = lazy(() => import('./pages/NotificationPage'));
const AuditLogPage = lazy(() => import('./pages/AuditLogPage'));
const SettingPage = lazy(() => import('./pages/SettingPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));

/** Màn hình chờ trong lúc tải mã của một trang. */
function PageLoading() {
  return (
    <LoadingBlock message="Đang tải..." />
  );
}

export default function App() {
  const { isDark } = useThemeMode();

  return (
    <ConfigProvider
      // locale vi_VN: bộ chọn ngày, phân trang, bảng... hiển thị tiếng Việt
      locale={viVN}
      theme={{
        algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: PRIMARY_COLOR,
          borderRadius: 8,
          // Font hệ thống, có hỗ trợ tiếng Việt trên cả Windows, macOS và Android
          fontFamily:
            "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
        },
        components: {
          Layout: {
            // Nền xám nhạt ở chế độ sáng giúp các thẻ nội dung nổi lên rõ hơn
            bodyBg: isDark ? '#141414' : '#f5f7fa',
          },
          Table: {
            headerBg: isDark ? '#1f1f1f' : '#fafafa',
            rowHoverBg: isDark ? '#262626' : '#f0f5ff',
          },
        },
      }}
    >
      {/* AntApp cung cấp message/notification/modal dùng được qua hook, không cần import tĩnh */}
      <AntApp>
        <Suspense fallback={<PageLoading />}>
          <Routes>
            {/* ---------- Đường dẫn công khai ---------- */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />

            {/* ---------- Đường dẫn cần đăng nhập ---------- */}
            <Route
              element={
                <ProtectedRoute>
                  <WarehouseProvider>
                    <AppLayout />
                  </WarehouseProvider>
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage />} />

              {/* Danh mục */}
              <Route path="/products" element={<ProductListPage />} />
              <Route path="/products/new" element={<ProductFormPage />} />
              <Route path="/products/:id/edit" element={<ProductFormPage />} />
              <Route path="/products/:id" element={<ProductDetailPage />} />
              <Route path="/categories" element={<CategoryPage />} />
              <Route path="/units" element={<UnitPage />} />
              <Route path="/warehouses" element={<WarehousePage />} />
              <Route path="/suppliers" element={<SupplierPage />} />
              <Route path="/customers" element={<CustomerPage />} />

              {/* Phiếu nhập */}
              <Route path="/receipts" element={<ReceiptListPage />} />
              <Route path="/receipts/new" element={<ReceiptFormPage />} />
              <Route path="/receipts/:id/edit" element={<ReceiptFormPage />} />
              <Route path="/receipts/:id" element={<ReceiptDetailPage />} />

              {/* Phiếu xuất */}
              <Route path="/issues" element={<IssueListPage />} />
              <Route path="/issues/new" element={<IssueFormPage />} />
              <Route path="/issues/:id/edit" element={<IssueFormPage />} />
              <Route path="/issues/:id" element={<IssueDetailPage />} />

              {/* Kiểm kê */}
              <Route path="/stocktakes" element={<StocktakeListPage />} />
              <Route path="/stocktakes/:id" element={<StocktakeDetailPage />} />

              {/* Tồn kho và báo cáo */}
              <Route path="/inventory" element={<InventoryPage />} />
              <Route path="/stock-card" element={<StockCardPage />} />
              <Route path="/replenishment" element={<ReplenishmentPage />} />
              <Route path="/reports" element={<ReportPage />} />
              <Route path="/barcode-labels" element={<BarcodeLabelPage />} />

              {/* Hệ thống */}
              <Route path="/users" element={<UserPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/change-password" element={<ChangePasswordPage />} />
              <Route path="/notifications" element={<NotificationPage />} />
              <Route path="/audit-logs" element={<AuditLogPage />} />
              <Route path="/settings" element={<SettingPage />} />
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
      </AntApp>
    </ConfigProvider>
  );
}
