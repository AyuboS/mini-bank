import { api } from './client';
import type {
  AccountResponse,
  CurrencyCode,
  PayeeLookupResponse,
  TransferResponse,
} from './types';

export const accountsApi = {
  list: () => api.get<AccountResponse[]>('/api/v1/accounts'),

  open: (currency: CurrencyCode) => api.post<AccountResponse>('/api/v1/accounts', { currency }),

  /** Who an account number belongs to. */
  /** Adds play money to one of your own spending accounts. Reuse the key when retrying the same top-up. */
  topUp: (id: string, amount: string, idempotencyKey: string) =>
    api.post<TransferResponse>(`/api/v1/accounts/${id}/top-up`, { amount }, { 'Idempotency-Key': idempotencyKey }),

  lookupPayee: (accountNumber: string) =>
    api.get<PayeeLookupResponse>(`/api/v1/accounts/lookup?accountNumber=${encodeURIComponent(accountNumber)}`),
};
