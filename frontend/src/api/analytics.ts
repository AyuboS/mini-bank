import { api } from './client';
import type { CurrencyCode, InsightResponse, MonthlySummary } from './types';
import { browserTimezone } from '../utils/format';

/** Spending analytics. */
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
