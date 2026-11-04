interface AvatarProps {
  initials: string;
  size?: 'sm' | 'md' | 'lg';
  /** Overrides the derived colour, for a category or a savings goal with its own accent. */
  accent?: string;
  /**
   * A colour slot chosen by the caller, 0–7.
   *
   * Used where several rows must be told apart by something other than their
   * initials — a table of users, for instance, where two people share them.
   */
  tone?: number;
  className?: string;
}

/**
 * The eight accents an avatar can take, in a fixed order.
 *
 * A fixed list rather than a generated hue, because generated colours drift into
 * each other and into the status palette; these eight were chosen to stay
 * distinguishable from one another and to hold white text.
 */
const ACCENTS = [
  'var(--c-teal)',
  'var(--c-blue)',
  'var(--c-violet)',
  'var(--c-magenta)',
  'var(--c-amber)',
  'var(--c-coral)',
  'var(--c-green)',
  'var(--c-lime)',
];

/**
 * Picks a stable accent from a string.
 *
 * The same name must produce the same colour on every render and on every
 * device, so a customer learns to recognise a payee by their colour. A hash of
 * the text does that; anything involving randomness or array position does not.
 */
function accentFor(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    // A small odd multiplier and a 32-bit truncation: enough spread for eight
    // buckets, and identical in every JavaScript engine.
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  // The modulo is always in range, but `noUncheckedIndexedAccess` cannot know
  // that, and a fallback is cheaper than an assertion that could rot.
  return ACCENTS[Math.abs(hash) % ACCENTS.length] ?? ACCENTS[0]!;
}

export function Avatar({ initials, size = 'md', accent, tone, className = '' }: AvatarProps) {
  const colour =
    accent ?? (tone !== undefined ? ACCENTS[Math.abs(tone) % ACCENTS.length]! : accentFor(initials || '?'));
  return (
    <span
      className={`avatar avatar--${size} ${className}`}
      style={{ ['--avatar-accent' as string]: colour }}
      /* Decorative: the name it stands for is always adjacent in the DOM, and
         announcing "AJ" before it would be noise. */
      aria-hidden="true"
    >
      {initials}
    </span>
  );
}
