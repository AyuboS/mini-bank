import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';
import { Logo } from '../components/Logo';

/** Shown while the session is being restored, so the app never flashes a login form. */
function SessionLoading() {
  return (
    <div className="auth-page">
      <div className="stack center gap-4 is-faint">
        <Logo size={44} />
        <span className="small dim">Restoring your session…</span>
      </div>
    </div>
  );
}

/**
 * Keeps signed-out visitors away from application screens.
 *
 * This is navigation, not authorization. Every endpoint behind these routes
 * enforces its own rules server-side, because hiding a route inside a bundle
 * the visitor has already downloaded protects nothing at all.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <SessionLoading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

/**
 * Hides the back-office section from customers.
 *
 * Cosmetic only, and deliberately so: the admin endpoints are guarded by both a
 * URL rule and a method-level check on the server. A customer who edits their
 * way to /admin sees the screen and every request on it returns 403.
 */
export function AdminRoute({ children }: { children: ReactNode }) {
  const { user, loading, isAdmin } = useAuth();

  if (loading) return <SessionLoading />;
  if (!user) return <Navigate to="/login" replace />;
  if (!isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}
