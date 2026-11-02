import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { DatabaseOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Checkbox, Form, Input, Space, Typography } from 'antd';
import { useAuth } from '@/contexts/AuthContext';
import { useThemeMode } from '@/contexts/ThemeContext';
import { getErrorMessage } from '@/api/client';

const { Title, Text, Paragraph } = Typography;

interface LoginFormValues {
  username: string;
  password: string;
}

/**
 * Trang đăng nhập.
 *
 * Hệ thống nội bộ nên KHÔNG có chức năng tự đăng ký - tài khoản do quản trị viên tạo.
 * Trang có liệt kê sẵn ba tài khoản demo để người chấm/giảng viên thử nhanh.
 */
export default function LoginPage() {
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { login, isAuthenticated, isInitializing } = useAuth();
  const { isDark } = useThemeMode();
  const navigate = useNavigate();
  const location = useLocation();

  // Đã đăng nhập rồi thì không cho vào lại trang này
  if (!isInitializing && isAuthenticated) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from ?? '/dashboard'} replace />;
  }

  const handleSubmit = async (values: LoginFormValues) => {
    setErrorMessage(null);
    setIsSubmitting(true);
    try {
      const user = await login(values.username.trim(), values.password);
      // Tài khoản bị buộc đổi mật khẩu thì vào thẳng trang đổi mật khẩu
      if (user.mustChangePassword) {
        navigate('/change-password', { replace: true });
        return;
      }
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? '/dashboard', { replace: true });
    } catch (error) {
      setErrorMessage(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        background: isDark
          ? 'linear-gradient(135deg, #0f1724 0%, #1a2332 100%)'
          : 'linear-gradient(135deg, #e6f0ff 0%, #f5f7fa 60%, #ffffff 100%)',
      }}
    >
      <Card
        style={{ width: '100%', maxWidth: 420, boxShadow: '0 8px 28px rgba(0,0,0,0.12)' }}
        styles={{ body: { padding: 28 } }}
      >
        <Space direction="vertical" size={4} style={{ width: '100%', textAlign: 'center', marginBottom: 22 }}>
          <DatabaseOutlined style={{ fontSize: 40, color: '#1677ff' }} />
          <Title level={3} style={{ margin: '8px 0 0' }}>
            SmartStock
          </Title>
          <Text type="secondary">Hệ thống quản lý kho đa chi nhánh</Text>
        </Space>

        {errorMessage && (
          <Alert
            type="error"
            showIcon
            message={errorMessage}
            style={{ marginBottom: 16 }}
            closable
            onClose={() => setErrorMessage(null)}
          />
        )}

        <Form<LoginFormValues> layout="vertical" onFinish={handleSubmit} size="large" requiredMark={false}>
          <Form.Item
            name="username"
            label="Tên đăng nhập"
            rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="Nhập tên đăng nhập" autoComplete="username" autoFocus />
          </Form.Item>

          <Form.Item
            name="password"
            label="Mật khẩu"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="Nhập mật khẩu"
              autoComplete="current-password"
            />
          </Form.Item>

          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
            <Form.Item name="remember" valuePropName="checked" noStyle>
              <Checkbox>Ghi nhớ đăng nhập</Checkbox>
            </Form.Item>
            <Link to="/forgot-password">Quên mật khẩu?</Link>
          </div>

          <Button type="primary" htmlType="submit" block loading={isSubmitting} size="large">
            Đăng nhập
          </Button>
        </Form>

        {/* Thông tin tài khoản demo - hữu ích khi trình bày đồ án */}
        <Card
          size="small"
          style={{ marginTop: 20, background: isDark ? 'rgba(255,255,255,0.04)' : '#fafafa' }}
          styles={{ body: { padding: '10px 12px' } }}
        >
          <Paragraph style={{ margin: 0, fontSize: 12 }}>
            <Text strong style={{ fontSize: 12 }}>
              Tài khoản demo:
            </Text>
            <br />
            <Text code style={{ fontSize: 11 }}>
              admin / Admin@123
            </Text>{' '}
            <Text type="secondary" style={{ fontSize: 11 }}>
              (toàn quyền)
            </Text>
            <br />
            <Text code style={{ fontSize: 11 }}>
              quanly / Quanly@123
            </Text>{' '}
            <Text type="secondary" style={{ fontSize: 11 }}>
              (quản lý kho Hà Nội, Đà Nẵng)
            </Text>
            <br />
            <Text code style={{ fontSize: 11 }}>
              nhanvien / Nhanvien@123
            </Text>{' '}
            <Text type="secondary" style={{ fontSize: 11 }}>
              (nhân viên kho Hà Nội)
            </Text>
          </Paragraph>
        </Card>
      </Card>
    </div>
  );
}
