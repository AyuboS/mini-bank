import { describe, expect, it } from 'vitest';
import { NO_FILTERS, countActive, filterProblem, toTransactionQuery } from './transactionFilters';

describe('toTransactionQuery', () => {
  it('sends nothing for empty filters', () => {
    expect(toTransactionQuery(NO_FILTERS)).toEqual({});
  });

  it('covers the whole of both picked days in local time', () => {
    const query = toTransactionQuery({ ...NO_FILTERS, from: '2026-11-01', to: '2026-11-30' });
    expect(new Date(query.from!).getTime()).toBe(new Date(2026, 10, 1, 0, 0, 0, 0).getTime());
    expect(new Date(query.to!).getTime()).toBe(new Date(2026, 10, 30, 23, 59, 59, 999).getTime());
  });

  it('passes the other filters through and trims the search text', () => {
    expect(
      toTransactionQuery({ ...NO_FILTERS, q: '  rent ', category: 'GROCERIES', minAmount: '10', maxAmount: '99.50', status: 'FAILED' }),
    ).toEqual({ q: 'rent', category: 'GROCERIES', minAmount: '10', maxAmount: '99.50', status: 'FAILED' });
  });
});

describe('filterProblem', () => {
  it('accepts empty and well-formed filters', () => {
    expect(filterProblem(NO_FILTERS)).toBeNull();
    expect(filterProblem({ ...NO_FILTERS, minAmount: '5', maxAmount: '5.25', from: '2026-01-01', to: '2026-01-01' })).toBeNull();
  });

  it('rejects malformed amounts, inverted amounts and inverted dates', () => {
    expect(filterProblem({ ...NO_FILTERS, minAmount: '1.234' })).toMatch(/two decimals/);
    expect(filterProblem({ ...NO_FILTERS, minAmount: '50', maxAmount: '9' })).toMatch(/minimum/);
    expect(filterProblem({ ...NO_FILTERS, from: '2026-02-02', to: '2026-02-01' })).toMatch(/start date/);
  });
});

describe('countActive', () => {
  it('counts only the filters that are set', () => {
    expect(countActive(NO_FILTERS)).toBe(0);
    expect(countActive({ ...NO_FILTERS, q: 'a', category: 'DINING' })).toBe(2);
  });
});
