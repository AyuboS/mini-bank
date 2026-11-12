import { api } from './client';
import type {
  CardDesign,
  CardDetailsResponse,
  CardResponse,
  CardSpendingResponse,
  CardType,
  PageResponse,
  TransactionResponse,
} from './types';

export interface CreateCardBody {
  accountId?: string;
  type: CardType;
  design: CardDesign;
  label?: string;
}

export interface CardLimitsBody {
  dailyLimit?: string;
  monthlyLimit?: string;
  atmLimit?: string;
  onlineLimit?: string;
  /*
   * An explicit null clears a cap, so these flags distinguish "not mentioned"
   * from "remove it". A request body cannot tell those apart on its own.
   */
  clearDailyLimit?: boolean;
  clearMonthlyLimit?: boolean;
  clearAtmLimit?: boolean;
  clearOnlineLimit?: boolean;
}

/** Which channels the card may be used through. */
export interface CardControlsBody {
  onlinePayments?: boolean;
  contactless?: boolean;
  atmWithdrawals?: boolean;
  international?: boolean;
  magneticStripe?: boolean;
}

export const cardsApi = {
  list: () => api.get<CardResponse[]>('/api/v1/cards'),

  create: (body: CreateCardBody) => api.post<CardResponse>('/api/v1/cards', body),

  update: (id: string, body: { label?: string; design?: CardDesign }) =>
    api.patch<CardResponse>(`/api/v1/cards/${id}`, body),

  freeze: (id: string) => api.patch<CardResponse>(`/api/v1/cards/${id}/freeze`),

  unfreeze: (id: string) => api.patch<CardResponse>(`/api/v1/cards/${id}/unfreeze`),

  cancel: (id: string) => api.delete<void>(`/api/v1/cards/${id}`),

  updateControls: (id: string, body: CardControlsBody) =>
    api.patch<CardResponse>(`/api/v1/cards/${id}/controls`, body),

  setLimits: (id: string, body: CardLimitsBody) =>
    api.patch<CardResponse>(`/api/v1/cards/${id}/limits`, body),

  spending: (id: string) => api.get<CardSpendingResponse>(`/api/v1/cards/${id}/spending`),

  /**
   * POST, not GET, and deliberately so: the response carries a card number, and a
   * GET would be cacheable, linkable and replayable from history. Every call is
   * audited server-side.
   */
  details: (id: string) => api.post<CardDetailsResponse>(`/api/v1/cards/${id}/details`),

  transactions: (id: string, params: { page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    const suffix = query.toString() ? `?${query}` : '';
    return api.get<PageResponse<TransactionResponse>>(`/api/v1/cards/${id}/transactions${suffix}`);
  },
};
