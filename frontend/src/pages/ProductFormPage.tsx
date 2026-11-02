import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeftOutlined, SaveOutlined, UploadOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Col,
  Form,
  Image,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Typography,
  Upload,
} from 'antd';
import type { UploadProps } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { categoryApi, fileApi, productApi, unitApi, type SaveProductRequest } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { applyServerFieldErrors } from '@/utils/form';
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES } from '@/utils/constants';
import LoadingBlock from '../components/LoadingBlock';

const { Text } = Typography;

/**
 * Trang THÊM / SỬA SẢN PHẨM.
 *
 * Dùng chung cho cả hai việc: có `id` trên URL là sửa, không có là thêm mới.
 *
 * Ảnh sản phẩm được tải lên TRƯỚC khi lưu biểu mẫu (hai bước riêng), nhờ vậy người dùng
 * xem được ảnh ngay và nếu đổi ý thì chỉ cần không bấm Lưu.
 */
export default function ProductFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEditMode = Boolean(id);
  const productId = id ? Number(id) : undefined;

  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<SaveProductRequest>();
  const [imageUrl, setImageUrl] = useState<string | undefined>();
  const [isUploading, setIsUploading] = useState(false);

  const { data: categories = [] } = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: categoryApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  const { data: units = [] } = useQuery({
    queryKey: ['units', 'all'],
    queryFn: unitApi.listAll,
    staleTime: 5 * 60 * 1000,
  });

  const { data: product, isLoading } = useQuery({
    queryKey: ['products', productId],
    queryFn: () => productApi.getById(productId as number),
    enabled: isEditMode,
  });

  // Nạp dữ liệu sản phẩm vào biểu mẫu khi ở chế độ sửa
  useEffect(() => {
    if (product) {
      form.setFieldsValue({
        sku: product.sku,
        barcode: product.barcode ?? '',
        name: product.name,
        categoryId: product.categoryId,
        unitId: product.unitId,
        purchasePrice: product.purchasePrice,
        salePrice: product.salePrice,
        minStock: product.minStock,
        description: product.description,
        status: product.status,
      });
      setImageUrl(product.imageUrl);
    }
  }, [product, form]);

  const saveMutation = useMutation({
    mutationFn: (values: SaveProductRequest) => {
      const payload: SaveProductRequest = { ...values, imageUrl };
      return isEditMode ? productApi.update(productId as number, payload) : productApi.create(payload);
    },
    onSuccess: (saved) => {
      message.success(isEditMode ? 'Đã cập nhật sản phẩm.' : 'Đã thêm sản phẩm mới.');
      void queryClient.invalidateQueries({ queryKey: ['products'] });
      navigate(`/products/${saved.id}`);
    },
    onError: (error) => {
      // Hiển thị lỗi theo từng trường cạnh ô nhập (ví dụ trùng mã SKU hoặc mã vạch)
      applyServerFieldErrors(form, error);
      message.error(getErrorMessage(error));
    },
  });

  /**
   * Kiểm tra ảnh trước khi tải lên.
   *
   * Kiểm tra ở giao diện giúp người dùng biết lỗi ngay, nhưng backend vẫn kiểm tra lại
   * (cả MIME lẫn chữ ký nội dung file) nên không thể lách qua đây.
   */
  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    const isAcceptedType = ACCEPTED_IMAGE_TYPES.split(',').includes(file.type);
    if (!isAcceptedType) {
      message.error('Chỉ chấp nhận ảnh định dạng JPG, PNG hoặc WebP.');
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
      message.error('Ảnh vượt quá 2 MB. Vui lòng chọn ảnh nhỏ hơn.');
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const handleUpload: UploadProps['customRequest'] = async (options) => {
    setIsUploading(true);
    try {
      const result = await fileApi.upload(options.file as File, 'products');
      setImageUrl(result.url);
      message.success('Đã tải ảnh lên.');
      options.onSuccess?.(result);
    } catch (error) {
      message.error(getErrorMessage(error));
      options.onError?.(error as Error);
    } finally {
      setIsUploading(false);
    }
  };

  if (isEditMode && isLoading) {
    return (
      <LoadingBlock message="Đang tải dữ liệu sản phẩm..." />
    );
  }

  return (
    <>
      <PageHeader
        title={isEditMode ? `Sửa sản phẩm ${product?.sku ?? ''}` : 'Thêm sản phẩm'}
        description="Mã SKU và mã vạch phải duy nhất trong hệ thống."
        extra={
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/products')}>
            Quay lại danh sách
          </Button>
        }
      />

      <Form<SaveProductRequest>
        form={form}
        layout="vertical"
        onFinish={(values) => saveMutation.mutate(values)}
        initialValues={{ purchasePrice: 0, salePrice: 0, minStock: 0, status: 'ACTIVE' }}
      >
        <Row gutter={12}>
          <Col xs={24} lg={16}>
            <Card size="small" title="Thông tin cơ bản">
              <Row gutter={12}>
                <Col xs={24} sm={8}>
                  <Form.Item
                    name="sku"
                    label="Mã SKU"
                    rules={[
                      { required: true, message: 'Vui lòng nhập mã sản phẩm' },
                      { max: 50, message: 'Mã tối đa 50 ký tự' },
                      {
                        pattern: /^[A-Za-z0-9_.-]+$/,
                        message: 'Mã chỉ gồm chữ không dấu, số và các ký tự . _ -',
                      },
                    ]}
                  >
                    <Input placeholder="SP0001" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item
                    name="barcode"
                    label="Mã vạch (EAN-13)"
                    tooltip="Để trống nếu sản phẩm chưa có mã vạch. Nếu có thì phải là dãy 8-14 chữ số và không trùng sản phẩm khác."
                    rules={[{ pattern: /^$|^[0-9]{8,14}$/, message: 'Mã vạch phải là dãy 8 đến 14 chữ số' }]}
                  >
                    <Input placeholder="8930000000019" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item name="status" label="Trạng thái kinh doanh">
                    <Select
                      options={[
                        { value: 'ACTIVE', label: 'Đang kinh doanh' },
                        { value: 'DISCONTINUED', label: 'Ngừng kinh doanh' },
                      ]}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                name="name"
                label="Tên sản phẩm"
                rules={[
                  { required: true, message: 'Vui lòng nhập tên sản phẩm' },
                  { max: 255, message: 'Tên tối đa 255 ký tự' },
                ]}
              >
                <Input placeholder="Ví dụ: Nước ngọt Coca-Cola 330ml (thùng 24 lon)" />
              </Form.Item>

              <Row gutter={12}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="categoryId"
                    label="Loại hàng"
                    rules={[{ required: true, message: 'Vui lòng chọn loại hàng' }]}
                  >
                    <Select
                      placeholder="Chọn loại hàng"
                      showSearch
                      optionFilterProp="label"
                      options={categories.map((category) => ({ value: category.id, label: category.name }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="unitId"
                    label="Đơn vị tính"
                    rules={[{ required: true, message: 'Vui lòng chọn đơn vị tính' }]}
                  >
                    <Select
                      placeholder="Chọn đơn vị tính"
                      showSearch
                      optionFilterProp="label"
                      options={units.map((unit) => ({ value: unit.id, label: unit.name }))}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                name="description"
                label="Mô tả"
                rules={[{ max: 1000, message: 'Mô tả tối đa 1000 ký tự' }]}
              >
                <Input.TextArea rows={3} placeholder="Thông tin thêm về sản phẩm (không bắt buộc)" />
              </Form.Item>
            </Card>

            <Card size="small" title="Giá và mức tồn" style={{ marginTop: 12 }}>
              <Row gutter={12}>
                <Col xs={24} sm={8}>
                  <Form.Item
                    name="purchasePrice"
                    label="Giá nhập tham chiếu"
                    tooltip="Dùng để điền sẵn đơn giá khi lập phiếu nhập. Giá vốn thực tế được hệ thống tự tính theo bình quân gia quyền của từng kho."
                    rules={[{ required: true, message: 'Vui lòng nhập giá nhập' }]}
                  >
                    <InputNumber<number>
                      style={{ width: '100%' }}
                      min={0}
                      step={1000}
                      // suffix thay cho addonAfter: antd đã đánh dấu addonAfter của InputNumber là lỗi thời
                      suffix="₫"
                      // Hiển thị phân cách nghìn theo kiểu Việt Nam ngay trong ô nhập
                      formatter={(value) => (value ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.') : '')}
                      parser={(value) => Number((value ?? '').replace(/\./g, '')) || 0}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item
                    name="salePrice"
                    label="Giá bán"
                    rules={[{ required: true, message: 'Vui lòng nhập giá bán' }]}
                  >
                    <InputNumber<number>
                      style={{ width: '100%' }}
                      min={0}
                      step={1000}
                      // suffix thay cho addonAfter: antd đã đánh dấu addonAfter của InputNumber là lỗi thời
                      suffix="₫"
                      formatter={(value) => (value ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.') : '')}
                      parser={(value) => Number((value ?? '').replace(/\./g, '')) || 0}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item
                    name="minStock"
                    label="Mức tồn tối thiểu"
                    tooltip="Khi tồn kho xuống bằng hoặc dưới mức này, hệ thống sinh cảnh báo và đưa sản phẩm vào gợi ý nhập hàng. Để 0 nếu không cần cảnh báo."
                    rules={[{ required: true, message: 'Vui lòng nhập mức tồn tối thiểu' }]}
                  >
                    <InputNumber<number> style={{ width: '100%' }} min={0} step={1} precision={3} />
                  </Form.Item>
                </Col>
              </Row>
            </Card>
          </Col>

          <Col xs={24} lg={8}>
            <Card size="small" title="Ảnh sản phẩm">
              <Space direction="vertical" size={10} style={{ width: '100%', textAlign: 'center' }}>
                {imageUrl ? (
                  <Image src={imageUrl} style={{ maxHeight: 200, objectFit: 'contain' }} />
                ) : (
                  <div
                    style={{
                      height: 180,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      border: '1px dashed #d9d9d9',
                      borderRadius: 8,
                      color: '#bfbfbf',
                    }}
                  >
                    Chưa có ảnh
                  </div>
                )}

                <Upload
                  accept={ACCEPTED_IMAGE_TYPES}
                  beforeUpload={beforeUpload}
                  customRequest={handleUpload}
                  showUploadList={false}
                  maxCount={1}
                >
                  <Button icon={<UploadOutlined />} loading={isUploading} block>
                    {imageUrl ? 'Đổi ảnh khác' : 'Tải ảnh lên'}
                  </Button>
                </Upload>

                {imageUrl && (
                  <Button size="small" type="text" danger onClick={() => setImageUrl(undefined)}>
                    Bỏ ảnh
                  </Button>
                )}

                <Text type="secondary" style={{ fontSize: 12 }}>
                  JPG, PNG hoặc WebP. Tối đa 2 MB.
                </Text>
              </Space>
            </Card>

            <Card size="small" style={{ marginTop: 12 }}>
              <Space direction="vertical" style={{ width: '100%' }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<SaveOutlined />}
                  loading={saveMutation.isPending}
                  block
                  size="large"
                >
                  {isEditMode ? 'Lưu thay đổi' : 'Thêm sản phẩm'}
                </Button>
                <Button block onClick={() => navigate('/products')}>
                  Hủy
                </Button>
              </Space>
            </Card>
          </Col>
        </Row>
      </Form>
    </>
  );
}
