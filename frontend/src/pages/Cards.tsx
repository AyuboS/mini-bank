import { useCallback, useMemo, useState } from 'react';
import {
  CreditCard,
  Eye,
  Gauge,
  Palette,
  Plus,
  Receipt,
  SlidersHorizontal,
  Snowflake,
  Sun,
  Trash2,
} from 'lucide-react';
import { accountsApi } from '../api/accounts';
import { cardsApi } from '../api/cards';
import type { AccountResponse, CardDesign, CardResponse, TransactionResponse } from '../api/types';
import { useAsync } from '../hooks/useAsync';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../components/ToastProvider';
import { BankCard, formatExpiry, toPresentation } from '../components/card/BankCard';
import { CardCarousel } from '../components/card/CardCarousel';
import { CardCreateModal } from '../components/card/CardCreateModal';
import { CardDetailsModal } from '../components/card/CardDetailsModal';
import { CardControlsSheet } from '../components/card/CardControlsSheet';
import { CardLimitsModal } from '../components/card/CardLimitsModal';
import { CARD_RANGE, CARD_RANGE_ORDER, cardName, materialLabel, materialOf } from '../components/card/range';
import { EmptyState, ErrorState } from '../components/States';
import { TransactionRow } from '../components/TransactionRow';
import { Button, Surface, Input, Sheet, Skeleton } from '../components/ui';
import { toFriendlyError } from '../utils/errors';

interface CardsData {
  cards: CardResponse[];
  accounts: AccountResponse[];
}

/**
 * The wallet.
 *
 * The carousel is the page's subject rather than an ornament on it: the card you
 * are looking at is the card every control below acts on, so there is never a
 * separate "which card?" step. Selection follows the scroll, which means a swipe
 * on a phone and a drag on a desktop both do the obvious thing.
 */
