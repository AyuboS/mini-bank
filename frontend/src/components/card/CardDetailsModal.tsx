import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, Check, Copy, Eye, ShieldAlert } from 'lucide-react';
import { cardsApi } from '../../api/cards';
import type { CardDetailsResponse, CardResponse } from '../../api/types';
import { Button, Sheet } from '../ui';
import { toFriendlyError } from '../../utils/errors';
import { formatExpiry } from './BankCard';

interface CardDetailsModalProps {
  open: boolean;
  card: CardResponse | null;
  onClose: () => void;
}

/** How long revealed details stay on screen before they are cleared. */
const AUTO_HIDE_MS = 60_000;

/** Groups a 16-digit number the way it is printed and read aloud. */
function groupDigits(number: string): string {
  return number.replace(/(.{4})/g, '$1 ').trim();
}

/**
 * The card's sensitive details, behind an explicit reveal.
 *
 * ## Why the details are not fetched when the modal opens
 *
 * Opening a card to rename it should not put its number on a screen in an open
 * office. The reveal is a separate, deliberate action, it is rate limited and
 * audited server-side, and the request is a POST so it cannot be cached or
 * replayed from history.
 *
 * ## Why they are dropped again
 *
 * The values live in this component's state and nowhere else — no store, no
 * cache, no parent. They are cleared when the modal closes and, failing that,
 * after a minute, because the realistic failure is somebody revealing a number
 * and walking away from the desk rather than an attacker reading React state.
 */
export function CardDetailsModal({ open, card, onClose }: CardDetailsModalProps) {
  const [details, setDetails] = useState<CardDetailsResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);
  const timerRef = useRef<number | null>(null);

  const clearDetails = useCallback(() => {
    setDetails(null);
    setError(null);
    setCopied(null);
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  // Nothing survives the modal closing, or the card being switched underneath it.
  useEffect(() => {
    if (!open) clearDetails();
  }, [open, clearDetails]);

  useEffect(() => clearDetails, [card?.id, clearDetails]);

  const reveal = async () => {
    if (!card) return;
    setBusy(true);
    setError(null);
    try {
      const response = await cardsApi.details(card.id);
      setDetails(response);
      timerRef.current = window.setTimeout(clearDetails, AUTO_HIDE_MS);
    } catch (err) {
      const friendly = toFriendlyError(err);
      setError(friendly.body ? `${friendly.title} ${friendly.body}` : friendly.title);
    } finally {
      setBusy(false);
    }
  };

  const copy = async (value: string, field: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(field);
      window.setTimeout(() => setCopied(null), 1600);
    } catch {
      // Clipboard access can be refused outright; the value is on screen to be
      // read either way, so there is nothing useful to report here.
    }
  };

  if (!card) return null;

  return (
    <Sheet open={open} onClose={onClose} title="Card details" subtitle={card.label}>
      {error && (
        <div className="auth-error" role="alert" style={{ marginBottom: 'var(--space-4)' }}>
          <AlertTriangle aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <dl className="detail-list">
        <div>
          <dt>Cardholder</dt>
          <dd>{details?.cardholderName ?? card.cardholderName}</dd>
        </div>

        <div>
          <dt>Card number</dt>
          <dd className="detail-list__secret">
            <span className="mono">
              {details ? groupDigits(details.number) : `•••• •••• •••• ${card.lastFour}`}
            </span>
            {details && (
              <button
                type="button"
                className="copy-btn"
                onClick={() => copy(details.number, 'number')}
                aria-label="Copy card number"
              >
                {copied === 'number' ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
              </button>
            )}
          </dd>
        </div>

        <div>
          <dt>Expires</dt>
          <dd className="mono">{formatExpiry(card.expirationMonth, card.expirationYear)}</dd>
        </div>

        <div>
          <dt>Security code</dt>
          <dd className="detail-list__secret">
            <span className="mono">{details?.verificationValue ?? '•••'}</span>
            {details && (
              <button
                type="button"
                className="copy-btn"
                onClick={() => copy(details.verificationValue, 'cvv')}
                aria-label="Copy security code"
              >
                {copied === 'cvv' ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
              </button>
            )}
          </dd>
        </div>
      </dl>

      {!details ? (
        <>
          <Button
            variant="primary"
            block
            size="lg"
            loading={busy}
            icon={<Eye aria-hidden="true" />}
            onClick={reveal}
            style={{ marginTop: 'var(--space-5)' }}
          >
            {busy ? 'Revealing…' : 'Reveal details'}
          </Button>
          <p className="field-hint" style={{ marginTop: 'var(--space-3)' }}>
            Revealing is recorded against your account, and limited to a few times an hour.
          </p>
        </>
      ) : (
        <>
          {/*
            Said plainly and on the same screen as the numbers. MBank is connected
            to no payment processor; presenting these as usable card credentials
            anywhere would be a lie the interface is responsible for.
          */}
          <div className="simulated-note" role="note">
            <ShieldAlert aria-hidden="true" />
            <span>
              <strong>Simulated details.</strong> MBank has no payment processor. This number
              authorises nothing and will not work anywhere.
            </span>
          </div>

          <Button
            variant="secondary"
            block
            onClick={clearDetails}
            style={{ marginTop: 'var(--space-4)' }}
          >
            Hide details
          </Button>
          <p className="field-hint" style={{ marginTop: 'var(--space-2)' }}>
            Hidden automatically after a minute.
          </p>
        </>
      )}
    </Sheet>
  );
}
