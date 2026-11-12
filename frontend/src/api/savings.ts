import { api } from './client';
import type { CurrencyCode, GoalAccent, SavingsGoalResponse } from './types';

export interface SavingsGoalBody {
  name: string;
  targetAmount: string;
  currency: CurrencyCode;
  deadline?: string;
  accent?: GoalAccent;
}

export interface ContributionBody {
  /** The spending account the money moves from, or back to. */
  accountId: string;
  amount: string;
}

/**
 * Savings goals.
 *
 * Contributions and withdrawals are POSTs against the goal rather than PATCHes
 * of a balance, because there is no balance field to set: each one performs a
 * real settled transfer between two of the customer's own accounts.
 */
export const savingsApi = {
  list: () => api.get<SavingsGoalResponse[]>('/api/v1/savings/goals'),

  create: (body: SavingsGoalBody) => api.post<SavingsGoalResponse>('/api/v1/savings/goals', body),

  update: (id: string, body: SavingsGoalBody) =>
    api.put<SavingsGoalResponse>(`/api/v1/savings/goals/${id}`, body),

  contribute: (id: string, body: ContributionBody) =>
    api.post<SavingsGoalResponse>(`/api/v1/savings/goals/${id}/contributions`, body),

  withdraw: (id: string, body: ContributionBody) =>
    api.post<SavingsGoalResponse>(`/api/v1/savings/goals/${id}/withdrawals`, body),

  archive: (id: string) => api.patch<void>(`/api/v1/savings/goals/${id}/archive`),
};
