import { ChevronRight } from 'lucide-react';
import { useId } from 'react';
import type { CardResponse } from '../../api/types';
import { CARD_RANGE } from '../../components/card/range';

interface CardSettingsProps {
  card: CardResponse;
  busy: boolean;
  onToggleFreeze: () => void;
  onDetails: () => void;
  onControls: () => void;
  onLimits: () => void;
  onDesign: () => void;
  onRename: () => void;
  onCancel: () => void;
}

/** Everything that can be done to one card, as a settings list. */
export function CardSettings(props: CardSettingsProps) {
  const { card, busy } = props;
  const freezeId = useId();
  const frozen = card.status === 'FROZEN';
  const usable = card.status === 'ACTIVE' || card.status === 'FROZEN';

  return (
    <ul className="settings-list" aria-label={`Settings for ${card.label}`}>
      <li className="settings-list__row">
        <label className="settings-list__text" htmlFor={freezeId}>
          <span className="settings-list__label">Freeze card</span>
          <span className="settings-list__hint">
            {frozen ? 'Frozen. Nothing can be spent until you turn this off.' : 'Stops all spending until you turn it off.'}
          </span>
        </label>
        <span className="switch">
          <input
            id={freezeId}
            type="checkbox"
            role="switch"
            checked={frozen}
            disabled={busy || !usable}
            onChange={props.onToggleFreeze}
          />
          <span className="switch__track" aria-hidden="true">
            <span className="switch__thumb" />
          </span>
        </span>
      </li>
      <SettingsLink label="Card details" hint="Number, expiry and security code" onClick={props.onDetails} disabled={!usable} />
      <SettingsLink
        label="Spending controls"
        hint="Online, contactless, cash and abroad"
        onClick={props.onControls}
        disabled={!usable}
      />
      <SettingsLink label="Limits" hint="Daily and monthly spending" onClick={props.onLimits} disabled={!usable} />
      <SettingsLink label="Design" value={CARD_RANGE[card.design].name} onClick={props.onDesign} disabled={busy || !usable} />
      <SettingsLink label="Name" value={card.label} onClick={props.onRename} disabled={busy || !usable} />
      <li>
        <button type="button" className="settings-list__row settings-list__danger" onClick={props.onCancel} disabled={busy}>
          Cancel card
        </button>
      </li>
    </ul>
  );
}

interface SettingsLinkProps {
  label: string;
  hint?: string;
  value?: string;
  disabled?: boolean;
  onClick: () => void;
}

function SettingsLink({ label, hint, value, disabled, onClick }: SettingsLinkProps) {
  return (
    <li>
      <button type="button" className="settings-list__row" onClick={onClick} disabled={disabled}>
        <span className="settings-list__text">
          <span className="settings-list__label">{label}</span>
          {hint && <span className="settings-list__hint">{hint}</span>}
        </span>
        {value && <span className="settings-list__value">{value}</span>}
        <ChevronRight className="settings-list__chevron" aria-hidden="true" />
      </button>
    </li>
  );
}
