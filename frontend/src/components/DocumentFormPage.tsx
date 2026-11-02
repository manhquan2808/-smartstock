import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeftOutlined,
  BarcodeOutlined,
  DeleteOutlined,
  PlusOutlined,
  SaveOutlined,
  SendOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { useMutation, useQuery } from '@tanstack/react-query';
import PageHeader from './PageHeader';
import BarcodeScanner from './BarcodeScanner';
import { customerApi, productApi, supplierApi } from '@/api/endpoints';
import { getErrorMessage, getStockShortages } from '@/api/client';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { dayjs, formatCurrency, formatQuantity, API_DATE_FORMAT } from '@/utils/format';
import type { DocumentDetail, ProductLookupResponse } from '@/types';
import LoadingBlock from './LoadingBlock';

const { Text, Title } = Typography;

/**
 * Màn hình LẬP / SỬA PHIẾU, dùng chung cho phiếu nhập và phiếu xuất.
 *
 * Các điểm trải nghiệm quan trọng (theo mục 7 của đặc tả):
 *
 * - Thêm dòng bằng ô tìm sản phẩm HOẶC nút quét mã vạch.
 * - Quét trùng sản phẩm đã có thì TĂNG số lượng thay vì thêm dòng mới.
 * - Hiển thị tồn hiện có cạnh từng dòng; dòng xuất vượt tồn bị tô đỏ.
 * - Tổng tiền cập nhật ngay khi đổi số lượng hoặc đơn giá.
 *
 * Màn hình được thiết kế dùng tốt trên điện thoại: bảng cuộn ngang, nút quét mã to, các ô
 * nhập xếp dọc khi màn hình hẹp.
 */

/** Một dòng hàng đang nhập trên biểu mẫu. */
export interface DocumentFormLine {
  /** Khóa tạm để React phân biệt các dòng khi chưa lưu. */
  key: string;
  productId: number;
  sku: string;
  productName: string;
  unitName: string;
  quantity: number;
  /** Chỉ dùng cho phiếu nhập. */
  unitPrice: number;
  /** Tồn hiện có tại kho đang chọn, dùng để cảnh báo xuất vượt tồn. */
  quantityOnHand: number;
  note?: string;
}

interface DocumentFormPageProps {
  /** 'receipt' = phiếu nhập (có đơn giá), 'issue' = phiếu xuất (dùng giá vốn). */
  mode: 'receipt' | 'issue';
  basePath: string;
  typeLabel: string;
  typeOptions: Array<{ value: string; label: string }>;
  /** Quy tắc bắt buộc đối tác / lý do theo từng loại phiếu. */
  typeRules: Record<string, { supplierRequired: boolean; customerRequired: boolean; reasonRequired: boolean }>;
  defaultType: string;
  /** Tải phiếu khi ở chế độ sửa. */
  fetchDocument: (id: number) => Promise<DocumentDetail>;
  /** Lưu phiếu mới; `submit` = true nghĩa là gửi duyệt luôn. */
  createDocument: (payload: DocumentPayload, submit: boolean) => Promise<DocumentDetail>;
  updateDocument: (id: number, payload: DocumentPayload) => Promise<DocumentDetail>;
}

/** Dữ liệu gửi lên backend, hợp nhất cho cả hai loại phiếu. */
export interface DocumentPayload {
  warehouseId: number;
  documentType: string;
  documentDate: string;
  supplierId?: number | null;
  customerId?: number | null;
  reason?: string;
  note?: string;
  items: Array<{ productId: number; quantity: number; unitPrice?: number; note?: string }>;
}

interface HeaderFormValues {
  warehouseId: number;
  documentType: string;
  documentDate: Dayjs;
  supplierId?: number;
  customerId?: number;
  reason?: string;
  note?: string;
}

