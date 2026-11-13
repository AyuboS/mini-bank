import { NavLink, Outlet } from 'react-router-dom';

const SECTIONS = [
  { to: '/payments/send', label: 'Send' },
  { to: '/payments/payees', label: 'Payees' },
  { to: '/payments/scheduled', label: 'Scheduled' },
  { to: '/payments/requests', label: 'Requests' },
];

/** Payments groups the four ways money is arranged to leave or arrive. */
export function PaymentsLayout() {
  return (
    <>
      <nav className="subnav" aria-label="Payments">
        {SECTIONS.map((section) => (
          <NavLink
            key={section.to}
            to={section.to}
            className={({ isActive }) => `subnav__item ${isActive ? 'subnav__item--active' : ''}`}
          >
            {section.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  );
}
