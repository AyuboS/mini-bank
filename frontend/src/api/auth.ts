import { api, tokenStore } from './client';
import type {
  AuthenticationResponse,
  UserResponse,
  VerificationChallenge,
  VerificationPurpose,
  VerificationTicket,
} from './types';

export interface RegisterInput {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  /** Only accepted together with a verificationToken proving control of it. */
  phoneNumber?: string;
  verificationToken?: string;
}

/**
 * Authentication calls.
 *
 * These use the anonymous transport: they either have no session yet, or —
 * in logout's case — are deliberately ending one, and a 401 here must not
 * trigger the refresh-and-retry path.
 */
export const authApi = {
  login: (username: string, password: string) =>
    api.postAnonymous<AuthenticationResponse>('/api/v1/auth/login', { username, password }),

  register: (input: RegisterInput) => api.postAnonymous<UserResponse>('/api/v1/auth/register', input),

  /**
   * Asks for an SMS code.
   *
   * Answers identically whether or not the number belongs to an account, so
   * nothing the client does with the response can reveal which it was.
   */
  requestPhoneCode: (phoneNumber: string, purpose: VerificationPurpose) =>
    api.postAnonymous<VerificationChallenge>('/api/v1/auth/phone/code', { phoneNumber, purpose }),

  /** Exchanges a LOGIN code for a session. */
  loginWithPhone: (phoneNumber: string, code: string) =>
    api.postAnonymous<AuthenticationResponse>('/api/v1/auth/phone/login', { phoneNumber, code }),

  /** Exchanges a REGISTRATION code for the ticket that registration requires. */
  confirmPhone: (phoneNumber: string, code: string) =>
    api.postAnonymous<VerificationTicket>('/api/v1/auth/phone/confirm', { phoneNumber, code }),

  /** Redeems the token from a verification email. */
  verifyEmail: (token: string) =>
    api.postAnonymous<UserResponse>('/api/v1/auth/email/verify', { token }),

  logout: async (): Promise<void> => {
    const refreshToken = tokenStore.refreshToken();
    if (!refreshToken) {
      return;
    }
    // Best effort. The local session is cleared by the caller regardless, so a
    // network failure cannot leave somebody apparently still signed in.
    try {
      await api.postAnonymous<void>('/api/v1/auth/logout', { refreshToken });
    } catch {
      /* ignored on purpose */
    }
  },
};
