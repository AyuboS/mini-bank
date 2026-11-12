import { useCallback, useEffect, useRef, useState } from 'react';
import { CloudOff, Loader2, RefreshCw } from 'lucide-react';
import { API_BASE_URL, pingApi } from '../api/client';

/**
 * Tells the reader the API is down, rather than letting every screen fail on
 * its own.
 *
 * "Cannot reach MBank" is a dead end: it names no cause and offers no next
 * step, and the most common cause by far is that the backend simply is not
 * running yet. This probes once on mount, listens for the unreachable event the
 * API client raises, then retries on a backoff until the server answers —
 * clearing itself without the reader having to reload anything.
 */
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
        /*
          Cleared the moment it fires, not when the banner next re-renders.
          `timer.current` is what tells the unreachable listener that a retry
          loop is already running; leaving a dead id in it for the length of a
          render would let a failure arriving in that window be swallowed, and
          the banner would then never appear again.
        */
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

  /*
    Tell the document the banner is up, so the page can reserve room for it.

    The banner is fixed and top-centred, which put it straight over the first
    heading of whatever screen was open. Reserving space in CSS rather than
    moving the banner keeps it where an outage notice belongs — the first thing
    on the page — without hiding the thing it is sitting on. The flag is on the
    root element rather than in React state because the padding belongs to
    layouts this component does not render.
  */
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
        <strong className="conn__title">MBank is not responding</strong>
        <span className="conn__detail">
          We are checking again automatically. This message goes away when the connection is back.
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
