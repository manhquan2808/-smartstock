import DocumentDetailPage from '@/components/DocumentDetailPage';
import { receiptApi } from '@/api/endpoints';

/** Trang CHI TIẾT PHIẾU NHẬP KHO, kèm các nút gửi duyệt / duyệt / từ chối / hủy / in. */
export default function ReceiptDetailPage() {
  return (
    <DocumentDetailPage
      mode="receipt"
      basePath="receipts"
      queryKey="receipts"
      typeLabel="Loại phiếu nhập"
      fetchDocument={receiptApi.getById}
      submitDocument={receiptApi.submit}
      approveDocument={receiptApi.approve}
      rejectDocument={receiptApi.reject}
      cancelDocument={receiptApi.cancel}
      deleteDocument={receiptApi.remove}
    />
  );
}
