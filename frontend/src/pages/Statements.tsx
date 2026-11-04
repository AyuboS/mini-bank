import { useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, Download, FileText } from 'lucide-react';
import { accountsApi } from '../api/accounts';
import { statementsApi } from '../api/statements';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import { Button, Select, Skeleton, Surface } from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { TransactionRow } from '../components/TransactionRow';
import { EmptyState, ErrorState } from '../components/States';
import { formatMoney, formatMonth, toMonthKey } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

/**
 * Monthly statements.
 *
 * The opening balance is derived from the ledger rather than stored, which is
 * why the four figures at the top always reconcile: opening plus money in minus
 * money out equals closing, by construction rather than by arithmetic done
 * twice.
 */
export function StatementsPage() {
  const toast = useToast();
  const accounts = useAsync(() => accountsApi.list(), []);
  const [accountId, setAccountId] = useState('');
  const [month, setMonth] = useState(toMonthKey());
  const [downloading, setDownloading] = useState(false);

  const selected = useMemo(() => {
    const list = accounts.data ?? [];
    return list.find((account) => account.id === accountId) ?? list[0];
  }, [accounts.data, accountId]);

  const statement = useAsync(
    () => (selected ? statementsApi.get(selected.id, month) : Promise.resolve(null)),
    [selected?.id, month],
  );

  const isCurrentMonth = month === toMonthKey();

  const step = (delta: number) => {
    const [year, monthNumber] = month.split('-').map(Number);
    if (!year || !monthNumber) return;
    setMonth(toMonthKey(new Date(year, monthNumber - 1 + delta, 1)));
  };

  const download = async () => {
    if (!selected) return;
    setDownloading(true);
    try {
      await statementsApi.download(selected.id, selected.accountNumber, month);
      toast.success('Statement downloaded');
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="stack-lg">
      <PageHeader
        title="Statements"
        subtitle="A month at a time, reconciled against the ledger."
        actions={
          <Button
            variant="primary"
            icon={<Download />}
            onClick={download}
            loading={downloading}
            disabled={!selected}
          >
            Download CSV
          </Button>
        }
      />

      <div className="statement-controls">
        <Select
          label="Account"
          value={selected?.id ?? ''}
          onChange={(event) => setAccountId(event.target.value)}
        >
          {(accounts.data ?? []).map((account) => (
            <option key={account.id} value={account.id}>
              {account.accountNumber} ({account.currency})
              {account.purpose === 'SAVINGS' ? ' · Savings' : ''}
            </option>
          ))}
        </Select>

        <Surface tone="chrome" className="month-switch">
          <button type="button" className="icon-btn" onClick={() => step(-1)} aria-label="Previous month">
            <ChevronLeft aria-hidden="true" />
          </button>
          <span className="month-switch__label">{formatMonth(month)}</span>
          <button
            type="button"
            className="icon-btn"
            onClick={() => step(1)}
            disabled={isCurrentMonth}
            aria-label="Next month"
          >
            <ChevronRight aria-hidden="true" />
          </button>
        </Surface>
      </div>

      {statement.loading ? (
        <Skeleton height="160px" />
      ) : statement.error ? (
        <ErrorState title={statement.error.title} body={statement.error.body} onRetry={statement.reload} />
      ) : !statement.data ? (
        <Surface>
          <EmptyState title="No account selected" icon={<FileText />} />
        </Surface>
      ) : (
        <>
          <Surface className="statement-summary">
            <div className="statement-summary__row">
              <span className="label">Opening balance</span>
              <span className="amount">
                {formatMoney(statement.data.openingBalance, statement.data.currency)}
              </span>
            </div>
            <div className="statement-summary__row">
              <span className="label">Money in</span>
              <span className="amount amount--in">
                +{formatMoney(statement.data.totalIn, statement.data.currency)}
              </span>
            </div>
            <div className="statement-summary__row">
              <span className="label">Money out</span>
              <span className="amount">
                −{formatMoney(statement.data.totalOut, statement.data.currency)}
              </span>
            </div>
            <div className="statement-summary__row statement-summary__row--total">
              <span className="label">Closing balance</span>
              <span className="amount">
                {formatMoney(statement.data.closingBalance, statement.data.currency)}
              </span>
            </div>
          </Surface>

          <Surface className="list-panel">
            {statement.data.transactions.length === 0 ? (
              <EmptyState
                title="No movement in this month"
                body="The opening and closing balances are the same."
              />
            ) : (
              statement.data.transactions.map((transaction) => (
                <TransactionRow key={transaction.transferId} transaction={transaction} />
              ))
            )}
          </Surface>
        </>
      )}
    </div>
  );
}
