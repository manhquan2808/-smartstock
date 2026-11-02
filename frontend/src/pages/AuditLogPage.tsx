import { useState } from 'react';
import { ClearOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Card, Col, DatePicker, Descriptions, Input, Modal, Row, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { auditApi } from '@/api/endpoints';
import { useUrlFilters, toStringOrUndefined } from '@/hooks/useUrlFilters';
import { dayjs, formatDateTime, prettyJson, API_DATE_FORMAT } from '@/utils/format';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/utils/constants';
import type { AuditLogResponse } from '@/types';

const { Text, Paragraph } = Typography;
const { RangePicker } = DatePicker;

/** Nhãn và màu cho từng loại hành động trong nhật ký. */
const ACTION_OPTIONS = [
  { value: 'LOGIN_SUCCESS', label: 'Đăng nhập thành công', color: 'green' },
  { value: 'LOGIN_FAILED', label: 'Đăng nhập thất bại', color: 'red' },
  { value: 'LOGOUT', label: 'Đăng xuất', color: 'default' },
  { value: 'PASSWORD_RESET_REQUEST', label: 'Yêu cầu đặt lại mật khẩu', color: 'orange' },
  { value: 'PASSWORD_RESET', label: 'Đặt lại mật khẩu', color: 'orange' },
  { value: 'PASSWORD_CHANGE', label: 'Đổi mật khẩu', color: 'orange' },
  { value: 'CREATE', label: 'Thêm mới', color: 'blue' },
  { value: 'UPDATE', label: 'Cập nhật', color: 'cyan' },
  { value: 'DELETE', label: 'Xóa', color: 'red' },
  { value: 'SUBMIT', label: 'Gửi duyệt', color: 'geekblue' },
  { value: 'APPROVE', label: 'Duyệt', color: 'green' },
  { value: 'REJECT', label: 'Từ chối', color: 'volcano' },
  { value: 'CANCEL', label: 'Hủy', color: 'orange' },
  { value: 'LOCK_USER', label: 'Khóa tài khoản', color: 'red' },
  { value: 'UNLOCK_USER', label: 'Mở khóa tài khoản', color: 'green' },
  { value: 'RESET_USER_PASSWORD', label: 'Đặt lại mật khẩu người dùng', color: 'orange' },
];

const ACTION_COLOR = new Map(ACTION_OPTIONS.map((option) => [option.value, option.color]));

/**
 * Trang NHẬT KÝ HOẠT ĐỘNG - chỉ ADMIN xem được.
 *
 * Ghi lại mọi thao tác quan trọng: đăng nhập (kể cả thất bại), lập/gửi duyệt/duyệt/từ
 * chối/hủy phiếu, thêm sửa xóa danh mục, khóa mở tài khoản. Mỗi dòng có người thực hiện,
 * thời điểm, địa chỉ IP và dữ liệu trước/sau khi thay đổi.
 *
 * Đây là công cụ để truy vết khi có nghi vấn: ai đã sửa số liệu này, lúc nào, từ máy nào.
 */
export default function AuditLogPage() {
  const [selectedLog, setSelectedLog] = useState<AuditLogResponse | null>(null);

  const { filters, setFilter, setFilters, resetFilters, page, size, setPagination } = useUrlFilters(
    { keyword: '', action: '', fromDate: '', toDate: '' },
    DEFAULT_PAGE_SIZE,
  );

  const { data, isFetching } = useQuery({
    queryKey: ['audit-logs', filters, page, size],
    queryFn: () =>
      auditApi.search({
        keyword: toStringOrUndefined(filters.keyword),
        action: toStringOrUndefined(filters.action),
        fromDate: toStringOrUndefined(filters.fromDate),
        toDate: toStringOrUndefined(filters.toDate),
        page,
        size,
      }),
  });

  const columns: ColumnsType<AuditLogResponse> = [
    {
      title: 'Thời điểm',
      dataIndex: 'createdAt',
      width: 160,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Người thực hiện',
      dataIndex: 'username',
      width: 150,
      render: (username?: string) => username ?? <Text type="secondary">(không rõ)</Text>,
    },
    {
      title: 'Hành động',
      dataIndex: 'action',
      width: 180,
      render: (action: string, record) => (
        <Tag color={ACTION_COLOR.get(action) ?? 'default'}>{record.actionName}</Tag>
      ),
    },
    {
      title: 'Đối tượng',
      key: 'entity',
      width: 150,
      responsive: ['lg'],
      render: (_, record) =>
        record.entityType ? (
          <Text style={{ fontSize: 12 }}>
            {record.entityType}
            {record.entityId ? ` #${record.entityId}` : ''}
          </Text>
        ) : (
          '—'
        ),
    },
    { title: 'Diễn giải', dataIndex: 'description', render: (value?: string) => value ?? '—' },
    {
      title: 'Địa chỉ IP',
      dataIndex: 'ipAddress',
      width: 130,
      responsive: ['xl'],
      render: (value?: string) => <Text style={{ fontSize: 12 }}>{value ?? '—'}</Text>,
    },
    {
      title: '',
      key: 'actions',
      width: 60,
      fixed: 'right',
      render: (_, record) =>
        record.beforeJson || record.afterJson ? (
          <Button size="small" icon={<EyeOutlined />} onClick={() => setSelectedLog(record)} />
        ) : null,
    },
  ];

  const hasActiveFilter = Object.values(filters).some((value) => value !== '');

  return (
    <>
      <PageHeader
        title="Nhật ký hoạt động"
        description="Truy vết mọi thao tác quan trọng trong hệ thống. Bản ghi chỉ thêm mới, không sửa và không xóa."
      />

      <Card size="small">
        <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
          <Col xs={24} md={7}>
            <Input
              placeholder="Tìm theo người thực hiện hoặc diễn giải..."
              prefix={<SearchOutlined />}
              allowClear
              defaultValue={filters.keyword}
              onPressEnter={(event) => setFilter('keyword', (event.target as HTMLInputElement).value)}
              onClear={() => setFilter('keyword', '')}
            />
          </Col>
          <Col xs={12} md={6}>
            <Select
              placeholder="Tất cả hành động"
              allowClear
              style={{ width: '100%' }}
              value={filters.action || undefined}
              onChange={(value) => setFilter('action', value)}
              showSearch
              optionFilterProp="label"
              options={ACTION_OPTIONS.map((option) => ({ value: option.value, label: option.label }))}
            />
          </Col>
          <Col xs={24} md={9}>
            <RangePicker
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
          {hasActiveFilter && (
            <Col xs={24} md={2}>
              <Button icon={<ClearOutlined />} onClick={resetFilters} block>
                Xóa
              </Button>
            </Col>
          )}
        </Row>

        <Table<AuditLogResponse>
          rowKey="id"
          columns={columns}
          dataSource={data?.content ?? []}
          loading={isFetching}
          size="small"
          scroll={{ x: 'max-content' }}
          rowClassName={(record) => (record.action === 'LOGIN_FAILED' ? 'row-warning' : '')}
          locale={{ emptyText: 'Không có bản ghi nhật ký nào khớp điều kiện lọc.' }}
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

      {/* Hộp thoại xem dữ liệu trước / sau khi thay đổi */}
      <Modal
        open={selectedLog !== null}
        title="Chi tiết thay đổi"
        onCancel={() => setSelectedLog(null)}
        footer={null}
        width={840}
      >
        {selectedLog && (
          <Space direction="vertical" size={12} style={{ width: '100%', marginTop: 12 }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="Thời điểm">{formatDateTime(selectedLog.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="Người thực hiện">{selectedLog.username ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Hành động">{selectedLog.actionName}</Descriptions.Item>
              <Descriptions.Item label="Địa chỉ IP">{selectedLog.ipAddress ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Đối tượng" span={2}>
                {selectedLog.entityType}
                {selectedLog.entityId ? ` #${selectedLog.entityId}` : ''}
              </Descriptions.Item>
              <Descriptions.Item label="Diễn giải" span={2}>
                {selectedLog.description ?? '—'}
              </Descriptions.Item>
            </Descriptions>

            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Card size="small" title="Dữ liệu TRƯỚC khi thay đổi">
                  {selectedLog.beforeJson ? (
                    <Paragraph
                      style={{
                        margin: 0,
                        fontSize: 11,
                        fontFamily: 'monospace',
                        whiteSpace: 'pre-wrap',
                        maxHeight: 340,
                        overflow: 'auto',
                      }}
                    >
                      {prettyJson(selectedLog.beforeJson)}
                    </Paragraph>
                  ) : (
                    <Text type="secondary">Không có (bản ghi mới được tạo)</Text>
                  )}
                </Card>
              </Col>
              <Col xs={24} md={12}>
                <Card size="small" title="Dữ liệu SAU khi thay đổi">
                  {selectedLog.afterJson ? (
                    <Paragraph
                      style={{
                        margin: 0,
                        fontSize: 11,
                        fontFamily: 'monospace',
                        whiteSpace: 'pre-wrap',
                        maxHeight: 340,
                        overflow: 'auto',
                      }}
                    >
                      {prettyJson(selectedLog.afterJson)}
                    </Paragraph>
                  ) : (
                    <Text type="secondary">Không có (bản ghi đã bị xóa)</Text>
                  )}
                </Card>
              </Col>
            </Row>
          </Space>
        )}
      </Modal>
    </>
  );
}
