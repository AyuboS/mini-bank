import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { accountsApi } from '../../api/accounts';
import { beneficiariesApi } from '../../api/beneficiaries';
import { transfersApi } from '../../api/transfers';
import type { PayeeLookupResponse, TransferResponse } from '../../api/types';
import { ErrorState } from '../../components/States';
import { Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { mustKeepIdempotencyKey, toFriendlyError, type FriendlyError } from '../../utils/errors';
import { createPaymentAttempt } from '../../utils/paymentAttempt';
import { AmountStep, type PaymentDraft } from './AmountStep';
import { ReceiptStep } from './ReceiptStep';
import { ACCOUNT_NUMBER, RecipientStep } from './RecipientStep';
import { ReviewStep } from './ReviewStep';

const STEPS = ['Recipient', 'Amount', 'Review', 'Receipt'] as const;
type Step = (typeof STEPS)[number];

const EMPTY_DRAFT: PaymentDraft = { fromAccountId: '', amount: '', description: '', category: '' };

/** Send money in four visible steps. Nothing is sent before Review is confirmed. */
export function SendPage() {
  const [searchParams] = useSearchParams();

  const { data, loading, error, reload } = useAsync(async () => {
    const [accounts, payees] = await Promise.all([accountsApi.list(), beneficiariesApi.list()]);
    return { accounts: accounts.filter((account) => account.status === 'ACTIVE'), payees };
  });

  const [step, setStep] = useState<Step>('Recipient');
  // "Send again" links pass ?to=. Only something shaped like an account number is accepted.
  const [accountNumber, setAccountNumber] = useState(() => {
    const requested = searchParams.get('to')?.trim() ?? '';
    return ACCOUNT_NUMBER.test(requested) ? requested : '';
  });
  const [recipient, setRecipient] = useState<PayeeLookupResponse | null>(null);
  const [draft, setDraft] = useState<PaymentDraft>(EMPTY_DRAFT);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<FriendlyError | null>(null);
  const [outcomeUnknown, setOutcomeUnknown] = useState(false);
  const [receipt, setReceipt] = useState<TransferResponse | null>(null);

  const attempt = useRef(createPaymentAttempt());
  const headingRef = useRef<HTMLHeadingElement | null>(null);
  const firstRender = useRef(true);

  // Moves keyboard and screen-reader focus to the new step's heading.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    headingRef.current?.focus();
  }, [step]);

  // The paying account cannot be the account being paid.
  const payingAccounts = (data?.accounts ?? []).filter((account) => account.accountNumber !== recipient?.accountNumber);
  const source = payingAccounts.find((account) => account.id === draft.fromAccountId) ?? null;

  const firstPayingId = payingAccounts[0]?.id;

  useEffect(() => {
    if (!source && firstPayingId) {
      setDraft((current) => ({ ...current, fromAccountId: firstPayingId }));
    }
  }, [source, firstPayingId]);

  const confirm = async () => {
    if (!source || !recipient) return;
    const payment = {
      fromAccountId: source.id,
      toAccountNumber: recipient.accountNumber,
      amount: draft.amount,
      currency: source.currency,
      ...(draft.description.trim() ? { description: draft.description.trim() } : {}),
      ...(draft.category ? { category: draft.category } : {}),
    };

    setBusy(true);
    setFailure(null);
    try {
      const transfer = await transfersApi.create(payment, attempt.current.keyFor(payment));
      attempt.current.finish();
      setReceipt(transfer);
      setStep('Receipt');
    } catch (err) {
      const unknown = mustKeepIdempotencyKey(err);
      // A coded refusal is final. Anything else may already have paid, so its key is kept for the retry.
      if (!unknown) attempt.current.finish();
      setOutcomeUnknown(unknown);
      setFailure(toFriendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const sendAnother = () => {
    setStep('Recipient');
    setAccountNumber('');
    setRecipient(null);
    setDraft(EMPTY_DRAFT);
    setReceipt(null);
    setFailure(null);
    void reload();
  };

  if (loading) {
    return (
      <div className="send">
        <Skeleton height={320} />
      </div>
    );
  }

  if (error || !data) {
    return <ErrorState title={error?.title ?? "We couldn't load this"} body={error?.body ?? ''} onRetry={reload} />;
  }

  if (data.accounts.length === 0) {
    return <ErrorState title="No account to send from" body="You need an active account before you can send money." />;
  }

  const current = STEPS.indexOf(step);

  return (
    <div className="send">
      <h1 className="page__title">Send money</h1>

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

      {step === 'Recipient' && (
        <RecipientStep
          accountNumber={accountNumber}
          onAccountNumberChange={setAccountNumber}
          payees={data.payees}
          onContinue={(found) => {
            setRecipient(found);
            setStep('Amount');
          }}
        />
      )}

      {step === 'Amount' && (
        <AmountStep
          accounts={payingAccounts}
          draft={draft}
          onChange={(patch) => setDraft((current) => ({ ...current, ...patch }))}
          onBack={() => setStep('Recipient')}
          onContinue={() => {
            setFailure(null);
            setStep('Review');
          }}
        />
      )}

      {step === 'Review' && source && recipient && (
        <ReviewStep
          source={source}
          recipient={recipient}
          draft={draft}
          busy={busy}
          failure={failure}
          outcomeUnknown={outcomeUnknown}
          onBack={() => setStep('Amount')}
          onConfirm={confirm}
        />
      )}

      {step === 'Receipt' && receipt && recipient && (
        <ReceiptStep transfer={receipt} recipient={recipient} onSendAnother={sendAnother} />
      )}
    </div>
  );
}
