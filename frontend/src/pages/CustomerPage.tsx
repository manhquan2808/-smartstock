import PartnerCatalogPage from '@/components/PartnerCatalogPage';
import { customerApi } from '@/api/endpoints';

/** Trang quản lý KHÁCH HÀNG - đối tác trên phiếu xuất bán và phiếu nhập hàng trả lại. */
export default function CustomerPage() {
  return (
    <PartnerCatalogPage
      title="Khách hàng"
      description="Đối tác mua hàng. Bắt buộc chọn khi lập phiếu xuất bán."
      entityLabel="khách hàng"
      queryKey="customers"
      api={customerApi}
    />
  );
}
