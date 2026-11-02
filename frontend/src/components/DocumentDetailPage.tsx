import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PrinterOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Input,
  Modal,
  Row,
  Space,
  Steps,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from './PageHeader';
import MoneyText from './MoneyText';
import StatusTag from './StatusTag';
import { getErrorMessage, getStockShortages, openPdfInNewTab } from '@/api/client';
import { formatCurrency, formatDate, formatDateTime, formatQuantity } from '@/utils/format';
import type { DocumentDetail, DocumentItem } from '@/types';
import LoadingBlock from './LoadingBlock';

const { Text, Title } = Typography;
const { TextArea } = Input;

/**
 * Trang CHI TIẾT PHIẾU, dùng chung cho phiếu nhập và phiếu xuất.
 *
 * Các nút hành động được bật/tắt theo ba cờ do BACKEND trả về (`canEdit`, `canApprove`,
 * `canCancel`). Cách này quan trọng: giao diện không tự suy luận quyền theo vai trò, mà
 * dùng đúng kết luận của backend - nơi biết đầy đủ cả vai trò, kho được gán và trạng thái
 * phiếu. Nhờ vậy giao diện không bao giờ hiện nút mà bấm vào sẽ bị từ chối.
 */

interface DocumentDetailPageProps {
  /** 'receipt' = phiếu nhập, 'issue' = phiếu xuất. */
  mode: 'receipt' | 'issue';
  basePath: string;
  queryKey: string;
  typeLabel: string;
  fetchDocument: (id: number) => Promise<DocumentDetail>;
  submitDocument: (id: number) => Promise<DocumentDetail>;
  approveDocument: (id: number) => Promise<DocumentDetail>;
  rejectDocument: (id: number, reason: string) => Promise<DocumentDetail>;
  cancelDocument: (id: number, reason: string) => Promise<DocumentDetail>;
  deleteDocument: (id: number) => Promise<void>;
}

/** Loại hộp thoại nhập lý do đang mở. */
type ReasonDialogType = 'reject' | 'cancel' | null;

