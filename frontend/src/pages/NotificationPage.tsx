import { useNavigate } from 'react-router-dom';
import { CheckOutlined } from '@ant-design/icons';
import { App, Badge, Button, Card, Empty, List, Segmented, Space, Tag, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import PageHeader from '@/components/PageHeader';
import { notificationApi } from '@/api/endpoints';
import { getErrorMessage } from '@/api/client';
import { useUrlFilters } from '@/hooks/useUrlFilters';
import { formatDateTime, formatRelativeTime } from '@/utils/format';
import type { NotificationResponse, NotificationType } from '@/types';

const { Text, Paragraph } = Typography;

/** Màu thẻ theo loại thông báo. */
const TYPE_COLOR: Record<NotificationType, string> = {
  RECEIPT_PENDING: 'blue',
  ISSUE_PENDING: 'cyan',
  STOCKTAKE_PENDING: 'purple',
  DOCUMENT_APPROVED: 'green',
  DOCUMENT_REJECTED: 'red',
  DOCUMENT_CANCELLED: 'orange',
  LOW_STOCK: 'volcano',
};

/**
 * Trang DANH SÁCH THÔNG BÁO.
 *
 * Thông báo mới xuất hiện gần như tức thì nhờ kênh SSE (xem `useNotificationStream`).
 * Bấm vào một thông báo sẽ đánh dấu đã đọc và chuyển tới phiếu/trang liên quan.
 */
export default function NotificationPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { filters, setFilter, page, size, setPagination } = useUrlFilters({ unreadOnly: '' }, 20);
  const unreadOnly = filters.unreadOnly === '1';

  const { data, isFetching } = useQuery({
    queryKey: ['notifications', 'list', unreadOnly, page, size],
    queryFn: () => notificationApi.list({ unreadOnly, page, size }),
  });

  const markAsRead = useMutation({
    mutationFn: notificationApi.markAsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const markAllAsRead = useMutation({
    mutationFn: notificationApi.markAllAsRead,
    onSuccess: (result) => {
      message.success(result.message);
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error) => message.error(getErrorMessage(error)),
  });

  const handleClick = (item: NotificationResponse) => {
    if (!item.read) {
      markAsRead.mutate(item.id);
    }
    if (item.link) {
      navigate(item.link);
    }
  };

  return (
    <>
      <PageHeader
        title="Thông báo"
        description="Phiếu chờ duyệt, kết quả duyệt và cảnh báo tồn thấp. Thông báo mới hiện ngay không cần tải lại trang."
        extra={
          <Space wrap>
            <Segmented
              value={unreadOnly ? 'unread' : 'all'}
              onChange={(value) => setFilter('unreadOnly', value === 'unread' ? '1' : '')}
              options={[
                { value: 'all', label: 'Tất cả' },
                { value: 'unread', label: 'Chưa đọc' },
              ]}
            />
            <Button
              icon={<CheckOutlined />}
              loading={markAllAsRead.isPending}
              onClick={() => markAllAsRead.mutate()}
            >
              Đánh dấu đã đọc hết
            </Button>
          </Space>
        }
      />

      <Card size="small">
        <List<NotificationResponse>
          loading={isFetching}
          dataSource={data?.content ?? []}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={unreadOnly ? 'Bạn đã đọc hết thông báo.' : 'Chưa có thông báo nào.'}
              />
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (total, range) => `${range[0]}-${range[1]} trong ${total} thông báo`,
            onChange: (nextPage, nextSize) => setPagination(nextPage - 1, nextSize),
          }}
          renderItem={(item) => (
            <List.Item
              onClick={() => handleClick(item)}
              style={{
                cursor: item.link ? 'pointer' : 'default',
                // Thông báo chưa đọc có nền nhạt để nổi bật
                background: item.read ? undefined : 'rgba(22,119,255,0.05)',
                borderRadius: 6,
                padding: '12px 14px',
              }}
              actions={
                item.read
                  ? undefined
                  : [
                      <Button
                        key="read"
                        size="small"
                        type="link"
                        icon={<CheckOutlined />}
                        onClick={(event) => {
                          // Chặn nổi bọt để không chuyển trang khi chỉ muốn đánh dấu đã đọc
                          event.stopPropagation();
                          markAsRead.mutate(item.id);
                        }}
                      >
                        Đã đọc
                      </Button>,
                    ]
              }
            >
              <List.Item.Meta
                avatar={item.read ? <Badge status="default" /> : <Badge status="processing" />}
                title={
                  <Space size={8} wrap>
                    <Text strong={!item.read}>{item.title}</Text>
                    <Tag color={TYPE_COLOR[item.type]} style={{ fontSize: 11 }}>
                      {item.typeName}
                    </Tag>
                  </Space>
                }
                description={
                  <Space direction="vertical" size={2}>
                    {item.content && (
                      <Paragraph style={{ margin: 0, fontSize: 13 }}>{item.content}</Paragraph>
                    )}
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {formatRelativeTime(item.createdAt)} · {formatDateTime(item.createdAt)}
                    </Text>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Card>
    </>
  );
}
