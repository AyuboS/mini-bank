import { useState, type FormEvent } from 'react';
import { savingsApi } from '../../api/savings';
import type { AccountResponse, SavingsGoalResponse } from '../../api/types';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Select, Sheet } from '../../components/ui';
import { toFriendlyError } from '../../utils/errors';
import { formatMoney, maskAccountNumber } from '../../utils/format';
import { cleanAmountInput, toMinorUnits } from '../../utils/money';

interface MoveMoneySheetProps {
  goal: SavingsGoalResponse;
  direction: 'in' | 'out';
  accounts: AccountResponse[];
  onClose: () => void;
  onDone: () => void;
}

/** Moves money between a spending account and a goal's own account. */
export function MoveMoneySheet({ goal, direction, accounts, onClose, onDone }: MoveMoneySheetProps) {
  const toast = useToast();
  const eligible = accounts.filter((account) => account.currency === goal.currency);
  const [accountId, setAccountId] = useState(eligible[0]?.id ?? '');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);

  const account = eligible.find((candidate) => candidate.id === accountId);
  // In whole cents. The server checks again and its answer is the one that counts.
  const available = direction === 'in' ? toMinorUnits(account?.balance) : toMinorUnits(goal.saved);
  const amountMinor = toMinorUnits(amount);
  const tooMuch = amountMinor > available;
  const valid = account !== undefined && amountMinor > 0 && !tooMuch;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!valid) return;
    setBusy(true);
    try {
      const body = { accountId, amount };
      if (direction === 'in') {
        await savingsApi.contribute(goal.id, body);
        toast.success('Money added to the goal');
      } else {
        await savingsApi.withdraw(goal.id, body);
        toast.success('Money moved back to spending');
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
      subtitle={`${formatMoney(goal.saved, goal.currency)} is in this goal now.`}
    >
      {eligible.length === 0 ? (
        <p className="dim">
          You have no active {goal.currency} spending account to move money {direction === 'in' ? 'from' : 'to'}.
        </p>
      ) : (
        <form className="sheet-form" onSubmit={submit}>
          <Select
            label={direction === 'in' ? 'From' : 'To'}
            value={accountId}
            onChange={(event) => setAccountId(event.target.value)}
          >
            {eligible.map((option) => (
              <option key={option.id} value={option.id}>
                Spending {maskAccountNumber(option.accountNumber)} · {formatMoney(option.balance, option.currency)}
              </option>
            ))}
          </Select>
          <Input
            label="Amount"
            inputMode="decimal"
            autoComplete="off"
            value={amount}
            onChange={(event) => setAmount(cleanAmountInput(event.target.value))}
            placeholder="50.00"
            suffix={<span className="dim">{goal.currency}</span>}
            error={tooMuch ? `That is more than the ${direction === 'in' ? 'account holds' : 'goal holds'}.` : undefined}
            autoFocus
            required
          />
          <div className="modal-actions">
            <Button variant="secondary" onClick={onClose} type="button">
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={busy} disabled={!valid}>
              {direction === 'in' ? 'Add money' : 'Withdraw'}
            </Button>
          </div>
        </form>
      )}
    </Sheet>
  );
}
