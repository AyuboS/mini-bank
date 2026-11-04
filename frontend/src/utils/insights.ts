import type { CurrencyCode, InsightResponse } from '../api/types';
import { CATEGORY_META } from './categories';
import { formatMoney } from './format';

/**
 * Turns an insight's figures into the sentence a customer reads.
 *
 * The wording lives here rather than on the server for two reasons. Money has to
 * be formatted with the reader's own locale, and a server that formats it
 * produces "€5307.92" next to the client's "€6,650.50" on the same screen.
 * And every sentence a server hard-codes is a sentence no translation can reach;
 * keeping them in one module here is what makes the interface translatable
 * later without touching the analytics.
 *
 * Every string below is a statement about numbers the server computed from real
 * transactions. Nothing here adds a claim the data does not support.
 */
export function describeInsight(
  insight: InsightResponse,
  currency: CurrencyCode,
): { headline: string; detail: string } {
  const amount = insight.amount ?? '0';
  const comparison = insight.comparisonAmount ?? '0';
  const category = insight.category ? CATEGORY_META[insight.category].label : '';

  switch (insight.kind) {
    case 'NET_POSITIVE':
      return {
        headline: `You put aside ${formatMoney(amount, currency)} this month`,
        detail: `${formatMoney(comparison, currency)} of it went out again`,
      };

    case 'NET_NEGATIVE':
      return {
        headline: `You spent ${formatMoney(amount, currency)} more than came in`,
        detail: `${formatMoney(comparison, currency)} went out in total`,
      };

    case 'TOP_CATEGORY':
      return {
        headline: `Your largest category was ${category}`,
        detail: `${formatMoney(amount, currency)}, ${insight.share}% of everything you spent`,
      };

    case 'SPENDING_DOWN':
    case 'SPENDING_UP': {
      const change = Math.abs(insight.percentChange ?? 0);
      const direction = insight.kind === 'SPENDING_DOWN' ? 'less' : 'more';
      return {
        headline: `You spent ${change}% ${direction} on ${category} this month`,
        detail: `${formatMoney(amount, currency)} against ${formatMoney(comparison, currency)} last month`,
      };
    }

    default:
      /*
        An unrecognised kind is rendered as the figures it carries rather than
        dropped. A new insight added on the server should degrade to something
        readable, not disappear from a client that has not been redeployed.
      */
      return {
        headline: category || 'This month',
        detail: formatMoney(amount, currency),
      };
  }
}
