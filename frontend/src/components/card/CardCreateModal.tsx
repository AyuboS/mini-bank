import { useMemo, useState } from 'react';
import { Check, CreditCard, Globe, Layers, Sparkles, Truck, Zap } from 'lucide-react';
import { cardsApi } from '../../api/cards';
import type { AccountResponse, CardDesign, CardResponse, CardType } from '../../api/types';
import { Button, Input, Sheet, Select } from '../ui';
import { toFriendlyError } from '../../utils/errors';
import { BankCard } from './BankCard';
import type { CardPresentation } from './BankCard';
import { CARD_RANGE, CARD_RANGE_ORDER, cardName, type CardMaterial } from './range';

interface CardCreateModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (card: CardResponse) => void;
  accounts: AccountResponse[];
  cardholderName: string;
}

/** The six steps of ordering a card. */
type Step = 'type' | 'material' | 'design' | 'customise' | 'delivery' | 'confirm';

const PHYSICAL_STEPS: Step[] = ['type', 'material', 'design', 'customise', 'delivery', 'confirm'];
const VIRTUAL_STEPS: Step[] = ['type', 'design', 'customise', 'confirm'];

/** The three finishes a physical card can be ordered in. */
const MATERIALS: { id: CardMaterial; title: string; body: string; icon: React.ReactNode }[] = [
  {
    id: 'plastic',
    title: 'Plastic',
    body: 'Recycled polycarbonate, matte finish. Five colours. Free.',
    icon: <CreditCard aria-hidden="true" />,
  },
  {
    id: 'metal',
    title: 'Metal',
    body: 'Brushed anodised graphite, 16 grams. One colour, and it only comes one way.',
    icon: <Layers aria-hidden="true" />,
  },
  {
    id: 'special',
    title: 'Special Edition',
    body: 'A seasonal two-tone face, produced in a limited run.',
    icon: <Sparkles aria-hidden="true" />,
  },
];

type Delivery = 'standard' | 'express';