export default function DocumentFormPage({
  mode,
  basePath,
  typeLabel,
  typeOptions,
  typeRules,
  defaultType,
  fetchDocument,
  createDocument,
  updateDocument,
}: DocumentFormPageProps) {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const documentId = id ? Number(id) : undefined;

  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<HeaderFormValues>();
  const { warehouses, currentWarehouseId } = useWarehouse();

  const [lines, setLines] = useState<DocumentFormLine[]>([]);
  const [isScannerOpen, setIsScannerOpen] = useState(false);
  const [productSearchText, setProductSearchText] = useState('');

  const isReceipt = mode === 'receipt';

  // Theo dõi loại phiếu và kho đang chọn để áp quy tắc bắt buộc và tra tồn đúng kho
  const selectedType = Form.useWatch('documentType', form) ?? defaultType;
  const selectedWarehouseId = Form.useWatch('warehouseId', form);
  const rules = typeRules[selectedType] ?? {
    supplierRequired: false,
    customerRequired: false,
    reasonRequired: false,
  };

  // ---------- Dữ liệu danh mục ----------
  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers', 'all'],
    queryFn: supplierApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  const { data: customers = [] } = useQuery({
    queryKey: ['customers', 'all'],
    queryFn: customerApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  /** Tìm sản phẩm cho ô chọn; gọi lại khi đổi kho để lấy đúng tồn hiện có. */
  const { data: productOptions = [], isFetching: isSearchingProducts } = useQuery({
    queryKey: ['products', 'lookup', productSearchText, selectedWarehouseId],
    queryFn: () =>
      productApi.lookup({
        keyword: productSearchText || undefined,
        warehouseId: selectedWarehouseId,
        limit: 20,
      }),
    enabled: Boolean(selectedWarehouseId),
  });

  // ---------- Nạp phiếu khi sửa ----------
  const { data: existingDocument, isLoading: isLoadingDocument } = useQuery({
    queryKey: [basePath, documentId],
    queryFn: () => fetchDocument(documentId as number),
    enabled: isEditMode,
  });

  useEffect(() => {
    if (!existingDocument) {
      return;
    }
    form.setFieldsValue({
      warehouseId: existingDocument.warehouseId,
      documentType: existingDocument.typeCode,
      documentDate: dayjs(existingDocument.documentDate),
      supplierId: rules.supplierRequired ? (existingDocument.partnerId ?? undefined) : undefined,
      customerId: rules.customerRequired ? (existingDocument.partnerId ?? undefined) : undefined,
      reason: existingDocument.reason,
      note: existingDocument.note,
    });

    setLines(
      existingDocument.items.map((item, index) => ({
        key: `existing-${item.id ?? index}`,
        productId: item.productId,
        sku: item.sku,
        productName: item.productName,
        unitName: item.unitName,
        quantity: item.quantity,
        unitPrice: item.unitPrice ?? 0,
        quantityOnHand: item.quantityOnHand,
        note: item.note,
      })),
    );
    // Chỉ chạy khi tải được phiếu; rules thay đổi theo loại phiếu đã nạp
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existingDocument, form]);

  // Đặt kho mặc định theo kho đang làm việc khi lập phiếu mới
  useEffect(() => {
    if (!isEditMode) {
      const defaultWarehouseId = currentWarehouseId ?? warehouses[0]?.id;
      if (defaultWarehouseId) {
        form.setFieldValue('warehouseId', defaultWarehouseId);
      }
    }
  }, [isEditMode, currentWarehouseId, warehouses, form]);

  // ---------- Thêm / sửa / xóa dòng hàng ----------

  /**
   * Thêm một sản phẩm vào phiếu.
   *
   * Nếu sản phẩm đã có trên phiếu thì TĂNG số lượng thêm 1 thay vì tạo dòng trùng -
   * đây là hành vi mong đợi khi nhân viên quét liên tục nhiều lần một mặt hàng.
   */
  const addProduct = useCallback(
    (product: ProductLookupResponse, quantityToAdd = 1) => {
      setLines((current) => {
        const existingIndex = current.findIndex((line) => line.productId === product.id);
        if (existingIndex >= 0) {
          const updated = [...current];
          updated[existingIndex] = {
            ...updated[existingIndex],
            quantity: updated[existingIndex].quantity + quantityToAdd,
          };
          message.success(`Tăng số lượng "${product.name}" lên ${updated[existingIndex].quantity}.`);
          return updated;
        }

        message.success(`Đã thêm "${product.name}" vào phiếu.`);
        return [
          ...current,
          {
            key: `new-${product.id}-${Date.now()}`,
            productId: product.id,
            sku: product.sku,
            productName: product.name,
            unitName: product.unitName,
            quantity: quantityToAdd,
            // Phiếu nhập điền sẵn giá nhập tham chiếu để người dùng đỡ phải gõ
            unitPrice: isReceipt ? product.purchasePrice : 0,
            quantityOnHand: product.quantityOnHand,
            note: undefined,
          },
        ];
      });
    },
    [isReceipt, message],
  );

  /** Xử lý mã vạch quét được: tra sản phẩm rồi thêm vào phiếu. */
  const scanMutation = useMutation({
    mutationFn: (code: string) => productApi.findByBarcode(code, selectedWarehouseId),
    onSuccess: (product) => addProduct(product),
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const updateLine = (key: string, changes: Partial<DocumentFormLine>) => {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...changes } : line)));
  };

  const removeLine = (key: string) => {
    setLines((current) => current.filter((line) => line.key !== key));
  };

  // ---------- Tính tổng ----------
  const totalAmount = useMemo(
    () => lines.reduce((sum, line) => sum + line.quantity * line.unitPrice, 0),
    [lines],
  );

  const totalQuantity = useMemo(() => lines.reduce((sum, line) => sum + line.quantity, 0), [lines]);

  /** Các dòng xuất vượt tồn - dùng để tô đỏ và cảnh báo trước khi gửi duyệt. */
  const overStockLines = useMemo(
    () => (isReceipt ? [] : lines.filter((line) => line.quantity > line.quantityOnHand)),
    [isReceipt, lines],
  );

  // ---------- Lưu phiếu ----------
  const buildPayload = (values: HeaderFormValues): DocumentPayload => ({
    warehouseId: values.warehouseId,
    documentType: values.documentType,
    documentDate: values.documentDate.format(API_DATE_FORMAT),
    supplierId: rules.supplierRequired ? values.supplierId : null,
    customerId: rules.customerRequired ? values.customerId : null,
    reason: values.reason,
    note: values.note,
    items: lines.map((line) => ({
      productId: line.productId,
      quantity: line.quantity,
      unitPrice: isReceipt ? line.unitPrice : undefined,
      note: line.note,
    })),
  });

  const saveMutation = useMutation({
    mutationFn: ({ values, submit }: { values: HeaderFormValues; submit: boolean }) => {
      const payload = buildPayload(values);
      return isEditMode
        ? updateDocument(documentId as number, payload)
        : createDocument(payload, submit);
    },
    onSuccess: (saved, variables) => {
      message.success(
        variables.submit ? `Đã lập và gửi duyệt phiếu ${saved.code}.` : `Đã lưu phiếu ${saved.code}.`,
      );
      navigate(`/${basePath}/${saved.id}`);
    },
    onError: (error) => {
      // Lỗi xuất vượt tồn có danh sách sản phẩm thiếu: hiển thị thành bảng cho dễ đọc
      const shortages = getStockShortages(error);
      if (shortages.length > 0) {
        modal.error({
          title: 'Không đủ tồn kho để xuất',
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
                    title: 'Yêu cầu',
                    dataIndex: 'requested',
                    width: 90,
                    align: 'right',
                    render: (value: number) => formatQuantity(value),
                  },
                  {
                    title: 'Còn lại',
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
              <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 10 }}>
                Vui lòng giảm số lượng hoặc nhập thêm hàng trước khi xuất.
              </Text>
            </div>
          ),
        });
        return;
      }
      message.error(getErrorMessage(error));
    },
  });

  const handleSave = (submit: boolean) => {
    form
      .validateFields()
      .then((values) => {
        if (lines.length === 0) {
          message.warning('Phiếu phải có ít nhất một dòng hàng.');
          return;
        }
        if (lines.some((line) => line.quantity <= 0)) {
          message.warning('Số lượng của mọi dòng phải lớn hơn 0.');
          return;
        }

        // Gửi duyệt phiếu xuất khi còn dòng vượt tồn: cảnh báo trước để người dùng sửa
        if (submit && overStockLines.length > 0) {
          modal.confirm({
            title: 'Có dòng vượt tồn kho',
            content: `Phiếu có ${overStockLines.length} dòng vượt tồn hiện có. Hệ thống sẽ chặn khi gửi duyệt. Bạn vẫn muốn thử gửi?`,
            okText: 'Vẫn gửi duyệt',
            cancelText: 'Để tôi sửa lại',
            onOk: () => saveMutation.mutate({ values, submit }),
          });
          return;
        }

        saveMutation.mutate({ values, submit });
      })
      .catch(() => message.warning('Vui lòng kiểm tra lại các trường được đánh dấu.'));
  };

  // ---------- Cột của bảng dòng hàng ----------
  const columns: ColumnsType<DocumentFormLine> = [
    {
      title: 'STT',
      key: 'index',
      width: 56,
      align: 'center',
      render: (_, __, index) => index + 1,
    },
    {
      title: 'Sản phẩm',
      key: 'product',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.productName}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.sku} · {record.unitName}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Tồn hiện có',
      dataIndex: 'quantityOnHand',
      width: 110,
      align: 'right',
      render: (value: number) => (
        <Text type={value <= 0 ? 'danger' : 'secondary'}>{formatQuantity(value)}</Text>
      ),
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      width: 130,
      render: (value: number, record) => (
        <InputNumber<number>
          value={value}
          min={0.001}
          step={1}
          precision={3}
          style={{ width: '100%' }}
          // Tô đỏ ô nhập ngay khi vượt tồn, không cần chờ bấm Lưu
          status={!isReceipt && value > record.quantityOnHand ? 'error' : undefined}
          onChange={(next) => updateLine(record.key, { quantity: next ?? 0 })}
        />
      ),
    },
  ];

  // Phiếu nhập có cột đơn giá và thành tiền; phiếu xuất dùng giá vốn nên không nhập giá
  if (isReceipt) {
    columns.push(
      {
        title: 'Đơn giá',
        dataIndex: 'unitPrice',
        width: 150,
        render: (value: number, record) => (
          <InputNumber<number>
            value={value}
            min={0}
            step={1000}
            style={{ width: '100%' }}
            formatter={(amount) => (amount ? `${amount}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.') : '')}
            parser={(amount) => Number((amount ?? '').replace(/\./g, '')) || 0}
            onChange={(next) => updateLine(record.key, { unitPrice: next ?? 0 })}
          />
        ),
      },
      {
        title: 'Thành tiền',
        key: 'amount',
        width: 140,
        align: 'right',
        render: (_, record) => <Text strong>{formatCurrency(record.quantity * record.unitPrice)}</Text>,
      },
    );
  }

  columns.push(
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      width: 180,
      responsive: ['xl'],
      render: (value?: string, record?: DocumentFormLine) => (
        <Input
          value={value}
          placeholder="Ghi chú dòng"
          maxLength={500}
          onChange={(event) => record && updateLine(record.key, { note: event.target.value })}
        />
      ),
    },
    {
      title: '',
      key: 'remove',
      width: 56,
      fixed: 'right',
      render: (_, record) => (
        <Tooltip title="Xóa dòng">
          <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeLine(record.key)} />
        </Tooltip>
      ),
    },
  );

  if (isEditMode && isLoadingDocument) {
    return (
      <LoadingBlock message="Đang tải phiếu..." />
    );
  }

  // Phiếu đã gửi duyệt hoặc hoàn tất thì không cho sửa
  if (isEditMode && existingDocument && !existingDocument.canEdit) {
    return (
      <>
        <PageHeader title={`Sửa phiếu ${existingDocument.code}`} />
        <Alert
          type="warning"
          showIcon
          message="Không thể sửa phiếu này"
          description={`Phiếu đang ở trạng thái "${existingDocument.statusName}". Chỉ phiếu Nháp hoặc Bị từ chối, và do chính bạn lập, mới sửa được.`}
          action={
            <Button type="primary" onClick={() => navigate(`/${basePath}/${existingDocument.id}`)}>
              Xem chi tiết phiếu
            </Button>
          }
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={
          isEditMode
            ? `Sửa phiếu ${existingDocument?.code ?? ''}`
            : isReceipt
              ? 'Lập phiếu nhập kho'
              : 'Lập phiếu xuất kho'
        }
        description={
          isReceipt
            ? 'Phiếu chỉ tác động tồn kho sau khi được quản lý duyệt.'
            : 'Hệ thống không cho xuất vượt tồn. Giá trị xuất tính theo giá vốn tại thời điểm duyệt.'
        }
        extra={
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/${basePath}`)}>
            Quay lại
          </Button>
        }
      />

      <Form<HeaderFormValues>
        form={form}
        layout="vertical"
        initialValues={{ documentType: defaultType, documentDate: dayjs() }}
      >
        {/* ---------- Thông tin phiếu ---------- */}
        <Card size="small" title="Thông tin phiếu">
          <Row gutter={12}>
            <Col xs={24} sm={12} md={6}>
              <Form.Item
                name="warehouseId"
                label="Kho"
                rules={[{ required: true, message: 'Vui lòng chọn kho' }]}
              >
                <Select
                  placeholder="Chọn kho"
                  options={warehouses.map((warehouse) => ({ value: warehouse.id, label: warehouse.name }))}
                  // Đổi kho giữa lúc đang lập phiếu sẽ làm tồn hiện có của các dòng sai lệch
                  onChange={() => {
                    if (lines.length > 0) {
                      message.info('Đã đổi kho. Vui lòng kiểm tra lại tồn hiện có của các dòng hàng.');
                    }
                  }}
                />
              </Form.Item>
            </Col>

            <Col xs={24} sm={12} md={6}>
              <Form.Item
                name="documentType"
                label={typeLabel}
                rules={[{ required: true, message: `Vui lòng chọn ${typeLabel.toLowerCase()}` }]}
              >
                <Select options={typeOptions} />
              </Form.Item>
            </Col>

            <Col xs={24} sm={12} md={6}>
              <Form.Item
                name="documentDate"
                label="Ngày chứng từ"
                rules={[{ required: true, message: 'Vui lòng chọn ngày chứng từ' }]}
              >
                <DatePicker
                  style={{ width: '100%' }}
                  format="DD/MM/YYYY"
                  // Không cho chọn ngày trong tương lai, khớp kiểm tra của backend
                  disabledDate={(current) => current && current > dayjs().endOf('day')}
                />
              </Form.Item>
            </Col>

            {/* Ô chọn đối tác hiện theo loại phiếu đang chọn */}
            {rules.supplierRequired && (
              <Col xs={24} sm={12} md={6}>
                <Form.Item
                  name="supplierId"
                  label="Nhà cung cấp"
                  rules={[{ required: true, message: 'Vui lòng chọn nhà cung cấp' }]}
                >
                  <Select
                    placeholder="Chọn nhà cung cấp"
                    showSearch
                    optionFilterProp="label"
                    options={suppliers.map((supplier) => ({
                      value: supplier.id,
                      label: `${supplier.code} - ${supplier.name}`,
                    }))}
                  />
                </Form.Item>
              </Col>
            )}

            {rules.customerRequired && (
              <Col xs={24} sm={12} md={6}>
                <Form.Item
                  name="customerId"
                  label="Khách hàng"
                  rules={[{ required: true, message: 'Vui lòng chọn khách hàng' }]}
                >
                  <Select
                    placeholder="Chọn khách hàng"
                    showSearch
                    optionFilterProp="label"
                    options={customers.map((customer) => ({
                      value: customer.id,
                      label: `${customer.code} - ${customer.name}`,
                    }))}
                  />
                </Form.Item>
              </Col>
            )}
          </Row>

          <Row gutter={12}>
            {rules.reasonRequired && (
              <Col xs={24} md={12}>
                <Form.Item
                  name="reason"
                  label="Lý do"
                  rules={[
                    { required: true, message: 'Loại phiếu này bắt buộc ghi lý do' },
                    { max: 500, message: 'Lý do tối đa 500 ký tự' },
                  ]}
                >
                  <Input placeholder="Nêu rõ lý do để người duyệt hiểu bối cảnh" />
                </Form.Item>
              </Col>
            )}

            <Col xs={24} md={rules.reasonRequired ? 12 : 24}>
              <Form.Item name="note" label="Ghi chú" rules={[{ max: 1000 }]}>
                <Input placeholder="Ghi chú thêm (không bắt buộc)" />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        {/* ---------- Danh sách hàng ---------- */}
        <Card
          size="small"
          title="Danh sách hàng"
          style={{ marginTop: 12 }}
          extra={
            <Space wrap>
              <Select<number>
                placeholder="Tìm sản phẩm theo mã, tên hoặc mã vạch..."
                // minWidth cố định 300px làm tràn ngang trên màn hình 390px (ô + nút Quét mã
                // rộng hơn cả khung nhìn). Dùng clamp để ô co lại vừa màn hình điện thoại
                // nhưng vẫn đủ rộng trên máy tính.
                style={{ width: 'clamp(180px, 60vw, 320px)' }}
                showSearch
                value={null}
                filterOption={false}
                notFoundContent={isSearchingProducts ? <Spin size="small" /> : 'Không tìm thấy sản phẩm'}
                onSearch={setProductSearchText}
                onChange={(productId) => {
                  const product = productOptions.find((item) => item.id === productId);
                  if (product) {
                    addProduct(product);
                  }
                }}
                options={productOptions.map((product) => ({
                  value: product.id,
                  label: `${product.sku} - ${product.name} (tồn: ${formatQuantity(product.quantityOnHand)})`,
                }))}
                suffixIcon={<PlusOutlined />}
                disabled={!selectedWarehouseId}
              />

              <Button
                icon={<BarcodeOutlined />}
                onClick={() => setIsScannerOpen(true)}
                disabled={!selectedWarehouseId}
                loading={scanMutation.isPending}
              >
                Quét mã
              </Button>
            </Space>
          }
        >
          {!selectedWarehouseId && (
            <Alert
              type="info"
              showIcon
              message="Vui lòng chọn kho trước khi thêm sản phẩm"
              style={{ marginBottom: 12 }}
            />
          )}

          {/* Cảnh báo vượt tồn ngay trên màn hình, trước khi người dùng bấm gửi duyệt */}
          {overStockLines.length > 0 && (
            <Alert
              type="error"
              showIcon
              style={{ marginBottom: 12 }}
              message={`${overStockLines.length} dòng có số lượng vượt tồn hiện có`}
              description={
                <Space direction="vertical" size={2}>
                  {overStockLines.map((line) => (
                    <Text key={line.key} style={{ fontSize: 13 }}>
                      {line.sku} - {line.productName}: cần {formatQuantity(line.quantity)}, chỉ còn{' '}
                      <Text type="danger" strong>
                        {formatQuantity(line.quantityOnHand)}
                      </Text>{' '}
                      {line.unitName}
                    </Text>
                  ))}
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Bạn vẫn lưu được phiếu nháp, nhưng hệ thống sẽ chặn khi gửi duyệt.
                  </Text>
                </Space>
              }
            />
          )}

          <Table<DocumentFormLine>
            rowKey="key"
            columns={columns}
            dataSource={lines}
            size="small"
            pagination={false}
            scroll={{ x: 'max-content' }}
            // Tô đỏ dòng xuất vượt tồn
            rowClassName={(record) =>
              !isReceipt && record.quantity > record.quantityOnHand ? 'row-danger' : ''
            }
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="Chưa có dòng hàng nào. Dùng ô tìm sản phẩm hoặc bấm Quét mã để thêm."
                />
              ),
            }}
          />

          {lines.length > 0 && (
            <>
              <Divider style={{ margin: '12px 0' }} />
              <Row justify="end">
                <Col xs={24} sm={12} md={8}>
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    <Row justify="space-between">
                      <Text type="secondary">Số dòng hàng:</Text>
                      <Text strong>{lines.length}</Text>
                    </Row>
                    <Row justify="space-between">
                      <Text type="secondary">Tổng số lượng:</Text>
                      <Text strong>{formatQuantity(totalQuantity)}</Text>
                    </Row>
                    {isReceipt && (
                      <Row justify="space-between" align="middle">
                        <Text type="secondary">Tổng tiền:</Text>
                        <Title level={4} style={{ margin: 0, color: '#1677ff' }}>
                          {formatCurrency(totalAmount)}
                        </Title>
                      </Row>
                    )}
                  </Space>
                </Col>
              </Row>
            </>
          )}
        </Card>

        {/* ---------- Nút lưu ---------- */}
        <Card size="small" style={{ marginTop: 12 }}>
          <Space wrap>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saveMutation.isPending}
              onClick={() => handleSave(false)}
            >
              Lưu nháp
            </Button>
            <Button
              type="primary"
              ghost
              icon={<SendOutlined />}
              loading={saveMutation.isPending}
              onClick={() => handleSave(true)}
            >
              Lưu và gửi duyệt
            </Button>
            <Button onClick={() => navigate(`/${basePath}`)}>Hủy</Button>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Phiếu nháp chưa ảnh hưởng tồn kho. Chỉ khi quản lý duyệt, tồn kho mới thay đổi.
            </Text>
          </Space>
        </Card>
      </Form>

      <BarcodeScanner
        open={isScannerOpen}
        onClose={() => setIsScannerOpen(false)}
        onScan={(code) => scanMutation.mutate(code)}
      />
    </>
  );
}
