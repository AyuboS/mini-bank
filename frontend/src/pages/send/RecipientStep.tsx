import { useEffect, useState } from 'react';
import { accountsApi } from '../../api/accounts';
import type { BeneficiaryResponse, PayeeLookupResponse } from '../../api/types';
import { Button, Input } from '../../components/ui';
import { maskAccountNumber } from '../../utils/format';

export const ACCOUNT_NUMBER = /^MB-\d{12}$/;

interface RecipientStepProps {
  accountNumber: string;
  onAccountNumberChange: (value: string) => void;
  payees: BeneficiaryResponse[];
  onContinue: (recipient: PayeeLookupResponse) => void;
}

type Lookup =
  | { state: 'idle' | 'checking' | 'unknown' }
  | { state: 'found'; recipient: PayeeLookupResponse };

/** Step 1: who is being paid. Continue is offered only once the account holder's name is known. */
export function RecipientStep({ accountNumber, onAccountNumberChange, payees, onContinue }: RecipientStepProps) {
  const [lookup, setLookup] = useState<Lookup>({ state: 'idle' });
  const candidate = accountNumber.trim();
  const wellFormed = ACCOUNT_NUMBER.test(candidate);

  // Looks the holder up once the number is complete. A late answer for an old number is dropped.
  useEffect(() => {
    if (!wellFormed) {
      setLookup({ state: 'idle' });
      return undefined;
    }

    let cancelled = false;
    setLookup({ state: 'checking' });
    const timer = window.setTimeout(async () => {
      try {
        const recipient = await accountsApi.lookupPayee(candidate);
        if (!cancelled) setLookup({ state: 'found', recipient });
      } catch {
        if (!cancelled) setLookup({ state: 'unknown' });
      }
    }, 300);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [candidate, wellFormed]);

  const formatError = candidate && !wellFormed ? 'Account numbers look like MB- followed by 12 digits.' : undefined;

  return (
    <form
      className="send-step"
      onSubmit={(event) => {
        event.preventDefault();
        if (lookup.state === 'found') onContinue(lookup.recipient);
      }}
    >
      <Input
        label="Account number"
        placeholder="MB-000000000000"
        value={accountNumber}
        onChange={(event) => onAccountNumberChange(event.target.value.toUpperCase())}
        spellCheck={false}
        autoComplete="off"
        {...(formatError ? { error: formatError } : {})}
      />

      <p className="send-lookup" aria-live="polite">
        {lookup.state === 'checking' && 'Checking the account…'}
        {lookup.state === 'unknown' && (
          <span className="send-lookup__problem">No MBank account has that number. Check the digits.</span>
        )}
        {lookup.state === 'found' && (
          <>
            <span className="send-lookup__name">{lookup.recipient.holderName}</span>
            <span>
              {lookup.recipient.bank} · {lookup.recipient.currency} account
            </span>
          </>
        )}
      </p>

      {payees.length > 0 && (
        <section aria-labelledby="send-payees-title">
          <h3 className="send-subhead" id="send-payees-title">
            Saved payees
          </h3>
          <ul className="send-payees">
            {payees.map((payee) => (
              <li key={payee.id}>
                <button
                  type="button"
                  className="send-payee"
                  aria-pressed={payee.accountNumber === candidate}
                  onClick={() => onAccountNumberChange(payee.accountNumber)}
                >
                  <span className="send-payee__name truncate">{payee.nickname}</span>
                  <span className="send-payee__number">{maskAccountNumber(payee.accountNumber)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="send-actions">
        <Button type="submit" variant="primary" disabled={lookup.state !== 'found'}>
          Continue
        </Button>
      </div>
    </form>
  );
}
