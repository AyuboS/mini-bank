import { NavLink, Outlet } from 'react-router-dom';

const TABS = [
  { to: '/admin', label: 'Overview', end: true },
  { to: '/admin/transfers', label: 'Transfers' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/audit', label: 'Audit trail' },
];

/**
 * The back office shares the customer application's material system entirely.
 *
 * Administration is where the consequential actions live — freezing accounts,
 * releasing held payments, reversing settled money — and dropping those into a
 * different-looking template makes them feel like a different, lesser product.
 */
export function AdminLayout() {
  return (
    <>
      <div className="page__head">
        <h1 className="page__title">Administration</h1>
        <p className="page__subtitle">Oversight of customers, accounts and money movement.</p>
      </div>

      {/*
        Links, not a tablist. These navigate, so they are anchors and the browser
        history is the source of truth for which one is selected — which is why
        this uses the segmented control's `--static` variant rather than the
        Tabs component, whose sliding pill is driven by controlled state.
      */}
      <nav className="tabs tabs--static" style={{ marginBottom: 'var(--space-5)' }} aria-label="Administration">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end ?? false}
            className={({ isActive }) => `tabs__tab ${isActive ? 'tabs__tab--active' : ''}`}
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <Outlet />
    </>
  );
}
