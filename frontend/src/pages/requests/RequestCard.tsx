import { useState } from 'react';
import { Check, X } from 'lucide-react';
import { moneyRequestsApi } from '../../api/moneyRequests';
import type { AccountResponse, MoneyRequestResponse } from '../../api/types';
import { useToast } from '../../components/ToastProvider';
import { Button, Sheet, Surface, StatusBadge } from '../../components/ui';
import { formatDay, formatMoney, maskAccountNumber, relativeTime } from '../../utils/format';
import { toFriendlyError } from '../../utils/errors';

export function RequestCard({
  request,
  accounts,
  onChanged,
}: {
  request: MoneyRequestResponse;
  accounts: AccountResponse[];
  onChanged: () => void;
}) {
  const toast = useToast();
  const [paying, setPaying] = useState(false);
  const [busy, setBusy] = useState(false);
  const incoming = request.direction === 'INCOMING';
  const pending = request.status === 'PENDING';

  const act = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      await action();
      toast.success(message);
      onChanged();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  /* The account the request names. The server refuses any other, so offering a
     choice the payer cannot use would only produce a rejection. */
  const payerAccount = accounts.find((account) => account.accountNumber === request.payerAccountNumber);

  return (
    <Surface className="request-card">
      <div className="row-between">
        <div className="stack gap-tight">
          <span className="request-card__who">
            {incoming ? request.requesterName : `To ${maskAccountNumber(request.payerAccountNumber)}`}
          </span>
          {request.description && <span className="dim truncate">{request.description}</span>}
        </div>
        <div className="stack gap-1 items-end">
          <span className="amount request-card__amount">{formatMoney(request.amount, request.currency)}</span>
          <StatusBadge status={request.status} />
        </div>
      </div>

      <div className="row-between request-card__foot">
        <span className="dim">
          {pending ? `Expires ${formatDay(request.expiresAt)}` : `${relativeTime(request.settledAt ?? request.createdAt)}`}
        </span>

        {pending && (
          <div className="row gap-2">
            {incoming ? (
              <>
                <Button
                  size="sm"
                  variant="ghost"
                  icon={<X />}
                  disabled={busy}
                  onClick={() => act(() => moneyRequestsApi.decline(request.id), 'Request declined')}
                >
                  Decline
                </Button>
                <Button
                  size="sm"
                  variant="primary"
                  icon={<Check />}
                  disabled={busy || !payerAccount}
                  onClick={() => setPaying(true)}
                >
                  Pay
                </Button>
              </>
            ) : (
              <Button
                size="sm"
                variant="ghost"
                icon={<X />}
                disabled={busy}
                onClick={() => act(() => moneyRequestsApi.cancel(request.id), 'Request withdrawn')}
              >
                Withdraw
              </Button>
            )}
          </div>
        )}
      </div>

      {/*
        Confirmation before money moves. Paying a request is a payment, and it
        gets the same review step a transfer does — the amount, who it goes to,
        and where it comes from, before anything happens.
      */}
      <Sheet
        open={paying}
        onClose={() => setPaying(false)}
        title="Pay this request?"
        subtitle="This creates a transfer from your account."
      >
        <div className="stack-lg">
          <dl className="review">
            <div className="review__row">
              <dt>Amount</dt>
              <dd className="amount">{formatMoney(request.amount, request.currency)}</dd>
            </div>
            <div className="review__row">
              <dt>To</dt>
              <dd>{request.requesterName}</dd>
            </div>
            <div className="review__row">
              <dt>From</dt>
              <dd>{maskAccountNumber(request.payerAccountNumber)}</dd>
            </div>
            {request.description && (
              <div className="review__row">
                <dt>For</dt>
                <dd>{request.description}</dd>
              </div>
            )}
            <div className="review__row review__row--total">
              <dt>Total</dt>
              <dd className="amount">{formatMoney(request.amount, request.currency)}</dd>
            </div>
          </dl>

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setPaying(false)}>
              Not now
            </Button>
            <Button
              variant="primary"
              loading={busy}
              onClick={() =>
                payerAccount &&
                act(() => moneyRequestsApi.pay(request.id, payerAccount.id), 'Payment sent').then(() =>
                  setPaying(false),
                )
              }
            >
              Confirm payment
            </Button>
          </div>
        </div>
      </Sheet>
    </Surface>
  );
}

/* ---------------------------------------------------------------------------- */
