import { Link } from 'react-router-dom';
import { adminApi } from '../../api/admin';
import type { PageResponse, TransferResponse } from '../../api/types';
import { ErrorState } from '../../components/States';
import { Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { LOCALE, formatDate, formatMoney } from '../../utils/format';

interface Metrics {
  users: number;
  accounts: number;
  transfers: number;
  failed: number;
  held: PageResponse<TransferResponse>;
}

export function AdminOverview() {
  // No metrics endpoint exists, so each count is the totalElements of a one-row page.
  const { data, loading, error, reload } = useAsync<Metrics>(async () => {
    const [users, accounts, transfers, failed, held] = await Promise.all([
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
      failed: failed.totalElements,
      held,
    };
  });

  if (loading && !data) return <Skeleton height={200} />;
  if (error || !data) return <ErrorState body={error?.body ?? 'We could not load the overview.'} onRetry={reload} />;

  return (
    <>
      <dl className="figures">
        <div>
          <dt>Users</dt>
          <dd className="figures__total">{data.users.toLocaleString(LOCALE)}</dd>
        </div>
        <div>
          <dt>Customer accounts</dt>
          <dd className="figures__total">{data.accounts.toLocaleString(LOCALE)}</dd>
        </div>
        <div>
          <dt>Transfers</dt>
          <dd className="figures__total">{data.transfers.toLocaleString(LOCALE)}</dd>
        </div>
        <div>
          <dt>Refused or failed</dt>
          <dd className="figures__total">{data.failed.toLocaleString(LOCALE)}</dd>
        </div>
      </dl>

      <section className="profile__section" aria-labelledby="held-title">
        <div className="section-title section-title--with-action">
          <h2 id="held-title">Awaiting review</h2>
          <Link className="link" to="/admin/transfers">
            All transfers
          </Link>
        </div>
        {data.held.content.length === 0 ? (
          <p className="quiet-empty">Nothing is held for review.</p>
        ) : (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Transfer</th>
                <th>From → to</th>
                <th className="admin-table__num">Amount</th>
              </tr>
            </thead>
            <tbody>
              {data.held.content.map((transfer) => (
                <tr key={transfer.id}>
                  <td>
                    {transfer.description || transfer.reference}
                    <span className="admin-table__sub">{formatDate(transfer.createdAt)}</span>
                  </td>
                  <td data-wide-only className="admin-table__mono">
                    {transfer.fromAccountNumber} → {transfer.toAccountNumber}
                  </td>
                  <td className="admin-table__num">{formatMoney(transfer.amount, transfer.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
