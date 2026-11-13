import { useCallback, useEffect, useRef, useState } from 'react';
import { toFriendlyError } from '../utils/errors';
import type { FriendlyError } from '../utils/errors';

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: FriendlyError | null;
}

/** Runs an async loader and tracks the three states every screen needs. */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []) {
  const [state, setState] = useState<AsyncState<T>>({ data: null, loading: true, error: null });
  const mounted = useRef(true);
  // Counts requests, so a slow answer to an old question cannot replace a newer one.
  const latestRun = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    const thisRun = ++latestRun.current;
    const isCurrent = () => mounted.current && thisRun === latestRun.current;
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const data = await loader();
      if (isCurrent()) {
        setState({ data, loading: false, error: null });
      }
    } catch (error) {
      if (isCurrent()) {
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

  /** Replaces the loaded value, either outright or from the current one. */
  const setData = useCallback((next: T | ((current: T | null) => T | null)) => {
    setState((state) => ({
      data: typeof next === 'function' ? (next as (current: T | null) => T | null)(state.data) : next,
      loading: false,
      error: null,
    }));
  }, []);

  return { ...state, reload: run, setData };
}
