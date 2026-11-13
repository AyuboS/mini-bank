import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeftRight, Bell, CreditCard, House, ListOrdered, PiggyBank, Search, ShieldCheck } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { Logo } from '../components/Logo';
import { ProfileMenu } from '../components/ProfileMenu';
import { ThemeToggle } from '../components/ThemeToggle';
import { useUnreadNotifications } from '../hooks/useNotifications';
import { openCommandPalette } from '../components/CommandPalette';

interface NavEntry {
  to: string;
  label: string;
  icon: typeof House;
  end?: boolean;
}

// The same five on the desktop sidebar and the phone's bottom bar.
const PRIMARY: NavEntry[] = [
  { to: '/', label: 'Home', icon: House, end: true },
  { to: '/transactions', label: 'Transactions', icon: ListOrdered },
  { to: '/payments', label: 'Payments', icon: ArrowLeftRight },
  { to: '/cards', label: 'Cards', icon: CreditCard },
  { to: '/savings', label: 'Savings', icon: PiggyBank },
];

const ADMIN: NavEntry = { to: '/admin', label: 'Administration', icon: ShieldCheck };

// Titles for the phone's top bar. Longest prefix wins, so /payments/send reads "Payments".
const TITLES: [prefix: string, title: string][] = [
  ['/payments', 'Payments'],
  ['/transactions', 'Transactions'],
  ['/cards', 'Cards'],
  ['/savings', 'Savings'],
  ['/accounts', 'Accounts'],
  ['/analytics', 'Analytics'],
  ['/notifications', 'Notifications'],
  ['/statements', 'Statements'],
  ['/support', 'Support'],
  ['/security', 'Security'],
  ['/profile', 'Profile'],
  ['/admin', 'Administration'],
];

function titleFor(pathname: string): string {
  if (pathname === '/') return 'Home';
  return TITLES.find(([prefix]) => pathname.startsWith(prefix))?.[1] ?? 'MBank';
}

function NavItem({ entry }: { entry: NavEntry }) {
  const Icon = entry.icon;
  return (
    <NavLink
      to={entry.to}
      end={entry.end ?? false}
      className={({ isActive }) => `nav-item ${isActive ? 'nav-item--active' : ''}`}
    >
      <span className="nav-item__icon">
        <Icon aria-hidden="true" />
      </span>
      {entry.label}
    </NavLink>
  );
}

function NotificationsButton() {
  const navigate = useNavigate();
  const { unread } = useUnreadNotifications();
  return (
    <button
      type="button"
      className="icon-btn"
      onClick={() => navigate('/notifications')}
      aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
    >
      <Bell aria-hidden="true" />
      {unread > 0 && <span className="icon-btn__count">{unread > 99 ? '99+' : unread}</span>}
    </button>
  );
}

export function AppLayout() {
  const { user } = useAuth();
  const location = useLocation();
  const isAdmin = user?.role === 'ADMIN';

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <aside className="sidebar">
        <div className="sidebar__brand">
          <Logo size={30} />
          <span className="sidebar__name">MBank</span>
        </div>

        <nav className="sidebar__section" aria-label="Main">
          {PRIMARY.map((entry) => (
            <NavItem entry={entry} key={entry.to} />
          ))}
          {isAdmin && <NavItem entry={ADMIN} />}
        </nav>

        <div className="sidebar__spacer" />

        <p className="demo-label">Demo. No real money.</p>
        <ProfileMenu placement="sidebar" />
      </aside>

      <div className="main">
        <header className="topbar">
          <button type="button" className="search" onClick={openCommandPalette}>
            <Search aria-hidden="true" />
            <span className="search__label">Search</span>
            <span className="search__key" aria-hidden="true">
              <kbd>Ctrl</kbd>
              <kbd>K</kbd>
            </span>
          </button>
          <ThemeToggle />
          <NotificationsButton />
        </header>

        <header className="mobile-topbar">
          <Logo size={26} />
          <span className="mobile-topbar__heading">
            <span className="mobile-topbar__title">{titleFor(location.pathname)}</span>
            <span className="demo-label">Demo. No real money.</span>
          </span>
          <button type="button" className="icon-btn" onClick={openCommandPalette} aria-label="Search">
            <Search aria-hidden="true" />
          </button>
          <NotificationsButton />
          <ProfileMenu placement="topbar" />
        </header>

        <main className="page" id="main">
          <Outlet />
        </main>
      </div>

      <nav className="mobile-nav" aria-label="Main">
        {PRIMARY.map((entry) => {
          const Icon = entry.icon;
          return (
            <NavLink
              key={entry.to}
              to={entry.to}
              end={entry.end ?? false}
              className={({ isActive }) => `mobile-nav__item ${isActive ? 'mobile-nav__item--active' : ''}`}
            >
              <Icon aria-hidden="true" />
              {entry.label}
            </NavLink>
          );
        })}
      </nav>
    </div>
  );
}
