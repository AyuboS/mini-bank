import type { CardResponse } from '../../api/types';
import { BankCard, toPresentation } from './BankCard';

interface CardStackProps {
  cards: CardResponse[];
  onSelect?: () => void;
}

/**
 * The wallet, compressed: one readable card with the others peeking out behind.
 *
 * Used where the dashboard needs to say "you have cards" in the space of one.
 * The layers behind are decoration — flat sheets tinted to match their skin, not
 * rendered cards — and are hidden from assistive technology, because announcing
 * two cards whose every detail is obscured is noise rather than information.
 */
export function CardStack({ cards, onSelect }: CardStackProps) {
  const [front, ...rest] = cards;
  if (!front) return null;

  return (
    <div className="card-stack">
      {/* Layer 1 sits directly behind the front card, layer 2 behind that. Depth
          comes from z-index rather than DOM order, so this reads in wallet order. */}
      {rest.slice(0, 2).map((card, index) => (
        <div
          key={card.id}
          className={`card-stack__layer card-stack__layer--${index + 1} bank-card--${card.design.toLowerCase()}`}
          aria-hidden="true"
        />
      ))}

      <div className="card-stack__front">
        <BankCard
          card={toPresentation(front)}
          size="md"
          interactive={Boolean(onSelect)}
          {...(onSelect ? { onClick: onSelect } : {})}
        />
      </div>
    </div>
  );
}
