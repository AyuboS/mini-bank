import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AlertCircle, ArrowLeft, AtSign, Check, KeyRound, Phone, User, UserPlus } from 'lucide-react';
import { authApi } from '../api/auth';
import { ApiRequestError } from '../api/client';
import type { VerificationChallenge } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../layouts/AuthLayout';
import { OtpInput } from '../components/auth/OtpInput';
import { PhoneField, toE164 } from '../components/auth/PhoneField';
import { Button, Input } from '../components/ui';
import { CAN_VERIFY_BY_MESSAGE } from '../utils/deployment';
import { toFriendlyError } from '../utils/errors';
import type { FriendlyError } from '../utils/errors';

const PASSWORD_HINT = 'At least 12 characters, with upper and lower case, a digit and a symbol.';

/** Where in the sign-up somebody is. Email-only registration skips straight to `details`. */
type Step = 'method' | 'verify' | 'details';

/** Opening an account. */
export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  // A public demo cannot text a code, so it goes straight to the details.
  const [step, setStep] = useState<Step>(CAN_VERIFY_BY_MESSAGE ? 'method' : 'details');
  const [withPhone, setWithPhone] = useState(CAN_VERIFY_BY_MESSAGE);

  const [dialCode, setDialCode] = useState('+998');
  const [number, setNumber] = useState('');
  const [challenge, setChallenge] = useState<VerificationChallenge | null>(null);
  const [code, setCode] = useState('');
  const [cooldown, setCooldown] = useState(0);
  const [verificationToken, setVerificationToken] = useState<string | null>(null);

  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    username: '',
    email: '',
    password: '',
  });

  const [error, setError] = useState<FriendlyError | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setInterval(() => setCooldown((seconds) => Math.max(0, seconds - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [cooldown]);

  const update = (field: keyof typeof form) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const sendCode = async () => {
    setError(null);
    setBusy(true);
    try {
      const response = await authApi.requestPhoneCode(toE164(dialCode, number), 'REGISTRATION');
      setChallenge(response);
      setCode('');
      setCooldown(response.resendAvailableInSeconds);
      setStep('verify');
    } catch (err) {
      setError(toFriendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const confirmRef = useRef<(entered: string) => void>(() => {});
  confirmRef.current = async (entered: string) => {
    if (busy) return;
    setError(null);
    setBusy(true);
    try {
      const ticket = await authApi.confirmPhone(toE164(dialCode, number), entered);
      setVerificationToken(ticket.token);
      setStep('details');
    } catch (err) {
      setError(toFriendlyError(err));
      setCode('');
    } finally {
      setBusy(false);
    }
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await register({
        ...form,
        username: form.username.trim(),
        email: form.email.trim(),
        // Sent together or not at all: the server rejects a number without proof.
        ...(withPhone && verificationToken
          ? { phoneNumber: toE164(dialCode, number), verificationToken }
          : {}),
      });
      navigate('/', { replace: true });
    } catch (err) {
      setError(toFriendlyError(err));
      // A spent or expired ticket cannot be reused, so send them back to redo
      // the one step that failed rather than the whole form. Branching on the
      // machine-readable code, never on the message: the wording is rewritten
      // freely, and a check against it would break silently the day it is.
      if (err instanceof ApiRequestError && err.code === 'VERIFICATION_REQUIRED') {
        setVerificationToken(null);
        setStep('method');
      }
    } finally {
      setBusy(false);
    }
  };

  const banner = error && (
    <div className="auth-error" role="alert">
      <AlertCircle aria-hidden="true" />
      <span>
        <span className="auth-error__title">{error.title}</span>
        {error.body && <span className="auth-error__body">{error.body}</span>}
      </span>
    </div>
  );

  const footer = (
    <p className="auth-foot">
      Already registered? <Link to="/login">Sign in</Link>
    </p>
  );

  // --- Step 1: how ---------------------------------------------------------
  if (step === 'method') {
    return (
      <AuthLayout
        title="Create your MBank account"
        subtitle="Your first EUR account is opened automatically"
        footer={footer}
      >
        <div className="method-switch" role="tablist" aria-label="Sign-up method">
          <button
            type="button"
            role="tab"
            aria-selected={withPhone}
            className={`method-switch__tab ${withPhone ? 'method-switch__tab--active' : ''}`}
            onClick={() => {
              setWithPhone(true);
              setError(null);
            }}
          >
            <Phone aria-hidden="true" />
            Phone
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={!withPhone}
            className={`method-switch__tab ${!withPhone ? 'method-switch__tab--active' : ''}`}
            onClick={() => {
              setWithPhone(false);
              setError(null);
            }}
          >
            <AtSign aria-hidden="true" />
            Email
          </button>
        </div>

        {banner}

        {withPhone ? (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void sendCode();
            }}
            noValidate
          >
            <div className="auth-fields">
              <PhoneField
                dialCode={dialCode}
                onDialCodeChange={setDialCode}
                number={number}
                onNumberChange={setNumber}
                disabled={busy}
                {...(error?.fields['phoneNumber'] ? { error: error.fields['phoneNumber'] } : {})}
              />
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              block
              loading={busy}
              disabled={number.replace(/\D/g, '').length < 6}
              className="mt-6"
            >
              {busy ? 'Sending…' : 'Send code'}
            </Button>

            <p className="field-hint mt-3 text-center">
              We verify your number first, so you can sign in with it later.
            </p>
          </form>
        ) : (
          <>
            <p className="small muted mb-4">
              You can add and verify a phone number later, from your profile.
            </p>
            <Button variant="primary" size="lg" block onClick={() => setStep('details')}>
              Continue with email
            </Button>
          </>
        )}
      </AuthLayout>
    );
  }

  // --- Step 2: the code ----------------------------------------------------
  if (step === 'verify') {
    return (
      <AuthLayout
        title="Verify your number"
        subtitle={`We sent a 6-digit code to ${challenge?.destination ?? 'your phone'}`}
      >
        {banner}

        <div className="auth-fields">
          <OtpInput
            value={code}
            onChange={setCode}
            onComplete={(entered) => confirmRef.current(entered)}
            disabled={busy}
            error={Boolean(error)}
            autoFocus
          />

          {challenge?.developmentCode && (
            <div className="auth-demo mt-0">
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
        </div>

        <Button
          type="button"
          variant="primary"
          size="lg"
          block
          loading={busy}
          disabled={code.length < 6}
          onClick={() => confirmRef.current(code)}
          className="mt-6"
        >
          {busy ? 'Verifying…' : 'Verify number'}
        </Button>

        <div className="auth-secondary">
          <button type="button" className="btn btn-ghost" onClick={sendCode} disabled={cooldown > 0 || busy}>
            {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => {
              setStep('method');
              setCode('');
              setError(null);
            }}
          >
            <ArrowLeft aria-hidden="true" />
            Change number
          </button>
        </div>
      </AuthLayout>
    );
  }

  // --- Step 3: the rest ----------------------------------------------------
  return (
    <AuthLayout
      title="A few details"
      subtitle="This is the last step."
      footer={footer}
    >
      <form onSubmit={submit} noValidate>
        {banner}

        {withPhone && verificationToken && (
          <div className="verified-chip">
            <Check aria-hidden="true" />
            <span>
              {toE164(dialCode, number)} verified
            </span>
          </div>
        )}

        <div className="auth-fields">
          <div className="row gap-3">
            <Input
              label="First name"
              className="grow"
              value={form.firstName}
              onChange={update('firstName')}
              autoComplete="given-name"
              required
              {...(error?.fields['firstName'] ? { error: error.fields['firstName'] } : {})}
            />
            <Input
              label="Last name"
              className="grow"
              value={form.lastName}
              onChange={update('lastName')}
              autoComplete="family-name"
              required
              {...(error?.fields['lastName'] ? { error: error.fields['lastName'] } : {})}
            />
          </div>

          <Input
            label="Username"
            icon={<User aria-hidden="true" />}
            value={form.username}
            onChange={update('username')}
            autoComplete="username"
            minLength={3}
            required
            {...(error?.fields['username'] ? { error: error.fields['username'] } : {})}
          />

          <Input
            label="Email"
            type="email"
            icon={<AtSign aria-hidden="true" />}
            value={form.email}
            onChange={update('email')}
            autoComplete="email"
            required
            hint="We will send a link to confirm it."
            {...(error?.fields['email'] ? { error: error.fields['email'] } : {})}
          />

          <Input
            label="Password"
            icon={<KeyRound aria-hidden="true" />}
            revealable
            value={form.password}
            onChange={update('password')}
            autoComplete="new-password"
            minLength={12}
            required
            hint={PASSWORD_HINT}
            {...(error?.fields['password'] ? { error: error.fields['password'] } : {})}
          />
        </div>

        <Button
          type="submit"
          variant="primary"
          size="lg"
          block
          loading={busy}
          icon={<UserPlus aria-hidden="true" />}
          className="mt-6"
        >
          {busy ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
    </AuthLayout>
  );
}
