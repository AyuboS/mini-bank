import { useCallback, useEffect, useRef, useState } from 'react';
import { CloudOff, Loader2, RefreshCw } from 'lucide-react';
import { API_BASE_URL, pingApi } from '../api/client';
import { PUBLIC_DEMO } from '../utils/deployment';

/** Tells the reader the API is down, rather than letting every screen fail on its own. */
export function ConnectionBanner() {
  const [offline, setOffline] = useState(false);
  const [checking, setChecking] = useState(false);
  const attempts = useRef(0);
  const timer = useRef<number | undefined>(undefined);

  const probe = useCallback(async () => {
    setChecking(true);
    const up = await pingApi();
    setChecking(false);
    setOffline(!up);
    return up;
  }, []);

  useEffect(() => {
    let cancelled = false;

    // The scheduled retry backs off from 2s to 30s.
    const schedule = () => {
      const delay = Math.min(2000 * 2 ** attempts.current, 30_000);
      attempts.current += 1;
      timer.current = window.setTimeout(async () => {
        /* Cleared the moment it fires, not when the banner next re-renders. */
        timer.current = undefined;
        if (cancelled) return;
        const up = await probe();
        if (up) {
          attempts.current = 0;
        } else {
          schedule();
        }
      }, delay);
    };

    const onUnreachable = () => {
      if (timer.current !== undefined) return; // A retry loop is already running.
      setOffline(true);
      schedule();
    };

    window.addEventListener('minibank:unreachable', onUnreachable);

    // One probe on load, so the sign-in screen can say the API is down before
    // anybody types a password into a form that cannot submit.
    void probe().then((up) => {
      if (!up && !cancelled) onUnreachable();
    });

    return () => {
      cancelled = true;
      window.removeEventListener('minibank:unreachable', onUnreachable);
      if (timer.current !== undefined) window.clearTimeout(timer.current);
    };
  }, [probe]);

  // A manual Retry that succeeds should also stop the scheduled one.
  useEffect(() => {
    if (!offline && timer.current !== undefined) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
      attempts.current = 0;
    }
  }, [offline]);

  /* Tell the document the banner is up, so the page can reserve room for it. */
  useEffect(() => {
    const root = document.documentElement;
    if (offline) root.dataset.apiDown = 'true';
    else delete root.dataset.apiDown;
    return () => {
      delete root.dataset.apiDown;
    };
  }, [offline]);

  if (!offline) {
    return null;
  }

  /*
    In development API_BASE_URL is empty and the dev server proxies /api, so the
    origin alone would name the thing that *is* answering. Naming the API root
    points at the address that actually failed in both setups.
  */
  const where = `${API_BASE_URL || window.location.origin}/api`;

  return (
    <div className="conn" role="status" aria-live="polite">
      <span className="conn__icon">
        <CloudOff aria-hidden="true" />
      </span>
      <span className="conn__body">
        <strong className="conn__title">{PUBLIC_DEMO ? 'The demo server is starting' : 'MBank is not responding'}</strong>
        <span className="conn__detail">
          {PUBLIC_DEMO
            ? 'It sleeps when nobody is using it and takes up to a minute to wake. This message goes away when it is ready.'
            : 'We are checking again automatically. This message goes away when the connection is back.'}
          {import.meta.env.DEV && (
            <>
              {' '}
              Developer note: nothing answered at <code>{where}</code>.
            </>
          )}
        </span>
      </span>
      <button
        type="button"
        className="conn__retry"
        onClick={() => void probe()}
        disabled={checking}
      >
        {checking ? (
          <Loader2 className="conn__spin" aria-hidden="true" />
        ) : (
          <RefreshCw aria-hidden="true" />
        )}
        {checking ? 'Checking' : 'Retry'}
      </button>
    </div>
  );
}
