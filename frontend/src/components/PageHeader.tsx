import type { ReactNode } from 'react';
import { Card, Space, Typography } from 'antd';

const { Title, Text } = Typography;

interface PageHeaderProps {
  title: string;
  /** Mô tả ngắn giúp người dùng mới hiểu trang này dùng để làm gì. */
  description?: string;
  /** Các nút hành động chính, hiển thị bên phải tiêu đề. */
  extra?: ReactNode;
}

/**
 * Tiêu đề trang dùng chung, để mọi trang có cùng một kiểu trình bày.
 *
 * Trên điện thoại, phần nút hành động tự xuống dòng dưới tiêu đề (flex-wrap).
 */
export default function PageHeader({ title, description, extra }: PageHeaderProps) {
  return (
    <Card size="small" style={{ marginBottom: 12 }} styles={{ body: { padding: '12px 16px' } }}>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 10,
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Space direction="vertical" size={0}>
          <Title level={4} style={{ margin: 0 }}>
            {title}
          </Title>
          {description && (
            <Text type="secondary" style={{ fontSize: 13 }}>
              {description}
            </Text>
          )}
        </Space>
        {extra && <Space wrap>{extra}</Space>}
      </div>
    </Card>
  );
}
