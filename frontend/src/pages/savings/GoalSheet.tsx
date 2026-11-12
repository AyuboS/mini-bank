import { useState, type FormEvent } from 'react';
import { savingsApi } from '../../api/savings';
import type { SavingsGoalResponse } from '../../api/types';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Sheet } from '../../components/ui';
import { toFriendlyError } from '../../utils/errors';
import { toDateKey } from '../../utils/format';
import { cleanAmountInput, fromMinorUnits, toMinorUnits } from '../../utils/money';

interface GoalSheetProps {
  open: boolean;
  goal: SavingsGoalResponse | null;
  onClose: () => void;
  onSaved: () => void;
}

/** Creates a goal, or edits the name, target and date of one. */
export function GoalSheet({ open, goal, onClose, onSaved }: GoalSheetProps) {
  const toast = useToast();
  const [name, setName] = useState(goal?.name ?? '');
  const [target, setTarget] = useState(goal ? fromMinorUnits(toMinorUnits(goal.targetAmount)) : '');
  const [deadline, setDeadline] = useState(goal?.deadline ?? '');
  const [saving, setSaving] = useState(false);

  const valid = name.trim().length > 0 && toMinorUnits(target) > 0;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!valid) return;
    setSaving(true);
    try {
      const body = {
        name: name.trim(),
        targetAmount: target,
        currency: goal?.currency ?? ('EUR' as const),
        ...(deadline ? { deadline } : {}),
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
      subtitle={goal ? undefined : 'MBank opens an account to hold the money.'}
    >
      <form className="sheet-form" onSubmit={submit}>
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
          inputMode="decimal"
          autoComplete="off"
          value={target}
          onChange={(event) => setTarget(cleanAmountInput(event.target.value))}
          placeholder="1500.00"
          suffix={<span className="dim">{goal?.currency ?? 'EUR'}</span>}
          required
        />
        <Input
          label="Target date (optional)"
          type="date"
          value={deadline}
          onChange={(event) => setDeadline(event.target.value)}
          min={toDateKey()}
        />
        <div className="modal-actions">
          <Button variant="secondary" onClick={onClose} type="button">
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving} disabled={!valid}>
            {goal ? 'Save changes' : 'Create goal'}
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
