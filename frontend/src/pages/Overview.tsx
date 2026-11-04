import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowLeftRight,
  ArrowRight,
  CalendarClock,
  ChartPie,
  Check,
  HandCoins,
  MoveDown,
  MoveUp,
  PiggyBank,
  Plus,
  SlidersHorizontal,
  Split,
  TrendingDown,
  TrendingUp,
  Users,
  Wallet,
} from 'lucide-react';
import { accountsApi } from '../api/accounts';
import { cardsApi } from '../api/cards';
import { transfersApi } from '../api/transfers';
import { analyticsApi } from '../api/analytics';
import { savingsApi } from '../api/savings';
import { scheduledApi } from '../api/scheduled';
import { beneficiariesApi } from '../api/beneficiaries';
import type { CurrencyCode, TransactionCategory, TransactionResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { useCountUp } from '../hooks/useCountUp';
import { useHomeLayout, HOME_WIDGETS, WIDGET_META, type HomeWidget } from '../hooks/useHomeLayout';
import { Avatar, Button, Progress, Sheet, Skeleton, SkeletonRows, Surface } from '../components/ui';
import { BankCard, toPresentation } from '../components/card/BankCard';
import { TransactionRow } from '../components/TransactionRow';
import { TransactionDetail } from '../components/TransactionDetail';
import { Sparkline } from '../components/Sparkline';
import { EmptyState, ErrorState } from '../components/States';
import { categoryMeta } from '../utils/categories';
import { avatarInitials, formatMoney, greeting, splitMoney, toNumber } from '../utils/format';

/** How many days of the month the balance trend looks back over. */
const TREND_DAYS = 14;

/**
 * The dashboard.
 *
 * Answers, in the order somebody actually asks: how much have I got, what can I
 * do with it, and what has been happening. The first two are fixed — everybody
 * gets a balance and the four actions — and everything below them is the
 * customer's own arrangement of blocks.
 */
export function OverviewPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [selected, setSelected] = useState<TransactionResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const { layout, toggle, move, reset } = useHomeLayout();

  const accounts = useAsync(() => accountsApi.list(), []);
  const summary = useAsync(() => analyticsApi.summary(), []);

  /**
   * The headline balance: spending accounts only.
   *
   * Savings are excluded and shown separately. Money earmarked for a goal is not
   * money available to spend, and rolling it into one figure is how somebody
   * books a holiday with their rent.
   *
   * Only the primary currency is summed. Adding a USD balance to a EUR one
   * requires a rate, and this product has no live FX — so the figure is the EUR
   * position, and the other accounts are listed individually.
   */
  const { spendable, saved, currency, hasOtherCurrencies } = useMemo(() => {
    const list = accounts.data ?? [];
    const primary = list.find((account) => account.purpose === 'SPENDING')?.currency ?? 'EUR';
    const inPrimary = list.filter((account) => account.currency === primary);
    return {
      spendable: inPrimary
        .filter((account) => account.purpose === 'SPENDING')
        .reduce((total, account) => total + toNumber(account.balance), 0),
      saved: inPrimary
        .filter((account) => account.purpose === 'SAVINGS')
        .reduce((total, account) => total + toNumber(account.balance), 0),
      currency: primary,
      hasOtherCurrencies: list.some((account) => account.currency !== primary),
    };
  }, [accounts.data]);

  const animatedBalance = useCountUp(spendable);
  const [major, minor] = splitMoney(animatedBalance, currency);

  /** The last fortnight of daily net movement, for the line under the balance. */
  const trend = useMemo(() => {
    const series = summary.data?.series ?? [];
    return series.slice(-TREND_DAYS).map((point) => toNumber(point.income) - toNumber(point.spending));
  }, [summary.data]);

  /**
   * The month's net movement as a share of what the customer started it with.
   *
   * Computed from the opening position — the balance now, less what the month
   * has added — rather than from the balance itself, so a month that doubled a
   * small balance reads as +100% and not +50%. Suppressed entirely when the
   * opening position was zero or negative, where a percentage means nothing.
   */
  const delta = useMemo(() => {
    if (!summary.data) return null;
    const net = toNumber(summary.data.net);
    const opening = spendable - net;
    if (opening <= 0) return null;
    return (net / opening) * 100;
  }, [summary.data, spendable]);

  return (
    <div className="stack-lg">
      <header className="home-head">
        <h1>
          {greeting()}, {user?.firstName ?? 'there'}
        </h1>
        <Button
          variant="ghost"
          size="sm"
          className="home-head__edit"
          icon={<SlidersHorizontal />}
          onClick={() => setEditing(true)}
          aria-label="Edit home"
        >
          <span>Edit home</span>
        </Button>
      </header>

      {/* ------------------------------------------------------------------ */}
      {/* The balance. The one block on the page filled with ink.            */}
      {/* ------------------------------------------------------------------ */}
      <Surface tone="hero" className="hero">
        <div className="hero__head">
          <span className="label">Available to spend</span>
          {hasOtherCurrencies && (
            <Link to="/accounts" className="hero__other">
              Other currencies <ArrowRight aria-hidden="true" />
            </Link>
          )}
        </div>

        <div className="hero__figure">
          {accounts.loading ? (
            <Skeleton height="3.5rem" width="62%" />
          ) : (
            <p className="balance">
              {major}
              <span className="balance__minor">{minor}</span>
            </p>
          )}

          {delta !== null && (
            <span className={`delta ${delta >= 0 ? 'delta--up' : 'delta--down'}`}>
              {delta >= 0 ? <TrendingUp aria-hidden="true" /> : <TrendingDown aria-hidden="true" />}
              {delta >= 0 ? '+' : ''}
              {delta.toFixed(1)}% this month
            </span>
          )}
        </div>

        {trend.length > 1 && (
          <div className="hero__trend">
            <Sparkline
              values={trend}
              stroke="var(--blue-fill)"
              height={44}
              label={`Daily net movement over the last ${trend.length} days`}
            />
          </div>
        )}

        <div className="hero__facts">
          <div className="hero__fact">
            <span className="label">In this month</span>
            <span className="amount amount--in">
              {summary.loading ? '—' : formatMoney(summary.data?.income ?? 0, currency)}
            </span>
          </div>
          <div className="hero__fact">
            <span className="label">Out this month</span>
            <span className="amount">
              {summary.loading ? '—' : formatMoney(summary.data?.spending ?? 0, currency)}
            </span>
          </div>
          <div className="hero__fact">
            <span className="label">Set aside</span>
            <span className="amount">{formatMoney(saved, currency)}</span>
          </div>
        </div>
      </Surface>

      {/*
        The four things a customer opens MBank to do, always here and always in
        this order.

        Deliberately not "Add money" and "Exchange". Neither exists behind this
        product — a customer cannot credit their own account, and the server
        refuses cross-currency transfers on principle rather than by omission
        (see TransferService.validate). A button that opens a screen which
        cannot do the thing on the button is worse than one fewer button.
      */}
      <nav className="quick-actions" aria-label="Quick actions">
        <QuickAction icon={<ArrowLeftRight />} label="Send" onClick={() => navigate('/transfer')} primary />
        <QuickAction icon={<HandCoins />} label="Request" onClick={() => navigate('/requests')} />
        <QuickAction icon={<Split />} label="Split a bill" onClick={() => navigate('/requests')} />
        <QuickAction icon={<PiggyBank />} label="Save" onClick={() => navigate('/savings')} />
      </nav>

      {layout.map((widget) => (
        <HomeSection
          key={widget}
          widget={widget}
          currency={currency}
          onSelectTransaction={setSelected}
        />
      ))}

      {selected && <TransactionDetail transaction={selected} onClose={() => setSelected(null)} />}

      <EditHomeSheet
        open={editing}
        onClose={() => setEditing(false)}
        layout={layout}
        onToggle={toggle}
        onMove={move}
        onReset={reset}
      />
    </div>
  );
}

