import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeftRight, FileText, PiggyBank, Plus, Wallet } from 'lucide-react';
import { accountsApi } from '../api/accounts';
import { savingsApi } from '../api/savings';
import type { AccountResponse, CurrencyCode } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import {
  Button,
  Select,
  Sheet,
  Skeleton,
  Surface,
  StatusBadge,
} from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState } from '../components/States';
import { formatDay, splitMoney, toNumber } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

const CURRENCIES: CurrencyCode[] = ['EUR', 'USD', 'GBP'];

/**
 * The customer's accounts.
 *
 * Spending and savings are shown apart, and the totals are per currency rather
 * than summed. There is no FX in this product, so adding a USD balance to a EUR
 * one would require a rate that does not exist — and a single "total" made from
 * two currencies is a number that means nothing.
 */
export function AccountsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const accounts = useAsync(() => accountsApi.list(), []);
  const goals = useAsync(() => savingsApi.list(), []);
  const [opening, setOpening] = useState(false);

  const spending = (accounts.data ?? []).filter((account) => account.purpose === 'SPENDING');
  const savings = (accounts.data ?? []).filter((account) => account.purpose === 'SAVINGS');

  /** Which goal each savings account backs, so the row can be named. */
  const goalByAccount = useMemo(() => {
    const map = new Map<string, string>();
    for (const goal of goals.data ?? []) map.set(goal.accountId, goal.name);
    return map;
  }, [goals.data]);

  /** One total per currency. Never one total across them. */
  const totals = useMemo(() => {
    const byCurrency = new Map<CurrencyCode, number>();
    for (const account of spending) {
      byCurrency.set(account.currency, (byCurrency.get(account.currency) ?? 0) + toNumber(account.balance));
    }
    return [...byCurrency.entries()];
  }, [spending]);

  const openAccount = async (currency: CurrencyCode) => {
    try {
      await accountsApi.open(currency);
      toast.success(`${currency} account opened`);
      setOpening(false);
      accounts.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <div className="stack-lg">
      <PageHeader
        title="Accounts"
        subtitle="Each currency is held separately. MBank does not convert between them."
        actions={
          <Button variant="primary" icon={<Plus />} onClick={() => setOpening(true)}>
            Open account
          </Button>
        }
      />

      {totals.length > 0 && (
        <div className="stat-grid">
          {totals.map(([currency, total]) => {
            const [major, minor] = splitMoney(total, currency);
            return (
              <Surface tone="hero" className="hero hero--compact" key={currency}>
                <span className="label">{currency} available</span>
                <p className="balance balance--sm">
                  {major}
                  <span className="balance__minor">{minor}</span>
                </p>
              </Surface>
            );
          })}
        </div>
      )}

      <section className="stack">
        <h2>Spending</h2>
        {accounts.loading ? (
          <div className="account-grid">
            <Skeleton height="150px" />
            <Skeleton height="150px" />
          </div>
        ) : accounts.error ? (
          <ErrorState title={accounts.error.title} body={accounts.error.body} onRetry={accounts.reload} />
        ) : spending.length === 0 ? (
          <Surface>
            <EmptyState
              title="No accounts yet"
              body="Open one to start receiving and sending money."
              icon={<Wallet />}
              action={
                <Button variant="primary" icon={<Plus />} onClick={() => setOpening(true)}>
                  Open an account
                </Button>
              }
            />
          </Surface>
        ) : (
          <div className="account-grid">
            {spending.map((account) => (
              <AccountCard key={account.id} account={account} onSend={() => navigate('/payments/send')} onStatement={() => navigate('/statements')} />
            ))}
          </div>
        )}
      </section>

      {savings.length > 0 && (
        <section className="stack">
          <h2>Savings</h2>
          <div className="account-grid">
            {savings.map((account) => (
              <AccountCard
                key={account.id}
                account={account}
                goalName={goalByAccount.get(account.id)}
                onSend={() => navigate('/savings')}
                onStatement={() => navigate('/statements')}
              />
            ))}
          </div>
        </section>
      )}

      <Sheet
        open={opening}
        onClose={() => setOpening(false)}
        title="Open an account"
        subtitle="A new account number in the currency you choose."
      >
        <OpenAccountForm existing={accounts.data ?? []} onOpen={openAccount} onCancel={() => setOpening(false)} />
      </Sheet>
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function AccountCard({
  account,
  goalName,
  onSend,
  onStatement,
}: {
  account: AccountResponse;
  goalName?: string;
  onSend: () => void;
  onStatement: () => void;
}) {
  const savings = account.purpose === 'SAVINGS';
  const [major, minor] = splitMoney(account.balance, account.currency);

  return (
    <Surface className="account-card">
      <div className="row-between">
        <span className="row" style={{ gap: 'var(--space-2)' }}>
          <span className="account-card__icon" aria-hidden="true">
            {savings ? <PiggyBank /> : <Wallet />}
          </span>
          <span className="stack" style={{ gap: 0 }}>
            <span className="account-card__name">{goalName ?? `${account.currency} account`}</span>
            <span className="dim account-card__number mono">{account.accountNumber}</span>
          </span>
        </span>
        {account.status !== 'ACTIVE' && <StatusBadge status={account.status} />}
      </div>

      <p className="account-card__balance">
        {major}
        <span className="balance__minor">{minor}</span>
      </p>

      <div className="row-between account-card__foot">
        <span className="dim">Opened {formatDay(account.createdAt)}</span>
        <span className="row" style={{ gap: 'var(--space-1)' }}>
          <Button
            size="sm"
            variant="ghost"
            icon={savings ? <PiggyBank /> : <ArrowLeftRight />}
            onClick={onSend}
          >
            {savings ? 'Goal' : 'Send'}
          </Button>
          <Button size="sm" variant="ghost" icon={<FileText />} onClick={onStatement}>
            Statement
          </Button>
        </span>
      </div>
    </Surface>
  );
}

/* ---------------------------------------------------------------------------- */

function OpenAccountForm({
  existing,
  onOpen,
  onCancel,
}: {
  existing: AccountResponse[];
  onOpen: (currency: CurrencyCode) => void;
  onCancel: () => void;
}) {
  const [currency, setCurrency] = useState<CurrencyCode>('EUR');
  const already = existing.filter(
    (account) => account.currency === currency && account.purpose === 'SPENDING',
  ).length;

  return (
    <form
      className="stack-lg"
      onSubmit={(event) => {
        event.preventDefault();
        onOpen(currency);
      }}
    >
      <Select
        label="Currency"
        value={currency}
        onChange={(event) => setCurrency(event.target.value as CurrencyCode)}
        hint={
          already > 0
            ? `You already have ${already} ${currency} account${already === 1 ? '' : 's'}. This opens another.`
            : undefined
        }
      >
        {CURRENCIES.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </Select>

      <p className="dim" style={{ fontSize: 'var(--text-small)' }}>
        Accounts are held separately per currency. Money cannot be moved between two different
        currencies inside MBank.
      </p>

      <div className="modal-actions">
        <Button variant="secondary" type="button" onClick={onCancel}>
          Cancel
        </Button>
        <Button variant="primary" type="submit">
          Open {currency} account
        </Button>
      </div>
    </form>
  );
}
