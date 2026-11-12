import { useId, useState } from 'react';
import { adminApi } from '../../api/admin';
import type { AccountResponse } from '../../api/types';
import { ErrorState } from '../../components/States';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Sheet, Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { toFriendlyError } from '../../utils/errors';
import { currencySymbol, formatDay, formatMoney } from '../../utils/format';
import { cleanAmountInput, formatMinorUnits, toMinorUnits } from '../../utils/money';

type Action = { kind: 'freeze' | 'unfreeze' | 'deposit'; account: AccountResponse };

const PAGE_SIZE = 20;

/** Customer accounts, with freeze, unfreeze and deposit. */
export function AdminAccounts() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [action, setAction] = useState<Action | null>(null);
  const [busy, setBusy] = useState(false);

  const { data, loading, error, reload, setData } = useAsync(() => adminApi.accounts(page, PAGE_SIZE), [page]);

  const replaceAccount = (updated: AccountResponse) =>
    setData((current) =>
      current
        ? { ...current, content: current.content.map((account) => (account.id === updated.id ? updated : account)) }
        : current,
    );

  const run = async (work: () => Promise<void>) => {
    setBusy(true);
    try {
      await work();
      setAction(null);
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const setFrozen = (account: AccountResponse, frozen: boolean) =>
    run(async () => {
      const updated = frozen ? await adminApi.freeze(account.id) : await adminApi.unfreeze(account.id);
      replaceAccount(updated);
      toast.success(frozen ? 'Account frozen' : 'Account unfrozen', updated.accountNumber);
    });

  const deposit = (account: AccountResponse, amount: string, description: string) =>
    run(async () => {
      const transfer = await adminApi.deposit(account.id, amount, description || undefined);
      toast.success('Deposit made', `${formatMoney(transfer.amount, transfer.currency)} · ${transfer.reference}`);
      await reload();
    });

  if (loading && !data) return <Skeleton height={320} />;
  if (error || !data) return <ErrorState body={error?.body ?? 'We could not load the accounts.'} onRetry={reload} />;

  return (
    <>
      <table className="admin-table">
        <thead>
          <tr>
            <th>Account</th>
            <th>Status</th>
            <th>Opened</th>
            <th className="admin-table__num">Balance</th>
            <th>
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {data.content.map((account) => (
            <tr key={account.id}>
              <td>
                <span className="admin-table__mono">{account.accountNumber}</span>
                <span className="admin-table__sub">
                  {account.purpose === 'SAVINGS' ? 'Savings' : 'Spending'} · {account.currency}
                </span>
              </td>
              <td className={account.status === 'FROZEN' ? 'admin-table__flagged' : undefined}>
                {account.status === 'ACTIVE' ? 'Active' : account.status === 'FROZEN' ? 'Frozen' : 'Closed'}
              </td>
              <td data-wide-only className="admin-table__mono dim">
                {formatDay(account.createdAt)}
              </td>
              <td className="admin-table__num">{formatMoney(account.balance, account.currency)}</td>
              <td>
                <div className="admin-table__actions">
                  {account.status === 'ACTIVE' && (
                    <>
                      <Button size="sm" variant="secondary" onClick={() => setAction({ kind: 'deposit', account })}
                        aria-label={`Deposit ${account.accountNumber}`}
                      >
                        Deposit
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setAction({ kind: 'freeze', account })}
                        aria-label={`Freeze ${account.accountNumber}`}
                      >
                        Freeze
                      </Button>
                    </>
                  )}
                  {account.status === 'FROZEN' && (
                    <Button size="sm" variant="ghost" onClick={() => setAction({ kind: 'unfreeze', account })}
                        aria-label={`Unfreeze ${account.accountNumber}`}
                      >
                      Unfreeze
                    </Button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="admin-pager">
        <span>
          Page {data.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} accounts
        </span>
        <Button size="sm" variant="ghost" disabled={data.first} onClick={() => setPage((p) => Math.max(0, p - 1))}>
          Previous
        </Button>
        <Button size="sm" variant="ghost" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
          Next
        </Button>
      </div>

      <Sheet
        open={action?.kind === 'freeze' || action?.kind === 'unfreeze'}
        onClose={() => setAction(null)}
        title={action?.kind === 'freeze' ? 'Freeze this account?' : 'Unfreeze this account?'}
        subtitle={action?.account.accountNumber}
      >
        <p className="admin-note">
          {action?.kind === 'freeze'
            ? 'A frozen account can neither send nor receive money. The balance stays as it is. The change is recorded in the audit trail.'
            : 'The account can send and receive money again. The change is recorded in the audit trail.'}
        </p>
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setAction(null)} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={busy}
            onClick={() => action && setFrozen(action.account, action.kind === 'freeze')}
          >
            {action?.kind === 'freeze' ? 'Freeze' : 'Unfreeze'}
          </Button>
        </div>
      </Sheet>

      <Sheet
        open={action?.kind === 'deposit'}
        onClose={() => setAction(null)}
        title="Deposit"
        subtitle={action?.account.accountNumber}
      >
        {action?.kind === 'deposit' && (
          <DepositForm
            account={action.account}
            busy={busy}
            onCancel={() => setAction(null)}
            onSubmit={(amount, description) => deposit(action.account, amount, description)}
          />
        )}
      </Sheet>
    </>
  );
}

interface DepositFormProps {
  account: AccountResponse;
  busy: boolean;
  onCancel: () => void;
  onSubmit: (amount: string, description: string) => void;
}

function DepositForm({ account, busy, onCancel, onSubmit }: DepositFormProps) {
  const amountId = useId();
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const amountMinor = toMinorUnits(amount);

  return (
    <form
      className="admin-form"
      onSubmit={(event) => {
        event.preventDefault();
        if (amountMinor > 0 && !busy) onSubmit(amount, description.trim());
      }}
    >
      <p className="admin-note">
        Funded from the bank&apos;s {account.currency} system account, so the ledger stays balanced. Customer limits do
        not apply. Recorded in the audit trail.
      </p>
      <div className="field">
        <label className="field__label" htmlFor={amountId}>
          Amount
        </label>
        <div className="field__well well send-amount">
          <span className="send-amount__symbol" aria-hidden="true">
            {currencySymbol(account.currency)}
          </span>
          <input
            id={amountId}
            className="field__input"
            type="text"
            inputMode="decimal"
            placeholder="0.00"
            autoComplete="off"
            value={amount}
            onChange={(event) => setAmount(cleanAmountInput(event.target.value))}
          />
        </div>
        <p className="field__hint">
          Balance after: {formatMinorUnits(toMinorUnits(account.balance) + amountMinor, account.currency)}
        </p>
      </div>
      <Input
        label="Description (optional)"
        placeholder="Deposit"
        maxLength={140}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
      />
      <div className="modal-actions">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" loading={busy} disabled={amountMinor <= 0}>
          Deposit {amountMinor > 0 ? formatMinorUnits(amountMinor, account.currency) : ''}
        </Button>
      </div>
    </form>
  );
}
