import { Typography } from 'antd';
import { formatCurrency, formatMoney } from '@/utils/format';

const { Text } = Typography;

interface MoneyTextProps {
  value?: number | null;
  /** Hiển thị kèm ký hiệu ₫ hay không. */
  showSymbol?: boolean;
  strong?: boolean;
}

/**
 * Hiển thị số tiền.
 *
 * Khi giá trị là `null` (trường hợp vai trò STAFF không được xem giá vốn), component
 * hiển thị dấu gạch mờ thay vì số 0 - để người dùng hiểu là "không có quyền xem", không
 * phải "giá trị bằng không".
 */
export default function MoneyText({ value, showSymbol = false, strong = false }: MoneyTextProps) {
  if (value === null || value === undefined) {
    return (
      <Text type="secondary" title="Bạn không có quyền xem giá trị này">
        —
      </Text>
    );
  }
  return <Text strong={strong}>{showSymbol ? formatCurrency(value) : formatMoney(value)}</Text>;
}
