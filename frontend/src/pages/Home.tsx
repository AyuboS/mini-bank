import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { accountsApi } from '../api/accounts';
import { analyticsApi } from '../api/analytics';
import { cardsApi } from '../api/cards';
import { scheduledApi } from '../api/scheduled';
import { transfersApi } from '../api/transfers';
import type { CardResponse, ScheduledTransferResponse, TransactionResponse } from '../api/types';
import { ErrorState } from '../components/States';
import { TransactionDetail } from '../components/TransactionDetail';
import { TransactionList } from '../components/TransactionList';
import { Skeleton } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { LOCALE, formatMoney } from '../utils/format';
import { formatMinorUnits, sumMinorUnits } from '../utils/money';

const RECENT_COUNT = 8;
const UPCOMING_COUNT = 4;

/**
 * Home: how much there is, what can be done with it, and what happened lately.
 * Upcoming payments and the card sit in a side column on a wide screen.
 */
export function HomePage() {
  const [selected, setSelected] = useState<TransactionResponse | null>(null);

  const accounts = useAsync(() => accountsApi.list(), []);
  const summary = useAsync(() => analyticsApi.summary(), []);
  const recent = useAsync(() => transfersApi.transactions({ size: RECENT_COUNT }), []);

  // Only one currency is totalled: adding EUR to USD needs a rate this product does not have.
  const balance = useMemo(() => {
    const list = accounts.data ?? [];
    const currency = list.find((account) => account.purpose === 'SPENDING')?.currency ?? 'EUR';
    const sameCurrency = list.filter((account) => account.currency === currency);
    const balancesOf = (purpose: 'SPENDING' | 'SAVINGS') =>
      sameCurrency.filter((account) => account.purpose === purpose).map((account) => account.balance);
    return {
      currency,
      spendable: sumMinorUnits(balancesOf('SPENDING')),
      saved: sumMinorUnits(balancesOf('SAVINGS')),
      hasOtherCurrencies: list.some((account) => account.currency !== currency),
    };
  }, [accounts.data]);

  return (
    <div className="home">
      <div className="home__main">
        <section className="home-balance" aria-labelledby="home-balance-label">
          <h1 className="sr-only">Home</h1>
          <p className="home-balance__label" id="home-balance-label">
            Available to spend
          </p>

          {accounts.loading ? (
            <Skeleton height="2.75rem" width="220px" />
          ) : accounts.error ? (
            <ErrorState title={accounts.error.title} body={accounts.error.body} onRetry={accounts.reload} />
          ) : (
            <>
              <p className="balance">{formatMinorUnits(balance.spendable, balance.currency)}</p>
              <p className="home-balance__sub">
                {balance.saved > 0 && (
                  <span>
                    {formatMinorUnits(balance.saved, balance.currency)} in{' '}
                    <Link className="link" to="/savings">
                      savings
                    </Link>
                  </span>
                )}
                {balance.hasOtherCurrencies && (
                  <Link className="link" to="/accounts">
                    Other currencies
                  </Link>
                )}
              </p>
            </>
          )}

          <nav className="home-actions" aria-label="Move money">
            <Link className="btn btn-primary" to="/payments/send">
              Send
            </Link>
            <Link className="btn btn-secondary" to="/payments/requests">
              Request
            </Link>
            <Link className="btn btn-secondary" to="/top-up">
              Top up
            </Link>
            <Link className="btn btn-secondary" to="/savings">
              Move to savings
            </Link>
          </nav>

          <dl className="home-month">
            <div>
              <dt>In this month</dt>
              <dd className="home-month__in">
                {summary.data ? `+${formatMoney(summary.data.income, balance.currency)}` : '—'}
              </dd>
            </div>
            <div>
              <dt>Out this month</dt>
              <dd>{summary.data ? `−${formatMoney(summary.data.spending, balance.currency)}` : '—'}</dd>
            </div>
          </dl>
        </section>

        <section className="home-section" aria-labelledby="home-recent-title">
          <header className="home-section__head">
            <h2 id="home-recent-title">Recent transactions</h2>
            <Link className="link" to="/transactions">
              View all
            </Link>
          </header>

          {recent.loading ? (
            <div className="home-loading" role="status" aria-busy="true">
              <span className="sr-only">Loading transactions</span>
              {Array.from({ length: 5 }, (_, index) => (
                <Skeleton key={index} height="1.25rem" />
              ))}
            </div>
          ) : recent.error ? (
            <ErrorState title={recent.error.title} body={recent.error.body} onRetry={recent.reload} />
          ) : (recent.data?.content.length ?? 0) === 0 ? (
            <p className="home-empty">No transactions yet. They appear here as soon as money moves.</p>
          ) : (
            <TransactionList transactions={recent.data?.content ?? []} onSelect={setSelected} />
          )}
        </section>
      </div>

      <aside className="home__rail">
        <UpcomingPayments />
        <PrimaryCard />
      </aside>

      <TransactionDetail
        transaction={selected}
        onClose={() => setSelected(null)}
        onRecategorised={(updated) => {
          setSelected(updated);
          recent.setData((page) =>
            page && {
              ...page,
              content: page.content.map((row) => (row.transferId === updated.transferId ? updated : row)),
            },
          );
        }}
      />
    </div>
  );
}

