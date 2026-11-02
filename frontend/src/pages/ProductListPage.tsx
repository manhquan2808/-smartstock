import { Link, useNavigate } from 'react-router-dom';
import {
  BarcodeOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { App, Button, Card, Col, Image, Input, Popconfirm, Row, Select, Space, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import MoneyText from '@/components/MoneyText';
import { categoryApi, productApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';
import { useUrlFilters, toNumberOrUndefined, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { formatQuantity } from '@/utils/format';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/utils/constants';
import type { ProductResponse, RecordStatus } from '@/types';

/**
 * Trang DANH SÁCH SẢN PHẨM.
 *
 * Tìm kiếm theo mã SKU, tên hoặc mã vạch; lọc theo loại hàng và trạng thái kinh doanh.
 * Bộ lọc được lưu trên URL nên bấm F5 hay gửi link cho người khác đều giữ nguyên kết quả.
 */
export default function ProductListPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { canEditCatalog } = useAuth();

  const { filters, setFilter, page, size, setPagination } = useUrlFilters(
    { keyword: '', categoryId: '', status: '' },
    DEFAULT_PAGE_SIZE,
  );

  const { data: categories = [] } = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: categoryApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  const { data, isFetching } = useQuery({
    queryKey: ['products', 'search', filters, page, size],
    queryFn: () =>
      productApi.search({
        keyword: toStringOrUndefined(filters.keyword),
        categoryId: toNumberOrUndefined(filters.categoryId),
        status: (toStringOrUndefined(filters.status) as RecordStatus | undefined) ?? undefined,
        page,
        size,
      }),
  });

  const deleteMutation = useMutation({
    mutationFn: productApi.remove,
    onSuccess: () => {
      message.success('Đã xóa sản phẩm.');
      void queryClient.invalidateQueries({ queryKey: ['products'] });
    },
    // Backend chặn xóa sản phẩm còn tồn và trả thông báo rõ ràng; ta hiển thị lại nguyên văn
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: RecordStatus }) => productApi.changeStatus(id, status),
    onSuccess: (product) => {
      message.success(`Đã chuyển "${product.name}" sang ${product.statusName}.`);
      void queryClient.invalidateQueries({ queryKey: ['products'] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const columns: ColumnsType<ProductResponse> = [
    {
      title: 'Ảnh',
      dataIndex: 'imageUrl',
      width: 60,
      responsive: ['md'],
      render: (url?: string) =>
        url ? (
          <Image src={url} width={38} height={38} style={{ objectFit: 'cover', borderRadius: 4 }} />
        ) : (
          <div
            style={{
              width: 38,
              height: 38,
              borderRadius: 4,
              background: 'rgba(0,0,0,0.04)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 15,
              color: '#bfbfbf',
            }}
          >
            <BarcodeOutlined />
          </div>
        ),
    },
    {
      title: 'Mã SKU',
      dataIndex: 'sku',
      width: 110,
      render: (sku: string, record) => <Link to={`/products/${record.id}`}>{sku}</Link>,
    },
    {
      title: 'Tên sản phẩm',
      dataIndex: 'name',
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <Link to={`/products/${record.id}`}>{name}</Link>
          {record.barcode && (
            <Space size={4}>
              <BarcodeOutlined style={{ fontSize: 11, color: '#8c8c8c' }} />
              <span style={{ fontSize: 11, color: '#8c8c8c' }}>{record.barcode}</span>
            </Space>
          )}
        </Space>
      ),
    },
    { title: 'Loại hàng', dataIndex: 'categoryName', width: 150, responsive: ['lg'] },
    { title: 'ĐVT', dataIndex: 'unitName', width: 80, responsive: ['md'] },
    {
      title: 'Giá nhập',
      dataIndex: 'purchasePrice',
      width: 110,
      align: 'right',
      responsive: ['md'],
      render: (value: number) => <MoneyText value={value} />,
    },
    {
      title: 'Giá bán',
      dataIndex: 'salePrice',
      width: 110,
      align: 'right',
      render: (value: number) => <MoneyText value={value} />,
    },
    {
      title: 'Tồn tối thiểu',
      dataIndex: 'minStock',
      width: 110,
      align: 'right',
      responsive: ['lg'],
      render: (value: number) => formatQuantity(value),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (status: RecordStatus, record) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{record.statusName}</Tag>
      ),
    },
  ];

  if (canEditCatalog) {
    columns.push({
      title: 'Thao tác',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Tooltip title="Sửa">
            <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/products/${record.id}/edit`)} />
          </Tooltip>

          <Tooltip title={record.status === 'ACTIVE' ? 'Ngừng kinh doanh' : 'Kinh doanh lại'}>
            <Popconfirm
              title={record.status === 'ACTIVE' ? 'Ngừng kinh doanh sản phẩm?' : 'Cho kinh doanh lại?'}
              description={
                record.status === 'ACTIVE'
                  ? 'Sản phẩm sẽ không chọn được khi lập phiếu mới, nhưng vẫn giữ nguyên tồn kho và lịch sử.'
                  : 'Sản phẩm sẽ chọn được khi lập phiếu mới.'
              }
              okText="Đồng ý"
              cancelText="Hủy"
              onConfirm={() =>
                statusMutation.mutate({
                  id: record.id,
                  status: record.status === 'ACTIVE' ? 'DISCONTINUED' : 'ACTIVE',
                })
              }
            >
              <Button size="small" icon={<StopOutlined />} />
            </Popconfirm>
          </Tooltip>

          <Tooltip title="Xóa">
            <Popconfirm
              title="Xóa sản phẩm?"
              description="Chỉ xóa được khi sản phẩm không còn tồn ở kho nào."
              okText="Xóa"
              cancelText="Hủy"
              okButtonProps={{ danger: true }}
              onConfirm={() => deleteMutation.mutate(record.id)}
            >
              <Button size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    });
  }

  return (
    <>
      <PageHeader
        title="Sản phẩm"
        description="Danh mục mặt hàng. Mã vạch EAN-13 dùng cho chức năng quét mã khi lập phiếu."
        extra={
          <Space wrap>
            <Link to="/barcode-labels">
              <Button icon={<BarcodeOutlined />}>In tem mã vạch</Button>
            </Link>
            {canEditCatalog && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/products/new')}>
                Thêm sản phẩm
              </Button>
            )}
          </Space>
        }
      />

      <Card size="small">
        <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
          <Col xs={24} md={10}>
            <Input
              placeholder="Tìm theo mã SKU, tên hoặc mã vạch..."
              prefix={<SearchOutlined />}
              allowClear
              defaultValue={filters.keyword}
              onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
              onClear={() => setFilter('keyword', '')}
            />
          </Col>
          <Col xs={12} md={7}>
            <Select
              placeholder="Tất cả loại hàng"
              allowClear
              style={{ width: '100%' }}
              value={filters.categoryId || undefined}
              onChange={(value) => setFilter('categoryId', value)}
              options={categories.map((category) => ({
                value: String(category.id),
                label: category.name,
              }))}
              showSearch
              optionFilterProp="label"
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
                { value: 'ACTIVE', label: 'Đang kinh doanh' },
                { value: 'DISCONTINUED', label: 'Ngừng kinh doanh' },
              ]}
            />
          </Col>
        </Row>

        <Table<ProductResponse>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: 'Không tìm thấy sản phẩm nào khớp điều kiện lọc.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} sản phẩm`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
        />
      </Card>
    </>
  );
}
