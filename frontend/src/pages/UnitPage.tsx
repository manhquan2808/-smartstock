import SimpleCatalogPage from '@/components/SimpleCatalogPage';
import { unitApi } from '@/api/endpoints';
import type { UnitResponse } from '@/types';

/**
 * Trang quản lý ĐƠN VỊ TÍNH (thùng, lon, kg, lít...).
 *
 * Hệ thống lưu số lượng với 3 chữ số thập phân nên dùng được cả đơn vị cân, đo như kg/lít.
 */
export default function UnitPage() {
  return (
    <SimpleCatalogPage<UnitResponse>
      title="Đơn vị tính"
      description="Đơn vị đo của sản phẩm. Hệ thống hỗ trợ số thập phân nên dùng được cho kg, lít."
      entityLabel="đơn vị tính"
      queryKey="units"
      api={unitApi}
      nameMaxLength={100}
    />
  );
}
