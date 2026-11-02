import { Tag } from 'antd';
import { DOCUMENT_STATUS_COLOR, DOCUMENT_STATUS_LABEL } from '@/utils/constants';
import type { DocumentStatus } from '@/types';

/**
 * Thẻ màu thể hiện trạng thái phiếu.
 *
 * Màu được tra từ bảng dùng chung nên trạng thái "Hoàn tất" luôn xanh ở mọi trang.
 */
export default function StatusTag({ status, label }: { status: DocumentStatus; label?: string }) {
  return <Tag color={DOCUMENT_STATUS_COLOR[status]}>{label ?? DOCUMENT_STATUS_LABEL[status]}</Tag>;
}
