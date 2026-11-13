import { api, newIdempotencyKey } from './client';
import type {
  CurrencyCode,
  LedgerEntryResponse,
  PageResponse,
  TransactionCategory,
  TransactionResponse,
  TransferResponse,
  TransferStatus,
} from './types';

export interface CreateTransferInput {
  fromAccountId: string;
  toAccountNumber: string;
  amount: string;
  currency: CurrencyCode;
  description?: string;
  /** Optional. Omit it and the server derives one from the description. */
  category?: TransactionCategory;
}

export interface TransactionQuery {
  page?: number;
  size?: number;
  status?: TransferStatus | '';
  from?: string;
  to?: string;
  minAmount?: string;
  maxAmount?: string;
  category?: TransactionCategory | '';
  /** Free text, matched against the description and the reference. */
  q?: string;
}

/** Builds a query string from only the filters that were actually supplied. */
function buildQuery(query: TransactionQuery): string {
  const params = new URLSearchParams({
    page: String(query.page ?? 0),
    size: String(query.size ?? 20),
    sort: 'createdAt,desc',
  });
  if (query.status) params.set('status', query.status);
  if (query.from) params.set('from', query.from);
  if (query.to) params.set('to', query.to);
  if (query.minAmount) params.set('minAmount', query.minAmount);
  if (query.maxAmount) params.set('maxAmount', query.maxAmount);
  if (query.category) params.set('category', query.category);
  if (query.q) params.set('q', query.q);
  return params.toString();
}

export const transfersApi = {
  /** Creates a transfer. */
  create: (input: CreateTransferInput, idempotencyKey: string) =>
    api.post<TransferResponse>('/api/v1/transfers', input, { 'Idempotency-Key': idempotencyKey }),

  get: (id: string) => api.get<TransferResponse>(`/api/v1/transfers/${id}`),

  ledger: (id: string) => api.get<LedgerEntryResponse[]>(`/api/v1/transfers/${id}/ledger`),

  transactions: (query: TransactionQuery = {}) =>
    api.get<PageResponse<TransactionResponse>>(`/api/v1/transactions?${buildQuery(query)}`),

  /** Alias used by the command palette, where "list transactions" reads better. */
  listTransactions: (query: TransactionQuery = {}) =>
    api.get<PageResponse<TransactionResponse>>(`/api/v1/transactions?${buildQuery(query)}`),

  /** Changes what a transaction was for. */
  recategorise: (id: string, category: TransactionCategory) =>
    api.patch<TransactionResponse>(`/api/v1/transfers/${id}/category`, { category }),
};

export { newIdempotencyKey };
