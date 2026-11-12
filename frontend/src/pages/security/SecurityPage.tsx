import { useState } from 'react';
import { securityApi } from '../../api/security';
import type { SessionResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { ErrorState } from '../../components/States';
import { useToast } from '../../components/ToastProvider';
import { Button, Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { toFriendlyError } from '../../utils/errors';
import { formatDate, humanise, relativeTime } from '../../utils/format';
import { ChangePasswordSheet } from './ChangePasswordSheet';

const DAY_MS = 24 * 3_600_000;

/**
 * Sign-in settings, devices and history. Ending a session revokes its token
 * family on the server, so the device's next refresh fails.
 */
export function SecurityPage() {
  const { user } = useAuth();
  const toast = useToast();
  const sessions = useAsync(() => securityApi.sessions(), []);
  const history = useAsync(() => securityApi.loginHistory(), []);
  const [changingPassword, setChangingPassword] = useState(false);
  const [busy, setBusy] = useState(false);

  const otherSessions = (sessions.data ?? []).filter((session) => !session.current).length;
  const failedRecently = (history.data ?? []).some(
    (entry) => entry.outcome === 'FAILURE' && Date.now() - new Date(entry.at).getTime() < DAY_MS,
  );

  // Each item is something the sections below let the customer check or fix.
  const concerns: string[] = [];
  if (user && !user.emailVerified) concerns.push('Your email address is not verified.');
  if (user && !user.phoneVerified) concerns.push('No verified phone number, so a forgotten password cannot be reset.');
  if (otherSessions > 2) concerns.push(`${otherSessions} other devices are signed in.`);
  if (failedRecently) concerns.push('There were failed sign-in attempts in the last day.');

  const act = async (work: () => Promise<void>) => {
    setBusy(true);
    try {
      await work();
      void sessions.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const endSession = (session: SessionResponse) =>
    act(async () => {
      await securityApi.revokeSession(session.id);
      toast.success('Device signed out', session.device);
    });

  const endOthers = () =>
    act(async () => {
      const result = await securityApi.revokeOtherSessions();
      toast.success(
        result.sessionsEnded === 0 ? 'No other devices were signed in' : `${result.sessionsEnded} devices signed out`,
        'This one is still signed in.',
      );
    });

  return (
    <div className="profile">
      <PageHeader title="Security" subtitle="How you sign in, where you are signed in, and what happened." />

      {concerns.length > 0 ? (
        <div className="attention" role="region" aria-label="Needs attention">
          <p className="attention__title">Needs attention</p>
          <ul>
            {concerns.map((concern) => (
              <li key={concern}>{concern}</li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="profile__copy">Nothing needs your attention.</p>
      )}

      <section className="profile__section" aria-labelledby="signin-title">
        <h2 id="signin-title" className="section-title">
          Sign-in
        </h2>
        <ul className="settings-list settings-list--flush">
          <li className="settings-list__row">
            <span className="settings-list__text">
              <span className="settings-list__label">Password</span>
              <span className="settings-list__hint">Changing it signs out every device, this one included.</span>
            </span>
            <Button variant="secondary" size="sm" onClick={() => setChangingPassword(true)}>
              Change
            </Button>
          </li>
          <li className="settings-list__row">
            <span className="settings-list__text">
              <span className="settings-list__label">Phone number</span>
              <span className="settings-list__hint">
                {user?.phoneVerified ? 'Sign in with a code and reset a forgotten password.' : 'Verify it on your Profile.'}
              </span>
            </span>
            <span className={user?.phoneVerified ? 'settings-list__value' : 'settings-list__value settings-list__value--warn'}>
              {user?.phoneVerified ? 'Verified' : 'Not verified'}
            </span>
          </li>
          <li className="settings-list__row">
            <span className="settings-list__text">
              <span className="settings-list__label">Email address</span>
              <span className="settings-list__hint">{user?.email}</span>
            </span>
            <span className={user?.emailVerified ? 'settings-list__value' : 'settings-list__value settings-list__value--warn'}>
              {user?.emailVerified ? 'Verified' : 'Not verified'}
            </span>
          </li>
        </ul>
      </section>

      <section className="profile__section" aria-labelledby="devices-title">
        <div className="section-title section-title--with-action">
          <h2 id="devices-title">Signed-in devices</h2>
          {otherSessions > 0 && (
            <Button variant="ghost" size="sm" onClick={endOthers} disabled={busy}>
              Sign out the others
            </Button>
          )}
        </div>
        {sessions.loading && !sessions.data ? (
          <Skeleton height="120px" />
        ) : sessions.error ? (
          <ErrorState title={sessions.error.title} body={sessions.error.body} onRetry={sessions.reload} />
        ) : (
          <ul className="settings-list settings-list--flush">
            {(sessions.data ?? []).map((session) => (
              <li className="settings-list__row" key={session.id}>
                <span className="settings-list__text">
                  <span className="settings-list__label">
                    {session.device}
                    {session.current && <span className="dim"> · this device</span>}
                  </span>
                  <span className="settings-list__hint">
                    {session.current ? 'Active now' : `Last used ${relativeTime(session.lastUsedAt)}`}
                    {session.ipAddress && ` · ${session.ipAddress}`}
                  </span>
                </span>
                {!session.current && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => endSession(session)}
                    disabled={busy}
                    aria-label={`Sign out ${session.device}`}
                  >
                    Sign out
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="profile__section" aria-labelledby="history-title">
        <h2 id="history-title" className="section-title">
          Sign-in history
        </h2>
        {history.loading && !history.data ? (
          <Skeleton height="120px" />
        ) : (history.data?.length ?? 0) === 0 ? (
          <p className="quiet-empty">Nothing recorded yet.</p>
        ) : (
          <ul className="settings-list settings-list--flush">
            {history.data?.map((entry, index) => (
              <li className="settings-list__row" key={`${entry.at}-${index}`}>
                <span className="settings-list__text">
                  <span className={`settings-list__label ${entry.outcome === 'FAILURE' ? 'settings-list__label--failed' : ''}`}>
                    {humanise(entry.action)}
                    {entry.outcome === 'FAILURE' && ' · failed'}
                  </span>
                  <span className="settings-list__hint">
                    {entry.device ?? 'Unknown device'}
                    {entry.ipAddress && ` · ${entry.ipAddress}`}
                  </span>
                </span>
                <span className="settings-list__value settings-list__value--time">{formatDate(entry.at)}</span>
              </li>
            ))}
          </ul>
        )}
        {failedRecently && (
          <p className="profile__copy">
            Do not recognise a sign-in? Change your password, which signs out every device, and contact support.
          </p>
        )}
      </section>

      <ChangePasswordSheet open={changingPassword} onClose={() => setChangingPassword(false)} />
    </div>
  );
}
