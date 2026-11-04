interface ProgressProps {
  /** 0–100. Clamped, so a caller cannot draw a bar past its own track. */
  percent: number;
  /** Colours the fill. `over` is for a budget that has been passed. */
  tone?: 'brand' | 'positive' | 'warning' | 'over';
  /** An accessible description, since the bar itself carries no text. */
  label?: string;
  size?: 'sm' | 'md';
}

/**
 * A filled cut into the glass.
 *
 * `role="progressbar"` with the three value attributes, so the figure is
 * available to a screen reader — a bar is a picture of a number, and without
 * them the number is only available to people who can see the picture.
 */
export function Progress({ percent, tone = 'brand', label, size = 'md' }: ProgressProps) {
  const clamped = Math.min(100, Math.max(0, Math.round(percent)));
  return (
    <div
      className={`progress progress--${size} well`}
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
    >
      <span className={`progress__fill progress__fill--${tone}`} style={{ width: `${clamped}%` }} />
    </div>
  );
}
