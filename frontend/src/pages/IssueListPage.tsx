import DocumentListPage from '@/components/DocumentListPage';
import { issueApi } from '@/api/endpoints';
import { ISSUE_TYPE_OPTIONS } from '@/utils/constants';
import type { PageResponse, DocumentSummary } from '@/types';

/**
 * Trang DANH SÁCH PHIẾU XUẤT KHO.
 *
 * Khác phiếu nhập ở cột tiền: phiếu xuất tính theo GIÁ VỐN tại thời điểm duyệt, nên cột
 * này chỉ có số sau khi phiếu hoàn tất.
 */
export default function IssueListPage() {
  return (
    <DocumentListPage
      title="Phiếu xuất kho"
      description="Mã phiếu dạng PX-yyyyMMdd-xxx. Hệ thống không cho xuất vượt tồn kho."
      basePath="issues"
      queryKey="issues"
      typeLabel="Loại phiếu xuất"
      typeOptions={ISSUE_TYPE_OPTIONS}
      typeParamName="issueType"
      amountColumnTitle="Giá trị xuất"
      search={(params) => issueApi.search(params) as Promise<PageResponse<DocumentSummary>>}
    />
  );
}
