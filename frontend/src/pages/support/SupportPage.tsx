import { useState } from 'react';
import { LifeBuoy, MessageSquare, Plus } from 'lucide-react';
import { supportApi } from '../../api/support';
import { useAsync } from '../../hooks/useAsync';
import { Button, Skeleton, Surface, StatusBadge } from '../../components/ui';
import { PageHeader } from '../../components/PageHeader';
import { EmptyState, ErrorState } from '../../components/States';
import { relativeTime } from '../../utils/format';
import { TicketSheet } from './TicketSheet';
import { TicketThread } from './TicketThread';

/** Answers to the questions that do not need a person. */
const FAQ = [
  {
    q: 'How do I freeze a card?',
    a: 'Open Cards, choose the card, and press Freeze. It stops immediately and nothing is charged while it is frozen. Unfreezing is just as quick.',
  },
  {
    q: 'Why was my payment held for review?',
    a: 'MBank checks every payment against a set of risk rules — an unusually large amount, a burst of payments, a payee added minutes earlier. A held payment has not left your account. Someone at MBank reviews it, and it is either sent or returned in full.',
  },
  {
    q: 'Can I get my money back out of a savings goal?',
    a: 'At any time. A goal is a name over a real account you own; withdrawing moves the money back to your spending account immediately.',
  },
  {
    q: 'What happens if a scheduled payment fails?',
    a: 'It is recorded against the payment with the reason, you are notified, and the schedule moves on to its next date. It will not retry repeatedly against an empty account.',
  },
  {
    q: 'Someone signed in that was not me. What do I do?',
    a: 'Go to Security, end that session, and change your password. Changing it signs out every device.',
  },
];

/** Support. */
export function SupportPage() {
  const tickets = useAsync(() => supportApi.list(), []);
  const [composing, setComposing] = useState(false);
  const [openTicket, setOpenTicket] = useState<string | null>(null);

  return (
    <div className="stack-lg">
      <PageHeader
        title="Support"
        subtitle="Answers to the common questions, and a way to reach a person for everything else."
        actions={
          <Button variant="primary" icon={<Plus />} onClick={() => setComposing(true)}>
            New ticket
          </Button>
        }
      />

      <section className="stack">
        <h2>Common questions</h2>
        <Surface className="panel">
          {FAQ.map((entry) => (
            /* A native <details>: it is keyboard accessible, announced correctly,
               and works before any JavaScript has run. */
            <details className="faq" key={entry.q}>
              <summary className="faq__q">{entry.q}</summary>
              <p className="faq__a">{entry.a}</p>
            </details>
          ))}
        </Surface>
      </section>

      <section className="stack">
        <h2>Your tickets</h2>
        {tickets.loading ? (
          <Skeleton height="120px" />
        ) : tickets.error ? (
          <ErrorState title={tickets.error.title} body={tickets.error.body} onRetry={tickets.reload} />
        ) : (tickets.data?.length ?? 0) === 0 ? (
          <Surface>
            <EmptyState
              title="No tickets open"
              body="If none of the answers above help, open a ticket and somebody will reply."
              icon={<LifeBuoy />}
              action={
                <Button variant="secondary" icon={<Plus />} onClick={() => setComposing(true)}>
                  Open a ticket
                </Button>
              }
            />
          </Surface>
        ) : (
          <Surface className="list-panel">
            {tickets.data?.map((ticket) => (
              <button
                type="button"
                className="ticket-row"
                key={ticket.id}
                onClick={() => setOpenTicket(ticket.id)}
              >
                <span className="ticket-row__icon" aria-hidden="true">
                  <MessageSquare />
                </span>
                <span className="grow stack gap-tight min-w-0">
                  <span className="ticket-row__subject truncate">{ticket.subject}</span>
                  <span className="dim ticket-row__meta">
                    {ticket.reference} · {relativeTime(ticket.updatedAt)}
                  </span>
                </span>
                <StatusBadge status={ticket.status} />
              </button>
            ))}
          </Surface>
        )}
      </section>

      {/*
        Reminder rather than nagging: shown once, on the screen where somebody is
        about to describe a problem, which is exactly where a phishing attempt
        would ask for a code.
      */}
      <p className="security-note">
        For your security, never include your password or a card's security code in a ticket. MBank will
        never ask for them.
      </p>

      <TicketSheet
        open={composing}
        onClose={() => setComposing(false)}
        onCreated={() => {
          setComposing(false);
          tickets.reload();
        }}
      />

      {openTicket && (
        <TicketThread
          ticketId={openTicket}
          onClose={() => setOpenTicket(null)}
          onChanged={() => tickets.reload()}
        />
      )}
    </div>
  );
}

/* ---------------------------------------------------------------------------- */
