import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeftOutlined, BarcodeOutlined, EditOutlined, FileTextOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Image,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import MoneyText from '@/components/MoneyText';
import { productApi } from '@/api/endpoints';
import { useAuth } from '@/contexts/AuthContext';
import { formatCurrency, formatDateTime, formatQuantity } from '@/utils/format';
import type { ProductStockRow } from '@/types';
import LoadingBlock from '../components/LoadingBlock';

const { Text } = Typography;

/**
 * Trang CHI TIẾT SẢN PHẨM.
 *
 * Hiển thị thông tin sản phẩm và tồn kho ở TỪNG KHO mà người dùng được phép xem.
 * Vai trò STAFF không nhận được giá vốn và giá trị tồn (backend trả null), nên các ô đó
 * hiện dấu gạch thay vì số 0.
 */
export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const productId = Number(id);
  const navigate = useNavigate();
  const { canEditCatalog, canViewCost } = useAuth();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['products', productId, 'detail'],
    queryFn: () => productApi.getDetail(productId),
    enabled: Number.isFinite(productId),
  });

  if (isLoading) {
    return (
      <LoadingBlock message="Đang tải chi tiết sản phẩm..." />
    );
  }

  if (isError || !data) {
    return (
      <>
        <PageHeader title="Chi tiết sản phẩm" />
        <Alert type="error" showIcon message="Không tải được dữ liệu" description={(error as Error)?.message} />
      </>
    );
  }

  const { product, stocks, totalQuantity } = data;

  const stockColumns: ColumnsType<ProductStockRow> = [
    {
      title: 'Kho',
      dataIndex: 'warehouseName',
      render: (name: string, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.warehouseCode}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Tồn kho',
      dataIndex: 'quantity',
      align: 'right',
      width: 140,
      render: (quantity: number) => {
        const isLow = product.minStock > 0 && quantity <= product.minStock;
        return (
          <Text strong style={{ color: quantity <= 0 ? '#cf1322' : isLow ? '#d46b08' : undefined }}>
            {formatQuantity(quantity)} {product.unitName}
          </Text>
        );
      },
    },
    {
      title: 'Giá vốn',
      dataIndex: 'avgCost',
      align: 'right',
      width: 130,
      render: (value?: number | null) => <MoneyText value={value} />,
    },
    {
      title: 'Giá trị tồn',
      dataIndex: 'stockValue',
      align: 'right',
      width: 150,
      render: (value?: number | null) => <MoneyText value={value} strong />,
    },
    {
      title: 'Thẻ kho',
      key: 'stockCard',
      width: 110,
      render: (_, record) => (
        <Link to={`/stock-card?productId=${product.id}&warehouseId=${record.warehouseId}`}>
          <Button size="small" icon={<FileTextOutlined />}>
            Xem
          </Button>
        </Link>
      ),
    },
  ];

  const totalValue = stocks.reduce((sum, row) => sum + (row.stockValue ?? 0), 0);

  return (
    <>
      <PageHeader
        title={product.name}
        description={`Mã SKU: ${product.sku}${product.barcode ? ` | Mã vạch: ${product.barcode}` : ''}`}
        extra={
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/products')}>
              Quay lại
            </Button>
            {product.barcode && (
              <Link to={`/barcode-labels?productId=${product.id}`}>
                <Button icon={<BarcodeOutlined />}>In tem</Button>
              </Link>
            )}
            {canEditCatalog && (
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={() => navigate(`/products/${product.id}/edit`)}
              >
                Sửa sản phẩm
              </Button>
            )}
          </Space>
        }
      />

      <Row gutter={[12, 12]}>
        <Col xs={24} lg={8}>
          <Card size="small" title="Thông tin sản phẩm">
            {product.imageUrl && (
              <div style={{ textAlign: 'center', marginBottom: 12 }}>
                <Image src={product.imageUrl} style={{ maxHeight: 180, objectFit: 'contain' }} />
              </div>
            )}

            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Mã SKU">{product.sku}</Descriptions.Item>
              <Descriptions.Item label="Mã vạch">{product.barcode ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Loại hàng">{product.categoryName}</Descriptions.Item>
              <Descriptions.Item label="Đơn vị tính">{product.unitName}</Descriptions.Item>
              <Descriptions.Item label="Giá nhập tham chiếu">
                {formatCurrency(product.purchasePrice)}
              </Descriptions.Item>
              <Descriptions.Item label="Giá bán">{formatCurrency(product.salePrice)}</Descriptions.Item>
              <Descriptions.Item label="Mức tồn tối thiểu">
                {formatQuantity(product.minStock)} {product.unitName}
              </Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <Tag color={product.status === 'ACTIVE' ? 'green' : 'default'}>{product.statusName}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Ngày tạo">{formatDateTime(product.createdAt)}</Descriptions.Item>
              {product.description && (
                <Descriptions.Item label="Mô tả">{product.description}</Descriptions.Item>
              )}
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={16}>
          <Row gutter={[12, 12]}>
            <Col xs={12} sm={canViewCost ? 8 : 12}>
              <Card size="small">
                <Statistic
                  title="Tổng tồn các kho"
                  value={formatQuantity(totalQuantity)}
                  suffix={product.unitName}
                />
              </Card>
            </Col>
            <Col xs={12} sm={canViewCost ? 8 : 12}>
              <Card size="small">
                <Statistic title="Số kho đang có hàng" value={stocks.filter((row) => row.quantity > 0).length} />
              </Card>
            </Col>
            {canViewCost && (
              <Col xs={24} sm={8}>
                <Card size="small">
                  <Statistic title="Tổng giá trị tồn" value={formatCurrency(totalValue)} />
                </Card>
              </Col>
            )}
          </Row>

          <Card size="small" title="Tồn kho theo từng kho" style={{ marginTop: 12 }}>
            {stocks.length === 0 ? (
              <Alert
                type="info"
                showIcon
                message="Sản phẩm chưa có tồn kho"
                description="Sản phẩm này chưa từng được nhập vào kho nào mà bạn được phép xem."
              />
            ) : (
              <Table<ProductStockRow>
                rowKey="warehouseId"
                columns={stockColumns}
                dataSource={stocks}
                size="small"
                pagination={false}
                scroll={{ x: 'max-content' }}
                rowClassName={(record) => {
                  if (record.quantity <= 0) {
                    return 'row-danger';
                  }
                  return product.minStock > 0 && record.quantity <= product.minStock ? 'row-warning' : '';
                }}
              />
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
