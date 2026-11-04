import type { CardDesign, CardType } from '../../api/types';

/**
 * What a card is made of.
 *
 * Derived rather than stored. The server's `design` enum already encodes the
 * material one-to-one — CARBON is the metal card, HOLOGRAPHIC is the seasonal
 * one, everything else is polycarbonate — so a `material` column would be a
 * second source of truth for a fact the first one already carries, and the two
 * would eventually disagree. Deriving it here keeps the presentation honest and
 * needs no migration.
 */
export type CardMaterial = 'plastic' | 'metal' | 'special' | 'virtual';

export interface CardRangeEntry {
  /** What the card is called in the range. */
  name: string;
  material: CardMaterial;
  /** One line for the order flow and the picker. */
  blurb: string;
  /** The face's dominant colour, for a swatch. */
  swatch: string;
}

/**
 * The MBank card range.
 *
 * Keyed by the design the server stores, so the enum is never re-spelled across
 * the wire. The names, palette and material treatments are MBank's own; the
 * faces themselves live in styles/cards.css.
 */
export const CARD_RANGE: Record<CardDesign, CardRangeEntry> = {
  MIDNIGHT: {
    name: 'Standard',
    material: 'plastic',
    blurb: 'Near-black polycarbonate. The one most people keep.',
    swatch: '#16181F',
  },
  OCEAN: {
    name: 'Ocean',
    material: 'plastic',
    blurb: 'Deep blue, matte finish.',
    swatch: '#12328F',
  },
  AURORA: {
    name: 'Violet',
    material: 'plastic',
    blurb: 'Violet, matte finish.',
    swatch: '#4E2BAE',
  },
  SUNSET: {
    name: 'Coral',
    material: 'plastic',
    blurb: 'Warm red, matte finish.',
    swatch: '#D33B22',
  },
  CRYSTAL: {
    name: 'Lime',
    material: 'plastic',
    blurb: 'The bright one. Dark lettering, hard to lose.',
    swatch: '#A8DC22',
  },
  CARBON: {
    name: 'Metal',
    material: 'metal',
    blurb: 'Brushed anodised graphite. Heavier than it looks.',
    swatch: '#464B55',
  },
  HOLOGRAPHIC: {
    name: 'Special Edition',
    material: 'special',
    blurb: 'A seasonal two-tone face. Limited run.',
    swatch: '#6B3FE4',
  },
};

/** The order the range is offered in: the default first, the unusual ones last. */
export const CARD_RANGE_ORDER: CardDesign[] = [
  'MIDNIGHT',
  'OCEAN',
  'AURORA',
  'SUNSET',
  'CRYSTAL',
  'CARBON',
  'HOLOGRAPHIC',
];

/**
 * The material a given card is actually made of.
 *
 * A virtual card has no material — it is a number, not an object — so the type
 * wins over the design. Saying "Metal" on a card that will never be printed
 * would be a small lie in the one part of the interface that has to be exact.
 */
export function materialOf(design: CardDesign, type: CardType): CardMaterial {
  if (type === 'VIRTUAL') return 'virtual';
  return CARD_RANGE[design].material;
}

const MATERIAL_LABEL: Record<CardMaterial, string> = {
  plastic: 'Plastic',
  metal: 'Metal',
  special: 'Special Edition',
  virtual: 'Virtual',
};

export function materialLabel(material: CardMaterial): string {
  return MATERIAL_LABEL[material];
}

/** "MBank Ocean" — the full product name, for a heading or an order summary. */
export function cardName(design: CardDesign): string {
  return `MBank ${CARD_RANGE[design].name}`;
}
