import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiRequestError, tokenStore } from './client';
import type { AuthenticationResponse } from './types';

const json = (status: number, body: unknown = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

describe('token refresh', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    tokenStore.save({ accessToken: 'old', refreshToken: 'refresh' } as AuthenticationResponse);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('retries the request with the new token after a successful refresh', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'AUTH_TOKEN_EXPIRED' }))
      .mockResolvedValueOnce(json(200, { accessToken: 'new', refreshToken: 'refresh-2' }))
      .mockResolvedValueOnce(json(200, { ok: true }));

    await expect(api.get('/api/v1/users/me')).resolves.toEqual({ ok: true });
    expect(tokenStore.accessToken()).toBe('new');
    const retried = fetchMock.mock.calls[2]![1] as RequestInit;
    expect((retried.headers as Record<string, string>).Authorization).toBe('Bearer new');
  });

  it('clears the tokens when the refresh token is rejected with 401', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'AUTH_TOKEN_EXPIRED' }))
      .mockResolvedValueOnce(json(401, { code: 'AUTH_REFRESH_TOKEN_INVALID' }));

    await expect(api.get('/api/v1/users/me')).rejects.toMatchObject({ status: 401 });
    expect(tokenStore.accessToken()).toBeNull();
  });

  it('keeps the tokens when the refresh call cannot reach the API', async () => {
    const unreachable = vi.fn();
    window.addEventListener('minibank:unreachable', unreachable);
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'AUTH_TOKEN_EXPIRED' }))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'));

    await expect(api.get('/api/v1/users/me')).rejects.toBeInstanceOf(TypeError);
    expect(tokenStore.refreshToken()).toBe('refresh');
    expect(unreachable).toHaveBeenCalledTimes(1);
    window.removeEventListener('minibank:unreachable', unreachable);
  });

  it('keeps the tokens when the refresh call fails with a server error', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'AUTH_TOKEN_EXPIRED' }))
      .mockResolvedValueOnce(json(503));

    const error = await api.get('/api/v1/users/me').catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(503);
    expect(tokenStore.refreshToken()).toBe('refresh');
  });
});
