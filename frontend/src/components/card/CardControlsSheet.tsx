import { useState } from 'react';
import { Banknote, Globe, Nfc, ShieldAlert, ShoppingCart } from 'lucide-react';
import type { ReactNode } from 'react';
import { cardsApi } from '../../api/cards';
import type { CardControlsBody } from '../../api/cards';
import type { CardResponse } from '../../api/types';
import { Sheet, Surface } from '../ui';
import { useToast } from '../ToastProvider';
import { toFriendlyError } from '../../utils/errors';

interface CardControlsSheetProps {
  open: boolean;
  card: CardResponse;
  onClose: () => void;
  onUpdated: (card: CardResponse) => void;
}

interface Control {
  key: keyof CardControlsBody;
  label: string;
  body: string;
  icon: ReactNode;
}

/** The five channels a card can be used through. */
const CONTROLS: Control[] = [
  {
    key: 'onlinePayments',
    label: 'Online payments',
    body: 'Paying on websites and in apps, where the card is not present.',
    icon: <ShoppingCart />,
  },
  {
    key: 'contactless',
    label: 'Contactless',
    body: 'Tapping to pay at a terminal.',
    icon: <Nfc />,
  },
  {
    key: 'atmWithdrawals',
    label: 'Cash withdrawals',
    body: 'Taking cash out at an ATM.',
    icon: <Banknote />,
  },
  {
    key: 'international',
    label: 'International use',
    body: 'Using the card outside its home country.',
    icon: <Globe />,
  },
  {
    key: 'magneticStripe',
    label: 'Magnetic stripe',
    body: 'The fallback swipe. It has no cryptographic protection, so it starts off.',
    icon: <ShieldAlert />,
  },
];

/** Card controls. */
export function CardControlsSheet({ open, card, onClose, onUpdated }: CardControlsSheetProps) {
  const toast = useToast();
  const [pending, setPending] = useState<keyof CardControlsBody | null>(null);

  const toggle = async (key: keyof CardControlsBody, next: boolean) => {
    setPending(key);
    try {
      /*
        Only the switch that changed is sent. Sending all five would mean this
        client restating its opinion about four settings the customer did not
        touch — and overwriting whatever another device had just changed.
      */
      const updated = await cardsApi.updateControls(card.id, { [key]: next });
      onUpdated(updated);
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setPending(null);
    }
  };

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Card controls"
      subtitle={`${card.label} · •••• ${card.lastFour}`}
    >
      <div className="stack">
        {CONTROLS.map((control) => {
          const enabled = Boolean(card.controls[control.key as keyof typeof card.controls]);
          return (
            <Surface tone="chrome" className="control-row" key={control.key}>
              <span className="control-row__icon" aria-hidden="true">
                {control.icon}
              </span>

              <span className="grow stack gap-tight">
                <span className="control-row__label">{control.label}</span>
                <span className="dim control-row__body">{control.body}</span>
              </span>

              {/*
                A real checkbox, styled. A div with a click handler would look
                identical and be invisible to a screen reader, unreachable by
                keyboard, and impossible to operate by voice.
              */}
              <label className="switch">
                <input
                  type="checkbox"
                  checked={enabled}
                  disabled={pending !== null || card.status !== 'ACTIVE'}
                  onChange={(event) => toggle(control.key, event.target.checked)}
                  aria-label={control.label}
                />
                <span className="switch__track" aria-hidden="true">
                  <span className="switch__thumb" />
                </span>
              </label>
            </Surface>
          );
        })}
      </div>

      {card.status !== 'ACTIVE' && (
        <p className="dim mt-4 text-small">
          Controls cannot be changed while the card is {card.status.toLowerCase()}.
        </p>
      )}
    </Sheet>
  );
}
