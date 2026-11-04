import { api } from './client';
import type { CurrencyCode, InsightResponse, MonthlySummary } from './types';
import { browserTimezone } from '../utils/format';

/**
 * Spending analytics.
 *
 * Everything returned is arithmetic over the caller's own settled transfers.
 * There is no endpoint here that estimates, projects or compares against other
 * customers, and `insights` returns an empty list rather than inventing an
 * observation when the history cannot support one.
 */
export const analyticsApi = {
  summary: (month?: string, currency: CurrencyCode = 'EUR') => {
    const params = new URLSearchParams({ currency, timezone: browserTimezone() });
    if (month) params.set('month', month);
    return api.get<MonthlySummary>(`/api/v1/analytics/summary?${params}`);
  },

  insights: (currency: CurrencyCode = 'EUR') =>
    api.get<InsightResponse[]>(
      `/api/v1/analytics/insights?currency=${currency}&timezone=${encodeURIComponent(browserTimezone())}`,
    ),
};
