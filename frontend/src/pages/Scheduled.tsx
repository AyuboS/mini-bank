import { useState } from 'react';
import { CalendarClock, Pause, Play, Plus, Repeat, X } from 'lucide-react';
import { scheduledApi } from '../api/scheduled';
import { accountsApi } from '../api/accounts';
import { beneficiariesApi } from '../api/beneficiaries';
import type { ScheduleFrequency, TransactionCategory } from '../api/types';
import { TRANSACTION_CATEGORIES } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import {
  Button,
  Input,
  Select,
  Sheet,
  Skeleton,
  Surface,
  StatusBadge,
} from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { CategoryIcon } from '../components/CategoryIcon';
import { EmptyState, ErrorState } from '../components/States';
import { CATEGORY_META } from '../utils/categories';
import { formatDay, formatMoney, maskAccountNumber, toDateKey } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

const FREQUENCY_LABEL: Record<ScheduleFrequency, string> = {
  ONCE: 'Once',
  WEEKLY: 'Every week',
  MONTHLY: 'Every month',
};

/**
 * Standing orders: scheduled and recurring payments.
 *
 * These execute for real. A background job puts each due order through the same
 * transfer path a manual payment uses, so limits, risk checks and the ledger all
 * apply — and a payment that fails is recorded and shown here rather than
 * retried silently.
 */
