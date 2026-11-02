import { useEffect } from 'react';
import { SaveOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Form, Input, InputNumber, Space, Switch, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { settingApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { useAuth } from '@/contexts/AuthContext';
import { formatDateTime } from '@/utils/format';
import type { SettingResponse } from '@/types';

const { Text } = Typography;

/**
 * Trang CẤU HÌNH HỆ THỐNG.
 *
 * Các tham số này ảnh hưởng trực tiếp tới trang Gợi ý nhập hàng và báo cáo hàng tồn lâu,
 * nên mọi vai trò đều ĐỌC được (để hiểu con số mình đang xem được tính thế nào), nhưng
 * chỉ ADMIN mới SỬA được.
 *
 * Thay đổi có hiệu lực ngay, không cần khởi động lại ứng dụng.
 */
export default function SettingPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { isAdmin } = useAuth();
  const [form] = Form.useForm<Record<string, string | number | boolean>>();

  const { data: settings = [], isLoading } = useQuery({
    queryKey: ['settings'],
    queryFn: settingApi.listAll,
  });

  // Nạp giá trị hiện tại vào biểu mẫu
  useEffect(() => {
    if (settings.length > 0) {
      const values: Record<string, string | number | boolean> = {};
      settings.forEach((setting) => {
        if (setting.valueType === 'BOOLEAN') {
          values[setting.key] = setting.value === 'true';
        } else if (setting.valueType === 'INTEGER') {
          values[setting.key] = Number(setting.value);
        } else {
          values[setting.key] = setting.value;
        }
      });
      form.setFieldsValue(values);
    }
  }, [settings, form]);

  const saveMutation = useMutation({
    mutationFn: (values: Record<string, string | number | boolean>) => {
      // Backend nhận mọi giá trị dưới dạng chuỗi
      const payload: Record<string, string> = {};
      Object.entries(values).forEach(([key, value]) => {
        payload[key] = String(value);
      });
      return settingApi.update(payload);
    },
    onSuccess: () => {
      message.success('Đã lưu cấu hình. Thay đổi có hiệu lực ngay.');
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
      // Gợi ý nhập hàng dùng các tham số này nên phải tính lại
      void queryClient.invalidateQueries({ queryKey: ['replenishment'] });
      void queryClient.invalidateQueries({ queryKey: ['reports'] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const columns: ColumnsType<SettingResponse> = [
    {
      title: 'Tham số',
      dataIndex: 'description',
      render: (description: string | undefined, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{description ?? record.key}</Text>
          <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
            {record.key}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Giá trị',
      key: 'value',
      width: 220,
      render: (_, record) => {
        if (!isAdmin) {
          // Người không phải ADMIN chỉ xem giá trị
          return record.valueType === 'BOOLEAN' ? (
            <Text>{record.value === 'true' ? 'Bật' : 'Tắt'}</Text>
          ) : (
            <Text strong>{record.value}</Text>
          );
        }

        if (record.valueType === 'BOOLEAN') {
          return (
            <Form.Item name={record.key} valuePropName="checked" noStyle>
              <Switch checkedChildren="Bật" unCheckedChildren="Tắt" />
            </Form.Item>
          );
        }

        if (record.valueType === 'INTEGER') {
          return (
            <Form.Item name={record.key} noStyle rules={[{ required: true, message: 'Bắt buộc' }]}>
              <InputNumber style={{ width: '100%' }} min={0} max={9999} />
            </Form.Item>
          );
        }

        return (
          <Form.Item name={record.key} noStyle rules={[{ required: true, message: 'Bắt buộc' }]}>
            <Input />
          </Form.Item>
        );
      },
    },
    {
      title: 'Cập nhật lần cuối',
      dataIndex: 'updatedAt',
      width: 170,
      responsive: ['md'],
      render: (value: string) => formatDateTime(value),
    },
  ];

  return (
    <>
      <PageHeader
        title="Cấu hình hệ thống"
        description={
          isAdmin
            ? 'Các tham số ảnh hưởng tới gợi ý nhập hàng, báo cáo hàng tồn lâu và email tự động.'
            : 'Xem các tham số hệ thống đang áp dụng. Chỉ quản trị viên sửa được.'
        }
        extra={
          isAdmin && (
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saveMutation.isPending}
              onClick={() => form.submit()}
            >
              Lưu cấu hình
            </Button>
          )
        }
      />

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="Ý nghĩa các tham số"
        description={
          <Space direction="vertical" size={2}>
            <Text style={{ fontSize: 13 }}>
              <Text strong>Thời gian chờ hàng về</Text> và <Text strong>Số ngày dự trữ an toàn</Text>: dùng
              trong công thức gợi ý nhập hàng. Tăng hai giá trị này thì hệ thống gợi ý nhập nhiều hơn mỗi lần.
            </Text>
            <Text style={{ fontSize: 13 }}>
              <Text strong>Số ngày tính bình quân xuất</Text>: lấy bao nhiêu ngày lịch sử để tính lượng xuất
              trung bình mỗi ngày.
            </Text>
            <Text style={{ fontSize: 13 }}>
              <Text strong>Ngưỡng hàng tồn lâu</Text>: mặt hàng không phát sinh xuất quá số ngày này sẽ vào báo
              cáo hàng tồn lâu.
            </Text>
            <Text style={{ fontSize: 13 }}>
              <Text strong>Email báo cáo tồn thấp</Text>: hệ thống gửi email hằng ngày vào giờ đã đặt (múi giờ
              Việt Nam) cho quản lý kho và quản trị viên.
            </Text>
          </Space>
        }
      />

      <Card size="small">
        <Form
          form={form}
          onFinish={(values) => saveMutation.mutate(values)}
          component={false}
        >
          <Table<SettingResponse>
            rowKey="key"
            columns={columns}
            dataSource={settings}
            loading={isLoading}
            size="small"
            pagination={false}
            scroll={{ x: 'max-content' }}
          />
        </Form>
      </Card>
    </>
  );
}
