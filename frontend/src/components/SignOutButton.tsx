import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { Button, Sheet } from './ui';

interface SignOutButtonProps {
  /** `icon` for a toolbar, `button` for a labelled button on a page. */
  variant: 'icon' | 'button';
}

/** Sign out, with a confirmation step. Used in the top bars and on Profile. */
export function SignOutButton({ variant }: SignOutButtonProps) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);

  const signOut = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <>
      {variant === 'icon' ? (
        <button type="button" className="icon-btn" onClick={() => setConfirming(true)} aria-label="Sign out">
          <LogOut aria-hidden="true" />
        </button>
      ) : (
        <Button variant="secondary" onClick={() => setConfirming(true)} icon={<LogOut aria-hidden="true" />}>
          Sign out
        </Button>
      )}

      <Sheet
        open={confirming}
        onClose={() => setConfirming(false)}
        title="Sign out?"
        subtitle="You will need your password to sign back in."
      >
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setConfirming(false)}>
            Stay signed in
          </Button>
          <Button variant="primary" onClick={signOut}>
            Sign out
          </Button>
        </div>
      </Sheet>
    </>
  );
}