/** Ordering a card. */
export function CardCreateModal({
  open,
  onClose,
  onCreated,
  accounts,
  cardholderName,
}: CardCreateModalProps) {
  const [step, setStep] = useState<Step>('type');
  const [type, setType] = useState<CardType>('VIRTUAL');
  const [material, setMaterial] = useState<CardMaterial>('plastic');
  const [design, setDesign] = useState<CardDesign>('MIDNIGHT');
  const [delivery, setDelivery] = useState<Delivery>('standard');
  const [label, setLabel] = useState('');
  const [accountId, setAccountId] = useState<string>(accounts[0]?.id ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const steps = type === 'VIRTUAL' ? VIRTUAL_STEPS : PHYSICAL_STEPS;
  const position = Math.max(0, steps.indexOf(step));

  /** The faces available in the chosen finish. */
  const designs = useMemo(
    () =>
      CARD_RANGE_ORDER.filter((option) =>
        type === 'VIRTUAL' ? CARD_RANGE[option].material === 'plastic' : CARD_RANGE[option].material === material,
      ),
    [material, type],
  );

  const reset = () => {
    setStep('type');
    setType('VIRTUAL');
    setMaterial('plastic');
    setDesign('MIDNIGHT');
    setDelivery('standard');
    setLabel('');
    setError(null);
    setBusy(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  const go = (direction: 1 | -1) => {
    const next = steps[position + direction];
    if (next) setStep(next);
  };

  /** Narrowing the finish can leave the chosen design outside it — pick Metal after choosing Ocean and the order would carry a face that finish does not come in. */
  const chooseMaterial = (chosen: CardMaterial) => {
    setMaterial(chosen);
    const first = CARD_RANGE_ORDER.find((option) => CARD_RANGE[option].material === chosen);
    if (first) setDesign(first);
  };

  const preview: CardPresentation = {
    design,
    network: 'VISA',
    type,
    status: 'ACTIVE',
    // Placeholder digits, labelled as such under the preview.
    maskedNumber: '4284 •••• •••• 0000',
    cardholderName: cardholderName.toUpperCase(),
    expirationMonth: 8,
    expirationYear: new Date().getFullYear() + 4,
  };

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const created = await cardsApi.create({
        type,
        design,
        ...(label.trim() ? { label: label.trim() } : {}),
        ...(accountId ? { accountId } : {}),
      });
      onCreated(created);
      reset();
    } catch (err) {
      const friendly = toFriendlyError(err);
      setError(friendly.body ? `${friendly.title} ${friendly.body}` : friendly.title);
    } finally {
      setBusy(false);
    }
  };

  const TITLES: Record<Step, { title: string; subtitle: string }> = {
    type: { title: 'New card', subtitle: 'Virtual cards are ready immediately.' },
    material: { title: 'Choose a material', subtitle: 'What the card is made of.' },
    design: { title: 'Choose a design', subtitle: 'You can change this later, at any time.' },
    customise: { title: 'Make it yours', subtitle: 'Give the card a name you will recognise.' },
    delivery: { title: 'Delivery', subtitle: 'Where it would go, and how quickly.' },
    confirm: { title: 'Confirm your card', subtitle: 'This is what will be issued.' },
  };

  return (
    <Sheet open={open} onClose={close} wide title={TITLES[step].title} subtitle={TITLES[step].subtitle}>
      {/* A plain counter rather than a stepper bar. Six dots at the top of a
          bottom sheet cost a line of vertical space that the card preview uses
          better. */}
      <span className="label">
        Step {position + 1} of {steps.length}
      </span>

      {error && (
        <div className="auth-error" role="alert">
          <span>{error}</span>
        </div>
      )}

      {step === 'type' && (
        <div className="stack gap-3">
          <button
            type="button"
            className={`choice-tile ${type === 'VIRTUAL' ? 'choice-tile--selected' : ''}`}
            onClick={() => setType('VIRTUAL')}
          >
            <span className="choice-tile__icon">
              <Globe aria-hidden="true" />
            </span>
            <span className="choice-tile__text">
              <span className="choice-tile__title">Virtual</span>
              <span className="choice-tile__body">
                For online payments. Available the moment you create it, and easy to throw away.
              </span>
            </span>
            {type === 'VIRTUAL' && <Check className="choice-tile__check" aria-hidden="true" />}
          </button>

          <button
            type="button"
            className={`choice-tile ${type === 'PHYSICAL' ? 'choice-tile--selected' : ''}`}
            onClick={() => setType('PHYSICAL')}
          >
            <span className="choice-tile__icon">
              <CreditCard aria-hidden="true" />
            </span>
            <span className="choice-tile__text">
              <span className="choice-tile__title">Physical</span>
              <span className="choice-tile__body">
                A card for the wallet, in plastic, metal or a special edition.
              </span>
            </span>
            {type === 'PHYSICAL' && <Check className="choice-tile__check" aria-hidden="true" />}
          </button>

          {accounts.length > 1 && (
            <Select label="Account" value={accountId} onChange={(event) => setAccountId(event.target.value)}>
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.accountNumber} · {account.currency}
                </option>
              ))}
            </Select>
          )}

          <div className="modal-actions">
            <Button variant="secondary" onClick={close}>
              Cancel
            </Button>
            <Button variant="primary" onClick={() => go(1)}>
              Continue
            </Button>
          </div>
        </div>
      )}

      {step === 'material' && (
        <div className="stack gap-3">
          {MATERIALS.map((option) => (
            <button
              key={option.id}
              type="button"
              className={`choice-tile ${material === option.id ? 'choice-tile--selected' : ''}`}
              onClick={() => chooseMaterial(option.id)}
            >
              <span className="choice-tile__icon">{option.icon}</span>
              <span className="choice-tile__text">
                <span className="choice-tile__title">{option.title}</span>
                <span className="choice-tile__body">{option.body}</span>
              </span>
              {material === option.id && <Check className="choice-tile__check" aria-hidden="true" />}
            </button>
          ))}

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => go(-1)}>
              Back
            </Button>
            <Button variant="primary" onClick={() => go(1)}>
              Continue
            </Button>
          </div>
        </div>
      )}

      {step === 'design' && (
        <div className="stack gap-4">
          <div className="design-grid">
            {designs.map((option) => (
              <button
                key={option}
                type="button"
                className={`design-swatch ${design === option ? 'design-swatch--selected' : ''}`}
                onClick={() => setDesign(option)}
                aria-pressed={design === option}
              >
                <BankCard card={{ ...preview, design: option }} size="sm" showStatus={false} />
                <span className="design-swatch__name">{CARD_RANGE[option].name}</span>
              </button>
            ))}
          </div>

          <p className="small muted">{CARD_RANGE[design].blurb}</p>

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => go(-1)}>
              Back
            </Button>
            <Button variant="primary" onClick={() => go(1)}>
              Continue
            </Button>
          </div>
        </div>
      )}

      {step === 'customise' && (
        <div className="stack gap-4">
          <div className="card-confirm">
            <BankCard card={preview} size="md" showStatus={false} />
          </div>

          <Input
            label="Name this card"
            placeholder={type === 'VIRTUAL' ? 'Online only' : CARD_RANGE[design].name}
            value={label}
            onChange={(event) => setLabel(event.target.value)}
            maxLength={50}
            hint="Optional. Shown instead of the number in your wallet."
          />

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => go(-1)}>
              Back
            </Button>
            <Button variant="primary" onClick={() => go(1)}>
              Continue
            </Button>
          </div>
        </div>
      )}

      {step === 'delivery' && (
        <div className="stack gap-4">
          {/*
            Said before the options rather than after them. MBank has no card
            bureau; nothing is printed and nothing is posted, and a delivery step
            that only admitted that in the small print at the bottom would be
            doing the misleading anyway.
          */}
          <p className="simulated-note">
            <Truck aria-hidden="true" />
            <span>
              Simulated. MBank has no card bureau — nothing is manufactured and nothing is posted.
              This step shows the choice a real issuer would offer you.
            </span>
          </p>

          <button
            type="button"
            className={`choice-tile ${delivery === 'standard' ? 'choice-tile--selected' : ''}`}
            onClick={() => setDelivery('standard')}
          >
            <span className="choice-tile__icon">
              <Truck aria-hidden="true" />
            </span>
            <span className="choice-tile__text">
              <span className="choice-tile__title">Standard · free</span>
              <span className="choice-tile__body">3–7 business days</span>
            </span>
            {delivery === 'standard' && <Check className="choice-tile__check" aria-hidden="true" />}
          </button>

          <button
            type="button"
            className={`choice-tile ${delivery === 'express' ? 'choice-tile--selected' : ''}`}
            onClick={() => setDelivery('express')}
          >
            <span className="choice-tile__icon">
              <Zap aria-hidden="true" />
            </span>
            <span className="choice-tile__text">
              <span className="choice-tile__title">Express</span>
              <span className="choice-tile__body">1–2 business days</span>
            </span>
            {delivery === 'express' && <Check className="choice-tile__check" aria-hidden="true" />}
          </button>

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => go(-1)}>
              Back
            </Button>
            <Button variant="primary" onClick={() => go(1)}>
              Continue
            </Button>
          </div>
        </div>
      )}

      {step === 'confirm' && (
        <div className="stack gap-4">
          <div className="card-confirm">
            <BankCard card={preview} size="md" showStatus={false} />
            <p className="field-hint text-center mt-3">
              The number shown is a placeholder. Your real one is generated when the card is issued.
            </p>
          </div>

          <dl className="detail-rows">
            <div className="detail-row">
              <dt className="detail-row__label">Card</dt>
              <dd className="detail-row__value">{cardName(design)}</dd>
            </div>
            <div className="detail-row">
              <dt className="detail-row__label">Type</dt>
              <dd className="detail-row__value">{type === 'VIRTUAL' ? 'Virtual' : 'Physical'}</dd>
            </div>
            {type === 'PHYSICAL' && (
              <>
                <div className="detail-row">
                  <dt className="detail-row__label">Material</dt>
                  <dd className="detail-row__value">
                    {MATERIALS.find((option) => option.id === material)?.title}
                  </dd>
                </div>
                <div className="detail-row">
                  <dt className="detail-row__label">Delivery</dt>
                  <dd className="detail-row__value">
                    {delivery === 'express' ? 'Express · 1–2 days' : 'Standard · 3–7 days'}{' '}
                    <span className="dim">(simulated)</span>
                  </dd>
                </div>
              </>
            )}
            {label.trim() && (
              <div className="detail-row">
                <dt className="detail-row__label">Name</dt>
                <dd className="detail-row__value">{label.trim()}</dd>
              </div>
            )}
            {accounts.length > 1 && (
              <div className="detail-row">
                <dt className="detail-row__label">Account</dt>
                <dd className="detail-row__value">
                  {accounts.find((account) => account.id === accountId)?.accountNumber ?? '—'}
                </dd>
              </div>
            )}
          </dl>

          <div className="modal-actions">
            <Button variant="secondary" onClick={() => go(-1)} disabled={busy}>
              Back
            </Button>
            <Button variant="primary" onClick={submit} loading={busy}>
              {busy ? 'Creating…' : 'Create card'}
            </Button>
          </div>
        </div>
      )}
    </Sheet>
  );
}
