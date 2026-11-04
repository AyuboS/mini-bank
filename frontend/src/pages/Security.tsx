import { useState } from 'react';
import {
  AlertTriangle,
  Check,
  KeyRound,
  Laptop,
  LogOut,
  Mail,
  Monitor,
  Shield,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';
import { securityApi } from '../api/security';
import { usersApi } from '../api/users';
import type { LoginHistoryEntry, SessionResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { useToast } from '../components/ToastProvider';
import {
  Badge,
  Button,
  Input,
  Sheet,
  Skeleton,
  Surface,
} from '../components/ui';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState } from '../components/States';
import { formatDateTime, humanise, relativeTime } from '../utils/format';
import { toFriendlyError } from '../utils/errors';

/**
 * The security centre.
 *
 * Everything here is a server-side fact. Ending a session revokes its token
 * family in the database, so the device's next refresh fails at the store —
 * this screen does not merely hide a row.
 */
export function SecurityPage() {
  const { user } = useAuth();
  const toast = useToast();
  const sessions = useAsync(() => securityApi.sessions(), []);
  const history = useAsync(() => securityApi.loginHistory(), []);
  const [changingPassword, setChangingPassword] = useState(false);
  const [busy, setBusy] = useState(false);

  const otherSessions = (sessions.data ?? []).filter((session) => !session.current).length;

  /**
   * The overall posture, from facts rather than from a score.
   *
   * Each condition is something the customer can see and act on in the sections
   * below, so the summary is never a verdict they cannot investigate.
   */
  const concerns: string[] = [];
  if (user && !user.emailVerified) concerns.push('Your email address is not verified');
  if (user && !user.phoneVerified) concerns.push('No verified phone number, so you cannot recover your password');
  if (otherSessions > 2) concerns.push(`${otherSessions} other devices are signed in`);
  const failedRecently = (history.data ?? []).some(
    (entry) => entry.outcome === 'FAILURE' && Date.now() - new Date(entry.at).getTime() < 24 * 3_600_000,
  );
  if (failedRecently) concerns.push('There were failed sign-in attempts in the last day');

  const endSession = async (session: SessionResponse) => {
    setBusy(true);
    try {
      await securityApi.revokeSession(session.id);
      toast.success('Device signed out', `${session.device} can no longer use this session.`);
      sessions.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const endOthers = async () => {
    setBusy(true);
    try {
      const result = await securityApi.revokeOtherSessions();
      toast.success(
        result.sessionsEnded === 0 ? 'No other devices were signed in' : `${result.sessionsEnded} devices signed out`,
        'You are still signed in on this one.',
      );
      sessions.reload();
    } catch (error) {
      const friendly = toFriendlyError(error);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="stack-lg">
      <PageHeader title="Security" subtitle="Who is signed in, what they did, and how to lock it down." />

      {/* ---------------------------------------------------------------- */}
      {/* Status                                                            */}
      {/* ---------------------------------------------------------------- */}
      <Surface tone="hero" className={`security-status ${concerns.length > 0 ? 'security-status--attention' : ''}`}>
        <span className="security-status__icon" aria-hidden="true">
          {concerns.length === 0 ? <ShieldCheck /> : <AlertTriangle />}
        </span>
        <div className="stack" style={{ gap: 4 }}>
          <span className="label">Security status</span>
          <p className="security-status__verdict">{concerns.length === 0 ? 'Good' : 'Needs attention'}</p>
          {concerns.length === 0 ? (
            <p className="dim">Nothing looks out of place.</p>
          ) : (
            <ul className="security-status__list">
              {concerns.map((concern) => (
                <li key={concern}>{concern}</li>
              ))}
            </ul>
          )}
        </div>
      </Surface>

      {/* ---------------------------------------------------------------- */}
      {/* Password and contacts                                             */}
      {/* ---------------------------------------------------------------- */}
      <section className="stack">
        <h2>Sign-in</h2>
        <Surface className="list-panel">
          <div className="setting-row">
            <span className="setting-row__icon" aria-hidden="true">
              <KeyRound />
            </span>
            <div className="grow stack" style={{ gap: 2 }}>
              <span className="setting-row__label">Password</span>
              <span className="dim setting-row__body">
                Changing it signs you out of every device, including this one.
              </span>
            </div>
            <Button variant="secondary" size="sm" onClick={() => setChangingPassword(true)}>
              Change
            </Button>
          </div>

          <div className="setting-row">
            <span className="setting-row__icon" aria-hidden="true">
              <Smartphone />
            </span>
            <div className="grow stack" style={{ gap: 2 }}>
              <span className="setting-row__label">Phone number</span>
              <span className="dim setting-row__body">
                {user?.phoneVerified
                  ? 'Verified. You can sign in with a code and recover a forgotten password.'
                  : 'Not verified. Without it you cannot recover a forgotten password.'}
              </span>
            </div>
            <Badge tone={user?.phoneVerified ? 'positive' : 'warning'} dot>
              {user?.phoneVerified ? 'Verified' : 'Not verified'}
            </Badge>
          </div>

          <div className="setting-row">
            <span className="setting-row__icon" aria-hidden="true">
              <Mail />
            </span>
            <div className="grow stack" style={{ gap: 2 }}>
              <span className="setting-row__label">Email address</span>
              <span className="dim setting-row__body">{user?.email}</span>
            </div>
            <Badge tone={user?.emailVerified ? 'positive' : 'warning'} dot>
              {user?.emailVerified ? 'Verified' : 'Not verified'}
            </Badge>
          </div>
        </Surface>
      </section>

      {/* ---------------------------------------------------------------- */}
      {/* Sessions                                                          */}
      {/* ---------------------------------------------------------------- */}
      <section className="stack">
        <div className="section-head">
          <h2>Signed-in devices</h2>
          {otherSessions > 0 && (
            <Button variant="ghost" size="sm" icon={<LogOut />} onClick={endOthers} loading={busy}>
              Sign out others
            </Button>
          )}
        </div>

        <Surface className="list-panel">
          {sessions.loading ? (
            <Skeleton height="120px" />
          ) : sessions.error ? (
            <ErrorState title={sessions.error.title} body={sessions.error.body} onRetry={sessions.reload} />
          ) : (sessions.data?.length ?? 0) === 0 ? (
            <EmptyState title="No active sessions" icon={<Shield />} />
          ) : (
            sessions.data?.map((session) => (
              <div className="session-row" key={session.id}>
                <span className="session-row__icon" aria-hidden="true">
                  {/iPhone|Android|iPad/.test(session.device) ? <Smartphone /> : /Mac|Windows|Linux/.test(session.device) ? <Laptop /> : <Monitor />}
                </span>

                <div className="grow stack" style={{ gap: 2, minWidth: 0 }}>
                  <span className="session-row__device truncate">
                    {session.device}
                    {session.current && (
                      <Badge tone="brand" dot>
                        This device
                      </Badge>
                    )}
                  </span>
                  <span className="dim session-row__meta truncate">
                    {session.current ? 'Active now' : `Last used ${relativeTime(session.lastUsedAt)}`}
                    {session.ipAddress && ` · ${session.ipAddress}`}
                  </span>
                </div>

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
              </div>
            ))
          )}
        </Surface>
      </section>

      {/* ---------------------------------------------------------------- */}
      {/* History                                                           */}
      {/* ---------------------------------------------------------------- */}
      <section className="stack">
        <h2>Sign-in history</h2>
        <Surface className="list-panel">
          {history.loading ? (
            <Skeleton height="120px" />
          ) : (history.data?.length ?? 0) === 0 ? (
            <EmptyState title="Nothing recorded yet" />
          ) : (
            history.data?.map((entry, index) => <HistoryRow entry={entry} key={`${entry.at}-${index}`} />)
          )}
        </Surface>

        {failedRecently && (
          <p className="security-note">
            Saw a sign-in you do not recognise? Change your password — it signs out every device — and open
            a support ticket.
          </p>
        )}
      </section>

      <ChangePasswordSheet open={changingPassword} onClose={() => setChangingPassword(false)} />
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function HistoryRow({ entry }: { entry: LoginHistoryEntry }) {
  const failed = entry.outcome === 'FAILURE';
  return (
    <div className="history-row">
      <span className={`history-row__icon ${failed ? 'history-row__icon--failed' : ''}`} aria-hidden="true">
        {failed ? <AlertTriangle /> : <Check />}
      </span>
      <div className="grow stack" style={{ gap: 2, minWidth: 0 }}>
        <span className="history-row__action">{humanise(entry.action)}</span>
        <span className="dim history-row__meta truncate">
          {entry.device ?? 'Unknown device'}
          {entry.ipAddress && ` · ${entry.ipAddress}`}
        </span>
      </div>
      <span className="dim history-row__time">{formatDateTime(entry.at)}</span>
    </div>
  );
}

/* ---------------------------------------------------------------------------- */

function ChangePasswordSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast();
  const { logout } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);

  const mismatch = confirm.length > 0 && next !== confirm;

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (mismatch) return;
    setSaving(true);
    try {
      await usersApi.changePassword(current, next);
      toast.success('Password changed', 'Every device has been signed out.');
      /*
        The server revokes every session, including this one, so the tokens in
        this browser are already dead. Signing out locally is what turns that
        into a clean redirect rather than the next request failing with a 401.
      */
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
      <form className="stack-lg" onSubmit={submit}>
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
