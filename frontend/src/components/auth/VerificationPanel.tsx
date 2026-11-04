import { useEffect, useState } from 'react';
import { AtSign, BadgeCheck, Phone, ShieldAlert } from 'lucide-react';
import { usersApi } from '../../api/users';
import type { UserResponse, VerificationChallenge } from '../../api/types';
import { useToast } from '../ToastProvider';
import { Button, Surface, Sheet } from '../ui';
import { OtpInput } from './OtpInput';
import { toFriendlyError } from '../../utils/errors';

interface VerificationPanelProps {
  user: UserResponse;
  onVerified: () => Promise<void> | void;
}

/**
 * Where a customer proves their phone number and email address.
 *
 * A verified phone is a sign-in credential, so this panel is the only way one
 * comes into existence outside registration: the profile form can change the
 * number, but changing it clears the verified flag server-side, and only a code
 * sent to the handset can set it again.
 *
 * The email side is informational rather than gating — an unverified address
 * blocks nothing — which is why it gets a quieter treatment than the phone.
 */
export function VerificationPanel({ user, onVerified }: VerificationPanelProps) {
  const toast = useToast();

  const [challenge, setChallenge] = useState<VerificationChallenge | null>(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setInterval(() => setCooldown((seconds) => Math.max(0, seconds - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [cooldown]);

  const sendPhoneCode = async () => {
    setBusy(true);
    try {
      const response = await usersApi.sendPhoneCode();
      setChallenge(response);
      setCode('');
      setCooldown(response.resendAvailableInSeconds);
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const confirmPhone = async (entered: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await usersApi.confirmPhone(entered);
      await onVerified();
      setChallenge(null);
      setCode('');
      toast.success('Phone number verified', 'You can now sign in with it.');
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
      setCode('');
    } finally {
      setBusy(false);
    }
  };

  const resendEmail = async () => {
    setBusy(true);
    try {
      await usersApi.resendEmailVerification();
      toast.success('Verification email sent', `Check ${user.email} for the link.`);
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  const phoneDone = user.phoneVerified;
  const emailDone = user.emailVerified;

  // Nothing to prompt about once both are proven.
  if (phoneDone && emailDone) {
    return (
      <Surface className="verify-panel verify-panel--done">
        <BadgeCheck aria-hidden="true" />
        <div>
          <p className="verify-panel__title">Your contact details are verified</p>
          <p className="small muted">Both your phone number and your email address are confirmed.</p>
        </div>
      </Surface>
    );
  }

  return (
    <>
      <Surface className="verify-panel">
        <ShieldAlert aria-hidden="true" />
        <div className="grow">
          <p className="verify-panel__title">Finish verifying your account</p>

          <div className="verify-panel__rows">
            {!phoneDone && (
              <div className="verify-panel__row">
                <span className="verify-panel__label">
                  <Phone aria-hidden="true" />
                  {user.phoneNumber ? (
                    <>Phone {user.phoneNumber}</>
                  ) : (
                    <>No phone number on file</>
                  )}
                </span>
                {user.phoneNumber ? (
                  <Button variant="secondary" onClick={sendPhoneCode} disabled={busy}>
                    Verify
                  </Button>
                ) : (
                  <span className="small muted">Add one below, then verify it.</span>
                )}
              </div>
            )}

            {!emailDone && (
              <div className="verify-panel__row">
                <span className="verify-panel__label">
                  <AtSign aria-hidden="true" />
                  {user.email}
                </span>
                <Button variant="secondary" onClick={resendEmail} disabled={busy}>
                  Send link
                </Button>
              </div>
            )}
          </div>

          <p className="small muted" style={{ marginTop: 'var(--space-3)' }}>
            A verified phone number lets you sign in without a password.
          </p>
        </div>
      </Surface>

      <Sheet
        open={challenge !== null}
        onClose={() => setChallenge(null)}
        title="Verify your number"
        subtitle={`We sent a 6-digit code to ${challenge?.destination ?? ''}`}
      >
        <OtpInput
          value={code}
          onChange={setCode}
          onComplete={confirmPhone}
          disabled={busy}
          autoFocus
        />

        {challenge?.developmentCode && (
          <div className="auth-demo">
            <span className="label">Development only — no SMS was sent</span>
            <button
              type="button"
              className="auth-demo__row"
              onClick={() => setCode(challenge.developmentCode ?? '')}
            >
              <span className="mono">{challenge.developmentCode}</span>
              <span className="dim">Tap to fill</span>
            </button>
          </div>
        )}

        <div className="modal-actions">
          <Button variant="secondary" onClick={sendPhoneCode} disabled={cooldown > 0 || busy}>
            {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
          </Button>
          <Button
            variant="primary"
            onClick={() => confirmPhone(code)}
            loading={busy}
            disabled={code.length < 6}
          >
            Verify
          </Button>
        </div>
      </Sheet>
    </>
  );
}
