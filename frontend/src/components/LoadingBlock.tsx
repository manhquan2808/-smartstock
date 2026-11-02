import { Spin, Typography } from 'antd';

interface LoadingBlockProps {
  /** Dòng chữ mô tả đang chờ gì, ví dụ "Đang tải phiếu...". */
  message: string;
  /** Chiều cao tối thiểu của vùng chờ. Dùng '100vh' khi chiếm trọn màn hình. */
  minHeight?: number | string;
}

/**
 * Vùng chờ dùng chung cho mọi màn hình.
 *
 * <p>Không truyền <code>tip</code> thẳng vào <code>Spin</code>: Ant Design 5 chỉ hỗ trợ
 * <code>tip</code> khi Spin bọc nội dung khác hoặc ở chế độ fullscreen, còn Spin đứng một mình
 * thì bỏ qua <code>tip</code> và in cảnh báo ra console. Ở đây hiển thị chữ bằng một dòng
 * riêng bên dưới, vừa đúng API vừa dễ đọc hơn.
 */
export default function LoadingBlock({ message, minHeight = 320 }: LoadingBlockProps) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        justifyContent: 'center',
        alignItems: 'center',
        minHeight,
      }}
      role="status"
      aria-live="polite"
    >
      <Spin size="large" />
      <Typography.Text type="secondary">{message}</Typography.Text>
    </div>
  );
}
