import { Link } from 'react-router-dom';
import {
  AlertOutlined,
  AppstoreOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  ExportOutlined,
  ImportOutlined,
} from '@ant-design/icons';
import { Alert, Card, Col, Empty, Row, Skeleton, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import PageHeader from '@/components/PageHeader';
import { reportApi } from '@/api/endpoints';
import { useAuth } from '@/contexts/AuthContext';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { useThemeMode } from '@/contexts/ThemeContext';
import { formatCurrency, formatNumber, formatQuantity } from '@/utils/format';
import type { DashboardResponse } from '@/types';

const { Text } = Typography;

/**
 * Trang TỔNG QUAN (dashboard).
 *
 * Số liệu thay đổi theo kho đang chọn ở thanh tiêu đề.
 *
 * Phân quyền: vai trò STAFF nhận "dashboard rút gọn" - backend không trả về tổng giá trị
 * tồn nên thẻ chỉ số đó tự ẩn. Giao diện không tự quyết định việc ẩn/hiện số liệu tiền;
 * nó chỉ hiển thị những gì backend cho phép xem.
 */
export default function DashboardPage() {
  const { currentWarehouseId } = useWarehouse();
  const { canViewCost, user } = useAuth();
  const { isDark } = useThemeMode();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['dashboard', currentWarehouseId],
    queryFn: () => reportApi.dashboard(currentWarehouseId ?? undefined),
    // Dashboard là trang người dùng mở lại nhiều lần trong ngày: làm mới mỗi 2 phút
    refetchInterval: 2 * 60 * 1000,
  });

  const chartGridColor = isDark ? '#303030' : '#f0f0f0';
  const chartTextColor = isDark ? '#a6a6a6' : '#595959';

  const lowStockColumns: ColumnsType<DashboardResponse['lowStockItems'][number]> = [
    {
      title: 'Sản phẩm',
      dataIndex: 'productName',
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <Link to={`/products/${record.productId}`}>{name}</Link>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.sku}
          </Text>
        </Space>
      ),
    },
    { title: 'Kho', dataIndex: 'warehouseName', width: 150, responsive: ['md'] },
    {
      title: 'Tồn / Tối thiểu',
      key: 'stock',
      width: 150,
      align: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Text strong style={{ color: record.quantity <= 0 ? '#cf1322' : '#d46b08' }}>
            {formatQuantity(record.quantity)}
          </Text>
          <Text type="secondary">/ {formatQuantity(record.minStock)}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.unitName}
          </Text>
        </Space>
      ),
    },
  ];

  const topProductColumns: ColumnsType<DashboardResponse['topIssuedProducts'][number]> = [
    {
      title: '#',
      key: 'rank',
      width: 46,
      render: (_, __, index) => <Tag color={index < 3 ? 'blue' : 'default'}>{index + 1}</Tag>,
    },
    {
      title: 'Sản phẩm',
      dataIndex: 'productName',
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <Link to={`/products/${record.productId}`}>{name}</Link>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.sku}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Đã xuất',
      dataIndex: 'quantity',
      width: 130,
      align: 'right',
      render: (value: number, record) => (
        <Text strong>
          {formatQuantity(value)} {record.unitName}
        </Text>
      ),
    },
  ];

  if (isError) {
    return (
      <>
        <PageHeader title="Tổng quan" />
        <Alert
          type="error"
          showIcon
          message="Không tải được số liệu tổng quan"
          description={(error as Error)?.message}
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Tổng quan"
        description={
          data
            ? `Số liệu của ${data.warehouseName}. Xin chào ${user?.fullName ?? ''}.`
            : 'Đang tải số liệu...'
        }
      />

      {/* ---------- Thẻ chỉ số ---------- */}
      <Row gutter={[12, 12]}>
        <Col xs={12} sm={12} md={8} lg={canViewCost ? 6 : 8}>
          <Card size="small">
            <Skeleton loading={isLoading} active paragraph={false}>
              <Statistic
                title="Tổng số mặt hàng"
                value={data?.totalProducts ?? 0}
                prefix={<AppstoreOutlined style={{ color: '#1677ff' }} />}
                formatter={(value) => formatNumber(Number(value))}
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                {formatNumber(data?.productsInStock ?? 0)} mặt hàng đang còn tồn
              </Text>
            </Skeleton>
          </Card>
        </Col>

        {/* Thẻ giá trị tồn chỉ hiện với ADMIN/MANAGER */}
        {canViewCost && (
          <Col xs={12} sm={12} md={8} lg={6}>
            <Card size="small">
              <Skeleton loading={isLoading} active paragraph={false}>
                <Statistic
                  title="Tổng giá trị tồn"
                  value={data?.totalStockValue ?? 0}
                  prefix={<DollarOutlined style={{ color: '#52c41a' }} />}
                  formatter={(value) => formatCurrency(Number(value))}
                />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Tính theo giá vốn bình quân
                </Text>
              </Skeleton>
            </Card>
          </Col>
        )}

        <Col xs={12} sm={12} md={8} lg={canViewCost ? 6 : 8}>
          <Card size="small">
            <Skeleton loading={isLoading} active paragraph={false}>
              <Space direction="vertical" size={2} style={{ width: '100%' }}>
                <Space size={16} wrap>
                  <Statistic
                    title="Phiếu nhập tháng này"
                    value={data?.receiptsThisMonth ?? 0}
                    prefix={<ImportOutlined style={{ color: '#1677ff' }} />}
                    valueStyle={{ fontSize: 20 }}
                  />
                  <Statistic
                    title="Phiếu xuất"
                    value={data?.issuesThisMonth ?? 0}
                    prefix={<ExportOutlined style={{ color: '#fa8c16' }} />}
                    valueStyle={{ fontSize: 20 }}
                  />
                </Space>
              </Space>
            </Skeleton>
          </Card>
        </Col>

        <Col xs={12} sm={12} md={8} lg={canViewCost ? 6 : 8}>
          <Card size="small">
            <Skeleton loading={isLoading} active paragraph={false}>
              <Statistic
                title="Phiếu chờ duyệt"
                value={data?.pendingDocuments ?? 0}
                prefix={<ClockCircleOutlined style={{ color: '#faad14' }} />}
                valueStyle={{ color: (data?.pendingDocuments ?? 0) > 0 ? '#d46b08' : undefined }}
              />
              <Space size={8}>
                <Link to="/receipts?status=PENDING" style={{ fontSize: 12 }}>
                  Phiếu nhập
                </Link>
                <Link to="/issues?status=PENDING" style={{ fontSize: 12 }}>
                  Phiếu xuất
                </Link>
              </Space>
            </Skeleton>
          </Card>
        </Col>
      </Row>

      {/* ---------- Cảnh báo tồn thấp ---------- */}
      {(data?.lowStockCount ?? 0) > 0 && (
        <Alert
          type="warning"
          showIcon
          icon={<AlertOutlined />}
          style={{ marginTop: 12 }}
          message={`Có ${data?.lowStockCount} mặt hàng đang ở mức tồn thấp`}
          description={
            <Space size={12} wrap>
              <Link to="/inventory?stockState=LOW">Xem danh sách tồn thấp</Link>
              {canViewCost && <Link to="/replenishment">Xem gợi ý nhập hàng</Link>}
            </Space>
          }
        />
      )}

      {/* ---------- Biểu đồ ---------- */}
      <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
        <Col xs={24} lg={14}>
          <Card
            size="small"
            title="Nhập - xuất 30 ngày gần nhất"
            extra={
              <Text type="secondary" style={{ fontSize: 12 }}>
                Đơn vị: số lượng
              </Text>
            }
          >
            <Skeleton loading={isLoading} active>
              {data && data.dailyFlow.length > 0 ? (
                <div style={{ width: '100%', height: 280 }}>
                  <ResponsiveContainer>
                    <LineChart data={data.dailyFlow} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke={chartGridColor} />
                      <XAxis
                        dataKey="date"
                        tick={{ fontSize: 11, fill: chartTextColor }}
                        // Chỉ hiện ngày/tháng cho gọn trục hoành
                        tickFormatter={(value: string) => value.slice(5).replace('-', '/')}
                      />
                      <YAxis tick={{ fontSize: 11, fill: chartTextColor }} />
                      <ChartTooltip
                        formatter={(value: number) => formatQuantity(value)}
                        labelFormatter={(label: string) => `Ngày ${label.split('-').reverse().join('/')}`}
                      />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Line
                        type="monotone"
                        dataKey="receivedQuantity"
                        name="Nhập"
                        stroke="#1677ff"
                        strokeWidth={2}
                        dot={false}
                      />
                      <Line
                        type="monotone"
                        dataKey="issuedQuantity"
                        name="Xuất"
                        stroke="#fa8c16"
                        strokeWidth={2}
                        dot={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="Chưa có giao dịch nào trong 30 ngày qua"
                />
              )}
            </Skeleton>
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card size="small" title="Top 10 sản phẩm xuất nhiều nhất">
            <Skeleton loading={isLoading} active>
              {data && data.topIssuedProducts.length > 0 ? (
                <div style={{ width: '100%', height: 280 }}>
                  <ResponsiveContainer>
                    <BarChart
                      data={data.topIssuedProducts.slice(0, 10)}
                      layout="vertical"
                      margin={{ top: 4, right: 12, left: 4, bottom: 4 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" stroke={chartGridColor} />
                      <XAxis type="number" tick={{ fontSize: 11, fill: chartTextColor }} />
                      <YAxis
                        type="category"
                        dataKey="sku"
                        width={70}
                        tick={{ fontSize: 10, fill: chartTextColor }}
                      />
                      <ChartTooltip
                        formatter={(value: number) => formatQuantity(value)}
                        labelFormatter={(sku: string) =>
                          data.topIssuedProducts.find((item) => item.sku === sku)?.productName ?? sku
                        }
                      />
                      <Bar dataKey="quantity" name="Số lượng xuất" fill="#1677ff" radius={[0, 4, 4, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có dữ liệu xuất kho" />
              )}
            </Skeleton>
          </Card>
        </Col>
      </Row>

      {/* ---------- Bảng chi tiết ---------- */}
      <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
        <Col xs={24} lg={12}>
          <Card
            size="small"
            title="Hàng sắp hết"
            extra={<Link to="/inventory?stockState=LOW">Xem tất cả</Link>}
          >
            <Table
              rowKey={(record) => `${record.productId}-${record.warehouseName}`}
              columns={lowStockColumns}
              dataSource={data?.lowStockItems ?? []}
              loading={isLoading}
              size="small"
              pagination={false}
              locale={{ emptyText: 'Không có mặt hàng nào dưới mức tồn tối thiểu' }}
            />
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card size="small" title="Xếp hạng sản phẩm xuất nhiều (30 ngày)">
            <Table
              rowKey="productId"
              columns={topProductColumns}
              dataSource={data?.topIssuedProducts ?? []}
              loading={isLoading}
              size="small"
              pagination={false}
              locale={{ emptyText: 'Chưa có dữ liệu' }}
            />
          </Card>
        </Col>
      </Row>
    </>
  );
}
