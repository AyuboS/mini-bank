import type { ApiError, AuthenticationResponse } from './types';

/** Where the API lives. */
export const API_BASE_URL: string = (
  import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? '' : 'http://localhost:8080')
).replace(/\/+$/, '');

const BASE_URL = API_BASE_URL;

/** Is the API answering at all? */
export async function pingApi(signal?: AbortSignal): Promise<boolean> {
  const url = `${BASE_URL}/actuator/health/readiness`;
  const options: RequestInit = { cache: 'no-store', ...(signal ? { signal } : {}) };

  try {
    const response = await fetch(url, options);
    return response.ok;
  } catch {
    /* A rejection here is not proof that nothing is there. */
    try {
      await fetch(url, { ...options, mode: 'no-cors' });
      return true;
    } catch {
      return false;
    }
  }
}

const ACCESS_TOKEN_KEY = 'minibank.accessToken';
const REFRESH_TOKEN_KEY = 'minibank.refreshToken';

/**
 * An error carrying the API's machine-readable code, so callers can branch on the
 * cause instead of matching on message text.
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(status: number, body: Partial<ApiError>) {
    super(body.message ?? 'Request failed');
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = body.code ?? 'UNKNOWN';
    this.fieldErrors = body.errors ?? [];
  }

  /** A message suitable for showing to a person, preferring field-level detail. */
  get displayMessage(): string {
    if (this.fieldErrors.length > 0) {
      return this.fieldErrors.map((e) => `${e.field}: ${e.message}`).join('\n');
    }
    return this.message;
  }
}

/** Token storage. */
export const tokenStore = {
  accessToken: (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY),
  refreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),
  save(tokens: AuthenticationResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  },
  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  /** Skips the refresh-and-retry dance, used by the refresh call itself. */
  skipRefresh?: boolean;
}

/** A single in-flight refresh, shared by every request that hits a 401 at once. */
let refreshInFlight: Promise<boolean> | null = null;

// fetch() rejects when the host cannot be reached. The event starts the connection banner.
async function fetchOrAnnounce(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch (cause) {
    window.dispatchEvent(new CustomEvent('minibank:unreachable'));
    throw cause;
  }
}

/**
 * Resolves false only when the server rejected the refresh token (401), and
 * clears the session then. An outage throws instead, so the session survives it.
 */
async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = tokenStore.refreshToken();
  if (!refreshToken) {
    return false;
  }
  refreshInFlight ??= (async () => {
    try {
      const response = await fetchOrAnnounce(`${BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (response.status === 401) {
        tokenStore.clear();
        return false;
      }
      if (!response.ok) {
        throw new ApiRequestError(response.status, {});
      }
      tokenStore.save((await response.json()) as AuthenticationResponse);
      return true;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

async function send<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, skipRefresh = false } = options;

  const doFetch = async (): Promise<Response> => {
    const accessToken = tokenStore.accessToken();
    return fetchOrAnnounce(`${BASE_URL}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
  };

  let response = await doFetch();

  // One retry, and only for an expired session. Retrying anything else would
  // risk repeating a request the server already acted on.
  if (response.status === 401 && !skipRefresh && (await refreshAccessToken())) {
    response = await doFetch();
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const payload: unknown = text ? JSON.parse(text) : {};

  if (!response.ok) {
    throw new ApiRequestError(response.status, payload as Partial<ApiError>);
  }
  return payload as T;
}

export const api = {
  get: <T>(path: string) => send<T>(path),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    send<T>(path, { method: 'POST', body, ...(headers ? { headers } : {}) }),
  put: <T>(path: string, body?: unknown) => send<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => send<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => send<T>(path, { method: 'DELETE' }),
  /** Used by login, register, refresh and password recovery, where no session exists yet. */
  postAnonymous: <T>(path: string, body: unknown) =>
    send<T>(path, { method: 'POST', body, skipRefresh: true }),
};

/** Downloads a file from an authenticated endpoint. */
export async function downloadFile(path: string, filename: string): Promise<void> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: tokenStore.accessToken() ? { Authorization: `Bearer ${tokenStore.accessToken()}` } : {},
  });

  if (!response.ok) {
    let body: Partial<ApiError> = {};
    try {
      body = await response.json();
    } catch {
      // A non-JSON error body is still an error; the status carries the meaning.
    }
    throw new ApiRequestError(response.status, body);
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** A key that identifies one logical payment attempt. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `key-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}
