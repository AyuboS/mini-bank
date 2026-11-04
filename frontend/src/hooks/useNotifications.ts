import { useCallback, useEffect, useState } from 'react';
import { notificationsApi } from '../api/notifications';
import { useAuth } from '../auth/AuthContext';

/** How often the badge is refreshed. */
const POLL_INTERVAL_MS = 60_000;

/**
 * The unread notification count, for the badge in the chrome.
 *
 * Polled rather than pushed: this application has no websocket, and opening one
 * for a number that changes a few times a day would be a lot of machinery for
 * very little. A minute is well inside the window in which somebody would
 * notice, and the endpoint is a single indexed count.
 *
 * Polling pauses while the tab is hidden. A background tab left open overnight
 * would otherwise make several hundred requests nobody will ever read the
 * result of.
 */
export function useUnreadNotifications() {
  const { user } = useAuth();
  const [unread, setUnread] = useState(0);

  const refresh = useCallback(async () => {
    if (!user) return;
    try {
      const result = await notificationsApi.unreadCount();
      setUnread(result.unread);
    } catch {
      // A failed badge refresh is not worth telling anyone about. The count
      // simply stays where it was until the next tick succeeds.
    }
  }, [user]);

  useEffect(() => {
    if (!user) {
      setUnread(0);
      return;
    }

    refresh();
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') refresh();
    }, POLL_INTERVAL_MS);

    // Catch up immediately when the tab comes back, rather than making somebody
    // wait out the rest of an interval that ran while they were away.
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [user, refresh]);

  return { unread, refresh };
}
