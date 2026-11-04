import { api } from './client';
import type { NotificationResponse, NotificationType, PageResponse } from './types';

export const notificationsApi = {
  list: (options: { type?: NotificationType; unreadOnly?: boolean; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams();
    if (options.type) params.set('type', options.type);
    if (options.unreadOnly) params.set('unreadOnly', 'true');
    params.set('page', String(options.page ?? 0));
    params.set('size', String(options.size ?? 20));
    return api.get<PageResponse<NotificationResponse>>(`/api/v1/notifications?${params}`);
  },

  /** Just the badge. Cheap enough to poll; the feed itself is not. */
  unreadCount: () => api.get<{ unread: number }>('/api/v1/notifications/unread-count'),

  markRead: (id: string) => api.post<NotificationResponse>(`/api/v1/notifications/${id}/read`),

  markAllRead: () => api.post<{ marked: number }>('/api/v1/notifications/read-all'),
};
