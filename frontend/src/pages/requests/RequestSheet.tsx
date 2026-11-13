import { useState } from 'react';
import { moneyRequestsApi } from '../../api/moneyRequests';
import type { AccountResponse } from '../../api/types';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Select, Sheet } from '../../components/ui';
import { toFriendlyError } from '../../utils/errors';

export function RequestSheet({
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
