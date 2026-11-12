import { ArrowDownLeft, ArrowUpRight, Landmark, RotateCcw } from 'lucide-react';
import type { TransactionResponse } from '../api/types';
import { formatDate, maskAccountNumber } from '../utils/format';
import { categoryMeta } from '../utils/categories';
import { StatusBadge } from './ui';
import { CategoryIcon } from './CategoryIcon';
import { Money } from './Money';

interface TransactionRowProps {
  transaction: TransactionResponse;
  /** Omitted where the row is a read-only summary, as on the card page. */
  onSelect?: (transaction: TransactionResponse) => void;
  /** Hides the date, for a list already grouped by day. */
  hideDate?: boolean;
}

/** Falls back to something meaningful when there is no description to show. */
export function titleFor(transaction: TransactionResponse): string {
  if (transaction.description) return transaction.description;
  if (transaction.type === 'DEPOSIT') return 'Deposit';
  if (transaction.type === 'REVERSAL') return 'Reversal';
  if (transaction.category) return categoryMeta(transaction.category).label;
  return transaction.direction === 'CREDIT' ? 'Money in' : 'Money out';
}

/**
 * One line of a statement.
 *
 * The glyph is coloured by category rather than by counterparty. That was a
 * deliberate change: a colour per payee makes a list pretty, a colour per
 * category makes it answerable — "where did the money go this week" is legible
 * from the colours alone, before a single row is read.
 *
 * Deposits and reversals keep a directional icon: they are events rather than
 * spending, and giving them a category colour would put them in the chart's
 * visual language without being in the chart.
 */
export function TransactionRow({ transaction, onSelect, hideDate = false }: TransactionRowProps) {
  const incoming = transaction.direction === 'CREDIT';
  const settled = transaction.status === 'COMPLETED';
  const title = titleFor(transaction);

  /*
    The server's word for an ordinary transfer is INTERNAL, so this asks which
    types are *not* ordinary rather than naming the one that is — a new transfer
    type should default to being a payment, not to being a system event.
  */
  const isEvent = transaction.type === 'DEPOSIT' || transaction.type === 'REVERSAL';

  const content = (
    <>
      {isEvent || !transaction.category ? (
        <span className={`txn__glyph ${incoming && settled ? 'txn__glyph--in' : ''}`} aria-hidden="true">
          {transaction.type === 'REVERSAL' ? (
            <RotateCcw />
          ) : transaction.type === 'DEPOSIT' ? (
            <Landmark />
          ) : incoming ? (
            <ArrowDownLeft />
          ) : (
            <ArrowUpRight />
          )}
        </span>
      ) : (
        <CategoryIcon category={transaction.category} />
      )}

      <span className="txn__body">
        <span className="txn__title truncate">{title}</span>
        <span className="txn__meta truncate">
          {!hideDate && `${formatDate(transaction.createdAt)} · `}
          {maskAccountNumber(transaction.counterpartyAccountNumber)}
        </span>
      </span>

      <span className="txn__right">
        {/* A movement that has not settled is dimmed, so it cannot be mistaken
            for money that has actually arrived. */}
        <Money
          amount={transaction.amount}
          currency={transaction.currency}
          direction={transaction.direction}
          muted={!settled}
        />
        {!settled && <StatusBadge status={transaction.status} />}
      </span>
    </>
  );

  // Without onSelect there is nothing to open, so the row must not be a button.
  if (!onSelect) {
    return <div className="txn txn--static">{content}</div>;
  }

  return (
    <button type="button" className="txn" onClick={() => onSelect(transaction)}>
      {content}
    </button>
  );
}
