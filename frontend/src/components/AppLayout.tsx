import { useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  AppstoreOutlined,
  BellOutlined,
  BulbOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ExportOutlined,
  ImportOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MoonOutlined,
  SettingOutlined,
  SolutionOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Breadcrumb, Dropdown, Layout, Menu, Space, Tooltip, Typography, theme } from 'antd';
import type { MenuProps } from 'antd';
import { useAuth } from '@/contexts/AuthContext';
import { useThemeMode } from '@/contexts/ThemeContext';
import NotificationBell from './NotificationBell';
import WarehouseSelector from './WarehouseSelector';
import { ROLE_LABEL } from '@/utils/constants';

const { Header, Sider, Content, Footer } = Layout;
const { Text } = Typography;

/**
 * Bố cục chính của hệ thống: menu trái thu gọn được, thanh tiêu đề và vùng nội dung.
 *
 * Menu được dựng theo VAI TRÒ: ví dụ mục "Người dùng" và "Nhật ký hoạt động" chỉ hiện với
 * ADMIN. Lưu ý quan trọng: việc ẩn menu chỉ để giao diện gọn gàng - backend vẫn kiểm tra
 * quyền ở mọi API, nên người dùng gõ tay đường dẫn cũng không truy cập được.
 */

/** Nhãn tiếng Việt cho breadcrumb, tra theo đoạn đầu của đường dẫn. */
const BREADCRUMB_LABELS: Record<string, string> = {
  dashboard: 'Tổng quan',
  products: 'Sản phẩm',
  categories: 'Loại hàng',
  units: 'Đơn vị tính',
  warehouses: 'Kho',
  suppliers: 'Nhà cung cấp',
  customers: 'Khách hàng',
  receipts: 'Phiếu nhập kho',
  issues: 'Phiếu xuất kho',
  stocktakes: 'Kiểm kê kho',
  inventory: 'Tồn kho',
  'stock-card': 'Thẻ kho',
  replenishment: 'Gợi ý nhập hàng',
  reports: 'Báo cáo',
  'barcode-labels': 'In tem mã vạch',
  users: 'Người dùng',
  profile: 'Hồ sơ cá nhân',
  'change-password': 'Đổi mật khẩu',
  notifications: 'Thông báo',
  'audit-logs': 'Nhật ký hoạt động',
  settings: 'Cấu hình hệ thống',
  new: 'Tạo mới',
  edit: 'Chỉnh sửa',
};

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, isAdmin, canViewCost } = useAuth();
  const { isDark, toggleTheme } = useThemeMode();
  const {
    token: { colorBgContainer, colorBorderSecondary },
  } = theme.useToken();

  /** Menu bên trái, lọc theo vai trò người đăng nhập. */
  const menuItems = useMemo<MenuProps['items']>(() => {
    const items: MenuProps['items'] = [
      { key: '/dashboard', icon: <DashboardOutlined />, label: <Link to="/dashboard">Tổng quan</Link> },
      {
        key: 'catalog',
        icon: <AppstoreOutlined />,
        label: 'Danh mục',
        children: [
          { key: '/products', label: <Link to="/products">Sản phẩm</Link> },
          { key: '/categories', label: <Link to="/categories">Loại hàng</Link> },
          { key: '/units', label: <Link to="/units">Đơn vị tính</Link> },
          { key: '/warehouses', label: <Link to="/warehouses">Kho</Link> },
          { key: '/suppliers', label: <Link to="/suppliers">Nhà cung cấp</Link> },
          { key: '/customers', label: <Link to="/customers">Khách hàng</Link> },
        ],
      },
      { key: '/receipts', icon: <ImportOutlined />, label: <Link to="/receipts">Phiếu nhập kho</Link> },
      { key: '/issues', icon: <ExportOutlined />, label: <Link to="/issues">Phiếu xuất kho</Link> },
      { key: '/stocktakes', icon: <SolutionOutlined />, label: <Link to="/stocktakes">Kiểm kê kho</Link> },
      { key: '/inventory', icon: <DatabaseOutlined />, label: <Link to="/inventory">Tồn kho</Link> },
      // { key: '/stock-card', icon: <FileTextOutlined />, label: <Link to="/stock-card">Thẻ kho</Link> },
    ];

    // Gợi ý nhập hàng và báo cáo giá trị chỉ dành cho ADMIN/MANAGER
    // if (canViewCost) {
    //   items.push({
    //     key: '/replenishment',
    //     icon: <ShopOutlined />,
    //     label: <Link to="/replenishment">Gợi ý nhập hàng</Link>,
    //   });
    //   items.push({ key: '/reports', icon: <BarChartOutlined />, label: <Link to="/reports">Báo cáo</Link> });
    // }

    // items.push({
    //   key: '/barcode-labels',
    //   icon: <BarcodeOutlined />,
    //   label: <Link to="/barcode-labels">In tem mã vạch</Link>,
    // });

    // Nhóm quản trị: chỉ ADMIN
    if (isAdmin) {
      items.push({
        key: 'system',
        icon: <SettingOutlined />,
        label: 'Quản trị',
        children: [
          { key: '/users', icon: <TeamOutlined />, label: <Link to="/users">Người dùng</Link> },
          // { key: '/audit-logs', icon: <AuditOutlined />, label: <Link to="/audit-logs">Nhật ký hoạt động</Link> },
          // { key: '/settings', label: <Link to="/settings">Cấu hình hệ thống</Link> },
        ],
      });
    }

    return items;
  }, [isAdmin, canViewCost]);

  /** Mục menu đang được chọn, suy ra từ đường dẫn hiện tại. */
  const selectedKeys = useMemo(() => {
    const segments = location.pathname.split('/').filter(Boolean);
    return segments.length > 0 ? [`/${segments[0]}`] : ['/dashboard'];
  }, [location.pathname]);

  /** Nhóm menu cần mở sẵn để thấy mục đang chọn. */
  const openKeys = useMemo(() => {
    const first = selectedKeys[0];
    if (['/products', '/categories', '/units', '/warehouses', '/suppliers', '/customers'].includes(first)) {
      return ['catalog'];
    }
    if (['/users', '/audit-logs', '/settings'].includes(first)) {
      return ['system'];
    }
    return [];
  }, [selectedKeys]);

  /** Dãy breadcrumb theo đường dẫn hiện tại. */
  const breadcrumbItems = useMemo(() => {
    const segments = location.pathname.split('/').filter(Boolean);
    const items: Array<{ title: React.ReactNode }> = [{ title: <Link to="/dashboard">Trang chủ</Link> }];

    segments.forEach((segment, index) => {
      const label = BREADCRUMB_LABELS[segment];
      // Bỏ qua các đoạn là số (id của bản ghi) để breadcrumb gọn gàng
      if (!label && /^\d+$/.test(segment)) {
        items.push({ title: 'Chi tiết' });
        return;
      }
      const path = `/${segments.slice(0, index + 1).join('/')}`;
      const isLast = index === segments.length - 1;
      items.push({
        title: isLast ? (label ?? segment) : <Link to={path}>{label ?? segment}</Link>,
      });
    });

    return items;
  }, [location.pathname]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  /** Menu thả xuống của avatar người dùng. */
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'info',
      label: (
        <div style={{ padding: '4px 0' }}>
          <Text strong>{user?.fullName}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            {user ? ROLE_LABEL[user.role] : ''}
          </Text>
        </div>
      ),
      disabled: true,
    },
    { type: 'divider' },
    { key: 'profile', icon: <UserOutlined />, label: <Link to="/profile">Hồ sơ cá nhân</Link> },
    { key: 'change-password', icon: <SettingOutlined />, label: <Link to="/change-password">Đổi mật khẩu</Link> },
    { key: 'notifications', icon: <BellOutlined />, label: <Link to="/notifications">Thông báo</Link> },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true, onClick: handleLogout },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        width={230}
        // Trên điện thoại, menu tự ẩn thành dạng nổi khi màn hình hẹp
        breakpoint="lg"
        collapsedWidth={collapsed ? 80 : 0}
        style={{ position: 'sticky', top: 0, height: '100vh', overflow: 'auto' }}
      >
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            gap: 10,
            padding: collapsed ? 0 : '0 16px',
            color: '#fff',
            fontWeight: 700,
            fontSize: 17,
            letterSpacing: 0.3,
          }}
        >
          <DatabaseOutlined style={{ fontSize: 20 }} />
          {!collapsed && <span>SmartStock</span>}
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={openKeys}
          items={menuItems}
          style={{ borderInlineEnd: 'none' }}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 10,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            padding: '0 16px',
            height: 56,
            background: colorBgContainer,
            borderBottom: `1px solid ${colorBorderSecondary}`,
          }}
        >
          <Space size={4}>
            <Tooltip title={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}>
              <span
                role="button"
                tabIndex={0}
                aria-label={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
                onClick={() => setCollapsed(!collapsed)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    setCollapsed(!collapsed);
                  }
                }}
                style={{ cursor: 'pointer', fontSize: 18, padding: '0 8px' }}
              >
                {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              </span>
            </Tooltip>
            <WarehouseSelector />
          </Space>

          <Space size={14}>
            <Tooltip title={isDark ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối'}>
              <span
                role="button"
                tabIndex={0}
                aria-label={isDark ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối'}
                onClick={toggleTheme}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    toggleTheme();
                  }
                }}
                style={{ cursor: 'pointer', fontSize: 17 }}
              >
                {isDark ? <BulbOutlined /> : <MoonOutlined />}
              </span>
            </Tooltip>

            <NotificationBell />

            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" trigger={['click']}>
              <Space style={{ cursor: 'pointer' }} size={8}>
                <Avatar size={32} src={user?.avatarUrl} icon={<UserOutlined />} />
                <Text style={{ display: 'none' }} className="header-username">
                  {user?.fullName}
                </Text>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{ padding: 16 }}>
          <Breadcrumb items={breadcrumbItems} style={{ marginBottom: 12 }} />
          <Outlet />
        </Content>

        <Footer style={{ textAlign: 'center', padding: '12px 16px' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            SmartStock - Hệ thống quản lý kho đa chi nhánh &copy; {new Date().getFullYear()}
          </Text>
        </Footer>
      </Layout>
    </Layout>
  );
}
