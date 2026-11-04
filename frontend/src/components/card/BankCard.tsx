import type { CardDesign, CardNetwork, CardResponse, CardStatus, CardType } from '../../api/types';
import { CardChip, ContactlessMark, NetworkMark } from './CardMarks';
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

/**
 * One MBank card, rendered as an object.
 *
 * The component holds no state and runs no effects. An earlier version tracked
 * the pointer at up to 120Hz to tilt the card three degrees towards the cursor
 * and sweep a highlight across it; it was carefully built and it was the exact
 * kind of thing that makes a banking app feel like a demo. What replaced it is
 * a hover lift declared in CSS.
 *
 * Two classes drive the face: the design decides the colour, the material
 * decides the finish. They vary independently — the Standard and the Metal are
 * both dark, and only one of them is brushed.
 */
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
    // A virtual card has no physical finish, so it borrows the plastic sheen
    // rather than being left flat.
    `bank-card--${material === 'virtual' ? 'plastic' : material}`,
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
      <div className="bank-card__sheen" aria-hidden="true" />
      <div className="bank-card__noise" aria-hidden="true" />

      <div className="bank-card__body">
        <div className="bank-card__top">
          <span className="bank-card__brand">MBank</span>
          <div className="bank-card__top-right">
            {showStatus && card.status !== 'ACTIVE' && (
              <span className={`bank-card__status bank-card__status--${card.status.toLowerCase()}`}>
                <span className="bank-card__status-dot" />
                {STATUS_LABEL[card.status]}
              </span>
            )}
            <span className="bank-card__type">{materialLabel(material)}</span>
          </div>
        </div>

        <div className="bank-card__hardware">
          <CardChip className="bank-card__chip" />
          <ContactlessMark className="bank-card__contactless" />
        </div>

        {/*
          The number is presentational: it is already masked by the server and
          carries no information a reader needs announced. The accessible name of
          the card comes from the label and last four digits in the surrounding
          list, where it is meaningful.
        */}
        <div className="bank-card__number" aria-hidden="true">
          {card.maskedNumber}
        </div>

        {/*
          No "CARDHOLDER" and "EXPIRES" captions.

          They were 0.66em — around 8px on a medium card — set in white at 58%,
          which measured between 2.0:1 and 3.5:1 depending on the face and was
          the least legible text in the product. They also said nothing: a name
          is recognisably a name and MM/YY is recognisably an expiry, which is
          why no issuer prints those words either. Removing them fixed the
          contrast and made the face quieter at the same time.
        */}
        <div className="bank-card__bottom">
          <span className="bank-card__name">{card.cardholderName}</span>
          <span className="bank-card__expiry">
            {formatExpiry(card.expirationMonth, card.expirationYear)}
          </span>
          <NetworkMark network={card.network} className="bank-card__network" />
        </div>
      </div>
    </div>
  );
}
