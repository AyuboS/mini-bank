import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle, ArrowLeft, KeyRound, LogIn, Mail, Phone, User } from 'lucide-react';
import { authApi } from '../api/auth';
import type { VerificationChallenge } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../layouts/AuthLayout';
import { OtpInput } from '../components/auth/OtpInput';
import { PhoneField, toE164 } from '../components/auth/PhoneField';
import { Button, Input } from '../components/ui';
import { CAN_VERIFY_BY_MESSAGE, SHOW_DEMO_ACCOUNTS } from '../utils/deployment';
import { toFriendlyError } from '../utils/errors';
import type { FriendlyError } from '../utils/errors';


const DEMO_ACCOUNTS = [
  { label: 'Customer', username: 'customer.a', password: 'Customer#2026!A' },
  { label: 'Administrator', username: 'admin', password: 'Admin#2026!Demo' },
];

type Method = 'phone' | 'password';

/** Signing in, by phone or by password. */
export function LoginPage() {
  const { login, loginWithPhone } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [method, setMethod] = useState<Method>(CAN_VERIFY_BY_MESSAGE ? 'phone' : 'password');
  const [error, setError] = useState<FriendlyError | null>(null);
  const [busy, setBusy] = useState(false);

  // Password path
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');

  // Phone path
  const [dialCode, setDialCode] = useState('+998');
  const [number, setNumber] = useState('');
  const [challenge, setChallenge] = useState<VerificationChallenge | null>(null);
  const [code, setCode] = useState('');
  const [cooldown, setCooldown] = useState(0);

  const goHome = useCallback(() => {
    const from = (location.state as { from?: string } | null)?.from;
    navigate(from && from !== '/login' ? from : '/', { replace: true });
  }, [location.state, navigate]);

  // The resend countdown. One interval, cleared on unmount and whenever the
  // challenge is replaced, so a second code cannot leave two timers running.
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setInterval(() => setCooldown((seconds) => Math.max(0, seconds - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [cooldown]);

  const sendCode = async () => {
    setError(null);
    setBusy(true);
    try {
      const response = await authApi.requestPhoneCode(toE164(dialCode, number), 'LOGIN');
      setChallenge(response);
      setCode('');
      setCooldown(response.resendAvailableInSeconds);
    } catch (err) {
      setError(toFriendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  // Held in a ref so OtpInput's completion effect does not re-fire when this
  // function's identity changes between renders.
  const submitCodeRef = useRef<(entered: string) => void>(() => {});
  submitCodeRef.current = async (entered: string) => {
    if (busy) return;
    setError(null);
    setBusy(true);
    try {
      await loginWithPhone(toE164(dialCode, number), entered);
      goHome();
    } catch (err) {
      setError(toFriendlyError(err));
      setCode('');
    } finally {
      setBusy(false);
    }
  };

  const submitPassword = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(identifier.trim(), password);
      goHome();
    } catch (err) {
      setError(toFriendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const fillDemoAccount = (demo: (typeof DEMO_ACCOUNTS)[number]) => {
    setMethod('password');
    setIdentifier(demo.username);
    setPassword(demo.password);
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

  // --- The code screen replaces the form entirely -------------------------
  if (method === 'phone' && challenge) {
    return (
      <AuthLayout
        title="Enter your code"
        subtitle={`We sent a 6-digit code to ${challenge.destination}`}
      >
        {banner}

        <div className="auth-fields">
          <OtpInput
            value={code}
            onChange={setCode}
            onComplete={(entered) => submitCodeRef.current(entered)}
            disabled={busy}
            error={Boolean(error)}
            autoFocus
          />

          {/*
            Development only, and the server decides. The code is present in this
            response solely when nothing was actually sent anywhere; a deployment
            with a real SMS provider never returns it, and the prod profile
            refuses to start with the flag enabled at all.
          */}
          {challenge.developmentCode && (
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
          onClick={() => submitCodeRef.current(code)}
          className="mt-6"
        >
          {busy ? 'Verifying…' : 'Verify'}
        </Button>

        <div className="auth-secondary">
          <button
            type="button"
            className="btn btn-ghost"
            onClick={sendCode}
            disabled={cooldown > 0 || busy}
          >
            {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => {
              setChallenge(null);
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

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to your MBank account"
      footer={
        <p className="auth-foot">
          Don&apos;t have an account? <Link to="/register">Create one</Link>
        </p>
      }
    >
      {CAN_VERIFY_BY_MESSAGE && (
        <div className="method-switch" role="tablist" aria-label="Sign-in method">
          <button
            type="button"
            role="tab"
            aria-selected={method === 'phone'}
            className={`method-switch__tab ${method === 'phone' ? 'method-switch__tab--active' : ''}`}
            onClick={() => {
              setMethod('phone');
              setError(null);
            }}
          >
            <Phone aria-hidden="true" />
            Phone
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={method === 'password'}
            className={`method-switch__tab ${method === 'password' ? 'method-switch__tab--active' : ''}`}
            onClick={() => {
              setMethod('password');
              setError(null);
            }}
          >
            <KeyRound aria-hidden="true" />
            Password
          </button>
        </div>
      )}

      {banner}

      {method === 'phone' ? (
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
            We will text you a 6-digit code. Standard rates apply.
          </p>
        </form>
      ) : (
        <form onSubmit={submitPassword} noValidate>
          <div className="auth-fields">
            <Input
              label="Username, email or phone"
              icon={<User aria-hidden="true" />}
              autoComplete="username"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              required
              {...(error?.fields['username'] ? { error: error.fields['username'] } : {})}
            />

            <Input
              label="Password"
              icon={<KeyRound aria-hidden="true" />}
              autoComplete="current-password"
              revealable
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              {...(error?.fields['password'] ? { error: error.fields['password'] } : {})}
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            size="lg"
            block
            loading={busy}
            icon={<LogIn aria-hidden="true" />}
            className="mt-6"
          >
            {busy ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
      )}

      {CAN_VERIFY_BY_MESSAGE && (
        <>
        <div className="auth-alt">
          <span>or</span>
        </div>

        <button
          type="button"
          className="btn btn-secondary btn-block"
          onClick={() => {
            setMethod(method === 'phone' ? 'password' : 'phone');
            setError(null);
          }}
        >
          {method === 'phone' ? (
            <>
              <Mail aria-hidden="true" />
              Sign in with a password
            </>
          ) : (
            <>
              <Phone aria-hidden="true" />
              Sign in with your phone
            </>
          )}
        </button>
        </>
      )}

      {SHOW_DEMO_ACCOUNTS && (
        <div className="auth-demo">
          <span className="label">Demo accounts</span>
          {DEMO_ACCOUNTS.map((demo) => (
            <button key={demo.username} type="button" className="auth-demo__row" onClick={() => fillDemoAccount(demo)}>
              <span>{demo.username}</span>
              <span className="dim">{demo.label}</span>
            </button>
          ))}
          <p className="field-hint">
            Tap to fill. These accounts exist only in the demo database.
            {!CAN_VERIFY_BY_MESSAGE && ' This demo sends no text messages, so signing in by phone is switched off.'}
          </p>
        </div>
      )}
    </AuthLayout>
  );
}
