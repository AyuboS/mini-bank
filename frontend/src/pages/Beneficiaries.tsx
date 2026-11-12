import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, Trash2, UserPlus, Users } from 'lucide-react';
import { beneficiariesApi } from '../api/beneficiaries';
import type { BeneficiaryResponse } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import { EmptyState, ErrorState } from '../components/States';
import {
  Avatar,
  Button,
  Surface,
  Input,
  Sheet,
  Skeleton,
} from '../components/ui';
import { initials, toneFor } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

export function BeneficiariesPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const { data, loading, error, reload } = useAsync<BeneficiaryResponse[]>(() => beneficiariesApi.list());

  const [addOpen, setAddOpen] = useState(false);
  const [accountNumber, setAccountNumber] = useState('');
  const [nickname, setNickname] = useState('');
  const [busy, setBusy] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [pendingRemoval, setPendingRemoval] = useState<BeneficiaryResponse | null>(null);

  const add = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setFieldError(null);
    try {
      const created = await beneficiariesApi.create(accountNumber.trim(), nickname.trim());
      toast.success('Payee saved', created.holderName ? `${created.nickname} · ${created.holderName}` : created.nickname);
      setAddOpen(false);
      setAccountNumber('');
      setNickname('');
      await reload();
    } catch (err) {
      const friendly = toFriendlyError(err);
      setFieldError(friendly.body ?? friendly.title);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!pendingRemoval) return;
    try {
      await beneficiariesApi.remove(pendingRemoval.id);
      toast.success('Payee removed');
      setPendingRemoval(null);
      await reload();
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    }
  };

  return (
    <>
      <div className="page__head spread">
        <div>
          <h1 className="page__title">Payees</h1>
          <p className="page__subtitle">Accounts you pay regularly.</p>
        </div>
        <Button variant="secondary" icon={<UserPlus aria-hidden="true" />} onClick={() => setAddOpen(true)}>
          Add payee
        </Button>
      </div>

      {loading && (
        <div className="payee-grid">
          <Skeleton height={168} />
          <Skeleton height={168} />
        </div>
      )}

      {!loading && (error || !data) && (
        <ErrorState body={error?.body ?? 'We could not load your payees.'} onRetry={reload} />
      )}

      {!loading && data && data.length === 0 && (
        <Surface>
          <EmptyState
            title="No payees saved"
            body="Save an account number once and paying it becomes a single tap."
            icon={<Users aria-hidden="true" />}
            action={
              <Button variant="secondary" onClick={() => setAddOpen(true)}>
                Add your first payee
              </Button>
            }
          />
        </Surface>
      )}

      {!loading && data && data.length > 0 && (
        <div className="payee-grid">
          {data.map((payee) => (
            <Surface key={payee.id} interactive lift className="payee-card">
              {/*
                Who, then what you can do — two rows rather than one.

                Everything on one line meant the name competed with two buttons
                and a monospaced account number for a 145px column, and lost:
                every payee rendered as "John Sm…". It also made the card's
                min-content width exceed its grid track, which pushed the whole
                page sideways at 320px.
              */}
              <div className="payee-card__who">
                <Avatar
                  initials={initials(payee.holderName?.split(' ')[0], payee.holderName?.split(' ')[1])}
                  tone={toneFor(payee.accountNumber)}
                />
                <div className="grow" style={{ minWidth: 0 }}>
                  <div className="payee-card__name truncate">{payee.nickname}</div>
                  {payee.holderName && <div className="small dim truncate">{payee.holderName}</div>}
                  <div className="payee-card__number truncate">{payee.accountNumber}</div>
                </div>
              </div>

              <div className="payee-card__actions">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => navigate('/payments/send')}
                  icon={<ArrowRight aria-hidden="true" />}
                >
                  Transfer
                </Button>
                {/* Icon-only, so square: two full-width buttons beside an
                    avatar and a name do not fit a 320px card. */}
                <Button
                  size="sm"
                  variant="danger"
                  className="btn-icon"
                  icon={<Trash2 aria-hidden="true" />}
                  onClick={() => setPendingRemoval(payee)}
                  aria-label={`Remove ${payee.nickname}`}
                />
              </div>
            </Surface>
          ))}
        </div>
      )}

      <Sheet
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Add a payee"
        subtitle="We check the account exists before saving it."
      >
        <form onSubmit={add}>
          <div className="stack gap-4">
            <Input
              label="Account number"
              placeholder="MB-000000000000"
              value={accountNumber}
              onChange={(event) => setAccountNumber(event.target.value.toUpperCase())}
              spellCheck={false}
              autoComplete="off"
              required
              {...(fieldError ? { error: fieldError } : {})}
            />
            <Input
              label="Name"
              placeholder="Landlord"
              maxLength={60}
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              required
            />
          </div>

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => setAddOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={busy}>
              Save payee
            </Button>
          </div>
        </form>
      </Sheet>

      <Sheet
        open={pendingRemoval !== null}
        onClose={() => setPendingRemoval(null)}
        title="Remove this payee?"
        subtitle={`${pendingRemoval?.nickname ?? ''} · ${pendingRemoval?.accountNumber ?? ''}`}
      >
        <p className="small dim">
          Past transfers to this account are unaffected. You can add them again at any time.
        </p>
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setPendingRemoval(null)}>
            Keep
          </Button>
          <Button variant="danger" onClick={remove}>
            Remove
          </Button>
        </div>
      </Sheet>
    </>
  );
}
