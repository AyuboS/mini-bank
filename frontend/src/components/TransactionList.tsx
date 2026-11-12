import type { TransactionResponse, TransferStatus } from '../api/types';
import { categoryMeta } from '../utils/categories';
import { LOCALE, formatSignedMoney } from '../utils/format';
import { titleFor } from './TransactionRow';

interface TransactionListProps {
  transactions: TransactionResponse[];
  onSelect: (transaction: TransactionResponse) => void;
  /** Shows the time instead of the date, for a list already grouped by day. */
  groupedByDay?: boolean;
}

// Completed is the normal case and gets no label.
export const STATUS_LABEL: Partial<Record<TransferStatus, string>> = {
  PENDING: 'In review',
  PROCESSING: 'Processing',
  FAILED: 'Failed',
  REVERSED: 'Reversed',
};

function shortDate(iso: string): string {
  return new Date(iso).toLocaleDateString(LOCALE, { day: 'numeric', month: 'short' });
}

function time(iso: string): string {
  return new Date(iso).toLocaleTimeString(LOCALE, { hour: '2-digit', minute: '2-digit' });
}

/** Transactions as ledger lines: date, description, category, amount. */
export function TransactionList({ transactions, onSelect, groupedByDay = false }: TransactionListProps) {
  return (
    <ul className="ledger">
      {transactions.map((transaction) => {
        const incoming = transaction.direction === 'CREDIT';
        const settled = transaction.status === 'COMPLETED';
        const status = STATUS_LABEL[transaction.status];
        const category = transaction.category ? categoryMeta(transaction.category).label : null;
        const date = groupedByDay ? time(transaction.createdAt) : shortDate(transaction.createdAt);

        return (
          <li key={transaction.transferId}>
            <button type="button" className="ledger__row" onClick={() => onSelect(transaction)}>
              <span className="ledger__date">{date}</span>
              <span className="ledger__main">
                <span className="ledger__title truncate">{titleFor(transaction)}</span>
                <span className="ledger__meta">
                  {date}
                  {category ? ` · ${category}` : ''}
                </span>
              </span>
              <span className="ledger__category">{category ?? '—'}</span>
              <span className="ledger__figure">
                <span
                  className={`ledger__amount ${incoming && settled ? 'ledger__amount--in' : ''} ${
                    settled ? '' : 'ledger__amount--unsettled'
                  }`}
                >
                  {formatSignedMoney(transaction.amount, transaction.currency, incoming ? 'in' : 'out')}
                </span>
                {status && (
                  <span className={`ledger__status ${transaction.status === 'FAILED' ? 'ledger__status--failed' : ''}`}>
                    {status}
                  </span>
                )}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
