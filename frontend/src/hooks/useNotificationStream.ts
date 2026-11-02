import { useEffect, useRef } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { API_BASE_URL, getAccessToken } from '@/api/client';
import type { NotificationResponse } from '@/types';

/**
 * Nhận thông báo realtime qua Server-Sent Events (SSE).
 *
 * **Vì sao dùng `@microsoft/fetch-event-source` thay vì `EventSource` có sẵn của trình
 * duyệt:** `EventSource` gốc KHÔNG cho phép gửi header, nên không gửi được
 * `Authorization: Bearer ...`. Thư viện này dựng SSE trên nền `fetch` nên gửi header
 * bình thường, đồng thời cho ta kiểm soát việc tự kết nối lại.
 *
 * Khi có thông báo mới, hook làm hai việc:
 * 1. Hiện một toast nhỏ để người dùng biết ngay.
 * 2. Xóa bộ đệm của các truy vấn thông báo, để số trên biểu tượng chuông cập nhật.
 */
export function useNotificationStream(enabled: boolean): void {
  const queryClient = useQueryClient();
  const { notification } = App.useApp();
  /** Dùng để hủy kết nối khi component bị tháo bỏ. */
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const token = getAccessToken();
    if (!token) {
      return;
    }

    const controller = new AbortController();
    abortRef.current = controller;

    void fetchEventSource(`${API_BASE_URL}/api/notifications/stream`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
      credentials: 'include',
      // Giữ kết nối khi người dùng chuyển sang tab khác (mặc định thư viện sẽ ngắt)
      openWhenHidden: true,

      onmessage(event) {
        if (event.event !== 'notification' || !event.data) {
          return; // bỏ qua sự kiện 'connected' và các dòng heartbeat
        }
        try {
          const payload = JSON.parse(event.data) as NotificationResponse;

          notification.info({
            message: payload.title,
            description: payload.content,
            placement: 'topRight',
            duration: 6,
          });

          // Đánh dấu dữ liệu thông báo đã cũ -> TanStack Query tự tải lại
          void queryClient.invalidateQueries({ queryKey: ['notifications'] });
        } catch {
          // Dữ liệu sự kiện không đúng khuôn dạng: bỏ qua, không làm gián đoạn giao diện
        }
      },

      onerror(error) {
        // Trả về undefined để thư viện tự kết nối lại sau một khoảng chờ.
        // Ném lỗi ở đây sẽ khiến kết nối dừng hẳn và người dùng mất thông báo realtime.
        if (controller.signal.aborted) {
          throw error; // chủ động hủy: dừng thật
        }
      },
    });

    return () => {
      controller.abort();
      abortRef.current = null;
    };
    // Chỉ phụ thuộc `enabled`: khi đăng nhập/đăng xuất thì mở hoặc đóng kênh
  }, [enabled, notification, queryClient]);
}
