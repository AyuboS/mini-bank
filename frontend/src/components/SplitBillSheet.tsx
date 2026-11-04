import { useMemo, useState } from 'react';
import { Check, Users } from 'lucide-react';
import { moneyRequestsApi } from '../api/moneyRequests';
import { beneficiariesApi } from '../api/beneficiaries';
import type { AccountResponse, BeneficiaryResponse, CurrencyCode } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { Avatar, Button, Input, Select, Sheet, SkeletonRows } from './ui';
import { EmptyState } from './States';
import { avatarInitials, currencySymbol, formatMoney } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

interface SplitBillSheetProps {
  open: boolean;
  onClose: () => void;
  accounts: AccountResponse[];
  /** Fired once every request has been raised, so the caller can refresh its list. */
  onCreated: (count: number) => void;
}

/**
 * How the pennies are handled.
 *
 * An equal split of £120.00 between seven people is £17.142857…, and seven
 * requests of £17.14 collect £119.98. The two pence have to go somewhere, and
 * the only honest place is the person doing the splitting: everybody else is
 * asked for a round share, and the organiser absorbs the remainder rather than
 * being quietly overpaid.
 *
 * Returns minor units throughout, because this is the one calculation in the
 * browser where a floating-point cent would end up in a real request.
 */
export function splitEvenly(totalMinor: number, people: number): { share: number; organiser: number } {
  if (people < 1) return { share: 0, organiser: totalMinor };
  const share = Math.floor(totalMinor / people);
  const remainder = totalMinor - share * (people - 1) - share;
  return { share, organiser: share + remainder };
}

/** Parses "12.34" into 1234 minor units without going through a float. */
function toMinor(input: string): number {
  const cleaned = input.replace(/[^\d.]/g, '');
  const [major = '0', minor = ''] = cleaned.split('.');
  const padded = `${minor}00`.slice(0, 2);
  return Number.parseInt(major || '0', 10) * 100 + Number.parseInt(padded, 10);
}

function fromMinor(minor: number): string {
  return (minor / 100).toFixed(2);
}

/**
 * Splitting a bill.
 *
 * Built on money requests rather than on anything new: one request per person,
 * each for their own share, each of which only that person can settle. Nothing
 * here moves money — the requests are real, they are created server-side, and
 * every one of them still has to be paid by the person it was sent to.
 *
 * The organiser's own share is shown but never requested. Asking yourself for
 * money is not a thing, and a total that included it would not add up.
 */
