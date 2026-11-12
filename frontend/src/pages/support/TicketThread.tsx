import { useState } from 'react';
import { Send } from 'lucide-react';
import { supportApi } from '../../api/support';
import { useAsync } from '../../hooks/useAsync';
import { useToast } from '../../components/ToastProvider';
import { Button, Sheet, Skeleton } from '../../components/ui';
import { formatDateTime } from '../../utils/format';
import { toFriendlyError } from '../../utils/errors';

export function TicketThread({
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
