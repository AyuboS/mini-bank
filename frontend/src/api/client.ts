import type { ApiError, AuthenticationResponse } from './types';

/**
 * Where the API lives.
 *
 * In development this is empty, so every request is same-origin and Vite's dev
 * proxy forwards it (see vite.config.ts). That removes CORS and port drift as
 * possible causes of "cannot reach MBank". A production bundle has no
 * server in front of it, so the origin is baked in at build time.
 */
export const API_BASE_URL: string = (
  import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? '' : 'http://localhost:8080')
).replace(/\/+$/, '');

const BASE_URL = API_BASE_URL;

/**
 * Is the API answering at all?
 *
 * Used to tell a genuinely unreachable server apart from a single failed
 * request, so the interface can say "the API is not running" instead of
 * shrugging. Readiness rather than liveness: it reports false while the
 * application is still starting or has lost the database.
 */
export async function pingApi(signal?: AbortSignal): Promise<boolean> {
  const url = `${BASE_URL}/actuator/health/readiness`;
  const options: RequestInit = { cache: 'no-store', ...(signal ? { signal } : {}) };

  try {
    const response = await fetch(url, options);
    return response.ok;
  } catch {
    /*
      A rejection here is not proof that nothing is there.

      The server's CORS configuration covers /api, not /actuator, so a probe
      sent cross-origin — which is exactly what the Docker frontend does, from
      :3000 to :8080 — is blocked by the browser even when the API is perfectly
      healthy. Reporting that as "the API is not responding" would put a false
      alarm on a working application, which is worse than saying nothing.

      A no-cors request cannot be read, but it still resolves or rejects on
      whether anything answered at all, and that is the only question being
      asked. It cannot see the status code, so a server that is up but not yet
      ready reads as up; the banner is about reachability, and a request that
      fails for any other reason still surfaces its own error.
    */
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

/**
 * Token storage.
 *
 * Tokens live in localStorage, which is the pragmatic choice for a demo but is
 * readable by any script that manages to run on the page. A production
 * deployment should keep the refresh token in an httpOnly, SameSite cookie so
 * that cross-site scripting cannot exfiltrate it, and pair that with CSRF
 * protection on the refresh endpoint. See docs/security.md.
 */
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

/**
 * A single in-flight refresh, shared by every request that hits a 401 at once.
 *
 * Without this, a dashboard that fires four requests on load would send four
 * refreshes; refresh tokens rotate, so three of them would present an
 * already-used token and trip the server's reuse detection, logging the user
 * out for doing nothing wrong.
 */
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

/**
 * Downloads a file from an authenticated endpoint.
 *
 * A plain `<a href>` cannot be used: the API takes a bearer token, and a browser
 * navigation carries no Authorization header, so the download would arrive as a
 * 401 page. The bytes are therefore fetched with the token attached and handed
 * to the browser as an object URL.
 *
 * The URL is revoked on the next tick. Without that, every export in a session
 * pins its own copy of the file in memory until the tab is closed.
 */
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

/**
 * A key that identifies one logical payment attempt.
 *
 * Generated once when the form is submitted and reused if the request is
 * retried, which is exactly what makes the retry safe: the server recognises
 * the key and returns the original outcome instead of paying twice.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `key-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}
