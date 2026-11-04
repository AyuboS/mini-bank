import { api } from './client';
import type { UserResponse, VerificationChallenge } from './types';

export interface ProfileUpdate {
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
}

export const usersApi = {
  me: () => api.get<UserResponse>('/api/v1/users/me'),

  updateProfile: (update: ProfileUpdate) => api.patch<UserResponse>('/api/v1/users/me', update),

  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<void>('/api/v1/users/me/password', { currentPassword, newPassword }),

  /** Sends a code to the number already on the profile. */
  sendPhoneCode: () => api.post<VerificationChallenge>('/api/v1/users/me/phone/code'),

  confirmPhone: (code: string) => api.post<UserResponse>('/api/v1/users/me/phone/confirm', { code }),

  /** Does nothing if the address is already verified. */
  resendEmailVerification: () => api.post<VerificationChallenge>('/api/v1/users/me/email/verify'),
};
