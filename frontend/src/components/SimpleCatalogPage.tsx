import { useState } from 'react';
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from './PageHeader';
import { getErrorMessage } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';
import { useUrlFilters, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { formatDateTime } from '@/utils/format';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/utils/constants';
import type { PageResponse } from '@/types';

/**
 * Trang danh mục đơn giản dùng chung cho LOẠI HÀNG và ĐƠN VỊ TÍNH.
 *
 * Hai danh mục này có cùng cấu trúc (mã, tên, mô tả) và cùng cách thao tác, nên viết một
 * component nhận tham số thay vì lặp lại hai trang gần như giống nhau. Khi cần thêm danh
 * mục tương tự, chỉ việc tạo thêm một file mỏng truyền tham số vào đây.
 *
 * Phân quyền: STAFF chỉ xem; nút Thêm/Sửa/Xóa chỉ hiện với ADMIN và MANAGER.
 */

/** Bản ghi tối thiểu mà một danh mục đơn giản phải có. */
export interface SimpleCatalogRecord {
  id: number;
  code: string;
  name: string;
  description?: string;
  createdAt: string;
}

interface SimpleCatalogPageProps<T extends SimpleCatalogRecord> {
  title: string;
  description: string;
  /** Tên hiển thị của một bản ghi, dùng trong câu thông báo: "Đã thêm loại hàng". */
  entityLabel: string;
  queryKey: string;
  api: {
    search: (params: { keyword?: string; page?: number; size?: number }) => Promise<PageResponse<T>>;
    create: (data: { code: string; name: string; description?: string }) => Promise<T>;
    update: (id: number, data: { code: string; name: string; description?: string }) => Promise<T>;
    remove: (id: number) => Promise<void>;
  };
  /** Độ dài tối đa của tên, khớp với ràng buộc của backend. */
  nameMaxLength?: number;
}

interface CatalogFormValues {
  code: string;
  name: string;
  description?: string;
}

export default function SimpleCatalogPage<T extends SimpleCatalogRecord>({
  title,
  description,
  entityLabel,
  queryKey,
  api,
  nameMaxLength = 150,
}: SimpleCatalogPageProps<T>) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { canEditCatalog } = useAuth();
  const [form] = Form.useForm<CatalogFormValues>();

  const [editingRecord, setEditingRecord] = useState<T | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const { filters, setFilter, page, size, setPagination } = useUrlFilters(
    { keyword: '' },
    DEFAULT_PAGE_SIZE,
  );

  const { data, isFetching } = useQuery({
    queryKey: [queryKey, 'search', filters.keyword, page, size],
    queryFn: () => api.search({ keyword: toStringOrUndefined(filters.keyword), page, size }),
  });

  const saveMutation = useMutation({
    mutationFn: (values: CatalogFormValues) =>
      editingRecord ? api.update(editingRecord.id, values) : api.create(values),
    onSuccess: () => {
      message.success(editingRecord ? `Đã cập nhật ${entityLabel}.` : `Đã thêm ${entityLabel}.`);
      closeModal();
      void queryClient.invalidateQueries({ queryKey: [queryKey] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: api.remove,
    onSuccess: () => {
      message.success(`Đã xóa ${entityLabel}.`);
      void queryClient.invalidateQueries({ queryKey: [queryKey] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const openCreateModal = () => {
    setEditingRecord(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  const openEditModal = (record: T) => {
    setEditingRecord(record);
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      description: record.description,
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingRecord(null);
    form.resetFields();
  };

  const columns: ColumnsType<T> = [
    { title: 'Mã', dataIndex: 'code', width: 130, render: (code: string) => <strong>{code}</strong> },
    { title: 'Tên', dataIndex: 'name' },
    {
      title: 'Mô tả',
      dataIndex: 'description',
      responsive: ['md'],
      render: (value?: string) => value ?? '—',
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      width: 160,
      responsive: ['lg'],
      render: (value: string) => formatDateTime(value),
    },
  ];

  if (canEditCatalog) {
    columns.push({
      title: 'Thao tác',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditModal(record)}>
            Sửa
          </Button>
          <Popconfirm
            title={`Xóa ${entityLabel}?`}
            description="Bản ghi sẽ được xóa mềm và không còn hiển thị khi lập phiếu mới."
            okText="Xóa"
            cancelText="Hủy"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteMutation.mutate(record.id)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    });
  }

  return (
    <>
      <PageHeader
        title={title}
        description={description}
        extra={
          canEditCatalog && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              Thêm mới
            </Button>
          )
        }
      />

      <Card size="small">
        <Input
          placeholder="Tìm theo mã hoặc tên..."
          prefix={<SearchOutlined />}
          allowClear
          defaultValue={filters.keyword}
          // onPressEnter + allowClear: tìm khi nhấn Enter, không gọi API theo từng ký tự
          onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
          onClear={() => setFilter('keyword', '')}
          style={{ maxWidth: 360, marginBottom: 12 }}
        />

        <Table<T>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: 'Chưa có dữ liệu. Bấm "Thêm mới" để tạo bản ghi đầu tiên.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} bản ghi`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
        />
      </Card>

      <Modal
        open={isModalOpen}
        title={editingRecord ? `Sửa ${entityLabel}` : `Thêm ${entityLabel}`}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="Lưu"
        cancelText="Hủy"
        destroyOnHidden
      >
        <Form<CatalogFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="code"
            label="Mã"
            rules={[
              { required: true, message: 'Vui lòng nhập mã' },
              { max: 20, message: 'Mã tối đa 20 ký tự' },
              {
                pattern: /^[A-Za-z0-9_-]+$/,
                message: 'Mã chỉ gồm chữ không dấu, số và các ký tự _ -',
              },
            ]}
          >
            <Input placeholder="Ví dụ: NGK" style={{ textTransform: 'uppercase' }} />
          </Form.Item>

          <Form.Item
            name="name"
            label="Tên"
            rules={[
              { required: true, message: 'Vui lòng nhập tên' },
              { max: nameMaxLength, message: `Tên tối đa ${nameMaxLength} ký tự` },
            ]}
          >
            <Input placeholder="Ví dụ: Nước giải khát" />
          </Form.Item>

          <Form.Item name="description" label="Mô tả" rules={[{ max: 500, message: 'Mô tả tối đa 500 ký tự' }]}>
            <Input.TextArea rows={3} placeholder="Mô tả thêm (không bắt buộc)" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
