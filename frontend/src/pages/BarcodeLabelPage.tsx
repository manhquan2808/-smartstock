import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PrinterOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  InputNumber,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import JsBarcode from 'jsbarcode';
import QRCode from 'qrcode';
import PageHeader from '@/components/PageHeader';
import { productApi } from '@/api/endpoints';
import { formatCurrency } from '@/utils/format';
import type { ProductResponse } from '@/types';

const { Text, Title } = Typography;

/** Một tem cần in: sản phẩm và số lượng tem. */
interface LabelSelection {
  product: ProductResponse;
  copies: number;
}

/** Kiểu mã in trên tem. */
type LabelFormat = 'barcode' | 'qrcode';

/**
 * Trang IN TEM MÃ VẠCH.
 *
 * Chọn nhiều sản phẩm, đặt số lượng tem cho từng sản phẩm, rồi in ra trang A4 dạng lưới
 * 3 cột. Dùng để dán tem lên hàng hóa chưa có mã vạch của nhà sản xuất.
 *
 * Về kỹ thuật in: tem được vẽ trực tiếp trên trang bằng thư viện JsBarcode (mã Code 128)
 * hoặc qrcode (mã QR), sau đó dùng chức năng in của trình duyệt. Phần CSS `@media print`
 * trong `global.css` lo việc ẩn menu, chia lưới và chống cắt tem giữa hai trang.
 */
