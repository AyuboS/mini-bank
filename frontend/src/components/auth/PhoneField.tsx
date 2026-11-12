import { useId } from 'react';
import { Phone } from 'lucide-react';

/** Dialling codes offered in the picker. */
const DIALLING_CODES = [
  { code: '+998', label: 'Uzbekistan' },
  { code: '+7', label: 'Kazakhstan / Russia' },
  { code: '+49', label: 'Germany' },
  { code: '+44', label: 'United Kingdom' },
  { code: '+33', label: 'France' },
  { code: '+1', label: 'United States / Canada' },
  { code: '+971', label: 'United Arab Emirates' },
  { code: '+90', label: 'Türkiye' },
] as const;

interface PhoneFieldProps {
  /** The dialling code, e.g. "+998". */
  dialCode: string;
  onDialCodeChange: (value: string) => void;
  /** The subscriber part, digits only as far as the caller is concerned. */
  number: string;
  onNumberChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
  autoFocus?: boolean;
}

/** Joins the two halves into the E.164 string the API expects. */
export function toE164(dialCode: string, number: string): string {
  return `${dialCode}${number.replace(/\D/g, '')}`;
}

/** A phone number entry: dialling code beside the rest. */
export function PhoneField({
  dialCode,
  onDialCodeChange,
  number,
  onNumberChange,
  disabled = false,
  error,
  autoFocus = false,
}: PhoneFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;

  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        Phone number
      </label>

      <div className={`phone-field ${error ? 'input-shell--error' : ''}`}>
        <span className="input-shell__icon">
          <Phone aria-hidden="true" />
        </span>

        <select
          className="phone-field__code"
          value={dialCode}
          onChange={(event) => onDialCodeChange(event.target.value)}
          disabled={disabled}
          aria-label="Country dialling code"
        >
          {DIALLING_CODES.map((entry) => (
            <option key={entry.code} value={entry.code}>
              {entry.code}
            </option>
          ))}
        </select>

        <input
          id={id}
          className="phone-field__number"
          value={number}
          onChange={(event) => onNumberChange(event.target.value.replace(/[^\d\s]/g, ''))}
          placeholder="90 123 45 67"
          disabled={disabled}
          autoFocus={autoFocus}
          /* tel rather than number: a numeric keypad without the spinner, the
             scroll-wheel hazard or the silent dropping of a leading zero. */
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
        />
      </div>

      {error && (
        <p className="field-error" id={errorId} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
