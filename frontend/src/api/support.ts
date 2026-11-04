import { api } from './client';
import type { SupportCategory, SupportTicketResponse } from './types';

export interface SupportTicketBody {
  subject: string;
  category: SupportCategory;
  description: string;
}

/** Support tickets. They go to a human queue; nothing here generates a reply. */
export const supportApi = {
  list: () => api.get<SupportTicketResponse[]>('/api/v1/support/tickets'),

  get: (id: string) => api.get<SupportTicketResponse>(`/api/v1/support/tickets/${id}`),

  open: (body: SupportTicketBody) => api.post<SupportTicketResponse>('/api/v1/support/tickets', body),

  reply: (id: string, body: string) =>
    api.post<SupportTicketResponse>(`/api/v1/support/tickets/${id}/messages`, { body }),

  close: (id: string) => api.patch<SupportTicketResponse>(`/api/v1/support/tickets/${id}/close`),
};
