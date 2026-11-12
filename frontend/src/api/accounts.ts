import { api } from './client';
import type {
  AccountResponse,
  CurrencyCode,
  PageResponse,
  PayeeLookupResponse,
  TransactionResponse,
  TransferResponse,
} from './types';

export const accountsApi = {
  list: () => api.get<AccountResponse[]>('/api/v1/accounts'),

  get: (id: string) => api.get<AccountResponse>(`/api/v1/accounts/${id}`),

  open: (currency: CurrencyCode) => api.post<AccountResponse>('/api/v1/accounts', { currency }),

  transactions: (id: string, page = 0, size = 20) =>
    api.get<PageResponse<TransactionResponse>>(
      `/api/v1/accounts/${id}/transactions?page=${page}&size=${size}`,
    ),

  /**
   * Who an account number belongs to.
   *
   * Confirmation of payee: shown before a transfer is reviewed, so a mistyped
   * digit is caught while it still costs nothing. Returns a first name and last
   * initial only.
   */
  /** Adds play money to one of your own spending accounts. Reuse the key when retrying the same top-up. */
  topUp: (id: string, amount: string, idempotencyKey: string) =>
    api.post<TransferResponse>(`/api/v1/accounts/${id}/top-up`, { amount }, { 'Idempotency-Key': idempotencyKey }),

  lookupPayee: (accountNumber: string) =>
    api.get<PayeeLookupResponse>(`/api/v1/accounts/lookup?accountNumber=${encodeURIComponent(accountNumber)}`),
};
