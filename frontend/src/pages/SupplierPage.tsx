import PartnerCatalogPage from '@/components/PartnerCatalogPage';
import { supplierApi } from '@/api/endpoints';

/** Trang quản lý NHÀ CUNG CẤP - đối tác trên phiếu nhập mua và phiếu trả hàng. */
export default function SupplierPage() {
  return (
    <PartnerCatalogPage
      title="Nhà cung cấp"
      description="Đối tác cung cấp hàng. Bắt buộc chọn khi lập phiếu nhập mua."
      entityLabel="nhà cung cấp"
      queryKey="suppliers"
      api={supplierApi}
    />
  );
}
