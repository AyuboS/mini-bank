import { AlertTriangle, ArrowLeftRight, CreditCard, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api/admin';
import { useAsync } from '../../hooks/useAsync';
import { ErrorState } from '../../components/States';
import { Surface, Skeleton, Stat, StatusBadge } from '../../components/ui';
import { LOCALE, formatDate, formatMoney } from '../../utils/format';
import type { PageResponse, TransferResponse } from '../../api/types';

interface Metrics {
  users: number;
  accounts: number;
  transfers: number;
  flagged: number;
  held: PageResponse<TransferResponse>;
}

export function AdminOverview() {
  /*
    There is no dedicated metrics endpoint, and inventing figures would be
    worse than useless on a screen an operator is meant to trust. Each count is
    the `totalElements` of a real paged query, asked for one row at a time so
    the payload stays tiny.
  */
  const { data, loading, error, reload } = useAsync<Metrics>(async () => {
    const [users, accounts, transfers, flagged, held] = await Promise.all([
      adminApi.users(0, 1),
      adminApi.accounts(0, 1),
      adminApi.transfers(0, 1),
      adminApi.transfers(0, 1, 'FAILED'),
      adminApi.transfers(0, 6, 'PENDING'),
    ]);
    return {
      users: users.totalElements,
      accounts: accounts.totalElements,
      transfers: transfers.totalElements,
      flagged: flagged.totalElements,
      held,
    };
  });

  if (loading) {
    return (
      <div className="stat-grid">
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton key={index} height={112} />
        ))}
      </div>
    );
  }

  if (error || !data) {
    return <ErrorState body={error?.body ?? 'We could not load the overview.'} onRetry={reload} />;
  }

  return (
    <>
      <div className="stat-grid">
        <Stat label="Customers" icon={<Users aria-hidden="true" />} value={data.users.toLocaleString(LOCALE)} />
        <Stat
          label="Accounts"
          icon={<CreditCard aria-hidden="true" />}
          value={data.accounts.toLocaleString(LOCALE)}
        />
        <Stat
          label="Transfers"
          icon={<ArrowLeftRight aria-hidden="true" />}
          value={data.transfers.toLocaleString(LOCALE)}
        />
        <Stat
          label="Refused or failed"
          icon={<AlertTriangle aria-hidden="true" />}
          value={data.flagged.toLocaleString(LOCALE)}
        />
      </div>

      <section className="section">
        <div className="section__head">
          <h3 className="section__title">Awaiting review</h3>
          <Link className="btn btn-ghost" to="/admin/transfers">
            All transfers
          </Link>
        </div>

        {data.held.content.length === 0 ? (
          <Surface style={{ padding: 'var(--space-6)' }}>
            <p className="small dim" style={{ textAlign: 'center' }}>
              Nothing is held for review. Money is moving freely.
            </p>
          </Surface>
        ) : (
          <div className="txn-list">
            {data.held.content.map((transfer) => (
              <Surface key={transfer.id} interactive className="txn">
                <span className="txn__glyph">
                  <AlertTriangle aria-hidden="true" />
                </span>
                <span className="txn__body">
                  <span className="txn__title">{transfer.description || transfer.reference}</span>
                  <span className="txn__meta">
                    {formatDate(transfer.createdAt)} · {transfer.fromAccountNumber} →{' '}
                    {transfer.toAccountNumber}
                  </span>
                </span>
                <span className="txn__right">
                  <span className="amount">{formatMoney(transfer.amount, transfer.currency)}</span>
                  <StatusBadge status={transfer.status} />
                </span>
              </Surface>
            ))}
            {/* A link styled as a button, not a button wrapping a link: nesting an
                anchor inside a button is invalid and breaks keyboard activation. */}
            <Link
              to="/admin/transfers"
              className="btn btn-secondary btn-block"
              style={{ marginTop: 'var(--space-3)' }}
            >
              Review them
            </Link>
          </div>
        )}
      </section>
    </>
  );
}
