import { useCallback, useEffect, useRef, useState } from 'react';
import { toFriendlyError } from '../utils/errors';
import type { FriendlyError } from '../utils/errors';

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: FriendlyError | null;
}

/**
 * Runs an async loader and tracks the three states every screen needs.
 *
 * Centralising this is what keeps "loading", "failed" and "empty" from being
 * reinvented — slightly differently — on each page. The in-flight result is
 * discarded if the component unmounts first, so a slow response cannot set
 * state on something that is gone.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []) {
  const [state, setState] = useState<AsyncState<T>>({ data: null, loading: true, error: null });
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const data = await loader();
      if (mounted.current) {
        setState({ data, loading: false, error: null });
      }
    } catch (error) {
      if (mounted.current) {
        setState({ data: null, loading: false, error: toFriendlyError(error) });
      }
    }
    // The loader is intentionally not a dependency: callers write it inline,
    // so a new function identity every render would loop forever.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    void run();
  }, [run]);

  /**
   * Replaces the loaded value, either outright or from the current one.
   *
   * The updater form matters for lists that are mutated in place: freezing one
   * card should swap that card and leave the rest — and the surrounding scroll
   * position — alone, rather than refetching the page and snapping back to the
   * top. Taking the current value as an argument avoids the caller closing over
   * a stale copy.
   */
  const setData = useCallback((next: T | ((current: T | null) => T | null)) => {
    setState((state) => ({
      data: typeof next === 'function' ? (next as (current: T | null) => T | null)(state.data) : next,
      loading: false,
      error: null,
    }));
  }, []);

  return { ...state, reload: run, setData };
}
