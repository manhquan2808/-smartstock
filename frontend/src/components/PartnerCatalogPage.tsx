import { useState } from 'react';
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { App, Button, Card, Col, Form, Input, Modal, Popconfirm, Row, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from './PageHeader';
import { getErrorMessage } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';
import { useUrlFilters, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/utils/constants';
import type { PageResponse, PartnerResponse } from '@/types';
import type { SavePartnerRequest } from '@/api/endpoints';

/**
 * Trang danh mục ĐỐI TÁC, dùng chung cho NHÀ CUNG CẤP và KHÁCH HÀNG.
 *
 * Hai danh mục có cùng tập thông tin (mã, tên, điện thoại, email, địa chỉ, mã số thuế,
 * ghi chú) nên dùng một component chung. Hai trang gọi vào đây chỉ khác tiêu đề và API.
 */

interface PartnerCatalogPageProps {
  title: string;
  description: string;
  entityLabel: string;
  queryKey: string;
  api: {
    search: (params: { keyword?: string; page?: number; size?: number }) => Promise<PageResponse<PartnerResponse>>;
    create: (data: SavePartnerRequest) => Promise<PartnerResponse>;
    update: (id: number, data: SavePartnerRequest) => Promise<PartnerResponse>;
    remove: (id: number) => Promise<void>;
  };
}

export default function PartnerCatalogPage({
  title,
  description,
  entityLabel,
  queryKey,
  api,
}: PartnerCatalogPageProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { canEditCatalog } = useAuth();
  const [form] = Form.useForm<SavePartnerRequest>();

  const [editingRecord, setEditingRecord] = useState<PartnerResponse | null>(null);
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
    mutationFn: (values: SavePartnerRequest) =>
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

  const openEditModal = (record: PartnerResponse) => {
    setEditingRecord(record);
    form.setFieldsValue(record);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingRecord(null);
    form.resetFields();
  };

  const columns: ColumnsType<PartnerResponse> = [
    { title: 'Mã', dataIndex: 'code', width: 120, render: (code: string) => <strong>{code}</strong> },
    { title: 'Tên', dataIndex: 'name', width: 260 },
    { title: 'Điện thoại', dataIndex: 'phone', width: 130, render: (value?: string) => value ?? '—' },
    { title: 'Email', dataIndex: 'email', responsive: ['lg'], render: (value?: string) => value ?? '—' },
    { title: 'Địa chỉ', dataIndex: 'address', responsive: ['xl'], render: (value?: string) => value ?? '—' },
    { title: 'Mã số thuế', dataIndex: 'taxCode', width: 130, responsive: ['xl'], render: (value?: string) => value ?? '—' },
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
            description="Bản ghi được xóa mềm nên các phiếu cũ vẫn hiển thị đúng tên đối tác."
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
          placeholder="Tìm theo mã, tên hoặc số điện thoại..."
          prefix={<SearchOutlined />}
          allowClear
          defaultValue={filters.keyword}
          onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
          onClear={() => setFilter('keyword', '')}
          style={{ maxWidth: 400, marginBottom: 12 }}
        />

        <Table<PartnerResponse>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: 'Chưa có dữ liệu.' }}
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
        width={620}
        destroyOnHidden
      >
        <Form<SavePartnerRequest>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          <Row gutter={12}>
            <Col xs={24} sm={8}>
              <Form.Item
                name="code"
                label="Mã"
                rules={[
                  { required: true, message: 'Vui lòng nhập mã' },
                  { max: 20, message: 'Mã tối đa 20 ký tự' },
                  { pattern: /^[A-Za-z0-9_-]+$/, message: 'Mã chỉ gồm chữ không dấu, số và _ -' },
                ]}
              >
                <Input placeholder="NCC001" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={16}>
              <Form.Item
                name="name"
                label="Tên"
                rules={[
                  { required: true, message: 'Vui lòng nhập tên' },
                  { max: 200, message: 'Tên tối đa 200 ký tự' },
                ]}
              >
                <Input placeholder="Tên đầy đủ" />
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
              <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không đúng định dạng' }]}>
                <Input placeholder="lienhe@congty.vn" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="address" label="Địa chỉ" rules={[{ max: 255, message: 'Địa chỉ tối đa 255 ký tự' }]}>
            <Input placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành" />
          </Form.Item>

          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item name="taxCode" label="Mã số thuế" rules={[{ max: 30 }]}>
                <Input placeholder="0123456789" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="note" label="Ghi chú" rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}>
            <Input.TextArea rows={2} placeholder="Ghi chú thêm (không bắt buộc)" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
