import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { KeyOutlined, SaveOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import {
  App,
  Avatar,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Row,
  Space,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { UploadProps } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { fileApi, profileApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';
import { formatDateTime } from '@/utils/format';
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES, ROLE_COLOR } from '@/utils/constants';
import LoadingBlock from '../components/LoadingBlock';

const { Text } = Typography;

interface ProfileFormValues {
  fullName: string;
  email: string;
  phone?: string;
}

/**
 * Trang HỒ SƠ CÁ NHÂN.
 *
 * Người dùng tự sửa được họ tên, email, số điện thoại và ảnh đại diện.
 * Vai trò và kho phụ trách CHỈ XEM - muốn đổi phải do quản trị viên thực hiện, vì đó là
 * quyết định phân quyền chứ không phải thông tin cá nhân.
 */
export default function ProfilePage() {
  const { message } = App.useApp();
  const { refreshUser } = useAuth();
  const [form] = Form.useForm<ProfileFormValues>();
  const [avatarUrl, setAvatarUrl] = useState<string | undefined>();
  const [isUploading, setIsUploading] = useState(false);

  const { data: profile, isLoading, refetch } = useQuery({
    queryKey: ['profile'],
    queryFn: profileApi.get,
  });

  useEffect(() => {
    if (profile) {
      form.setFieldsValue({
        fullName: profile.fullName,
        email: profile.email,
        phone: profile.phone,
      });
      setAvatarUrl(profile.avatarUrl);
    }
  }, [profile, form]);

  const saveMutation = useMutation({
    mutationFn: (values: ProfileFormValues) => profileApi.update({ ...values, avatarUrl }),
    onSuccess: async () => {
      message.success('Đã cập nhật hồ sơ cá nhân.');
      // Nạp lại thông tin để tên và ảnh trên thanh tiêu đề đổi theo
      await refreshUser();
      await refetch();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    if (!ACCEPTED_IMAGE_TYPES.split(',').includes(file.type)) {
      message.error('Chỉ chấp nhận ảnh định dạng JPG, PNG hoặc WebP.');
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
      message.error('Ảnh vượt quá 2 MB.');
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const handleUpload: UploadProps['customRequest'] = async (options) => {
    setIsUploading(true);
    try {
      const result = await fileApi.upload(options.file as File, 'avatars');
      setAvatarUrl(result.url);
      message.success('Đã tải ảnh lên. Bấm "Lưu thay đổi" để áp dụng.');
      options.onSuccess?.(result);
    } catch (error) {
      message.error(getErrorMessage(error));
      options.onError?.(error as Error);
    } finally {
      setIsUploading(false);
    }
  };

  if (isLoading) {
    return (
      <LoadingBlock message="Đang tải hồ sơ..." />
    );
  }

  return (
    <>
      <PageHeader
        title="Hồ sơ cá nhân"
        description="Vai trò và kho phụ trách do quản trị viên quyết định, bạn chỉ xem được."
        extra={
          <Link to="/change-password">
            <Button icon={<KeyOutlined />}>Đổi mật khẩu</Button>
          </Link>
        }
      />

      <Row gutter={[12, 12]}>
        <Col xs={24} md={8}>
          <Card size="small" title="Ảnh đại diện">
            <Space direction="vertical" size={12} style={{ width: '100%', textAlign: 'center' }}>
              <Avatar size={110} src={avatarUrl} icon={<UserOutlined />} />

              <Upload
                accept={ACCEPTED_IMAGE_TYPES}
                beforeUpload={beforeUpload}
                customRequest={handleUpload}
                showUploadList={false}
                maxCount={1}
              >
                <Button icon={<UploadOutlined />} loading={isUploading}>
                  {avatarUrl ? 'Đổi ảnh khác' : 'Tải ảnh lên'}
                </Button>
              </Upload>

              {avatarUrl && (
                <Button size="small" type="text" danger onClick={() => setAvatarUrl(undefined)}>
                  Bỏ ảnh
                </Button>
              )}

              <Text type="secondary" style={{ fontSize: 12 }}>
                JPG, PNG hoặc WebP, tối đa 2 MB.
              </Text>
            </Space>
          </Card>

          <Card size="small" title="Thông tin phân quyền" style={{ marginTop: 12 }}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Tên đăng nhập">
                <Text strong>{profile?.username}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Vai trò">
                {profile && <Tag color={ROLE_COLOR[profile.role]}>{profile.roleName}</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="Kho phụ trách">
                {profile?.role === 'ADMIN' ? (
                  <Tag>Tất cả kho</Tag>
                ) : profile && profile.warehouses.length > 0 ? (
                  <Space size={2} wrap>
                    {profile.warehouses.map((warehouse) => (
                      <Tag key={warehouse.id}>{warehouse.name}</Tag>
                    ))}
                  </Space>
                ) : (
                  <Text type="danger">Chưa được gán kho</Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="Đăng nhập lần cuối">
                {profile?.lastLoginAt ? formatDateTime(profile.lastLoginAt) : 'Chưa có'}
              </Descriptions.Item>
              <Descriptions.Item label="Ngày tạo tài khoản">
                {formatDateTime(profile?.createdAt)}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} md={16}>
          <Card size="small" title="Thông tin cá nhân">
            <Form<ProfileFormValues>
              form={form}
              layout="vertical"
              onFinish={(values) => saveMutation.mutate(values)}
            >
              <Form.Item
                name="fullName"
                label="Họ tên"
                rules={[
                  { required: true, message: 'Vui lòng nhập họ tên' },
                  { max: 150, message: 'Họ tên tối đa 150 ký tự' },
                ]}
              >
                <Input placeholder="Họ và tên đầy đủ" />
              </Form.Item>

              <Form.Item
                name="email"
                label="Email"
                tooltip="Dùng để nhận thông báo phiếu chờ duyệt, báo cáo tồn thấp và liên kết đặt lại mật khẩu."
                rules={[
                  { required: true, message: 'Vui lòng nhập email' },
                  { type: 'email', message: 'Email không đúng định dạng' },
                ]}
              >
                <Input placeholder="email@congty.vn" />
              </Form.Item>

              <Form.Item
                name="phone"
                label="Số điện thoại"
                rules={[{ pattern: /^$|^[0-9+\s.()-]{8,20}$/, message: 'Số điện thoại không đúng định dạng' }]}
              >
                <Input placeholder="0912345678" />
              </Form.Item>

              <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saveMutation.isPending}>
                Lưu thay đổi
              </Button>
            </Form>
          </Card>
        </Col>
      </Row>
    </>
  );
}