const FREQUENCY_LABEL: Record<string, string> = { MONTHLY: 'Monthly', WEEKLY: 'Weekly' };

function UpcomingPayments() {
  const scheduled = useAsync(() => scheduledApi.list(), []);
  const due: ScheduledTransferResponse[] = (scheduled.data ?? [])
    .filter((order) => order.status === 'SCHEDULED')
    .sort((a, b) => a.nextRunOn.localeCompare(b.nextRunOn))
    .slice(0, UPCOMING_COUNT);

  return (
    <section aria-labelledby="home-upcoming-title">
      <header className="home-section__head">
        <h2 id="home-upcoming-title">Upcoming</h2>
        <Link className="link" to="/payments/scheduled">
          Scheduled
        </Link>
      </header>

      {scheduled.loading ? (
        <Skeleton height="1.25rem" />
      ) : scheduled.error ? (
        <p className="home-empty">Scheduled payments could not be loaded.</p>
      ) : due.length === 0 ? (
        <p className="home-empty">Nothing scheduled.</p>
      ) : (
        <ul className="upcoming">
          {due.map((order) => (
            <li className="upcoming__row" key={order.id}>
              <span className="upcoming__main">
                <span className="truncate">{order.payeeName ?? order.description ?? 'Payment'}</span>
                <span className="upcoming__meta">
                  {new Date(order.nextRunOn).toLocaleDateString(LOCALE, { day: 'numeric', month: 'short' })}
                  {' · '}
                  {FREQUENCY_LABEL[order.frequency] ?? 'One-off'}
                </span>
              </span>
              <span className="amount">{formatMoney(order.amount, order.currency)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

const CARD_STATUS_LABEL: Record<string, string> = { ACTIVE: 'Active', FROZEN: 'Frozen' };

function PrimaryCard() {
  const cards = useAsync(() => cardsApi.list(), []);
  const card: CardResponse | undefined =
    (cards.data ?? []).find((candidate) => candidate.status === 'ACTIVE') ?? cards.data?.[0];

  return (
    <section aria-labelledby="home-card-title">
      <header className="home-section__head">
        <h2 id="home-card-title">Card</h2>
        <Link className="link" to="/cards">
          {card ? 'Manage' : 'Create a card'}
        </Link>
      </header>

      {cards.loading ? (
        <Skeleton height="1.25rem" />
      ) : cards.error ? (
        <p className="home-empty">Cards could not be loaded.</p>
      ) : !card ? (
        <p className="home-empty">You have no cards yet.</p>
      ) : (
        <>
          <div className="card-face" role="img" aria-label={`${card.label} card ending ${card.lastFour}`}>
            <span className="card-face__brand">MBank</span>
            <span className="card-face__type">{card.type === 'VIRTUAL' ? 'Virtual' : 'Physical'}</span>
            <span className="card-face__number">•••• {card.lastFour}</span>
            <span className="card-face__holder">{card.cardholderName}</span>
            <span className="card-face__expiry">
              {String(card.expirationMonth).padStart(2, '0')}/{String(card.expirationYear).slice(-2)}
            </span>
          </div>
          <p className="home-card-meta">
            {card.label} · {CARD_STATUS_LABEL[card.status] ?? card.status}
          </p>
        </>
      )}
    </section>
  );
}
