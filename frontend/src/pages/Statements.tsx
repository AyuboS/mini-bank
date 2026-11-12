import { useMemo, useState } from 'react';
import { accountsApi } from '../api/accounts';
import { statementsApi } from '../api/statements';
import type { TransactionResponse } from '../api/types';
import { MonthSwitch } from '../components/MonthSwitch';
import { PageHeader } from '../components/PageHeader';
import { ErrorState } from '../components/States';
import { useToast } from '../components/ToastProvider';
import { TransactionDetail } from '../components/TransactionDetail';
import { TransactionList } from '../components/TransactionList';
import { Button, Select, Skeleton } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { toFriendlyError } from '../utils/errors';
import { formatMoney, maskAccountNumber, toMonthKey } from '../utils/format';

/**
 * Monthly statements. The server derives the opening balance from the ledger,
 * so opening + in − out always equals closing.
 */
export function StatementsPage() {
  const toast = useToast();
  const accounts = useAsync(() => accountsApi.list(), []);
  const [accountId, setAccountId] = useState('');
  const [month, setMonth] = useState(toMonthKey());
  const [downloading, setDownloading] = useState(false);
  const [selected, setSelected] = useState<TransactionResponse | null>(null);

  const account = useMemo(() => {
    const list = accounts.data ?? [];
    return list.find((candidate) => candidate.id === accountId) ?? list[0];
  }, [accounts.data, accountId]);

  const statement = useAsync(
    () => (account ? statementsApi.get(account.id, month) : Promise.resolve(null)),
    [account?.id, month],
  );

  const download = async () => {
    if (!account) return;
    setDownloading(true);
    try {
      await statementsApi.download(account.id, account.accountNumber, month);
      toast.success('Statement downloaded');
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setDownloading(false);
    }
  };

  if (accounts.error) {
    return <ErrorState title={accounts.error.title} body={accounts.error.body} onRetry={accounts.reload} />;
  }

  const data = statement.data;

  return (
    <>
      <PageHeader
        title="Statements"
        subtitle="One month of one account, reconciled against the ledger."
        actions={
          <Button variant="secondary" onClick={download} loading={downloading} disabled={!account}>
            Download CSV
          </Button>
        }
      />

      <div className="statement-controls">
        <Select label="Account" value={account?.id ?? ''} onChange={(event) => setAccountId(event.target.value)}>
          {(accounts.data ?? []).map((option) => (
            <option key={option.id} value={option.id}>
              {option.purpose === 'SAVINGS' ? 'Savings' : 'Spending'} {maskAccountNumber(option.accountNumber)} ·{' '}
              {option.currency}
            </option>
          ))}
        </Select>
        <MonthSwitch month={month} onChange={setMonth} />
      </div>

      {statement.loading && !data ? (
        <Skeleton height="160px" />
      ) : statement.error ? (
        <ErrorState title={statement.error.title} body={statement.error.body} onRetry={statement.reload} />
      ) : !data ? null : (
        <>
          <dl className="figures">
            <div>
              <dt>Opening balance</dt>
              <dd>{formatMoney(data.openingBalance, data.currency)}</dd>
            </div>
            <div>
              <dt>Money in</dt>
              <dd className="figures__in">+{formatMoney(data.totalIn, data.currency)}</dd>
            </div>
            <div>
              <dt>Money out</dt>
              <dd>−{formatMoney(data.totalOut, data.currency)}</dd>
            </div>
            <div>
              <dt>Closing balance</dt>
              <dd className="figures__total">{formatMoney(data.closingBalance, data.currency)}</dd>
            </div>
          </dl>

          <section className="statement-lines" aria-label="Transactions in this month">
            {data.transactions.length === 0 ? (
              <p className="quiet-empty">No money moved in this month.</p>
            ) : (
              <TransactionList transactions={data.transactions} onSelect={setSelected} />
            )}
          </section>
        </>
      )}

      <TransactionDetail
        transaction={selected}
        onClose={() => setSelected(null)}
        onRecategorised={(updated) => {
          setSelected(updated);
          statement.setData(
            (current) =>
              current && {
                ...current,
                transactions: current.transactions.map((row) => (row.transferId === updated.transferId ? updated : row)),
              },
          );
        }}
      />
    </>
  );
}
