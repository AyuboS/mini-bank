import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, ClipboardEvent, KeyboardEvent } from 'react';

interface OtpInputProps {
  value: string;
  onChange: (value: string) => void;
  /** Fired once the last digit is entered, so nobody has to press a button. */
  onComplete?: (value: string) => void;
  length?: number;
  disabled?: boolean;
  error?: boolean;
  autoFocus?: boolean;
  label?: string;
}

/** A six-digit code entry. */
export function OtpInput({
  value,
  onChange,
  onComplete,
  length = 6,
  disabled = false,
  error = false,
  autoFocus = false,
  label = 'Verification code',
}: OtpInputProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const completedRef = useRef(false);
  // Real state rather than reading document.activeElement during render: the
  // active-cell highlight has to change when focus changes, and focus changes do
  // not themselves trigger a render.
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (autoFocus) inputRef.current?.focus();
  }, [autoFocus]);

  // Fires once per completed code. Without the latch, every re-render while the
  // code sits full would submit again — including the re-render caused by the
  // failure of the previous attempt.
  useEffect(() => {
    if (value.length === length && !completedRef.current) {
      completedRef.current = true;
      onComplete?.(value);
    }
    if (value.length < length) {
      completedRef.current = false;
    }
  }, [value, length, onComplete]);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    // Digits only, and never longer than the code. Stripping rather than
    // rejecting means a pasted "482 913" or "code: 482913" still works.
    const digits = event.target.value.replace(/\D/g, '').slice(0, length);
    onChange(digits);
  };

  const handlePaste = (event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault();
    onChange(event.clipboardData.getData('text').replace(/\D/g, '').slice(0, length));
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    // The caret is invisible, so arrow keys would move an insertion point
    // nobody can see and make typing land in the middle of the code.
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
    }
  };

  const cells = Array.from({ length }, (_, index) => index);

  return (
    <div className="otp">
      <label className="field-label" htmlFor="otp-code">
        {label}
      </label>

      <div
        className={`otp__row ${error ? 'otp__row--error' : ''}`}
        onClick={() => inputRef.current?.focus()}
        role="presentation"
      >
        {cells.map((index) => {
          const digit = value[index];
          // The next empty cell is the one that looks focused.
          const isNext = focused && index === Math.min(value.length, length - 1);
          return (
            <div
              key={index}
              className={`otp__cell ${digit ? 'otp__cell--filled' : ''} ${isNext ? 'otp__cell--active' : ''}`}
              aria-hidden="true"
            >
              {digit ?? ''}
            </div>
          );
        })}

        <input
          ref={inputRef}
          id="otp-code"
          className="otp__input"
          value={value}
          onChange={handleChange}
          onPaste={handlePaste}
          onKeyDown={handleKeyDown}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          disabled={disabled}
          /* The three attributes that make a phone offer the code from the SMS. */
          autoComplete="one-time-code"
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={length}
          aria-label={label}
          aria-invalid={error || undefined}
          /* Keyboards love to capitalise and correct a field of digits. */
          autoCorrect="off"
          autoCapitalize="off"
          spellCheck={false}
        />
      </div>
    </div>
  );
}
