import { Link } from 'react-router-dom';
import { Button, Result } from 'antd';

/** Trang hiển thị khi người dùng mở một đường dẫn không tồn tại. */
export default function NotFoundPage() {
  return (
    <Result
      status="404"
      title="404"
      subTitle="Không tìm thấy trang bạn yêu cầu."
      extra={
        <Link to="/dashboard">
          <Button type="primary">Về trang tổng quan</Button>
        </Link>
      }
      style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}
    />
  );
}
