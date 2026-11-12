import { useState } from 'react';
import { AlertTriangle, Bell, CheckCheck, CreditCard, Info, PiggyBank, Shield, TrendingUp } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { notificationsApi } from '../api/notifications';
import type { NotificationResponse, NotificationType } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import { Button, Tabs, Surface, SkeletonRows } from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState } from '../components/States';
import { relativeTime } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

const TABS = [
  { id: 'ALL', label: 'Everything' },
  { id: 'TRANSACTION', label: 'Money' },
  { id: 'SECURITY', label: 'Security' },
  { id: 'CARD', label: 'Cards' },
  { id: 'BUDGET', label: 'Budgets' },
];

const ICONS: Record<NotificationType, typeof Bell> = {
  TRANSACTION: TrendingUp,
  SECURITY: Shield,
  CARD: CreditCard,
  BUDGET: PiggyBank,
  SYSTEM: Info,
};

/**
 * The notification feed.
 *
 * Entries are written by the same transaction that performed the underlying
 * action, so a notification cannot exist for a payment that was rolled back —
 * and a settled payment cannot silently produce none.
 */
export function NotificationsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [filter, setFilter] = useState('ALL');
  const feed = useAsync(
    () => notificationsApi.list({ ...(filter === 'ALL' ? {} : { type: filter as NotificationType }), size: 50 }),
    [filter],
  );

  const unread = (feed.data?.content ?? []).filter((entry) => !entry.read).length;

  const markAll = async () => {
    try {
      const result = await notificationsApi.markAllRead();
      toast.success(result.marked === 0 ? 'Nothing left to mark' : `${result.marked} marked as read`);
      feed.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  /** Opening an entry marks it read and follows it to whatever it is about. */
  const open = async (entry: NotificationResponse) => {
    if (!entry.read) {
      try {
        await notificationsApi.markRead(entry.id);
        feed.setData((current) =>
          current
            ? {
                ...current,
                content: current.content.map((item) =>
                  item.id === entry.id ? { ...item, read: true } : item,
                ),
              }
            : current,
        );
      } catch {
        // Failing to mark one entry read is not worth interrupting the
        // navigation the customer actually asked for.
      }
    }

    const destination = destinationFor(entry);
    if (destination) navigate(destination);
  };

  return (
    <div className="stack-lg">
      <PageHeader
        title="Notifications"
        actions={
          unread > 0 ? (
            <Button variant="secondary" icon={<CheckCheck />} onClick={markAll}>
              Mark all read
            </Button>
          ) : undefined
        }
      />

      <Tabs label="Notification type" tabs={TABS} active={filter} onChange={setFilter} />

      <Surface className="list-panel">
        {feed.loading ? (
          <SkeletonRows rows={6} />
        ) : feed.error ? (
          <ErrorState title={feed.error.title} body={feed.error.body} onRetry={feed.reload} />
        ) : (feed.data?.content.length ?? 0) === 0 ? (
          <EmptyState
            title="Nothing here"
            body={
              filter === 'ALL'
                ? 'Payments, security events and budget alerts will appear here.'
                : 'Nothing of this kind yet.'
            }
            icon={<Bell />}
          />
        ) : (
          feed.data?.content.map((entry) => {
            const Icon = ICONS[entry.type] ?? Info;
            return (
              <button
                type="button"
                key={entry.id}
                className={`notice ${entry.read ? '' : 'notice--unread'}`}
                onClick={() => open(entry)}
              >
                <span className={`notice__icon notice__icon--${entry.severity.toLowerCase()}`} aria-hidden="true">
                  {entry.severity === 'CRITICAL' || entry.severity === 'WARNING' ? <AlertTriangle /> : <Icon />}
                </span>

                <span className="notice__body">
                  <span className="notice__title">{entry.title}</span>
                  <span className="notice__text">{entry.body}</span>
                  <span className="notice__time dim">{relativeTime(entry.createdAt)}</span>
                </span>

                {/* The unread marker is a dot, and the state is also announced,
                    because a dot alone is invisible to a screen reader. */}
                {!entry.read && (
                  <>
                    <span className="notice__dot" aria-hidden="true" />
                    <span className="sr-only">Unread</span>
                  </>
                )}
              </button>
            );
          })
        )}
      </Surface>
    </div>
  );
}

/**
 * Where a notification leads.
 *
 * Returns undefined rather than a guess for anything it does not recognise: a
 * notification that navigates somewhere unrelated is worse than one that only
 * marks itself read.
 */
function destinationFor(entry: NotificationResponse): string | undefined {
  switch (entry.entityType) {
    case 'Transfer':
      return '/transactions';
    case 'SavingsGoal':
      return '/savings';
    case 'Budget':
      return '/analytics';
    case 'ScheduledTransfer':
      return '/payments/scheduled';
    case 'MoneyRequest':
      return '/payments/requests';
    case 'Card':
      return '/cards';
    case 'User':
      return '/security';
    default:
      return undefined;
  }
}
