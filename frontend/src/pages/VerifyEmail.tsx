import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle } from 'lucide-react';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '../layouts/AuthLayout';
import { Button } from '../components/ui';
import { toFriendlyError } from '../utils/errors';
import type { FriendlyError } from '../utils/errors';

type State = { status: 'working' } | { status: 'done' } | { status: 'failed'; error: FriendlyError };

/** The landing page for a verification link. */
export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const { user, refreshUser } = useAuth();
  const [state, setState] = useState<State>({ status: 'working' });

  // React runs effects twice in StrictMode, and this token is single use: the
  // second call would redeem nothing and report failure on a link that had just
  // worked. The guard makes the redemption happen exactly once.
  const attempted = useRef(false);

  useEffect(() => {
    const token = params.get('token');
    if (attempted.current) return;
    attempted.current = true;

    if (!token) {
      setState({
        status: 'failed',
        error: { title: 'This link is incomplete', body: 'Ask for a new one from your profile.', fields: {} },
      });
      return;
    }

    void (async () => {
      try {
        await authApi.verifyEmail(token);
        setState({ status: 'done' });
        if (user) {
          // Best effort: the address is verified either way.
          await refreshUser().catch(() => {});
        }
      } catch (err) {
        setState({ status: 'failed', error: toFriendlyError(err) });
      }
    })();
    // Runs once, on the token present at mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (state.status === 'working') {
    return (
      <AuthLayout title="Verifying your email" subtitle="One moment.">
        <div className="verify-result">
          <span className="btn-spinner btn-spinner--lg" aria-hidden="true" />
        </div>
      </AuthLayout>
    );
  }

  if (state.status === 'done') {
    return (
      <AuthLayout title="Email verified" subtitle="Thank you — that is all we needed.">
        <div className="verify-result verify-result--ok">
          <CheckCircle2 aria-hidden="true" />
        </div>
        <Button
          variant="primary"
          size="lg"
          block
          onClick={() => window.location.assign(user ? '/' : '/login')}
          className="mt-6"
        >
          {user ? 'Back to MBank' : 'Sign in'}
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={state.error.title}
      subtitle={state.error.body ?? 'The link may have expired, or already been used.'}
      footer={
        <p className="auth-foot">
          <Link to="/login">Back to sign in</Link>
        </p>
      }
    >
      <div className="verify-result verify-result--bad">
        <XCircle aria-hidden="true" />
      </div>
      <p className="small muted text-center mt-5">
        Verification links last 24 hours and work once. You can request a new one from your profile
        after signing in.
      </p>
    </AuthLayout>
  );
}
