import { useId } from 'react';

interface SparklineProps {
  /** Oldest first. Fewer than two points draws nothing. */
  values: number[];
  /** Any CSS colour, including a variable. */
  stroke?: string;
  height?: number;
  /** Fills the area under the line with a fade of the stroke colour. */
  filled?: boolean;
  /** An accessible summary. Without one the chart is invisible to a screen reader. */
  label?: string;
}

/**
 * A small line chart with no axes, no grid and no library.
 *
 * Hand-drawn SVG rather than a charting dependency, for two reasons that both
 * matter here: a chart library ships a default visual language that fights the
 * glass and has to be overridden property by property, and this is forty lines
 * of path arithmetic against a hundred kilobytes of JavaScript.
 *
 * The viewBox is a fixed 100 × `height` and the path is scaled into it, so the
 * component is resolution-independent and stretches to whatever width its
 * container gives it without redrawing.
 */
export function Sparkline({ values, stroke = 'var(--brand)', height = 40, filled = true, label }: SparklineProps) {
  const gradientId = useId();

  // One point is not a trend, and zero points is not a chart.
  if (values.length < 2) {
    return <div style={{ height }} aria-hidden="true" />;
  }

  const max = Math.max(...values);
  const min = Math.min(...values);
  // A flat series would divide by zero; a range of 1 draws it as a centred line.
  const range = max - min || 1;

  const points = values.map((value, index) => {
    const x = (index / (values.length - 1)) * 100;
    // SVG's y axis points down, so the value is inverted. The 2px inset keeps
    // the stroke from being clipped at the top and bottom edges.
    const y = height - 2 - ((value - min) / range) * (height - 4);
    return [x, y] as const;
  });

  const line = points.map(([x, y], index) => `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`).join(' ');
  const area = `${line} L100,${height} L0,${height} Z`;

  return (
    <svg
      viewBox={`0 0 100 ${height}`}
      preserveAspectRatio="none"
      style={{ width: '100%', height }}
      role={label ? 'img' : 'presentation'}
      aria-label={label}
    >
      {filled && (
        <>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={stroke} stopOpacity="0.28" />
              <stop offset="100%" stopColor={stroke} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={area} fill={`url(#${gradientId})`} />
        </>
      )}
      <path
        d={line}
        fill="none"
        stroke={stroke}
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        /* The stroke would otherwise be squashed by preserveAspectRatio="none"
           along with everything else, and end up thicker vertically than
           horizontally. */
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