export default function DocumentDetailPage({
  mode,
  basePath,
  queryKey,
  typeLabel,
  fetchDocument,
  submitDocument,
  approveDocument,
  rejectDocument,
  cancelDocument,
  deleteDocument,
}: DocumentDetailPageProps) {
  const { id } = useParams<{ id: string }>();
  const documentId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();

  const [reasonDialog, setReasonDialog] = useState<ReasonDialogType>(null);
  const [reasonText, setReasonText] = useState('');

  const isReceipt = mode === 'receipt';

  const { data: document, isLoading, isError, error } = useQuery({
    queryKey: [queryKey, documentId],
    queryFn: () => fetchDocument(documentId),
    enabled: Number.isFinite(documentId),
  });

  /** Làm mới dữ liệu phiếu và các truy vấn liên quan (tồn kho, dashboard, thông báo). */
  const invalidateAll = () => {
    void queryClient.invalidateQueries({ queryKey: [queryKey] });
    void queryClient.invalidateQueries({ queryKey: ['inventory'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const submitMutation = useMutation({
    mutationFn: () => submitDocument(documentId),
    onSuccess: (updated) => {
      message.success(`Đã gửi duyệt phiếu ${updated.code}. Quản lý kho sẽ nhận được thông báo.`);
      invalidateAll();
    },
    onError: (error) => showActionError(error),
  });

  const approveMutation = useMutation({
    mutationFn: () => approveDocument(documentId),
    onSuccess: (updated) => {
      message.success(
        isReceipt
          ? `Đã duyệt phiếu ${updated.code}. Tồn kho đã được cộng và giá vốn được cập nhật.`
          : `Đã duyệt phiếu ${updated.code}. Tồn kho đã được trừ.`,
      );
      invalidateAll();
    },
    onError: (error) => showActionError(error),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => rejectDocument(documentId, reason),
    onSuccess: (updated) => {
      message.success(`Đã từ chối phiếu ${updated.code}. Người lập sẽ nhận được thông báo.`);
      closeReasonDialog();
      invalidateAll();
    },
    onError: (error) => showActionError(error),
  });

  const cancelMutation = useMutation({
    mutationFn: (reason: string) => cancelDocument(documentId, reason),
    onSuccess: (updated) => {
      message.success(`Đã hủy phiếu ${updated.code}. Hệ thống đã ghi giao dịch đảo ngược trong sổ kho.`);
      closeReasonDialog();
      invalidateAll();
    },
    onError: (error) => showActionError(error),
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteDocument(documentId),
    onSuccess: () => {
      message.success('Đã xóa phiếu nháp.');
      invalidateAll();
      navigate(`/${basePath}`);
    },
    onError: (error) => showActionError(error),
  });

  const printMutation = useMutation({
    mutationFn: () => openPdfInNewTab(`/api/print/${isReceipt ? 'stock-receipts' : 'stock-issues'}/${documentId}`),
    onError: (error) => message.error(getErrorMessage(error)),
  });

  /**
   * Hiển thị lỗi hành động.
   *
   * Riêng lỗi thiếu tồn (khi duyệt phiếu xuất hoặc hủy phiếu nhập) được trình bày thành
   * bảng liệt kê từng sản phẩm thiếu - người duyệt biết ngay phải xử lý mặt hàng nào.
   */
  const showActionError = (error: unknown) => {
    const shortages = getStockShortages(error);
    if (shortages.length > 0) {
      modal.error({
        title: 'Không đủ tồn kho',
        width: 560,
        content: (
          <div style={{ marginTop: 12 }}>
            <Table
              rowKey="productId"
              size="small"
              pagination={false}
              dataSource={shortages}
              columns={[
                { title: 'Mã SKU', dataIndex: 'sku', width: 110 },
                { title: 'Sản phẩm', dataIndex: 'productName' },
                {
                  title: 'Cần',
                  dataIndex: 'requested',
                  width: 90,
                  align: 'right',
                  render: (value: number) => formatQuantity(value),
                },
                {
                  title: 'Còn',
                  dataIndex: 'available',
                  width: 90,
                  align: 'right',
                  render: (value: number) => (
                    <Text type="danger" strong>
                      {formatQuantity(value)}
                    </Text>
                  ),
                },
              ]}
            />
          </div>
        ),
      });
      return;
    }
    message.error(getErrorMessage(error));
  };

  const closeReasonDialog = () => {
    setReasonDialog(null);
    setReasonText('');
  };

  const handleReasonSubmit = () => {
    const reason = reasonText.trim();
    if (reason.length < 3) {
      message.warning('Vui lòng nhập lý do (tối thiểu 3 ký tự).');
      return;
    }
    if (reasonDialog === 'reject') {
      rejectMutation.mutate(reason);
    } else if (reasonDialog === 'cancel') {
      cancelMutation.mutate(reason);
    }
  };

  if (isLoading) {
    return (
      <LoadingBlock message="Đang tải phiếu..." />
    );
  }

  if (isError || !document) {
    return (
      <>
        <PageHeader title="Chi tiết phiếu" />
        <Alert type="error" showIcon message="Không tải được phiếu" description={(error as Error)?.message} />
      </>
    );
  }

  /** Bước hiện tại trên thanh tiến trình trạng thái. */
  const currentStep = (() => {
    switch (document.status) {
      case 'DRAFT':
        return 0;
      case 'PENDING':
        return 1;
      case 'COMPLETED':
      case 'CANCELLED':
        return 2;
      case 'REJECTED':
        return 1;
      default:
        return 0;
    }
  })();

  const columns: ColumnsType<DocumentItem> = [
    { title: 'STT', dataIndex: 'lineNo', width: 56, align: 'center' },
    {
      title: 'Sản phẩm',
      key: 'product',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.productName}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.sku}
            {record.barcode ? ` · ${record.barcode}` : ''}
          </Text>
        </Space>
      ),
    },
    { title: 'ĐVT', dataIndex: 'unitName', width: 80, align: 'center' },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      width: 110,
      align: 'right',
      render: (value: number) => <Text strong>{formatQuantity(value)}</Text>,
    },
    {
      title: isReceipt ? 'Đơn giá' : 'Giá vốn',
      dataIndex: 'unitPrice',
      width: 130,
      align: 'right',
      render: (value?: number | null) => <MoneyText value={value} />,
    },
    {
      title: 'Thành tiền',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (value?: number | null) => <MoneyText value={value} strong />,
    },
    {
      title: 'Tồn hiện tại',
      dataIndex: 'quantityOnHand',
      width: 120,
      align: 'right',
      responsive: ['lg'],
      render: (value: number) => formatQuantity(value),
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      responsive: ['xl'],
      render: (value?: string) => value ?? '—',
    },
  ];

  return (
    <>
      <PageHeader
        title={`${document.documentTypeName} ${document.code}`}
        description={`${document.typeName} · ${document.warehouseName} · Ngày chứng từ ${formatDate(document.documentDate)}`}
        extra={
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/${basePath}`)}>
              Quay lại
            </Button>

            <Button
              icon={<PrinterOutlined />}
              loading={printMutation.isPending}
              onClick={() => printMutation.mutate()}
            >
              In phiếu (PDF)
            </Button>

            {document.canEdit && (
              <>
                <Button icon={<EditOutlined />} onClick={() => navigate(`/${basePath}/${document.id}/edit`)}>
                  Sửa
                </Button>
                <Button
                  type="primary"
                  ghost
                  icon={<SendOutlined />}
                  loading={submitMutation.isPending}
                  onClick={() =>
                    modal.confirm({
                      title: 'Gửi duyệt phiếu?',
                      content: 'Sau khi gửi, phiếu sẽ không sửa được cho tới khi quản lý duyệt hoặc từ chối.',
                      okText: 'Gửi duyệt',
                      cancelText: 'Hủy',
                      onOk: () => submitMutation.mutate(),
                    })
                  }
                >
                  Gửi duyệt
                </Button>
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  loading={deleteMutation.isPending}
                  onClick={() =>
                    modal.confirm({
                      title: 'Xóa phiếu nháp?',
                      content: 'Phiếu sẽ bị xóa vĩnh viễn. Thao tác này không hoàn lại được.',
                      okText: 'Xóa',
                      okButtonProps: { danger: true },
                      cancelText: 'Hủy',
                      onOk: () => deleteMutation.mutate(),
                    })
                  }
                >
                  Xóa
                </Button>
              </>
            )}

            {document.canApprove && (
              <>
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  loading={approveMutation.isPending}
                  onClick={() =>
                    modal.confirm({
                      title: 'Duyệt phiếu?',
                      content: isReceipt
                        ? 'Tồn kho sẽ được cộng và giá vốn bình quân được tính lại ngay sau khi duyệt.'
                        : 'Tồn kho sẽ bị trừ ngay sau khi duyệt. Hệ thống kiểm tra lại tồn trước khi thực hiện.',
                      okText: 'Duyệt phiếu',
                      cancelText: 'Hủy',
                      onOk: () => approveMutation.mutate(),
                    })
                  }
                >
                  Duyệt
                </Button>
                <Button
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => {
                    setReasonText('');
                    setReasonDialog('reject');
                  }}
                >
                  Từ chối
                </Button>
              </>
            )}

            {document.canCancel && (
              <Button
                danger
                icon={<StopOutlined />}
                onClick={() => {
                  setReasonText('');
                  setReasonDialog('cancel');
                }}
              >
                Hủy phiếu
              </Button>
            )}
          </Space>
        }
      />

      {/* ---------- Thanh tiến trình trạng thái ---------- */}
      <Card size="small" style={{ marginBottom: 12 }}>
        <Steps
          size="small"
          current={currentStep}
          status={
            document.status === 'REJECTED' || document.status === 'CANCELLED' ? 'error' : 'process'
          }
          items={[
            { title: 'Nháp', description: formatDateTime(document.createdAt) },
            {
              title: document.status === 'REJECTED' ? 'Bị từ chối' : 'Chờ duyệt',
              description: document.submittedAt ? formatDateTime(document.submittedAt) : '—',
            },
            {
              title: document.status === 'CANCELLED' ? 'Đã hủy' : 'Hoàn tất',
              description: document.approvedAt
                ? formatDateTime(document.approvedAt)
                : document.cancelledAt
                  ? formatDateTime(document.cancelledAt)
                  : '—',
            },
          ]}
        />
      </Card>

      {/* ---------- Thông báo trạng thái đặc biệt ---------- */}
      {document.status === 'REJECTED' && document.rejectReason && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message="Phiếu bị từ chối"
          description={
            <Space direction="vertical" size={2}>
              <Text>Lý do: {document.rejectReason}</Text>
              {document.canEdit && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Bạn có thể sửa phiếu rồi gửi duyệt lại.
                </Text>
              )}
            </Space>
          }
        />
      )}

      {document.status === 'CANCELLED' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`Phiếu đã bị hủy bởi ${document.cancelledByName ?? 'quản lý'} lúc ${formatDateTime(document.cancelledAt)}`}
          description={
            <Space direction="vertical" size={2}>
              <Text>Lý do: {document.cancelReason}</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Hệ thống đã ghi giao dịch đảo ngược trong sổ kho; tồn kho đã được điều chỉnh lại.
              </Text>
            </Space>
          }
        />
      )}

      {document.status === 'COMPLETED' && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 12 }}
          message={`Phiếu đã hoàn tất, tồn kho đã được cập nhật`}
          description={`Người duyệt: ${document.approvedByName ?? '—'} · ${formatDateTime(document.approvedAt)}`}
        />
      )}

      <Row gutter={[12, 12]}>
        <Col xs={24} lg={8}>
          <Card size="small" title="Thông tin phiếu">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Mã phiếu">
                <Text strong>{document.code}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <StatusTag status={document.status} label={document.statusName} />
              </Descriptions.Item>
              <Descriptions.Item label={typeLabel}>{document.typeName}</Descriptions.Item>
              <Descriptions.Item label="Kho">
                {document.warehouseName} ({document.warehouseCode})
              </Descriptions.Item>
              <Descriptions.Item label="Ngày chứng từ">{formatDate(document.documentDate)}</Descriptions.Item>
              {document.partnerName && (
                <Descriptions.Item label="Đối tác">
                  {document.partnerName}
                  {document.partnerCode ? ` (${document.partnerCode})` : ''}
                </Descriptions.Item>
              )}
              {document.reason && <Descriptions.Item label="Lý do">{document.reason}</Descriptions.Item>}
              <Descriptions.Item label="Người lập">{document.createdByName ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Thời điểm lập">{formatDateTime(document.createdAt)}</Descriptions.Item>
              {document.approvedByName && (
                <Descriptions.Item label="Người duyệt">{document.approvedByName}</Descriptions.Item>
              )}
              {document.note && <Descriptions.Item label="Ghi chú">{document.note}</Descriptions.Item>}
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={16}>
          <Card size="small" title={`Danh sách hàng (${document.items.length} dòng)`}>
            <Table<DocumentItem>
              rowKey={(record) => String(record.id ?? record.lineNo)}
              columns={columns}
              dataSource={document.items}
              size="small"
              pagination={false}
              scroll={{ x: 'max-content' }}
            />

            <Divider style={{ margin: '12px 0' }} />

            <Row justify="end">
              <Col xs={24} sm={14} md={10}>
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Row justify="space-between">
                    <Text type="secondary">Tổng số lượng:</Text>
                    <Text strong>
                      {formatQuantity(document.items.reduce((sum, item) => sum + item.quantity, 0))}
                    </Text>
                  </Row>
                  <Row justify="space-between" align="middle">
                    <Text type="secondary">{isReceipt ? 'Tổng tiền:' : 'Tổng giá trị xuất:'}</Text>
                    {document.totalAmount === null || document.totalAmount === undefined ? (
                      <Text type="secondary">—</Text>
                    ) : (
                      <Title level={4} style={{ margin: 0, color: '#1677ff' }}>
                        {formatCurrency(document.totalAmount)}
                      </Title>
                    )}
                  </Row>
                </Space>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      {/* ---------- Hộp thoại nhập lý do từ chối / hủy ---------- */}
      <Modal
        open={reasonDialog !== null}
        title={reasonDialog === 'reject' ? 'Từ chối phiếu' : 'Hủy phiếu đã hoàn tất'}
        onCancel={closeReasonDialog}
        onOk={handleReasonSubmit}
        okText={reasonDialog === 'reject' ? 'Từ chối phiếu' : 'Hủy phiếu'}
        cancelText="Đóng"
        okButtonProps={{ danger: true }}
        confirmLoading={rejectMutation.isPending || cancelMutation.isPending}
      >
        <Space direction="vertical" size={10} style={{ width: '100%', marginTop: 12 }}>
          {reasonDialog === 'cancel' && (
            <Alert
              type="warning"
              showIcon
              message="Hủy phiếu sẽ sinh giao dịch đảo ngược trong sổ kho"
              description={
                isReceipt
                  ? 'Tồn kho sẽ bị trừ lại. Nếu hàng đã xuất đi và tồn không còn đủ, hệ thống sẽ chặn thao tác này.'
                  : 'Tồn kho sẽ được cộng lại số lượng đã xuất.'
              }
            />
          )}

          <div>
            <Text strong>Lý do (bắt buộc)</Text>
            <TextArea
              value={reasonText}
              onChange={(event) => setReasonText(event.target.value)}
              rows={3}
              maxLength={500}
              showCount
              placeholder={
                reasonDialog === 'reject'
                  ? 'Ví dụ: Đơn giá nhập cao hơn hợp đồng, vui lòng kiểm tra lại với nhà cung cấp'
                  : 'Ví dụ: Nhập sai nhà cung cấp, cần hủy để lập lại phiếu đúng'
              }
              autoFocus
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              Lý do được lưu vào phiếu và nhật ký hoạt động để truy vết về sau.
            </Text>
          </div>
        </Space>
      </Modal>
    </>
  );
}
