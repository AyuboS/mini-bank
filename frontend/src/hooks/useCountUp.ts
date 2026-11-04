import { useEffect, useRef, useState } from 'react';
import { useReducedMotion } from './useReducedMotion';

/**
 * Counts a figure up from zero when it first appears.
 *
 * Used on the dashboard balance and nowhere else. It is a flourish, and the
 * rules that keep it from becoming an irritation are:
 *
 *  - it runs once, on the first real value, not on every update. A balance that
 *    re-counts every time the page refetches is maddening.
 *  - it is short. 900ms is long enough to register and over before anyone could
 *    be waiting on it.
 *  - it eases out, so most of the distance is covered immediately and the last
 *    digits settle. A linear count reads like a slot machine.
 *  - `prefers-reduced-motion` skips it entirely and returns the value.
 *
 * The figure is rendered with tabular numerals (see `.balance` in base.css), so
 * it does not jitter horizontally while the digits change.
 */
export function useCountUp(target: number, durationMs = 900): number {
  const reducedMotion = useReducedMotion();
  const [value, setValue] = useState(reducedMotion ? target : 0);
  const hasRun = useRef(false);

  useEffect(() => {
    if (reducedMotion) {
      setValue(target);
      return;
    }
    // Only the first non-zero value animates; later changes land directly.
    if (hasRun.current || target === 0) {
      setValue(target);
      return;
    }
    hasRun.current = true;

    let frame = 0;
    const start = performance.now();

    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs);
      // Cubic ease-out: fast, then settling.
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(target * eased);
      if (progress < 1) {
        frame = requestAnimationFrame(tick);
      } else {
        // Land exactly on the target, rather than on 4250.4999 from the easing.
        setValue(target);
      }
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target, durationMs, reducedMotion]);

  return value;
}
