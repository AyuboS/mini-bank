import type { ButtonHTMLAttributes, ReactNode } from 'react';

/** `primary` fills with the brand gradient and is the one action a screen is about. */
type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'quiet';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: 'sm' | 'md' | 'lg';
  block?: boolean;
  /** Swaps the label for a spinner and disables the control. */
  loading?: boolean;
  icon?: ReactNode;
  /** An icon after the label, for "continue"-style actions. */
  trailingIcon?: ReactNode;
  children?: ReactNode;
}

export function Button({
  variant = 'secondary',
  size = 'md',
  block = false,
  loading = false,
  icon,
  trailingIcon,
  className = '',
  children,
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) {
  const classes = ['btn', `btn-${variant}`, `btn-${size}`, block ? 'btn-block' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      /*
       * Announced rather than merely shown. A spinner that replaces a label is
       * invisible to a screen reader, so somebody who cannot see it is left
       * pressing a button that appears to do nothing.
       */
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <span className="btn__spinner" aria-hidden="true" /> : icon}
      {children}
      {!loading && trailingIcon}
    </button>
  );
}
