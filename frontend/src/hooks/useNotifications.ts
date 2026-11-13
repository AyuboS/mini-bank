import { useCallback, useEffect, useState } from 'react';
import { notificationsApi } from '../api/notifications';
import { useAuth } from '../auth/AuthContext';

/** How often the badge is refreshed. */
const POLL_INTERVAL_MS = 60_000;

/** The unread notification count, for the badge in the chrome. */
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
