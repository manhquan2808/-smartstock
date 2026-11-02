import DocumentListPage from '@/components/DocumentListPage';
import { receiptApi } from '@/api/endpoints';
import { RECEIPT_TYPE_OPTIONS } from '@/utils/constants';
import type { PageResponse, DocumentSummary } from '@/types';

/**
 * Trang DANH SÁCH PHIẾU NHẬP KHO.
 *
 * Giao diện và bộ lọc nằm ở component dùng chung `DocumentListPage`; trang này chỉ cấu
 * hình những gì khác biệt của phiếu nhập.
 */
export default function ReceiptListPage() {
  return (
    <DocumentListPage
      title="Phiếu nhập kho"
      description="Mã phiếu dạng PN-yyyyMMdd-xxx. Phiếu chỉ cộng tồn kho sau khi được duyệt."
      basePath="receipts"
      queryKey="receipts"
      typeLabel="Loại phiếu nhập"
      typeOptions={RECEIPT_TYPE_OPTIONS}
      typeParamName="receiptType"
      amountColumnTitle="Tổng tiền"
      search={(params) => receiptApi.search(params) as Promise<PageResponse<DocumentSummary>>}
    />
  );
}
