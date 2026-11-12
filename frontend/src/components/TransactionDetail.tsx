import { useEffect, useState } from 'react';
import { transfersApi } from '../api/transfers';
import type {
  LedgerEntryResponse,
  TransactionCategory,
  TransactionResponse,
  TransferResponse,
} from '../api/types';
import { TRANSACTION_CATEGORIES } from '../api/types';
import { CATEGORY_META } from '../utils/categories';
import { toFriendlyError } from '../utils/errors';
import { formatDateTime, formatMoney, formatSignedMoney } from '../utils/format';
import { Select, Sheet } from './ui';
import { STATUS_LABEL } from './TransactionList';

interface TransactionDetailProps {
  transaction: TransactionResponse | null;
  onClose: () => void;
  /** Called with the saved transaction after its category changes, so a list can update its row. */
  onRecategorised?: (updated: TransactionResponse) => void;
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

export function TransactionDetail({ transaction, onClose, onRecategorised }: TransactionDetailProps) {
  const [transfer, setTransfer] = useState<TransferResponse | null>(null);
  const [ledger, setLedger] = useState<LedgerEntryResponse[]>([]);
  const [category, setCategory] = useState<TransactionCategory | ''>('');
  const [savingCategory, setSavingCategory] = useState(false);
  const [categoryError, setCategoryError] = useState<string | null>(null);

  const transferId = transaction?.transferId;
  const initialCategory = transaction?.category ?? '';

  useEffect(() => {
    setCategory(initialCategory);
    setCategoryError(null);
  }, [transferId, initialCategory]);

  const changeCategory = async (next: TransactionCategory) => {
    if (!transferId) return;
    const previous = category;
    setCategory(next);
    setSavingCategory(true);
    setCategoryError(null);
    try {
      // Saved first: inside `onRecategorised?.(...)` the call would be skipped when no callback is passed.
      const updated = await transfersApi.recategorise(transferId, next);
      onRecategorised?.(updated);
    } catch (err) {
      setCategory(previous);
      setCategoryError(toFriendlyError(err).title);
    } finally {
      setSavingCategory(false);
    }
  };

  useEffect(() => {
    if (!transferId) {
      setTransfer(null);
      setLedger([]);
      return;
    }

    let cancelled = false;
    // The list row carries only what a statement line needs; the full record
    // and its postings are fetched when somebody actually opens one.
    void (async () => {
      try {
        const full = await transfersApi.get(transferId);
        if (!cancelled) setTransfer(full);
        const entries = await transfersApi.ledger(transferId);
        if (!cancelled) setLedger(entries);
      } catch {
        /* The summary below is already enough to be useful. */
      }
    })();

    return () => {
      cancelled = true;
    };
    // Keyed on the id so a recategorised row does not refetch the same transfer.
  }, [transferId]);

  if (!transaction) {
    return null;
  }

  const steps = stepsFor(transaction);
  const incoming = transaction.direction === 'CREDIT';
  const held = transaction.status === 'PENDING' || transaction.status === 'PROCESSING';

  return (
    <Sheet open onClose={onClose} title="Transaction" subtitle={transaction.reference} initialFocus="panel" side>
      <div>
        <p className={`detail-amount ${incoming && transaction.status === 'COMPLETED' ? 'detail-amount--in' : ''}`}>
          {formatSignedMoney(transaction.amount, transaction.currency, incoming ? 'in' : 'out')}
        </p>
        <p className={`detail-status ${transaction.status === 'FAILED' ? 'detail-status--failed' : ''}`}>
          {STATUS_LABEL[transaction.status] ?? 'Completed'}
        </p>
      </div>

      {held && (
        <p className="detail-note">
          This payment is being checked before it is released. No money has moved yet.
        </p>
      )}

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
            <span className="detail-row__value detail-status--failed">
              {transfer.failureReason}
            </span>
          </div>
        )}
      </div>

      <Select
        label="Category"
        value={category}
        disabled={savingCategory}
        onChange={(event) => void changeCategory(event.target.value as TransactionCategory)}
        className="detail-category"
        {...(categoryError
          ? { error: categoryError }
          : { hint: 'Both sides of the payment see the category you choose.' })}
      >
        {category === '' && (
          <option value="" disabled>
            Uncategorised
          </option>
        )}
        {TRANSACTION_CATEGORIES.map((option) => (
          <option key={option} value={option}>
            {CATEGORY_META[option].label}
          </option>
        ))}
      </Select>

      {ledger.length > 0 && (
        <>
          <hr className="divider" />
          <h3 className="detail-heading">Ledger postings</h3>
          <div className="detail-rows">
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
          <p className="field-hint">
            Every transfer posts one debit and one matching credit. They always sum to zero.
          </p>
        </>
      )}
    </Sheet>
  );
}
