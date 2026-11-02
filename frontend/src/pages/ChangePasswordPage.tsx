import { useNavigate } from 'react-router-dom';
import { LockOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Form, Input, Space, Typography } from 'antd';
import { useMutation } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { authApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';

const { Text } = Typography;

interface ChangePasswordFormValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/**
 * Trang ĐỔI MẬT KHẨU cho người đang đăng nhập.
 *
 * Trang này cũng là nơi người dùng bị chuyển tới bắt buộc khi quản trị viên vừa đặt lại
 * mật khẩu cho họ (cờ `mustChangePassword` trong thông tin người dùng).
 */
export default function ChangePasswordPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { user, refreshUser } = useAuth();
  const [form] = Form.useForm<ChangePasswordFormValues>();

  const mutation = useMutation({
    mutationFn: (values: ChangePasswordFormValues) =>
      authApi.changePassword(values.currentPassword, values.newPassword),
    onSuccess: async () => {
      message.success('Đổi mật khẩu thành công.');
      form.resetFields();
      // Nạp lại thông tin để xóa cờ "bắt buộc đổi mật khẩu"
      await refreshUser();
      navigate('/dashboard');
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  return (
    <>
      <PageHeader title="Đổi mật khẩu" description="Mật khẩu mới phải khác mật khẩu hiện tại." />

      {user?.mustChangePassword && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="Bạn cần đổi mật khẩu trước khi tiếp tục sử dụng hệ thống"
          description="Quản trị viên vừa đặt lại mật khẩu cho tài khoản của bạn."
        />
      )}

      <Card style={{ maxWidth: 520 }}>
        <Form<ChangePasswordFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => mutation.mutate(values)}
        >
          <Form.Item
            name="currentPassword"
            label="Mật khẩu hiện tại"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu hiện tại' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="Mật khẩu đang dùng" autoFocus />
          </Form.Item>

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
            <Input.Password prefix={<LockOutlined />} placeholder="Mật khẩu mới" />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label="Nhập lại mật khẩu mới"
            dependencies={['newPassword']}
            hasFeedback
            rules={[
              { required: true, message: 'Vui lòng nhập lại mật khẩu mới' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || getFieldValue('newPassword') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('Hai mật khẩu không khớp nhau')),
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="Nhập lại mật khẩu mới" />
          </Form.Item>

          <Space>
            <Button type="primary" htmlType="submit" loading={mutation.isPending}>
              Đổi mật khẩu
            </Button>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Sau khi đổi, phiên hiện tại vẫn được giữ.
            </Text>
          </Space>
        </Form>
      </Card>
    </>
  );
}
