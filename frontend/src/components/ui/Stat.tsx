import type { ReactNode } from 'react';

interface StatProps {
  label: string;
  value: ReactNode;
  /** A change, a share, or the second half of a ratio. */
  detail?: ReactNode;
  icon?: ReactNode;
  /** Colours the value: `positive` for money in, `danger` for a problem. */
  tone?: 'default' | 'positive' | 'warning' | 'danger';
}

/** One figure with its label. The unit the financial overview is assembled from. */
export function Stat({ label, value, detail, icon, tone = 'default' }: StatProps) {
  return (
    <div className="stat surface">
      <div className="stat__head">
        <span className="label">{label}</span>
        {icon && <span className="stat__icon">{icon}</span>}
      </div>
      <div className={`stat__value amount stat__value--${tone}`}>{value}</div>
      {detail && <div className="stat__detail">{detail}</div>}
    </div>
  );
}
