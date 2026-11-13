import type { CurrencyCode } from '../api/types';
import { formatMoney } from './format';

/** Money arithmetic for the browser. */

const AMOUNT = /^(-?)(\d+)(?:\.(\d+))?$/;

/** "4250.5000" becomes 425050. A third decimal of 5 or more rounds away from zero. */
export function toMinorUnits(amount: string | number | undefined): number {
  if (amount === undefined) return 0;
  const match = AMOUNT.exec(String(amount).trim());
  if (!match) return 0;

  const [, sign, whole = '0', fraction = ''] = match;
  const cents = Number(fraction.padEnd(2, '0').slice(0, 2));
  const roundUp = Number(fraction[2] ?? '0') >= 5 ? 1 : 0;
  const minor = Number(whole) * 100 + cents + roundUp;
  return sign === '-' ? -minor : minor;
}

/** 425050 becomes "4250.50", the form the API and the formatter accept. */
export function fromMinorUnits(minor: number): string {
  const sign = minor < 0 ? '-' : '';
  const absolute = Math.abs(minor);
  return `${sign}${Math.floor(absolute / 100)}.${String(absolute % 100).padStart(2, '0')}`;
}

/** Adds amounts exactly. */
export function sumMinorUnits(amounts: (string | number | undefined)[]): number {
  return amounts.reduce<number>((total, amount) => total + toMinorUnits(amount), 0);
}

export function formatMinorUnits(minor: number, currency: CurrencyCode): string {
  const formatted = formatMoney(fromMinorUnits(Math.abs(minor)), currency);
  return minor < 0 ? `−${formatted}` : formatted;
}

/** What a person typed into an amount field: digits and one separator, two decimals at most. "12,5" becomes "12.5". */
export function cleanAmountInput(raw: string): string {
  const cleaned = raw.replace(',', '.').replace(/[^\d.]/g, '');
  const [whole = '', ...rest] = cleaned.split('.');
  return rest.length > 0 ? `${whole}.${rest.join('').slice(0, 2)}` : whole;
}
