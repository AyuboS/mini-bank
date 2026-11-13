import { api, tokenStore } from './client';
import type { LoginHistoryEntry, SessionResponse, VerificationChallenge, VerificationTicket } from './types';

/** The security centre. */
export const securityApi = {
  /** The caller's signed-in devices. */
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
