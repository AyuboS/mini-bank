import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Check, KeyRound } from 'lucide-react';
import { securityApi } from '../api/security';
import { AuthLayout } from '../layouts/AuthLayout';
import { Button, Input, Surface } from '../components/ui';
import { OtpInput } from '../components/auth/OtpInput';
import { PhoneField, toE164 } from '../components/auth/PhoneField';
import { toFriendlyError } from '../utils/errors';

type Step = 'phone' | 'code' | 'password' | 'done';

/** Recovering a forgotten password. */
export function ForgotPasswordPage() {
  const navigate = useNavigate();

  const [step, setStep] = useState<Step>('phone');
  /* The number is held as its two halves, the way PhoneField works with it, and
     joined into E.164 only when a request is actually made. */
  const [dialCode, setDialCode] = useState('+371');
  const [number, setNumber] = useState('');
  const phone = toE164(dialCode, number);
  const [code, setCode] = useState('');
  const [ticket, setTicket] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [developmentCode, setDevelopmentCode] = useState<string | null>(null);

  const mismatch = confirm.length > 0 && password !== confirm;

  const requestCode = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const challenge = await securityApi.requestPasswordReset(phone);
      // Present only when the deployment opts in, and never in production.
      setDevelopmentCode(challenge.developmentCode ?? null);
      setStep('code');
    } catch (caught) {
      const friendly = toFriendlyError(caught);
      setError(friendly.body ?? friendly.title);
    } finally {
      setBusy(false);
    }
  };

  const verifyCode = async (value: string) => {
    setBusy(true);
    setError(null);
    try {
      const issued = await securityApi.verifyPasswordReset(phone, value);
      setTicket(issued.token);
      setStep('password');
    } catch (caught) {
      const friendly = toFriendlyError(caught);
      setError(friendly.body ?? friendly.title);
      setCode('');
    } finally {
      setBusy(false);
    }
  };

  const setNewPassword = async (event: React.FormEvent) => {
    event.preventDefault();
    if (mismatch) return;
    setBusy(true);
    setError(null);
    try {
      await securityApi.completePasswordReset(phone, ticket, password);
      setStep('done');
    } catch (caught) {
      const friendly = toFriendlyError(caught);
      setError(friendly.body ?? friendly.title);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title={step === 'done' ? 'Password changed' : 'Reset your password'}
      subtitle={
        step === 'phone'
          ? 'Enter the phone number on your account and we will send a code to it.'
          : step === 'code'
            ? 'Enter the six digits from the message.'
            : step === 'password'
              ? 'Choose a new password. Every device will be signed out.'
              : 'You can sign in with your new password now.'
      }
      footer={
        step !== 'done' ? (
          <p className="auth-footer">
            <Link to="/login" className="row gap-2 justify-center">
              <ArrowLeft size={16} aria-hidden="true" /> Back to sign in
            </Link>
          </p>
        ) : undefined
      }
    >
      {error && (
        <p className="auth-error" role="alert">
          {error}
        </p>
      )}

      {step === 'phone' && (
        <form className="stack-lg" onSubmit={requestCode}>
          <PhoneField
            dialCode={dialCode}
            onDialCodeChange={setDialCode}
            number={number}
            onNumberChange={setNumber}
            disabled={busy}
            autoFocus
          />
          <Button variant="primary" size="lg" block type="submit" loading={busy}>
            Send code
          </Button>
          <p className="dim auth-note">
            If there is an account with that number, a code is on its way. For your security we do not
            say whether a number is registered.
          </p>
        </form>
      )}

      {step === 'code' && (
        <div className="stack-lg">
          {developmentCode && (
            <Surface tone="chrome" className="dev-code">
              <span className="label">Development build</span>
              <p>
                No SMS gateway is configured, so the code is shown here instead of being sent:{' '}
                <strong className="mono">{developmentCode}</strong>
              </p>
            </Surface>
          )}

          <OtpInput
            value={code}
            onChange={setCode}
            onComplete={verifyCode}
            disabled={busy}
            error={Boolean(error)}
            autoFocus
            label="Verification code"
          />

          <Button
            variant="primary"
            size="lg"
            block
            onClick={() => verifyCode(code)}
            loading={busy}
            disabled={code.length < 6}
          >
            Verify
          </Button>

          <Button variant="ghost" block onClick={() => setStep('phone')} type="button">
            Use a different number
          </Button>
        </div>
      )}

      {step === 'password' && (
        <form className="stack-lg" onSubmit={setNewPassword}>
          <Input
            label="New password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            icon={<KeyRound />}
            hint="At least 12 characters, with upper and lower case, a digit and a symbol."
            autoFocus
            required
          />
          <Input
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
            error={mismatch ? 'These do not match' : undefined}
            required
          />

          <Button variant="primary" size="lg" block type="submit" loading={busy} disabled={mismatch}>
            Set new password
          </Button>

          <p className="dim auth-note">
            Setting a new password signs out every device that was using the old one.
          </p>
        </form>
      )}

      {step === 'done' && (
        <div className="stack-lg">
          <div className="reset-done">
            <span className="reset-done__mark" aria-hidden="true">
              <Check />
            </span>
            <p className="dim">
              Your password has been changed and every device has been signed out.
            </p>
          </div>

          <Button variant="primary" size="lg" block onClick={() => navigate('/login')}>
            Sign in
          </Button>
        </div>
      )}
    </AuthLayout>
  );
}
