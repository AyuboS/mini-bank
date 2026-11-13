import { api } from './client';
import type { ScheduleFrequency, ScheduledTransferResponse, TransactionCategory } from './types';

export interface ScheduledTransferBody {
  fromAccountId: string;
  toAccountNumber: string;
  amount: string;
  description?: string;
  category?: TransactionCategory;
  frequency: ScheduleFrequency;
  /** Today or later. The server refuses a first run in the past. */
  startOn: string;
  endsOn?: string;
}

/** Standing orders. */
export const scheduledApi = {
  list: () => api.get<ScheduledTransferResponse[]>('/api/v1/scheduled-transfers'),

  create: (body: ScheduledTransferBody) =>
    api.post<ScheduledTransferResponse>('/api/v1/scheduled-transfers', body),

  pause: (id: string) => api.patch<ScheduledTransferResponse>(`/api/v1/scheduled-transfers/${id}/pause`),

  resume: (id: string) => api.patch<ScheduledTransferResponse>(`/api/v1/scheduled-transfers/${id}/resume`),

  cancel: (id: string) => api.patch<ScheduledTransferResponse>(`/api/v1/scheduled-transfers/${id}/cancel`),
};
