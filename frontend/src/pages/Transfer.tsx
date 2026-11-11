import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowDown, Check, Send, Wallet } from 'lucide-react';
import { accountsApi } from '../api/accounts';
import { beneficiariesApi } from '../api/beneficiaries';
import { newIdempotencyKey, transfersApi } from '../api/transfers';
import type {
  AccountResponse,
  BeneficiaryResponse,
  PayeeLookupResponse,
  TransactionCategory,
  TransferResponse,
} from '../api/types';
import { TRANSACTION_CATEGORIES } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import {
  Avatar,
  Button,
  Surface,
  Input,
  Select,
  Skeleton,
} from '../components/ui';
import { ErrorState } from '../components/States';
import { RiskGauge } from '../components/RiskGauge';
import { CATEGORY_META } from '../utils/categories';
import { avatarInitials, currencySymbol, formatMoney, maskAccountNumber, toNumber } from '../utils/format';
import { mustKeepIdempotencyKey, toFriendlyError } from '../utils/errors';
import type { FriendlyError } from '../utils/errors';

type Phase = 'compose' | 'review' | 'done';

interface TransferData {
  accounts: AccountResponse[];
  payees: BeneficiaryResponse[];
}

export function TransferPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const { data, loading, error, reload } = useAsync<TransferData>(async () => {
    const [accounts, payees] = await Promise.all([accountsApi.list(), beneficiariesApi.list()]);
    return { accounts, payees };
  });

  const [phase, setPhase] = useState<Phase>('compose');
  const [fromAccountId, setFromAccountId] = useState('');
  /*
    A payee can be handed in by the "send again" links on the dashboard. Only
    something shaped like an account number is accepted: the field is echoed
    back into the interface, and a query string is not a trusted source.
  */
  const [toAccountNumber, setToAccountNumber] = useState(() => {
    const requested = searchParams.get('to')?.trim() ?? '';
    return /^MB-\d{12}$/.test(requested) ? requested : '';
  });
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<TransactionCategory | ''>('');
  /**
   * Confirmation of payee.
   *
   * Resolved as the account number is typed, so a mistyped digit is caught while
   * it still costs nothing. `null` means "not looked up or not found"; the field
   * says which, because "we could not find that account" and "we have not
   * checked yet" call for very different reactions from the payer.
   */
  const [resolvedPayee, setResolvedPayee] = useState<PayeeLookupResponse | null>(null);
  const [resolving, setResolving] = useState(false);
  const [unknownPayee, setUnknownPayee] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<FriendlyError | null>(null);
  const [result, setResult] = useState<TransferResponse | null>(null);

  // One key per payment attempt, reused on retry so the server cannot pay twice.
  const idempotencyKey = useRef(newIdempotencyKey());

  // Preselect the first account that can actually send money.
  useEffect(() => {
    if (data && !fromAccountId) {
      const usable = data.accounts.find((account) => account.status === 'ACTIVE');
      if (usable) setFromAccountId(usable.id);
    }
  }, [data, fromAccountId]);

  /*
    Looks the payee up once the number is complete, debounced so that typing
    twelve digits does not make twelve requests. Aborted on change, so a slow
    answer for an old number cannot overwrite a fresh one.
  */
  useEffect(() => {
    const candidate = toAccountNumber.trim();
    if (!/^MB-\d{12}$/.test(candidate)) {
      setResolvedPayee(null);
      setUnknownPayee(false);
      return undefined;
    }

    let cancelled = false;
    setResolving(true);
    setUnknownPayee(false);

    const timer = window.setTimeout(async () => {
      try {
        const found = await accountsApi.lookupPayee(candidate);
        if (!cancelled) setResolvedPayee(found);
      } catch {
        if (!cancelled) {
          setResolvedPayee(null);
          setUnknownPayee(true);
        }
      } finally {
        if (!cancelled) setResolving(false);
      }
    }, 300);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [toAccountNumber]);

  const source = useMemo(
    () => data?.accounts.find((account) => account.id === fromAccountId) ?? null,
    [data, fromAccountId],
  );

  const payee = useMemo(
    () => data?.payees.find((p) => p.accountNumber === toAccountNumber) ?? null,
    [data, toAccountNumber],
  );

  const numericAmount = toNumber(amount);
  const available = source ? toNumber(source.balance) : 0;
  const overBalance = numericAmount > available;

  // Client-side checks are for immediacy only. The server re-validates all of
  // this, and its answer is the one that counts.
  const canReview =
    Boolean(source) &&
    /^MB-\d{12}$/.test(toAccountNumber.trim()) &&
    numericAmount > 0 &&
    !overBalance &&
    toAccountNumber.trim() !== source?.accountNumber;

  const submit = async () => {
    if (!source) return;
    setBusy(true);
    setFailure(null);
    try {
      const transfer = await transfersApi.create(
        {
          fromAccountId: source.id,
          toAccountNumber: toAccountNumber.trim(),
          amount,
          currency: source.currency,
          ...(description.trim() ? { description: description.trim() } : {}),
          ...(category ? { category } : {}),
        },
        idempotencyKey.current,
      );
      setResult(transfer);
      setPhase('done');
      // This payment is settled; the next one is a new attempt with a new key.
      idempotencyKey.current = newIdempotencyKey();
    } catch (err) {
      const friendly = toFriendlyError(err);
      setFailure(friendly);
      setPhase('compose');
      // A coded refusal is final. Anything else may already have paid.
      if (!mustKeepIdempotencyKey(err)) {
        idempotencyKey.current = newIdempotencyKey();
      }
    } finally {
      setBusy(false);
    }
  };

  const startAnother = () => {
    setPhase('compose');
    setResult(null);
    setAmount('');
    setDescription('');
    setCategory('');
    setToAccountNumber('');
    setResolvedPayee(null);
    setFailure(null);
    void reload();
  };

  if (loading) {
    return (
      <div className="transfer-shell">
        <Skeleton height={520} />
      </div>
    );
  }

  if (error || !data) {
    return <ErrorState body={error?.body ?? 'We could not load your accounts.'} onRetry={reload} />;
  }

  if (data.accounts.length === 0) {
    return <ErrorState title="No accounts available" body="You need an account before you can send money." />;
  }

  /* ---------------------------------------------------------------- done -- */
  if (phase === 'done' && result) {
    const held = result.status === 'PENDING';
    return (
      <div className="transfer-shell">
        <Surface tone="raised" className="transfer-panel" style={{ position: 'relative', overflow: 'hidden' }}>
          {/* A soft wave of light crossing the panel as it lands. */}
          <span className="success__wave" aria-hidden="true" />

          <div className="success">
            <div className="success__mark">
              {held ? (
                <Wallet aria-hidden="true" style={{ color: 'var(--warning)' }} />
              ) : (
                /* The tick draws itself from a single stroke. */
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
                  <path d="M4 12.5 9.5 18 20 7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
            </div>

            <p className="success__title">{held ? 'Held for review' : 'Transfer complete'}</p>
            <p className="success__amount">{formatMoney(result.amount, result.currency)}</p>
            <p className="success__ref">{result.reference}</p>

            {held && (
              <p className="field-hint" style={{ marginTop: 'var(--space-4)' }}>
                No money has moved yet. An administrator needs to approve this transfer.
              </p>
            )}
          </div>

          {held && result.risk && (
            <>
              <hr className="divider" />
              <div className="risk">
                <RiskGauge score={result.risk.riskScore} decision={result.risk.decision} size={92} />
                <div className="risk__reasons">
                  <span className="label">Why</span>
                  {result.risk.reasons.map((reason) => (
                    <span className="risk__reason" key={reason}>
                      {reason}
                    </span>
                  ))}
                </div>
              </div>
            </>
          )}

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => navigate('/transactions')}>
              View activity
            </Button>
            <Button variant="primary" onClick={startAnother}>
              Send another
            </Button>
          </div>
        </Surface>
      </div>
    );
  }

  /* -------------------------------------------------------------- review -- */
  if (phase === 'review' && source) {
    return (
      <div className="transfer-shell">
        <Surface tone="raised" className="transfer-panel">
          <div className="transfer-head">
            <p className="label">Review transfer</p>
            <p className="transfer-head__sub" style={{ marginTop: 10 }}>
              You are sending
            </p>
            <div className="review-amount__value" style={{ marginTop: 6 }}>
              {formatMoney(amount, source.currency)}
            </div>
            <p className="review-amount__to">
              to {payee?.nickname ?? 'account'} · {maskAccountNumber(toAccountNumber)}
            </p>
          </div>

          <hr className="divider" />

          <div className="detail-rows">
            <div className="detail-row">
              <span className="detail-row__label">From</span>
              <span className="detail-row__value">
                {source.currency} · {maskAccountNumber(source.accountNumber)}
              </span>
            </div>
            <div className="detail-row">
              <span className="detail-row__label">To</span>
              <span className="detail-row__value mono">{toAccountNumber}</span>
            </div>
            {(resolvedPayee?.holderName || payee?.holderName) && (
              <div className="detail-row">
                <span className="detail-row__label">Account holder</span>
                <span className="detail-row__value">
                  {resolvedPayee?.holderName ?? payee?.holderName}
                </span>
              </div>
            )}
            <div className="detail-row">
              <span className="detail-row__label">Description</span>
              <span className="detail-row__value">{description.trim() || '—'}</span>
            </div>
            {category && (
              <div className="detail-row">
                <span className="detail-row__label">Category</span>
                <span className="detail-row__value">{CATEGORY_META[category].label}</span>
              </div>
            )}
            {/*
              Stated even though it is zero. A fee the customer only discovers
              afterwards is the thing this line exists to make impossible, and
              showing it only when non-zero would train people not to look.
            */}
            <div className="detail-row">
              <span className="detail-row__label">Fee</span>
              <span className="detail-row__value">{formatMoney(0, source.currency)}</span>
            </div>
            <div className="detail-row detail-row--total">
              <span className="detail-row__label">Total</span>
              <span className="detail-row__value amount">{formatMoney(amount, source.currency)}</span>
            </div>
            <div className="detail-row">
              <span className="detail-row__label">Balance after</span>
              <span className="detail-row__value">
                {formatMoney(available - numericAmount, source.currency)}
              </span>
            </div>
          </div>

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setPhase('compose')} disabled={busy}>
              Back
            </Button>
            <Button
              variant="primary"
              loading={busy}
              onClick={submit}
              icon={<Check aria-hidden="true" />}
            >
              {busy ? 'Sending…' : 'Confirm'}
            </Button>
          </div>
        </Surface>
      </div>
    );
  }

  /* ------------------------------------------------------------- compose -- */
  return (
    <div className="transfer-shell">
      <Surface tone="raised" className="transfer-panel">
        <div className="transfer-head">
          <h1 className="transfer-head__title">Send money</h1>
          <p className="transfer-head__sub">Transfers between MBank accounts settle instantly</p>
        </div>

        {failure && (
          <Surface style={{ padding: 14, marginBottom: 'var(--space-5)' }} role="alert">
            <div style={{ color: 'var(--danger)', fontWeight: 550, fontSize: 'var(--text-small)' }}>
              {failure.title}
            </div>
            {failure.body && (
              <div className="small dim" style={{ marginTop: 4 }}>
                {failure.body}
              </div>
            )}
          </Surface>
        )}

        <Select
          label="From"
          value={fromAccountId}
          onChange={(event) => setFromAccountId(event.target.value)}
        >
          {data.accounts.map((account) => (
            <option key={account.id} value={account.id} disabled={account.status !== 'ACTIVE'}>
              {account.currency} · {maskAccountNumber(account.accountNumber)} ·{' '}
              {formatMoney(account.balance, account.currency)}
              {account.status !== 'ACTIVE' ? ` (${account.status})` : ''}
            </option>
          ))}
        </Select>

        <div className="transfer-arrow" aria-hidden="true">
          <ArrowDown />
        </div>

        <Input
          label="To account number"
          placeholder="MB-000000000000"
          value={toAccountNumber}
          onChange={(event) => setToAccountNumber(event.target.value.toUpperCase())}
          spellCheck={false}
          autoComplete="off"
          {...(toAccountNumber && !/^MB-\d{12}$/.test(toAccountNumber.trim())
            ? { error: 'Account numbers look like MB- followed by 12 digits' }
            : {})}
        />

        {/*
          What the payer needs in order to catch their own mistake: who this is,
          which bank, and the last four digits they can check against whatever
          they were given.
        */}
        {(resolving || resolvedPayee || unknownPayee) && (
          <div className={`payee-confirm ${unknownPayee ? 'payee-confirm--unknown' : ''}`} aria-live="polite">
            {resolving ? (
              <span className="dim">Checking…</span>
            ) : unknownPayee ? (
              <span>No MBank account with that number. Check the digits.</span>
            ) : (
              resolvedPayee && (
                <>
                  <Avatar initials={avatarInitials(resolvedPayee.holderName)} size="sm" />
                  <span className="stack" style={{ gap: 0 }}>
                    <span className="payee-confirm__name">{resolvedPayee.holderName}</span>
                    <span className="dim payee-confirm__meta">
                      {resolvedPayee.bank} · {maskAccountNumber(resolvedPayee.accountNumber)}
                    </span>
                  </span>
                </>
              )
            )}
          </div>
        )}

        {data.payees.length > 0 && (
          <div className="payee-chips">
            {data.payees.map((p) => (
              <button
                type="button"
                key={p.id}
                className={`chip ${p.accountNumber === toAccountNumber ? 'chip--active' : ''}`}
                onClick={() => setToAccountNumber(p.accountNumber)}
              >
                {p.nickname}
              </button>
            ))}
          </div>
        )}

        <div style={{ marginTop: 'var(--space-5)' }}>
          <span className="field-label">Amount</span>
          <div className="amount-field" style={{ marginTop: 8 }}>
            <span className="amount-field__symbol" aria-hidden="true">
              {currencySymbol(source?.currency ?? 'EUR')}
            </span>
            {/*
              type="text" with inputMode="decimal", not type="number".

              A number input still shows spinner arrows on desktop, changes value
              when the scroll wheel passes over it, and accepts "e", "+" and "-"
              because those are valid in a number — none of which anybody wants
              on a field that decides how much money leaves an account. The
              inputMode is what actually summons the numeric keypad on a phone;
              the type never did.
            */}
            <input
              type="text"
              inputMode="decimal"
              placeholder="0.00"
              value={amount}
              onChange={(event) => {
                // Digits and at most one separator. Commas become dots, because
                // half of Europe types the decimal comma and the API takes a dot.
                const cleaned = event.target.value.replace(',', '.').replace(/[^\d.]/g, '');
                const [whole, ...rest] = cleaned.split('.');
                setAmount(rest.length > 0 ? `${whole}.${rest.join('').slice(0, 2)}` : (whole ?? ''));
              }}
              enterKeyHint="done"
              autoComplete="off"
              aria-label={`Amount in ${source?.currency ?? 'EUR'}`}
            />
          </div>
          <p className="amount-available">
            {overBalance ? (
              <span style={{ color: 'var(--danger)' }}>
                More than the {formatMoney(available, source?.currency ?? 'EUR')} available
              </span>
            ) : (
              <>{formatMoney(available, source?.currency ?? 'EUR')} available</>
            )}
          </p>
        </div>

        <Input
          label="Reference (optional)"
          placeholder="Rent"
          maxLength={140}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          style={{ marginTop: 'var(--space-4)' }}
        />

        <Select
          label="Category (optional)"
          value={category}
          onChange={(event) => setCategory(event.target.value as TransactionCategory | '')}
          hint="Leave it and MBank works one out from the reference."
          style={{ marginTop: 'var(--space-4)' }}
        >
          <option value="">Work it out for me</option>
          {TRANSACTION_CATEGORIES.map((option) => (
            <option key={option} value={option}>
              {CATEGORY_META[option].label}
            </option>
          ))}
        </Select>

        {/*
          Sticky on a phone, ordinary on a desktop.

          One-handed use means the primary action has to be within thumb reach
          at the bottom, and it has to stay above both the home indicator and
          the keyboard — see .transfer-submit in pages.css for how.
        */}
        <div className="transfer-submit">
          <Button
            variant="primary"
            size="lg"
            block
            disabled={!canReview}
            onClick={() => setPhase('review')}
            icon={<Send aria-hidden="true" />}
          >
            Review transfer
          </Button>
        </div>
      </Surface>
    </div>
  );
}
