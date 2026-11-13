import { useState } from 'react';
import { adminApi } from '../../api/admin';
import type { AuditLogResponse, PageResponse } from '../../api/types';
import { ErrorState } from '../../components/States';
import { Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, humanise } from '../../utils/format';
import { AdminPager, FilterRow } from './AdminParts';

// The actions worth a shortcut. The API accepts the whole enum.
const FILTERS = [
  { id: '', label: 'Everything' },
  { id: 'LOGIN_FAILED', label: 'Failed logins' },
  { id: 'TRANSFER_COMPLETED', label: 'Transfers' },
  { id: 'TOP_UP_COMPLETED', label: 'Top-ups' },
  { id: 'TRANSFER_BLOCKED_BY_RISK', label: 'Risk blocks' },
  { id: 'ACCOUNT_FROZEN', label: 'Freezes' },
  { id: 'TOKEN_REUSE_DETECTED', label: 'Token reuse' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];

/** The append-only audit trail. No endpoint edits or deletes a record. */
export function AdminAudit() {
  const [filter, setFilter] = useState<Filter>('');
  const [page, setPage] = useState(0);

  const { data, loading, error, reload } = useAsync<PageResponse<AuditLogResponse>>(
    () => adminApi.auditLogs(page, 25, filter || undefined),
    [page, filter],
  );

  return (
    <>
      <FilterRow
        label="Filter the audit trail"
        options={FILTERS}
        value={filter}
        onChange={(value) => {
          setFilter(value);
          setPage(0);
        }}
      />

      {loading && !data ? (
        <Skeleton height={360} />
      ) : error || !data ? (
        <ErrorState body={error?.body ?? 'We could not load the audit trail.'} onRetry={reload} />
      ) : data.content.length === 0 ? (
        <p className="quiet-empty">No records match this filter.</p>
      ) : (
        <>
          <table className="admin-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Action</th>
                <th>Who</th>
                <th>Outcome</th>
                <th>Request</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((entry) => (
                <tr key={entry.id}>
                  <td className="admin-table__mono dim">{formatDateTime(entry.createdAt)}</td>
                  <td>
                    {humanise(entry.action)}
                    {entry.entityType && <span className="admin-table__sub">{entry.entityType}</span>}
                  </td>
                  <td>{entry.username ?? <span className="dim">anonymous</span>}</td>
                  <td className={entry.outcome === 'SUCCESS' ? undefined : 'admin-table__flagged'}>
                    {humanise(entry.outcome)}
                  </td>
                  {/* The id the client got in its response headers, so a row traces back to one request. */}
                  <td data-wide-only className="admin-table__mono dim">
                    {entry.requestId?.slice(0, 8) ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <AdminPager page={data} noun="records" onPage={setPage} />
        </>
      )}
    </>
  );
}
