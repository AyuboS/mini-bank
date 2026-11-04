import { useState } from 'react';
import { Bell, Globe, Palette, Shield, User as UserIcon, Wallet } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Select, Surface } from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { ThemeToggle } from '../components/ThemeToggle';
import { formatDay } from '../utils/format';

/** What the customer can change from here, and what lives elsewhere. */
const LINKS = [
  { to: '/profile', label: 'Profile', body: 'Your name, email and phone number', icon: UserIcon },
  { to: '/security', label: 'Security', body: 'Password, signed-in devices, sign-in history', icon: Shield },
  { to: '/notifications', label: 'Notifications', body: 'Everything MBank has told you', icon: Bell },
  { to: '/accounts', label: 'Accounts', body: 'Balances, account numbers and statements', icon: Wallet },
];

/**
 * Settings.
 *
 * Two kinds of thing, kept apart: preferences that live in this browser, and
 * links to the screens that own the real settings. Duplicating a password form
 * here would mean two places to change one thing.
 *
 * The preferences below are honestly labelled as browser-local. They are not
 * synced to an account, because there is no endpoint that stores them — and a
 * toggle that silently forgets itself on another device is worse than one that
 * says it will.
 */
export function SettingsPage() {
  const { user } = useAuth();
  const [language, setLanguage] = useState(() => localStorage.getItem('minibank.language') ?? 'en');

  /**
   * The language list.
   *
   * Only English is implemented. The others are listed as coming, rather than
   * offered and then ignored: an interface that lets you pick Russian and stays
   * in English has lied to you.
   */
  const chooseLanguage = (value: string) => {
    setLanguage(value);
    try {
      localStorage.setItem('minibank.language', value);
    } catch {
      // Private browsing with storage denied. The choice holds for this session.
    }
  };

  return (
    <div className="stack-lg">
      <PageHeader title="Settings" subtitle={user ? `Signed in as ${user.username}` : undefined} />

      <section className="stack">
        <h2>Appearance</h2>
        <Surface className="panel">
          <div className="setting-row">
            <span className="setting-row__icon" aria-hidden="true">
              <Palette />
            </span>
            <div className="grow stack" style={{ gap: 2 }}>
              <span className="setting-row__label">Theme</span>
              <span className="dim setting-row__body">
                MBank is designed dark. The light theme is there for a bright room.
              </span>
            </div>
            <ThemeToggle />
          </div>

          <div className="setting-row">
            <span className="setting-row__icon" aria-hidden="true">
              <Globe />
            </span>
            <div className="grow stack" style={{ gap: 2 }}>
              <span className="setting-row__label">Language</span>
              <span className="dim setting-row__body">
                Only English is available today. The rest are not translated yet, and are listed rather
                than offered.
              </span>
            </div>
            <Select
              value={language}
              onChange={(event) => chooseLanguage(event.target.value)}
              aria-label="Language"
            >
              <option value="en">English</option>
              <option value="ru" disabled>
                Русский — coming
              </option>
              <option value="uz" disabled>
                Oʻzbekcha — coming
              </option>
              <option value="lv" disabled>
                Latviešu — coming
              </option>
            </Select>
          </div>
        </Surface>
        <p className="dim settings__note">
          Appearance and language are remembered in this browser only. They do not follow you to another
          device.
        </p>
      </section>

      <section className="stack">
        <h2>Your account</h2>
        <Surface className="list-panel">
          {LINKS.map((link) => {
            const Icon = link.icon;
            return (
              <Link className="setting-link" to={link.to} key={link.to}>
                <span className="setting-row__icon" aria-hidden="true">
                  <Icon />
                </span>
                <span className="grow stack" style={{ gap: 2 }}>
                  <span className="setting-row__label">{link.label}</span>
                  <span className="dim setting-row__body">{link.body}</span>
                </span>
              </Link>
            );
          })}
        </Surface>
      </section>

      <section className="stack">
        <h2>Privacy</h2>
        <Surface className="panel">
          <p className="dim">
            MBank holds your name, email address, phone number, the accounts and cards you opened, and the
            payments you made. It does not you across other sites and has no advertising. Your
            sign-in history and signed-in devices are visible to you under{' '}
            <Link to="/security">Security</Link>.
          </p>
          {user && (
            <p className="dim" style={{ marginTop: 'var(--space-3)' }}>
              Member since {formatDay(user.createdAt)}.
            </p>
          )}
        </Surface>
      </section>
    </div>
  );
}
