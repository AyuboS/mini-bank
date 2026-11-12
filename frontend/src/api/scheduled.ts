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

/**
 * Standing orders.
 *
 * Due orders are executed by a background runner through the ordinary transfer
 * path, so a scheduled payment carries the same limits, risk checks and ledger
 * guarantees as one typed by hand.
 */
export const scheduledApi = {
  list: () => api.get<ScheduledTransferResponse[]>('/api/v1/scheduled-transfers'),

  create: (body: ScheduledTransferBody) =>
    api.post<ScheduledTransferResponse>('/api/v1/scheduled-transfers', body),

  pause: (id: string) => api.patch<ScheduledTransferResponse>(`/api/v1/scheduled-transfers/${id}/pause`),

  resume: (id: string) => api.patch<ScheduledTransferResponse>(`/api/v1/scheduled-transfers/${id}/resume`),

  cancel: (id: string) => api.patch<ScheduledTransferResponse>(`/api/v1/scheduled-transfers/${id}/cancel`),
};
