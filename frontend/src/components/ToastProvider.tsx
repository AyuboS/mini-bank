import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react';

type ToastTone = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: number;
  tone: ToastTone;
  title: string;
  body?: string;
}

interface ToastApi {
  success: (title: string, body?: string) => void;
  error: (title: string, body?: string) => void;
  warning: (title: string, body?: string) => void;
  info: (title: string, body?: string) => void;
}

const ToastContext = createContext<ToastApi | undefined>(undefined);

const ICONS = {
  success: CheckCircle2,
  error: XCircle,
  warning: AlertTriangle,
  info: Info,
} as const;

/** Errors stay longer, because they usually need reading rather than glancing. */
const LIFETIME: Record<ToastTone, number> = {
  success: 4000,
  info: 4000,
  warning: 6000,
  error: 7000,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (tone: ToastTone, title: string, body?: string) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, tone, title, ...(body ? { body } : {}) }]);
      window.setTimeout(() => dismiss(id), LIFETIME[tone]);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (title, body) => push('success', title, body),
      error: (title, body) => push('error', title, body),
      warning: (title, body) => push('warning', title, body),
      info: (title, body) => push('info', title, body),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      {/*
        A polite live region: announced when the screen reader is idle rather
        than interrupting, which is right for confirmations. Errors that block
        the user are also rendered inline in the form itself.
      */}
      <div className="toast-region" role="status" aria-live="polite">
        {toasts.map((toast) => {
          const Icon = ICONS[toast.tone];
          return (
            <div key={toast.id} className={`surface--raised toast toast--${toast.tone}`}>
              <span className="toast__icon">
                <Icon aria-hidden="true" />
              </span>
              <div className="grow">
                <div className="toast__title">{toast.title}</div>
                {toast.body && <div className="toast__body">{toast.body}</div>}
              </div>
              <button
                type="button"
                className="toast__close"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss notification"
              >
                <X />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider');
  }
  return context;
}
