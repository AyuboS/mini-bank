import { useEffect, useId, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { accountsApi } from '../api/accounts';
import type { AccountResponse, TransferResponse } from '../api/types';
import { ErrorState } from '../components/States';
import { Button, Select, Skeleton } from '../components/ui';
import { useAsync } from '../hooks/useAsync';
import { mustKeepIdempotencyKey, toFriendlyError, type FriendlyError } from '../utils/errors';
import { currencySymbol, formatDateTime, formatMoney, maskAccountNumber } from '../utils/format';
import { cleanAmountInput, formatMinorUnits, toMinorUnits } from '../utils/money';
import { createPaymentAttempt } from '../utils/paymentAttempt';

const STEPS = ['Amount', 'Review', 'Receipt'] as const;
type Step = (typeof STEPS)[number];

// Mirrors minibank.top-up.per-top-up-max on the server, which has the final say.
const PER_TOP_UP_MAX_MINOR = 100_000;

/** Adds play money to a spending account. Nothing here touches real money. */
export function TopUpPage() {
  const [searchParams] = useSearchParams();
  const { data: accounts, loading, error, reload } = useAsync(async () =>
    (await accountsApi.list()).filter((account) => account.status === 'ACTIVE' && account.purpose === 'SPENDING'),
  );

  const [step, setStep] = useState<Step>('Amount');
  const [accountId, setAccountId] = useState(searchParams.get('account') ?? '');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<FriendlyError | null>(null);
  const [outcomeUnknown, setOutcomeUnknown] = useState(false);
  const [receipt, setReceipt] = useState<TransferResponse | null>(null);

  const attempt = useRef(createPaymentAttempt());
  const headingRef = useRef<HTMLHeadingElement | null>(null);
  const firstRender = useRef(true);

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    headingRef.current?.focus();
  }, [step]);

  const account = accounts?.find((candidate) => candidate.id === accountId) ?? accounts?.[0] ?? null;

  const confirm = async () => {
    if (!account) return;
    const request = { accountId: account.id, amount };
    setBusy(true);
    setFailure(null);
    try {
      const transfer = await accountsApi.topUp(account.id, amount, attempt.current.keyFor(request));
      attempt.current.finish();
      setReceipt(transfer);
      setStep('Receipt');
      void reload();
    } catch (err) {
      const unknown = mustKeepIdempotencyKey(err);
      if (!unknown) attempt.current.finish();
      setOutcomeUnknown(unknown);
      setFailure(toFriendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const startAgain = () => {
    setStep('Amount');
    setAmount('');
    setReceipt(null);
    setFailure(null);
  };

  if (loading && !accounts) {
    return (
      <div className="send">
        <Skeleton height={280} />
      </div>
    );
  }
  if (error || !accounts) {
    return <ErrorState title={error?.title ?? "We couldn't load this"} body={error?.body ?? ''} onRetry={reload} />;
  }
  if (!account) {
    return <ErrorState title="No account to top up" body="Top-ups go to an active spending account." />;
  }

  const current = STEPS.indexOf(step);

  return (
    <div className="send">
      <div>
        <h1 className="page__title">Simulated top-up</h1>
        <p className="page__subtitle">Adds demo money to your account. No real money or card is involved.</p>
      </div>

      <ol className="steps" aria-label="Progress">
        {STEPS.map((name, index) => (
          <li
            key={name}
            className={`steps__item ${index === current ? 'steps__item--current' : ''} ${index < current ? 'steps__item--done' : ''}`}
            aria-current={index === current ? 'step' : undefined}
          >
            <span className="steps__number" aria-hidden="true">
              {index + 1}
            </span>
            {name}
          </li>
        ))}
      </ol>

      <h2 className="sr-only" tabIndex={-1} ref={headingRef}>
        Step {current + 1} of {STEPS.length}: {step}
      </h2>

      {step === 'Amount' && (
        <TopUpAmount
          accounts={accounts}
          account={account}
          amount={amount}
          onAccountChange={setAccountId}
          onAmountChange={setAmount}
          onContinue={() => {
            setFailure(null);
            setStep('Review');
          }}
        />
      )}

      {step === 'Review' && (
        <div className="send-step">
          {failure && (
            <div className="send-alert" role="alert">
              <p className="send-alert__title">{failure.title}</p>
              {failure.body && <p>{failure.body}</p>}
              {outcomeUnknown && <p>The top-up may still have gone through. Trying again is safe: it will not be added twice.</p>}
            </div>
          )}
          <dl className="send-summary">
            <div>
              <dt>To</dt>
              <dd>Spending {maskAccountNumber(account.accountNumber)}</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>Simulated top-up (demo money)</dd>
            </div>
            <div className="send-summary__total">
              <dt>Amount</dt>
              <dd>{formatMinorUnits(toMinorUnits(amount), account.currency)}</dd>
            </div>
            <div>
              <dt>Balance after</dt>
              <dd>{formatMinorUnits(toMinorUnits(account.balance) + toMinorUnits(amount), account.currency)}</dd>
            </div>
          </dl>
          <div className="send-actions">
            <Button variant="secondary" onClick={() => setStep('Amount')} disabled={busy}>
              Back
            </Button>
            <Button variant="primary" loading={busy} onClick={confirm}>
              {busy ? 'Adding…' : `Add ${formatMinorUnits(toMinorUnits(amount), account.currency)}`}
            </Button>
          </div>
        </div>
      )}

      {step === 'Receipt' && receipt && (
        <div className="send-step">
          <div role="status">
            <p className="send-receipt__title">Simulated top-up added</p>
            <p className="send-receipt__note">The demo money is in your account now.</p>
          </div>
          <dl className="send-summary">
            <div className="send-summary__total">
              <dt>Amount</dt>
              <dd>{formatMoney(receipt.amount, receipt.currency)}</dd>
            </div>
            <div>
              <dt>To</dt>
              <dd>{maskAccountNumber(receipt.toAccountNumber)}</dd>
            </div>
            <div>
              <dt>Description</dt>
              <dd>{receipt.description}</dd>
            </div>
            <div>
              <dt>Date</dt>
              <dd>{formatDateTime(receipt.completedAt ?? receipt.createdAt)}</dd>
            </div>
            <div>
              <dt>Reference</dt>
              <dd className="send-summary__reference">{receipt.reference}</dd>
            </div>
          </dl>
          <div className="send-actions">
            <Link className="btn btn-secondary" to="/">
              Back to Home
            </Link>
            <Button variant="primary" onClick={startAgain}>
              Top up again
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

interface TopUpAmountProps {
  accounts: AccountResponse[];
  account: AccountResponse;
  amount: string;
  onAccountChange: (id: string) => void;
  onAmountChange: (amount: string) => void;
  onContinue: () => void;
}

function TopUpAmount({ accounts, account, amount, onAccountChange, onAmountChange, onContinue }: TopUpAmountProps) {
  const amountId = useId();
  const amountMinor = toMinorUnits(amount);
  const overCap = amountMinor > PER_TOP_UP_MAX_MINOR;
  const canContinue = amountMinor > 0 && !overCap;
  const cap = formatMinorUnits(PER_TOP_UP_MAX_MINOR, account.currency);

  return (
    <form
      className="send-step"
      onSubmit={(event) => {
        event.preventDefault();
        if (canContinue) onContinue();
      }}
    >
      {accounts.length > 1 && (
        <Select label="To" value={account.id} onChange={(event) => onAccountChange(event.target.value)}>
          {accounts.map((option) => (
            <option key={option.id} value={option.id}>
              Spending {maskAccountNumber(option.accountNumber)} · {formatMoney(option.balance, option.currency)}
            </option>
          ))}
        </Select>
      )}

      <div className={`field ${overCap ? 'field--error' : ''}`}>
        <label className="field__label" htmlFor={amountId}>
          Amount
        </label>
        <div className="field__well well send-amount">
          <span className="send-amount__symbol" aria-hidden="true">
            {currencySymbol(account.currency)}
          </span>
          <input
            id={amountId}
            className="field__input"
            type="text"
            inputMode="decimal"
            placeholder="0.00"
            autoComplete="off"
            value={amount}
            onChange={(event) => onAmountChange(cleanAmountInput(event.target.value))}
            aria-invalid={overCap || undefined}
            aria-describedby={`${amountId}-message`}
          />
        </div>
        <p id={`${amountId}-message`} className={overCap ? 'field__error' : 'field__hint'} role={overCap ? 'alert' : undefined}>
          {overCap ? `A single top-up can be at most ${cap}.` : `Up to ${cap} per top-up. Daily limits apply.`}
        </p>
      </div>

      <div className="send-actions">
        <Button type="submit" variant="primary" disabled={!canContinue}>
          Continue
        </Button>
      </div>
    </form>
  );
}
