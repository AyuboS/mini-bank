import type { CurrencyCode, EntryType } from '../api/types';
import { formatMoney, splitMoney } from '../utils/format';

interface MoneyProps {
  amount: string | number;
  currency: CurrencyCode;
  /** Adds a sign and colours the value by direction. */
  direction?: EntryType;
  muted?: boolean;
}

export function Money({ amount, currency, direction, muted }: MoneyProps) {
  const formatted = formatMoney(amount, currency);

  if (!direction) {
    return <span className={`amount ${muted ? 'amount--muted' : ''}`}>{formatted}</span>;
  }

  const incoming = direction === 'CREDIT';
  return (
    <span className={`amount ${incoming ? 'amount--in' : 'amount--out'} ${muted ? 'amount--muted' : ''}`}>
      {/* A true minus sign, not a hyphen: it aligns with the digits. */}
      {incoming ? '+' : '−'}
      {formatted}
    </span>
  );
}

/** The balance, with its minor units set smaller. */
export function DisplayMoney({ amount, currency }: { amount: string | number; currency: CurrencyCode }) {
  const [major, minor] = splitMoney(amount, currency);
  return (
    <span>
      {major}
      {minor && <span className="balance__minor">{minor}</span>}
    </span>
  );
}
