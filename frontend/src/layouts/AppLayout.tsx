import { useMemo } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  ArrowLeftRight,
  Bell,
  CalendarClock,
  ChartPie,
  ChevronRight,
  CreditCard,
  FileText,
  HandCoins,
  LayoutGrid,
  LifeBuoy,
  ListOrdered,
  PiggyBank,
  Search,
  Settings,
  Shield,
  ShieldCheck,
  User,
  Users,
  Wallet,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/ui';
import { Logo } from '../components/Logo';
import { SignOutButton } from '../components/SignOutButton';
import { ThemeToggle } from '../components/ThemeToggle';
import { useUnreadNotifications } from '../hooks/useNotifications';
import { openCommandPalette } from '../components/CommandPalette';
import { initials } from '../utils/format';

interface NavEntry {
  to: string;
  label: string;
  icon: typeof LayoutGrid;
  end?: boolean;
}

/** Money: what a customer opens the app to do. */
const MONEY: NavEntry[] = [
  { to: '/', label: 'Overview', icon: LayoutGrid, end: true },
  { to: '/accounts', label: 'Accounts', icon: Wallet },
  { to: '/cards', label: 'Cards', icon: CreditCard },
  { to: '/transfer', label: 'Send', icon: ArrowLeftRight },
  { to: '/requests', label: 'Requests', icon: HandCoins },
];

/** Planning: what a customer opens the app to understand. */
const PLAN: NavEntry[] = [
  { to: '/transactions', label: 'Transactions', icon: ListOrdered },
  { to: '/analytics', label: 'Analytics', icon: ChartPie },
  { to: '/savings', label: 'Savings', icon: PiggyBank },
  { to: '/scheduled', label: 'Scheduled', icon: CalendarClock },
  { to: '/statements', label: 'Statements', icon: FileText },
];

const ACCOUNT: NavEntry[] = [
  { to: '/beneficiaries', label: 'Payees', icon: Users },
  { to: '/security', label: 'Security', icon: Shield },
  { to: '/settings', label: 'Settings', icon: Settings },
  { to: '/support', label: 'Support', icon: LifeBuoy },
];

const ADMIN: NavEntry[] = [{ to: '/admin', label: 'Administration', icon: ShieldCheck }];

/**
 * Five destinations reachable by thumb; everything else lives behind Me.
 *
 * Five is a ceiling, not a target. Past it the labels have to shrink below
 * legibility and the targets drop under the 44px minimum on a 375px screen. The
 * five chosen are the ones with a daily reason to be opened; Transactions is one
 * tap from Overview and does not need a permanent slot.
 */
const MOBILE: NavEntry[] = [
  { to: '/', label: 'Home', icon: LayoutGrid, end: true },
  { to: '/cards', label: 'Cards', icon: CreditCard },
  { to: '/transfer', label: 'Send', icon: ArrowLeftRight },
  { to: '/analytics', label: 'Insights', icon: ChartPie },
  { to: '/profile', label: 'Me', icon: User },
];

/** Titles for the phone's top bar, so each screen does not have to repeat itself. */
const TITLES: Record<string, string> = {
  '/': 'Overview',
  '/accounts': 'Accounts',
  '/cards': 'Cards',
  '/transfer': 'Send money',
  '/transactions': 'Transactions',
  '/analytics': 'Analytics',
  '/savings': 'Savings',
  '/scheduled': 'Scheduled',
  '/requests': 'Requests',
  '/beneficiaries': 'Payees',
  '/notifications': 'Notifications',
  '/statements': 'Statements',
  '/support': 'Support',
  '/security': 'Security',
  '/settings': 'Settings',
  '/profile': 'Profile',
  '/admin': 'Administration',
};

function NavItem({ entry, count }: { entry: NavEntry; count?: number }) {
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
      {count !== undefined && count > 0 && <span className="nav-item__count">{count > 99 ? '99+' : count}</span>}
    </NavLink>
  );
}

