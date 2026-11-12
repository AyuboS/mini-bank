import type { ReactNode } from 'react';
import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';

interface SheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  children: ReactNode;
  /** Suppresses the close button, for a step the customer must finish or cancel explicitly. */
  dismissible?: boolean;
  /** Wider than the default, for tables and multi-column forms. */
  wide?: boolean;
  /** Where focus lands on open. Use `panel` for a read-first view that happens to contain a field. */
  initialFocus?: 'first-field' | 'panel';
}

/**
 * A modal on a desktop, a bottom sheet on a phone.
 *
 * One component rather than two because it is one thing: a surface that comes
 * forward and takes the interaction. Which way it arrives is a property of the
 * viewport, decided in CSS, so no screen has to know which it is getting.
 *
 * The behaviour that makes it a dialog rather than a floating div — the focus
 * trap, the escape key, the scroll lock, the restored focus on close — is all
 * here, because getting it wrong is invisible to a sighted mouse user and
 * completely disabling to everybody else.
 */
export function Sheet({
  open,
  onClose,
  title,
  subtitle,
  children,
  dismissible = true,
  wide = false,
  initialFocus = 'first-field',
}: SheetProps) {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const restoreFocusTo = useRef<HTMLElement | null>(null);

  // Read through a ref so a parent re-render (new onClose) does not re-run the focus effect.
  const latest = useRef({ onClose, dismissible, initialFocus });
  latest.current = { onClose, dismissible, initialFocus };

  useEffect(() => {
    if (!open) return;

    // Remembered before focus moves, so closing returns the customer to the
    // control they opened it from rather than to the top of the document.
    restoreFocusTo.current = document.activeElement as HTMLElement | null;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && latest.current.dismissible) {
        latest.current.onClose();
        return;
      }
      if (event.key !== 'Tab') return;

      // The focus trap. Without it, tabbing walks out of the dialog and into the
      // page behind, which is still there and still interactive — so a keyboard
      // user can silently end up operating a form they cannot see.
      const focusable = panelRef.current?.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), textarea, input:not([disabled]), select, [tabindex]:not([tabindex="-1"])',
      );
      if (!focusable || focusable.length === 0) return;

      const first = focusable[0]!;
      const last = focusable[focusable.length - 1]!;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);

    // The page behind must not scroll under the sheet. Compensating for the
    // scrollbar's width keeps the layout from jumping sideways as it vanishes.
    const scrollbar = window.innerWidth - document.documentElement.clientWidth;
    const previousOverflow = document.body.style.overflow;
    const previousPadding = document.body.style.paddingRight;
    document.body.style.overflow = 'hidden';
    if (scrollbar > 0) document.body.style.paddingRight = `${scrollbar}px`;

    // Focus the first field if there is one, otherwise the first button.
    const raf = requestAnimationFrame(() => {
      const panel = panelRef.current;
      if (latest.current.initialFocus === 'panel') {
        panel?.focus();
        return;
      }
      const target =
        panel?.querySelector<HTMLElement>(
          'input:not([type="hidden"]):not([disabled]), textarea:not([disabled]), select:not([disabled])',
        ) ??
        panel?.querySelector<HTMLElement>('.sheet__body button:not([disabled])') ??
        panel?.querySelector<HTMLElement>('button:not([disabled])');
      (target ?? panel)?.focus();
    });

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      document.body.style.paddingRight = previousPadding;
      cancelAnimationFrame(raf);
      restoreFocusTo.current?.focus?.();
    };
  }, [open]);

  if (!open) return null;

  return (
    <div className="sheet-scrim" onClick={dismissible ? onClose : undefined}>
      <div
        ref={panelRef}
        className={`sheet surface--raised ${wide ? 'sheet--wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        /* The scrim closes on click; the panel must not, or every click inside
           the dialog would dismiss it. */
        onClick={(event) => event.stopPropagation()}
      >
        {/* The drag handle only renders as one on a phone, where the sheet
            arrives from the bottom and the affordance means something. */}
        <div className="sheet__grip" aria-hidden="true" />

        {(title || dismissible) && (
          <header className="sheet__head">
            <div className="stack">
              {title && <h2 className="sheet__title">{title}</h2>}
              {subtitle && <p className="sheet__subtitle">{subtitle}</p>}
            </div>
            {dismissible && (
              <button type="button" className="icon-btn" onClick={onClose} aria-label="Close">
                <X aria-hidden="true" />
              </button>
            )}
          </header>
        )}

        <div className="sheet__body">{children}</div>
      </div>
    </div>
  );
}
