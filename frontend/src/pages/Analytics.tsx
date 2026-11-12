import { useMemo, useState } from 'react';
import {
  Plus,
  RefreshCw,
  Sparkles,
  Trash2,
  TrendingDown,
  TrendingUp,
  Wallet,
} from 'lucide-react';
import { analyticsApi } from '../api/analytics';
import { budgetsApi } from '../api/budgets';
import { transfersApi } from '../api/transfers';
import type { BudgetResponse, CurrencyCode, TransactionCategory } from '../api/types';
import { TRANSACTION_CATEGORIES } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import {
  Button,
  Input,
  Progress,
  Select,
  Sheet,
  Skeleton,
  Stat,
  Surface,
} from '../components/ui';
import { MonthSwitch } from '../components/MonthSwitch';
import { PageHeader } from '../components/PageHeader';
import { CategoryIcon } from '../components/CategoryIcon';
import { EmptyState, ErrorState } from '../components/States';
import { CATEGORY_META, categoryMeta } from '../utils/categories';
import { describeInsight } from '../utils/insights';
import { formatDay, formatMoney, toMonthKey, toNumber } from '../utils/format';
import { formatMinorUnits, toMinorUnits } from '../utils/money';
import { findSubscriptions, monthlyTotal, type Subscription } from '../utils/subscriptions';
import { toFriendlyError } from '../utils/errors';

/**
 * Spending analytics.
 *
 * Everything on this screen is arithmetic over the customer's own settled
 * transactions. Nothing is estimated, projected or compared against other
 * customers, and when the data cannot support a statement the statement is
 * simply absent — which is why the insights list can legitimately be empty.
 */
