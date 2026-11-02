import { Link, useNavigate } from 'react-router-dom';
import { ClearOutlined, EyeOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Card, Col, DatePicker, Input, Row, Select, Table, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import PageHeader from './PageHeader';
import MoneyText from './MoneyText';
import StatusTag from './StatusTag';
import { useAuth } from '@/contexts/AuthContext';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { useUrlFilters, toNumberOrUndefined, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { dayjs, formatDate, formatQuantity, API_DATE_FORMAT } from '@/utils/format';
import {
  DEFAULT_PAGE_SIZE,
  DOCUMENT_STATUS_OPTIONS,
  PAGE_SIZE_OPTIONS,
} from '@/utils/constants';
import type { DocumentStatus, DocumentSummary, PageResponse } from '@/types';

const { Text } = Typography;
const { RangePicker } = DatePicker;

/**
 * Trang DANH SÁCH PHIẾU, dùng chung cho phiếu nhập và phiếu xuất.
 *
 * Hai loại phiếu có cùng bộ lọc (kho, loại phiếu, trạng thái, khoảng ngày, người lập, mã
 * phiếu) và cùng cách hiển thị, nên gom vào một component. Hai trang gọi vào đây chỉ khác
 * tiêu đề, đường dẫn và danh sách loại phiếu.
 *
 * Bộ lọc nằm trên URL nên chia sẻ được đường dẫn kèm điều kiện lọc - ví dụ
 * `/receipts?status=PENDING` là danh sách phiếu đang chờ duyệt, dùng ngay trên dashboard.
 */

interface DocumentListPageProps {
  title: string;
  description: string;
  /** Đường dẫn gốc, ví dụ "receipts" hoặc "issues". */
  basePath: string;
  queryKey: string;
  /** Nhãn của ô chọn loại phiếu, ví dụ "Loại phiếu nhập". */
  typeLabel: string;
  typeOptions: Array<{ value: string; label: string }>;
  /** Tên tham số loại phiếu khi gọi API (receiptType / issueType). */
  typeParamName: 'receiptType' | 'issueType';
  search: (params: Record<string, unknown>) => Promise<PageResponse<DocumentSummary>>;
  /** Nhãn cột tiền, khác nhau giữa phiếu nhập (tổng tiền) và phiếu xuất (giá trị xuất). */
  amountColumnTitle: string;
}

export default function DocumentListPage({
  title,
  description,
  basePath,
  queryKey,
  typeLabel,
  typeOptions,
  typeParamName,
  search,
  amountColumnTitle,
}: DocumentListPageProps) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { currentWarehouseId, warehouses, hasMultipleWarehouses } = useWarehouse();

  const { filters, setFilter, setFilters, resetFilters, page, size, setPagination } = useUrlFilters(
    { keyword: '', status: '', docType: '', fromDate: '', toDate: '', warehouseId: '', onlyMine: '' },
    DEFAULT_PAGE_SIZE,
  );

  // Ưu tiên kho chọn trong bộ lọc; nếu không có thì theo kho đang làm việc ở thanh tiêu đề
  const effectiveWarehouseId = toNumberOrUndefined(filters.warehouseId) ?? currentWarehouseId ?? undefined;

  const { data, isFetching } = useQuery({
    queryKey: [queryKey, 'search', filters, effectiveWarehouseId, page, size],
    queryFn: () =>
      search({
        warehouseId: effectiveWarehouseId,
        [typeParamName]: toStringOrUndefined(filters.docType),
        status: toStringOrUndefined(filters.status) as DocumentStatus | undefined,
        fromDate: toStringOrUndefined(filters.fromDate),
        toDate: toStringOrUndefined(filters.toDate),
        // Lọc "chỉ phiếu của tôi" - hữu ích với nhân viên lập nhiều phiếu
        createdBy: filters.onlyMine === '1' ? user?.id : undefined,
        keyword: toStringOrUndefined(filters.keyword),
        page,
        size,
      }),
  });

  const columns: ColumnsType<DocumentSummary> = [
    {
      title: 'Mã phiếu',
      dataIndex: 'code',
      width: 160,
      render: (code: string, record) => (
        <Link to={`/${basePath}/${record.id}`} style={{ fontWeight: 600 }}>
          {code}
        </Link>
      ),
    },
    {
      title: 'Ngày chứng từ',
      dataIndex: 'documentDate',
      width: 120,
      render: (value: string) => formatDate(value),
    },
    { title: typeLabel, dataIndex: 'typeName', width: 150, responsive: ['md'] },
    {
      title: 'Kho',
      dataIndex: 'warehouseName',
      width: 150,
      responsive: ['lg'],
    },
    {
      title: 'Đối tác',
      dataIndex: 'partnerName',
      responsive: ['lg'],
      render: (value?: string) => value ?? '—',
    },
    {
      title: 'Số dòng',
      dataIndex: 'itemCount',
      width: 90,
      align: 'center',
      responsive: ['xl'],
    },
    {
      title: 'Tổng SL',
      dataIndex: 'totalQuantity',
      width: 110,
      align: 'right',
      responsive: ['xl'],
      render: (value: number) => formatQuantity(value),
    },
    {
      title: amountColumnTitle,
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right',
      render: (value?: number | null) => <MoneyText value={value} />,
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
        <Tooltip title="Xem chi tiết">
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/${basePath}/${record.id}`)} />
        </Tooltip>
      ),
    },
  ];

  const hasActiveFilter = Object.values(filters).some((value) => value !== '');

  return (
    <>
      <PageHeader
        title={title}
        description={description}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate(`/${basePath}/new`)}>
            Lập phiếu mới
          </Button>
        }
      />

      <Card size="small">
        <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
          <Col xs={24} md={6}>
            <Input
              placeholder="Tìm theo mã phiếu..."
              prefix={<SearchOutlined />}
              allowClear
              defaultValue={filters.keyword}
              onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
              onClear={() => setFilter('keyword', '')}
            />
          </Col>

          <Col xs={12} md={5}>
            <Select
              placeholder={`Tất cả ${typeLabel.toLowerCase()}`}
              allowClear
              style={{ width: '100%' }}
              value={filters.docType || undefined}
              onChange={(value) => setFilter('docType', value)}
              options={typeOptions}
            />
          </Col>

          <Col xs={12} md={5}>
            <Select
              placeholder="Tất cả trạng thái"
              allowClear
              style={{ width: '100%' }}
              value={filters.status || undefined}
              onChange={(value) => setFilter('status', value)}
              options={DOCUMENT_STATUS_OPTIONS}
            />
          </Col>

          <Col xs={24} md={8}>
            <RangePicker
              style={{ width: '100%' }}
              format="DD/MM/YYYY"
              placeholder={['Từ ngày', 'Đến ngày']}
              value={
                filters.fromDate && filters.toDate
                  ? [dayjs(filters.fromDate), dayjs(filters.toDate)]
                  : null
              }
              onChange={(range) =>
                setFilters({
                  fromDate: range?.[0]?.format(API_DATE_FORMAT),
                  toDate: range?.[1]?.format(API_DATE_FORMAT),
                })
              }
            />
          </Col>

          {hasMultipleWarehouses && (
            <Col xs={12} md={5}>
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

          <Col xs={12} md={5}>
            <Select
              style={{ width: '100%' }}
              value={filters.onlyMine || ''}
              onChange={(value) => setFilter('onlyMine', value)}
              options={[
                { value: '', label: 'Mọi người lập' },
                { value: '1', label: 'Chỉ phiếu của tôi' },
              ]}
            />
          </Col>

          {hasActiveFilter && (
            <Col xs={24} md={4}>
              <Button icon={<ClearOutlined />} onClick={resetFilters} block>
                Xóa bộ lọc
              </Button>
            </Col>
          )}
        </Row>

        <Table<DocumentSummary>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          locale={{
            emptyText: hasActiveFilter
              ? 'Không có phiếu nào khớp điều kiện lọc. Thử xóa bộ lọc để xem tất cả.'
              : 'Chưa có phiếu nào. Bấm "Lập phiếu mới" để bắt đầu.',
          }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZE_OPTIONS,
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} phiếu`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
          summary={() =>
            data && data.content.length > 0 ? (
              <Table.Summary fixed>
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={columns.length}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Trang {page + 1}/{data.totalPages} - tổng {data.totalElements} phiếu
                    </Text>
                  </Table.Summary.Cell>
                </Table.Summary.Row>
              </Table.Summary>
            ) : null
          }
        />
      </Card>
    </>
  );
}
