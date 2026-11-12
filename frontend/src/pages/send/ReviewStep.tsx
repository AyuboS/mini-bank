import type { AccountResponse, PayeeLookupResponse } from '../../api/types';
import { Button } from '../../components/ui';
import { CATEGORY_META } from '../../utils/categories';
import type { FriendlyError } from '../../utils/errors';
import { formatMoney, maskAccountNumber } from '../../utils/format';
import { formatMinorUnits, toMinorUnits } from '../../utils/money';
import type { PaymentDraft } from './AmountStep';

interface ReviewStepProps {
  source: AccountResponse;
  recipient: PayeeLookupResponse;
  draft: PaymentDraft;
  busy: boolean;
  failure: FriendlyError | null;
  /** True when the last try got no clear answer, so the payment may already have been made. */
  outcomeUnknown: boolean;
  onBack: () => void;
  onConfirm: () => void;
}

/** Step 3: everything about the payment on one screen, before anything is sent. */
export function ReviewStep({ source, recipient, draft, busy, failure, outcomeUnknown, onBack, onConfirm }: ReviewStepProps) {
  const amountMinor = toMinorUnits(draft.amount);
  const balanceAfter = toMinorUnits(source.balance) - amountMinor;
  const amount = formatMinorUnits(amountMinor, source.currency);

  return (
    <div className="send-step">
      {failure && (
        <div className="send-alert" role="alert">
          <p className="send-alert__title">{failure.title}</p>
          {failure.body && <p>{failure.body}</p>}
          {outcomeUnknown && (
            <p>
              The payment may still have gone through. Trying again here is safe: it will not be paid twice. If you
              change the details first, check Transactions before sending.
            </p>
          )}
        </div>
      )}

      <dl className="send-summary">
        <div>
          <dt>To</dt>
          <dd>
            {recipient.holderName}
            <span className="send-summary__sub">{recipient.accountNumber}</span>
          </dd>
        </div>
        <div>
          <dt>From</dt>
          <dd>
            {source.purpose === 'SAVINGS' ? 'Savings' : 'Spending'} {maskAccountNumber(source.accountNumber)}
          </dd>
        </div>
        {draft.description.trim() && (
          <div>
            <dt>Reference</dt>
            <dd>{draft.description.trim()}</dd>
          </div>
        )}
        {draft.category && (
          <div>
            <dt>Category</dt>
            <dd>{CATEGORY_META[draft.category].label}</dd>
          </div>
        )}
        <div>
          <dt>Amount</dt>
          <dd>{amount}</dd>
        </div>
        {/* Shown even at zero, so nobody has to wonder whether there is a fee. */}
        <div>
          <dt>Fee</dt>
          <dd>{formatMoney(0, source.currency)}</dd>
        </div>
        <div className="send-summary__total">
          <dt>Total</dt>
          <dd>{amount}</dd>
        </div>
        <div>
          <dt>Balance after</dt>
          <dd>{formatMinorUnits(balanceAfter, source.currency)}</dd>
        </div>
      </dl>

      <div className="send-actions">
        <Button variant="secondary" onClick={onBack} disabled={busy}>
          Back
        </Button>
        <Button variant="primary" loading={busy} onClick={onConfirm}>
          {busy ? 'Sending…' : `Send ${amount}`}
        </Button>
      </div>
    </div>
  );
}
