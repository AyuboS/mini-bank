import { api, downloadFile } from './client';
import type { StatementResponse } from './types';
import { browserTimezone } from '../utils/format';

/**
 * Monthly account statements.
 *
 * The opening balance is derived from the ledger rather than stored, so
 * `openingBalance + totalIn − totalOut` always equals `closingBalance`.
 */
export const statementsApi = {
  get: (accountId: string, month: string) =>
    api.get<StatementResponse>(
      `/api/v1/accounts/${accountId}/statements?month=${month}&timezone=${encodeURIComponent(browserTimezone())}`,
    ),

  /** Fetches with the bearer token attached and hands the bytes to the browser. */
  download: (accountId: string, accountNumber: string, month: string) =>
    downloadFile(
      `/api/v1/accounts/${accountId}/statements/export?month=${month}&timezone=${encodeURIComponent(browserTimezone())}`,
      `mbank-${accountNumber}-${month}.csv`,
    ),
};
