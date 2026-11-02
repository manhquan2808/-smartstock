import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeftOutlined,
  BarcodeOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  PrinterOutlined,
  SaveOutlined,
  SendOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import MoneyText from '@/components/MoneyText';
import StatusTag from '@/components/StatusTag';
import BarcodeScanner from '@/components/BarcodeScanner';
import { stocktakeApi } from '@/api/endpoints';
import { getErrorMessage, openPdfInNewTab } from '@/api/client';
import { formatDate, formatDateTime, formatQuantity } from '@/utils/format';
import type { StocktakeItem } from '@/types';
import LoadingBlock from '../components/LoadingBlock';

const { Text, Title } = Typography;
const { TextArea } = Input;

/**
 * Trang CHI TIẾT ĐỢT KIỂM KÊ - nơi nhân viên nhập số đếm thực tế.
 *
 * Thiết kế hướng tới dùng trên ĐIỆN THOẠI khi đi đếm trong kho:
 *
 * - Nút quét mã to, quét xong tự nhảy tới đúng dòng sản phẩm và tô sáng.
 * - Số đếm nhập trực tiếp trên bảng, lưu theo lô (bấm "Lưu số đếm") nên không cần mạng
 *   liên tục cho từng ô.
 * - Chênh lệch hiển thị ngay cạnh để người đếm tự kiểm tra lại khi thấy lệch lớn.
 */
