import type { TransactionQuery } from '../api/transfers';
import type { TransactionCategory, TransferStatus } from '../api/types';

/** What the filter controls hold: plain strings, exactly as typed or picked. */
export interface TransactionFilters {
  q: string;
  /** `2026-11-01`, from a date input. */
  from: string;
  to: string;
  category: TransactionCategory | '';
  minAmount: string;
  maxAmount: string;
  status: TransferStatus | '';
}

export const NO_FILTERS: TransactionFilters = {
  q: '',
  from: '',
  to: '',
  category: '',
  minAmount: '',
  maxAmount: '',
  status: '',
};

const AMOUNT = /^\d+(\.\d{1,2})?$/;

export function countActive(filters: TransactionFilters): number {
  return Object.values(filters).filter((value) => value !== '').length;
}

/** Why the filters cannot be sent as they stand, or null when they can. */
export function filterProblem(filters: TransactionFilters): string | null {
  const { from, to, minAmount, maxAmount } = filters;
  if ((minAmount && !AMOUNT.test(minAmount)) || (maxAmount && !AMOUNT.test(maxAmount))) {
    return 'Amounts are numbers with up to two decimals, like 25 or 25.50.';
  }
  if (minAmount && maxAmount && Number(minAmount) > Number(maxAmount)) {
    return 'The minimum amount is larger than the maximum.';
  }
  if (from && to && from > to) {
    return 'The start date is after the end date.';
  }
  return null;
}

/**
 * Turns the controls into API parameters.
 *
 * The API filters on an instant, so a picked day becomes that day's first and
 * last millisecond in the browser's own timezone.
 */
export function toTransactionQuery(filters: TransactionFilters): TransactionQuery {
  const query: TransactionQuery = {};
  const q = filters.q.trim();
  if (q) query.q = q;
  if (filters.from) query.from = new Date(`${filters.from}T00:00:00`).toISOString();
  if (filters.to) query.to = new Date(`${filters.to}T23:59:59.999`).toISOString();
  if (filters.category) query.category = filters.category;
  if (filters.minAmount) query.minAmount = filters.minAmount;
  if (filters.maxAmount) query.maxAmount = filters.maxAmount;
  if (filters.status) query.status = filters.status;
  return query;
}
