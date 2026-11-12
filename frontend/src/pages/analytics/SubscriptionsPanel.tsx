import { RefreshCw } from 'lucide-react';
import type { CurrencyCode } from '../../api/types';
import { Skeleton, Surface } from '../../components/ui';
import { EmptyState } from '../../components/States';
import { formatDay } from '../../utils/format';
import { formatMinorUnits } from '../../utils/money';
import { monthlyTotal, type Subscription } from '../../utils/subscriptions';

/* ---------------------------------------------------------------------------- */

/**
 * Recurring payments.
 *
 * Inferred from the customer's own settled transactions rather than fetched
 * from a merchant feed, because there is not one — see utils/subscriptions.ts.
 * The heading says "looks recurring" for that reason: the panel is telling the
 * customer what MBank can see in their statement, not asserting a fact about
 * their contracts.
 */
export function SubscriptionsPanel({
  subscriptions,
  loading,
  currency,
}: {
  subscriptions: Subscription[];
  loading: boolean;
  currency: CurrencyCode;
}) {
  if (loading) return <Skeleton height="160px" />;

  return (
    <section className="section">
      <div className="section__head">
        <h2 className="section__title">Subscriptions</h2>
        {subscriptions.length > 0 && (
          <span className="amount">
            {formatMinorUnits(monthlyTotal(subscriptions), currency)}
            <span className="dim"> a month</span>
          </span>
        )}
      </div>

      <Surface className="list-panel">
        {subscriptions.length === 0 ? (
          <EmptyState
            title="Nothing looks recurring yet"
            body="Payments to the same place in two or more months will be grouped here."
            icon={<RefreshCw />}
          />
        ) : (
          subscriptions.map((subscription) => (
            <div className="txn" key={subscription.name}>
              <span className="txn__glyph" aria-hidden="true">
                <RefreshCw />
              </span>
              <span className="txn__body">
                <span className="txn__title truncate">{subscription.name}</span>
                <span className="txn__meta truncate">
                  Seen in {subscription.months} months · next around {formatDay(subscription.nextDue)}
                </span>
              </span>
              <span className="txn__right">
                <span className="amount">{formatMinorUnits(subscription.amountMinor, subscription.currency)}</span>
              </span>
            </div>
          ))
        )}
      </Surface>

      <p className="analytics__note">
        Worked out from your statement: the same payee, in two or more months, for about the same
        amount. MBank has no feed from the merchants themselves, so an annual plan or a renamed
        payment will not appear here.
      </p>
    </section>
  );
}
