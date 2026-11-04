interface SkeletonProps {
  /** Any CSS length. A bare number is read as pixels, as elsewhere in React. */
  width?: string | number;
  height?: string | number;
  /** A circle, for an avatar or an icon slot. */
  circle?: boolean;
  className?: string;
}

/**
 * A shimmering pane standing in for content that has not arrived.
 *
 * Skeletons rather than a spinner because they say something a spinner cannot:
 * how much is coming and roughly where it will be, so the layout does not jump
 * when it lands. The shimmer is a moving gradient, which stops on
 * `prefers-reduced-motion` and leaves a static block.
 *
 * Marked `aria-hidden`: the loading state is announced once by the region that
 * owns it, not by every placeholder rectangle inside it.
 */
export function Skeleton({ width = '100%', height = '1rem', circle = false, className = '' }: SkeletonProps) {
  return (
    <span
      className={`skeleton ${circle ? 'skeleton--circle' : ''} ${className}`}
      style={{ width, height }}
      aria-hidden="true"
    />
  );
}

/** A paragraph of skeleton lines, the last one short so it reads as prose. */
export function SkeletonLines({ lines = 3 }: { lines?: number }) {
  return (
    <div className="stack" aria-hidden="true">
      {Array.from({ length: lines }).map((_, index) => (
        <Skeleton key={index} height="0.75rem" width={index === lines - 1 ? '58%' : '100%'} />
      ))}
    </div>
  );
}

/**
 * A list of rows that have not loaded.
 *
 * Wrapped in a live region so a screen reader hears "Loading" once, instead of
 * silence followed by content appearing from nowhere.
 */
export function SkeletonRows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="stack" role="status" aria-busy="true">
      <span className="sr-only">Loading</span>
      {Array.from({ length: rows }).map((_, index) => (
        <div className="skeleton-row" key={index} aria-hidden="true">
          <Skeleton circle width="40px" height="40px" />
          <div className="grow stack">
            <Skeleton height="0.8125rem" width="42%" />
            <Skeleton height="0.6875rem" width="26%" />
          </div>
          <Skeleton height="0.9375rem" width="72px" />
        </div>
      ))}
    </div>
  );
}
