import { useState } from 'react';
import { EditOutlined, KeyOutlined, LockOutlined, PlusOutlined, SearchOutlined, UnlockOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { userApi, warehouseApi, type SaveUserRequest } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { applyServerFieldErrors } from '@/utils/form';
import { useUrlFilters, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { formatDateTime } from '@/utils/format';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS, ROLE_COLOR, ROLE_OPTIONS } from '@/utils/constants';
import type { Role, UserResponse, UserStatus } from '@/types';

const { Text } = Typography;

interface UserFormValues extends SaveUserRequest {
  warehouseIds?: number[];
}

/**
 * Trang QUẢN LÝ NGƯỜI DÙNG - chỉ ADMIN truy cập được.
 *
 * Các quy tắc nghiệp vụ được backend bảo đảm, giao diện chỉ hướng dẫn người dùng:
 *
 * - Không xóa tài khoản, chỉ KHÓA, để lịch sử phiếu vẫn truy được người lập.
 * - MANAGER và STAFF buộc phải được gán ít nhất một kho; ADMIN mặc định thấy mọi kho.
 * - Không tự khóa chính mình và phải còn ít nhất một ADMIN đang hoạt động.
 */
export default function UserPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<UserFormValues>();
  const [resetForm] = Form.useForm<{ newPassword: string }>();

  const [editingUser, setEditingUser] = useState<UserResponse | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [resetPasswordUser, setResetPasswordUser] = useState<UserResponse | null>(null);

  const { filters, setFilter, page, size, setPagination } = useUrlFilters(
    { keyword: '', role: '', status: '' },
    DEFAULT_PAGE_SIZE,
  );

  const selectedRole = Form.useWatch('role', form);

  const { data: warehouses = [] } = useQuery({
    queryKey: ['warehouses', 'all-for-assign'],
    queryFn: () => warehouseApi.search({ size: 100 }).then((page) => page.content),
    staleTime: 5 * 60 * 1000,
  });

  const { data, isFetching } = useQuery({
    queryKey: ['users', 'search', filters, page, size],
    queryFn: () =>
      userApi.search({
        keyword: toStringOrUndefined(filters.keyword),
        role: toStringOrUndefined(filters.role) as Role | undefined,
        status: toStringOrUndefined(filters.status) as UserStatus | undefined,
        page,
        size,
      }),
  });

  const saveMutation = useMutation({
    mutationFn: (values: UserFormValues) => {
      const payload: SaveUserRequest = {
        ...values,
        // ADMIN không cần gán kho (mặc định thấy tất cả)
        warehouseIds: values.role === 'ADMIN' ? [] : (values.warehouseIds ?? []),
      };
      return editingUser ? userApi.update(editingUser.id, payload) : userApi.create(payload);
    },
    onSuccess: () => {
      message.success(editingUser ? 'Đã cập nhật tài khoản.' : 'Đã tạo tài khoản mới.');
      closeModal();
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error) => {
      applyServerFieldErrors(form, error);
      message.error(getErrorMessage(error));
    },
  });

  const lockMutation = useMutation({
    mutationFn: ({ id, lock }: { id: number; lock: boolean }) =>
      lock ? userApi.lock(id) : userApi.unlock(id),
    onSuccess: (user) => {
      message.success(
        user.status === 'LOCKED'
          ? `Đã khóa tài khoản ${user.username}. Mọi phiên đăng nhập của tài khoản này đã bị thu hồi.`
          : `Đã mở khóa tài khoản ${user.username}.`,
      );
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const resetPasswordMutation = useMutation({
    mutationFn: ({ id, newPassword }: { id: number; newPassword: string }) =>
      userApi.resetPassword(id, newPassword, true),
    onSuccess: () => {
      message.success('Đã đặt lại mật khẩu. Người dùng phải đổi mật khẩu khi đăng nhập lần tới.');
      setResetPasswordUser(null);
      resetForm.resetFields();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const openCreateModal = () => {
    setEditingUser(null);
    form.resetFields();
    form.setFieldsValue({ role: 'STAFF', mustChangePassword: true });
    setIsModalOpen(true);
  };

  const openEditModal = (user: UserResponse) => {
    setEditingUser(user);
    form.setFieldsValue({
      email: user.email,
      fullName: user.fullName,
      phone: user.phone,
      role: user.role,
      warehouseIds: user.warehouses.map((warehouse) => warehouse.id),
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingUser(null);
    form.resetFields();
  };

  const columns: ColumnsType<UserResponse> = [
    {
      title: 'Tên đăng nhập',
      dataIndex: 'username',
      width: 140,
      render: (username: string) => <Text strong>{username}</Text>,
    },
    { title: 'Họ tên', dataIndex: 'fullName', width: 200 },
    { title: 'Email', dataIndex: 'email', responsive: ['lg'] },
    { title: 'Điện thoại', dataIndex: 'phone', width: 130, responsive: ['xl'], render: (value?: string) => value ?? '—' },
    {
      title: 'Vai trò',
      dataIndex: 'role',
      width: 150,
      render: (role: Role, record) => <Tag color={ROLE_COLOR[role]}>{record.roleName}</Tag>,
    },
    {
      title: 'Kho phụ trách',
      key: 'warehouses',
      width: 200,
      responsive: ['lg'],
      render: (_, record) =>
        record.role === 'ADMIN' ? (
          <Tag>Tất cả kho</Tag>
        ) : record.warehouses.length === 0 ? (
          <Text type="danger" style={{ fontSize: 12 }}>
            Chưa gán kho
          </Text>
        ) : (
          <Space size={2} wrap>
            {record.warehouses.map((warehouse) => (
              <Tag key={warehouse.id} style={{ fontSize: 11 }}>
                {warehouse.name}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (status: UserStatus, record) => (
        <Space direction="vertical" size={0}>
          <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>{record.statusName}</Tag>
          {record.mustChangePassword && (
            <Text type="warning" style={{ fontSize: 11 }}>
              Phải đổi mật khẩu
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Đăng nhập cuối',
      dataIndex: 'lastLoginAt',
      width: 150,
      responsive: ['xl'],
      render: (value?: string) => (value ? formatDateTime(value) : 'Chưa đăng nhập'),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Tooltip title="Sửa thông tin">
            <Button size="small" icon={<EditOutlined />} onClick={() => openEditModal(record)} />
          </Tooltip>

          <Tooltip title="Đặt lại mật khẩu">
            <Button
              size="small"
              icon={<KeyOutlined />}
              onClick={() => {
                setResetPasswordUser(record);
                resetForm.resetFields();
              }}
            />
          </Tooltip>

          <Tooltip title={record.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}>
            <Popconfirm
              title={record.status === 'ACTIVE' ? 'Khóa tài khoản này?' : 'Mở khóa tài khoản này?'}
              description={
                record.status === 'ACTIVE'
                  ? 'Người dùng sẽ bị đăng xuất khỏi mọi thiết bị và không đăng nhập được nữa. Lịch sử phiếu vẫn được giữ nguyên.'
                  : 'Người dùng sẽ đăng nhập lại được.'
              }
              okText="Đồng ý"
              cancelText="Hủy"
              okButtonProps={{ danger: record.status === 'ACTIVE' }}
              onConfirm={() => lockMutation.mutate({ id: record.id, lock: record.status === 'ACTIVE' })}
            >
              <Button
                size="small"
                danger={record.status === 'ACTIVE'}
                icon={record.status === 'ACTIVE' ? <LockOutlined /> : <UnlockOutlined />}
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Người dùng"
        description="Hệ thống nội bộ: tài khoản do quản trị viên tạo, không có chức năng tự đăng ký."
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            Tạo tài khoản
          </Button>
        }
      />

      <Card size="small">
        <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
          <Col xs={24} md={10}>
            <Input
              placeholder="Tìm theo tên đăng nhập, họ tên hoặc email..."
              prefix={<SearchOutlined />}
              allowClear
              defaultValue={filters.keyword}
              onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
              onClear={() => setFilter('keyword', '')}
            />
          </Col>
          <Col xs={12} md={7}>
            <Select
              placeholder="Tất cả vai trò"
              allowClear
              style={{ width: '100%' }}
              value={filters.role || undefined}
              onChange={(value) => setFilter('role', value)}
              options={ROLE_OPTIONS}
            />
          </Col>
          <Col xs={12} md={7}>
            <Select
              placeholder="Tất cả trạng thái"
              allowClear
              style={{ width: '100%' }}
              value={filters.status || undefined}
              onChange={(value) => setFilter('status', value)}
              options={[
                { value: 'ACTIVE', label: 'Đang hoạt động' },
                { value: 'LOCKED', label: 'Đã khóa' },
              ]}
            />
          </Col>
        </Row>

        <Table<UserResponse>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          rowClassName={(record) => (record.status === 'LOCKED' ? 'row-warning' : '')}
          locale={{ emptyText: 'Không tìm thấy tài khoản nào.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} tài khoản`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
        />
      </Card>

      {/* ---------- Hộp thoại tạo / sửa tài khoản ---------- */}
      <Modal
        open={isModalOpen}
        title={editingUser ? `Sửa tài khoản ${editingUser.username}` : 'Tạo tài khoản mới'}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="Lưu"
        cancelText="Hủy"
        width={620}
        destroyOnHidden
      >
        <Form<UserFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          {!editingUser && (
            <Row gutter={12}>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="username"
                  label="Tên đăng nhập"
                  tooltip="Không đổi được sau khi tạo, vì các phiếu cũ cần truy được người lập."
                  rules={[
                    { required: true, message: 'Vui lòng nhập tên đăng nhập' },
                    { min: 3, max: 50, message: 'Tên đăng nhập phải có từ 3 đến 50 ký tự' },
                    {
                      pattern: /^[a-zA-Z0-9._-]+$/,
                      message: 'Chỉ gồm chữ không dấu, số và các ký tự . _ -',
                    },
                  ]}
                >
                  <Input placeholder="nhanvien01" autoComplete="off" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="password"
                  label="Mật khẩu"
                  rules={[
                    { required: true, message: 'Vui lòng nhập mật khẩu' },
                    { min: 8, message: 'Mật khẩu phải có ít nhất 8 ký tự' },
                    {
                      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/,
                      message: 'Phải có chữ thường, chữ in hoa và chữ số',
                    },
                  ]}
                >
                  <Input.Password placeholder="Mật khẩu ban đầu" autoComplete="new-password" />
                </Form.Item>
              </Col>
            </Row>
          )}

          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="fullName"
                label="Họ tên"
                rules={[
                  { required: true, message: 'Vui lòng nhập họ tên' },
                  { max: 150, message: 'Họ tên tối đa 150 ký tự' },
                ]}
              >
                <Input placeholder="Nguyễn Văn A" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="email"
                label="Email"
                tooltip="Dùng để nhận thông báo phiếu chờ duyệt và liên kết đặt lại mật khẩu."
                rules={[
                  { required: true, message: 'Vui lòng nhập email' },
                  { type: 'email', message: 'Email không đúng định dạng' },
                ]}
              >
                <Input placeholder="nhanvien@congty.vn" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="phone"
                label="Điện thoại"
                rules={[{ pattern: /^$|^[0-9+\s.()-]{8,20}$/, message: 'Số điện thoại không đúng định dạng' }]}
              >
                <Input placeholder="0912345678" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="role"
                label="Vai trò"
                rules={[{ required: true, message: 'Vui lòng chọn vai trò' }]}
              >
                <Select options={ROLE_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

          {/* ADMIN mặc định thấy mọi kho nên không cần gán */}
          {selectedRole !== 'ADMIN' && (
            <Form.Item
              name="warehouseIds"
              label="Kho phụ trách"
              tooltip="Người dùng chỉ thao tác được trên các kho được gán. Bắt buộc với vai trò Quản lý kho và Nhân viên kho."
              rules={[{ required: true, message: 'Vui lòng gán ít nhất một kho' }]}
            >
              <Select
                mode="multiple"
                placeholder="Chọn một hoặc nhiều kho"
                options={warehouses.map((warehouse) => ({
                  value: warehouse.id,
                  label: warehouse.name,
                  disabled: !warehouse.active,
                }))}
              />
            </Form.Item>
          )}

          {selectedRole === 'ADMIN' && (
            <Alert
              type="info"
              showIcon
              message="Vai trò Quản trị hệ thống tự động có quyền trên mọi kho"
              style={{ marginBottom: 16 }}
            />
          )}
        </Form>
      </Modal>

      {/* ---------- Hộp thoại đặt lại mật khẩu ---------- */}
      <Modal
        open={resetPasswordUser !== null}
        title={`Đặt lại mật khẩu cho ${resetPasswordUser?.username ?? ''}`}
        onCancel={() => setResetPasswordUser(null)}
        onOk={() => resetForm.submit()}
        confirmLoading={resetPasswordMutation.isPending}
        okText="Đặt lại mật khẩu"
        cancelText="Hủy"
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 12, marginBottom: 16 }}
          message="Người dùng sẽ bị đăng xuất khỏi mọi thiết bị"
          description="Hệ thống bắt buộc người dùng tự đổi mật khẩu khi đăng nhập lần tới."
        />
        <Form
          form={resetForm}
          layout="vertical"
          onFinish={(values: { newPassword: string }) =>
            resetPasswordUser &&
            resetPasswordMutation.mutate({ id: resetPasswordUser.id, newPassword: values.newPassword })
          }
        >
          <Form.Item
            name="newPassword"
            label="Mật khẩu mới"
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu mới' },
              { min: 8, message: 'Mật khẩu phải có ít nhất 8 ký tự' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/,
                message: 'Phải có chữ thường, chữ in hoa và chữ số',
              },
            ]}
          >
            <Input.Password placeholder="Mật khẩu tạm thời" autoComplete="new-password" autoFocus />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
