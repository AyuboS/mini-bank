import type { ReactNode } from 'react';
import { Logo } from '../components/Logo';
import { ThemeToggle } from '../components/ThemeToggle';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer?: ReactNode;
}

/** The shell for signing in and registering. */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  return (
    <div className="auth-page">
      <aside className="auth-poster">
        <div className="auth-poster__brand">
          <Logo size={40} />
          <span className="auth-poster__name">MBank</span>
        </div>

        <div>
          <h1 className="auth-poster__statement">A bank, simulated.</h1>
          <p className="auth-poster__sub">
            Accounts, cards, payments and savings on a double-entry ledger. A portfolio project: no real money
            moves.
          </p>
        </div>

        <ul className="auth-poster__points">
          <li>Every payment is two ledger entries that sum to zero.</li>
          <li>Retrying a payment is safe: each one carries an idempotency key.</li>
          <li>EUR, USD and GBP are held separately and never mixed.</li>
        </ul>
      </aside>

      <div className="auth-form-side">
        <div className="auth-card">
          {/* Shown only on narrow screens, where the poster is not rendered. */}
          <div className="auth-brand">
            <Logo size={40} />
            <span className="auth-poster__name">MBank</span>
            <span className="ml-auto">
              <ThemeToggle />
            </span>
          </div>

          <h1 className="auth-title">{title}</h1>
          <p className="auth-sub">{subtitle}</p>

          {children}
          {footer}
        </div>
      </div>
    </div>
  );
}
