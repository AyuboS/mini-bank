import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError, tokenStore } from '../api/client';
import { usersApi } from '../api/users';
import type { AuthenticationResponse, UserResponse } from '../api/types';
import { AuthProvider, useAuth } from './AuthContext';

const user = { id: 'u1', username: 'amelia', role: 'CUSTOMER' } as UserResponse;

function Probe() {
  const { user: current, loading } = useAuth();
  return <p>{loading ? 'restoring' : current ? `signed in as ${current.username}` : 'signed out'}</p>;
}

function renderProvider() {
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
}

// Lets the rejected or resolved me() call settle inside React's act().
const settle = () => act(async () => {});

describe('session restore', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    tokenStore.save({ accessToken: 'access', refreshToken: 'refresh' } as AuthenticationResponse);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    localStorage.clear();
  });

  it('signs out and clears the tokens on 401', async () => {
    vi.spyOn(usersApi, 'me').mockRejectedValue(new ApiRequestError(401, { code: 'AUTH_TOKEN_INVALID' }));
    renderProvider();
    await settle();

    expect(screen.getByText('signed out')).toBeTruthy();
    expect(tokenStore.accessToken()).toBeNull();
    expect(tokenStore.refreshToken()).toBeNull();
  });

  it('keeps the tokens and restores the session once an unreachable API answers', async () => {
    const me = vi
      .spyOn(usersApi, 'me')
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(user);
    renderProvider();
    await settle();

    expect(screen.getByText('restoring')).toBeTruthy();
    expect(tokenStore.accessToken()).toBe('access');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });

    expect(me).toHaveBeenCalledTimes(2);
    expect(screen.getByText('signed in as amelia')).toBeTruthy();
    expect(tokenStore.refreshToken()).toBe('refresh');
  });

  it('keeps the tokens when the API answers with a server error', async () => {
    vi.spyOn(usersApi, 'me').mockRejectedValue(new ApiRequestError(503, {}));
    renderProvider();
    await settle();

    expect(screen.getByText('restoring')).toBeTruthy();
    expect(tokenStore.accessToken()).toBe('access');
  });

  it('does not ask the API when there is no token', async () => {
    localStorage.clear();
    const me = vi.spyOn(usersApi, 'me');
    renderProvider();
    await settle();

    expect(screen.getByText('signed out')).toBeTruthy();
    expect(me).not.toHaveBeenCalled();
  });
});