export default function StocktakeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const stocktakeId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();

  /** Số đếm đang nhập nhưng chưa lưu, theo id dòng kiểm kê. */
  const [draftCounts, setDraftCounts] = useState<Record<number, number | null>>({});

  /** Ghi chú đang nhập nhưng chưa lưu, theo id dòng kiểm kê. */
  const [draftNotes, setDraftNotes] = useState<Record<number, string>>({});
  const [isScannerOpen, setIsScannerOpen] = useState(false);
  const [highlightedItemId, setHighlightedItemId] = useState<number | null>(null);
  const [isRejectOpen, setIsRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showOnlyUncounted, setShowOnlyUncounted] = useState(false);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['stocktakes', stocktakeId],
    queryFn: () => stocktakeApi.getById(stocktakeId),
    enabled: Number.isFinite(stocktakeId),
  });

  // Nạp số đã đếm từ server vào ô nhập khi mở trang
  useEffect(() => {
    if (data) {
      const initial: Record<number, number | null> = {};
      data.items.forEach((item) => {
        initial[item.id] = item.actualQuantity ?? null;
      });
      setDraftCounts(initial);
    }
  }, [data]);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['stocktakes'] });
    void queryClient.invalidateQueries({ queryKey: ['inventory'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const saveCountMutation = useMutation({
    mutationFn: () =>
      stocktakeApi.saveCount(
        stocktakeId,
        Object.entries(draftCounts).map(([itemId, actualQuantity]) => ({
          itemId: Number(itemId),
          actualQuantity,
          // Chỉ gửi ghi chú của những dòng người dùng đã sửa
          note: draftNotes[Number(itemId)],
        })),
      ),
    onSuccess: () => {
      message.success('Đã lưu số đếm thực tế.');
      setDraftNotes({});
      invalidate();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const submitMutation = useMutation({
    mutationFn: () => stocktakeApi.submit(stocktakeId),
    onSuccess: (updated) => {
      message.success(`Đã gửi duyệt đợt kiểm kê ${updated.code}.`);
      invalidate();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const approveMutation = useMutation({
    mutationFn: () => stocktakeApi.approve(stocktakeId),
    onSuccess: (updated) => {
      message.success(`Đã duyệt kiểm kê ${updated.code}. Tồn kho đã điều chỉnh về số thực tế.`);
      invalidate();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => stocktakeApi.reject(stocktakeId, reason),
    onSuccess: () => {
      message.success('Đã từ chối đợt kiểm kê.');
      setIsRejectOpen(false);
      setRejectReason('');
      invalidate();
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const printMutation = useMutation({
    mutationFn: () => openPdfInNewTab(`/api/print/stocktakes/${stocktakeId}`),
    onError: (error) => message.error(getErrorMessage(error)),
  });

  /** Quét mã: tìm dòng tương ứng rồi tô sáng và cuộn tới. */
  const handleScan = (code: string) => {
    const matched = data?.items.find((item) => item.barcode === code || item.sku.toLowerCase() === code.toLowerCase());
    if (!matched) {
      message.warning(`Không tìm thấy sản phẩm với mã "${code}" trong đợt kiểm kê này.`);
      return;
    }
    setHighlightedItemId(matched.id);
    message.success(`Đã tìm thấy: ${matched.productName}`);
    // Cuộn tới dòng được tô sáng để người dùng nhập ngay
    setTimeout(() => {
      document.getElementById(`stocktake-row-${matched.id}`)?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
    }, 100);
  };

  /** Số dòng đã nhập số đếm (tính cả phần chưa lưu). */
  const countedCount = useMemo(
    () => Object.values(draftCounts).filter((value) => value !== null && value !== undefined).length,
    [draftCounts],
  );

  /** Có thay đổi chưa lưu hay không, để nhắc người dùng trước khi gửi duyệt. */
  const hasUnsavedChanges = useMemo(() => {
    if (!data) {
      return false;
    }
    return data.items.some((item) => {
      const draft = draftCounts[item.id];
      const saved = item.actualQuantity ?? null;
      return (draft ?? null) !== saved;
    });
  }, [data, draftCounts]);

  const visibleItems = useMemo(() => {
    if (!data) {
      return [];
    }
    if (!showOnlyUncounted) {
      return data.items;
    }
    return data.items.filter((item) => draftCounts[item.id] === null || draftCounts[item.id] === undefined);
  }, [data, showOnlyUncounted, draftCounts]);

  if (isLoading) {
    return (
      <LoadingBlock message="Đang tải đợt kiểm kê..." />
    );
  }

  if (isError || !data) {
    return (
      <>
        <PageHeader title="Chi tiết kiểm kê" />
        <Alert type="error" showIcon message="Không tải được dữ liệu" description={(error as Error)?.message} />
      </>
    );
  }

  const isEditable = data.canEdit;
  const progressPercent = data.totalItems > 0 ? Math.round((countedCount / data.totalItems) * 100) : 0;

  const columns: ColumnsType<StocktakeItem> = [
    { title: 'STT', dataIndex: 'lineNo', width: 56, align: 'center' },
    {
      title: 'Sản phẩm',
      key: 'product',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.productName}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.sku}
            {record.barcode ? ` · ${record.barcode}` : ''} · {record.unitName}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Tồn hệ thống',
      dataIndex: 'systemQuantity',
      width: 120,
      align: 'right',
      render: (value: number) => formatQuantity(value),
    },
    {
      title: 'Số đếm thực tế',
      key: 'actual',
      width: 150,
      render: (_, record) =>
        isEditable ? (
          <InputNumber<number>
            value={draftCounts[record.id] ?? undefined}
            min={0}
            step={1}
            precision={3}
            style={{ width: '100%' }}
            placeholder="Chưa đếm"
            onChange={(value) =>
              setDraftCounts((current) => ({ ...current, [record.id]: value ?? null }))
            }
          />
        ) : record.actualQuantity === null || record.actualQuantity === undefined ? (
          <Text type="secondary">Chưa đếm</Text>
        ) : (
          <Text strong>{formatQuantity(record.actualQuantity)}</Text>
        ),
    },
    {
      title: 'Chênh lệch',
      key: 'difference',
      width: 130,
      align: 'right',
      render: (_, record) => {
        // Tính chênh lệch theo số đang nhập để người đếm thấy ngay, không chờ lưu
        const actual = isEditable ? draftCounts[record.id] : record.actualQuantity;
        if (actual === null || actual === undefined) {
          return <Text type="secondary">—</Text>;
        }
        const difference = actual - record.systemQuantity;
        if (difference === 0) {
          return <Tag color="green">Khớp</Tag>;
        }
        return (
          <Text strong className={difference < 0 ? 'text-negative' : 'text-positive'}>
            {difference > 0 ? '+' : ''}
            {formatQuantity(difference)}
          </Text>
        );
      },
    },
    {
      title: 'Giá trị lệch',
      dataIndex: 'differenceValue',
      width: 140,
      align: 'right',
      responsive: ['lg'],
      render: (value?: number | null) => <MoneyText value={value} />,
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      width: 180,
      responsive: ['xl'],
      render: (value?: string, record?: StocktakeItem) =>
        isEditable && record ? (
          <Input
            value={draftNotes[record.id] ?? value ?? ''}
            placeholder="Ví dụ: hộp bị móp"
            maxLength={500}
            onChange={(event) =>
              setDraftNotes((current) => ({ ...current, [record.id]: event.target.value }))
            }
          />
        ) : (
          (value ?? '—')
        ),
    },
  ];

  return (
    <>
      <PageHeader
        title={`Kiểm kê ${data.code}`}
        description={`${data.warehouseName} · ${data.categoryName} · Ngày ${formatDate(data.documentDate)}`}
        extra={
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/stocktakes')}>
              Quay lại
            </Button>
            <Button
              icon={<PrinterOutlined />}
              loading={printMutation.isPending}
              onClick={() => printMutation.mutate()}
            >
              In biên bản
            </Button>

            {isEditable && (
              <>
                <Button icon={<BarcodeOutlined />} onClick={() => setIsScannerOpen(true)}>
                  Quét mã tìm sản phẩm
                </Button>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  loading={saveCountMutation.isPending}
                  onClick={() => saveCountMutation.mutate()}
                  disabled={!hasUnsavedChanges}
                >
                  Lưu số đếm
                </Button>
                <Button
                  type="primary"
                  ghost
                  icon={<SendOutlined />}
                  loading={submitMutation.isPending}
                  onClick={() => {
                    if (hasUnsavedChanges) {
                      message.warning('Vui lòng bấm "Lưu số đếm" trước khi gửi duyệt.');
                      return;
                    }
                    modal.confirm({
                      title: 'Gửi duyệt đợt kiểm kê?',
                      content: 'Sau khi gửi, bạn không sửa được số đếm cho tới khi quản lý duyệt hoặc từ chối.',
                      okText: 'Gửi duyệt',
                      cancelText: 'Hủy',
                      onOk: () => submitMutation.mutate(),
                    });
                  }}
                >
                  Gửi duyệt
                </Button>
              </>
            )}

            {data.canApprove && (
              <>
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  loading={approveMutation.isPending}
                  onClick={() =>
                    modal.confirm({
                      title: 'Duyệt kết quả kiểm kê?',
                      width: 520,
                      content: (
                        <Space direction="vertical" size={6} style={{ marginTop: 10 }}>
                          <Text>
                            Tồn kho của {data.differenceItems} mặt hàng có chênh lệch sẽ được điều chỉnh về đúng
                            số đếm thực tế.
                          </Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            Hệ thống ghi một giao dịch loại "Điều chỉnh kiểm kê" cho mỗi mặt hàng lệch, nên thẻ
                            kho vẫn truy được nguyên nhân thay đổi.
                          </Text>
                        </Space>
                      ),
                      okText: 'Duyệt và điều chỉnh tồn',
                      cancelText: 'Hủy',
                      onOk: () => approveMutation.mutate(),
                    })
                  }
                >
                  Duyệt kiểm kê
                </Button>
                <Button danger icon={<CloseCircleOutlined />} onClick={() => setIsRejectOpen(true)}>
                  Từ chối
                </Button>
              </>
            )}
          </Space>
        }
      />

      {/* Cảnh báo có phiếu nhập/xuất hoàn tất sau khi tạo đợt kiểm kê */}
      {data.warningDocumentCodes.length > 0 && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 12 }}
          message="Có phiếu nhập/xuất đã hoàn tất sau khi tạo đợt kiểm kê"
          description={
            <Space direction="vertical" size={2}>
              <Text>
                Các phiếu: <Text code>{data.warningDocumentCodes.join(', ')}</Text>
              </Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Số tồn hệ thống trong đợt kiểm kê này được chụp lúc tạo đợt, nên có thể không còn khớp với tồn
                hiện tại. Hãy cân nhắc tạo đợt kiểm kê mới nếu chênh lệch không giải thích được.
              </Text>
            </Space>
          }
        />
      )}

      {data.status === 'REJECTED' && data.rejectReason && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message="Đợt kiểm kê bị từ chối"
          description={`Lý do: ${data.rejectReason}`}
        />
      )}

      {data.status === 'COMPLETED' && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 12 }}
          message="Đợt kiểm kê đã được duyệt, tồn kho đã điều chỉnh về số thực tế"
          description={`Người duyệt: ${data.approvedByName ?? '—'} · ${formatDateTime(data.approvedAt)}`}
        />
      )}

      <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Tổng mặt hàng" value={data.totalItems} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Space direction="vertical" size={2} style={{ width: '100%' }}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Tiến độ đếm
              </Text>
              <Progress
                percent={progressPercent}
                size="small"
                format={() => `${countedCount}/${data.totalItems}`}
              />
            </Space>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Mặt hàng lệch"
              value={data.differenceItems}
              valueStyle={{ color: data.differenceItems > 0 ? '#d46b08' : undefined }}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Space direction="vertical" size={0}>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Giá trị chênh lệch
              </Text>
              <Title level={4} style={{ margin: 0 }}>
                <MoneyText value={data.totalDifferenceValue} strong />
              </Title>
            </Space>
          </Card>
        </Col>
      </Row>

      <Row gutter={[12, 12]}>
        <Col xs={24} xl={6}>
          <Card size="small" title="Thông tin đợt kiểm kê">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Mã phiếu">
                <Text strong>{data.code}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <StatusTag status={data.status} label={data.statusName} />
              </Descriptions.Item>
              <Descriptions.Item label="Kho">{data.warehouseName}</Descriptions.Item>
              <Descriptions.Item label="Phạm vi">{data.categoryName}</Descriptions.Item>
              <Descriptions.Item label="Ngày kiểm kê">{formatDate(data.documentDate)}</Descriptions.Item>
              <Descriptions.Item label="Người lập">{data.createdByName ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Thời điểm tạo">{formatDateTime(data.createdAt)}</Descriptions.Item>
              {data.approvedByName && (
                <Descriptions.Item label="Người duyệt">{data.approvedByName}</Descriptions.Item>
              )}
              {data.note && <Descriptions.Item label="Ghi chú">{data.note}</Descriptions.Item>}
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} xl={18}>
          <Card
            size="small"
            title={`Danh sách kiểm kê (${visibleItems.length}/${data.totalItems} dòng)`}
            extra={
              isEditable && (
                <Button
                  size="small"
                  type={showOnlyUncounted ? 'primary' : 'default'}
                  onClick={() => setShowOnlyUncounted(!showOnlyUncounted)}
                >
                  {showOnlyUncounted ? 'Hiện tất cả' : 'Chỉ hiện chưa đếm'}
                </Button>
              )
            }
          >
            {isEditable && (
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 12 }}
                message="Nhập số đếm thực tế rồi bấm Lưu số đếm"
                description="Có thể lưu nhiều lần, không cần đếm hết một lượt. Trên điện thoại, dùng nút Quét mã để nhảy nhanh tới sản phẩm đang cầm trên tay."
              />
            )}

            <Table<StocktakeItem>
              rowKey="id"
              columns={columns}
              dataSource={visibleItems}
              size="small"
              scroll={{ x: 'max-content' }}
              onRow={(record) => ({
                id: `stocktake-row-${record.id}`,
                style:
                  highlightedItemId === record.id
                    ? { background: 'rgba(22,119,255,0.12)', transition: 'background 0.4s' }
                    : undefined,
              })}
              rowClassName={(record) => {
                const actual = isEditable ? draftCounts[record.id] : record.actualQuantity;
                if (actual === null || actual === undefined) {
                  return '';
                }
                return actual - record.systemQuantity < 0 ? 'row-danger' : '';
              }}
              pagination={{ pageSize: 50, showSizeChanger: true, pageSizeOptions: ['20', '50', '100', '200'] }}
              locale={{ emptyText: showOnlyUncounted ? 'Đã đếm hết mọi mặt hàng.' : 'Không có dữ liệu' }}
            />
          </Card>
        </Col>
      </Row>

      <BarcodeScanner
        open={isScannerOpen}
        onClose={() => setIsScannerOpen(false)}
        onScan={handleScan}
        title="Quét mã để tìm sản phẩm trong đợt kiểm kê"
      />

      <Modal
        open={isRejectOpen}
        title="Từ chối đợt kiểm kê"
        onCancel={() => setIsRejectOpen(false)}
        onOk={() => {
          if (rejectReason.trim().length < 3) {
            message.warning('Vui lòng nhập lý do từ chối (tối thiểu 3 ký tự).');
            return;
          }
          rejectMutation.mutate(rejectReason.trim());
        }}
        okText="Từ chối"
        okButtonProps={{ danger: true }}
        cancelText="Đóng"
        confirmLoading={rejectMutation.isPending}
      >
        <Space direction="vertical" size={6} style={{ width: '100%', marginTop: 12 }}>
          <Text strong>Lý do từ chối (bắt buộc)</Text>
          <TextArea
            value={rejectReason}
            onChange={(event) => setRejectReason(event.target.value)}
            rows={3}
            maxLength={500}
            showCount
            placeholder="Ví dụ: Chênh lệch quá lớn ở nhiều mặt hàng, yêu cầu đếm lại"
            autoFocus
          />
        </Space>
      </Modal>
    </>
  );
}
