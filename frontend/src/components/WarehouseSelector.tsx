import { Select, Space, Typography } from 'antd';
import { ShopOutlined } from '@ant-design/icons';
import { useWarehouse } from '@/contexts/WarehouseContext';
import { useAuth } from '@/contexts/AuthContext';

const { Text } = Typography;

/**
 * Bộ chọn kho đang làm việc, đặt ở thanh tiêu đề.
 *
 * Người dùng chỉ có một kho thì hiển thị tên kho ở dạng chữ, không cần ô chọn.
 * ADMIN và MANAGER nhiều kho có thêm lựa chọn "Tất cả kho" để xem số liệu gộp.
 */
export default function WarehouseSelector() {
  const { warehouses, currentWarehouseId, setCurrentWarehouseId, hasMultipleWarehouses } = useWarehouse();
  const { canViewCost } = useAuth();

  if (warehouses.length === 0) {
    return (
      <Text type="secondary" style={{ fontSize: 13 }}>
        Chưa được gán kho
      </Text>
    );
  }

  if (!hasMultipleWarehouses) {
    return (
      <Space size={6}>
        <ShopOutlined style={{ color: '#1677ff' }} />
        <Text strong style={{ fontSize: 13 }}>
          {warehouses[0].name}
        </Text>
      </Space>
    );
  }

  const options = [
    // Chỉ ADMIN/MANAGER mới có nhu cầu xem gộp nhiều kho
    ...(canViewCost ? [{ value: 'all', label: 'Tất cả kho' }] : []),
    ...warehouses.map((warehouse) => ({ value: String(warehouse.id), label: warehouse.name })),
  ];

  return (
    <Select
      value={currentWarehouseId === null ? 'all' : String(currentWarehouseId)}
      onChange={(value) => setCurrentWarehouseId(value === 'all' ? null : Number(value))}
      options={options}
      style={{ minWidth: 190 }}
      size="middle"
      suffixIcon={<ShopOutlined />}
      aria-label="Chọn kho đang làm việc"
    />
  );
}
