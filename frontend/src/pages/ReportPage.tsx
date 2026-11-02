import { useState } from 'react';
import { Link } from 'react-router-dom';
import { FileExcelOutlined, FilePdfOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  InputNumber,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import MoneyText from '@/components/MoneyText';
import { categoryApi, reportApi } from '@/api/endpoints';
import { downloadFile, getErrorMessage } from '@/api/client';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { dayjs, formatDate, formatQuantity, API_DATE_FORMAT, firstDayOfMonthForApi, todayForApi } from '@/utils/format';
import type { InventoryReportRow, SlowMovingRow } from '@/types';

const { Text } = Typography;
const { RangePicker } = DatePicker;

/**
 * Trang BÁO CÁO, gồm hai báo cáo chính:
 *
 * 1. **Nhập - xuất - tồn** theo kỳ: tồn đầu kỳ, nhập, xuất, tồn cuối kỳ và giá trị tồn.
 *    Mọi số liệu tính từ sổ giao dịch kho nên luôn khớp với thẻ kho của từng sản phẩm.
 *
 * 2. **Hàng tồn lâu**: hàng còn tồn nhưng không phát sinh xuất trong N ngày (mặc định 90),
 *    bao gồm cả hàng chưa từng xuất - nhóm thường bị bỏ sót khi chỉ xét ngày xuất cuối.
 *
 * Cả hai báo cáo đều xuất được Excel và PDF với tiếng Việt có dấu.
 */
export default function ReportPage() {
  const { message } = App.useApp();
  const { warehouses, currentWarehouseId } = useWarehouse();

  const [activeTab, setActiveTab] = useState<'inventory' | 'slow-moving'>('inventory');

  // Bộ lọc báo cáo nhập - xuất - tồn
  const [fromDate, setFromDate] = useState(firstDayOfMonthForApi());
  const [toDate, setToDate] = useState(todayForApi());
  const [warehouseId, setWarehouseId] = useState<number | undefined>(currentWarehouseId ?? undefined);
  const [categoryId, setCategoryId] = useState<number | undefined>();

  // Bộ lọc báo cáo hàng tồn lâu
  const [slowMovingDays, setSlowMovingDays] = useState(90);

  const { data: categories = [] } = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: categoryApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  const inventoryReport = useQuery({
    queryKey: ['reports', 'inventory', fromDate, toDate, warehouseId, categoryId],
    queryFn: () => reportApi.inventoryReport({ fromDate, toDate, warehouseId, categoryId }),
    enabled: activeTab === 'inventory',
  });

  const slowMovingReport = useQuery({
    queryKey: ['reports', 'slow-moving', warehouseId, slowMovingDays],
    queryFn: () => reportApi.slowMoving({ warehouseId, days: slowMovingDays }),
    enabled: activeTab === 'slow-moving',
  });

  /** Tải file báo cáo. Đường dẫn và tên file khác nhau theo loại báo cáo và định dạng. */
  const handleExport = async (report: 'inventory' | 'slow-moving', format: 'excel' | 'pdf') => {
    try {
      const params = new URLSearchParams();
      if (report === 'inventory') {
        params.set('fromDate', fromDate);
        params.set('toDate', toDate);
        if (categoryId) {
          params.set('categoryId', String(categoryId));
        }
      } else {
        params.set('days', String(slowMovingDays));
      }
      if (warehouseId) {
        params.set('warehouseId', String(warehouseId));
      }

      const fileName =
        report === 'inventory'
          ? `Bao-cao-nhap-xuat-ton.${format === 'excel' ? 'xlsx' : 'pdf'}`
          : `Bao-cao-hang-ton-lau.${format === 'excel' ? 'xlsx' : 'pdf'}`;

      await downloadFile(`/api/reports/${report}/${format}?${params.toString()}`, fileName);
      message.success('Đã tải file báo cáo.');
    } catch (error) {
      message.error(getErrorMessage(error));
    }
  };

  // ---------- Cột báo cáo nhập - xuất - tồn ----------
  const inventoryColumns: ColumnsType<InventoryReportRow> = [
    {
      title: 'Mã SKU',
      dataIndex: 'sku',
      width: 110,
      fixed: 'left',
      render: (sku: string, record) => <Link to={`/products/${record.productId}`}>{sku}</Link>,
    },
    { title: 'Tên sản phẩm', dataIndex: 'productName', width: 260 },
    { title: 'ĐVT', dataIndex: 'unitName', width: 80, align: 'center' },
    { title: 'Loại hàng', dataIndex: 'categoryName', width: 150, responsive: ['lg'] },
    {
      title: 'Tồn đầu kỳ',
      dataIndex: 'openingQuantity',
      width: 120,
      align: 'right',
      render: (value: number) => formatQuantity(value),
    },
    {
      title: 'Nhập trong kỳ',
      dataIndex: 'receivedQuantity',
      width: 130,
      align: 'right',
      render: (value: number) =>
        value > 0 ? <Text style={{ color: '#389e0d' }}>+{formatQuantity(value)}</Text> : '—',
    },
    {
      title: 'Xuất trong kỳ',
      dataIndex: 'issuedQuantity',
      width: 130,
      align: 'right',
      render: (value: number) =>
        value > 0 ? <Text style={{ color: '#cf1322' }}>-{formatQuantity(value)}</Text> : '—',
    },
    {
      title: 'Tồn cuối kỳ',
      dataIndex: 'closingQuantity',
      width: 130,
      align: 'right',
      render: (value: number) => <Text strong>{formatQuantity(value)}</Text>,
    },
    {
      title: 'Giá trị tồn cuối',
      dataIndex: 'closingValue',
      width: 150,
      align: 'right',
      render: (value?: number | null) => <MoneyText value={value} strong />,
    },
  ];

  // ---------- Cột báo cáo hàng tồn lâu ----------
  const slowMovingColumns: ColumnsType<SlowMovingRow> = [
    {
      title: 'Mã SKU',
      dataIndex: 'sku',
      width: 110,
      render: (sku: string, record) => <Link to={`/products/${record.productId}`}>{sku}</Link>,
    },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'unitName', width: 80, align: 'center', responsive: ['md'] },
    { title: 'Kho', dataIndex: 'warehouseName', width: 150, responsive: ['md'] },
    {
      title: 'Tồn hiện tại',
      dataIndex: 'quantity',
      width: 120,
      align: 'right',
      render: (value: number) => <Text strong>{formatQuantity(value)}</Text>,
    },
    {
      title: 'Ngày xuất cuối',
      dataIndex: 'lastIssueDate',
      width: 140,
      align: 'center',
      render: (value?: string | null) =>
        value ? formatDate(value) : <Tag color="red">Chưa từng xuất</Tag>,
    },
    {
      title: 'Số ngày không xuất',
      dataIndex: 'daysSinceLastIssue',
      width: 150,
      align: 'right',
      render: (value?: number | null) =>
        value === null || value === undefined ? (
          <Text type="secondary">—</Text>
        ) : (
          <Text strong style={{ color: value >= 180 ? '#cf1322' : '#d46b08' }}>
            {value} ngày
          </Text>
        ),
    },
    {
      title: 'Giá trị tồn',
      dataIndex: 'stockValue',
      width: 150,
      align: 'right',
      render: (value?: number | null) => <MoneyText value={value} strong />,
    },
  ];

  return (
    <>
      <PageHeader
        title="Báo cáo"
        description="Số liệu được tính từ sổ giao dịch kho nên luôn khớp với thẻ kho của từng sản phẩm."
      />

      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as 'inventory' | 'slow-moving')}
        items={[
          {
            key: 'inventory',
            label: 'Nhập - xuất - tồn',
            children: (
              <>
                <Card size="small" style={{ marginBottom: 12 }}>
                  <Row gutter={[8, 8]} align="middle">
                    <Col xs={24} md={8}>
                      <RangePicker
                        style={{ width: '100%' }}
                        format="DD/MM/YYYY"
                        allowClear={false}
                        value={[dayjs(fromDate), dayjs(toDate)]}
                        onChange={(range) => {
                          if (range?.[0] && range?.[1]) {
                            setFromDate(range[0].format(API_DATE_FORMAT));
                            setToDate(range[1].format(API_DATE_FORMAT));
                          }
                        }}
                        // Giới hạn 1 năm, khớp kiểm tra của backend
                        disabledDate={(current) => current > dayjs().endOf('day')}
                      />
                    </Col>
                    <Col xs={12} md={5}>
                      <Select
                        placeholder="Tất cả kho"
                        allowClear
                        style={{ width: '100%' }}
                        value={warehouseId}
                        onChange={setWarehouseId}
                        options={warehouses.map((warehouse) => ({
                          value: warehouse.id,
                          label: warehouse.name,
                        }))}
                      />
                    </Col>
                    <Col xs={12} md={5}>
                      <Select
                        placeholder="Tất cả loại hàng"
                        allowClear
                        style={{ width: '100%' }}
                        value={categoryId}
                        onChange={setCategoryId}
                        showSearch
                        optionFilterProp="label"
                        options={categories.map((category) => ({
                          value: category.id,
                          label: category.name,
                        }))}
                      />
                    </Col>
                    <Col xs={24} md={6}>
                      <Space wrap>
                        <Button
                          icon={<FileExcelOutlined />}
                          onClick={() => void handleExport('inventory', 'excel')}
                          disabled={!inventoryReport.data}
                        >
                          Excel
                        </Button>
                        <Button
                          icon={<FilePdfOutlined />}
                          onClick={() => void handleExport('inventory', 'pdf')}
                          disabled={!inventoryReport.data}
                        >
                          PDF
                        </Button>
                      </Space>
                    </Col>
                  </Row>
                </Card>

                {inventoryReport.isError ? (
                  <Alert
                    type="error"
                    showIcon
                    message="Không tải được báo cáo"
                    description={getErrorMessage(inventoryReport.error)}
                  />
                ) : (
                  <>
                    {inventoryReport.data && (
                      <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
                        <Col xs={12} md={6}>
                          <Card size="small">
                            <Statistic
                              title="Tồn đầu kỳ"
                              value={formatQuantity(inventoryReport.data.totalOpeningQuantity)}
                            />
                          </Card>
                        </Col>
                        <Col xs={12} md={6}>
                          <Card size="small">
                            <Statistic
                              title="Nhập trong kỳ"
                              value={formatQuantity(inventoryReport.data.totalReceivedQuantity)}
                              valueStyle={{ color: '#389e0d' }}
                            />
                          </Card>
                        </Col>
                        <Col xs={12} md={6}>
                          <Card size="small">
                            <Statistic
                              title="Xuất trong kỳ"
                              value={formatQuantity(inventoryReport.data.totalIssuedQuantity)}
                              valueStyle={{ color: '#cf1322' }}
                            />
                          </Card>
                        </Col>
                        <Col xs={12} md={6}>
                          <Card size="small">
                            <Statistic
                              title="Tồn cuối kỳ"
                              value={formatQuantity(inventoryReport.data.totalClosingQuantity)}
                              valueStyle={{ color: '#1677ff' }}
                            />
                          </Card>
                        </Col>
                      </Row>
                    )}

                    <Card
                      size="small"
                      title={
                        inventoryReport.data
                          ? `${inventoryReport.data.warehouseName} · kỳ ${formatDate(inventoryReport.data.fromDate)} - ${formatDate(inventoryReport.data.toDate)}`
                          : 'Báo cáo nhập - xuất - tồn'
                      }
                    >
                      <Table<InventoryReportRow>
                        rowKey="productId"
                        columns={inventoryColumns}
                        dataSource={inventoryReport.data?.rows ?? []}
                        loading={inventoryReport.isFetching}
                        size="small"
                        scroll={{ x: 'max-content' }}
                        locale={{
                          emptyText: 'Không có giao dịch kho nào trong kỳ báo cáo đã chọn.',
                        }}
                        pagination={{
                          pageSize: 50,
                          showSizeChanger: true,
                          pageSizeOptions: ['20', '50', '100', '200'],
                          showTotal: (total) => `Tổng ${total} sản phẩm`,
                        }}
                        summary={() =>
                          inventoryReport.data && inventoryReport.data.rows.length > 0 ? (
                            <Table.Summary fixed>
                              <Table.Summary.Row>
                                <Table.Summary.Cell index={0} colSpan={4}>
                                  <Text strong>TỔNG CỘNG</Text>
                                </Table.Summary.Cell>
                                <Table.Summary.Cell index={4} align="right">
                                  <Text strong>
                                    {formatQuantity(inventoryReport.data.totalOpeningQuantity)}
                                  </Text>
                                </Table.Summary.Cell>
                                <Table.Summary.Cell index={5} align="right">
                                  <Text strong style={{ color: '#389e0d' }}>
                                    {formatQuantity(inventoryReport.data.totalReceivedQuantity)}
                                  </Text>
                                </Table.Summary.Cell>
                                <Table.Summary.Cell index={6} align="right">
                                  <Text strong style={{ color: '#cf1322' }}>
                                    {formatQuantity(inventoryReport.data.totalIssuedQuantity)}
                                  </Text>
                                </Table.Summary.Cell>
                                <Table.Summary.Cell index={7} align="right">
                                  <Text strong>
                                    {formatQuantity(inventoryReport.data.totalClosingQuantity)}
                                  </Text>
                                </Table.Summary.Cell>
                                <Table.Summary.Cell index={8} align="right">
                                  <MoneyText value={inventoryReport.data.totalClosingValue} strong />
                                </Table.Summary.Cell>
                              </Table.Summary.Row>
                            </Table.Summary>
                          ) : null
                        }
                      />
                    </Card>
                  </>
                )}
              </>
            ),
          },
          {
            key: 'slow-moving',
            label: 'Hàng tồn lâu',
            children: (
              <>
                <Card size="small" style={{ marginBottom: 12 }}>
                  <Row gutter={[8, 8]} align="middle">
                    <Col xs={24} md={6}>
                      <Space>
                        <Text>Không xuất quá</Text>
                        <InputNumber
                          min={30}
                          max={730}
                          step={30}
                          value={slowMovingDays}
                          onChange={(value) => setSlowMovingDays(value ?? 90)}
                          style={{ width: 90 }}
                        />
                        <Text>ngày</Text>
                      </Space>
                    </Col>
                    <Col xs={12} md={6}>
                      <Select
                        placeholder="Tất cả kho"
                        allowClear
                        style={{ width: '100%' }}
                        value={warehouseId}
                        onChange={setWarehouseId}
                        options={warehouses.map((warehouse) => ({
                          value: warehouse.id,
                          label: warehouse.name,
                        }))}
                      />
                    </Col>
                    <Col xs={12} md={6}>
                      <Button
                        icon={<SearchOutlined />}
                        onClick={() => void slowMovingReport.refetch()}
                        loading={slowMovingReport.isFetching}
                      >
                        Xem báo cáo
                      </Button>
                    </Col>
                    <Col xs={24} md={6}>
                      <Space wrap>
                        <Button
                          icon={<FileExcelOutlined />}
                          onClick={() => void handleExport('slow-moving', 'excel')}
                          disabled={!slowMovingReport.data}
                        >
                          Excel
                        </Button>
                        <Button
                          icon={<FilePdfOutlined />}
                          onClick={() => void handleExport('slow-moving', 'pdf')}
                          disabled={!slowMovingReport.data}
                        >
                          PDF
                        </Button>
                      </Space>
                    </Col>
                  </Row>
                </Card>

                {slowMovingReport.data && slowMovingReport.data.rows.length > 0 && (
                  <Alert
                    type="warning"
                    showIcon
                    style={{ marginBottom: 12 }}
                    message={`${slowMovingReport.data.rows.length} mặt hàng không phát sinh xuất trong ${slowMovingReport.data.thresholdDays} ngày`}
                    description={
                      slowMovingReport.data.totalValue !== null &&
                      slowMovingReport.data.totalValue !== undefined ? (
                        <Text>
                          Tổng giá trị hàng tồn lâu: <MoneyText value={slowMovingReport.data.totalValue} strong />{' '}
                          ₫. Đây là phần vốn đang bị giữ trong kho, nên xem xét giảm giá hoặc ngừng nhập thêm.
                        </Text>
                      ) : (
                        'Nên xem xét giảm giá hoặc ngừng nhập thêm các mặt hàng này.'
                      )
                    }
                  />
                )}

                <Card size="small" title="Báo cáo hàng tồn lâu">
                  <Table<SlowMovingRow>
                    rowKey={(record) => `${record.productId}-${record.warehouseId}`}
                    columns={slowMovingColumns}
                    dataSource={slowMovingReport.data?.rows ?? []}
                    loading={slowMovingReport.isFetching}
                    size="small"
                    scroll={{ x: 'max-content' }}
                    rowClassName={(record) =>
                      record.daysSinceLastIssue === null || record.daysSinceLastIssue === undefined
                        ? 'row-danger'
                        : ''
                    }
                    locale={{
                      emptyText: `Không có mặt hàng nào tồn quá ${slowMovingDays} ngày mà không xuất.`,
                    }}
                    pagination={{ pageSize: 50, showSizeChanger: true, pageSizeOptions: ['20', '50', '100'] }}
                  />
                </Card>
              </>
            ),
          },
        ]}
      />
    </>
  );
}