export function AnalyticsPage() {
  const [month, setMonth] = useState(toMonthKey());
  const currency: CurrencyCode = 'EUR';

  const summary = useAsync(() => analyticsApi.summary(month, currency), [month]);
  const insights = useAsync(() => analyticsApi.insights(currency), []);
  const budgets = useAsync(() => budgetsApi.list(currency), []);
  const [budgetSheet, setBudgetSheet] = useState(false);

  /*
    A wide window, because recurring payments are only visible across months.
    200 rows is roughly half a year of ordinary use and one request; paging the
    whole history to find Netflix would not be a better answer.
  */
  const history = useAsync(() => transfersApi.transactions({ size: 200 }), []);
  const subscriptions = useMemo(
    () => findSubscriptions(history.data?.content ?? []),
    [history.data],
  );

  const spending = toMinorUnits(summary.data?.spending);
  const income = toMinorUnits(summary.data?.income);
  const net = toMinorUnits(summary.data?.net);

  return (
    <div className="stack-lg">
      <PageHeader
        title="Analytics"
        subtitle="Every figure here is computed from your own settled transactions."
      />

      {/* -------------------------------------------------------------- */}
      {/* Month picker                                                    */}
      {/* -------------------------------------------------------------- */}
      <MonthSwitch month={month} onChange={setMonth} />

      {summary.error ? (
        <ErrorState title={summary.error.title} body={summary.error.body} onRetry={summary.reload} />
      ) : (
        <>
          {/* ------------------------------------------------------------ */}
          {/* The three totals                                              */}
          {/* ------------------------------------------------------------ */}
          <div className="stat-grid">
            <Stat
              label="Money in"
              value={summary.loading ? '—' : formatMinorUnits(income, currency)}
              tone="positive"
              icon={<TrendingUp />}
            />
            <Stat
              label="Money out"
              value={summary.loading ? '—' : formatMinorUnits(spending, currency)}
              icon={<TrendingDown />}
            />
            <Stat
              label={net >= 0 ? 'Put aside' : 'Overspent by'}
              value={summary.loading ? '—' : formatMinorUnits(Math.abs(net), currency)}
              tone={net >= 0 ? 'positive' : 'danger'}
              detail={`${summary.data?.transactionCount ?? 0} transactions`}
              icon={<Wallet />}
            />
          </div>

          {/*
            Movement between the customer's own accounts is stated explicitly
            rather than folded into either total. Without this line, somebody who
            moved €500 into savings would look at "money out" and wonder where
            it went.
          */}
          {toMinorUnits(summary.data?.internalMovement) > 0 && (
            <p className="dim analytics__note">
              {formatMoney(summary.data?.internalMovement ?? 0, currency)} moved between your own accounts.
              Counted as neither money in nor money out.
            </p>
          )}

          {/* ------------------------------------------------------------ */}
          {/* Daily chart                                                   */}
          {/* ------------------------------------------------------------ */}
          <Surface className="panel">
            <div className="section-head">
              <h2>Day by day</h2>
            </div>
            {summary.loading ? (
              <Skeleton height="140px" />
            ) : (
              <DailyChart series={summary.data?.series ?? []} currency={currency} />
            )}
          </Surface>

          <div className="analytics-grid">
            {/* ---------------------------------------------------------- */}
            {/* Category breakdown                                          */}
            {/* ---------------------------------------------------------- */}
            <Surface className="panel">
              <div className="section-head">
                <h2>Where it went</h2>
              </div>

              {summary.loading ? (
                <Skeleton height="200px" />
              ) : (summary.data?.byCategory.length ?? 0) === 0 ? (
                <EmptyState title="Nothing spent this month" body="Spending will be grouped here." />
              ) : (
                <div className="stack">
                  {summary.data?.byCategory.map((slice) => {
                    const meta = categoryMeta(slice.category);
                    return (
                      <div className="cat-row" key={slice.category ?? 'none'}>
                        <CategoryIcon category={slice.category} size="sm" />
                        <div className="grow stack gap-1">
                          <div className="row-between">
                            <span className="cat-row__name">{meta.label}</span>
                            <span className="amount cat-row__amount">
                              {formatMoney(slice.amount, currency)}
                            </span>
                          </div>
                          <div className="cat-bar__track well">
                            <span
                              className="cat-bar__fill"
                              style={{ width: `${Math.min(100, toNumber(slice.share))}%`, background: meta.colour }}
                            />
                          </div>
                          <span className="dim cat-row__share">
                            {slice.share}% · {slice.transactionCount} payment
                            {slice.transactionCount === 1 ? '' : 's'}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {summary.data?.largest && (
                <div className="largest well">
                  <span className="label">Largest single payment</span>
                  <div className="row-between">
                    <span>{summary.data.largest.description || categoryMeta(summary.data.largest.category).label}</span>
                    <span className="amount">{formatMoney(summary.data.largest.amount, currency)}</span>
                  </div>
                </div>
              )}
            </Surface>

            {/* ---------------------------------------------------------- */}
            {/* Insights and budgets                                        */}
            {/* ---------------------------------------------------------- */}
            <div className="stack-lg">
              <Surface className="panel">
                <div className="section-head">
                  <h2>Insights</h2>
                </div>

                {insights.loading ? (
                  <Skeleton height="120px" />
                ) : (insights.data?.length ?? 0) === 0 ? (
                  <EmptyState
                    title="Not enough history yet"
                    body="Observations appear once there is a month of activity to compare against. Nothing here is invented."
                    icon={<Sparkles />}
                  />
                ) : (
                  <div className="stack">
                    {insights.data?.map((insight, index) => {
                      const worded = describeInsight(insight, currency);
                      return (
                        <div
                          className={`insight insight--${insight.tone.toLowerCase()}`}
                          key={`${insight.kind}-${insight.category ?? index}`}
                        >
                          <p className="insight__headline">{worded.headline}</p>
                          <p className="insight__detail">{worded.detail}</p>
                        </div>
                      );
                    })}
                  </div>
                )}
              </Surface>

              <Surface className="panel">
                <div className="section-head">
                  <h2>Budgets</h2>
                  <Button size="sm" variant="ghost" icon={<Plus />} onClick={() => setBudgetSheet(true)}>
                    Set
                  </Button>
                </div>

                {budgets.loading ? (
                  <Skeleton height="120px" />
                ) : (budgets.data?.length ?? 0) === 0 ? (
                  <EmptyState
                    title="No budgets set"
                    body="Cap a category, and MBank will tell you when you are getting close."
                  />
                ) : (
                  <div className="stack">
                    {budgets.data?.map((budget) => (
                      <BudgetRow key={budget.id} budget={budget} onChanged={budgets.reload} />
                    ))}
                  </div>
                )}
              </Surface>
            </div>
          </div>

          <SubscriptionsPanel
            subscriptions={subscriptions}
            loading={history.loading}
            currency={currency}
          />
        </>
      )}

      <BudgetSheet
        open={budgetSheet}
        currency={currency}
        existing={budgets.data ?? []}
        onClose={() => setBudgetSheet(false)}
        onSaved={() => {
          setBudgetSheet(false);
          budgets.reload();
        }}
      />
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

/**
 * Recurring payments.
 *
 * Inferred from the customer's own settled transactions rather than fetched
 * from a merchant feed, because there is not one — see utils/subscriptions.ts.
 * The heading says "looks recurring" for that reason: the panel is telling the
 * customer what MBank can see in their statement, not asserting a fact about
 * their contracts.
 */
function SubscriptionsPanel({
  subscriptions,
  loading,
  currency,
}: {
  subscriptions: Subscription[];
  loading: boolean;
  currency: CurrencyCode;
}) {
  if (loading) return <Skeleton height="160px" />;

  return (
    <section className="section">
      <div className="section__head">
        <h2 className="section__title">Subscriptions</h2>
        {subscriptions.length > 0 && (
          <span className="amount">
            {formatMinorUnits(monthlyTotal(subscriptions), currency)}
            <span className="dim"> a month</span>
          </span>
        )}
      </div>

      <Surface className="list-panel">
        {subscriptions.length === 0 ? (
          <EmptyState
            title="Nothing looks recurring yet"
            body="Payments to the same place in two or more months will be grouped here."
            icon={<RefreshCw />}
          />
        ) : (
          subscriptions.map((subscription) => (
            <div className="txn" key={subscription.name}>
              <span className="txn__glyph" aria-hidden="true">
                <RefreshCw />
              </span>
              <span className="txn__body">
                <span className="txn__title truncate">{subscription.name}</span>
                <span className="txn__meta truncate">
                  Seen in {subscription.months} months · next around {formatDay(subscription.nextDue)}
                </span>
              </span>
              <span className="txn__right">
                <span className="amount">{formatMinorUnits(subscription.amountMinor, subscription.currency)}</span>
              </span>
            </div>
          ))
        )}
      </Surface>

      <p className="analytics__note">
        Worked out from your statement: the same payee, in two or more months, for about the same
        amount. MBank has no feed from the merchants themselves, so an annual plan or a renamed
        payment will not appear here.
      </p>
    </section>
  );
}

/* ---------------------------------------------------------------------------- */

/**
 * Spending and income, one bar pair per day.
 *
 * Drawn as divs rather than SVG, because the bars are a flex row of rectangles
 * and a chart library would bring its own visual language to override. The
 * heights are percentages of the busiest day, so the shape of the month is
 * legible regardless of its scale.
 */
function DailyChart({ series, currency }: { series: { date: string; spending: string; income: string }[]; currency: CurrencyCode }) {
  // Bar heights only; nothing here is added up or shown as money.
  const peak = useMemo(
    () => Math.max(1, ...series.map((point) => Math.max(toNumber(point.spending), toNumber(point.income)))),
    [series],
  );

  if (series.length === 0) {
    return <EmptyState title="No activity this month" />;
  }

  return (
    <div className="daily-chart" role="img" aria-label={`Daily spending and income for the month, peaking at ${formatMoney(peak, currency)}`}>
      {series.map((point) => {
        const spending = toNumber(point.spending);
        const income = toNumber(point.income);
        const day = Number(point.date.slice(-2));
        return (
          <div className="daily-chart__day" key={point.date}>
            <div className="daily-chart__bars">
              <span
                className="daily-chart__bar daily-chart__bar--in"
                style={{ height: `${(income / peak) * 100}%` }}
                /* The tooltip is the only place the exact figure appears; the
                   aria-label above covers the non-visual case. */
                title={`${point.date}: ${formatMoney(income, currency)} in`}
              />
              <span
                className="daily-chart__bar daily-chart__bar--out"
                style={{ height: `${(spending / peak) * 100}%` }}
                title={`${point.date}: ${formatMoney(spending, currency)} out`}
              />
            </div>
            {/* Every fifth day is labelled. Labelling all 31 is unreadable at
                any width a phone has. */}
            <span className="daily-chart__label">{day % 5 === 0 ? day : ''}</span>
          </div>
        );
      })}
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function BudgetRow({ budget, onChanged }: { budget: BudgetResponse; onChanged: () => void }) {
  const toast = useToast();
  const meta = CATEGORY_META[budget.category];

  const remove = async () => {
    try {
      await budgetsApi.remove(budget.id);
      toast.success('Budget removed');
      onChanged();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <div className="budget-row">
      <div className="row-between">
        <span className="row gap-2">
          <CategoryIcon category={budget.category} size="sm" />
          <span className="budget-row__name">{meta.label}</span>
        </span>
        <span className="row gap-2">
          <span className="amount budget-row__amount">
            {formatMoney(budget.spent, budget.currency)}
            <span className="dim"> / {formatMoney(budget.amount, budget.currency)}</span>
          </span>
          <button type="button" className="icon-btn" onClick={remove} aria-label={`Remove the ${meta.label} budget`}>
            <Trash2 aria-hidden="true" />
          </button>
        </span>
      </div>

      <Progress
        percent={budget.usedPercent}
        tone={budget.state === 'OVER' ? 'over' : budget.state === 'NEARING' ? 'warning' : 'positive'}
        label={`${meta.label}: ${budget.usedPercentRaw}% of budget used`}
        size="sm"
      />

      <span className={`budget-row__state budget-row__state--${budget.state.toLowerCase()}`}>
        {budget.state === 'OVER'
          ? `${budget.usedPercentRaw}% used — over by ${formatMinorUnits(
              toMinorUnits(budget.spent) - toMinorUnits(budget.amount),
              budget.currency,
            )}`
          : `${budget.usedPercentRaw}% used · ${formatMoney(budget.remaining, budget.currency)} left`}
      </span>
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function BudgetSheet({
  open,
  currency,
  existing,
  onClose,
  onSaved,
}: {
  open: boolean;
  currency: CurrencyCode;
  existing: BudgetResponse[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [category, setCategory] = useState<TransactionCategory>('GROCERIES');
  const [amount, setAmount] = useState('');
  const [saving, setSaving] = useState(false);

  /** Only categories that represent spending; a budget on Salary is meaningless. */
  const options = TRANSACTION_CATEGORIES.filter((candidate) => CATEGORY_META[candidate].spending);
  const already = existing.find((budget) => budget.category === category);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      await budgetsApi.set({ category, amount, currency });
      toast.success(already ? 'Budget updated' : 'Budget set');
      onSaved();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Set a monthly budget"
      subtitle="Progress is measured against your real transactions, and updates as they arrive."
    >
      <form className="stack-lg" onSubmit={submit}>
        <Select
          label="Category"
          value={category}
          onChange={(event) => setCategory(event.target.value as TransactionCategory)}
        >
          {options.map((option) => (
            <option key={option} value={option}>
              {CATEGORY_META[option].label}
            </option>
          ))}
        </Select>

        <Input
          label="Monthly limit"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0.01"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="300.00"
          suffix={<span className="dim">{currency}</span>}
          hint={already ? `Currently ${formatMoney(already.amount, currency)}. Saving replaces it.` : undefined}
          required
        />

        <div className="modal-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving}>
            {already ? 'Update budget' : 'Set budget'}
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
