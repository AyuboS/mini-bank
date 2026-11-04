import { useState } from 'react';
import { AlertTriangle, Check, RotateCcw, X } from 'lucide-react';
import { adminApi } from '../../api/admin';
import type { PageResponse, TransferResponse, TransferStatus } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { useToast } from '../../components/ToastProvider';
import { RiskGauge } from '../../components/RiskGauge';
import { ErrorState } from '../../components/States';
import {
  Button,
  Surface,
  Input,
  Sheet,
  Skeleton,
  Tabs,
  StatusBadge,
} from '../../components/ui';
import { formatDateTime, formatMoney } from '../../utils/format';
import { toFriendlyError } from '../../utils/errors';

const FILTERS = [
  { id: 'PENDING', label: 'Awaiting review' },
  { id: 'COMPLETED', label: 'Settled' },
  { id: 'FAILED', label: 'Failed' },
  { id: 'REVERSED', label: 'Reversed' },
  { id: '', label: 'All' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];
type Action = 'approve' | 'reject' | 'reverse';

export function AdminTransfers() {
  const toast = useToast();
  const [filter, setFilter] = useState<Filter>('PENDING');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<TransferResponse | null>(null);
  const [action, setAction] = useState<Action | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const { data, loading, error, reload } = useAsync<PageResponse<TransferResponse>>(
    () => adminApi.transfers(page, 12, filter as TransferStatus | ''),
    [page, filter],
  );

  const run = async () => {
    if (!selected || !action) return;
    setBusy(true);
    try {
      if (action === 'approve') {
        const settled = await adminApi.approve(selected.id);
        toast.success('Transfer approved', `${settled.reference} has settled.`);
      } else if (action === 'reject') {
        await adminApi.reject(selected.id, reason);
        toast.success('Transfer rejected', 'No money moved.');
      } else {
        const reversal = await adminApi.reverse(selected.id, reason);
        toast.success('Transfer reversed', `Compensating transfer ${reversal.reference} created.`);
      }
      setSelected(null);
      setAction(null);
      setReason('');
      await reload();
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const openAction = (transfer: TransferResponse, next: Action) => {
    setSelected(transfer);
    setAction(next);
    setReason('');
  };

  return (
    <>
      <Surface tone="chrome" className="filter-bar">
        <Tabs
          tabs={FILTERS}
          active={filter}
          label="Filter transfers"
          onChange={(value) => {
            setFilter(value as Filter);
            setPage(0);
          }}
        />
      </Surface>

      {loading && <Skeleton height={320} />}

      {!loading && (error || !data) && (
        <ErrorState body={error?.body ?? 'We could not load transfers.'} onRetry={reload} />
      )}

      {!loading && data && data.content.length === 0 && (
        <Surface style={{ padding: 'var(--space-7)' }}>
          <p className="small dim" style={{ textAlign: 'center' }}>
            No transfers match this filter.
          </p>
        </Surface>
      )}

      {!loading && data && data.content.length > 0 && (
        <>
          <Surface className="table-wrap">
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>From → To</th>
                    <th>Amount</th>
                    <th>Risk</th>
                    <th>Status</th>
                    <th aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((transfer) => (
                    <tr key={transfer.id}>
                      <td>
                        <div className="mono">{transfer.reference}</div>
                        <div className="dim" style={{ fontSize: 11 }}>
                          {formatDateTime(transfer.createdAt)}
                        </div>
                      </td>
                      <td className="mono" style={{ fontSize: 11 }}>
                        <div>{transfer.fromAccountNumber}</div>
                        <div className="dim">→ {transfer.toAccountNumber}</div>
                      </td>
                      <td className="tnum" style={{ fontWeight: 560, whiteSpace: 'nowrap' }}>
                        {formatMoney(transfer.amount, transfer.currency)}
                      </td>
                      <td>
                        {transfer.risk && transfer.risk.decision !== 'ALLOW' ? (
                          <button
                            type="button"
                            className="chip"
                            onClick={() => {
                              setSelected(transfer);
                              setAction(null);
                            }}
                          >
                            <AlertTriangle aria-hidden="true" style={{ width: 13, height: 13 }} />
                            {transfer.risk.riskScore}
                          </button>
                        ) : (
                          <span className="dim">—</span>
                        )}
                      </td>
                      <td>
                        <StatusBadge status={transfer.status} />
                      </td>
                      <td>
                        <div className="row gap-2" style={{ justifyContent: 'flex-end' }}>
                          {transfer.status === 'PENDING' && (
                            <>
                              <Button
                                variant="secondary"
                                className="btn-icon"
                                onClick={() => openAction(transfer, 'approve')}
                                aria-label={`Approve ${transfer.reference}`}
                              >
                                <Check aria-hidden="true" />
                              </Button>
                              <Button
                                variant="danger"
                                className="btn-icon"
                                onClick={() => openAction(transfer, 'reject')}
                                aria-label={`Reject ${transfer.reference}`}
                              >
                                <X aria-hidden="true" />
                              </Button>
                            </>
                          )}
                          {transfer.status === 'COMPLETED' && (
                            <Button
                              variant="secondary"
                              className="btn-icon"
                              onClick={() => openAction(transfer, 'reverse')}
                              aria-label={`Reverse ${transfer.reference}`}
                            >
                              <RotateCcw aria-hidden="true" />
                            </Button>
                          )}
                        </div>
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
              Page {data.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} total
            </span>
            <Button variant="ghost" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </>
      )}

      {/* Risk detail, opened from the score chip. */}
      <Sheet
        open={selected !== null && action === null}
        onClose={() => setSelected(null)}
        title="Risk review"
        subtitle={selected?.reference}
        wide
      >
        {selected?.risk && (
          <>
            <div className="review-amount">
              <div className="review-amount__value">
                {formatMoney(selected.amount, selected.currency)}
              </div>
              <div className="review-amount__to">
                {selected.fromAccountNumber} → {selected.toAccountNumber}
              </div>
            </div>

            <hr className="divider" />

            <div className="risk">
              <RiskGauge score={selected.risk.riskScore} decision={selected.risk.decision} />
              <div className="risk__reasons">
                <span className="label">Decision: {selected.risk.decision}</span>
                {selected.risk.reasons.length === 0 ? (
                  <span className="small dim">No individual rule explanations were recorded.</span>
                ) : (
                  selected.risk.reasons.map((entry) => (
                    <span className="risk__reason" key={entry}>
                      <AlertTriangle aria-hidden="true" />
                      {entry}
                    </span>
                  ))
                )}
              </div>
            </div>

            {selected.status === 'PENDING' && (
              <div className="modal-actions">
                <Button variant="danger" onClick={() => setAction('reject')}>
                  Reject
                </Button>
                <Button variant="primary" onClick={() => setAction('approve')}>
                  Approve
                </Button>
              </div>
            )}
          </>
        )}
      </Sheet>

      {/* Confirmation, with a reason where the API requires one. */}
      <Sheet
        open={action !== null}
        onClose={() => {
          setAction(null);
          setSelected(null);
        }}
        title={
          action === 'approve' ? 'Approve transfer?' : action === 'reject' ? 'Reject transfer?' : 'Reverse transfer?'
        }
        subtitle={selected?.reference}
      >
        <p className="small dim">
          {action === 'approve' &&
            'Balances are re-checked under lock at this point, so approval can still fail if the funds are no longer there.'}
          {action === 'reject' && 'The transfer is marked failed. No money moves.'}
          {action === 'reverse' &&
            'A compensating transfer is created with opposite ledger entries. The original is kept and marked reversed.'}
        </p>

        {action !== 'approve' && (
          <div style={{ marginTop: 'var(--space-5)' }}>
            <Input
              label="Reason"
              placeholder="Confirmed with customer by phone"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={200}
              required
              hint="Recorded in the audit trail."
            />
          </div>
        )}

        <div className="modal-actions">
          <Button
            variant="secondary"
            onClick={() => {
              setAction(null);
              setSelected(null);
            }}
            disabled={busy}
          >
            Cancel
          </Button>
          <Button
            variant={action === 'approve' ? 'primary' : 'danger'}
            loading={busy}
            disabled={action !== 'approve' && reason.trim().length === 0}
            onClick={run}
          >
            {action === 'approve' ? 'Approve' : action === 'reject' ? 'Reject' : 'Reverse'}
          </Button>
        </div>
      </Sheet>
    </>
  );
}