export function ScheduledPage() {
  const toast = useToast();
  const orders = useAsync(() => scheduledApi.list(), []);
  const accounts = useAsync(() => accountsApi.list(), []);
  const payees = useAsync(() => beneficiariesApi.list(), []);
  const [composing, setComposing] = useState(false);

  const live = (orders.data ?? []).filter((order) => order.status === 'SCHEDULED' || order.status === 'PAUSED');
  const finished = (orders.data ?? []).filter((order) => order.status === 'COMPLETED' || order.status === 'CANCELLED');

  const act = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action();
      toast.success(message);
      orders.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <div className="stack-lg">
      <PageHeader
        title="Scheduled payments"
        subtitle="Rent, subscriptions, anything that repeats. These run automatically on their due date."
        actions={
          <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
            New payment
          </Button>
        }
      />

      {orders.loading ? (
        <Skeleton height="200px" />
      ) : orders.error ? (
        <ErrorState title={orders.error.title} body={orders.error.body} onRetry={orders.reload} />
      ) : live.length === 0 ? (
        <Surface>
          <EmptyState
            title="Nothing scheduled"
            body="Set up a payment that runs on a date you choose, once or every month."
            icon={<CalendarClock />}
            action={
              <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
                Schedule a payment
              </Button>
            }
          />
        </Surface>
      ) : (
        <div className="stack">
          {live.map((order) => (
            <Surface className="order-card" key={order.id}>
              <div className="order-card__main">
                <CategoryIcon category={order.category} />

                <div className="grow stack" style={{ gap: 2 }}>
                  <span className="order-card__name truncate">
                    {order.description || order.payeeName || 'Scheduled payment'}
                  </span>
                  <span className="dim order-card__meta truncate">
                    {order.payeeName ?? maskAccountNumber(order.toAccountNumber)} ·{' '}
                    <Repeat size={12} style={{ display: 'inline', verticalAlign: '-2px' }} aria-hidden="true" />{' '}
                    {FREQUENCY_LABEL[order.frequency]}
                  </span>
                </div>

                <div className="stack order-card__right" style={{ gap: 2 }}>
                  <span className="amount">{formatMoney(order.amount, order.currency)}</span>
                  <StatusBadge status={order.status} />
                </div>
              </div>

              <div className="order-card__foot">
                <span className="dim">
                  {order.status === 'PAUSED' ? 'Paused' : `Next on ${formatDay(order.nextRunOn)}`}
                  {order.runsCompleted > 0 && ` · ${order.runsCompleted} sent so far`}
                </span>

                <div className="row" style={{ gap: 'var(--space-1)' }}>
                  {order.status === 'SCHEDULED' ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={<Pause />}
                      onClick={() => act(() => scheduledApi.pause(order.id), 'Payment paused')}
                    >
                      Pause
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={<Play />}
                      onClick={() => act(() => scheduledApi.resume(order.id), 'Payment resumed')}
                    >
                      Resume
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="ghost"
                    icon={<X />}
                    onClick={() => act(() => scheduledApi.cancel(order.id), 'Payment cancelled')}
                  >
                    Cancel
                  </Button>
                </div>
              </div>

              {/*
                A failed run is surfaced, not swallowed. The customer needs to
                know their rent did not go out, and the schedule has already
                moved on to its next date rather than retrying all day.
              */}
              {order.lastFailureReason && (
                <p className="order-card__failure" role="status">
                  Last attempt did not go through: {order.lastFailureReason}
                </p>
              )}
            </Surface>
          ))}
        </div>
      )}

      {finished.length > 0 && (
        <section className="stack">
          <h2>Finished</h2>
          <Surface className="list-panel">
            {finished.map((order) => (
              <div className="row-between order-card__done" key={order.id}>
                <span className="dim truncate">{order.description || order.payeeName || 'Payment'}</span>
                <span className="row" style={{ gap: 'var(--space-3)' }}>
                  <span className="amount dim">{formatMoney(order.amount, order.currency)}</span>
                  <StatusBadge status={order.status} />
                </span>
              </div>
            ))}
          </Surface>
        </section>
      )}

      <ScheduleSheet
        open={composing}
        accounts={accounts.data ?? []}
        payees={payees.data ?? []}
        onClose={() => setComposing(false)}
        onCreated={() => {
          setComposing(false);
          orders.reload();
        }}
      />
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function ScheduleSheet({
  open,
  accounts,
  payees,
  onClose,
  onCreated,
}: {
  open: boolean;
  accounts: { id: string; accountNumber: string; currency: string; balance: string; purpose: string }[];
  payees: { accountNumber: string; nickname: string }[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const toast = useToast();
  const spending = accounts.filter((account) => account.purpose === 'SPENDING');

  const [fromAccountId, setFromAccountId] = useState(spending[0]?.id ?? '');
  const [toAccountNumber, setToAccountNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<TransactionCategory | ''>('');
  const [frequency, setFrequency] = useState<ScheduleFrequency>('MONTHLY');
  const [startOn, setStartOn] = useState(toDateKey());
  const [endsOn, setEndsOn] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      await scheduledApi.create({
        fromAccountId,
        toAccountNumber: toAccountNumber.trim(),
        amount,
        ...(description.trim() ? { description: description.trim() } : {}),
        ...(category ? { category } : {}),
        frequency,
        startOn,
        ...(endsOn ? { endsOn } : {}),
      });
      toast.success('Payment scheduled', `First run on ${formatDay(startOn)}.`);
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
      title="Schedule a payment"
      subtitle="Runs automatically on its due date, subject to your usual limits."
    >
      <form className="stack-lg" onSubmit={submit}>
        <Select
          label="From"
          value={fromAccountId}
          onChange={(event) => setFromAccountId(event.target.value)}
          required
        >
          {spending.map((account) => (
            <option key={account.id} value={account.id}>
              {account.accountNumber} — {formatMoney(account.balance, account.currency as 'EUR')}
            </option>
          ))}
        </Select>

        <Input
          label="To"
          value={toAccountNumber}
          onChange={(event) => setToAccountNumber(event.target.value)}
          placeholder="MB-482910375512"
          pattern="^MB-[0-9]{12}$"
          list="scheduled-payees"
          required
        />
        {/*
          A datalist rather than a custom dropdown: the browser's own
          autocomplete is keyboard accessible, works on a phone, and still lets
          somebody type an account number that is not saved.
        */}
        <datalist id="scheduled-payees">
          {payees.map((payee) => (
            <option key={payee.accountNumber} value={payee.accountNumber}>
              {payee.nickname}
            </option>
          ))}
        </datalist>

        <Input
          label="Amount"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0.01"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="500.00"
          required
        />

        <Input
          label="Description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          placeholder="Rent"
          maxLength={140}
        />

        <Select
          label="Category"
          value={category}
          onChange={(event) => setCategory(event.target.value as TransactionCategory | '')}
          hint="Leave this and MBank will work it out from the description."
        >
          <option value="">Work it out for me</option>
          {TRANSACTION_CATEGORIES.map((option) => (
            <option key={option} value={option}>
              {CATEGORY_META[option].label}
            </option>
          ))}
        </Select>

        <Select
          label="How often"
          value={frequency}
          onChange={(event) => setFrequency(event.target.value as ScheduleFrequency)}
        >
          <option value="ONCE">Once</option>
          <option value="WEEKLY">Every week</option>
          <option value="MONTHLY">Every month</option>
        </Select>

        <Input
          label="First payment"
          type="date"
          value={startOn}
          min={toDateKey()}
          onChange={(event) => setStartOn(event.target.value)}
          required
        />

        {frequency !== 'ONCE' && (
          <Input
            label="Stop after"
            type="date"
            value={endsOn}
            min={startOn}
            onChange={(event) => setEndsOn(event.target.value)}
            hint="Optional. Leave it blank to run until you cancel."
          />
        )}

        <div className="modal-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving}>
            Schedule payment
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
