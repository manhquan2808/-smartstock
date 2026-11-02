import { useState } from 'react';
import { Link } from 'react-router-dom';
import { MailOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Result, Space, Typography } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';

const { Title, Text, Paragraph } = Typography;

/**
 * Trang QUÊN MẬT KHẨU.
 *
 * Lưu ý bảo mật: dù email không tồn tại trong hệ thống, trang vẫn hiện thông báo thành
 * công. Nếu báo "email không tồn tại", kẻ tấn công có thể dò ra danh sách người dùng của
 * doanh nghiệp. Đây là quy tắc được thực thi ở cả backend và giao diện.
 */
export default function ForgotPasswordPage() {
  const [submittedEmail, setSubmittedEmail] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (email: string) => authApi.forgotPassword(email),
    onSuccess: (_, email) => setSubmittedEmail(email),
    onError: (error) => setErrorMessage(getErrorMessage(error)),
  });

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        background: 'linear-gradient(135deg, #e6f0ff 0%, #f5f7fa 100%)',
      }}
    >
      <Card style={{ width: '100%', maxWidth: 440 }} styles={{ body: { padding: 28 } }}>
        {submittedEmail ? (
          <Result
            status="success"
            title="Đã gửi hướng dẫn đặt lại mật khẩu"
            subTitle={
              <Paragraph style={{ marginBottom: 0 }}>
                Nếu <Text strong>{submittedEmail}</Text> tồn tại trong hệ thống, bạn sẽ nhận được email
                kèm liên kết đặt lại mật khẩu. Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần.
              </Paragraph>
            }
            extra={
              <Link to="/login">
                <Button type="primary">Về trang đăng nhập</Button>
              </Link>
            }
          />
        ) : (
          <>
            <Space direction="vertical" size={2} style={{ marginBottom: 20 }}>
              <Title level={4} style={{ margin: 0 }}>
                Quên mật khẩu
              </Title>
              <Text type="secondary">
                Nhập email của tài khoản. Hệ thống sẽ gửi liên kết để bạn tạo mật khẩu mới.
              </Text>
            </Space>

            {errorMessage && (
              <Alert type="error" showIcon message={errorMessage} style={{ marginBottom: 16 }} closable />
            )}

            <Form layout="vertical" onFinish={(values: { email: string }) => mutation.mutate(values.email)}>
              <Form.Item
                name="email"
                label="Email"
                rules={[
                  { required: true, message: 'Vui lòng nhập email' },
                  { type: 'email', message: 'Email không đúng định dạng' },
                ]}
              >
                <Input prefix={<MailOutlined />} placeholder="email@congty.vn" size="large" autoFocus />
              </Form.Item>

              <Button type="primary" htmlType="submit" block size="large" loading={mutation.isPending}>
                Gửi liên kết đặt lại mật khẩu
              </Button>
            </Form>

            <div style={{ textAlign: 'center', marginTop: 16 }}>
              <Link to="/login">Quay lại đăng nhập</Link>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
