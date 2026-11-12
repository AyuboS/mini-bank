import type { CardDesign, CardType } from '../../api/types';

/** What a card is made of. */
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

/** The material a given card is actually made of. */
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
