import type { AccountResponse } from '../api/types';
import { currencySymbol, formatMoney, maskAccountNumber } from '../utils/format';
import { Surface, StatusBadge } from './ui';

interface AccountCardProps {
  account: AccountResponse;
  onSelect?: (account: AccountResponse) => void;
  /** The first account a customer opens is their main one. */
  primary?: boolean;
}

/**
 * One account, as a card.
 *
 * The currency disc replaced a giant ghosted letter bled into the corner. A
 * filled circle carrying the symbol is legible at a glance and gives a grid of
 * accounts three distinct colours to sort itself by; the ghosted letter was
 * invisible at any contrast that did not also fight the balance.
 */
export function AccountCard({ account, onSelect, primary = false }: AccountCardProps) {
  const frozen = account.status !== 'ACTIVE';

  return (
    <Surface
            interactive
      lift
      className="account-card"
      as={onSelect ? 'button' : 'div'}
      {...(onSelect
        ? { onClick: () => onSelect(account), type: 'button', 'aria-label': `Account ${account.accountNumber}` }
        : {})}
    >
      <div className="account-card__head">
        <div className="row gap-3">
          <span className={`account-card__disc account-card__disc--${account.currency}`} aria-hidden="true">
            {currencySymbol(account.currency)}
          </span>
          <div>
            <div className="account-card__type">{primary ? 'Main account' : 'Account'}</div>
            <div className="account-card__currency">{account.currency}</div>
          </div>
        </div>
        {/* Only an account that cannot transact earns a badge; ACTIVE is the silent default. */}
        {frozen && <StatusBadge status={account.status} />}
      </div>

      <div className="account-card__balance">{formatMoney(account.balance, account.currency)}</div>
      <div className="account-card__number">{maskAccountNumber(account.accountNumber)}</div>
    </Surface>
  );
}
