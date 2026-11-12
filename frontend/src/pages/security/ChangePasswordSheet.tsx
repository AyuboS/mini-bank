import { useState, type FormEvent } from 'react';
import { usersApi } from '../../api/users';
import { useAuth } from '../../auth/AuthContext';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Sheet } from '../../components/ui';
import { toFriendlyError } from '../../utils/errors';

/** Changing the password signs out every device, this one included. */
export function ChangePasswordSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast();
  const { logout } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);

  const mismatch = confirm.length > 0 && next !== confirm;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (mismatch) return;
    setSaving(true);
    try {
      await usersApi.changePassword(current, next);
      toast.success('Password changed', 'Every device has been signed out.');
      // The server has revoked every session; signing out here gives a clean redirect instead of a 401.
      await logout();
      window.location.assign('/login');
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Change your password"
      subtitle="Every device will be signed out, including this one."
    >
      <form className="sheet-form" onSubmit={submit}>
        <Input
          label="Current password"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
          required
        />
        <Input
          label="New password"
          type="password"
          autoComplete="new-password"
          value={next}
          onChange={(event) => setNext(event.target.value)}
          hint="At least 12 characters, with upper and lower case, a digit and a symbol."
          required
        />
        <Input
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(event) => setConfirm(event.target.value)}
          error={mismatch ? 'These do not match' : undefined}
          required
        />

        <div className="modal-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" loading={saving} disabled={mismatch}>
            Change password
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
