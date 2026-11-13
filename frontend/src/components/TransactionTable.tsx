import type { TransactionResponse } from '../api/types';
import { categoryMeta } from '../utils/categories';
import { formatDay, formatSignedMoney, maskAccountNumber } from '../utils/format';
import { STATUS_LABEL } from './TransactionList';
import { titleFor } from './TransactionRow';

interface TransactionTableProps {
  transactions: TransactionResponse[];
  onSelect: (transaction: TransactionResponse) => void;
}

/** The desktop transaction list: one row per payment, amounts right-aligned. */
export function TransactionTable({ transactions, onSelect }: TransactionTableProps) {
  return (
    <table className="txn-table">
      <thead>
        <tr>
          <th scope="col">Date</th>
          <th scope="col">Description</th>
          <th scope="col">Category</th>
          {/* The other party's account: the API does not say which of your own accounts was used. */}
          <th scope="col">Account</th>
          <th scope="col" className="txn-table__num">
            Amount
          </th>
        </tr>
      </thead>
      <tbody>
        {transactions.map((transaction) => {
          const incoming = transaction.direction === 'CREDIT';
          const settled = transaction.status === 'COMPLETED';
          const status = STATUS_LABEL[transaction.status];

          return (
            // The click bubbles up from the button, so keyboard and mouse share one handler.
            <tr key={transaction.transferId} onClick={() => onSelect(transaction)}>
              <td className="txn-table__date">{formatDay(transaction.createdAt)}</td>
              <td className="txn-table__title">
                <button type="button" className="txn-table__open truncate">
                  {titleFor(transaction)}
                </button>
              </td>
              <td>{transaction.category ? categoryMeta(transaction.category).label : '—'}</td>
              <td className="txn-table__account">
                {incoming ? 'From ' : 'To '}
                {maskAccountNumber(transaction.counterpartyAccountNumber)}
              </td>
              <td className="txn-table__num">
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
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
