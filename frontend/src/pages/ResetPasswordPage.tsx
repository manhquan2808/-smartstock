import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { LockOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Result, Space, Typography } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';

const { Title, Text } = Typography;

interface ResetFormValues {
  newPassword: string;
  confirmPassword: string;
}

/**
 * Trang ĐẶT LẠI MẬT KHẨU bằng liên kết trong email.
 *
 * Mã xác thực nằm trên URL (`/reset-password?token=...`). Nếu thiếu mã, trang hiển thị
 * hướng dẫn yêu cầu liên kết mới thay vì để người dùng nhập xong mới báo lỗi.
 */
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isDone, setIsDone] = useState(false);

  const mutation = useMutation({
    mutationFn: (values: ResetFormValues) => authApi.resetPassword(token ?? '', values.newPassword),
    onSuccess: () => setIsDone(true),
    onError: (error) => setErrorMessage(getErrorMessage(error)),
  });

  const wrapperStyle: React.CSSProperties = {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
    background: 'linear-gradient(135deg, #e6f0ff 0%, #f5f7fa 100%)',
  };

  if (!token) {
    return (
      <div style={wrapperStyle}>
        <Card style={{ width: '100%', maxWidth: 440 }}>
          <Result
            status="warning"
            title="Liên kết không hợp lệ"
            subTitle="Liên kết đặt lại mật khẩu thiếu mã xác thực. Vui lòng yêu cầu liên kết mới."
            extra={
              <Link to="/forgot-password">
                <Button type="primary">Yêu cầu liên kết mới</Button>
              </Link>
            }
          />
        </Card>
      </div>
    );
  }

  if (isDone) {
    return (
      <div style={wrapperStyle}>
        <Card style={{ width: '100%', maxWidth: 440 }}>
          <Result
            status="success"
            title="Đặt lại mật khẩu thành công"
            subTitle="Bạn có thể đăng nhập bằng mật khẩu mới. Các thiết bị đang đăng nhập khác đã bị đăng xuất."
            extra={
              <Button type="primary" onClick={() => navigate('/login', { replace: true })}>
                Đăng nhập ngay
              </Button>
            }
          />
        </Card>
      </div>
    );
  }

  return (
    <div style={wrapperStyle}>
      <Card style={{ width: '100%', maxWidth: 440 }} styles={{ body: { padding: 28 } }}>
        <Space direction="vertical" size={2} style={{ marginBottom: 20 }}>
          <Title level={4} style={{ margin: 0 }}>
            Tạo mật khẩu mới
          </Title>
          <Text type="secondary">Mật khẩu cần tối thiểu 8 ký tự, có chữ thường, chữ in hoa và chữ số.</Text>
        </Space>

        {errorMessage && (
          <Alert type="error" showIcon message={errorMessage} style={{ marginBottom: 16 }} closable />
        )}

        <Form<ResetFormValues> layout="vertical" onFinish={(values) => mutation.mutate(values)}>
          <Form.Item
            name="newPassword"
            label="Mật khẩu mới"
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu mới' },
              { min: 8, message: 'Mật khẩu phải có ít nhất 8 ký tự' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/,
                message: 'Mật khẩu phải có chữ thường, chữ in hoa và chữ số',
              },
            ]}
            hasFeedback
          >
            <Input.Password prefix={<LockOutlined />} size="large" placeholder="Nhập mật khẩu mới" autoFocus />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="Nhập lại mật khẩu mới"
            dependencies={['newPassword']}
            hasFeedback
            rules={[
              { required: true, message: 'Vui lòng nhập lại mật khẩu' },
              // So khớp hai lần nhập để tránh người dùng gõ sai mà không biết
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || getFieldValue('newPassword') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('Hai mật khẩu không khớp nhau')),
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} size="large" placeholder="Nhập lại mật khẩu mới" />
          </Form.Item>

          <Button type="primary" htmlType="submit" block size="large" loading={mutation.isPending}>
            Đặt lại mật khẩu
          </Button>
        </Form>

        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Link to="/login">Quay lại đăng nhập</Link>
        </div>
      </Card>
    </div>
  );
}