export function AppLayout() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { unread } = useUnreadNotifications();

  const title = useMemo(() => {
    const exact = TITLES[location.pathname];
    if (exact) return exact;
    return location.pathname.startsWith('/admin') ? 'Administration' : 'MBank';
  }, [location.pathname]);

  const who = initials(user?.firstName, user?.lastName);
  const isAdmin = user?.role === 'ADMIN';

  return (
    <div className="shell">
      {/* The first thing in the tab order, and the only way past a long
          navigation for somebody using a keyboard. */}
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <aside className="surface--chrome sidebar">
        <div className="sidebar__brand">
          <Logo size={34} />
          <span className="sidebar__wordmark">
            <span className="sidebar__name">MBank</span>
            <span className="sidebar__tagline">{isAdmin ? 'Back office' : 'Personal'}</span>
          </span>
        </div>

        <nav className="sidebar__section" aria-label="Money">
          {MONEY.map((entry) => (
            <NavItem entry={entry} key={entry.to} />
          ))}
        </nav>

        <div className="sidebar__heading">Plan</div>
        <nav className="sidebar__section" aria-label="Planning">
          {PLAN.map((entry) => (
            <NavItem entry={entry} key={entry.to} />
          ))}
        </nav>

        <div className="sidebar__heading">Account</div>
        <nav className="sidebar__section" aria-label="Account">
          {ACCOUNT.map((entry) => (
            <NavItem entry={entry} key={entry.to} />
          ))}
        </nav>

        {isAdmin && (
          <>
            <div className="sidebar__heading">Back office</div>
            <nav className="sidebar__section" aria-label="Administration">
              {ADMIN.map((entry) => (
                <NavItem entry={entry} key={entry.to} />
              ))}
            </nav>
          </>
        )}

        <div className="sidebar__spacer" />

        <button type="button" className="user-pill" onClick={() => navigate('/profile')}>
          <Avatar initials={who} />
          <span className="stack">
            <span className="user-pill__name">
              {user?.firstName} {user?.lastName}
            </span>
            <span className="user-pill__role">{isAdmin ? 'Administrator' : 'Personal account'}</span>
          </span>
          <span className="user-pill__chevron">
            <ChevronRight aria-hidden="true" />
          </span>
        </button>
      </aside>

      <div className="main">
        {/*
          No page title in the desktop bar. Every screen states its own name in
          display type immediately below, and a bar repeating it in smaller
          letters is chrome for the sake of chrome.
        */}
        <header className="topbar">
          <div className="topbar__actions">
            <button type="button" className="search" onClick={openCommandPalette}>
              <Search aria-hidden="true" />
              <span className="grow" style={{ textAlign: 'left' }}>
                Search MBank
              </span>
              <span className="search__key" aria-hidden="true">
                <kbd>⌘</kbd>
                <kbd>K</kbd>
              </span>
            </button>

            <ThemeToggle />

            <button
              type="button"
              className="icon-btn"
              onClick={() => navigate('/notifications')}
              aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
            >
              <Bell aria-hidden="true" />
              {unread > 0 && <span className="icon-btn__count">{unread > 99 ? '99+' : unread}</span>}
            </button>

            <SignOutButton variant="icon" />
          </div>
        </header>

        <header className="mobile-topbar">
          <Logo size={28} />
          <span className="grow mobile-topbar__title">{title}</span>
          <button type="button" className="icon-btn" onClick={openCommandPalette} aria-label="Search">
            <Search aria-hidden="true" />
          </button>
          <button
            type="button"
            className="icon-btn"
            onClick={() => navigate('/notifications')}
            aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
          >
            <Bell aria-hidden="true" />
            {unread > 0 && <span className="icon-btn__dot" />}
          </button>
          <SignOutButton variant="icon" />
        </header>

        {/* Keyed on the path so each navigation replays the entrance. */}
        <main className="page" id="main" key={location.pathname}>
          <Outlet />
        </main>
      </div>

      <nav className="surface--chrome mobile-nav" aria-label="Main">
        {MOBILE.map((entry) => {
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
