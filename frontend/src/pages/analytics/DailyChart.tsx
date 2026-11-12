import { useMemo } from 'react';
import type { CurrencyCode } from '../../api/types';
import { EmptyState } from '../../components/States';
import { formatMoney, toNumber } from '../../utils/format';

/* ---------------------------------------------------------------------------- */

/**
 * Spending and income, one bar pair per day.
 *
 * Drawn as divs rather than SVG, because the bars are a flex row of rectangles
 * and a chart library would bring its own visual language to override. The
 * heights are percentages of the busiest day, so the shape of the month is
 * legible regardless of its scale.
 */
export function DailyChart({ series, currency }: { series: { date: string; spending: string; income: string }[]; currency: CurrencyCode }) {
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
