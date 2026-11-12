import { useMemo, useState } from 'react';
import { Plus, Sparkles, TrendingDown, TrendingUp, Wallet } from 'lucide-react';
import { analyticsApi } from '../../api/analytics';
import { budgetsApi } from '../../api/budgets';
import { transfersApi } from '../../api/transfers';
import type { CurrencyCode } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { Button, Skeleton, Stat, Surface } from '../../components/ui';
import { MonthSwitch } from '../../components/MonthSwitch';
import { PageHeader } from '../../components/PageHeader';
import { CategoryIcon } from '../../components/CategoryIcon';
import { EmptyState, ErrorState } from '../../components/States';
import { categoryMeta } from '../../utils/categories';
import { describeInsight } from '../../utils/insights';
import { formatMoney, toMonthKey, toNumber } from '../../utils/format';
import { formatMinorUnits, toMinorUnits } from '../../utils/money';
import { findSubscriptions } from '../../utils/subscriptions';
import { SubscriptionsPanel } from './SubscriptionsPanel';
import { DailyChart } from './DailyChart';
import { BudgetRow, BudgetSheet } from './Budgets';

/** Spending analytics. */
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
