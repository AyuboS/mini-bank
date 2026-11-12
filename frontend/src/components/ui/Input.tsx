import type { InputHTMLAttributes, ReactNode } from 'react';
import { useId, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  /** A validation message. Its presence is what puts the field into its error state. */
  error?: string;
  /** Guidance shown under the field when there is no error. */
  hint?: string;
  /** An icon inside the field, before the text. */
  icon?: ReactNode;
  /** A unit or action at the trailing edge — a currency symbol, a reveal toggle. */
  suffix?: ReactNode;
  /** Adds a show/hide toggle to a password field. */
  revealable?: boolean;
}

/** A recessed well cut into the glass rather than a pane laid on top of it. */
export function Input({
  label,
  error,
  hint,
  icon,
  suffix,
  revealable = false,
  className = '',
  id,
  type,
  ...rest
}: InputProps) {
  const generatedId = useId();
  const [revealed, setRevealed] = useState(false);
  const inputId = id ?? generatedId;
  const messageId = `${inputId}-message`;
  const message = error ?? hint;
  const effectiveType = revealable && revealed ? 'text' : type;

  return (
    <div className={`field ${error ? 'field--error' : ''} ${className}`}>
      {label && (
        <label className="field__label" htmlFor={inputId}>
          {label}
        </label>
      )}

      <div className="field__well well">
        {icon && <span className="field__icon">{icon}</span>}
        <input
          id={inputId}
          className="field__input"
          type={effectiveType}
          aria-invalid={error ? true : undefined}
          /* Points the announcement at whichever message is showing, so the
             error is read out with the field rather than sitting near it. */
          aria-describedby={message ? messageId : undefined}
          {...rest}
        />
        {revealable && (
          <button
            type="button"
            className="field__reveal"
            onClick={() => setRevealed((current) => !current)}
            aria-label={revealed ? 'Hide password' : 'Show password'}
            aria-pressed={revealed}
            /* Kept out of the tab order: somebody tabbing through a sign-in form
               wants the next field, not a visibility toggle between every one. */
            tabIndex={-1}
          >
            {revealed ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
          </button>
        )}
        {suffix && <span className="field__suffix">{suffix}</span>}
      </div>

      {message && (
        <p
          id={messageId}
          className={error ? 'field__error' : 'field__hint'}
          /* Errors appear after the customer acts, so they must be announced
             when they arrive. Hints are static and must not interrupt. */
          role={error ? 'alert' : undefined}
        >
          {message}
        </p>
      )}
    </div>
  );
}
