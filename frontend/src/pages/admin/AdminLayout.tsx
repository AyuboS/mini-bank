import { NavLink, Outlet } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';

const TABS = [
  { to: '/admin', label: 'Overview', end: true },
  { to: '/admin/transfers', label: 'Transfers' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/accounts', label: 'Accounts' },
  { to: '/admin/audit', label: 'Audit trail' },
];

/** The back office. Links rather than tabs: each section has its own address. */
export function AdminLayout() {
  return (
    <>
      <PageHeader title="Administration" subtitle="Customers, accounts and money movement." />
      <nav className="admin-nav" aria-label="Administration">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end ?? false}
            className={({ isActive }) => `admin-nav__link ${isActive ? 'admin-nav__link--active' : ''}`}
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  );
}
