import { useState } from 'react';
import { Ban, CheckCircle2, Snowflake } from 'lucide-react';
import { adminApi } from '../../api/admin';
import type { AdminUserResponse, PageResponse, UserStatus } from '../../api/types';
import { useAsync } from '../../hooks/useAsync';
import { useAuth } from '../../auth/AuthContext';
import { useToast } from '../../components/ToastProvider';
import { ErrorState } from '../../components/States';
import {
  Avatar,
  Badge,
  Button,
  Surface,
  Input,
  Sheet,
  Select,
  Skeleton,
  Tabs,
} from '../../components/ui';
import { formatDate, humanise, initials, relativeTime, toneFor } from '../../utils/format';
import { toFriendlyError } from '../../utils/errors';

const FILTERS = [
  { id: '', label: 'All' },
  { id: 'ACTIVE', label: 'Active' },
  { id: 'LOCKED', label: 'Locked' },
  { id: 'SUSPENDED', label: 'Suspended' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];

const STATUS_TONE: Record<UserStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  ACTIVE: 'success',
  LOCKED: 'warning',
  SUSPENDED: 'danger',
  DELETED: 'neutral',
};

export function AdminUsers() {
  const toast = useToast();
  const { user: currentUser } = useAuth();
  const [filter, setFilter] = useState<Filter>('');
  const [page, setPage] = useState(0);
  const [target, setTarget] = useState<AdminUserResponse | null>(null);
  const [nextStatus, setNextStatus] = useState<UserStatus>('SUSPENDED');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const { data, loading, error, reload } = useAsync<PageResponse<AdminUserResponse>>(
    () => adminApi.users(page, 12, filter as UserStatus | ''),
    [page, filter],
  );

  const applyStatus = async () => {
    if (!target) return;
    setBusy(true);
    try {
      await adminApi.changeUserStatus(target.id, nextStatus, reason.trim());
      toast.success('Status updated', `${target.username} is now ${nextStatus.toLowerCase()}.`);
      setTarget(null);
      setReason('');
      await reload();
    } catch (err) {
      const friendly = toFriendlyError(err);
      toast.error(friendly.title, friendly.body);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Surface tone="chrome" className="filter-bar">
        <Tabs
          tabs={FILTERS}
          active={filter}
          label="Filter users by status"
          onChange={(value) => {
            setFilter(value as Filter);
            setPage(0);
          }}
        />
      </Surface>

      {loading && <Skeleton height={320} />}

      {!loading && (error || !data) && (
        <ErrorState body={error?.body ?? 'We could not load the customer list.'} onRetry={reload} />
      )}

      {!loading && data && (
        <>
          <Surface className="table-wrap">
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>Customer</th>
                    <th>Contact</th>
                    <th>Status</th>
                    <th>Last seen</th>
                    <th aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((user) => (
                    <tr key={user.id}>
                      <td>
                        <div className="row gap-3">
                          <Avatar
                            initials={initials(user.fullName.split(' ')[0], user.fullName.split(' ')[1])}
                            tone={toneFor(user.username)}
                            size="sm"
                          />
                          <div>
                            <div style={{ fontWeight: 540 }}>{user.fullName}</div>
                            <div className="dim" style={{ fontSize: 11 }}>
                              @{user.username}
                              {user.role === 'ADMIN' && ' · admin'}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div>{user.email}</div>
                        {/* Masked by the server, not by this component. */}
                        <div className="dim mono" style={{ fontSize: 11 }}>
                          {user.maskedPhoneNumber ?? '—'}
                        </div>
                      </td>
                      <td>
                        <Badge tone={STATUS_TONE[user.status]}>{humanise(user.status)}</Badge>
                        {user.failedLoginAttempts > 0 && (
                          <div className="dim" style={{ fontSize: 11, marginTop: 4 }}>
                            {user.failedLoginAttempts} failed{' '}
                            {user.failedLoginAttempts === 1 ? 'attempt' : 'attempts'}
                          </div>
                        )}
                      </td>
                      <td className="dim" style={{ whiteSpace: 'nowrap' }}>
                        {user.lastLoginAt ? relativeTime(user.lastLoginAt) : 'never'}
                      </td>
                      <td>
                        <div className="row gap-2" style={{ justifyContent: 'flex-end' }}>
                          {/*
                            Suspending yourself would revoke your own sessions and
                            lock you out of the console mid-task. The server would
                            happily do it, so the guard belongs here.
                          */}
                          {user.id === currentUser?.id ? (
                            <span className="small dim">You</span>
                          ) : user.status === 'ACTIVE' ? (
                            <Button
                              variant="secondary"
                              onClick={() => {
                                setTarget(user);
                                setNextStatus('SUSPENDED');
                              }}
                              icon={<Ban aria-hidden="true" />}
                            >
                              Suspend
                            </Button>
                          ) : (
                            <Button
                              variant="secondary"
                              onClick={() => {
                                setTarget(user);
                                setNextStatus('ACTIVE');
                              }}
                              icon={<CheckCircle2 aria-hidden="true" />}
                            >
                              Restore
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Surface>

          <div className="pager">
            <Button variant="ghost" disabled={data.first} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <span className="small dim">
              Page {data.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} customers
            </span>
            <Button variant="ghost" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </>
      )}

      <Sheet
        open={target !== null}
        onClose={() => setTarget(null)}
        title={nextStatus === 'ACTIVE' ? 'Restore access?' : 'Change customer status'}
        subtitle={target ? `${target.fullName} · @${target.username}` : ''}
      >
        <p className="small dim">
          {nextStatus === 'ACTIVE'
            ? 'The customer will be able to sign in again, and any lockout counter is cleared.'
            : 'Suspending immediately revokes every active session. Their accounts and history are untouched.'}
        </p>

        <div className="stack gap-4" style={{ marginTop: 'var(--space-5)' }}>
          <Select
            label="New status"
            value={nextStatus}
            onChange={(event) => setNextStatus(event.target.value as UserStatus)}
          >
            <option value="ACTIVE">Active</option>
            <option value="SUSPENDED">Suspended</option>
            <option value="LOCKED">Locked</option>
          </Select>

          <Input
            label="Reason"
            placeholder="Reported stolen device"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={255}
            hint="Recorded in the audit trail."
          />
        </div>

        {target && (
          <p className="field-hint" style={{ marginTop: 'var(--space-4)' }}>
            <Snowflake aria-hidden="true" style={{ width: 12, height: 12, display: 'inline', marginRight: 4 }} />
            Customer since {formatDate(target.createdAt)}
          </p>
        )}

        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setTarget(null)} disabled={busy}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy} onClick={applyStatus}>
            Apply
          </Button>
        </div>
      </Sheet>
    </>
  );
}
