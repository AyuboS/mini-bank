import { useEffect, useState } from 'react';
import { cardsApi } from '../../api/cards';
import type { CardResponse, CardSpendingResponse, CurrencyCode } from '../../api/types';
import { Button, Input, Sheet } from '../ui';
import { toFriendlyError } from '../../utils/errors';
import { formatMoney, toNumber } from '../../utils/format';

interface CardLimitsModalProps {
  open: boolean;
  card: CardResponse | null;
  onClose: () => void;
  onSaved: (card: CardResponse) => void;
}

/** Card spending caps, and how much of them is gone. */
export function CardLimitsModal({ open, card, onClose, onSaved }: CardLimitsModalProps) {
  const [daily, setDaily] = useState('');
  const [monthly, setMonthly] = useState('');
  const [spending, setSpending] = useState<CardSpendingResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !card) return;

    setDaily(card.dailyLimit ?? '');
    setMonthly(card.monthlyLimit ?? '');
    setError(null);

    let cancelled = false;
    cardsApi
      .spending(card.id)
      .then((response) => {
        if (!cancelled) setSpending(response);
      })
      .catch(() => {
        // The bars are supporting detail; failing to load them must not stop
        // somebody setting a limit, which is what they opened this for.
        if (!cancelled) setSpending(null);
      });

    return () => {
      cancelled = true;
    };
  }, [open, card]);

  if (!card) return null;

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      // An emptied field means "remove this cap". A record cannot tell that apart
      // from "not mentioned", so the API takes an explicit flag for it.
      const updated = await cardsApi.setLimits(card.id, {
        ...(daily.trim() ? { dailyLimit: daily.trim() } : { clearDailyLimit: true }),
        ...(monthly.trim() ? { monthlyLimit: monthly.trim() } : { clearMonthlyLimit: true }),
      });
      onSaved(updated);
      onClose();
    } catch (err) {
      const friendly = toFriendlyError(err);
      setError(friendly.body ? `${friendly.title} ${friendly.body}` : friendly.title);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Spending limits"
      subtitle={`${card.label} · •••• ${card.lastFour}`}
    >
      {error && (
        <div className="auth-error mb-4" role="alert">
          <span>{error}</span>
        </div>
      )}

      <div className="stack gap-4">
        <div className="stack gap-2">
          <Input
            label="Daily limit"
            inputMode="decimal"
            placeholder="No limit"
            value={daily}
            onChange={(event) => setDaily(event.target.value)}
            hint="Leave empty to remove the cap."
          />
          {spending && (
            <UsageBar
              used={toNumber(spending.spentToday)}
              limit={daily.trim() ? Number(daily) : null}
              currency={spending.currency}
              period="today"
            />
          )}
        </div>

        <div className="stack gap-2">
          <Input
            label="Monthly limit"
            inputMode="decimal"
            placeholder="No limit"
            value={monthly}
            onChange={(event) => setMonthly(event.target.value)}
            hint="Must be at least the daily limit."
          />
          {spending && (
            <UsageBar
              used={toNumber(spending.spentThisMonth)}
              limit={monthly.trim() ? Number(monthly) : null}
              currency={spending.currency}
              period="this month"
            />
          )}
        </div>

        <p className="field-hint">
          Measured against money leaving {card.accountNumber}, the account this card is attached to.
        </p>
      </div>

      <div className="modal-actions">
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button variant="primary" onClick={save} loading={busy}>
          {busy ? 'Saving…' : 'Save limits'}
        </Button>
      </div>
    </Sheet>
  );
}

interface UsageBarProps {
  used: number;
  limit: number | null;
  currency: CurrencyCode;
  period: string;
}

/** A quiet progress reading, which turns amber and then red as the cap approaches. */
function UsageBar({ used, limit, currency, period }: UsageBarProps) {
  if (!limit || !Number.isFinite(limit) || limit <= 0) {
    return (
      <p className="small muted">
        {formatMoney(used, currency)} spent {period} · no limit set
      </p>
    );
  }

  const ratio = Math.min(used / limit, 1);
  const tone = ratio >= 1 ? 'danger' : ratio >= 0.8 ? 'warning' : 'ok';

  return (
    <div className="usage">
      <div className="usage__track" role="presentation">
        <div className={`usage__fill usage__fill--${tone}`} style={{ width: `${ratio * 100}%` }} />
      </div>
      <p className="small muted">
        {formatMoney(used, currency)} / {formatMoney(limit, currency)} used {period}
      </p>
    </div>
  );
}
