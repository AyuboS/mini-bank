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

/**
 * A six-digit code entry.
 *
 * ## One input, drawn as six boxes
 *
 * The obvious build is six `<input maxlength="1">` elements, and it is a trap.
 * Six inputs means six focus states to shuttle between, backspace-into-the-
 * previous-box logic, paste that lands entirely in the first box, and — the one
 * that actually matters — SMS autofill that fills box one with the whole code
 * and boxes two through six with nothing, because the platform sees six
 * unrelated fields.
 *
 * So this is a single `<input>` holding all six digits, made invisible and
 * stretched across the row, with six styled `<div>`s painted underneath it. The
 * platform sees one field of type `one-time-code`:
 *
 *   - iOS and Android offer the code from the SMS above the keyboard, and
 *     filling it fills everything at once;
 *   - paste works, because there is one value to paste into;
 *   - backspace works, because it is just a text field;
 *   - selection, undo and the caret all behave, because none of them have been
 *     reimplemented.
 *
 * The caret is hidden and the box for the next digit is highlighted instead,
 * which is the only piece of the illusion that needs maintaining.
 */
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
