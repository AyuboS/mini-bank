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
 * The MBank card range, keyed by the design the server stores. The enum names
 * are older than the colours; the faces live in styles/cards.css.
 */
export const CARD_RANGE: Record<CardDesign, CardRangeEntry> = {
  MIDNIGHT: { name: 'Ink', material: 'plastic', blurb: 'Near-black. The one most people keep.', swatch: '#171612' },
  OCEAN: { name: 'Green', material: 'plastic', blurb: 'The MBank green.', swatch: '#0B6B43' },
  AURORA: { name: 'Clay', material: 'plastic', blurb: 'A warm brown.', swatch: '#6E4B3A' },
  SUNSET: { name: 'Stone', material: 'plastic', blurb: 'Warm grey with dark lettering.', swatch: '#CFC9BC' },
  CRYSTAL: { name: 'Paper', material: 'plastic', blurb: 'Off-white with dark lettering.', swatch: '#FBFAF7' },
  CARBON: { name: 'Metal', material: 'metal', blurb: 'Graphite metal. Heavier than it looks.', swatch: '#45443F' },
  HOLOGRAPHIC: { name: 'Special Edition', material: 'special', blurb: 'Deep green, limited run.', swatch: '#123B2C' },
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