export default function BarcodeLabelPage() {
  const { message } = App.useApp();
  const [searchParams] = useSearchParams();
  const printAreaRef = useRef<HTMLDivElement>(null);

  const [selections, setSelections] = useState<LabelSelection[]>([]);
  const [labelFormat, setLabelFormat] = useState<LabelFormat>('barcode');
  const [showPrice, setShowPrice] = useState(true);
  const [searchText, setSearchText] = useState('');

  const { data: searchResult, isFetching } = useQuery({
    queryKey: ['products', 'label-search', searchText],
    queryFn: () => productApi.search({ keyword: searchText || undefined, status: 'ACTIVE', size: 20 }),
  });

  /** Nếu mở trang từ nút "In tem" của một sản phẩm, tự chọn sẵn sản phẩm đó. */
  const initialProductId = searchParams.get('productId');
  const { data: initialProduct } = useQuery({
    queryKey: ['products', initialProductId],
    queryFn: () => productApi.getById(Number(initialProductId)),
    enabled: Boolean(initialProductId),
  });

  useEffect(() => {
    if (initialProduct) {
      setSelections((current) =>
        current.some((item) => item.product.id === initialProduct.id)
          ? current
          : [...current, { product: initialProduct, copies: 1 }],
      );
    }
  }, [initialProduct]);

  /** Danh sách tem đã được "nhân bản" theo số lượng bản in. */
  const labelsToRender = useMemo(
    () =>
      selections.flatMap((selection) =>
        Array.from({ length: selection.copies }, (_, index) => ({
          key: `${selection.product.id}-${index}`,
          product: selection.product,
        })),
      ),
    [selections],
  );

  /**
   * Vẽ mã vạch / mã QR cho toàn bộ tem sau khi danh sách thay đổi.
   *
   * Phải vẽ sau khi React dựng xong DOM, vì hai thư viện này cần phần tử thật
   * (`<svg>` hoặc `<canvas>`) để vẽ vào.
   */
  useEffect(() => {
    labelsToRender.forEach((label) => {
      const code = label.product.barcode ?? label.product.sku;

      if (labelFormat === 'barcode') {
        const element = document.getElementById(`barcode-${label.key}`);
        if (element) {
          try {
            JsBarcode(element, code, {
              format: 'CODE128',
              width: 1.6,
              height: 42,
              displayValue: true,
              fontSize: 12,
              margin: 2,
              textMargin: 1,
            });
          } catch {
            // Mã không hợp lệ với Code 128 (ví dụ chứa ký tự lạ): bỏ qua tem này
          }
        }
        return;
      }

      const canvas = document.getElementById(`qrcode-${label.key}`) as HTMLCanvasElement | null;
      if (canvas) {
        void QRCode.toCanvas(canvas, code, { width: 80, margin: 1 });
      }
    });
  }, [labelsToRender, labelFormat]);

  const addProduct = (productId: number) => {
    const product = searchResult?.content.find((item) => item.id === productId);
    if (!product) {
      return;
    }
    if (selections.some((item) => item.product.id === productId)) {
      message.info('Sản phẩm này đã có trong danh sách in.');
      return;
    }
    setSelections((current) => [...current, { product, copies: 1 }]);
  };

  const updateCopies = (productId: number, copies: number) => {
    setSelections((current) =>
      current.map((item) => (item.product.id === productId ? { ...item, copies } : item)),
    );
  };

  const removeProduct = (productId: number) => {
    setSelections((current) => current.filter((item) => item.product.id !== productId));
  };

  const handlePrint = () => {
    if (labelsToRender.length === 0) {
      message.warning('Vui lòng chọn ít nhất một sản phẩm để in tem.');
      return;
    }
    // Gọi hộp thoại in của trình duyệt; CSS @media print lo phần bố cục trang A4
    window.print();
  };

  const columns: ColumnsType<LabelSelection> = [
    {
      title: 'Sản phẩm',
      key: 'product',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.product.name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.product.sku}
            {record.product.barcode ? ` · ${record.product.barcode}` : ' · chưa có mã vạch, dùng mã SKU'}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Số tem',
      key: 'copies',
      width: 130,
      render: (_, record) => (
        <InputNumber
          min={1}
          max={200}
          value={record.copies}
          onChange={(value) => updateCopies(record.product.id, value ?? 1)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '',
      key: 'remove',
      width: 80,
      render: (_, record) => (
        <Button size="small" danger onClick={() => removeProduct(record.product.id)}>
          Bỏ
        </Button>
      ),
    },
  ];

  return (
    <>
      {/* Toàn bộ phần điều khiển có lớp no-print nên không xuất hiện trên bản in */}
      <div className="no-print">
        <PageHeader
          title="In tem mã vạch"
          description="Chọn sản phẩm và số lượng tem, hệ thống dựng trang A4 dạng lưới 3 cột để in và dán lên hàng hóa."
          extra={
            <Button
              type="primary"
              icon={<PrinterOutlined />}
              onClick={handlePrint}
              disabled={labelsToRender.length === 0}
            >
              In {labelsToRender.length > 0 ? `${labelsToRender.length} tem` : ''}
            </Button>
          }
        />

        <Row gutter={[12, 12]}>
          <Col xs={24} lg={14}>
            <Card size="small" title="Chọn sản phẩm cần in tem">
              <Select
                placeholder="Tìm sản phẩm theo mã SKU, tên hoặc mã vạch..."
                style={{ width: '100%', marginBottom: 12 }}
                showSearch
                value={null}
                filterOption={false}
                suffixIcon={<SearchOutlined />}
                notFoundContent={isFetching ? <Spin size="small" /> : 'Không tìm thấy sản phẩm'}
                onSearch={setSearchText}
                onChange={addProduct}
                options={(searchResult?.content ?? []).map((product) => ({
                  value: product.id,
                  label: `${product.sku} - ${product.name}`,
                }))}
              />

              <Table<LabelSelection>
                rowKey={(record) => record.product.id}
                columns={columns}
                dataSource={selections}
                size="small"
                pagination={false}
                locale={{ emptyText: 'Chưa chọn sản phẩm nào.' }}
              />

              {selections.length > 0 && (
                <Space style={{ marginTop: 12 }} wrap>
                  <Text type="secondary">
                    Tổng: <Text strong>{labelsToRender.length}</Text> tem, khoảng{' '}
                    <Text strong>{Math.ceil(labelsToRender.length / 24)}</Text> trang A4
                  </Text>
                  <Button size="small" type="link" onClick={() => setSelections([])}>
                    Xóa hết
                  </Button>
                </Space>
              )}
            </Card>
          </Col>

          <Col xs={24} lg={10}>
            <Card size="small" title="Tùy chọn in">
              <Space direction="vertical" size={14} style={{ width: '100%' }}>
                <div>
                  <Text strong style={{ display: 'block', marginBottom: 6 }}>
                    Kiểu mã
                  </Text>
                  <Radio.Group
                    value={labelFormat}
                    onChange={(event) => setLabelFormat(event.target.value as LabelFormat)}
                    optionType="button"
                    buttonStyle="solid"
                  >
                    <Radio.Button value="barcode">Mã vạch (Code 128)</Radio.Button>
                    <Radio.Button value="qrcode">Mã QR</Radio.Button>
                  </Radio.Group>
                </div>

                <div>
                  <Text strong style={{ display: 'block', marginBottom: 6 }}>
                    Thông tin trên tem
                  </Text>
                  <Radio.Group
                    value={showPrice ? 'with-price' : 'no-price'}
                    onChange={(event) => setShowPrice(event.target.value === 'with-price')}
                    optionType="button"
                  >
                    <Radio.Button value="with-price">Có giá bán</Radio.Button>
                    <Radio.Button value="no-price">Không có giá</Radio.Button>
                  </Radio.Group>
                </div>

                <Alert
                  type="info"
                  showIcon
                  message="Mẹo khi in"
                  description={
                    <Space direction="vertical" size={2}>
                      <Text style={{ fontSize: 12 }}>
                        Trong hộp thoại in của trình duyệt, chọn khổ A4 và tỉ lệ 100% (không chọn "Fit to page")
                        để máy quét đọc được mã.
                      </Text>
                      <Text style={{ fontSize: 12 }}>
                        Sản phẩm chưa có mã vạch sẽ in theo mã SKU - vẫn quét được vì hệ thống tra cứu cả hai.
                      </Text>
                    </Space>
                  }
                />
              </Space>
            </Card>
          </Col>
        </Row>
      </div>

      {/* ---------- Vùng in: lưới tem A4 ---------- */}
      {labelsToRender.length > 0 && (
        <Card size="small" title="Xem trước bản in" style={{ marginTop: 12 }} className="print-card">
          <div className="print-area" ref={printAreaRef}>
            <div
              className="label-grid"
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 8,
              }}
            >
              {labelsToRender.map((label) => (
                <div
                  key={label.key}
                  className="label-item"
                  style={{
                    border: '1px dashed #d9d9d9',
                    borderRadius: 4,
                    padding: 6,
                    textAlign: 'center',
                    background: '#fff',
                    color: '#000',
                  }}
                >
                  <div
                    style={{
                      fontSize: 10,
                      fontWeight: 600,
                      marginBottom: 2,
                      // Tên dài hiển thị tối đa 2 dòng để tem không bị xô lệch
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      minHeight: 24,
                      lineHeight: 1.2,
                    }}
                  >
                    {label.product.name}
                  </div>

                  {labelFormat === 'barcode' ? (
                    <svg id={`barcode-${label.key}`} style={{ maxWidth: '100%' }} />
                  ) : (
                    <Space direction="vertical" size={0}>
                      <canvas id={`qrcode-${label.key}`} />
                      <Text style={{ fontSize: 9, color: '#000' }}>
                        {label.product.barcode ?? label.product.sku}
                      </Text>
                    </Space>
                  )}

                  {showPrice && (
                    <div style={{ fontSize: 11, fontWeight: 700, marginTop: 2 }}>
                      {formatCurrency(label.product.salePrice)}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </Card>
      )}

      {labelsToRender.length === 0 && (
        <Card className="no-print" style={{ marginTop: 12 }}>
          <div style={{ textAlign: 'center', padding: 28 }}>
            <Title level={5} type="secondary">
              Chưa có tem nào để in
            </Title>
            <Text type="secondary">Chọn sản phẩm ở khung bên trên để xem trước bản in.</Text>
          </div>
        </Card>
      )}
    </>
  );
}
