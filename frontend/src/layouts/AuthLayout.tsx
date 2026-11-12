import type { ReactNode } from 'react';
import { Landmark, Lock, ShieldCheck } from 'lucide-react';
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
        <div className="auth-poster__bloom" />

        <div className="auth-poster__brand">
          <Logo size={40} />
          <span className="auth-poster__name">MBank</span>
        </div>

        <div>
          <h1 className="auth-poster__statement">
            Money that <em>moves</em> when you say so.
          </h1>
          <p className="auth-poster__sub">
            Accounts in three currencies, transfers that settle in a second, and a ledger that
            balances to the cent.
          </p>
        </div>

        <div className="auth-poster__points">
          <span className="auth-poster__point">
            <ShieldCheck aria-hidden="true" />
            Every movement double-entered and auditable
          </span>
          <span className="auth-poster__point">
            <Lock aria-hidden="true" />
            Sessions expire, tokens rotate, failures are counted
          </span>
          <span className="auth-poster__point">
            <Landmark aria-hidden="true" />
            EUR, USD and GBP, held separately
          </span>
        </div>
      </aside>

      <div className="auth-form-side">
        <div className="auth-card">
          {/* Shown only on narrow screens, where the poster is not rendered. */}
          <div className="auth-brand">
            <Logo size={40} />
            <span className="auth-poster__name ml-auto">MBank</span>
            <span>
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
