import { api } from './client';
import type {
  AccountResponse,
  AdminUserResponse,
  AuditLogResponse,
  EffectiveLimits,
  PageResponse,
  TransferResponse,
  TransferStatus,
  UserStatus,
} from './types';

export const adminApi = {
  users: (page = 0, size = 20, status?: UserStatus | '') => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    return api.get<PageResponse<AdminUserResponse>>(`/api/v1/admin/users?${params}`);
  },

  user: (id: string) => api.get<AdminUserResponse>(`/api/v1/admin/users/${id}`),

  changeUserStatus: (id: string, status: UserStatus, reason: string) =>
    api.patch<AdminUserResponse>(`/api/v1/admin/users/${id}/status`, { status, reason }),

  limits: (id: string) => api.get<EffectiveLimits>(`/api/v1/admin/users/${id}/limits`),

  accounts: (page = 0, size = 20) =>
    api.get<PageResponse<AccountResponse>>(`/api/v1/admin/accounts?page=${page}&size=${size}`),

  freeze: (id: string) => api.patch<AccountResponse>(`/api/v1/admin/accounts/${id}/freeze`),
  unfreeze: (id: string) => api.patch<AccountResponse>(`/api/v1/admin/accounts/${id}/unfreeze`),

  deposit: (id: string, amount: string, description?: string) =>
    api.post<TransferResponse>(`/api/v1/admin/accounts/${id}/deposit`, { amount, description }),

  transfers: (page = 0, size = 20, status?: TransferStatus | '') => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    return api.get<PageResponse<TransferResponse>>(`/api/v1/admin/transfers?${params}`);
  },

  approve: (id: string) => api.post<TransferResponse>(`/api/v1/admin/transfers/${id}/approve`),

  reject: (id: string, reason: string) =>
    api.post<TransferResponse>(`/api/v1/admin/transfers/${id}/reject`, { reason }),

  reverse: (id: string, reason: string) =>
    api.post<TransferResponse>(`/api/v1/admin/transfers/${id}/reverse`, { reason }),

  auditLogs: (page = 0, size = 40, action?: string, userId?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (action) params.set('action', action);
    if (userId) params.set('userId', userId);
    return api.get<PageResponse<AuditLogResponse>>(`/api/v1/admin/audit-logs?${params}`);
  },
};
