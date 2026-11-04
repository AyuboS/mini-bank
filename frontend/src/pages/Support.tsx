import { useState } from 'react';
import { LifeBuoy, MessageSquare, Plus, Send } from 'lucide-react';
import { supportApi } from '../api/support';
import type { SupportCategory } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import {
  Button,
  Input,
  Select,
  Sheet,
  Skeleton,
  Surface,
  StatusBadge,
} from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState } from '../components/States';
import { formatDateTime, relativeTime } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

const CATEGORIES: { value: SupportCategory; label: string }[] = [
  { value: 'CARD', label: 'A card' },
  { value: 'TRANSFER', label: 'A payment' },
  { value: 'ACCOUNT', label: 'My account' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'APP', label: 'The app itself' },
  { value: 'OTHER', label: 'Something else' },
];

/** Answers to the questions that do not need a person. */
const FAQ = [
  {
    q: 'How do I freeze a card?',
    a: 'Open Cards, choose the card, and press Freeze. It stops immediately and nothing is charged while it is frozen. Unfreezing is just as quick.',
  },
  {
    q: 'Why was my payment held for review?',
    a: 'MBank checks every payment against a set of risk rules — an unusually large amount, a burst of payments, a payee added minutes earlier. A held payment has not left your account, and the reason is shown on the transaction.',
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

/**
 * Support.
 *
 * A ticket goes to a human queue. Nothing here generates a reply, and the
 * interface does not pretend otherwise: an automated "we understand your
 * concern" would be worse than silence, because it would make somebody with an
 * urgent problem believe they had been heard.
 */
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
                <span className="grow stack" style={{ gap: 2, minWidth: 0 }}>
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

function TicketSheet({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
  const toast = useToast();
  const [subject, setSubject] = useState('');
  const [category, setCategory] = useState<SupportCategory>('CARD');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      const ticket = await supportApi.open({ subject: subject.trim(), category, description: description.trim() });
      toast.success('Ticket opened', `Your reference is ${ticket.reference}.`);
      onCreated();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet open={open} onClose={onClose} title="Open a ticket" subtitle="A person will read this and reply.">
      <form className="stack-lg" onSubmit={submit}>
        <Input
          label="Subject"
          value={subject}
          onChange={(event) => setSubject(event.target.value)}
          placeholder="My card was declined at a shop"
          maxLength={140}
          required
        />

        <Select
          label="What is it about"
          value={category}
          onChange={(event) => setCategory(event.target.value as SupportCategory)}
        >
          {CATEGORIES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>

        <div className="field">
          <label className="field__label" htmlFor="ticket-description">
            What happened
          </label>
          <div className="field__well field__well--area well">
            <textarea
              id="ticket-description"
              className="field__input"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Include the date, the amount and anything you saw on screen."
              maxLength={4000}
              required
            />
          </div>
          <p className="field__hint">Never include your password or a card's security code.</p>
        </div>

        <div className="modal-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving}>
            Open ticket
          </Button>
        </div>
      </form>
    </Sheet>
  );
}

/* ---------------------------------------------------------------------------- */

function TicketThread({
  ticketId,
  onClose,
  onChanged,
}: {
  ticketId: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const toast = useToast();
  const ticket = useAsync(() => supportApi.get(ticketId), [ticketId]);
  const [reply, setReply] = useState('');
  const [sending, setSending] = useState(false);

  const send = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!reply.trim()) return;
    setSending(true);
    try {
      await supportApi.reply(ticketId, reply.trim());
      setReply('');
      ticket.reload();
      onChanged();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setSending(false);
    }
  };

  const close = async () => {
    try {
      await supportApi.close(ticketId);
      toast.success('Ticket closed');
      onChanged();
      onClose();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    }
  };

  const closed = ticket.data?.status === 'CLOSED';

  return (
    <Sheet
      open
      onClose={onClose}
      wide
      title={ticket.data?.subject ?? 'Ticket'}
      subtitle={ticket.data ? `${ticket.data.reference} · opened ${formatDateTime(ticket.data.createdAt)}` : undefined}
    >
      {ticket.loading ? (
        <Skeleton height="200px" />
      ) : (
        <div className="stack-lg">
          <div className="thread">
            {ticket.data?.messages?.map((message) => (
              <div
                className={`thread__message ${message.fromStaff ? 'thread__message--staff' : 'thread__message--mine'}`}
                key={message.id}
              >
                <span className="thread__author">{message.authorName}</span>
                <p className="thread__body">{message.body}</p>
                <span className="thread__time dim">{formatDateTime(message.createdAt)}</span>
              </div>
            ))}
          </div>

          {closed ? (
            <p className="dim">This ticket is closed. Open a new one to continue.</p>
          ) : (
            <form className="stack" onSubmit={send}>
              <div className="field">
                <label className="sr-only" htmlFor="ticket-reply">
                  Your reply
                </label>
                <div className="field__well field__well--area well">
                  <textarea
                    id="ticket-reply"
                    className="field__input"
                    value={reply}
                    onChange={(event) => setReply(event.target.value)}
                    placeholder="Add to this ticket…"
                    maxLength={4000}
                  />
                </div>
              </div>

              <div className="row-between">
                <Button variant="ghost" type="button" onClick={close}>
                  Close ticket
                </Button>
                <Button variant="primary" type="submit" icon={<Send />} loading={sending} disabled={!reply.trim()}>
                  Send
                </Button>
              </div>
            </form>
          )}
        </div>
      )}
    </Sheet>
  );
}