export function CardsPage() {
  const { user } = useAuth();
  const toast = useToast();

  const [activeIndex, setActiveIndex] = useState(0);
  const [creating, setCreating] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [limitsOpen, setLimitsOpen] = useState(false);
  const [controlsOpen, setControlsOpen] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [restyling, setRestyling] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [draftLabel, setDraftLabel] = useState('');
  const [busy, setBusy] = useState(false);

  const { data, loading, error, reload, setData } = useAsync<CardsData>(async () => {
    const [cards, accounts] = await Promise.all([cardsApi.list(), accountsApi.list()]);
    return { cards, accounts };
  });

  const cards = useMemo(() => data?.cards ?? [], [data]);
  const active = cards[Math.min(activeIndex, cards.length - 1)] ?? null;

  // Card transactions are loaded per selection rather than up front: a wallet of
  // eight cards would otherwise mean eight history requests nobody asked for.
  const { data: activity } = useAsync<TransactionResponse[]>(
    async () => {
      if (!active) return [];
      const page = await cardsApi.transactions(active.id, { size: 5 });
      return page.content;
    },
    [active?.id],
  );

  /** Replaces one card in place, so the carousel does not jump back to the start. */
  const replaceCard = useCallback(
    (updated: CardResponse) => {
      setData((current) =>
        current
          ? { ...current, cards: current.cards.map((card) => (card.id === updated.id ? updated : card)) }
          : current,
      );
    },
    [setData],
  );

  const run = async (action: () => Promise<void>, failureContext: string) => {
    setBusy(true);
    try {
      await action();
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body ?? failureContext);
    } finally {
      setBusy(false);
    }
  };

  const toggleFreeze = () =>
    active &&
    run(async () => {
      const updated =
        active.status === 'FROZEN' ? await cardsApi.unfreeze(active.id) : await cardsApi.freeze(active.id);
      replaceCard(updated);
      toast.success(
        updated.status === 'FROZEN' ? 'Card frozen' : 'Card unfrozen',
        updated.status === 'FROZEN'
          ? 'It cannot be used until you unfreeze it.'
          : 'The card works again.',
      );
    }, 'The card could not be updated.');

  const rename = () =>
    active &&
    run(async () => {
      replaceCard(await cardsApi.update(active.id, { label: draftLabel.trim() }));
      setRenaming(false);
      toast.success('Card renamed');
    }, 'The card could not be renamed.');

  const restyle = (design: CardDesign) =>
    active &&
    run(async () => {
      replaceCard(await cardsApi.update(active.id, { design }));
      setRestyling(false);
      toast.success('Design changed');
    }, 'The design could not be changed.');

  const cancelCard = () =>
    active &&
    run(async () => {
      await cardsApi.cancel(active.id);
      setData((current) =>
        current ? { ...current, cards: current.cards.filter((card) => card.id !== active.id) } : current,
      );
      setActiveIndex(0);
      setCancelling(false);
      toast.success('Card cancelled', 'It has been removed from your wallet.');
    }, 'The card could not be cancelled.');

  if (loading) {
    return (
      <div className="stack gap-5">
        <Skeleton height={38} width={220} />
        <Skeleton height={240} />
        <Skeleton height={160} />
      </div>
    );
  }

  if (error || !data) {
    return <ErrorState body={error?.body ?? 'We could not load your cards.'} onRetry={reload} />;
  }

  const frozen = active?.status === 'FROZEN';
  const actionable = active !== null && (active.status === 'ACTIVE' || active.status === 'FROZEN');

  return (
    <>
      <div className="page__head spread">
        <h1 className="page__title">Your cards</h1>
        <Button variant="primary" icon={<Plus aria-hidden="true" />} onClick={() => setCreating(true)}>
          Add card
        </Button>
      </div>

      {cards.length === 0 ? (
        <Surface>
          <EmptyState
            title="No cards yet"
            body="Create a virtual card for online payments, or order one for your wallet."
            icon={<CreditCard aria-hidden="true" />}
            action={
              <Button variant="primary" icon={<Plus aria-hidden="true" />} onClick={() => setCreating(true)}>
                Create your first card
              </Button>
            }
          />
        </Surface>
      ) : (
        <>
          <CardCarousel
            ariaLabel="Your cards"
            onActiveChange={setActiveIndex}
            items={cards.map((card) => ({
              key: card.id,
              node: <BankCard card={toPresentation(card)} size="md" interactive />,
            }))}
          />

          {active && (
            <>
              <Surface className="card-detail">
                <div className="card-detail__head">
                  <div className="stack gap-1">
                    <h2 className="card-detail__title">{active.label}</h2>
                    <p className="small muted">
                      {cardName(active.design)} · {materialLabel(materialOf(active.design, active.type))} ·
                      •••• {active.lastFour} · expires{' '}
                      {formatExpiry(active.expirationMonth, active.expirationYear)}
                    </p>
                  </div>
                  <span className={`status-chip status-chip--${active.status.toLowerCase()}`}>
                    {active.status.charAt(0) + active.status.slice(1).toLowerCase()}
                  </span>
                </div>

                <div className="card-actions">
                  <button
                    type="button"
                    className="card-action"
                    onClick={toggleFreeze}
                    disabled={busy || !actionable}
                  >
                    <span className="card-action__icon">
                      {frozen ? <Sun aria-hidden="true" /> : <Snowflake aria-hidden="true" />}
                    </span>
                    {frozen ? 'Unfreeze' : 'Freeze'}
                  </button>

                  <button
                    type="button"
                    className="card-action"
                    onClick={() => setDetailsOpen(true)}
                    disabled={!actionable}
                  >
                    <span className="card-action__icon">
                      <Eye aria-hidden="true" />
                    </span>
                    Details
                  </button>

                  <button
                    type="button"
                    className="card-action"
                    onClick={() => setControlsOpen(true)}
                    disabled={!actionable}
                  >
                    <span className="card-action__icon">
                      <SlidersHorizontal aria-hidden="true" />
                    </span>
                    Controls
                  </button>

                  <button
                    type="button"
                    className="card-action"
                    onClick={() => setLimitsOpen(true)}
                    disabled={!actionable}
                  >
                    <span className="card-action__icon">
                      <Gauge aria-hidden="true" />
                    </span>
                    Limits
                  </button>

                  <button
                    type="button"
                    className="card-action"
                    onClick={() => setRestyling(true)}
                    disabled={busy || !actionable}
                  >
                    <span className="card-action__icon">
                      <Palette aria-hidden="true" />
                    </span>
                    Customise
                  </button>

                  <button
                    type="button"
                    className="card-action"
                    onClick={() => {
                      setDraftLabel(active.label);
                      setRenaming(true);
                    }}
                    disabled={busy || !actionable}
                  >
                    <span className="card-action__icon">
                      <CreditCard aria-hidden="true" />
                    </span>
                    Rename
                  </button>

                  <button
                    type="button"
                    className="card-action card-action--danger"
                    onClick={() => setCancelling(true)}
                    disabled={busy}
                  >
                    <span className="card-action__icon">
                      <Trash2 aria-hidden="true" />
                    </span>
                    Cancel
                  </button>
                </div>

                {frozen && (
                  <p className="card-detail__note">
                    This card is frozen. Nothing can be spent on it until you unfreeze it — your
                    money and the card itself are untouched.
                  </p>
                )}
              </Surface>

              <section className="section">
                <div className="section__head">
                  <h3 className="section__title">Card activity</h3>
                </div>
                {/*
                  Named honestly. MBank has no card acquiring, so this is the
                  history of the account the card is attached to rather than a
                  separate stream of card authorisations, and the note says so
                  instead of implying a precision that does not exist.
                */}
                <p className="small muted" style={{ marginTop: 'calc(var(--space-3) * -1)', marginBottom: 'var(--space-4)' }}>
                  Movements on {active.accountNumber}, the account behind this card.
                </p>

                {!activity || activity.length === 0 ? (
                  <Surface>
                    <EmptyState
                      title="Nothing yet"
                      body="Transactions on this account will appear here."
                      icon={<Receipt aria-hidden="true" />}
                    />
                  </Surface>
                ) : (
                  <Surface className="list-panel">
                    {activity.map((txn) => (
                      <TransactionRow key={txn.transferId} transaction={txn} />
                    ))}
                  </Surface>
                )}
              </section>
            </>
          )}
        </>
      )}

      <CardCreateModal
        open={creating}
        onClose={() => setCreating(false)}
        accounts={data.accounts}
        cardholderName={`${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim()}
        onCreated={(card) => {
          setData((current) => (current ? { ...current, cards: [card, ...current.cards] } : current));
          setActiveIndex(0);
          setCreating(false);
          toast.success('Card created', `${card.label} is ready to use.`);
        }}
      />

      <CardDetailsModal open={detailsOpen} card={active} onClose={() => setDetailsOpen(false)} />

      {active && (
        <CardControlsSheet
          open={controlsOpen}
          card={active}
          onClose={() => setControlsOpen(false)}
          /* Swaps just this card in the loaded list, so the wallet does not
             refetch and snap back to the first card. */
          onUpdated={replaceCard}
        />
      )}

      <CardLimitsModal
        open={limitsOpen}
        card={active}
        onClose={() => setLimitsOpen(false)}
        onSaved={(updated) => {
          replaceCard(updated);
          toast.success('Limits updated');
        }}
      />

      <Sheet open={renaming} onClose={() => setRenaming(false)} title="Rename card">
        <Input
          label="Card name"
          value={draftLabel}
          onChange={(event) => setDraftLabel(event.target.value)}
          maxLength={50}
          autoFocus
        />
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setRenaming(false)}>
            Cancel
          </Button>
          <Button variant="primary" onClick={rename} loading={busy} disabled={!draftLabel.trim()}>
            Save
          </Button>
        </div>
      </Sheet>

      <Sheet
        open={restyling}
        onClose={() => setRestyling(false)}
        wide
        title="Customise card"
        subtitle="Your number, limits and history stay exactly as they are."
      >
        <div className="design-grid">
          {CARD_RANGE_ORDER.map((design) => (
            <button
              key={design}
              type="button"
              className={`design-swatch ${active?.design === design ? 'design-swatch--selected' : ''}`}
              onClick={() => restyle(design)}
              disabled={busy}
              aria-pressed={active?.design === design}
            >
              {active && <BankCard card={{ ...toPresentation(active), design }} size="sm" showStatus={false} />}
              <span className="design-swatch__name">{CARD_RANGE[design].name}</span>
            </button>
          ))}
        </div>
      </Sheet>

      <Sheet
        open={cancelling}
        onClose={() => setCancelling(false)}
        title="Cancel this card?"
        subtitle="This cannot be undone. If you only want to pause it, freeze it instead."
      >
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setCancelling(false)}>
            Keep card
          </Button>
          <Button variant="danger" onClick={cancelCard} loading={busy}>
            Cancel card
          </Button>
        </div>
      </Sheet>
    </>
  );
}
