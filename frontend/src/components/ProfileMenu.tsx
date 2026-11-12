import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { initials } from '../utils/format';
import { Avatar } from './ui';
import { SignOutConfirm } from './SignOutButton';

interface ProfileMenuProps {
  /** `sidebar` shows the name beside the avatar and opens upward; `topbar` is the avatar alone. */
  placement: 'sidebar' | 'topbar';
}

const LINKS = [
  { to: '/profile', label: 'Profile' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/analytics', label: 'Analytics' },
  { to: '/statements', label: 'Statements' },
  { to: '/security', label: 'Security' },
  { to: '/settings', label: 'Settings' },
  { to: '/support', label: 'Support' },
];

/** Everything that is not one of the five main destinations, ending with Sign out. */
export function ProfileMenu({ placement }: ProfileMenuProps) {
  const { user } = useAuth();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const buttonRef = useRef<HTMLButtonElement | null>(null);

  // Following a link closes the menu.
  useEffect(() => setOpen(false), [location.pathname]);

  useEffect(() => {
    if (!open) return undefined;

    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };

    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const isAdmin = user?.role === 'ADMIN';
  const fullName = `${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim();

  return (
    <div className={`profile-menu profile-menu--${placement}`} ref={rootRef}>
      <button
        type="button"
        ref={buttonRef}
        className="profile-menu__button"
        aria-expanded={open}
        aria-controls={`profile-menu-${placement}`}
        aria-label={`Account menu for ${fullName || 'you'}`}
        onClick={() => setOpen((current) => !current)}
      >
        <Avatar initials={initials(user?.firstName, user?.lastName)} size="sm" />
        {placement === 'sidebar' && <span className="profile-menu__name truncate">{fullName}</span>}
      </button>

      {open && (
        <nav className="profile-menu__panel" id={`profile-menu-${placement}`} aria-label="Account">
          <p className="profile-menu__who">
            <span className="truncate">{fullName}</span>
            <span className="profile-menu__username truncate">@{user?.username}</span>
          </p>
          <ul>
            {LINKS.map((link) => (
              <li key={link.to}>
                <Link className="profile-menu__item" to={link.to}>
                  {link.label}
                </Link>
              </li>
            ))}
            {isAdmin && (
              <li>
                <Link className="profile-menu__item" to="/admin">
                  Administration
                </Link>
              </li>
            )}
            <li className="profile-menu__last">
              <button
                type="button"
                className="profile-menu__item"
                onClick={() => {
                  setOpen(false);
                  setConfirming(true);
                }}
              >
                Sign out
              </button>
            </li>
          </ul>
        </nav>
      )}

      <SignOutConfirm open={confirming} onClose={() => setConfirming(false)} />
    </div>
  );
}
