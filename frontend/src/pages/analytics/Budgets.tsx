import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import { budgetsApi } from '../../api/budgets';
import type { BudgetResponse, CurrencyCode, TransactionCategory } from '../../api/types';
import { TRANSACTION_CATEGORIES } from '../../api/types';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Progress, Select, Sheet } from '../../components/ui';
import { CategoryIcon } from '../../components/CategoryIcon';
import { CATEGORY_META } from '../../utils/categories';
import { formatMoney } from '../../utils/format';
import { formatMinorUnits, toMinorUnits } from '../../utils/money';
import { toFriendlyError } from '../../utils/errors';

export function BudgetRow({ budget, onChanged }: { budget: BudgetResponse; onChanged: () => void }) {
  const toast = useToast();
  const meta = CATEGORY_META[budget.category];

  const remove = async () => {
    try {
      await budgetsApi.remove(budget.id);
      toast.success('Budget removed');
      onChanged();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <div className="budget-row">
      <div className="row-between">
        <span className="row gap-2">
          <CategoryIcon category={budget.category} size="sm" />
          <span className="budget-row__name">{meta.label}</span>
        </span>
        <span className="row gap-2">
          <span className="amount budget-row__amount">
            {formatMoney(budget.spent, budget.currency)}
            <span className="dim"> / {formatMoney(budget.amount, budget.currency)}</span>
          </span>
          <button type="button" className="icon-btn" onClick={remove} aria-label={`Remove the ${meta.label} budget`}>
            <Trash2 aria-hidden="true" />
          </button>
        </span>
      </div>

      <Progress
        percent={budget.usedPercent}
        tone={budget.state === 'OVER' ? 'over' : budget.state === 'NEARING' ? 'warning' : 'positive'}
        label={`${meta.label}: ${budget.usedPercentRaw}% of budget used`}
        size="sm"
      />

      <span className={`budget-row__state budget-row__state--${budget.state.toLowerCase()}`}>
        {budget.state === 'OVER'
          ? `${budget.usedPercentRaw}% used — over by ${formatMinorUnits(
              toMinorUnits(budget.spent) - toMinorUnits(budget.amount),
              budget.currency,
            )}`
          : `${budget.usedPercentRaw}% used · ${formatMoney(budget.remaining, budget.currency)} left`}
      </span>
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

export function BudgetSheet({
  open,
  currency,
  existing,
  onClose,
  onSaved,
}: {
  open: boolean;
  currency: CurrencyCode;
  existing: BudgetResponse[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [category, setCategory] = useState<TransactionCategory>('GROCERIES');
  const [amount, setAmount] = useState('');
  const [saving, setSaving] = useState(false);

  /** Only categories that represent spending; a budget on Salary is meaningless. */
  const options = TRANSACTION_CATEGORIES.filter((candidate) => CATEGORY_META[candidate].spending);
  const already = existing.find((budget) => budget.category === category);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      await budgetsApi.set({ category, amount, currency });
      toast.success(already ? 'Budget updated' : 'Budget set');
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
      title="Set a monthly budget"
      subtitle="Progress is measured against your real transactions, and updates as they arrive."
    >
      <form className="stack-lg" onSubmit={submit}>
        <Select
          label="Category"
          value={category}
          onChange={(event) => setCategory(event.target.value as TransactionCategory)}
        >
          {options.map((option) => (
            <option key={option} value={option}>
              {CATEGORY_META[option].label}
            </option>
          ))}
        </Select>

        <Input
          label="Monthly limit"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0.01"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="300.00"
          suffix={<span className="dim">{currency}</span>}
          hint={already ? `Currently ${formatMoney(already.amount, currency)}. Saving replaces it.` : undefined}
          required
        />

        <div className="modal-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving}>
            {already ? 'Update budget' : 'Set budget'}
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
