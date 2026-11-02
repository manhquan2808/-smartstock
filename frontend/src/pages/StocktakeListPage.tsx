import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { EyeOutlined, PlusOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Table,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import MoneyText from '@/components/MoneyText';
import StatusTag from '@/components/StatusTag';
import { categoryApi, stocktakeApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { useUrlFilters, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { dayjs, formatDate, API_DATE_FORMAT } from '@/utils/format';
import { DEFAULT_PAGE_SIZE, DOCUMENT_STATUS_OPTIONS, PAGE_SIZE_OPTIONS } from '@/utils/constants';
import type { DocumentStatus, StocktakeSummary } from '@/types';

const { Text } = Typography;

interface CreateStocktakeFormValues {
  warehouseId: number;
  documentDate: Dayjs;
  categoryId?: number;
  note?: string;
}

/**
 * Trang DANH SÁCH ĐỢT KIỂM KÊ.
 *
 * Khi tạo đợt mới, hệ thống CHỤP LẠI số tồn hiện tại của mọi sản phẩm trong kho. Vì vậy
 * nên tạo đợt vào thời điểm kho tạm ngừng nhập xuất; nếu có phiếu hoàn tất trong lúc kiểm
 * kê, trang chi tiết sẽ cảnh báo rõ.
 */
export default function StocktakeListPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { warehouses, currentWarehouseId } = useWarehouse();
  const [form] = Form.useForm<CreateStocktakeFormValues>();
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  const { filters, setFilter, setFilters, page, size, setPagination } = useUrlFilters(
    { status: '', fromDate: '', toDate: '' },
    DEFAULT_PAGE_SIZE,
  );

  const { data: categories = [] } = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: categoryApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  const { data, isFetching } = useQuery({
    queryKey: ['stocktakes', 'search', filters, currentWarehouseId, page, size],
    queryFn: () =>
      stocktakeApi.search({
        warehouseId: currentWarehouseId ?? undefined,
        status: toStringOrUndefined(filters.status) as DocumentStatus | undefined,
        fromDate: toStringOrUndefined(filters.fromDate),
        toDate: toStringOrUndefined(filters.toDate),
        page,
        size,
      }),
  });

  const createMutation = useMutation({
    mutationFn: (values: CreateStocktakeFormValues) =>
      stocktakeApi.create({
        warehouseId: values.warehouseId,
        documentDate: values.documentDate.format(API_DATE_FORMAT),
        categoryId: values.categoryId ?? null,
        note: values.note,
      }),
    onSuccess: (created) => {
      message.success(`Đã tạo đợt kiểm kê ${created.code} với ${created.totalItems} mặt hàng.`);
      setIsCreateOpen(false);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['stocktakes'] });
      // Chuyển sang trang chi tiết để nhân viên bắt đầu nhập số đếm
      navigate(`/stocktakes/${created.id}`);
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const columns: ColumnsType<StocktakeSummary> = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 160,
      render: (code: string, record) => (
        <Link to={`/stocktakes/${record.id}`} style={{ fontWeight: 600 }}>
          {code}
        </Link>
      ),
    },
    {
      title: 'Ngày kiểm kê',
      dataIndex: 'documentDate',
      width: 130,
      render: (value: string) => formatDate(value),
    },
    { title: 'Kho', dataIndex: 'warehouseName', width: 160, responsive: ['md'] },
    { title: 'Phạm vi', dataIndex: 'categoryName', width: 160, responsive: ['lg'] },
    {
      title: 'Tiến độ đếm',
      key: 'progress',
      width: 180,
      render: (_, record) => {
        const percent = record.totalItems > 0 ? Math.round((record.countedItems / record.totalItems) * 100) : 0;
        return (
          <Space direction="vertical" size={0} style={{ width: '100%' }}>
            <Progress
              percent={percent}
              size="small"
              status={percent === 100 ? 'success' : 'active'}
              format={() => `${record.countedItems}/${record.totalItems}`}
            />
            {record.differenceItems > 0 && (
              <Text type="warning" style={{ fontSize: 11 }}>
                {record.differenceItems} mặt hàng lệch
              </Text>
            )}
          </Space>
        );
      },
    },
    {
      title: 'Giá trị chênh lệch',
      dataIndex: 'totalDifferenceValue',
      width: 160,
      align: 'right',
      render: (value?: number | null) =>
        value === null || value === undefined ? (
          <Text type="secondary">—</Text>
        ) : (
          <Text strong style={{ color: value < 0 ? '#cf1322' : value > 0 ? '#389e0d' : undefined }}>
            <MoneyText value={value} />
          </Text>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 120,
      render: (status: DocumentStatus, record) => <StatusTag status={status} label={record.statusName} />,
    },
    {
      title: 'Người lập',
      dataIndex: 'createdByName',
      width: 150,
      responsive: ['xl'],
      render: (value?: string) => value ?? '—',
    },
    {
      title: '',
      key: 'actions',
      width: 60,
      fixed: 'right',
      render: (_, record) => (
        <Tooltip title="Xem và nhập số đếm">
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/stocktakes/${record.id}`)} />
        </Tooltip>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Kiểm kê kho"
        description="So sánh số tồn hệ thống với số đếm thực tế. Khi duyệt, tồn kho được điều chỉnh về đúng số thực tế."
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.setFieldsValue({
                warehouseId: currentWarehouseId ?? warehouses[0]?.id,
                documentDate: dayjs(),
              });
              setIsCreateOpen(true);
            }}
          >
            Tạo đợt kiểm kê
          </Button>
        }
      />

      <Card size="small">
        <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
          <Col xs={12} md={6}>
            <Select
              placeholder="Tất cả trạng thái"
              allowClear
              style={{ width: '100%' }}
              value={filters.status || undefined}
              onChange={(value) => setFilter('status', value)}
              options={DOCUMENT_STATUS_OPTIONS}
            />
          </Col>
          <Col xs={24} md={10}>
            <DatePicker.RangePicker
              style={{ width: '100%' }}
              format="DD/MM/YYYY"
              placeholder={['Từ ngày', 'Đến ngày']}
              value={filters.fromDate && filters.toDate ? [dayjs(filters.fromDate), dayjs(filters.toDate)] : null}
              onChange={(range) =>
                setFilters({
                  fromDate: range?.[0]?.format(API_DATE_FORMAT),
                  toDate: range?.[1]?.format(API_DATE_FORMAT),
                })
              }
            />
          </Col>
        </Row>

        <Table<StocktakeSummary>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: 'Chưa có đợt kiểm kê nào. Bấm "Tạo đợt kiểm kê" để bắt đầu.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} đợt kiểm kê`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
        />
      </Card>

      <Modal
        open={isCreateOpen}
        title="Tạo đợt kiểm kê"
        onCancel={() => setIsCreateOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending}
        okText="Tạo đợt kiểm kê"
        cancelText="Hủy"
        width={560}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12, marginBottom: 16 }}
          message="Hệ thống sẽ chụp lại số tồn hiện tại"
          description="Số tồn hệ thống được ghi lại ngay khi tạo đợt. Nếu có phiếu nhập/xuất hoàn tất trong lúc kiểm kê, hệ thống sẽ cảnh báo vì số liệu có thể lệch."
        />

        <Form<CreateStocktakeFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => createMutation.mutate(values)}
        >
          <Form.Item
            name="warehouseId"
            label="Kho kiểm kê"
            rules={[{ required: true, message: 'Vui lòng chọn kho' }]}
          >
            <Select
              placeholder="Chọn kho"
              options={warehouses.map((warehouse) => ({ value: warehouse.id, label: warehouse.name }))}
            />
          </Form.Item>

          <Form.Item
            name="documentDate"
            label="Ngày kiểm kê"
            rules={[{ required: true, message: 'Vui lòng chọn ngày kiểm kê' }]}
          >
            <DatePicker
              style={{ width: '100%' }}
              format="DD/MM/YYYY"
              disabledDate={(current) => current && current > dayjs().endOf('day')}
            />
          </Form.Item>

          <Form.Item
            name="categoryId"
            label="Phạm vi kiểm kê"
            tooltip="Để trống nghĩa là kiểm kê toàn bộ sản phẩm đang có trong kho. Chọn loại hàng nếu chỉ kiểm kê một nhóm."
          >
            <Select
              placeholder="Toàn bộ sản phẩm trong kho"
              allowClear
              showSearch
              optionFilterProp="label"
              options={categories.map((category) => ({ value: category.id, label: category.name }))}
            />
          </Form.Item>

          <Form.Item name="note" label="Ghi chú">
            <Input.TextArea rows={2} placeholder="Ví dụ: Kiểm kê định kỳ cuối tháng" maxLength={1000} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
