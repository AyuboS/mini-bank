import { useMemo, useState } from 'react';
import { Archive, Minus, PiggyBank, Plus, Target } from 'lucide-react';
import { savingsApi } from '../api/savings';
import { accountsApi } from '../api/accounts';
import type { AccountResponse, GoalAccent, SavingsGoalResponse } from '../api/types';
import { GOAL_ACCENTS } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import {
  Button,
  Input,
  Progress,
  Select,
  Sheet,
  Skeleton,
  Surface,
  StatusBadge,
} from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState } from '../components/States';
import { formatDay, formatMoney, toDateKey, toNumber } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

/** The accent tokens, so a goal's colour is one of the wallet's own. */
const ACCENT_COLOUR: Record<string, string> = {
  violet: 'var(--c-violet)',
  cyan: 'var(--c-teal)',
  emerald: 'var(--c-green)',
  amber: 'var(--c-amber)',
  coral: 'var(--c-coral)',
  blue: 'var(--c-blue)',
};

/**
 * Savings goals.
 *
 * Each goal is backed by a real account, and contributing performs a real
 * settled transfer. The interface says so plainly rather than presenting a
 * progress bar over a number that lives nowhere: the account number is on the
 * card, and the money can be taken back out at any time.
 */
export function SavingsPage() {
  const toast = useToast();
  const goals = useAsync(() => savingsApi.list(), []);
  const accounts = useAsync(() => accountsApi.list(), []);

  const [editing, setEditing] = useState<SavingsGoalResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [moving, setMoving] = useState<{ goal: SavingsGoalResponse; direction: 'in' | 'out' } | null>(null);

  /** Only spending accounts can fund a goal; the server enforces the same rule. */
  const spendingAccounts = useMemo(
    () => (accounts.data ?? []).filter((account) => account.purpose === 'SPENDING'),
    [accounts.data],
  );

  const active = (goals.data ?? []).filter((goal) => goal.status !== 'ARCHIVED');
  const archived = (goals.data ?? []).filter((goal) => goal.status === 'ARCHIVED');

  const totalSaved = active.reduce((sum, goal) => sum + toNumber(goal.saved), 0);
  const currency = active[0]?.currency ?? 'EUR';

  const archive = async (goal: SavingsGoalResponse) => {
    try {
      await savingsApi.archive(goal.id);
      toast.success('Goal archived');
      goals.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <div className="stack-lg">
      <PageHeader
        title="Savings"
        subtitle="Money set aside, in an account of its own. Take it back whenever you want."
        actions={
          <Button variant="primary" icon={<Plus />} onClick={() => setCreating(true)}>
            New goal
          </Button>
        }
      />

      {active.length > 0 && (
        <Surface tone="hero" className="hero hero--compact">
          <span className="label">Total set aside</span>
          <p className="balance balance--sm">{formatMoney(totalSaved, currency)}</p>
          <p className="dim" style={{ fontSize: 'var(--text-small)' }}>
            Across {active.length} goal{active.length === 1 ? '' : 's'}
          </p>
        </Surface>
      )}

      {goals.loading ? (
        <div className="goal-grid">
          <Skeleton height="180px" />
          <Skeleton height="180px" />
        </div>
      ) : goals.error ? (
        <ErrorState title={goals.error.title} body={goals.error.body} onRetry={goals.reload} />
      ) : active.length === 0 ? (
        <Surface>
          <EmptyState
            title="No savings goals yet"
            body="Name something you are saving for, set a target, and put money aside for it. The money stays in an account you own."
            icon={<PiggyBank />}
            action={
              <Button variant="primary" icon={<Plus />} onClick={() => setCreating(true)}>
                Start a goal
              </Button>
            }
          />
        </Surface>
      ) : (
        <div className="goal-grid">
          {active.map((goal) => (
            <GoalCard
              key={goal.id}
              goal={goal}
              onAdd={() => setMoving({ goal, direction: 'in' })}
              onWithdraw={() => setMoving({ goal, direction: 'out' })}
              onEdit={() => setEditing(goal)}
              onArchive={() => archive(goal)}
            />
          ))}
        </div>
      )}

      {archived.length > 0 && (
        <section className="stack">
          <h2>Archived</h2>
          <Surface className="list-panel">
            {archived.map((goal) => (
              <div className="row-between" key={goal.id} style={{ padding: 'var(--space-4)' }}>
                <span className="dim">{goal.name}</span>
                <StatusBadge status={goal.status} />
              </div>
            ))}
          </Surface>
        </section>
      )}

      <GoalSheet
        /*
          Keyed on which goal is open, so the form remounts with fresh state
          rather than showing the previous goal's values. This is React's own
          answer to "reset state when a prop changes", and it is why the sheet
          needs no effect to re-seed itself.
        */
        key={editing?.id ?? 'new-goal'}
        open={creating || editing !== null}
        goal={editing}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        onSaved={() => {
          setCreating(false);
          setEditing(null);
          goals.reload();
        }}
      />

      {moving && (
        <MoveMoneySheet
          goal={moving.goal}
          direction={moving.direction}
          accounts={spendingAccounts}
          onClose={() => setMoving(null)}
          onDone={() => {
            setMoving(null);
            goals.reload();
            accounts.reload();
          }}
        />
      )}
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function GoalCard({
  goal,
  onAdd,
  onWithdraw,
  onEdit,
  onArchive,
}: {
  goal: SavingsGoalResponse;
  onAdd: () => void;
  onWithdraw: () => void;
  onEdit: () => void;
  onArchive: () => void;
}) {
  const accent = ACCENT_COLOUR[goal.accent] ?? 'var(--c-violet)';
  const empty = toNumber(goal.saved) === 0;

  return (
    <Surface className="goal-card" style={{ ['--goal-accent' as string]: accent }}>
      <div className="goal-card__glow" aria-hidden="true" />

      <div className="row-between">
        <div className="stack" style={{ gap: 2 }}>
          <h3 className="goal-card__name">{goal.name}</h3>
          {goal.deadline && <span className="dim goal-card__deadline">by {formatDay(goal.deadline)}</span>}
        </div>
        {goal.status === 'ACHIEVED' && <StatusBadge status={goal.status} />}
      </div>

      <div className="goal-card__figures">
        <span className="goal-card__saved amount">{formatMoney(goal.saved, goal.currency)}</span>
        <span className="dim">of {formatMoney(goal.targetAmount, goal.currency)}</span>
      </div>

      <Progress
        percent={goal.progressPercent}
        tone={goal.status === 'ACHIEVED' ? 'positive' : 'brand'}
        label={`${goal.name}: ${goal.progressPercent}% of target saved`}
      />

      <div className="row-between goal-card__meta">
        <span className="dim">{goal.progressPercent}%</span>
        <span className="dim">{formatMoney(goal.remaining, goal.currency)} to go</span>
      </div>

      <div className="goal-card__actions">
        <Button size="sm" variant="primary" icon={<Plus />} onClick={onAdd}>
          Add
        </Button>
        <Button size="sm" variant="secondary" icon={<Minus />} onClick={onWithdraw} disabled={empty}>
          Withdraw
        </Button>
        {/* Icon-only, so square rather than a button with an empty label slot —
            four full-width controls do not fit a 300px goal card. */}
        <Button size="sm" variant="ghost" className="btn-icon" icon={<Target />} onClick={onEdit} aria-label="Edit goal" />
        <Button
          size="sm"
          variant="ghost"
          className="btn-icon"
          icon={<Archive />}
          onClick={onArchive}
          disabled={!empty}
          /* Explains the disabled state rather than leaving a dead control. */
          title={empty ? 'Archive this goal' : 'Withdraw the money before archiving'}
          aria-label="Archive goal"
        />
      </div>

      {/* The account is named, because the money is genuinely in one. */}
      <p className="goal-card__account dim">Held in {goal.accountNumber}</p>
    </Surface>
  );
}

/* ---------------------------------------------------------------------------- */

function GoalSheet({
  open,
  goal,
  onClose,
  onSaved,
}: {
  open: boolean;
  goal: SavingsGoalResponse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(goal?.name ?? '');
  const [target, setTarget] = useState(goal ? String(toNumber(goal.targetAmount)) : '');
  const [deadline, setDeadline] = useState(goal?.deadline ?? '');
  const [accent, setAccent] = useState<GoalAccent>((goal?.accent as GoalAccent) ?? 'violet');
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      const body = {
        name: name.trim(),
        targetAmount: target,
        currency: goal?.currency ?? ('EUR' as const),
        ...(deadline ? { deadline } : {}),
        accent,
      };
      if (goal) {
        await savingsApi.update(goal.id, body);
        toast.success('Goal updated');
      } else {
        await savingsApi.create(body);
        toast.success('Goal created', 'MBank opened an account to hold it.');
      }
      onSaved();
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
      title={goal ? 'Edit goal' : 'New savings goal'}
      subtitle={goal ? undefined : 'MBank opens a dedicated account to hold the money.'}
    >
      <form className="stack-lg" onSubmit={submit}>
        <Input
          label="What are you saving for?"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Thailand trip"
          maxLength={60}
          required
        />

        <Input
          label="Target amount"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0.01"
          value={target}
          onChange={(event) => setTarget(event.target.value)}
          placeholder="1500.00"
          suffix={<span className="dim">{goal?.currency ?? 'EUR'}</span>}
          required
        />

        <Input
          label="Target date"
          type="date"
          value={deadline}
          onChange={(event) => setDeadline(event.target.value)}
          min={toDateKey()}
          hint="Optional. Saving without a date is a perfectly good way to save."
        />

        <div className="field">
          <span className="field__label">Colour</span>
          <div className="accent-row">
            {GOAL_ACCENTS.map((option) => (
              <button
                key={option}
                type="button"
                className={`accent-dot ${accent === option ? 'accent-dot--selected' : ''}`}
                style={{ background: ACCENT_COLOUR[option] }}
                onClick={() => setAccent(option)}
                aria-label={option}
                aria-pressed={accent === option}
              />
            ))}
          </div>
        </div>

        <div className="modal-actions">
          <Button variant="secondary" onClick={onClose} type="button">
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving}>
            {goal ? 'Save changes' : 'Create goal'}
          </Button>
        </div>
      </form>
    </Sheet>
  );
}

/* ---------------------------------------------------------------------------- */

function MoveMoneySheet({
  goal,
  direction,
  accounts,
  onClose,
  onDone,
}: {
  goal: SavingsGoalResponse;
  direction: 'in' | 'out';
  accounts: AccountResponse[];
  onClose: () => void;
  onDone: () => void;
}) {
  const toast = useToast();
  const eligible = accounts.filter((account) => account.currency === goal.currency);
  const [accountId, setAccountId] = useState(eligible[0]?.id ?? '');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const body = { accountId, amount };
      if (direction === 'in') {
        await savingsApi.contribute(goal.id, body);
        toast.success('Money moved into the goal');
      } else {
        await savingsApi.withdraw(goal.id, body);
        toast.success('Money moved back out');
      }
      onDone();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet
      open
      onClose={onClose}
      title={direction === 'in' ? `Add to ${goal.name}` : `Withdraw from ${goal.name}`}
      subtitle={
        direction === 'in'
          ? 'A real transfer from your spending account into the goal.'
          : `${formatMoney(goal.saved, goal.currency)} is currently in this goal.`
      }
    >
      <form className="stack-lg" onSubmit={submit}>
        {eligible.length === 0 ? (
          <p className="dim">
            You have no {goal.currency} spending account to move money {direction === 'in' ? 'from' : 'to'}.
          </p>
        ) : (
          <>
            <Select
              label={direction === 'in' ? 'From' : 'To'}
              value={accountId}
              onChange={(event) => setAccountId(event.target.value)}
              required
            >
              {eligible.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.accountNumber} — {formatMoney(account.balance, account.currency)}
                </option>
              ))}
            </Select>

            <Input
              label="Amount"
              type="number"
              inputMode="decimal"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="50.00"
              suffix={<span className="dim">{goal.currency}</span>}
              autoFocus
              required
            />

            <div className="modal-actions">
              <Button variant="secondary" onClick={onClose} type="button">
                Cancel
              </Button>
              <Button variant="primary" type="submit" loading={busy}>
                {direction === 'in' ? 'Add money' : 'Withdraw'}
              </Button>
            </div>
          </>
        )}
      </form>
    </Sheet>
  );
}
