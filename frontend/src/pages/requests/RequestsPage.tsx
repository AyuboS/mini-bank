import { useState } from 'react';
import { HandCoins, Plus, Split } from 'lucide-react';
import { moneyRequestsApi } from '../../api/moneyRequests';
import { accountsApi } from '../../api/accounts';
import { useAsync } from '../../hooks/useAsync';
import { Button, Skeleton, Tabs, Surface } from '../../components/ui';
import { PageHeader } from '../../components/PageHeader';
import { SplitBillSheet } from '../../components/SplitBillSheet';
import { EmptyState, ErrorState } from '../../components/States';
import { RequestCard } from './RequestCard';
import { RequestSheet } from './RequestSheet';

/**
 * Payment requests.
 *
 * The screen is split by direction because the two sides can do entirely
 * different things: a request you received can be paid or declined, and one you
 * sent can only be withdrawn. Showing both in one list would mean showing
 * controls that do nothing for half the rows.
 */
export function RequestsPage() {
  const [tab, setTab] = useState<'incoming' | 'outgoing'>('incoming');
  const [composing, setComposing] = useState(false);
  const [splitting, setSplitting] = useState(false);

  const incoming = useAsync(() => moneyRequestsApi.incoming(), []);
  const outgoing = useAsync(() => moneyRequestsApi.outgoing(), []);
  const accounts = useAsync(() => accountsApi.list(), []);

  const pendingIncoming = (incoming.data ?? []).filter((request) => request.status === 'PENDING');
  const active = tab === 'incoming' ? incoming : outgoing;

  return (
    <div className="stack-lg">
      <PageHeader
        title="Requests"
        subtitle="Ask to be paid. A request never moves money on its own — only the payer can settle it."
        actions={
          <>
            <Button variant="ghost" icon={<Split />} onClick={() => setSplitting(true)}>
              Split a bill
            </Button>
            <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
              Request money
            </Button>
          </>
        }
      />

      <Tabs
        label="Request direction"
        active={tab}
        onChange={(id) => setTab(id as 'incoming' | 'outgoing')}
        tabs={[
          { id: 'incoming', label: 'Asked of you', count: pendingIncoming.length },
          { id: 'outgoing', label: 'You asked' },
        ]}
      />

      {active.loading ? (
        <Skeleton height="200px" />
      ) : active.error ? (
        <ErrorState title={active.error.title} body={active.error.body} onRetry={active.reload} />
      ) : (active.data?.length ?? 0) === 0 ? (
        <Surface>
          <EmptyState
            title={tab === 'incoming' ? 'Nobody has asked you for money' : 'You have not asked anyone'}
            body={
              tab === 'incoming'
                ? 'Requests sent to your accounts will appear here.'
                : 'Split a bill, or remind somebody what they owe you.'
            }
            icon={<HandCoins />}
            action={
              tab === 'outgoing' ? (
                <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
                  Request money
                </Button>
              ) : undefined
            }
          />
        </Surface>
      ) : (
        <div className="stack">
          {active.data?.map((request) => (
            <RequestCard
              key={request.id}
              request={request}
              accounts={accounts.data ?? []}
              onChanged={() => {
                incoming.reload();
                outgoing.reload();
                accounts.reload();
              }}
            />
          ))}
        </div>
      )}

      <RequestSheet
        open={composing}
        accounts={accounts.data ?? []}
        onClose={() => setComposing(false)}
        onCreated={() => {
          setComposing(false);
          outgoing.reload();
          setTab('outgoing');
        }}
      />

      <SplitBillSheet
        open={splitting}
        accounts={accounts.data ?? []}
        onClose={() => setSplitting(false)}
        onCreated={() => {
          setSplitting(false);
          outgoing.reload();
          setTab('outgoing');
        }}
      />
    </div>
  );
}

/* ---------------------------------------------------------------------------- */
