import { useEffect, useState } from 'react';

/**
 * Whether the visitor has asked for reduced motion.
 *
 * The CSS honours the preference on its own. This hook exists for the effects
 * JavaScript drives rather than CSS — the balance counting up, the card tilt,
 * the pointer-tracked sheen — so they are never started at all, rather than
 * started and then run at zero duration.
 */
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() =>
    typeof window !== 'undefined'
      ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
      : false,
  );

  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = (event: MediaQueryListEvent) => setReduced(event.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  return reduced;
}
