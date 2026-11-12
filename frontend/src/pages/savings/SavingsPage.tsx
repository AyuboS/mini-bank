import { useMemo, useState } from 'react';
import { accountsApi } from '../../api/accounts';
import { savingsApi } from '../../api/savings';
import type { SavingsGoalResponse } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
import { ErrorState } from '../../components/States';
import { useToast } from '../../components/ToastProvider';
import { Button, Progress, Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { toFriendlyError } from '../../utils/errors';
import { formatDay, formatMoney } from '../../utils/format';
import { formatMinorUnits, sumMinorUnits, toMinorUnits } from '../../utils/money';
import { GoalSheet } from './GoalSheet';
import { MoveMoneySheet } from './MoveMoneySheet';

/**
 * Savings goals. Each goal is a real account of its own, and adding or
 * withdrawing is a settled transfer, so the account number is shown.
 */
export function SavingsPage() {
  const toast = useToast();
  const goals = useAsync(() => savingsApi.list(), []);
  const accounts = useAsync(() => accountsApi.list(), []);

  const [editing, setEditing] = useState<SavingsGoalResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [moving, setMoving] = useState<{ goal: SavingsGoalResponse; direction: 'in' | 'out' } | null>(null);

  // Only spending accounts can fund a goal; the server enforces the same rule.
  const spendingAccounts = useMemo(
    () => (accounts.data ?? []).filter((account) => account.purpose === 'SPENDING' && account.status === 'ACTIVE'),
    [accounts.data],
  );

  const active = (goals.data ?? []).filter((goal) => goal.status !== 'ARCHIVED');
  const archived = (goals.data ?? []).filter((goal) => goal.status === 'ARCHIVED');
  const currency = active[0]?.currency ?? 'EUR';
  const totalSaved = sumMinorUnits(active.filter((goal) => goal.currency === currency).map((goal) => goal.saved));

  const archive = async (goal: SavingsGoalResponse) => {
    try {
      await savingsApi.archive(goal.id);
      toast.success('Goal archived');
      void goals.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <>
      <PageHeader
        title="Savings"
        subtitle="Money set aside in an account of its own. Take it back whenever you want."
        actions={
          <Button variant="primary" onClick={() => setCreating(true)}>
            New goal
          </Button>
        }
      />

      {goals.loading && !goals.data ? (
        <Skeleton height="180px" />
      ) : goals.error ? (
        <ErrorState title={goals.error.title} body={goals.error.body} onRetry={goals.reload} />
      ) : active.length === 0 ? (
        <p className="quiet-empty">
          No goals yet. Name something you are saving for and set a target; MBank opens an account to hold the money.
        </p>
      ) : (
        <>
          <dl className="figures savings-figures">
            <div>
              <dt>Set aside</dt>
              <dd className="figures__total">{formatMinorUnits(totalSaved, currency)}</dd>
            </div>
            <div>
              <dt>Goals</dt>
              <dd>{active.length}</dd>
            </div>
          </dl>

          <ul className="goals">
            {active.map((goal) => (
              <GoalRow
                key={goal.id}
                goal={goal}
                onAdd={() => setMoving({ goal, direction: 'in' })}
                onWithdraw={() => setMoving({ goal, direction: 'out' })}
                onEdit={() => setEditing(goal)}
                onArchive={() => archive(goal)}
              />
            ))}
          </ul>
        </>
      )}

      {archived.length > 0 && (
        <section className="savings-archived">
          <h2 className="section-title">Archived</h2>
          <ul className="plain-list">
            {archived.map((goal) => (
              <li key={goal.id}>
                <span>{goal.name}</span>
                <span className="dim">Target {formatMoney(goal.targetAmount, goal.currency)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <GoalSheet
        // Keyed so the form remounts with the chosen goal's values.
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
          void goals.reload();
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
            void goals.reload();
            void accounts.reload();
          }}
        />
      )}
    </>
  );
}

interface GoalRowProps {
  goal: SavingsGoalResponse;
  onAdd: () => void;
  onWithdraw: () => void;
  onEdit: () => void;
  onArchive: () => void;
}

function GoalRow({ goal, onAdd, onWithdraw, onEdit, onArchive }: GoalRowProps) {
  const empty = toMinorUnits(goal.saved) === 0;
  const achieved = goal.status === 'ACHIEVED';

  return (
    <li className="goal">
      <div className="goal__head">
        <div className="goal__name">
          <h2>{goal.name}</h2>
          <span className="goal__sub">
            {goal.deadline ? `By ${formatDay(goal.deadline)} · ` : ''}
            {goal.accountNumber}
          </span>
        </div>
        <p className="goal__figure">
          {formatMoney(goal.saved, goal.currency)}
          <span className="goal__sub"> of {formatMoney(goal.targetAmount, goal.currency)}</span>
        </p>
      </div>

      <Progress percent={goal.progressPercent} tone="brand" size="sm" label={`${goal.name}: ${goal.progressPercent}% saved`} />

      <div className="goal__foot">
        <span className="goal__sub">
          {achieved ? 'Target reached' : `${goal.progressPercent}% · ${formatMoney(goal.remaining, goal.currency)} to go`}
        </span>
        <div className="goal__actions">
          <Button size="sm" variant="secondary" onClick={onAdd}>
            Add money
          </Button>
          <Button size="sm" variant="ghost" onClick={onWithdraw} disabled={empty}>
            Withdraw
          </Button>
          <Button size="sm" variant="ghost" onClick={onEdit}>
            Edit
          </Button>
          {empty && (
            <Button size="sm" variant="ghost" onClick={onArchive}>
              Archive
            </Button>
          )}
        </div>
      </div>
    </li>
  );
}
