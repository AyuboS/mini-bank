import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { Button, Sheet } from './ui';

interface SignOutConfirmProps {
  open: boolean;
  onClose: () => void;
}

/** The "Sign out?" question. Shared by the profile menu and the Profile page. */
export function SignOutConfirm({ open, onClose }: SignOutConfirmProps) {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const signOut = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <Sheet open={open} onClose={onClose} title="Sign out?" subtitle="You will need your password to sign back in.">
      <div className="modal-actions">
        <Button variant="secondary" onClick={onClose}>
          Stay signed in
        </Button>
        <Button variant="primary" onClick={signOut}>
          Sign out
        </Button>
      </div>
    </Sheet>
  );
}

/** A labelled Sign out button with its confirmation step. */
export function SignOutButton() {
  const [confirming, setConfirming] = useState(false);

  return (
    <>
      <Button variant="secondary" onClick={() => setConfirming(true)} icon={<LogOut aria-hidden="true" />}>
        Sign out
      </Button>
      <SignOutConfirm open={confirming} onClose={() => setConfirming(false)} />
    </>
  );
}
