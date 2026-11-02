import DocumentFormPage, { type DocumentPayload } from '@/components/DocumentFormPage';
import { issueApi } from '@/api/endpoints';
import { ISSUE_TYPE_OPTIONS, ISSUE_TYPE_RULES } from '@/utils/constants';
import type { IssueType, SaveIssueRequest } from '@/types';

/**
 * Trang LẬP / SỬA PHIẾU XUẤT KHO.
 *
 * Phiếu xuất KHÔNG có đơn giá do người lập nhập: giá trị xuất được hệ thống tính theo
 * giá vốn bình quân tại thời điểm duyệt, nên dữ liệu gửi lên chỉ gồm sản phẩm và số lượng.
 */
export default function IssueFormPage() {
  const toRequest = (payload: DocumentPayload): SaveIssueRequest => ({
    warehouseId: payload.warehouseId,
    issueType: payload.documentType as IssueType,
    documentDate: payload.documentDate,
    customerId: payload.customerId,
    supplierId: payload.supplierId,
    reason: payload.reason,
    note: payload.note,
    items: payload.items.map((item) => ({
      productId: item.productId,
      quantity: item.quantity,
      note: item.note,
    })),
  });

  return (
    <DocumentFormPage
      mode="issue"
      basePath="issues"
      typeLabel="Loại phiếu xuất"
      typeOptions={ISSUE_TYPE_OPTIONS}
      typeRules={ISSUE_TYPE_RULES}
      defaultType="SALE"
      fetchDocument={issueApi.getById}
      createDocument={(payload, submit) => issueApi.create(toRequest(payload), submit)}
      updateDocument={(id, payload) => issueApi.update(id, toRequest(payload))}
    />
  );
}
