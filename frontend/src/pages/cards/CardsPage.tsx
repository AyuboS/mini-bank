import { useCallback, useMemo, useState } from 'react';
import { accountsApi } from '../../api/accounts';
import { cardsApi } from '../../api/cards';
import type { AccountResponse, CardDesign, CardResponse, TransactionResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { BankCard, formatExpiry, toPresentation } from '../../components/card/BankCard';
import { CardCarousel } from '../../components/card/CardCarousel';
import { CardControlsSheet } from '../../components/card/CardControlsSheet';
import { CardCreateModal } from '../../components/card/CardCreateModal';
import { CardDetailsModal } from '../../components/card/CardDetailsModal';
import { CardLimitsModal } from '../../components/card/CardLimitsModal';
import { CARD_RANGE, CARD_RANGE_ORDER, cardName, materialLabel, materialOf } from '../../components/card/range';
import { PageHeader } from '../../components/PageHeader';
import { ErrorState } from '../../components/States';
import { useToast } from '../../components/ToastProvider';
import { TransactionDetail } from '../../components/TransactionDetail';
import { TransactionList } from '../../components/TransactionList';
import { Button, Input, Sheet, Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { toFriendlyError } from '../../utils/errors';
import { CardSettings } from './CardSettings';

type Panel = 'details' | 'controls' | 'limits' | 'design' | 'rename' | 'cancel' | 'create' | null;

/** The wallet. The card in view is the one every setting below acts on. */
export function CardsPage() {
  const { user } = useAuth();
  const toast = useToast();

  const [activeIndex, setActiveIndex] = useState(0);
  const [panel, setPanel] = useState<Panel>(null);
  const [draftLabel, setDraftLabel] = useState('');
  const [busy, setBusy] = useState(false);
  const [selectedTxn, setSelectedTxn] = useState<TransactionResponse | null>(null);

  const { data, loading, error, reload, setData } = useAsync<{ cards: CardResponse[]; accounts: AccountResponse[] }>(
    async () => {
      const [cards, accounts] = await Promise.all([cardsApi.list(), accountsApi.list()]);
      return { cards, accounts };
    },
  );

  const cards = useMemo(() => data?.cards ?? [], [data]);
  const active = cards[Math.min(activeIndex, cards.length - 1)] ?? null;

  // Loaded for the card in view only, not for every card up front.
  const activity = useAsync<TransactionResponse[]>(
    async () => (active ? (await cardsApi.transactions(active.id, { size: 5 })).content : []),
    [active?.id],
  );

  // Swaps one card in place, so the carousel keeps its position.
  const replaceCard = useCallback(
    (updated: CardResponse) =>
      setData((current) =>
        current ? { ...current, cards: current.cards.map((card) => (card.id === updated.id ? updated : card)) } : current,
      ),
    [setData],
  );

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    try {
      await action();
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const toggleFreeze = () =>
    active &&
    run(async () => {
      const updated = active.status === 'FROZEN' ? await cardsApi.unfreeze(active.id) : await cardsApi.freeze(active.id);
      replaceCard(updated);
      toast.success(updated.status === 'FROZEN' ? 'Card frozen' : 'Card unfrozen');
    });

  const rename = () =>
    active &&
    run(async () => {
      replaceCard(await cardsApi.update(active.id, { label: draftLabel.trim() }));
      setPanel(null);
      toast.success('Card renamed');
    });

  const restyle = (design: CardDesign) =>
    active &&
    run(async () => {
      replaceCard(await cardsApi.update(active.id, { design }));
      setPanel(null);
      toast.success('Design changed');
    });

  const cancelCard = () =>
    active &&
    run(async () => {
      await cardsApi.cancel(active.id);
      setData((current) => (current ? { ...current, cards: current.cards.filter((card) => card.id !== active.id) } : current));
      setActiveIndex(0);
      setPanel(null);
      toast.success('Card cancelled');
    });

  if (loading && !data) return <Skeleton height={320} />;
  if (error || !data) return <ErrorState body={error?.body ?? 'We could not load your cards.'} onRetry={reload} />;

  return (
    <>
      <PageHeader
        title="Cards"
        actions={
          <Button variant="secondary" onClick={() => setPanel('create')}>
            Add card
          </Button>
        }
      />

      {cards.length === 0 ? (
        <p className="quiet-empty">
          No cards yet. Add a virtual card for paying online, or order a plastic one.
        </p>
      ) : (
        <div className="cards-layout">
          <div className="cards-layout__wallet">
            <CardCarousel
              ariaLabel="Your cards"
              onActiveChange={setActiveIndex}
              items={cards.map((card) => ({ key: card.id, node: <BankCard card={toPresentation(card)} size="md" /> }))}
            />
          </div>

          {active && (
            <div className="cards-layout__settings">
              <h2 className="cards-title">{active.label}</h2>
              <p className="cards-subtitle">
                {cardName(active.design)} · {materialLabel(materialOf(active.design, active.type))} · ••••{' '}
                {active.lastFour} · expires {formatExpiry(active.expirationMonth, active.expirationYear)}
              </p>
              <CardSettings
                card={active}
                busy={busy}
                onToggleFreeze={toggleFreeze}
                onDetails={() => setPanel('details')}
                onControls={() => setPanel('controls')}
                onLimits={() => setPanel('limits')}
                onDesign={() => setPanel('design')}
                onRename={() => {
                  setDraftLabel(active.label);
                  setPanel('rename');
                }}
                onCancel={() => setPanel('cancel')}
              />
            </div>
          )}

          {active && (
            <section className="cards-layout__activity" aria-labelledby="card-activity">
              <h2 id="card-activity" className="section-title">
                Activity
              </h2>
              {/* MBank has no card acquiring, so this is the history of the account behind the card. */}
              <p className="cards-subtitle">Recent movements on {active.accountNumber}, the account behind this card.</p>
              {activity.data && activity.data.length > 0 ? (
                <TransactionList transactions={activity.data} onSelect={setSelectedTxn} />
              ) : (
                <p className="quiet-empty">{activity.loading ? 'Loading…' : 'Nothing yet.'}</p>
              )}
            </section>
          )}
        </div>
      )}

      <CardCreateModal
        open={panel === 'create'}
        onClose={() => setPanel(null)}
        accounts={data.accounts}
        cardholderName={`${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim()}
        onCreated={(card) => {
          setData((current) => (current ? { ...current, cards: [card, ...current.cards] } : current));
          setActiveIndex(0);
          setPanel(null);
          toast.success('Card created', `${card.label} is ready to use.`);
        }}
      />

      <CardDetailsModal open={panel === 'details'} card={active} onClose={() => setPanel(null)} />

      {active && (
        <CardControlsSheet open={panel === 'controls'} card={active} onClose={() => setPanel(null)} onUpdated={replaceCard} />
      )}

      <CardLimitsModal
        open={panel === 'limits'}
        card={active}
        onClose={() => setPanel(null)}
        onSaved={(updated) => {
          replaceCard(updated);
          toast.success('Limits updated');
        }}
      />

      <Sheet open={panel === 'rename'} onClose={() => setPanel(null)} title="Rename card">
        <form
          className="sheet-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (draftLabel.trim()) void rename();
          }}
        >
          <Input label="Card name" value={draftLabel} onChange={(event) => setDraftLabel(event.target.value)} maxLength={50} autoFocus />
          <div className="modal-actions">
            <Button type="button" variant="secondary" onClick={() => setPanel(null)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={busy} disabled={!draftLabel.trim()}>
              Save
            </Button>
          </div>
        </form>
      </Sheet>

      <Sheet open={panel === 'design'} onClose={() => setPanel(null)} wide title="Card design" subtitle="The number, limits and history stay the same.">
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
              {active && <BankCard card={{ ...toPresentation(active), design, status: 'ACTIVE' }} size="sm" showStatus={false} />}
              <span className="design-swatch__name">{CARD_RANGE[design].name}</span>
            </button>
          ))}
        </div>
      </Sheet>

      <Sheet
        open={panel === 'cancel'}
        onClose={() => setPanel(null)}
        title="Cancel this card?"
        subtitle="This cannot be undone. To pause the card instead, freeze it."
      >
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setPanel(null)}>
            Keep card
          </Button>
          <Button variant="danger" onClick={cancelCard} loading={busy}>
            Cancel card
          </Button>
        </div>
      </Sheet>

      <TransactionDetail transaction={selectedTxn} onClose={() => setSelectedTxn(null)} />
    </>
  );
}
