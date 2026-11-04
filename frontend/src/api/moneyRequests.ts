import { api } from './client';
import type { MoneyRequestResponse } from './types';

export interface MoneyRequestBody {
  /** The account of yours the money should land in. */
  toAccountId: string;
  /** The account being asked. */
  fromAccountNumber: string;
  amount: string;
  description?: string;
  expiresInDays?: number;
}

/**
 * Payment requests.
 *
 * A request moves no money by existing. Only the payer can settle one, and doing
 * so creates an ordinary transfer from the payer's own account.
 */
export const moneyRequestsApi = {
  outgoing: () => api.get<MoneyRequestResponse[]>('/api/v1/money-requests/outgoing'),

  incoming: () => api.get<MoneyRequestResponse[]>('/api/v1/money-requests/incoming'),

  create: (body: MoneyRequestBody) => api.post<MoneyRequestResponse>('/api/v1/money-requests', body),

  /** Payable only by the account the request names. */
  pay: (id: string, fromAccountId: string) =>
    api.post<MoneyRequestResponse>(`/api/v1/money-requests/${id}/pay`, { fromAccountId }),

  decline: (id: string) => api.patch<MoneyRequestResponse>(`/api/v1/money-requests/${id}/decline`),

  /** Only the person who raised it may withdraw it. */
  cancel: (id: string) => api.patch<MoneyRequestResponse>(`/api/v1/money-requests/${id}/cancel`),
};