/** One of the four primary actions: a disc, a word, and a target you cannot miss. */
function QuickAction({
  icon,
  label,
  onClick,
  primary = false,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  primary?: boolean;
}) {
  return (
    <button type="button" className={`action ${primary ? 'action--key' : ''}`} onClick={onClick}>
      <span className="action__disc" aria-hidden="true">
        {icon}
      </span>
      {label}
    </button>
  );
}

/* =============================================================================
   The widgets
   -----------------------------------------------------------------------------
   Each fetches its own data. That is deliberate: a home screen with five blocks
   on it should issue the five requests those blocks need and no more, and a
   customer who has hidden Savings should not be paying for the goals call.
   ========================================================================== */

function HomeSection({
  widget,
  currency,
  onSelectTransaction,
}: {
  widget: HomeWidget;
  currency: CurrencyCode;
  onSelectTransaction: (transaction: TransactionResponse) => void;
}) {
  switch (widget) {
    case 'cards':
      return <CardsWidget />;
    case 'recent':
      return <RecentWidget onSelect={onSelectTransaction} />;
    case 'spending':
      return <SpendingWidget currency={currency} />;
    case 'goals':
      return <GoalsWidget />;
    case 'upcoming':
      return <UpcomingWidget />;
    case 'payees':
      return <PayeesWidget />;
    case 'accounts':
      return <AccountsWidget />;
    default:
      return null;
  }
}

