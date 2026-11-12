import { useEffect, useState } from 'react';

/** Tracks a media query from JavaScript. */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() =>
    typeof window !== 'undefined' ? window.matchMedia(query).matches : false,
  );

  useEffect(() => {
    const list = window.matchMedia(query);
    // Re-read on mount: the query may have changed between the initial state and
    // this effect, for instance if the window was resized during hydration.
    setMatches(list.matches);
    const onChange = (event: MediaQueryListEvent) => setMatches(event.matches);
    list.addEventListener('change', onChange);
    return () => list.removeEventListener('change', onChange);
  }, [query]);

  return matches;
}

/** The product's own phone breakpoint, so the number lives in one place. */
export function useIsPhone(): boolean {
  return useMediaQuery('(max-width: 767px)');
}
