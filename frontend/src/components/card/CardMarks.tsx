/**
 * The small printed things on a card face.
 *
 * All three are SVG rather than glyphs or emoji, for the same reason: they have
 * to sit at an exact optical size next to each other, inherit the skin's ink
 * colour, and stay crisp at 2x. An emoji chip would arrive in whatever colour
 * and shape the platform felt like, which is the single fastest way to make a
 * card look like a mock-up.
 */

interface MarkProps {
  className?: string;
}

/**
 * The EMV chip.
 *
 * Drawn the way a real contact pad is laid out: a rounded gold plate divided
 * into the eight contacts by a cross of etched channels, with a highlight down
 * the upper-left and a shadow along the lower-right so it reads as raised metal
 * rather than a printed rectangle.
 *
 * The gradients are declared with card-scoped ids (`mb-chip-*`). Two cards on
 * the same page would otherwise both define `#gold`, and every chip would
 * silently inherit whichever definition the browser saw last.
 */
export function CardChip({ className }: MarkProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 46 36"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="mb-chip-plate" x1="6" y1="2" x2="40" y2="34" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#F6E6B0" />
          <stop offset="0.28" stopColor="#D9BC72" />
          <stop offset="0.55" stopColor="#B8985180" stopOpacity="1" />
          <stop offset="0.56" stopColor="#C9A85E" />
          <stop offset="0.82" stopColor="#EBD79F" />
          <stop offset="1" stopColor="#A98B49" />
        </linearGradient>
        <linearGradient id="mb-chip-sheen" x1="4" y1="0" x2="30" y2="24" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#FFFFFF" stopOpacity="0.75" />
          <stop offset="0.5" stopColor="#FFFFFF" stopOpacity="0.12" />
          <stop offset="1" stopColor="#FFFFFF" stopOpacity="0" />
        </linearGradient>
      </defs>

      {/* The plate */}
      <rect x="0.75" y="0.75" width="44.5" height="34.5" rx="6" fill="url(#mb-chip-plate)" />

      {/*
        The contacts. Real chips are divided into eight pads; these are the
        channels between them, drawn as strokes in the plate's own shadow colour
        so they look etched into the metal rather than printed on top of it.
      */}
      <g stroke="#8A6E32" strokeOpacity="0.55" strokeWidth="1.1" strokeLinecap="round">
        <path d="M0.75 12h13" />
        <path d="M32.25 12h13" />
        <path d="M0.75 24h13" />
        <path d="M32.25 24h13" />
        <path d="M14 1v34" />
        <path d="M32 1v34" />
        <path d="M14 18h18" />
      </g>

      {/* The inner pad outline, which every contact plate has. */}
      <rect
        x="14"
        y="7.5"
        width="18"
        height="21"
        rx="2.5"
        stroke="#8A6E32"
        strokeOpacity="0.5"
        strokeWidth="1.1"
      />

      {/* Light along the top-left, shadow along the bottom-right: the two things
          that make a flat shape read as a raised one. */}
      <rect x="0.75" y="0.75" width="44.5" height="34.5" rx="6" fill="url(#mb-chip-sheen)" />
      <rect
        x="0.75"
        y="0.75"
        width="44.5"
        height="34.5"
        rx="6"
        stroke="#7A6029"
        strokeOpacity="0.45"
        strokeWidth="1"
      />
    </svg>
  );
}

/**
 * The contactless symbol: four arcs of increasing radius, radiating right.
 *
 * Uses `currentColor`, so it picks up whichever ink the skin sets rather than
 * needing a variant per design.
 */
export function ContactlessMark({ className }: MarkProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <g stroke="currentColor" strokeWidth="2" strokeLinecap="round" fill="none">
        <path d="M6.5 8.6a5 5 0 0 1 0 6.8" opacity="0.95" />
        <path d="M10.6 5.9a9.4 9.4 0 0 1 0 12.2" opacity="0.78" />
        <path d="M14.7 3.2a13.8 13.8 0 0 1 0 17.6" opacity="0.58" />
        <path d="M18.8 0.6a18 18 0 0 1 0 22.8" opacity="0.36" />
      </g>
    </svg>
  );
}

/**
 * The scheme mark.
 *
 * Set as type rather than reproduced as a logo, deliberately: MBank has no
 * relationship with any card scheme, and copying a trademarked mark would imply
 * one. The wordmark is MBank's own lettering, which is honest about what this
 * is — a demo card that names the scheme it imitates.
 */
export function NetworkMark({ network, className }: MarkProps & { network: string }) {
  if (network === 'MASTERCARD') {
    return (
      <svg
        className={className}
        viewBox="0 0 48 30"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        role="img"
        aria-label="Mastercard"
      >
        {/* Two interlocking discs: the scheme's arrangement, in the card's own
            ink rather than the trademarked red and yellow. */}
        <circle cx="18" cy="15" r="11" fill="currentColor" fillOpacity="0.85" />
        <circle cx="30" cy="15" r="11" fill="currentColor" fillOpacity="0.5" />
      </svg>
    );
  }

  return (
    <svg
      className={className}
      viewBox="0 0 62 22"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label="Visa"
    >
      <text
        x="0"
        y="17"
        fill="currentColor"
        fontFamily="var(--font-sans)"
        fontSize="19"
        fontWeight="700"
        fontStyle="italic"
        letterSpacing="1.5"
      >
        VISA
      </text>
    </svg>
  );
}
