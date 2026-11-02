import { useState } from 'react';
import { EditOutlined, PlusOutlined, SearchOutlined, StopOutlined } from '@ant-design/icons';
import {
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
  Switch,
  Table,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { userApi, warehouseApi, type SaveWarehouseRequest } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';
import { useUrlFilters, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/utils/constants';
import type { WarehouseResponse } from '@/types';

/**
 * Trang QUẢN LÝ KHO / CHI NHÁNH.
 *
 * Chỉ ADMIN thêm/sửa được; MANAGER và STAFF chỉ xem các kho mình được gán.
 *
 * Kho không bị xóa cứng mà chuyển sang "Ngừng hoạt động", vì các phiếu và giao dịch cũ
 * vẫn tham chiếu tới kho. Hệ thống còn chặn ngừng hoạt động khi kho đang còn tồn kho.
 */
export default function WarehousePage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { isAdmin } = useAuth();
  const [form] = Form.useForm<SaveWarehouseRequest>();

  const [editingRecord, setEditingRecord] = useState<WarehouseResponse | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const { filters, setFilter, page, size, setPagination } = useUrlFilters({ keyword: '' }, DEFAULT_PAGE_SIZE);

  const { data, isFetching } = useQuery({
    queryKey: ['warehouses', 'search', filters.keyword, page, size],
    queryFn: () => warehouseApi.search({ keyword: toStringOrUndefined(filters.keyword), page, size }),
  });

  /** Danh sách người có thể làm quản lý kho (chỉ ADMIN mới gọi được API người dùng). */
  const { data: managerCandidates } = useQuery({
    queryKey: ['users', 'managers'],
    queryFn: () => userApi.search({ role: 'MANAGER', status: 'ACTIVE', size: 100 }),
    enabled: isAdmin && isModalOpen,
  });

  const saveMutation = useMutation({
    mutationFn: (values: SaveWarehouseRequest) =>
      editingRecord ? warehouseApi.update(editingRecord.id, values) : warehouseApi.create(values),
    onSuccess: () => {
      message.success(editingRecord ? 'Đã cập nhật kho.' : 'Đã thêm kho mới.');
      closeModal();
      void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const deactivateMutation = useMutation({
    mutationFn: warehouseApi.deactivate,
    onSuccess: () => {
      message.success('Đã ngừng hoạt động kho.');
      void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const openCreateModal = () => {
    setEditingRecord(null);
    form.resetFields();
    form.setFieldsValue({ active: true });
    setIsModalOpen(true);
  };

  const openEditModal = (record: WarehouseResponse) => {
    setEditingRecord(record);
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      address: record.address,
      phone: record.phone,
      managerId: record.managerId ?? null,
      active: record.active,
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingRecord(null);
    form.resetFields();
  };

  const columns: ColumnsType<WarehouseResponse> = [
    { title: 'Mã kho', dataIndex: 'code', width: 110, render: (code: string) => <strong>{code}</strong> },
    { title: 'Tên kho', dataIndex: 'name', width: 220 },
    { title: 'Địa chỉ', dataIndex: 'address', render: (value?: string) => value ?? '—' },
    { title: 'Điện thoại', dataIndex: 'phone', width: 130, responsive: ['md'], render: (value?: string) => value ?? '—' },
    {
      title: 'Người quản lý',
      dataIndex: 'managerName',
      width: 180,
      responsive: ['lg'],
      render: (value?: string) => value ?? '—',
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 140,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'default'}>{active ? 'Đang hoạt động' : 'Ngừng hoạt động'}</Tag>
      ),
    },
  ];

  if (isAdmin) {
    columns.push({
      title: 'Thao tác',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditModal(record)}>
            Sửa
          </Button>
          {record.active && (
            <Popconfirm
              title="Ngừng hoạt động kho?"
              description="Kho sẽ không chọn được khi lập phiếu mới. Bị chặn nếu kho còn tồn kho."
              okText="Ngừng hoạt động"
              cancelText="Hủy"
              okButtonProps={{ danger: true }}
              onConfirm={() => deactivateMutation.mutate(record.id)}
            >
              <Button size="small" danger icon={<StopOutlined />} />
            </Popconfirm>
          )}
        </Space>
      ),
    });
  }

  return (
    <>
      <PageHeader
        title="Kho / Chi nhánh"
        description={
          isAdmin
            ? 'Mỗi kho có số tồn và giá vốn riêng cho cùng một sản phẩm.'
            : 'Danh sách các kho bạn được phân công làm việc.'
        }
        extra={
          isAdmin && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              Thêm kho
            </Button>
          )
        }
      />

      <Card size="small">
        <Input
          placeholder="Tìm theo mã hoặc tên kho..."
          prefix={<SearchOutlined />}
          allowClear
          defaultValue={filters.keyword}
          onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
          onClear={() => setFilter('keyword', '')}
          style={{ maxWidth: 360, marginBottom: 12 }}
        />

        <Table<WarehouseResponse>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          rowClassName={(record) => (record.active ? '' : 'row-warning')}
          locale={{ emptyText: 'Chưa có kho nào.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} kho`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
        />
      </Card>

      <Modal
        open={isModalOpen}
        title={editingRecord ? `Sửa kho ${editingRecord.code}` : 'Thêm kho mới'}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="Lưu"
        cancelText="Hủy"
        width={580}
        destroyOnHidden
      >
        <Form<SaveWarehouseRequest>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          <Row gutter={12}>
            <Col xs={24} sm={8}>
              <Form.Item
                name="code"
                label="Mã kho"
                rules={[
                  { required: true, message: 'Vui lòng nhập mã kho' },
                  { max: 20, message: 'Mã tối đa 20 ký tự' },
                  { pattern: /^[A-Za-z0-9_-]+$/, message: 'Mã chỉ gồm chữ không dấu, số và _ -' },
                ]}
              >
                <Input placeholder="KHN" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={16}>
              <Form.Item
                name="name"
                label="Tên kho"
                rules={[
                  { required: true, message: 'Vui lòng nhập tên kho' },
                  { max: 150, message: 'Tên tối đa 150 ký tự' },
                ]}
              >
                <Input placeholder="Kho Hà Nội" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="address" label="Địa chỉ" rules={[{ max: 255 }]}>
            <Input placeholder="Số nhà, đường, quận/huyện, tỉnh/thành" />
          </Form.Item>

          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="phone"
                label="Điện thoại"
                rules={[{ pattern: /^$|^[0-9+\s.()-]{8,20}$/, message: 'Số điện thoại không đúng định dạng' }]}
              >
                <Input placeholder="0243822100" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item
                name="managerId"
                label="Người quản lý kho"
                tooltip="Người này nhận thông báo và email khi có phiếu chờ duyệt tại kho. Phải là tài khoản vai trò Quản lý kho hoặc Quản trị."
              >
                <Select
                  placeholder="Chưa chỉ định"
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={(managerCandidates?.content ?? []).map((user) => ({
                    value: user.id,
                    label: `${user.fullName} (${user.username})`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="active" label="Đang hoạt động" valuePropName="checked">
            <Switch checkedChildren="Có" unCheckedChildren="Không" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
