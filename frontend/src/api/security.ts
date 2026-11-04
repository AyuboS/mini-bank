import { api, tokenStore } from './client';
import type { LoginHistoryEntry, SessionResponse, VerificationChallenge, VerificationTicket } from './types';

/**
 * The security centre.
 *
 * Session revocation is a server-side write: the token family is revoked, so the
 * next refresh from that device fails at the store. Nothing here is a
 * client-side illusion of having signed a device out.
 */
export const securityApi = {
  /**
   * The caller's signed-in devices.
   *
   * The current refresh token is passed so the server can mark which row is this
   * device. It is a value the client already holds and is sent over the same
   * authenticated channel as everything else; the list is correct without it,
   * it simply cannot say "this device".
   */
  sessions: () => {
    const current = tokenStore.refreshToken();
    const query = current ? `?currentRefreshToken=${encodeURIComponent(current)}` : '';
    return api.get<SessionResponse[]>(`/api/v1/security/sessions${query}`);
  },

  revokeSession: (id: string) => api.delete<void>(`/api/v1/security/sessions/${id}`),

  /** Ends every other session and leaves this one signed in. */
  revokeOtherSessions: () => {
    const current = tokenStore.refreshToken();
    const query = current ? `?currentRefreshToken=${encodeURIComponent(current)}` : '';
    return api.post<{ sessionsEnded: number }>(`/api/v1/security/sessions/revoke-others${query}`);
  },

  loginHistory: () => api.get<LoginHistoryEntry[]>('/api/v1/security/login-history'),

  // --- password recovery, all three public ---

  requestPasswordReset: (phoneNumber: string) =>
    api.postAnonymous<VerificationChallenge>('/api/v1/security/password-reset/request', { phoneNumber }),

  verifyPasswordReset: (phoneNumber: string, code: string) =>
    api.postAnonymous<VerificationTicket>('/api/v1/security/password-reset/verify', { phoneNumber, code }),

  completePasswordReset: (phoneNumber: string, ticket: string, newPassword: string) =>
    api.postAnonymous<void>('/api/v1/security/password-reset/complete', {
      phoneNumber,
      ticket,
      newPassword,
    }),
};
