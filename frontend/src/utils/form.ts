import type { FormInstance } from 'antd';
import { getFieldErrors } from '@/api/client';

/**
 * Gắn lỗi do BACKEND trả về vào đúng ô nhập trên biểu mẫu.
 *
 * Backend trả lỗi theo từng trường trong `fieldErrors`, ví dụ:
 * `{ "sku": "Mã sản phẩm đã tồn tại" }`. Hàm này đặt thông báo đó ngay dưới ô tương ứng
 * thay vì chỉ hiện một toast chung - người dùng thấy ngay phải sửa ở đâu.
 *
 * Về kiểu dữ liệu: tên trường từ backend là chuỗi bất kỳ, còn `form.setFields` của Ant
 * Design yêu cầu tên phải thuộc danh sách khóa của biểu mẫu. Việc chuyển kiểu được gom
 * vào một chỗ duy nhất ở đây, thay vì rải rác ở từng trang.
 *
 * @returns true nếu có lỗi theo trường (trang gọi có thể bỏ qua việc hiện toast chung)
 */
export function applyServerFieldErrors<T extends object>(form: FormInstance<T>, error: unknown): boolean {
  const fieldErrors = getFieldErrors(error);
  const entries = Object.entries(fieldErrors);

  if (entries.length === 0) {
    return false;
  }

  form.setFields(
    entries.map(([name, errorMessage]) => ({
      name: name as keyof T & string,
      errors: [errorMessage],
    })) as Parameters<FormInstance<T>['setFields']>[0],
  );
  return true;
}