function SectionHead({ title, to, linkLabel }: { title: string; to: string; linkLabel: string }) {
  return (
    <div className="section-head">
      <h2>{title}</h2>
      <Link to={to} className="section-head__link">
        {linkLabel} <ArrowRight aria-hidden="true" />
      </Link>
    </div>
  );
}

function CardsWidget() {
  const navigate = useNavigate();
  const cards = useAsync(() => cardsApi.list(), []);
  const wallet = (cards.data ?? []).slice(0, 4);

  return (
    <section className="stack">
      <SectionHead title="Your cards" to="/cards" linkLabel="All cards" />
      {cards.loading ? (
        <Skeleton height="200px" />
      ) : wallet.length === 0 ? (
        <Surface>
          <EmptyState
            title="No cards yet"
            body="Create a virtual card in a few seconds, or order a physical one."
            action={
              <Button variant="primary" icon={<Plus />} onClick={() => navigate('/cards')}>
                Create a card
              </Button>
            }
          />
        </Surface>
      ) : (
        <div className="card-track">
          {wallet.map((card) => (
            <div className="card-track__item" key={card.id}>
              <BankCard card={toPresentation(card)} interactive onClick={() => navigate('/cards')} />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function RecentWidget({ onSelect }: { onSelect: (transaction: TransactionResponse) => void }) {
  const recent = useAsync(() => transfersApi.transactions({ size: 6 }), []);

  return (
    <section className="stack">
      <SectionHead title="Recent transactions" to="/transactions" linkLabel="All transactions" />
      <Surface className="list-panel">
        {recent.loading ? (
          <SkeletonRows rows={5} />
        ) : recent.error ? (
          <ErrorState title={recent.error.title} body={recent.error.body} onRetry={recent.reload} />
        ) : (recent.data?.content.length ?? 0) === 0 ? (
          <EmptyState
            title="No transactions yet"
            body="Once you start using MBank, your activity will appear here."
          />
        ) : (
          recent.data?.content.map((transaction) => (
            <TransactionRow key={transaction.transferId} transaction={transaction} onSelect={onSelect} />
          ))
        )}
      </Surface>
    </section>
  );
}

function SpendingWidget({ currency }: { currency: CurrencyCode }) {
  const summary = useAsync(() => analyticsApi.summary(), []);
  const spending = summary.data?.spending ?? '0';

  return (
    <section className="stack">
      <SectionHead title="Spending this month" to="/analytics" linkLabel="Analytics" />
      <Surface className="panel">
        {summary.loading ? (
          <SkeletonRows rows={3} />
        ) : (summary.data?.byCategory.length ?? 0) === 0 ? (
          <EmptyState
            title="Nothing to break down yet"
            body="Once you have spent something, it will be grouped here."
            icon={<ChartPie />}
          />
        ) : (
          <>
            <div className="stack" style={{ gap: 'var(--space-1)' }}>
              <span className="label">You have spent</span>
              <span className="spend-total">{formatMoney(spending, currency)}</span>
            </div>
            <div className="stack">
              {summary.data?.byCategory.slice(0, 5).map((slice) => (
                <CategoryBar
                  key={slice.category ?? 'none'}
                  label={slice.category ?? null}
                  amount={slice.amount}
                  share={toNumber(slice.share)}
                  currency={currency}
                />
              ))}
            </div>
          </>
        )}
      </Surface>
    </section>
  );
}

function GoalsWidget() {
  const navigate = useNavigate();
  const goals = useAsync(() => savingsApi.list(), []);
  const active = (goals.data ?? []).filter((goal) => goal.status !== 'ARCHIVED').slice(0, 3);

  return (
    <section className="stack">
      <SectionHead title="Savings goals" to="/savings" linkLabel="All goals" />
      {goals.loading ? (
        <Skeleton height="96px" />
      ) : active.length === 0 ? (
        <Surface>
          <EmptyState
            title="No savings goals"
            body="Put money aside for something, and watch it get closer."
            icon={<PiggyBank />}
            action={
              <Button variant="secondary" icon={<Plus />} onClick={() => navigate('/savings')}>
                Start a goal
              </Button>
            }
          />
        </Surface>
      ) : (
        <div className="stack">
          {active.map((goal) => (
            <Surface
              as="button"
              type="button"
              interactive
              key={goal.id}
              className="goal-row"
              onClick={() => navigate('/savings')}
            >
              <div className="row-between">
                <span className="goal-row__name">{goal.name}</span>
                <span className="amount">
                  {formatMoney(goal.saved, goal.currency)}
                  <span className="dim"> / {formatMoney(goal.targetAmount, goal.currency)}</span>
                </span>
              </div>
              <Progress
                percent={goal.progressPercent}
                tone={goal.status === 'ACHIEVED' ? 'positive' : 'brand'}
                label={`${goal.name}: ${goal.progressPercent}% saved`}
                size="sm"
              />
            </Surface>
          ))}
        </div>
      )}
    </section>
  );
}

function UpcomingWidget() {
  const scheduled = useAsync(() => scheduledApi.list(), []);
  const due = (scheduled.data ?? [])
    .filter((order) => order.status === 'SCHEDULED')
    .sort((a, b) => a.nextRunOn.localeCompare(b.nextRunOn))
    .slice(0, 4);

  return (
    <section className="stack">
      <SectionHead title="Coming up" to="/scheduled" linkLabel="All scheduled" />
      <Surface className="list-panel">
        {scheduled.loading ? (
          <SkeletonRows rows={3} />
        ) : due.length === 0 ? (
          <EmptyState
            title="Nothing scheduled"
            body="Standing orders and future-dated payments will appear here."
            icon={<CalendarClock />}
          />
        ) : (
          due.map((order) => (
            <div className="txn" key={order.id}>
              <span className="txn__glyph" aria-hidden="true">
                <CalendarClock />
              </span>
              <span className="txn__body">
                <span className="txn__title">{order.payeeName ?? order.description ?? 'Payment'}</span>
                <span className="txn__meta">
                  {new Date(order.nextRunOn).toLocaleDateString(undefined, {
                    day: 'numeric',
                    month: 'short',
                  })}
                  {' · '}
                  {order.frequency === 'MONTHLY' ? 'Monthly' : order.frequency === 'WEEKLY' ? 'Weekly' : 'One-off'}
                </span>
              </span>
              <span className="txn__right">
                <span className="amount">{formatMoney(order.amount, order.currency)}</span>
              </span>
            </div>
          ))
        )}
      </Surface>
    </section>
  );
}

function PayeesWidget() {
  const navigate = useNavigate();
  const payees = useAsync(() => beneficiariesApi.list(), []);
  const favourites = (payees.data ?? [])
    .slice()
    .sort((a, b) => Number(b.favourite) - Number(a.favourite))
    .slice(0, 6);

  return (
    <section className="stack">
      <SectionHead title="Send to" to="/beneficiaries" linkLabel="All payees" />
      {payees.loading ? (
        <Skeleton height="96px" />
      ) : favourites.length === 0 ? (
        <Surface>
          <EmptyState
            title="No payees yet"
            body="Save somebody you send to often and they will show up here."
            icon={<Users />}
            action={
              <Button variant="secondary" icon={<Plus />} onClick={() => navigate('/beneficiaries')}>
                Add a payee
              </Button>
            }
          />
        </Surface>
      ) : (
        <div className="payee-strip">
          {favourites.map((payee) => (
            <button
              key={payee.id}
              type="button"
              className="payee-tile"
              onClick={() => navigate(`/transfer?to=${encodeURIComponent(payee.accountNumber)}`)}
            >
              <Avatar initials={avatarInitials(payee.nickname)} size="lg" />
              <span className="payee-tile__name truncate">{payee.nickname}</span>
            </button>
          ))}
          <button type="button" className="payee-tile" onClick={() => navigate('/beneficiaries')}>
            <span className="payee-tile__add" aria-hidden="true">
              <Plus />
            </span>
            <span className="payee-tile__name">New</span>
          </button>
        </div>
      )}
    </section>
  );
}

function AccountsWidget() {
  const navigate = useNavigate();
  const accounts = useAsync(() => accountsApi.list(), []);
  const spending = (accounts.data ?? []).filter((account) => account.purpose === 'SPENDING');

  return (
    <section className="stack">
      <SectionHead title="Accounts" to="/accounts" linkLabel="All accounts" />
      {accounts.loading ? (
        <Skeleton height="96px" />
      ) : (
        <div className="account-grid">
          {spending.map((account) => (
            <Surface
              as="button"
              type="button"
              interactive
              key={account.id}
              className="account-card"
              onClick={() => navigate('/accounts')}
            >
              <div className="account-card__head">
                <span className={`account-card__disc account-card__disc--${account.currency.toLowerCase()}`}>
                  {account.currency}
                </span>
                <span className="stack" style={{ gap: 0 }}>
                  <span className="account-card__name">{account.currency} account</span>
                  <span className="account-card__number">···· {account.accountNumber.slice(-4)}</span>
                </span>
              </div>
              <span className="account-card__balance">{formatMoney(account.balance, account.currency)}</span>
            </Surface>
          ))}
        </div>
      )}
    </section>
  );
}

/** One row of the spending breakdown: a name, an amount and a proportional bar. */
function CategoryBar({
  label,
  amount,
  share,
  currency,
}: {
  label: string | null;
  amount: string;
  share: number;
  currency: CurrencyCode;
}) {
  const meta = categoryMeta(label as TransactionCategory | null);
  return (
    <div className="cat-bar">
      <div className="row-between">
        <span className="cat-bar__name">{meta.label}</span>
        <span className="amount cat-bar__amount">{formatMoney(amount, currency)}</span>
      </div>
      <div className="cat-bar__track well">
        <span
          className="cat-bar__fill"
          style={{ width: `${Math.min(100, share)}%`, background: meta.colour }}
        />
      </div>
    </div>
  );
}

/* =============================================================================
   Edit home
   -----------------------------------------------------------------------------
   A checklist with two arrows per row rather than drag-and-drop. Dragging is
   nicer with a mouse and materially worse with a finger, a keyboard or a screen
   reader — and this is a phone-first product, so the version that works
   everywhere wins.
   ========================================================================== */

function EditHomeSheet({
  open,
  onClose,
  layout,
  onToggle,
  onMove,
  onReset,
}: {
  open: boolean;
  onClose: () => void;
  layout: HomeWidget[];
  onToggle: (id: HomeWidget) => void;
  onMove: (id: HomeWidget, direction: -1 | 1) => void;
  onReset: () => void;
}) {
  const hidden = HOME_WIDGETS.filter((id) => !layout.includes(id));

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Edit home"
      subtitle="Choose what appears, and in what order."
    >
      <div className="stack">
        <span className="label">On your home screen</span>
        {layout.map((id, index) => (
          <div className="widget-row" key={id}>
            <div className="grow">
              <div className="widget-row__title">{WIDGET_META[id].title}</div>
              <div className="widget-row__body">{WIDGET_META[id].description}</div>
            </div>
            <div className="row nowrap" style={{ gap: 'var(--space-1)' }}>
              <button
                type="button"
                className="icon-btn icon-btn--sm"
                onClick={() => onMove(id, -1)}
                disabled={index === 0}
                aria-label={`Move ${WIDGET_META[id].title} up`}
              >
                <MoveUp aria-hidden="true" />
              </button>
              <button
                type="button"
                className="icon-btn icon-btn--sm"
                onClick={() => onMove(id, 1)}
                disabled={index === layout.length - 1}
                aria-label={`Move ${WIDGET_META[id].title} down`}
              >
                <MoveDown aria-hidden="true" />
              </button>
              <button
                type="button"
                className="icon-btn icon-btn--sm"
                onClick={() => onToggle(id)}
                aria-label={`Remove ${WIDGET_META[id].title}`}
              >
                <Check aria-hidden="true" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {hidden.length > 0 && (
        <div className="stack">
          <span className="label">Available</span>
          {hidden.map((id) => (
            <div className="widget-row widget-row--off" key={id}>
              <div className="grow">
                <div className="widget-row__title">{WIDGET_META[id].title}</div>
                <div className="widget-row__body">{WIDGET_META[id].description}</div>
              </div>
              <button
                type="button"
                className="icon-btn icon-btn--sm"
                onClick={() => onToggle(id)}
                aria-label={`Add ${WIDGET_META[id].title}`}
              >
                <Plus aria-hidden="true" />
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="modal-actions">
        <Button variant="quiet" icon={<Wallet />} onClick={onReset}>
          Reset to default
        </Button>
        <Button variant="primary" onClick={onClose}>
          Done
        </Button>
      </div>
    </Sheet>
  );
}