export function SplitBillSheet({ open, onClose, accounts, onCreated }: SplitBillSheetProps) {
  const [total, setTotal] = useState('');
  const [note, setNote] = useState('');
  const [chosen, setChosen] = useState<string[]>([]);
  const [accountId, setAccountId] = useState(accounts[0]?.id ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const payees = useAsync(() => beneficiariesApi.list(), []);

  const account = accounts.find((entry) => entry.id === accountId) ?? accounts[0];
  const currency: CurrencyCode = account?.currency ?? 'EUR';

  // The organiser counts as a head even though they are never asked, which is
  // what makes "four people" mean four ways rather than five.
  const heads = chosen.length + 1;
  const totalMinor = toMinor(total);
  const { share, organiser } = useMemo(() => splitEvenly(totalMinor, heads), [totalMinor, heads]);

  const toggle = (id: string) =>
    setChosen((current) => (current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id]));

  const reset = () => {
    setTotal('');
    setNote('');
    setChosen([]);
    setError(null);
    setBusy(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  const submit = async () => {
    if (!account) return;
    setBusy(true);
    setError(null);

    const selected = (payees.data ?? []).filter((payee) => chosen.includes(payee.id));

    try {
      /*
        Sequential rather than Promise.all.

        Each of these is a write, and the server rate-limits writes. Firing five
        at once is the reliable way to have the fourth rejected and leave the
        split half-created, with no way for the customer to tell which of their
        friends were asked.
      */
      for (const payee of selected) {
        await moneyRequestsApi.create({
          toAccountId: account.id,
          fromAccountNumber: payee.accountNumber,
          amount: fromMinor(share),
          ...(note.trim() ? { description: note.trim() } : {}),
        });
      }
      onCreated(selected.length);
      reset();
    } catch (err) {
      const friendly = toFriendlyError(err);
      setError(
        `${friendly.title} ${friendly.body ?? ''} Some requests may already have been sent — check "You asked".`.trim(),
      );
    } finally {
      setBusy(false);
    }
  };

  const ready = totalMinor > 0 && chosen.length > 0 && Boolean(account);

  return (
    <Sheet
      open={open}
      onClose={close}
      title="Split a bill"
      subtitle="Everyone gets their own request. Nothing moves until they pay it."
    >
      {error && (
        <div className="auth-error" role="alert">
          <span>{error}</span>
        </div>
      )}

      <Input
        label="Total to split"
        inputMode="decimal"
        placeholder="0.00"
        value={total}
        onChange={(event) => setTotal(event.target.value)}
        icon={<span aria-hidden="true">{currencySymbol(currency)}</span>}
      />

      <Input
        label="What was it for?"
        placeholder="Dinner"
        value={note}
        onChange={(event) => setNote(event.target.value)}
        maxLength={100}
        hint="Optional. Shown to everybody you ask."
      />

      {accounts.length > 1 && (
        <Select label="Paid into" value={accountId} onChange={(event) => setAccountId(event.target.value)}>
          {accounts.map((entry) => (
            <option key={entry.id} value={entry.id}>
              {entry.accountNumber} · {entry.currency}
            </option>
          ))}
        </Select>
      )}

      <div className="stack">
        <span className="label">Who is splitting it</span>
        {payees.loading ? (
          <SkeletonRows rows={3} />
        ) : (payees.data ?? []).length === 0 ? (
          <EmptyState
            title="No payees yet"
            body="Save the people you split bills with, and they will show up here."
            icon={<Users />}
          />
        ) : (
          <div className="split-people">
            {(payees.data ?? []).map((payee: BeneficiaryResponse) => (
              <button
                key={payee.id}
                type="button"
                className={`split-person ${chosen.includes(payee.id) ? 'split-person--on' : ''}`}
                onClick={() => toggle(payee.id)}
                aria-pressed={chosen.includes(payee.id)}
              >
                <Avatar initials={avatarInitials(payee.nickname)} />
                <span className="grow truncate">{payee.nickname}</span>
                {chosen.includes(payee.id) && <Check aria-hidden="true" className="split-person__check" />}
              </button>
            ))}
          </div>
        )}
      </div>

      {totalMinor > 0 && chosen.length > 0 && (
        <dl className="detail-rows">
          <div className="detail-row">
            <dt className="detail-row__label">Split</dt>
            <dd className="detail-row__value">
              {formatMoney(fromMinor(totalMinor), currency)} · {heads} ways
            </dd>
          </div>
          <div className="detail-row">
            <dt className="detail-row__label">Each of them pays</dt>
            <dd className="detail-row__value">{formatMoney(fromMinor(share), currency)}</dd>
          </div>
          <div className="detail-row detail-row--total">
            <dt className="detail-row__label">Your share</dt>
            <dd className="detail-row__value">{formatMoney(fromMinor(organiser), currency)}</dd>
          </div>
        </dl>
      )}

      {/* Said before the button, not after it. */}
      {chosen.length > 0 && (
        <p className="field-hint">
          {chosen.length === 1 ? 'One request' : `${chosen.length} requests`} will be sent, each for{' '}
          {formatMoney(fromMinor(share), currency)}. You keep the odd pennies.
        </p>
      )}

      <div className="modal-actions">
        <Button variant="secondary" onClick={close} disabled={busy}>
          Cancel
        </Button>
        <Button variant="primary" onClick={submit} loading={busy} disabled={!ready}>
          {busy ? 'Sending…' : `Request ${formatMoney(fromMinor(share * chosen.length), currency)}`}
        </Button>
      </div>
    </Sheet>
  );
}
