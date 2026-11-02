import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BellOutlined, CheckOutlined } from '@ant-design/icons';
import { Badge, Button, Dropdown, Empty, List, Space, Tag, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationApi } from '@/api/endpoints';
import { useAuth } from '@/contexts/AuthContext';
import { useNotificationStream } from '@/hooks/useNotificationStream';
import { formatRelativeTime } from '@/utils/format';
import type { NotificationResponse } from '@/types';

const { Text } = Typography;

/**
 * Biểu tượng chuông thông báo trên thanh tiêu đề.
 *
 * Số thông báo chưa đọc cập nhật theo hai đường:
 * 1. **Realtime qua SSE** (xem {@link useNotificationStream}) - gần như tức thì.
 * 2. **Hỏi lại định kỳ mỗi 60 giây** - đường dự phòng cho trường hợp kết nối SSE bị
 *    proxy hoặc tường lửa chặn. Nhờ vậy số đếm không bao giờ "đứng" quá một phút.
 */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuth();

  // Mở kênh nhận thông báo realtime
  useNotificationStream(isAuthenticated);

  const { data: unreadCount = 0 } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.unreadCount,
    enabled: isAuthenticated,
    refetchInterval: 60 * 1000,
  });

  const { data: recent } = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => notificationApi.list({ page: 0, size: 8 }),
    // Chỉ tải danh sách khi người dùng mở menu thả xuống
    enabled: isAuthenticated && open,
  });

  const markAsRead = useMutation({
    mutationFn: notificationApi.markAsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const markAllAsRead = useMutation({
    mutationFn: notificationApi.markAllAsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const handleClickItem = (item: NotificationResponse) => {
    if (!item.read) {
      markAsRead.mutate(item.id);
    }
    setOpen(false);
    if (item.link) {
      navigate(item.link);
    }
  };

  const dropdownContent = (
    <div
      style={{
        width: 360,
        maxWidth: '90vw',
        background: 'var(--ss-dropdown-bg, #fff)',
        borderRadius: 8,
        boxShadow: '0 6px 16px rgba(0,0,0,0.12)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 14px',
          borderBottom: '1px solid rgba(5,5,5,0.06)',
        }}
      >
        <Text strong>Thông báo</Text>
        {unreadCount > 0 && (
          <Button
            type="link"
            size="small"
            icon={<CheckOutlined />}
            loading={markAllAsRead.isPending}
            onClick={() => markAllAsRead.mutate()}
          >
            Đánh dấu đã đọc hết
          </Button>
        )}
      </div>

      <div style={{ maxHeight: 420, overflowY: 'auto' }}>
        {recent && recent.content.length > 0 ? (
          <List
            dataSource={recent.content}
            renderItem={(item) => (
              <List.Item
                onClick={() => handleClickItem(item)}
                style={{
                  padding: '10px 14px',
                  cursor: 'pointer',
                  // Thông báo chưa đọc có nền nhạt để dễ phân biệt
                  background: item.read ? undefined : 'rgba(22,119,255,0.06)',
                }}
              >
                <Space direction="vertical" size={2} style={{ width: '100%' }}>
                  <Space size={6} wrap>
                    {!item.read && <Badge status="processing" />}
                    <Text strong style={{ fontSize: 13 }}>
                      {item.title}
                    </Text>
                  </Space>
                  {item.content && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {item.content}
                    </Text>
                  )}
                  <Space size={8}>
                    <Tag color={item.type === 'LOW_STOCK' ? 'orange' : 'blue'} style={{ fontSize: 11 }}>
                      {item.typeName}
                    </Tag>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {formatRelativeTime(item.createdAt)}
                    </Text>
                  </Space>
                </Space>
              </List.Item>
            )}
          />
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Chưa có thông báo nào"
            style={{ padding: '28px 0' }}
          />
        )}
      </div>

      <div style={{ padding: 10, textAlign: 'center', borderTop: '1px solid rgba(5,5,5,0.06)' }}>
        <Link to="/notifications" onClick={() => setOpen(false)}>
          Xem tất cả thông báo
        </Link>
      </div>
    </div>
  );

  // Dùng popupRender chứ không phải dropdownRender: antd đã đánh dấu dropdownRender
  // là lỗi thời từ bản 5.25 và in cảnh báo ra console mỗi lần render.
  return (
    <Dropdown
      open={open}
      onOpenChange={setOpen}
      trigger={['click']}
      placement="bottomRight"
      popupRender={() => dropdownContent}
    >
      <Badge count={unreadCount} size="small" overflowCount={99}>
        <span
          role="button"
          tabIndex={0}
          aria-label={`Thông báo${unreadCount > 0 ? `, ${unreadCount} chưa đọc` : ''}`}
          style={{ cursor: 'pointer', fontSize: 18, display: 'inline-flex' }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              setOpen(!open);
            }
          }}
        >
          <BellOutlined />
        </span>
      </Badge>
    </Dropdown>
  );
}
