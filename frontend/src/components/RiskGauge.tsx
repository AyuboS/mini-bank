interface RiskGaugeProps {
  /** 0–100. */
  score: number;
  decision: 'ALLOW' | 'REVIEW' | 'BLOCK';
  size?: number;
}

const TONE = {
  ALLOW: { stroke: 'rgb(79,217,164)', glow: 'rgba(79,217,164,0.35)' },
  REVIEW: { stroke: 'rgb(255,196,112)', glow: 'rgba(255,196,112,0.35)' },
  BLOCK: { stroke: 'rgb(255,133,133)', glow: 'rgba(255,133,133,0.35)' },
} as const;

/**
 * The risk score as a thin circular arc.
 *
 * A ring rather than a bar deliberately: a horizontal bar that fills with
 * colour reads as a game health meter, which is the wrong register entirely for
 * telling somebody their payment is being reviewed. The arc is quiet, and the
 * number stays the thing you read.
 */
export function RiskGauge({ score, decision, size = 108 }: RiskGaugeProps) {
  const tone = TONE[decision];
  const stroke = 6;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(100, score));
  const offset = circumference - (clamped / 100) * circumference;

  return (
    <div className="risk__gauge" style={{ width: size, height: size }}>
      <svg
        width={size}
        height={size}
        /* Start the arc at twelve o'clock instead of three. */
        style={{ transform: 'rotate(-90deg)' }}
        aria-hidden="true"
      >
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="rgba(255,255,255,0.07)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={tone.stroke}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{
            filter: `drop-shadow(0 0 6px ${tone.glow})`,
            transition: 'stroke-dashoffset var(--dur-panel) var(--ease)',
          }}
        />
      </svg>
      <div className="risk__readout">
        <div>
          <div className="risk__score" style={{ color: tone.stroke }}>
            {clamped}
          </div>
          <div className="risk__of">of 100</div>
        </div>
      </div>
      <span className="sr-only">
        Risk score {clamped} out of 100. Decision: {decision}.
      </span>
    </div>
  );
}
