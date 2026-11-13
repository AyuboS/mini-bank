import { describe, expect, it } from 'vitest';
import type { TransactionResponse } from '../api/types';
import { findSubscriptions, monthlyTotal } from './subscriptions';

function charge(description: string, amount: string, createdAt: string): TransactionResponse {
  return {
    transferId: `${description}-${createdAt}`,
    reference: 'TRX',
    direction: 'DEBIT',
    amount,
    currency: 'EUR',
    status: 'COMPLETED',
    description,
    createdAt,
  } as TransactionResponse;
}

describe('subscriptions', () => {
  it('finds a monthly charge and totals in whole cents', () => {
    const found = findSubscriptions([
      charge('Netflix', '15.99', '2026-09-03T10:00:00Z'),
      charge('Netflix', '15.99', '2026-10-03T10:00:00Z'),
      charge('Spotify', '0.10', '2026-09-05T10:00:00Z'),
      charge('Spotify', '0.20', '2026-10-05T10:00:00Z'),
    ]);
    expect(found.map((subscription) => subscription.name)).toContain('Netflix');
    expect(found.find((subscription) => subscription.name === 'Netflix')?.amountMinor).toBe(1599);
    expect(monthlyTotal([{ ...found[0]!, amountMinor: 10 }, { ...found[0]!, amountMinor: 20 }])).toBe(30);
  });
});
