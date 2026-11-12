import { useState } from 'react';
import { adminApi } from '../../api/admin';
import type { PageResponse, TransferResponse, TransferStatus } from '../../api/types';
import { RiskGauge } from '../../components/RiskGauge';
import { ErrorState } from '../../components/States';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Sheet, Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { toFriendlyError } from '../../utils/errors';
import { formatDateTime, formatMoney, humanise } from '../../utils/format';
import { AdminPager, FilterRow } from './AdminParts';

const FILTERS = [
  { id: 'PENDING', label: 'Awaiting review' },
  { id: 'COMPLETED', label: 'Settled' },
  { id: 'FAILED', label: 'Failed' },
  { id: 'REVERSED', label: 'Reversed' },
  { id: '', label: 'All' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];
type Action = 'review' | 'approve' | 'reject' | 'reverse';

const CONFIRM_COPY: Record<Exclude<Action, 'review'>, { title: string; body: string; verb: string }> = {
  approve: {
    title: 'Approve transfer?',
    body: 'Balances are checked again under lock, so approval can still fail if the money is no longer there.',
    verb: 'Approve',
  },
  reject: { title: 'Reject transfer?', body: 'The transfer is marked failed. No money moves.', verb: 'Reject' },
  reverse: {
    title: 'Reverse transfer?',
    body: 'A compensating transfer with opposite ledger entries is created. The original stays, marked reversed.',
    verb: 'Reverse',
  },
};

/** Held, settled and failed transfers, with approve, reject and reverse. */
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

  const open = (transfer: TransferResponse, next: Action) => {
    setSelected(transfer);
    setAction(next);
    setReason('');
  };

  const close = () => {
    setSelected(null);
    setAction(null);
  };

  const confirm = async () => {
    if (!selected || !action || action === 'review') return;
    setBusy(true);
    try {
      if (action === 'approve') {
        const settled = await adminApi.approve(selected.id);
        toast.success('Transfer approved', `${settled.reference} has settled.`);
      } else if (action === 'reject') {
        await adminApi.reject(selected.id, reason.trim());
        toast.success('Transfer rejected', 'No money moved.');
      } else {
        const reversal = await adminApi.reverse(selected.id, reason.trim());
        toast.success('Transfer reversed', `Compensating transfer ${reversal.reference}.`);
      }
      close();
      await reload();
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const copy = action && action !== 'review' ? CONFIRM_COPY[action] : null;

  return (
    <>
      <FilterRow
        label="Filter transfers"
        options={FILTERS}
        value={filter}
        onChange={(value) => {
          setFilter(value);
          setPage(0);
        }}
      />

      {loading && !data ? (
        <Skeleton height={320} />
      ) : error || !data ? (
        <ErrorState body={error?.body ?? 'We could not load transfers.'} onRetry={reload} />
      ) : data.content.length === 0 ? (
        <p className="quiet-empty">No transfers match this filter.</p>
      ) : (
        <>
          <table className="admin-table">
            <thead>
              <tr>
                <th>Reference</th>
                <th>From → to</th>
                <th>Risk</th>
                <th>Status</th>
                <th className="admin-table__num">Amount</th>
                <th>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((transfer) => (
                <tr key={transfer.id}>
                  <td>
                    <span className="admin-table__mono">{transfer.reference}</span>
                    <span className="admin-table__sub">{formatDateTime(transfer.createdAt)}</span>
                  </td>
                  <td data-wide-only className="admin-table__mono">
                    {transfer.fromAccountNumber}
                    <span className="admin-table__sub">→ {transfer.toAccountNumber}</span>
                  </td>
                  <td data-wide-only>
                    {transfer.risk && transfer.risk.decision !== 'ALLOW' ? (
                      <button type="button" className="link" onClick={() => open(transfer, 'review')}>
                        {transfer.risk.riskScore} · {humanise(transfer.risk.decision)}
                      </button>
                    ) : (
                      <span className="dim">—</span>
                    )}
                  </td>
                  <td className={transfer.status === 'FAILED' ? 'admin-table__flagged' : undefined}>
                    {humanise(transfer.status)}
                  </td>
                  <td className="admin-table__num">{formatMoney(transfer.amount, transfer.currency)}</td>
                  <td>
                    <div className="admin-table__actions">
                      {transfer.status === 'PENDING' && (
                        <>
                          <Button size="sm" variant="secondary" onClick={() => open(transfer, 'approve')} aria-label={`Approve ${transfer.reference}`}>
                            Approve
                          </Button>
                          <Button size="sm" variant="ghost" onClick={() => open(transfer, 'reject')} aria-label={`Reject ${transfer.reference}`}>
                            Reject
                          </Button>
                        </>
                      )}
                      {transfer.status === 'COMPLETED' && (
                        <Button size="sm" variant="ghost" onClick={() => open(transfer, 'reverse')} aria-label={`Reverse ${transfer.reference}`}>
                          Reverse
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <AdminPager page={data} noun="transfers" onPage={setPage} />
        </>
      )}

      <Sheet open={action === 'review' && selected !== null} onClose={close} title="Risk review" subtitle={selected?.reference} wide>
        {selected?.risk && (
          <>
            <p className="admin-review__amount">{formatMoney(selected.amount, selected.currency)}</p>
            <p className="admin-table__sub admin-table__mono">
              {selected.fromAccountNumber} → {selected.toAccountNumber}
            </p>
            <div className="risk">
              <RiskGauge score={selected.risk.riskScore} decision={selected.risk.decision} />
              <ul className="risk__reasons">
                {selected.risk.reasons.length === 0 ? (
                  <li className="dim">No rule explanations were recorded.</li>
                ) : (
                  selected.risk.reasons.map((entry) => (
                    <li className="risk__reason" key={entry}>
                      {entry}
                    </li>
                  ))
                )}
              </ul>
            </div>
            {selected.status === 'PENDING' && (
              <div className="modal-actions">
                <Button variant="secondary" onClick={() => setAction('reject')}>
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

      <Sheet open={copy !== null} onClose={close} title={copy?.title ?? ''} subtitle={selected?.reference}>
        <form
          className="sheet-form"
          onSubmit={(event) => {
            event.preventDefault();
            void confirm();
          }}
        >
          <p className="admin-note">{copy?.body}</p>
          {action !== 'approve' && (
            <Input
              label="Reason"
              placeholder="Confirmed with the customer by phone"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={200}
              required
              hint="Recorded in the audit trail."
            />
          )}
          <div className="modal-actions">
            <Button type="button" variant="secondary" onClick={close} disabled={busy}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant={action === 'approve' ? 'primary' : 'danger'}
              loading={busy}
              disabled={action !== 'approve' && reason.trim().length === 0}
            >
              {copy?.verb}
            </Button>
          </div>
        </form>
      </Sheet>
    </>
  );
}
