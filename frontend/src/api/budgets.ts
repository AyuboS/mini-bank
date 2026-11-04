import { api } from './client';
import type { BudgetResponse, CurrencyCode, TransactionCategory } from './types';
import { browserTimezone } from '../utils/format';

export interface BudgetBody {
  category: TransactionCategory;
  amount: string;
  currency: CurrencyCode;
}

/**
 * Monthly spending caps.
 *
 * Every call carries the browser's timezone: a budget month is a calendar month
 * on the customer's own calendar, and computing the boundary in UTC would put a
 * late-evening payment on the 31st into the wrong month for anyone east of
 * Greenwich.
 */
export const budgetsApi = {
  list: (currency: CurrencyCode = 'EUR') =>
    api.get<BudgetResponse[]>(
      `/api/v1/budgets?currency=${currency}&timezone=${encodeURIComponent(browserTimezone())}`,
    ),

  /** Creates the budget if there is none, otherwise moves the existing one. */
  set: (body: BudgetBody) =>
    api.put<BudgetResponse>(`/api/v1/budgets?timezone=${encodeURIComponent(browserTimezone())}`, body),

  remove: (id: string) => api.delete<void>(`/api/v1/budgets/${id}`),
};
