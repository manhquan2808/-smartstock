import DocumentDetailPage from '@/components/DocumentDetailPage';
import { issueApi } from '@/api/endpoints';

/** Trang CHI TIẾT PHIẾU XUẤT KHO. */
export default function IssueDetailPage() {
  return (
    <DocumentDetailPage
      mode="issue"
      basePath="issues"
      queryKey="issues"
      typeLabel="Loại phiếu xuất"
      fetchDocument={issueApi.getById}
      submitDocument={issueApi.submit}
      approveDocument={issueApi.approve}
      rejectDocument={issueApi.reject}
      cancelDocument={issueApi.cancel}
      deleteDocument={issueApi.remove}
    />
  );
}
