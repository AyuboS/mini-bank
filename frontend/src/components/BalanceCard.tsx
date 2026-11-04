import { useNavigate } from 'react-router-dom';
import { ArrowUpRight, Landmark, Minus, Receipt, Send, Users, Wallet } from 'lucide-react';
import type { AccountResponse, CurrencyCode, TransactionResponse } from '../api/types';
import { formatMoney, toNumber } from '../utils/format';
import { DisplayMoney } from './Money';
import { Sparkline } from './Sparkline';

interface BalanceCardProps {
  accounts: AccountResponse[];
  currency: CurrencyCode;
  transactions: TransactionResponse[];
}

/**
 * Reconstructs how the balance arrived at where it is now.
 *
 * The API has no balance-history endpoint, and inventing one client-side would
 * be guesswork. What it does have is the settled transactions, so the series is
 * derived by walking backwards from the current balance and undoing each one.
 * That is a real history of this account, not decoration.
 */
function buildSeries(current: number, transactions: TransactionResponse[]): number[] {
  const settled = transactions.filter((txn) => txn.status === 'COMPLETED').slice(0, 24);
  if (settled.length < 2) {
    return [];
  }

  const series: number[] = [current];
  let running = current;
  // Newest first, so undoing each movement steps further into the past.
  for (const txn of settled) {
    running += txn.direction === 'CREDIT' ? -toNumber(txn.amount) : toNumber(txn.amount);
    series.push(running);
  }
  return series.reverse();
}

/**
 * The centrepiece.
 *
 * The one dark object in a light interface, and the only place display type is
 * used at full size — both of which exist to guarantee that the eye lands here
 * first. The actions live on the card rather than in a panel beside it: the
 * money and the things you can do with it are one thought, and splitting them
 * across two boxes is what made the old dashboard read as a report.
 */
export function BalanceCard({ accounts, currency, transactions }: BalanceCardProps) {
  const navigate = useNavigate();

  const inCurrency = accounts.filter((account) => account.currency === currency);
  const total = inCurrency.reduce((sum, account) => sum + toNumber(account.balance), 0);
  const series = buildSeries(total, transactions);

  /*
    Net movement across the visible window.

    Only shown when that window starts from a non-zero balance. If the visible
    history reaches all the way back to the account opening, the "change" is
    simply the entire balance, and reporting that as a recent gain would be
    actively misleading rather than merely useless.
  */
  const windowStart = series.length >= 2 ? series[0]! : 0;
  const change = total - windowStart;
  const showDelta = series.length >= 2 && windowStart > 0;
  const rising = change > 0;

  return (
    <div className="balance">
      <div className="balance__bloom" />
      <div className="balance__bloom balance__bloom--warm" />

      <div className="balance__content">
        <span className="balance__label">
          <Wallet aria-hidden="true" />
          Total balance
        </span>

        <div className="balance__amount">
          <DisplayMoney amount={total} currency={currency} />
        </div>

        {showDelta && (
          <div className={`balance__delta ${rising ? 'balance__delta--up' : 'balance__delta--flat'}`}>
            {rising ? <ArrowUpRight aria-hidden="true" /> : <Minus aria-hidden="true" />}
            <span>
              {rising ? '+' : ''}
              {formatMoney(change, currency)} over the last {series.length - 1} transactions
            </span>
          </div>
        )}

        <div className="balance__accounts">
          {inCurrency.length} {inCurrency.length === 1 ? 'account' : 'accounts'} · {currency}
        </div>
      </div>

      {/* One filled disc among four, so there is never a question about which
          action the screen is actually for. */}
      <div className="balance__actions">
        <button type="button" className="action action--on-hero action--key" onClick={() => navigate('/transfer')}>
          <span className="action__disc">
            <Send aria-hidden="true" />
          </span>
          Send
        </button>
        <button type="button" className="action action--on-hero" onClick={() => navigate('/accounts')}>
          <span className="action__disc">
            <Landmark aria-hidden="true" />
          </span>
          Accounts
        </button>
        <button type="button" className="action action--on-hero" onClick={() => navigate('/beneficiaries')}>
          <span className="action__disc">
            <Users aria-hidden="true" />
          </span>
          Payees
        </button>
        <button type="button" className="action action--on-hero" onClick={() => navigate('/transactions')}>
          <span className="action__disc">
            <Receipt aria-hidden="true" />
          </span>
          Activity
        </button>
      </div>

      {/* The real history of this account, along the foot of the card. */}
      {series.length >= 2 && (
        <div className="balance__chart">
          <Sparkline values={series} height={84} />
        </div>
      )}
    </div>
  );
}
