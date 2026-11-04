import type { CurrencyCode } from '../api/types';

const SYMBOLS: Record<CurrencyCode, string> = { EUR: '€', USD: '$', GBP: '£' };

export function currencySymbol(currency: CurrencyCode): string {
  return SYMBOLS[currency] ?? '';
}

/**
 * Formats an amount for display.
 *
 * The API sends four decimal places because that is the storage scale; people
 * read money in minor units, so display trims to two. This is presentation
 * only — no arithmetic is ever done on these values in the browser.
 */
export function formatMoney(amount: string | number, currency: CurrencyCode): string {
  const value = typeof amount === 'number' ? amount : Number.parseFloat(amount);
  if (Number.isNaN(value)) {
    return `${currencySymbol(currency)}0.00`;
  }
  return `${currencySymbol(currency)}${value.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/** Splits a formatted amount so the minor units can be set smaller. */
export function splitMoney(amount: string | number, currency: CurrencyCode): [string, string] {
  const formatted = formatMoney(amount, currency);
  const separator = formatted.lastIndexOf('.');
  return separator === -1
    ? [formatted, '']
    : [formatted.slice(0, separator), formatted.slice(separator)];
}

export function toNumber(amount: string | number | undefined): number {
  if (amount === undefined) return 0;
  const value = typeof amount === 'number' ? amount : Number.parseFloat(amount);
  return Number.isNaN(value) ? 0 : value;
}

/** "Today", "Yesterday", then a date — the way a statement reads. */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOfDay(now) - startOfDay(date)) / 86_400_000);

  if (days === 0) return `Today, ${date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`;
  if (days === 1) return `Yesterday, ${date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`;
  if (days < 7) return date.toLocaleDateString(undefined, { weekday: 'long' });
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function relativeTime(iso: string): string {
  const elapsed = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  const days = Math.floor(hours / 24);
  if (days < 31) return `${days} day${days === 1 ? '' : 's'} ago`;
  return formatDate(iso);
}

/**
 * MB-482910375512 becomes •••• 5512 — enough to recognise, not enough to reuse.
 *
 * The bank's own funding accounts are named rather than masked: MB-SYSTEM-EUR
 * would otherwise render as the meaningless "•••• -EUR", and there is nothing
 * to conceal about an account every customer's opening balance comes from.
 */
export function maskAccountNumber(accountNumber: string): string {
  if (!accountNumber) return accountNumber;
  if (accountNumber.startsWith('MB-SYSTEM')) return 'MBank';
  if (accountNumber.length < 4) return accountNumber;
  return `•••• ${accountNumber.slice(-4)}`;
}

export function initials(first?: string, last?: string): string {
  const a = first?.trim()?.[0] ?? '';
  const b = last?.trim()?.[0] ?? '';
  return (a + b).toUpperCase() || '?';
}

/** LOGIN_SUCCESS becomes Login success, for audit tables. */
export function humanise(value: string): string {
  const lower = value.toLowerCase().replace(/_/g, ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/**
 * A stable colour slot for a name, 0–5.
 *
 * Transaction and payee avatars are coloured by who they belong to rather than
 * at random, so the same counterparty is the same colour on every screen and a
 * list becomes scannable by colour before it is read. A trivial hash is right
 * here: it only has to be deterministic, not uniform or unguessable.
 */
export function toneFor(seed: string): number {
  let hash = 0;
  for (let index = 0; index < seed.length; index += 1) {
    hash = (hash * 31 + seed.charCodeAt(index)) | 0;
  }
  return Math.abs(hash) % 6;
}

/**
 * The initials to put on an avatar for a description or counterparty.
 *
 * Falls back to a single glyph rather than to an empty circle, because an empty
 * circle in a list of transfers reads as a rendering failure.
 */
export function avatarInitials(label: string): string {
  const words = label.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0]!.slice(0, 2).toUpperCase();
  return (words[0]![0]! + words[1]![0]!).toUpperCase();
}

/**
 * The browser's timezone, for endpoints whose answer depends on where a month
 * begins.
 *
 * A statement month and a budget month are calendar concepts on the customer's
 * own calendar. Computing them in UTC would put a late-evening payment on the
 * 31st into the following month for anyone east of Greenwich, and into the
 * previous one for anyone far enough west.
 */
export function browserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    // Some locked-down environments throw rather than returning a zone.
    return 'UTC';
  }
}

/** `2026-08`, the form the analytics and statement endpoints expect. */
export function toMonthKey(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

/** `2026-08` becomes `August 2026`. */
export function formatMonth(monthKey: string): string {
  const [year, month] = monthKey.split('-').map(Number);
  if (!year || !month) return monthKey;
  return new Date(year, month - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
}

/** `2026-09-01`, the form the scheduling endpoints expect. */
export function toDateKey(date: Date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** A bare date, without a time. Used where an hour would be noise. */
export function formatDay(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

/**
 * A signed amount, for a figure whose direction matters.
 *
 * The sign goes before the currency symbol — "−€48.20", not "€−48.20" — which
 * is how money is written and what stops the minus reading as part of the
 * number rather than as its direction.
 */
export function formatSignedMoney(
  amount: string | number,
  currency: CurrencyCode,
  direction: 'in' | 'out',
): string {
  return `${direction === 'in' ? '+' : '−'}${formatMoney(amount, currency)}`;
}

/**
 * A time-aware greeting.
 *
 * Read from the device clock, so it matches what the customer sees out of their
 * window rather than what a server on another continent thinks time it is.
 */
export function greeting(now: Date = new Date()): string {
  const hour = now.getHours();
  if (hour < 5) return 'Good evening';
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

/** `+37120000000` becomes `+371 2000 0000`, in groups a person can read back. */
export function formatPhone(phone?: string): string {
  if (!phone) return '';
  const digits = phone.replace(/[^\d+]/g, '');
  if (digits.length < 8) return phone;
  const country = digits.slice(0, digits.length - 8);
  const rest = digits.slice(-8);
  return `${country} ${rest.slice(0, 4)} ${rest.slice(4)}`.trim();
}
