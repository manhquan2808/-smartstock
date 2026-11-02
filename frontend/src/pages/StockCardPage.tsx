import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { DownloadOutlined, FileExcelOutlined, FilePdfOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import MoneyText from '@/components/MoneyText';
import { inventoryApi, productApi } from '@/api/endpoints';
import { downloadFile, getErrorMessage } from '@/api/client';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { dayjs, formatDateTime, formatQuantity, API_DATE_FORMAT } from '@/utils/format';
import type { StockCardEntry, TransactionType } from '@/types';

const { Text } = Typography;
const { RangePicker } = DatePicker;

/** Màu thẻ loại giao dịch trên thẻ kho. */
const TRANSACTION_TYPE_COLOR: Record<TransactionType, string> = {
  RECEIPT: 'blue',
  ISSUE: 'orange',
  ADJUSTMENT: 'purple',
  REVERSAL: 'red',
};

/**
 * Trang THẺ KHO - lịch sử biến động của một sản phẩm tại một kho.
 *
 * Đây là màn hình đối chiếu quan trọng nhất khi có nghi vấn về số tồn: mỗi dòng cho biết
 * giao dịch nào, thuộc phiếu nào, và tồn lũy kế sau giao dịch đó. Cột "tồn lũy kế" lấy
 * trực tiếp từ giá trị đã ghi lúc phát sinh giao dịch, nên luôn khớp với tồn thực tế.
 */
export default function StockCardPage() {
  const { message } = App.useApp();
  const [searchParams, setSearchParams] = useSearchParams();
  const { warehouses, currentWarehouseId } = useWarehouse();

  const [productSearchText, setProductSearchText] = useState('');

  const productId = searchParams.get('productId') ? Number(searchParams.get('productId')) : undefined;
  const warehouseId = searchParams.get('warehouseId')
    ? Number(searchParams.get('warehouseId'))
    : (currentWarehouseId ?? warehouses[0]?.id);
  const fromDate = searchParams.get('fromDate') ?? undefined;
  const toDate = searchParams.get('toDate') ?? undefined;

  /** Cập nhật tham số trên URL để chia sẻ được đường dẫn thẻ kho. */
  const updateParam = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
    });
    setSearchParams(next, { replace: true });
  };

  const { data: productOptions = [], isFetching: isSearchingProducts } = useQuery({
    queryKey: ['products', 'lookup', productSearchText, warehouseId],
    queryFn: () => productApi.lookup({ keyword: productSearchText || undefined, warehouseId, limit: 20 }),
    enabled: Boolean(warehouseId),
  });

  /** Tên sản phẩm đang chọn, để hiện trong ô chọn sau khi tải lại trang. */
  const { data: selectedProduct } = useQuery({
    queryKey: ['products', productId],
    queryFn: () => productApi.getById(productId as number),
    enabled: Boolean(productId),
  });

  const { data, isFetching, isError, error } = useQuery({
    queryKey: ['inventory', 'stock-card', productId, warehouseId, fromDate, toDate],
    queryFn: () =>
      inventoryApi.stockCard({
        productId: productId as number,
        warehouseId: warehouseId as number,
        fromDate,
        toDate,
      }),
    enabled: Boolean(productId && warehouseId),
  });

  const handleExport = async (format: 'excel' | 'pdf') => {
    if (!productId || !warehouseId) {
      return;
    }
    try {
      const params = new URLSearchParams({
        productId: String(productId),
        warehouseId: String(warehouseId),
      });
      if (fromDate) {
        params.set('fromDate', fromDate);
      }
      if (toDate) {
        params.set('toDate', toDate);
      }
      await downloadFile(
        `/api/reports/stock-card/${format}?${params.toString()}`,
        `The-kho.${format === 'excel' ? 'xlsx' : 'pdf'}`,
      );
    } catch (exportError) {
      message.error(getErrorMessage(exportError));
    }
  };

  const columns: ColumnsType<StockCardEntry> = [
    {
      title: 'Thời điểm',
      dataIndex: 'transactionDate',
      width: 150,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Mã chứng từ',
      dataIndex: 'refCode',
      width: 160,
      render: (code: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{code}</Text>
          <Tag color={TRANSACTION_TYPE_COLOR[record.type]} style={{ fontSize: 11 }}>
            {record.typeName}
          </Tag>
        </Space>
      ),
    },
    { title: 'Diễn giải', dataIndex: 'description', responsive: ['md'] },
    {
      title: 'Nhập',
      dataIndex: 'quantityIn',
      width: 110,
      align: 'right',
      render: (value?: number | null) =>
        value ? (
          <Text strong style={{ color: '#389e0d' }}>
            +{formatQuantity(value)}
          </Text>
        ) : (
          ''
        ),
    },
    {
      title: 'Xuất',
      dataIndex: 'quantityOut',
      width: 110,
      align: 'right',
      render: (value?: number | null) =>
        value ? (
          <Text strong style={{ color: '#cf1322' }}>
            -{formatQuantity(value)}
          </Text>
        ) : (
          ''
        ),
    },
    {
      title: 'Tồn lũy kế',
      dataIndex: 'balanceAfter',
      width: 120,
      align: 'right',
      render: (value: number) => <Text strong>{formatQuantity(value)}</Text>,
    },
    {
      title: 'Giá vốn',
      dataIndex: 'unitCost',
      width: 120,
      align: 'right',
      responsive: ['lg'],
      render: (value?: number | null) => <MoneyText value={value} />,
    },
  ];

  return (
    <>
      <PageHeader
        title="Thẻ kho"
        description="Lịch sử nhập - xuất - tồn của một sản phẩm tại một kho, dùng để đối chiếu khi có nghi vấn về số tồn."
        extra={
          data && (
            <Space wrap>
              <Button icon={<FileExcelOutlined />} onClick={() => void handleExport('excel')}>
                Xuất Excel
              </Button>
              <Button icon={<FilePdfOutlined />} onClick={() => void handleExport('pdf')}>
                Xuất PDF
              </Button>
            </Space>
          )
        }
      />

      <Card size="small" style={{ marginBottom: 12 }}>
        <Row gutter={[8, 8]}>
          <Col xs={24} md={9}>
            <Select
              placeholder="Chọn sản phẩm (tìm theo mã, tên, mã vạch)"
              style={{ width: '100%' }}
              showSearch
              filterOption={false}
              value={productId}
              notFoundContent={isSearchingProducts ? <Spin size="small" /> : 'Không tìm thấy sản phẩm'}
              onSearch={setProductSearchText}
              onChange={(value) => updateParam({ productId: value ? String(value) : undefined })}
              options={
                // Bảo đảm sản phẩm đang chọn luôn có trong danh sách dù không khớp từ khóa
                selectedProduct && !productOptions.some((item) => item.id === selectedProduct.id)
                  ? [
                      { value: selectedProduct.id, label: `${selectedProduct.sku} - ${selectedProduct.name}` },
                      ...productOptions.map((item) => ({
                        value: item.id,
                        label: `${item.sku} - ${item.name}`,
                      })),
                    ]
                  : productOptions.map((item) => ({ value: item.id, label: `${item.sku} - ${item.name}` }))
              }
            />
          </Col>

          <Col xs={24} md={6}>
            <Select
              placeholder="Chọn kho"
              style={{ width: '100%' }}
              value={warehouseId}
              onChange={(value) => updateParam({ warehouseId: String(value) })}
              options={warehouses.map((warehouse) => ({ value: warehouse.id, label: warehouse.name }))}
            />
          </Col>

          <Col xs={24} md={9}>
            <RangePicker
              style={{ width: '100%' }}
              format="DD/MM/YYYY"
              placeholder={['Từ ngày', 'Đến ngày']}
              value={fromDate && toDate ? [dayjs(fromDate), dayjs(toDate)] : null}
              onChange={(range) =>
                updateParam({
                  fromDate: range?.[0]?.format(API_DATE_FORMAT),
                  toDate: range?.[1]?.format(API_DATE_FORMAT),
                })
              }
            />
          </Col>
        </Row>
      </Card>

      {!productId ? (
        <Card>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Vui lòng chọn sản phẩm và kho để xem thẻ kho"
          />
        </Card>
      ) : isError ? (
        <Alert type="error" showIcon message="Không tải được thẻ kho" description={(error as Error)?.message} />
      ) : (
        <>
          {data && (
            <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="Tồn đầu kỳ"
                    value={formatQuantity(data.openingQuantity)}
                    suffix={data.unitName}
                  />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="Nhập trong kỳ"
                    value={formatQuantity(data.totalIn)}
                    valueStyle={{ color: '#389e0d' }}
                    suffix={data.unitName}
                  />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="Xuất trong kỳ"
                    value={formatQuantity(data.totalOut)}
                    valueStyle={{ color: '#cf1322' }}
                    suffix={data.unitName}
                  />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title="Tồn cuối kỳ"
                    value={formatQuantity(data.closingQuantity)}
                    valueStyle={{ color: '#1677ff', fontWeight: 700 }}
                    suffix={data.unitName}
                  />
                </Card>
              </Col>
            </Row>
          )}

          <Card
            size="small"
            title={
              data
                ? `${data.sku} - ${data.productName} tại ${data.warehouseName}`
                : 'Thẻ kho'
            }
            extra={
              data && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  <DownloadOutlined /> {data.entries.length} giao dịch
                </Text>
              )
            }
          >
            <Table<StockCardEntry>
              rowKey="id"
              columns={columns}
              dataSource={data?.entries ?? []}
              loading={isFetching}
              size="small"
              scroll={{ x: 'max-content' }}
              pagination={{ pageSize: 50, showSizeChanger: true, pageSizeOptions: ['20', '50', '100'] }}
              locale={{ emptyText: 'Sản phẩm chưa có giao dịch nào tại kho này trong khoảng thời gian đã chọn.' }}
              summary={() =>
                data && data.entries.length > 0 ? (
                  <Table.Summary fixed>
                    <Table.Summary.Row>
                      <Table.Summary.Cell index={0} colSpan={3}>
                        <Text strong>TỔNG CỘNG</Text>
                      </Table.Summary.Cell>
                      <Table.Summary.Cell index={3} align="right">
                        <Text strong style={{ color: '#389e0d' }}>
                          +{formatQuantity(data.totalIn)}
                        </Text>
                      </Table.Summary.Cell>
                      <Table.Summary.Cell index={4} align="right">
                        <Text strong style={{ color: '#cf1322' }}>
                          -{formatQuantity(data.totalOut)}
                        </Text>
                      </Table.Summary.Cell>
                      <Table.Summary.Cell index={5} align="right">
                        <Text strong>{formatQuantity(data.closingQuantity)}</Text>
                      </Table.Summary.Cell>
                      <Table.Summary.Cell index={6} />
                    </Table.Summary.Row>
                  </Table.Summary>
                ) : null
              }
            />
          </Card>
        </>
      )}
    </>
  );
}
