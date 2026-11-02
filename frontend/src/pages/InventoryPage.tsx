import { Link } from 'react-router-dom';
import { ClearOutlined, FileTextOutlined, SearchOutlined, WarningOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Col, Input, Row, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import MoneyText from '@/components/MoneyText';
import { categoryApi, inventoryApi } from '@/api/endpoints';
import { useAuth } from '@/contexts/AuthContext';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { useUrlFilters, toNumberOrUndefined, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { formatQuantity } from '@/utils/format';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS, STOCK_STATE_OPTIONS } from '@/utils/constants';
import type { InventoryRow, StockState } from '@/types';

const { Text } = Typography;

/**
 * Trang TỒN KHO.
 *
 * Hiển thị tồn theo từng cặp (sản phẩm × kho). Người dùng chỉ thấy kho mình được gán.
 *
 * Phân quyền: vai trò STAFF không nhận được giá vốn và giá trị tồn từ backend, nên hai
 * cột đó tự ẩn. Việc ẩn cột ở đây chỉ để giao diện gọn - dữ liệu đã không được gửi về.
 */
export default function InventoryPage() {
  const { canViewCost } = useAuth();
  const { currentWarehouseId, warehouses, hasMultipleWarehouses } = useWarehouse();

  const { filters, setFilter, resetFilters, page, size, setPagination } = useUrlFilters(
    { keyword: '', categoryId: '', stockState: 'ALL', warehouseId: '' },
    DEFAULT_PAGE_SIZE,
  );

  const effectiveWarehouseId = toNumberOrUndefined(filters.warehouseId) ?? currentWarehouseId ?? undefined;

  const { data: categories = [] } = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: categoryApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  const { data, isFetching } = useQuery({
    queryKey: ['inventory', 'search', filters, effectiveWarehouseId, page, size],
    queryFn: () =>
      inventoryApi.search({
        warehouseId: effectiveWarehouseId,
        categoryId: toNumberOrUndefined(filters.categoryId),
        keyword: toStringOrUndefined(filters.keyword),
        stockState: (filters.stockState as StockState) || 'ALL',
        page,
        size,
      }),
  });

  const columns: ColumnsType<InventoryRow> = [
    {
      title: 'Mã SKU',
      dataIndex: 'sku',
      width: 110,
      render: (sku: string, record) => <Link to={`/products/${record.productId}`}>{sku}</Link>,
    },
    {
      title: 'Sản phẩm',
      dataIndex: 'productName',
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <Link to={`/products/${record.productId}`}>{name}</Link>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.categoryName}
            {record.barcode ? ` · ${record.barcode}` : ''}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Kho',
      dataIndex: 'warehouseName',
      width: 150,
      responsive: ['md'],
    },
    { title: 'ĐVT', dataIndex: 'unitName', width: 80, align: 'center', responsive: ['lg'] },
    {
      title: 'Tồn kho',
      dataIndex: 'quantity',
      width: 130,
      align: 'right',
      render: (quantity: number, record) => (
        <Space direction="vertical" size={0} align="end">
          <Text
            strong
            style={{ color: record.outOfStock ? '#cf1322' : record.lowStock ? '#d46b08' : undefined }}
          >
            {formatQuantity(quantity)}
          </Text>
          {record.minStock > 0 && (
            <Text type="secondary" style={{ fontSize: 11 }}>
              tối thiểu {formatQuantity(record.minStock)}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Tình trạng',
      key: 'state',
      width: 120,
      render: (_, record) => {
        if (record.outOfStock) {
          return <Tag color="red">Hết hàng</Tag>;
        }
        if (record.lowStock) {
          return (
            <Tag color="orange" icon={<WarningOutlined />}>
              Sắp hết
            </Tag>
          );
        }
        return <Tag color="green">Còn hàng</Tag>;
      },
    },
  ];

  // Chỉ thêm cột giá vốn / giá trị tồn khi người dùng có quyền xem
  if (canViewCost) {
    columns.push(
      {
        title: 'Giá vốn',
        dataIndex: 'avgCost',
        width: 130,
        align: 'right',
        responsive: ['lg'],
        render: (value?: number | null) => <MoneyText value={value} />,
      },
      {
        title: 'Giá trị tồn',
        dataIndex: 'stockValue',
        width: 150,
        align: 'right',
        render: (value?: number | null) => <MoneyText value={value} strong />,
      },
    );
  }

  columns.push({
    title: 'Thẻ kho',
    key: 'stockCard',
    width: 90,
    fixed: 'right',
    render: (_, record) => (
      <Tooltip title="Xem lịch sử biến động">
        <Link to={`/stock-card?productId=${record.productId}&warehouseId=${record.warehouseId}`}>
          <Button size="small" icon={<FileTextOutlined />} />
        </Link>
      </Tooltip>
    ),
  });

  const hasActiveFilter =
    filters.keyword !== '' || filters.categoryId !== '' || filters.stockState !== 'ALL' || filters.warehouseId !== '';

  return (
    <>
      <PageHeader
        title="Tồn kho"
        description="Số tồn luôn bằng tổng các giao dịch trong sổ kho. Bấm biểu tượng thẻ kho để xem lịch sử biến động."
        extra={
          canViewCost && (
            <Link to="/replenishment">
              <Button type="primary">Xem gợi ý nhập hàng</Button>
            </Link>
          )
        }
      />

      {filters.stockState === 'LOW' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="Đang lọc các mặt hàng sắp hết"
          description="Đây là các mặt hàng có tồn bằng hoặc dưới mức tồn tối thiểu đã đặt cho sản phẩm."
        />
      )}

      <Card size="small">
        <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
          <Col xs={24} md={8}>
            <Input
              placeholder="Tìm theo mã SKU, tên hoặc mã vạch..."
              prefix={<SearchOutlined />}
              allowClear
              defaultValue={filters.keyword}
              onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
              onClear={() => setFilter('keyword', '')}
            />
          </Col>

          <Col xs={12} md={5}>
            <Select
              placeholder="Tất cả loại hàng"
              allowClear
              style={{ width: '100%' }}
              value={filters.categoryId || undefined}
              onChange={(value) => setFilter('categoryId', value)}
              showSearch
              optionFilterProp="label"
              options={categories.map((category) => ({ value: String(category.id), label: category.name }))}
            />
          </Col>

          <Col xs={12} md={5}>
            <Select
              style={{ width: '100%' }}
              value={filters.stockState || 'ALL'}
              onChange={(value) => setFilter('stockState', value)}
              options={STOCK_STATE_OPTIONS}
            />
          </Col>

          {hasMultipleWarehouses && (
            <Col xs={12} md={4}>
              <Select
                placeholder="Theo kho đang chọn"
                allowClear
                style={{ width: '100%' }}
                value={filters.warehouseId || undefined}
                onChange={(value) => setFilter('warehouseId', value)}
                options={warehouses.map((warehouse) => ({
                  value: String(warehouse.id),
                  label: warehouse.name,
                }))}
              />
            </Col>
          )}

          {hasActiveFilter && (
            <Col xs={12} md={2}>
              <Button icon={<ClearOutlined />} onClick={resetFilters} block>
                Xóa
              </Button>
            </Col>
          )}
        </Row>

        <Table<InventoryRow>
          rowKey={(record) => `${record.productId}-${record.warehouseId}`}
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          rowClassName={(record) => (record.outOfStock ? 'row-danger' : record.lowStock ? 'row-warning' : '')}
          locale={{ emptyText: 'Không có dòng tồn kho nào khớp điều kiện lọc.' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} dòng tồn kho`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
        />
      </Card>
    </>
  );
}
