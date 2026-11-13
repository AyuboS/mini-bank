import { useState } from 'react';
import { scheduledApi } from '../../api/scheduled';
import type { ScheduleFrequency, TransactionCategory } from '../../api/types';
import { TRANSACTION_CATEGORIES } from '../../api/types';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Select, Sheet } from '../../components/ui';
import { CATEGORY_META } from '../../utils/categories';
import { formatDay, formatMoney, toDateKey } from '../../utils/format';
import { toFriendlyError } from '../../utils/errors';

export function ScheduleSheet({
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
