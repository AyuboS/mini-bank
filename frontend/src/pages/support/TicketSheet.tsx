import { useState } from 'react';
import { supportApi } from '../../api/support';
import type { SupportCategory } from '../../api/types';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Select, Sheet } from '../../components/ui';
import { toFriendlyError } from '../../utils/errors';

const CATEGORIES: { value: SupportCategory; label: string }[] = [
  { value: 'CARD', label: 'A card' },
  { value: 'TRANSFER', label: 'A payment' },
  { value: 'ACCOUNT', label: 'My account' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'APP', label: 'The app itself' },
  { value: 'OTHER', label: 'Something else' },
];

export function TicketSheet({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: () => void }) {
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
