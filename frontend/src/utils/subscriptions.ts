import type { CurrencyCode, TransactionResponse } from '../api/types';
import { toNumber } from './format';

export interface Subscription {
  /** The merchant, as it appears on the statement. */
  name: string;
  /** The most recent amount charged. */
  amount: number;
  currency: CurrencyCode;
  /** How many distinct months it has been seen in. */
  months: number;
  lastCharged: string;
  /** Roughly when the next one is due, projected from the last charge. */
  nextDue: string;
}

/** Two sightings in two different months is the least that can be called recurring. */
const MIN_MONTHS = 2;

/**
 * Charges that vary by more than this from the average are not the same
 * subscription. A streaming service that puts its price up is still recurring;
 * a supermarket you happen to visit monthly is not, and its amounts scatter.
 */
const MAX_VARIANCE = 0.25;

function monthKey(iso: string): string {
  return iso.slice(0, 7);
}

/**
 * Finds the recurring payments in a run of transactions.
 *
 * ## What this is, and what it is not
 *
 * MBank has no merchant registry and no subscription feed from a card network,
 * so this is inference over the customer's own settled outgoing payments: the
 * same description, in at least two different months, for an amount that does
 * not move much. That is enough to surface Netflix and a gym membership, which
 * is the useful half.
 *
 * It will also miss things — a subscription billed annually, or one whose
 * description changes — and it can be wrong about a regular payment to the same
 * person. The screen presents it as "what looks recurring" rather than as a
 * list the bank knows to be subscriptions, because that is what it is.
 *
 * Amounts are compared as numbers rather than as decimals on purpose: this is
 * grouping and display, never arithmetic that lands in a balance.
 */
export function findSubscriptions(transactions: TransactionResponse[]): Subscription[] {
  const groups = new Map<string, TransactionResponse[]>();

  for (const transaction of transactions) {
    if (transaction.direction !== 'DEBIT') continue;
    if (transaction.status !== 'COMPLETED') continue;
    const name = transaction.description?.trim();
    if (!name) continue;
    // Case-insensitive, so "Spotify" and "SPOTIFY" are one subscription.
    const key = name.toLowerCase();
    const bucket = groups.get(key);
    if (bucket) bucket.push(transaction);
    else groups.set(key, [transaction]);
  }

  const found: Subscription[] = [];

  for (const charges of groups.values()) {
    const months = new Set(charges.map((charge) => monthKey(charge.createdAt)));
    if (months.size < MIN_MONTHS) continue;

    const amounts = charges.map((charge) => toNumber(charge.amount));
    const average = amounts.reduce((total, value) => total + value, 0) / amounts.length;
    if (average <= 0) continue;

    const erratic = amounts.some((value) => Math.abs(value - average) / average > MAX_VARIANCE);
    if (erratic) continue;

    const sorted = [...charges].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    const latest = sorted[0]!;
    const due = new Date(latest.createdAt);
    // Monthly is the only cadence claimed, because it is the only one two
    // sightings can support. Anything else would be a guess presented as a date.
    due.setMonth(due.getMonth() + 1);

    found.push({
      name: latest.description!.trim(),
      amount: toNumber(latest.amount),
      currency: latest.currency,
      months: months.size,
      lastCharged: latest.createdAt,
      nextDue: due.toISOString(),
    });
  }

  return found.sort((a, b) => b.amount - a.amount);
}

/** What the recurring payments come to in a month. */
export function monthlyTotal(subscriptions: Subscription[]): number {
  return subscriptions.reduce((total, subscription) => total + subscription.amount, 0);
}
