import SimpleCatalogPage from '@/components/SimpleCatalogPage';
import { categoryApi } from '@/api/endpoints';
import type { CategoryResponse } from '@/types';

/**
 * Trang quản lý LOẠI HÀNG.
 *
 * Toàn bộ giao diện và thao tác nằm ở component dùng chung `SimpleCatalogPage`;
 * trang này chỉ cung cấp tiêu đề và bộ API tương ứng.
 */
export default function CategoryPage() {
  return (
    <SimpleCatalogPage<CategoryResponse>
      title="Loại hàng"
      description="Phân nhóm sản phẩm theo ngành hàng, dùng để lọc danh sách và báo cáo."
      entityLabel="loại hàng"
      queryKey="categories"
      api={categoryApi}
    />
  );
}
