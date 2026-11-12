import { ChevronLeft, ChevronRight } from 'lucide-react';
import { formatMonth, toMonthKey } from '../utils/format';

interface MonthSwitchProps {
  /** "2026-11" */
  month: string;
  onChange: (month: string) => void;
}

/** Previous and next month around a label. The current month is the last one. */
export function MonthSwitch({ month, onChange }: MonthSwitchProps) {
  const isCurrentMonth = month === toMonthKey();

  const step = (delta: number) => {
    const [year, monthNumber] = month.split('-').map(Number);
    if (!year || !monthNumber) return;
    onChange(toMonthKey(new Date(year, monthNumber - 1 + delta, 1)));
  };

  return (
    <div className="month-switch" role="group" aria-label="Month">
      <button type="button" className="icon-btn" onClick={() => step(-1)} aria-label="Previous month">
        <ChevronLeft aria-hidden="true" />
      </button>
      <span className="month-switch__label" aria-live="polite">
        {formatMonth(month)}
      </span>
      <button
        type="button"
        className="icon-btn"
        onClick={() => step(1)}
        disabled={isCurrentMonth}
        aria-label="Next month"
      >
        <ChevronRight aria-hidden="true" />
      </button>
    </div>
  );
}
