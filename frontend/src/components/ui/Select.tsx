import type { ReactNode, SelectHTMLAttributes } from 'react';
import { useId } from 'react';
import { ChevronDown } from 'lucide-react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}

/** A native `<select>` in a glass well. */
export function Select({
  label,
  error,
  hint,
  className = '',
  id,
  children,
  ...rest
}: SelectProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const messageId = `${selectId}-message`;
  const message = error ?? hint;

  return (
    <div className={`field ${error ? 'field--error' : ''} ${className}`}>
      {label && (
        <label className="field__label" htmlFor={selectId}>
          {label}
        </label>
      )}

      <div className="field__well well">
        <select
          id={selectId}
          className="field__input field__select"
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? messageId : undefined}
          {...rest}
        >
          {children}
        </select>
        <span className="field__suffix" aria-hidden="true">
          <ChevronDown size={16} />
        </span>
      </div>

      {message && (
        <p id={messageId} className={error ? 'field__error' : 'field__hint'} role={error ? 'alert' : undefined}>
          {message}
        </p>
      )}
    </div>
  );
}
