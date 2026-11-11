import { useState } from 'react';
import { AtSign, Phone, Save, User } from 'lucide-react';
import { usersApi } from '../api/users';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../components/ToastProvider';
import { VerificationPanel } from '../components/auth/VerificationPanel';
import { SignOutButton } from '../components/SignOutButton';
import { Avatar, Button, Surface, Input } from '../components/ui';
import { formatDate, initials } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const toast = useToast();

  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [phoneNumber, setPhoneNumber] = useState(user?.phoneNumber ?? '');
  const [busy, setBusy] = useState(false);
  const [fields, setFields] = useState<Record<string, string>>({});

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setFields({});
    try {
      await usersApi.updateProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        // An emptied field is omitted rather than sent as "", which the API
        // would reject as a malformed phone number.
        ...(phoneNumber.trim() ? { phoneNumber: phoneNumber.trim() } : {}),
      });
      await refreshUser();
      toast.success('Profile updated');
    } catch (err) {
      const friendly = toFriendlyError(err);
      setFields(friendly.fields);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const dirty =
    firstName !== (user?.firstName ?? '') ||
    lastName !== (user?.lastName ?? '') ||
    phoneNumber !== (user?.phoneNumber ?? '');

  return (
    <>
      <div className="page__head">
        <h1 className="page__title">Profile</h1>
        <p className="page__subtitle">Your details, as MBank holds them.</p>
      </div>

      <Surface tone="raised" className="profile-head">
        <Avatar initials={initials(user?.firstName, user?.lastName)} size="lg" />
        <div className="grow">
          <div className="profile-head__name">
            {user?.firstName} {user?.lastName}
          </div>
          <div className="small dim">@{user?.username}</div>
          <div className="small dim" style={{ marginTop: 6 }}>
            {user?.role === 'ADMIN' ? 'Administrator' : 'Personal account'} · member since{' '}
            {user ? formatDate(user.createdAt) : ''}
          </div>
        </div>
      </Surface>

      {user && (
        <section className="section">
          <VerificationPanel user={user} onVerified={refreshUser} />
        </section>
      )}

      <section className="section">
        <h3 className="section__title" style={{ marginBottom: 'var(--space-4)' }}>
          Personal details
        </h3>

        <Surface style={{ padding: 'var(--space-6)' }}>
          <form onSubmit={save}>
            <div className="stack gap-4">
              <div className="row gap-3">
                <Input
                  label="First name"
                  className="grow"
                  icon={<User aria-hidden="true" />}
                  value={firstName}
                  onChange={(event) => setFirstName(event.target.value)}
                  {...(fields['firstName'] ? { error: fields['firstName'] } : {})}
                />
                <Input
                  label="Last name"
                  className="grow"
                  value={lastName}
                  onChange={(event) => setLastName(event.target.value)}
                  {...(fields['lastName'] ? { error: fields['lastName'] } : {})}
                />
              </div>

              <Input
                label="Phone number"
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                icon={<Phone aria-hidden="true" />}
                placeholder="+998 90 123 45 67"
                value={phoneNumber}
                onChange={(event) => setPhoneNumber(event.target.value)}
                hint={
                  user?.phoneVerified
                    ? 'Verified. Changing it clears that, and you will need to verify the new one.'
                    : 'Stored in international format. Verify it to sign in without a password.'
                }
                {...(fields['phoneNumber'] ? { error: fields['phoneNumber'] } : {})}
              />

              {/* Username and email are shown but not editable: both are
                  identifiers other records point at, and the API does not
                  accept changes to them. */}
              <Input
                label="Username"
                icon={<User aria-hidden="true" />}
                value={user?.username ?? ''}
                disabled
                hint="Your username cannot be changed."
              />
              <Input
                label="Email"
                icon={<AtSign aria-hidden="true" />}
                value={user?.email ?? ''}
                disabled
                hint={user?.emailVerified ? 'Verified.' : 'Not verified yet.'}
              />
            </div>

            <Button
              type="submit"
              variant="primary"
              loading={busy}
              disabled={!dirty}
              icon={<Save aria-hidden="true" />}
              style={{ marginTop: 'var(--space-6)' }}
            >
              Save changes
            </Button>
          </form>
        </Surface>
      </section>

      <section className="section">
        <h3 className="section__title" style={{ marginBottom: 'var(--space-4)' }}>
          This device
        </h3>
        <SignOutButton variant="button" />
      </section>
    </>
  );
}
