import DocumentFormPage, { type DocumentPayload } from '@/components/DocumentFormPage';
import { receiptApi } from '@/api/endpoints';
import { RECEIPT_TYPE_OPTIONS, RECEIPT_TYPE_RULES } from '@/utils/constants';
import type { ReceiptType, SaveReceiptRequest } from '@/types';

/**
 * Trang LẬP / SỬA PHIẾU NHẬP KHO.
 *
 * Chuyển dữ liệu từ khuôn dạng dùng chung của biểu mẫu sang khuôn dạng API phiếu nhập
 * (có đơn giá cho từng dòng).
 */
export default function ReceiptFormPage() {
  const toRequest = (payload: DocumentPayload): SaveReceiptRequest => ({
    warehouseId: payload.warehouseId,
    receiptType: payload.documentType as ReceiptType,
    documentDate: payload.documentDate,
    supplierId: payload.supplierId,
    customerId: payload.customerId,
    reason: payload.reason,
    note: payload.note,
    items: payload.items.map((item) => ({
      productId: item.productId,
      quantity: item.quantity,
      unitPrice: item.unitPrice ?? 0,
      note: item.note,
    })),
  });

  return (
    <DocumentFormPage
      mode="receipt"
      basePath="receipts"
      typeLabel="Loại phiếu nhập"
      typeOptions={RECEIPT_TYPE_OPTIONS}
      typeRules={RECEIPT_TYPE_RULES}
      defaultType="PURCHASE"
      fetchDocument={receiptApi.getById}
      createDocument={(payload, submit) => receiptApi.create(toRequest(payload), submit)}
      updateDocument={(id, payload) => receiptApi.update(id, toRequest(payload))}
    />
  );
}
