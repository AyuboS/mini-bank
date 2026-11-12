import { useState } from 'react';
import { Check, HandCoins, Plus, Split, X } from 'lucide-react';
import { moneyRequestsApi } from '../api/moneyRequests';
import { accountsApi } from '../api/accounts';
import type { AccountResponse, MoneyRequestResponse } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import {
  Button,
  Input,
  Select,
  Sheet,
  Skeleton,
  Tabs,
  Surface,
  StatusBadge,
} from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { SplitBillSheet } from '../components/SplitBillSheet';
import { EmptyState, ErrorState } from '../components/States';
import { formatDay, formatMoney, maskAccountNumber, relativeTime } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

/**
 * Payment requests.
 *
 * The screen is split by direction because the two sides can do entirely
 * different things: a request you received can be paid or declined, and one you
 * sent can only be withdrawn. Showing both in one list would mean showing
 * controls that do nothing for half the rows.
 */
export function RequestsPage() {
  const [tab, setTab] = useState<'incoming' | 'outgoing'>('incoming');
  const [composing, setComposing] = useState(false);
  const [splitting, setSplitting] = useState(false);

  const incoming = useAsync(() => moneyRequestsApi.incoming(), []);
  const outgoing = useAsync(() => moneyRequestsApi.outgoing(), []);
  const accounts = useAsync(() => accountsApi.list(), []);

  const pendingIncoming = (incoming.data ?? []).filter((request) => request.status === 'PENDING');
  const active = tab === 'incoming' ? incoming : outgoing;

  return (
    <div className="stack-lg">
      <PageHeader
        title="Requests"
        subtitle="Ask to be paid. A request never moves money on its own — only the payer can settle it."
        actions={
          <>
            <Button variant="ghost" icon={<Split />} onClick={() => setSplitting(true)}>
              Split a bill
            </Button>
            <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
              Request money
            </Button>
          </>
        }
      />

      <Tabs
        label="Request direction"
        active={tab}
        onChange={(id) => setTab(id as 'incoming' | 'outgoing')}
        tabs={[
          { id: 'incoming', label: 'Asked of you', count: pendingIncoming.length },
          { id: 'outgoing', label: 'You asked' },
        ]}
      />

      {active.loading ? (
        <Skeleton height="200px" />
      ) : active.error ? (
        <ErrorState title={active.error.title} body={active.error.body} onRetry={active.reload} />
      ) : (active.data?.length ?? 0) === 0 ? (
        <Surface>
          <EmptyState
            title={tab === 'incoming' ? 'Nobody has asked you for money' : 'You have not asked anyone'}
            body={
              tab === 'incoming'
                ? 'Requests sent to your accounts will appear here.'
                : 'Split a bill, or remind somebody what they owe you.'
            }
            icon={<HandCoins />}
            action={
              tab === 'outgoing' ? (
                <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
                  Request money
                </Button>
              ) : undefined
            }
          />
        </Surface>
      ) : (
        <div className="stack">
          {active.data?.map((request) => (
            <RequestCard
              key={request.id}
              request={request}
              accounts={accounts.data ?? []}
              onChanged={() => {
                incoming.reload();
                outgoing.reload();
                accounts.reload();
              }}
            />
          ))}
        </div>
      )}

      <RequestSheet
        open={composing}
        accounts={accounts.data ?? []}
        onClose={() => setComposing(false)}
        onCreated={() => {
          setComposing(false);
          outgoing.reload();
          setTab('outgoing');
        }}
      />

      <SplitBillSheet
        open={splitting}
        accounts={accounts.data ?? []}
        onClose={() => setSplitting(false)}
        onCreated={() => {
          setSplitting(false);
          outgoing.reload();
          setTab('outgoing');
        }}
      />
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function RequestCard({
  request,
  accounts,
  onChanged,
}: {
  request: MoneyRequestResponse;
  accounts: AccountResponse[];
  onChanged: () => void;
}) {
  const toast = useToast();
  const [paying, setPaying] = useState(false);
  const [busy, setBusy] = useState(false);
  const incoming = request.direction === 'INCOMING';
  const pending = request.status === 'PENDING';

  const act = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      await action();
      toast.success(message);
      onChanged();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  /* The account the request names. The server refuses any other, so offering a
     choice the payer cannot use would only produce a rejection. */
  const payerAccount = accounts.find((account) => account.accountNumber === request.payerAccountNumber);

  return (
    <Surface className="request-card">
      <div className="row-between">
        <div className="stack gap-tight">
          <span className="request-card__who">
            {incoming ? request.requesterName : `To ${maskAccountNumber(request.payerAccountNumber)}`}
          </span>
          {request.description && <span className="dim truncate">{request.description}</span>}
        </div>
        <div className="stack gap-1 items-end">
          <span className="amount request-card__amount">{formatMoney(request.amount, request.currency)}</span>
          <StatusBadge status={request.status} />
        </div>
      </div>

      <div className="row-between request-card__foot">
        <span className="dim">
          {pending ? `Expires ${formatDay(request.expiresAt)}` : `${relativeTime(request.settledAt ?? request.createdAt)}`}
        </span>

        {pending && (
          <div className="row gap-2">
            {incoming ? (
              <>
                <Button
                  size="sm"
                  variant="ghost"
                  icon={<X />}
                  disabled={busy}
                  onClick={() => act(() => moneyRequestsApi.decline(request.id), 'Request declined')}
                >
                  Decline
                </Button>
                <Button
                  size="sm"
                  variant="primary"
                  icon={<Check />}
                  disabled={busy || !payerAccount}
                  onClick={() => setPaying(true)}
                >
                  Pay
                </Button>
              </>
            ) : (
              <Button
                size="sm"
                variant="ghost"
                icon={<X />}
                disabled={busy}
                onClick={() => act(() => moneyRequestsApi.cancel(request.id), 'Request withdrawn')}
              >
                Withdraw
              </Button>
            )}
          </div>
        )}
      </div>

      {/*
        Confirmation before money moves. Paying a request is a payment, and it
        gets the same review step a transfer does — the amount, who it goes to,
        and where it comes from, before anything happens.
      */}
      <Sheet
        open={paying}
        onClose={() => setPaying(false)}
        title="Pay this request?"
        subtitle="This creates a transfer from your account."
      >
        <div className="stack-lg">
          <dl className="review">
            <div className="review__row">
              <dt>Amount</dt>
              <dd className="amount">{formatMoney(request.amount, request.currency)}</dd>
            </div>
            <div className="review__row">
              <dt>To</dt>
              <dd>{request.requesterName}</dd>
            </div>
            <div className="review__row">
              <dt>From</dt>
              <dd>{maskAccountNumber(request.payerAccountNumber)}</dd>
            </div>
            {request.description && (
              <div className="review__row">
                <dt>For</dt>
                <dd>{request.description}</dd>
              </div>
            )}
            <div className="review__row review__row--total">
              <dt>Total</dt>
              <dd className="amount">{formatMoney(request.amount, request.currency)}</dd>
            </div>
          </dl>

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setPaying(false)}>
              Not now
            </Button>
            <Button
              variant="primary"
              loading={busy}
              onClick={() =>
                payerAccount &&
                act(() => moneyRequestsApi.pay(request.id, payerAccount.id), 'Payment sent').then(() =>
                  setPaying(false),
                )
              }
            >
              Confirm payment
            </Button>
          </div>
        </div>
      </Sheet>
    </Surface>
  );
}

/* ---------------------------------------------------------------------------- */

function RequestSheet({
  open,
  accounts,
  onClose,
  onCreated,
}: {
  open: boolean;
  accounts: AccountResponse[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const toast = useToast();
  const spending = accounts.filter((account) => account.purpose === 'SPENDING');
  const [toAccountId, setToAccountId] = useState(spending[0]?.id ?? '');
  const [fromAccountNumber, setFromAccountNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [expiresInDays, setExpiresInDays] = useState('7');
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      await moneyRequestsApi.create({
        toAccountId,
        fromAccountNumber: fromAccountNumber.trim(),
        amount,
        ...(description.trim() ? { description: description.trim() } : {}),
        expiresInDays: Number(expiresInDays),
      });
      toast.success('Request sent', 'They will see it in their MBank.');
      onCreated();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Request money"
      subtitle="They decide whether to pay. Nothing is taken from their account."
    >
      <form className="stack-lg" onSubmit={submit}>
        <Input
          label="Ask"
          value={fromAccountNumber}
          onChange={(event) => setFromAccountNumber(event.target.value)}
          placeholder="MB-482910375512"
          pattern="^MB-[0-9]{12}$"
          hint="Their MBank account number."
          required
        />

        <Input
          label="Amount"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0.01"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="25.00"
          required
        />

        <Select
          label="Into"
          value={toAccountId}
          onChange={(event) => setToAccountId(event.target.value)}
          required
        >
          {spending.map((account) => (
            <option key={account.id} value={account.id}>
              {account.accountNumber} ({account.currency})
            </option>
          ))}
        </Select>

        <Input
          label="What for"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Dinner on Friday"
          maxLength={140}
        />

        <Select
          label="Expires after"
          value={expiresInDays}
          onChange={(event) => setExpiresInDays(event.target.value)}
        >
          <option value="1">1 day</option>
          <option value="3">3 days</option>
          <option value="7">7 days</option>
          <option value="14">14 days</option>
          <option value="30">30 days</option>
        </Select>

        <div className="modal-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving}>
            Send request
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
