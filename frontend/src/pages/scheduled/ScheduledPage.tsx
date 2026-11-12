import { useState } from 'react';
import { CalendarClock, Pause, Play, Plus, Repeat, X } from 'lucide-react';
import { scheduledApi } from '../../api/scheduled';
import { accountsApi } from '../../api/accounts';
import { beneficiariesApi } from '../../api/beneficiaries';
import type { ScheduleFrequency } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { useToast } from '../../components/ToastProvider';
import { Button, Skeleton, Surface, StatusBadge } from '../../components/ui';
import { PageHeader } from '../../components/PageHeader';
import { CategoryIcon } from '../../components/CategoryIcon';
import { EmptyState, ErrorState } from '../../components/States';
import { formatDay, formatMoney, maskAccountNumber } from '../../utils/format';
import { toFriendlyError } from '../../utils/errors';
import { ScheduleSheet } from './ScheduleSheet';

const FREQUENCY_LABEL: Record<ScheduleFrequency, string> = {
  ONCE: 'Once',
  WEEKLY: 'Every week',
  MONTHLY: 'Every month',
};

/**
 * Standing orders: scheduled and recurring payments.
 *
 * These execute for real. A background job puts each due order through the same
 * transfer path a manual payment uses, so limits, risk checks and the ledger all
 * apply — and a payment that fails is recorded and shown here rather than
 * retried silently.
 */
export function ScheduledPage() {
  const toast = useToast();
  const orders = useAsync(() => scheduledApi.list(), []);
  const accounts = useAsync(() => accountsApi.list(), []);
  const payees = useAsync(() => beneficiariesApi.list(), []);
  const [composing, setComposing] = useState(false);

  const live = (orders.data ?? []).filter((order) => order.status === 'SCHEDULED' || order.status === 'PAUSED');
  const finished = (orders.data ?? []).filter((order) => order.status === 'COMPLETED' || order.status === 'CANCELLED');

  const act = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action();
      toast.success(message);
      orders.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <div className="stack-lg">
      <PageHeader
        title="Scheduled payments"
        subtitle="Rent, subscriptions, anything that repeats. These run automatically on their due date."
        actions={
          <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
            New payment
          </Button>
        }
      />

      {orders.loading ? (
        <Skeleton height="200px" />
      ) : orders.error ? (
        <ErrorState title={orders.error.title} body={orders.error.body} onRetry={orders.reload} />
      ) : live.length === 0 ? (
        <Surface>
          <EmptyState
            title="Nothing scheduled"
            body="Set up a payment that runs on a date you choose, once or every month."
            icon={<CalendarClock />}
            action={
              <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
                Schedule a payment
              </Button>
            }
          />
        </Surface>
      ) : (
        <div className="stack">
          {live.map((order) => (
            <Surface className="order-card" key={order.id}>
              <div className="order-card__main">
                <CategoryIcon category={order.category} />

                <div className="grow stack gap-tight">
                  <span className="order-card__name truncate">
                    {order.description || order.payeeName || 'Scheduled payment'}
                  </span>
                  <span className="dim order-card__meta truncate inline-icon">
                    {order.payeeName ?? maskAccountNumber(order.toAccountNumber)} ·{' '}
                    <Repeat size={12} aria-hidden="true" />{' '}
                    {FREQUENCY_LABEL[order.frequency]}
                  </span>
                </div>

                <div className="stack order-card__right gap-tight">
                  <span className="amount">{formatMoney(order.amount, order.currency)}</span>
                  <StatusBadge status={order.status} />
                </div>
              </div>

              <div className="order-card__foot">
                <span className="dim">
                  {order.status === 'PAUSED' ? 'Paused' : `Next on ${formatDay(order.nextRunOn)}`}
                  {order.runsCompleted > 0 && ` · ${order.runsCompleted} sent so far`}
                </span>

                <div className="row gap-1">
                  {order.status === 'SCHEDULED' ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={<Pause />}
                      onClick={() => act(() => scheduledApi.pause(order.id), 'Payment paused')}
                    >
                      Pause
                    </Button>
                  ) : (
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={<Play />}
                      onClick={() => act(() => scheduledApi.resume(order.id), 'Payment resumed')}
                    >
                      Resume
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="ghost"
                    icon={<X />}
                    onClick={() => act(() => scheduledApi.cancel(order.id), 'Payment cancelled')}
                  >
                    Cancel
                  </Button>
                </div>
              </div>

              {/*
                A failed run is surfaced, not swallowed. The customer needs to
                know their rent did not go out, and the schedule has already
                moved on to its next date rather than retrying all day.
              */}
              {order.lastFailureReason && (
                <p className="order-card__failure" role="status">
                  Last attempt did not go through: {order.lastFailureReason}
                </p>
              )}
            </Surface>
          ))}
        </div>
      )}

      {finished.length > 0 && (
        <section className="stack">
          <h2>Finished</h2>
          <Surface className="list-panel">
            {finished.map((order) => (
              <div className="row-between order-card__done" key={order.id}>
                <span className="dim truncate">{order.description || order.payeeName || 'Payment'}</span>
                <span className="row gap-3">
                  <span className="amount dim">{formatMoney(order.amount, order.currency)}</span>
                  <StatusBadge status={order.status} />
                </span>
              </div>
            ))}
          </Surface>
        </section>
      )}

      <ScheduleSheet
        open={composing}
        accounts={accounts.data ?? []}
        payees={payees.data ?? []}
        onClose={() => setComposing(false)}
        onCreated={() => {
          setComposing(false);
          orders.reload();
        }}
      />
    </div>
  );
}

/* ---------------------------------------------------------------------------- */
