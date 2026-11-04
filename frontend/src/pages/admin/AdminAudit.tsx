import { useState } from 'react';
import { adminApi } from '../../api/admin';
import type { AuditLogResponse, PageResponse } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { ErrorState } from '../../components/States';
import { Badge, Button, Surface, Skeleton, Tabs } from '../../components/ui';
import { formatDateTime, humanise } from '../../utils/format';

/** The actions worth filtering to. The API accepts the full enum. */
const FILTERS = [
  { id: '', label: 'Everything' },
  { id: 'LOGIN_FAILED', label: 'Failed logins' },
  { id: 'TRANSFER_COMPLETED', label: 'Transfers' },
  { id: 'TRANSFER_BLOCKED_BY_RISK', label: 'Risk blocks' },
  { id: 'ACCOUNT_FROZEN', label: 'Freezes' },
  { id: 'TOKEN_REUSE_DETECTED', label: 'Token reuse' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];

export function AdminAudit() {
  const [filter, setFilter] = useState<Filter>('');
  const [page, setPage] = useState(0);

  const { data, loading, error, reload } = useAsync<PageResponse<AuditLogResponse>>(
    () => adminApi.auditLogs(page, 25, filter || undefined),
    [page, filter],
  );

  return (
    <>
      <Surface tone="chrome" className="filter-bar">
        <Tabs
          tabs={FILTERS}
          active={filter}
          label="Filter the audit trail"
          onChange={(value) => {
            setFilter(value as Filter);
            setPage(0);
          }}
        />
      </Surface>

      {loading && <Skeleton height={360} />}

      {!loading && (error || !data) && (
        <ErrorState body={error?.body ?? 'We could not load the audit trail.'} onRetry={reload} />
      )}

      {!loading && data && (
        <>
          <Surface className="table-wrap">
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Action</th>
                    <th>Actor</th>
                    <th>Outcome</th>
                    <th>Request</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((entry) => (
                    <tr key={entry.id}>
                      <td style={{ whiteSpace: 'nowrap' }} className="dim">
                        {formatDateTime(entry.createdAt)}
                      </td>
                      <td>
                        <div style={{ fontWeight: 540 }}>{humanise(entry.action)}</div>
                        {entry.entityType && (
                          <div className="dim" style={{ fontSize: 11 }}>
                            {entry.entityType}
                          </div>
                        )}
                      </td>
                      <td>{entry.username ?? <span className="dim">anonymous</span>}</td>
                      <td>
                        <Badge tone={entry.outcome === 'SUCCESS' ? 'success' : 'danger'}>
                          {humanise(entry.outcome)}
                        </Badge>
                      </td>
                      {/* The same id the client saw in its response headers, which is
                          what makes an audit row traceable back to one request. */}
                      <td className="mono dim" style={{ fontSize: 11 }}>
                        {entry.requestId?.slice(0, 8) ?? '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Surface>

          <div className="pager">
            <Button variant="ghost" disabled={data.first} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <span className="small dim">
              Page {data.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} records
            </span>
            <Button variant="ghost" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>

          <p className="field-hint" style={{ marginTop: 'var(--space-4)' }}>
            The audit trail is append-only. There is no endpoint that edits or deletes a record.
          </p>
        </>
      )}
    </>
  );
}
