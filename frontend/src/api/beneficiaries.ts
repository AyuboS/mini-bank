import { api } from './client';
import type { BeneficiaryResponse } from './types';

export const beneficiariesApi = {
  list: () => api.get<BeneficiaryResponse[]>('/api/v1/beneficiaries'),

  create: (accountNumber: string, nickname: string) =>
    api.post<BeneficiaryResponse>('/api/v1/beneficiaries', { accountNumber, nickname }),

  remove: (id: string) => api.delete<void>(`/api/v1/beneficiaries/${id}`),
};
