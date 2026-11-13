import { useId } from 'react';
import type { AccountResponse, TransactionCategory } from '../../api/types';
import { TRANSACTION_CATEGORIES } from '../../api/types';
import { Button, Input, Select } from '../../components/ui';
import { CATEGORY_META } from '../../utils/categories';
import { currencySymbol, formatMoney, maskAccountNumber } from '../../utils/format';
import { cleanAmountInput, toMinorUnits } from '../../utils/money';

export interface PaymentDraft {
  fromAccountId: string;
  amount: string;
  description: string;
  category: TransactionCategory | '';
}

interface AmountStepProps {
  accounts: AccountResponse[];
  draft: PaymentDraft;
  onChange: (patch: Partial<PaymentDraft>) => void;
  onBack: () => void;
  onContinue: () => void;
}

/** Step 2: which account pays, how much, and what it is for. */
export function AmountStep({ accounts, draft, onChange, onBack, onContinue }: AmountStepProps) {
  const amountId = useId();
  const source = accounts.find((account) => account.id === draft.fromAccountId) ?? null;
  const currency = source?.currency ?? 'EUR';

  // Compared in whole cents. The server checks again, and its answer is the one that counts.
  const amountMinor = toMinorUnits(draft.amount);
  const overBalance = source !== null && amountMinor > toMinorUnits(source.balance);
  const canContinue = source !== null && amountMinor > 0 && !overBalance;

  return (
    <form
      className="send-step"
      onSubmit={(event) => {
        event.preventDefault();
        if (canContinue) onContinue();
      }}
    >
      <Select label="From" value={draft.fromAccountId} onChange={(event) => onChange({ fromAccountId: event.target.value })}>
        {accounts.map((account) => (
          <option key={account.id} value={account.id}>
            {account.purpose === 'SAVINGS' ? 'Savings' : 'Spending'} {maskAccountNumber(account.accountNumber)} ·{' '}
            {formatMoney(account.balance, account.currency)}
          </option>
        ))}
      </Select>

      <div className={`field ${overBalance ? 'field--error' : ''}`}>
        <label className="field__label" htmlFor={amountId}>
          Amount
        </label>
        <div className="field__well well send-amount">
          <span className="send-amount__symbol" aria-hidden="true">
            {currencySymbol(currency)}
          </span>
          {/* Text with a decimal keypad: a number input changes value on scroll and accepts "e". */}
          <input
            id={amountId}
            className="field__input"
            type="text"
            inputMode="decimal"
            placeholder="0.00"
            autoComplete="off"
            value={draft.amount}
            onChange={(event) => onChange({ amount: cleanAmountInput(event.target.value) })}
            aria-invalid={overBalance || undefined}
            aria-describedby={`${amountId}-message`}
          />
        </div>
        <p id={`${amountId}-message`} className={overBalance ? 'field__error' : 'field__hint'} role={overBalance ? 'alert' : undefined}>
          {source &&
            (overBalance
              ? `That is more than the ${formatMoney(source.balance, currency)} available.`
              : `${formatMoney(source.balance, currency)} available`)}
        </p>
      </div>

      <Input
        label="Reference (optional)"
        placeholder="Rent"
        maxLength={140}
        value={draft.description}
        onChange={(event) => onChange({ description: event.target.value })}
      />

      <Select
        label="Category (optional)"
        value={draft.category}
        onChange={(event) => onChange({ category: event.target.value as TransactionCategory | '' })}
        hint="Left empty, MBank picks one from the reference."
      >
        <option value="">Choose for me</option>
        {TRANSACTION_CATEGORIES.map((option) => (
          <option key={option} value={option}>
            {CATEGORY_META[option].label}
          </option>
        ))}
      </Select>

      <div className="send-actions">
        <Button type="button" variant="secondary" onClick={onBack}>
          Back
        </Button>
        <Button type="submit" variant="primary" disabled={!canContinue}>
          Continue
        </Button>
      </div>
    </form>
  );
}
