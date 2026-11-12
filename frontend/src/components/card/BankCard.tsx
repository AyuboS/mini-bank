import type { CardDesign, CardNetwork, CardResponse, CardStatus, CardType } from '../../api/types';
import { materialLabel, materialOf } from './range';

interface BankCardProps {
  card: CardPresentation;
  size?: 'sm' | 'md' | 'lg';
  /** Pointer cursor and a small lift on hover. */
  interactive?: boolean;
  onClick?: () => void;
  className?: string;
  /** Shows the status pill. Off in the picker, where every preview is hypothetical. */
  showStatus?: boolean;
}

/**
 * Everything the face needs.
 *
 * Deliberately not `CardResponse`: the picker in the order flow renders cards
 * that do not exist yet and have no id, account or created date. Taking the
 * smaller shape lets one component serve both without inventing a fake card to
 * preview one.
 */
export interface CardPresentation {
  design: CardDesign;
  network: CardNetwork;
  type: CardType;
  status: CardStatus;
  maskedNumber: string;
  cardholderName: string;
  expirationMonth: number;
  expirationYear: number;
}

/** Narrows a real card to the fields the face uses. */
export function toPresentation(card: CardResponse): CardPresentation {
  return {
    design: card.design,
    network: card.network,
    type: card.type,
    status: card.status,
    maskedNumber: card.maskedNumber,
    cardholderName: card.cardholderName,
    expirationMonth: card.expirationMonth,
    expirationYear: card.expirationYear,
  };
}

const STATUS_LABEL: Record<CardStatus, string> = {
  ACTIVE: 'Active',
  FROZEN: 'Frozen',
  EXPIRED: 'Expired',
  CANCELLED: 'Cancelled',
};

/** MM/YY, which is how an expiry is printed and how every payment form asks for it. */
export function formatExpiry(month: number, year: number): string {
  return `${String(month).padStart(2, '0')}/${String(year % 100).padStart(2, '0')}`;
}

const NETWORK_LABEL: Record<CardNetwork, string> = { VISA: 'Visa', MASTERCARD: 'Mastercard' };

/** One card face: a flat colour per design. Stateless; hover and focus are CSS. */
export function BankCard({
  card,
  size = 'md',
  interactive = false,
  onClick,
  className = '',
  showStatus = true,
}: BankCardProps) {
  const material = materialOf(card.design, card.type);

  const classes = [
    'bank-card',
    `bank-card--${card.design.toLowerCase()}`,
    `bank-card--${size}`,
    interactive ? 'bank-card--interactive' : '',
    card.status !== 'ACTIVE' ? `bank-card--${card.status.toLowerCase()}` : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const interactiveProps = onClick
    ? {
        role: 'button' as const,
        tabIndex: 0,
        onClick,
        onKeyDown: (event: React.KeyboardEvent) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onClick();
          }
        },
      }
    : {};

  return (
    <div className={classes} {...interactiveProps}>
      <div className="bank-card__top">
        <span className="bank-card__brand">MBank</span>
        <span className="bank-card__type">
          {showStatus && card.status !== 'ACTIVE' ? (
            <span className="bank-card__status">{STATUS_LABEL[card.status]}</span>
          ) : (
            materialLabel(material)
          )}
        </span>
      </div>

      {/* Hidden from screen readers: the list around the card names it by label and last four. */}
      <div className="bank-card__number" aria-hidden="true">
        •••• {card.maskedNumber.slice(-4)}
      </div>

      <div className="bank-card__bottom">
        <span className="bank-card__name">{card.cardholderName}</span>
        <span className="bank-card__expiry">{formatExpiry(card.expirationMonth, card.expirationYear)}</span>
        <span className="bank-card__network">{NETWORK_LABEL[card.network] ?? card.network}</span>
      </div>
    </div>
  );
}
