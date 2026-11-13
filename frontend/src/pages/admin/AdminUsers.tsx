import { useState } from 'react';
import { adminApi } from '../../api/admin';
import type { AdminUserResponse, PageResponse, UserStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorState } from '../../components/States';
import { useToast } from '../../components/ToastProvider';
import { Button, Input, Select, Sheet, Skeleton } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { toFriendlyError } from '../../utils/errors';
import { formatDay, humanise, relativeTime } from '../../utils/format';
import { AdminPager, FilterRow } from './AdminParts';

const FILTERS = [
  { id: '', label: 'All' },
  { id: 'ACTIVE', label: 'Active' },
  { id: 'LOCKED', label: 'Locked' },
  { id: 'SUSPENDED', label: 'Suspended' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];

/** Users, with suspend and restore. */
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

  const openChange = (user: AdminUserResponse, status: UserStatus) => {
    setTarget(user);
    setNextStatus(status);
    setReason('');
  };

  const applyStatus = async () => {
    if (!target) return;
    setBusy(true);
    try {
      await adminApi.changeUserStatus(target.id, nextStatus, reason.trim());
      toast.success('Status changed', `${target.username} is now ${nextStatus.toLowerCase()}.`);
      setTarget(null);
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
      <FilterRow
        label="Filter users by status"
        options={FILTERS}
        value={filter}
        onChange={(value) => {
          setFilter(value);
          setPage(0);
        }}
      />

      {loading && !data ? (
        <Skeleton height={320} />
      ) : error || !data ? (
        <ErrorState body={error?.body ?? 'We could not load the users.'} onRetry={reload} />
      ) : data.content.length === 0 ? (
        <p className="quiet-empty">No users match this filter.</p>
      ) : (
        <>
          <table className="admin-table">
            <thead>
              <tr>
                <th>User</th>
                <th>Contact</th>
                <th>Status</th>
                <th>Last sign-in</th>
                <th>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((user) => (
                <tr key={user.id}>
                  <td>
                    {user.fullName}
                    <span className="admin-table__sub">
                      @{user.username}
                      {user.role === 'ADMIN' && ' · administrator'}
                    </span>
                  </td>
                  <td data-wide-only>
                    {user.email}
                    {/* Masked by the server. */}
                    <span className="admin-table__sub admin-table__mono">{user.maskedPhoneNumber ?? 'No phone'}</span>
                  </td>
                  <td className={user.status === 'ACTIVE' ? undefined : 'admin-table__flagged'}>
                    {humanise(user.status)}
                    {user.failedLoginAttempts > 0 && (
                      <span className="admin-table__sub">
                        {user.failedLoginAttempts} failed {user.failedLoginAttempts === 1 ? 'attempt' : 'attempts'}
                      </span>
                    )}
                  </td>
                  <td data-wide-only className="dim">
                    {user.lastLoginAt ? relativeTime(user.lastLoginAt) : 'Never'}
                  </td>
                  <td>
                    <div className="admin-table__actions">
                      {/* Suspending yourself would end your own session mid-task. */}
                      {user.id === currentUser?.id ? (
                        <span className="dim">You</span>
                      ) : user.status === 'ACTIVE' ? (
                        <Button size="sm" variant="ghost" onClick={() => openChange(user, 'SUSPENDED')} aria-label={`Suspend ${user.username}`}>
                          Suspend
                        </Button>
                      ) : (
                        <Button size="sm" variant="secondary" onClick={() => openChange(user, 'ACTIVE')} aria-label={`Restore ${user.username}`}>
                          Restore
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <AdminPager page={data} noun="users" onPage={setPage} />
        </>
      )}

      <Sheet
        open={target !== null}
        onClose={() => setTarget(null)}
        title={nextStatus === 'ACTIVE' ? 'Restore access?' : 'Change status'}
        subtitle={target ? `${target.fullName} · @${target.username} · since ${formatDay(target.createdAt)}` : ''}
      >
        <form
          className="sheet-form"
          onSubmit={(event) => {
            event.preventDefault();
            void applyStatus();
          }}
        >
          <p className="admin-note">
            {nextStatus === 'ACTIVE'
              ? 'They can sign in again, and the failed-attempt counter is cleared.'
              : 'Every active session ends at once. Their accounts and history are untouched.'}
          </p>
          <Select label="New status" value={nextStatus} onChange={(event) => setNextStatus(event.target.value as UserStatus)}>
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
          <div className="modal-actions">
            <Button type="button" variant="secondary" onClick={() => setTarget(null)} disabled={busy}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={busy}>
              Apply
            </Button>
          </div>
        </form>
      </Sheet>
    </>
  );
}
