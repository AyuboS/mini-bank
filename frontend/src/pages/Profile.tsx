import { useState, type FormEvent } from 'react';
import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { usersApi } from '../api/users';
import { useAuth } from '../auth/AuthContext';
import { VerificationPanel } from '../components/auth/VerificationPanel';
import { PageHeader } from '../components/PageHeader';
import { SignOutButton } from '../components/SignOutButton';
import { ThemeToggle } from '../components/ThemeToggle';
import { useToast } from '../components/ToastProvider';
import { Button, Input } from '../components/ui';
import { toFriendlyError } from '../utils/errors';
import { formatDay } from '../utils/format';

const LINKS = [
  { to: '/security', label: 'Security', hint: 'Password, signed-in devices and sign-in history' },
  { to: '/notifications', label: 'Notifications', hint: 'Everything MBank has told you' },
  { to: '/accounts', label: 'Accounts', hint: 'Account numbers and balances' },
  { to: '/statements', label: 'Statements', hint: 'Monthly statements and CSV export' },
];

/** Personal details, preferences and the way out. Settings lives here too. */
export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const toast = useToast();

  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [phoneNumber, setPhoneNumber] = useState(user?.phoneNumber ?? '');
  const [busy, setBusy] = useState(false);
  const [fields, setFields] = useState<Record<string, string>>({});

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setFields({});
    try {
      await usersApi.updateProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        // An emptied field is left out: the API rejects "" as a phone number.
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
    firstName !== (user?.firstName ?? '') || lastName !== (user?.lastName ?? '') || phoneNumber !== (user?.phoneNumber ?? '');

  return (
    <div className="profile">
      <PageHeader
        title={user ? `${user.firstName} ${user.lastName}` : 'Profile'}
        subtitle={user ? `@${user.username} · customer since ${formatDay(user.createdAt)}` : undefined}
      />

      {user && <VerificationPanel user={user} onVerified={refreshUser} />}

      <section className="profile__section" aria-labelledby="details-title">
        <h2 id="details-title" className="section-title">
          Personal details
        </h2>
        <form className="profile-form" onSubmit={save}>
          <div className="profile-form__names">
            <Input
              label="First name"
              autoComplete="given-name"
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
              {...(fields['firstName'] ? { error: fields['firstName'] } : {})}
            />
            <Input
              label="Last name"
              autoComplete="family-name"
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
            placeholder="+998 90 123 45 67"
            value={phoneNumber}
            onChange={(event) => setPhoneNumber(event.target.value)}
            hint={
              user?.phoneVerified
                ? 'Verified. Changing it means verifying the new number.'
                : 'International format. Verify it to sign in with a code.'
            }
            {...(fields['phoneNumber'] ? { error: fields['phoneNumber'] } : {})}
          />

          {/* Identifiers other records point at; the API does not change them. */}
          <dl className="profile-fixed">
            <div>
              <dt>Username</dt>
              <dd>{user?.username}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>
                {user?.email}
                <span className="dim"> · {user?.emailVerified ? 'verified' : 'not verified'}</span>
              </dd>
            </div>
          </dl>

          <div>
            <Button type="submit" variant="primary" loading={busy} disabled={!dirty}>
              Save changes
            </Button>
          </div>
        </form>
      </section>

      <section className="profile__section" aria-labelledby="settings-title">
        <h2 id="settings-title" className="section-title">
          Settings
        </h2>
        <ul className="settings-list settings-list--flush">
          <li className="settings-list__row">
            <span className="settings-list__text">
              <span className="settings-list__label">Theme</span>
              <span className="settings-list__hint">Light or dark. Remembered in this browser only.</span>
            </span>
            <ThemeToggle />
          </li>
          {LINKS.map((link) => (
            <li key={link.to}>
              <Link className="settings-list__row" to={link.to}>
                <span className="settings-list__text">
                  <span className="settings-list__label">{link.label}</span>
                  <span className="settings-list__hint">{link.hint}</span>
                </span>
                <ChevronRight className="settings-list__chevron" aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="profile__section" aria-labelledby="privacy-title">
        <h2 id="privacy-title" className="section-title">
          Privacy
        </h2>
        <p className="profile__copy">
          MBank holds your name, email address, phone number, the accounts and cards you opened, and the payments you
          made. It does not track you across other sites and shows no advertising. Your sign-in history is under{' '}
          <Link className="link" to="/security">
            Security
          </Link>
          .
        </p>
      </section>

      <section className="profile__section" aria-labelledby="device-title">
        <h2 id="device-title" className="section-title">
          This device
        </h2>
        <p className="profile__copy">Signing out ends this session. Your other devices stay signed in.</p>
        <SignOutButton />
      </section>
    </div>
  );
}
