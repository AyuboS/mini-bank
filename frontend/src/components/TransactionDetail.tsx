import { useEffect, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { transfersApi } from '../api/transfers';
import type { LedgerEntryResponse, TransactionResponse, TransferResponse } from '../api/types';
import { formatDateTime, formatMoney } from '../utils/format';
import { Sheet, StatusBadge } from './ui';
import { Money } from './Money';
import { RiskGauge } from './RiskGauge';

interface TransactionDetailProps {
  transaction: TransactionResponse | null;
  onClose: () => void;
}

interface Step {
  title: string;
  time?: string;
  state: 'done' | 'current' | 'failed' | 'idle';
}

/** The lifecycle as it actually happened, derived from the transfer's status. */
function stepsFor(transaction: TransactionResponse): Step[] {
  const created: Step = { title: 'Created', time: formatDateTime(transaction.createdAt), state: 'done' };

  switch (transaction.status) {
    case 'COMPLETED':
      return [
        created,
        { title: 'Processing', state: 'done' },
        {
          title: 'Completed',
          ...(transaction.completedAt ? { time: formatDateTime(transaction.completedAt) } : {}),
          state: 'done',
        },
      ];
    case 'REVERSED':
      return [
        created,
        { title: 'Completed', state: 'done' },
        {
          title: 'Reversed',
          ...(transaction.completedAt ? { time: formatDateTime(transaction.completedAt) } : {}),
          state: 'current',
        },
      ];
    case 'FAILED':
      return [created, { title: 'Failed', state: 'failed' }];
    default:
      // PENDING and PROCESSING: money has not moved yet.
      return [
        created,
        { title: 'Awaiting review', state: 'current' },
        { title: 'Completed', state: 'idle' },
      ];
  }
}

export function TransactionDetail({ transaction, onClose }: TransactionDetailProps) {
  const [transfer, setTransfer] = useState<TransferResponse | null>(null);
  const [ledger, setLedger] = useState<LedgerEntryResponse[]>([]);

  useEffect(() => {
    if (!transaction) {
      setTransfer(null);
      setLedger([]);
      return;
    }

    let cancelled = false;
    // The list row carries only what a statement line needs; the full record
    // and its postings are fetched when somebody actually opens one.
    void (async () => {
      try {
        const full = await transfersApi.get(transaction.transferId);
        if (!cancelled) setTransfer(full);
        const entries = await transfersApi.ledger(transaction.transferId);
        if (!cancelled) setLedger(entries);
      } catch {
        /* The summary below is already enough to be useful. */
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [transaction]);

  if (!transaction) {
    return null;
  }

  const steps = stepsFor(transaction);
  const risk = transfer?.risk;

  return (
    <Sheet open onClose={onClose} title="Transaction" subtitle={transaction.reference}>
      <div className="review-amount">
        <div className="review-amount__value">
          <Money
            amount={transaction.amount}
            currency={transaction.currency}
            direction={transaction.direction}
          />
        </div>
        <div style={{ marginTop: 10 }}>
          <StatusBadge status={transaction.status} />
        </div>
      </div>

      <div className="timeline">
        {steps.map((step, index) => (
          <div className="timeline__step" key={step.title}>
            <div className="timeline__rail">
              <span
                className={`timeline__dot ${
                  step.state === 'done'
                    ? 'timeline__dot--done'
                    : step.state === 'current'
                      ? 'timeline__dot--current'
                      : step.state === 'failed'
                        ? 'timeline__dot--failed'
                        : ''
                }`}
              />
              {index < steps.length - 1 && (
                <span className={`timeline__line ${step.state === 'done' ? 'timeline__line--done' : ''}`} />
              )}
            </div>
            <div className="timeline__body">
              <div className="timeline__title">{step.title}</div>
              {step.time && <div className="timeline__time">{step.time}</div>}
            </div>
          </div>
        ))}
      </div>

      {risk && risk.decision !== 'ALLOW' && (
        <>
          <hr className="divider" />
          <div className="risk">
            <RiskGauge score={risk.riskScore} decision={risk.decision} size={92} />
            <div className="risk__reasons">
              <span className="label">Why this was flagged</span>
              {risk.reasons.map((reason) => (
                <span className="risk__reason" key={reason}>
                  <AlertTriangle aria-hidden="true" />
                  {reason}
                </span>
              ))}
            </div>
          </div>
        </>
      )}

      <hr className="divider" />

      <div className="detail-rows">
        <div className="detail-row">
          <span className="detail-row__label">Reference</span>
          <span className="detail-row__value mono">{transaction.reference}</span>
        </div>
        <div className="detail-row">
          <span className="detail-row__label">{transaction.direction === 'CREDIT' ? 'From' : 'To'}</span>
          <span className="detail-row__value mono">{transaction.counterpartyAccountNumber}</span>
        </div>
        <div className="detail-row">
          <span className="detail-row__label">Date</span>
          <span className="detail-row__value">{formatDateTime(transaction.createdAt)}</span>
        </div>
        {transaction.description && (
          <div className="detail-row">
            <span className="detail-row__label">Description</span>
            <span className="detail-row__value">{transaction.description}</span>
          </div>
        )}
        {transfer?.failureReason && (
          <div className="detail-row">
            <span className="detail-row__label">Reason</span>
            <span className="detail-row__value" style={{ color: 'var(--danger)' }}>
              {transfer.failureReason}
            </span>
          </div>
        )}
      </div>

      {ledger.length > 0 && (
        <>
          <hr className="divider" />
          <span className="label">Ledger postings</span>
          <div className="detail-rows" style={{ marginTop: 8 }}>
            {ledger.map((entry) => (
              <div className="detail-row" key={entry.id}>
                <span className="detail-row__label">
                  {entry.entryType === 'DEBIT' ? 'Debit' : 'Credit'} · {entry.accountNumber}
                </span>
                <span className="detail-row__value mono">
                  {formatMoney(entry.amount, entry.currency)}
                </span>
              </div>
            ))}
          </div>
          <p className="field-hint" style={{ marginTop: 10 }}>
            Every transfer posts one debit and one matching credit. They always sum to zero.
          </p>
        </>
      )}
    </Sheet>
  );
}
