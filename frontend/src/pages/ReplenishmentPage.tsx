import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileExcelOutlined, InfoCircleOutlined, LineChartOutlined, PlusOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts';
import PageHeader from '@/components/PageHeader';
import { receiptApi, reportApi } from '@/api/endpoints';
import { downloadFile, getErrorMessage } from '@/api/client';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { formatCurrency, formatQuantity, todayForApi } from '@/utils/format';
import { FORECAST_WINDOW_OPTIONS, URGENCY_COLOR, URGENCY_LABEL } from '@/utils/constants';
import type { ReplenishmentRow, Urgency } from '@/types';

const { Text, Paragraph } = Typography;

/**
 * Trang GỢI Ý NHẬP HÀNG (dự báo nhu cầu).
 *
 * Công thức được giải thích ngay trên giao diện (tooltip ở từng cột và khối giải thích ở
 * đầu trang) để người trình bày đồ án thuyết minh được cách tính mà không cần mở mã nguồn.
 *
 * Nút "Tạo phiếu nhập từ gợi ý" tạo một phiếu NHÁP với đúng các mặt hàng và số lượng được
 * gợi ý - người dùng xem lại, sửa nếu cần rồi mới gửi duyệt.
 */
export default function ReplenishmentPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { warehouses, currentWarehouseId } = useWarehouse();

  const [warehouseId, setWarehouseId] = useState<number | undefined>(
    currentWarehouseId ?? warehouses[0]?.id,
  );

  /**
   * Chọn kho mặc định khi danh sách kho vừa tải xong.
   *
   * Gợi ý nhập hàng luôn tính cho MỘT kho cụ thể (nhu cầu mỗi kho khác nhau), nên khi
   * người dùng đang để "Tất cả kho" ở thanh tiêu đề, trang này phải tự chọn kho đầu tiên
   * - nếu không, bảng sẽ trống mà người dùng không hiểu vì sao.
   */
  useEffect(() => {
    if (warehouseId === undefined && warehouses.length > 0) {
      setWarehouseId(currentWarehouseId ?? warehouses[0].id);
    }
  }, [warehouseId, warehouses, currentWarehouseId]);
  const [windowDays, setWindowDays] = useState(30);
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [trendProductId, setTrendProductId] = useState<number | null>(null);
  const [isCreatingReceipt, setIsCreatingReceipt] = useState(false);

  const { data, isFetching, isError, error } = useQuery({
    queryKey: ['replenishment', warehouseId, windowDays],
    queryFn: () => reportApi.replenishment({ warehouseId: warehouseId as number, windowDays }),
    enabled: Boolean(warehouseId),
  });

  const { data: trend, isFetching: isLoadingTrend } = useQuery({
    queryKey: ['replenishment', 'trend', trendProductId, warehouseId, windowDays],
    queryFn: () =>
      reportApi.issueTrend({
        productId: trendProductId as number,
        warehouseId: warehouseId as number,
        windowDays,
      }),
    enabled: Boolean(trendProductId && warehouseId),
  });

  /**
   * Tạo phiếu nhập NHÁP từ các dòng được chọn.
   *
   * Cố ý tạo ở trạng thái nháp (không gửi duyệt ngay) vì gợi ý chỉ là ước lượng - người
   * phụ trách cần xem lại số lượng và chọn nhà cung cấp trước khi trình duyệt.
   */
  const handleCreateReceipt = async () => {
    if (!data || selectedRowKeys.length === 0 || !warehouseId) {
      return;
    }

    const selectedRows = data.rows.filter((row) => selectedRowKeys.includes(row.productId));
    const items = selectedRows
      .filter((row) => row.suggestedQuantity > 0)
      .map((row) => ({
        productId: row.productId,
        quantity: row.suggestedQuantity,
        unitPrice: row.purchasePrice,
      }));

    if (items.length === 0) {
      message.warning('Các dòng đã chọn đều không cần nhập thêm.');
      return;
    }

    setIsCreatingReceipt(true);
    try {
      const created = await receiptApi.create(
        {
          warehouseId,
          receiptType: 'OTHER',
          documentDate: todayForApi(),
          // Loại OTHER bắt buộc ghi lý do; ghi rõ nguồn gốc để người duyệt hiểu bối cảnh
          reason: `Tạo từ gợi ý nhập hàng (dựa trên ${windowDays} ngày gần nhất)`,
          note: 'Phiếu nháp sinh tự động từ trang Gợi ý nhập hàng. Vui lòng chọn nhà cung cấp và kiểm tra lại đơn giá.',
          items,
        },
        false,
      );
      message.success(`Đã tạo phiếu nháp ${created.code} với ${items.length} mặt hàng.`);
      navigate(`/receipts/${created.id}/edit`);
    } catch (createError) {
      message.error(getErrorMessage(createError));
    } finally {
      setIsCreatingReceipt(false);
    }
  };

  const handleExportExcel = async () => {
    if (!warehouseId) {
      return;
    }
    try {
      await downloadFile(
        `/api/reports/replenishment/excel?warehouseId=${warehouseId}&windowDays=${windowDays}`,
        'Goi-y-nhap-hang.xlsx',
      );
    } catch (exportError) {
      message.error(getErrorMessage(exportError));
    }
  };

  const columns: ColumnsType<ReplenishmentRow> = [
    {
      title: 'Sản phẩm',
      dataIndex: 'productName',
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.sku} · {record.unitName}
          </Text>
        </Space>
      ),
    },
    {
      title: (
        <Tooltip title="Số tồn hiện tại tại kho đang chọn">
          <Space size={4}>
            Tồn hiện tại <InfoCircleOutlined style={{ fontSize: 11 }} />
          </Space>
        </Tooltip>
      ),
      dataIndex: 'quantityOnHand',
      width: 120,
      align: 'right',
      render: (value: number, record) => (
        <Space direction="vertical" size={0} align="end">
          <Text strong style={{ color: value <= 0 ? '#cf1322' : undefined }}>
            {formatQuantity(value)}
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
      title: (
        <Tooltip title={`Tổng lượng xuất trong ${windowDays} ngày chia cho ${windowDays}`}>
          <Space size={4}>
            Xuất BQ/ngày <InfoCircleOutlined style={{ fontSize: 11 }} />
          </Space>
        </Tooltip>
      ),
      dataIndex: 'averageDailyIssue',
      width: 130,
      align: 'right',
      responsive: ['md'],
      render: (value: number, record) => (
        <Space direction="vertical" size={0} align="end">
          <Text>{formatQuantity(value)}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            tổng {formatQuantity(record.totalIssued)}
          </Text>
        </Space>
      ),
    },
    {
      title: (
        <Tooltip title="Tồn hiện tại chia cho lượng xuất bình quân mỗi ngày. Dấu gạch nghĩa là sản phẩm chưa từng xuất nên không dự báo được.">
          <Space size={4}>
            Số ngày còn đủ <InfoCircleOutlined style={{ fontSize: 11 }} />
          </Space>
        </Tooltip>
      ),
      dataIndex: 'daysOfStockLeft',
      width: 130,
      align: 'right',
      render: (value: number | null | undefined, record) =>
        value === null || value === undefined ? (
          <Text type="secondary">—</Text>
        ) : (
          <Space direction="vertical" size={0} align="end">
            <Text strong style={{ color: value <= (data?.leadTimeDays ?? 7) ? '#cf1322' : undefined }}>
              {formatQuantity(value)} ngày
            </Text>
            <Tag color={URGENCY_COLOR[record.urgency as Urgency]} style={{ fontSize: 10, marginRight: 0 }}>
              {URGENCY_LABEL[record.urgency as Urgency]}
            </Tag>
          </Space>
        ),
    },
    {
      title: (
        <Tooltip
          title={
            data
              ? `Xuất BQ/ngày × (${data.leadTimeDays} ngày chờ hàng + ${data.safetyStockDays} ngày dự trữ) + tồn tối thiểu − tồn hiện tại, làm tròn lên`
              : 'Số lượng nên nhập'
          }
        >
          <Space size={4}>
            SL nên nhập <InfoCircleOutlined style={{ fontSize: 11 }} />
          </Space>
        </Tooltip>
      ),
      dataIndex: 'suggestedQuantity',
      width: 130,
      align: 'right',
      render: (value: number, record) => (
        <Text strong style={{ color: value > 0 ? '#1677ff' : undefined, fontSize: 15 }}>
          {formatQuantity(value)} {record.unitName}
        </Text>
      ),
    },
    {
      title: 'Ước tính tiền',
      dataIndex: 'estimatedCost',
      width: 140,
      align: 'right',
      responsive: ['lg'],
      render: (value: number) => <Text>{formatCurrency(value)}</Text>,
    },
    {
      title: 'Biểu đồ',
      key: 'trend',
      width: 80,
      fixed: 'right',
      render: (_, record) => (
        <Tooltip title="Xem lượng xuất theo ngày">
          <Button
            size="small"
            icon={<LineChartOutlined />}
            onClick={() => setTrendProductId(record.productId)}
          />
        </Tooltip>
      ),
    },
  ];

  const totalEstimatedCost =
    data?.rows
      .filter((row) => selectedRowKeys.includes(row.productId))
      .reduce((sum, row) => sum + row.estimatedCost, 0) ?? 0;

  return (
    <>
      <PageHeader
        title="Gợi ý nhập hàng"
        description="Dự báo nhu cầu nhập dựa trên lịch sử xuất kho, thời gian chờ hàng và số ngày dự trữ an toàn."
        extra={
          <Space wrap>
            <Button icon={<FileExcelOutlined />} onClick={() => void handleExportExcel()} disabled={!data}>
              Xuất Excel
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={selectedRowKeys.length === 0}
              loading={isCreatingReceipt}
              onClick={() => void handleCreateReceipt()}
            >
              Tạo phiếu nhập từ gợi ý ({selectedRowKeys.length})
            </Button>
          </Space>
        }
      />

      {/* Khối giải thích công thức - phục vụ việc trình bày khi bảo vệ đồ án */}
      {data && (
        <Alert
          type="info"
          showIcon
          icon={<InfoCircleOutlined />}
          style={{ marginBottom: 12 }}
          message="Cách hệ thống tính số lượng nên nhập"
          description={
            <Space direction="vertical" size={2}>
              <Paragraph style={{ margin: 0, fontSize: 13 }}>
                <Text code>
                  SL nên nhập = xuất BQ/ngày × ({data.leadTimeDays} ngày chờ hàng + {data.safetyStockDays} ngày
                  dự trữ) + tồn tối thiểu − tồn hiện tại
                </Text>
              </Paragraph>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Trong đó xuất BQ/ngày = tổng lượng xuất trong {data.windowDays} ngày gần nhất chia cho{' '}
                {data.windowDays}. Kết quả được làm tròn lên và không bao giờ âm. Danh sách sắp xếp theo số ngày
                còn đủ hàng tăng dần, nên mặt hàng nguy cấp nhất nằm trên cùng. Hai tham số thời gian chờ hàng và
                số ngày dự trữ sửa được ở trang Cấu hình hệ thống.
              </Text>
            </Space>
          }
        />
      )}

      <Card size="small" style={{ marginBottom: 12 }}>
        <Row gutter={[8, 8]} align="middle">
          <Col xs={24} md={7}>
            <Select
              placeholder="Chọn kho"
              style={{ width: '100%' }}
              value={warehouseId}
              onChange={(value) => {
                setWarehouseId(value);
                setSelectedRowKeys([]);
              }}
              options={warehouses.map((warehouse) => ({ value: warehouse.id, label: warehouse.name }))}
            />
          </Col>
          <Col xs={24} md={7}>
            <Select
              style={{ width: '100%' }}
              value={windowDays}
              onChange={setWindowDays}
              options={FORECAST_WINDOW_OPTIONS}
            />
          </Col>
          {selectedRowKeys.length > 0 && (
            <Col xs={24} md={10}>
              <Space wrap>
                <Text type="secondary">Đã chọn {selectedRowKeys.length} mặt hàng,</Text>
                <Text strong>ước tính {formatCurrency(totalEstimatedCost)}</Text>
                <Button size="small" type="link" onClick={() => setSelectedRowKeys([])}>
                  Bỏ chọn
                </Button>
              </Space>
            </Col>
          )}
        </Row>
      </Card>

      {isError ? (
        <Alert type="error" showIcon message="Không tải được gợi ý" description={(error as Error)?.message} />
      ) : (
        <>
          {data && data.rows.length > 0 && (
            <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Mặt hàng cần nhập" value={data.rows.length} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="Đã hết hàng"
                    value={data.rows.filter((row) => row.urgency === 'OUT_OF_STOCK').length}
                    valueStyle={{ color: '#cf1322' }}
                  />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="Rất cấp thiết"
                    value={data.rows.filter((row) => row.urgency === 'CRITICAL').length}
                    valueStyle={{ color: '#d4380d' }}
                  />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="Tổng tiền dự kiến"
                    value={formatCurrency(data.rows.reduce((sum, row) => sum + row.estimatedCost, 0))}
                  />
                </Card>
              </Col>
            </Row>
          )}

          <Card size="small">
            <Table<ReplenishmentRow>
              rowKey="productId"
              columns={columns}
              dataSource={data?.rows ?? []}
              loading={isFetching}
              size="small"
              scroll={{ x: 'max-content' }}
              rowSelection={{
                selectedRowKeys,
                onChange: (keys) => setSelectedRowKeys(keys as number[]),
                // Mặt hàng không cần nhập thêm thì không cho chọn
                getCheckboxProps: (record) => ({ disabled: record.suggestedQuantity <= 0 }),
              }}
              rowClassName={(record) =>
                record.urgency === 'OUT_OF_STOCK'
                  ? 'row-danger'
                  : record.urgency === 'CRITICAL'
                    ? 'row-warning'
                    : ''
              }
              locale={{
                emptyText:
                  'Không có mặt hàng nào cần nhập thêm. Tồn kho hiện đủ cho nhu cầu dự báo của kỳ tới.',
              }}
              pagination={{ pageSize: 20, showSizeChanger: true, pageSizeOptions: ['20', '50', '100'] }}
            />
          </Card>
        </>
      )}

      {/* Hộp thoại biểu đồ lượng xuất theo ngày */}
      <Modal
        open={trendProductId !== null}
        title={trend ? `Lượng xuất theo ngày - ${trend.productName}` : 'Lượng xuất theo ngày'}
        onCancel={() => setTrendProductId(null)}
        footer={null}
        width={720}
      >
        {isLoadingTrend ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin tip="Đang tải biểu đồ..." />
          </div>
        ) : trend && trend.points.length > 0 ? (
          <>
            <div style={{ width: '100%', height: 300 }}>
              <ResponsiveContainer>
                <BarChart data={trend.points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis
                    dataKey="date"
                    tick={{ fontSize: 11 }}
                    tickFormatter={(value: string) => value.slice(5).replace('-', '/')}
                  />
                  <YAxis tick={{ fontSize: 11 }} />
                  <ChartTooltip
                    formatter={(value: number) => formatQuantity(value)}
                    labelFormatter={(label: string) => `Ngày ${label.split('-').reverse().join('/')}`}
                  />
                  <Bar dataKey="quantity" name="Lượng xuất" fill="#fa8c16" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Biểu đồ giúp kiểm tra xem con số bình quân có đại diện cho nhu cầu thực tế: nếu lượng xuất dồn
              vào vài ngày cao điểm, nên xem thêm xu hướng thay vì chỉ dựa vào bình quân.
            </Text>
          </>
        ) : (
          <Alert
            type="info"
            showIcon
            message="Chưa có dữ liệu xuất kho"
            description={`Sản phẩm này không phát sinh xuất trong ${windowDays} ngày gần nhất.`}
          />
        )}
      </Modal>
    </>
  );
}
