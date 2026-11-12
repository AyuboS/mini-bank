import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../api/client';
import { transfersApi } from '../api/transfers';
import type { TransactionResponse, TransferResponse } from '../api/types';
import { TransactionDetail } from './TransactionDetail';

const transaction: TransactionResponse = {
  transferId: 't1',
  reference: 'TRX-2026-ABCDEFGH',
  type: 'INTERNAL',
  status: 'COMPLETED',
  direction: 'DEBIT',
  amount: '42.00',
  currency: 'EUR',
  description: 'Internet',
  category: 'BILLS',
  counterpartyAccountNumber: 'MB-000000005535',
  createdAt: '2026-11-11T10:00:00Z',
};

const categorySelect = () => screen.getByLabelText('Category') as HTMLSelectElement;
const choose = (value: string) =>
  act(async () => {
    fireEvent.change(categorySelect(), { target: { value } });
  });

describe('TransactionDetail category', () => {
  beforeEach(() => {
    vi.spyOn(transfersApi, 'get').mockResolvedValue({} as TransferResponse);
    vi.spyOn(transfersApi, 'ledger').mockResolvedValue([]);
  });

  afterEach(cleanup);

  it('saves the chosen category and reports the updated transaction', async () => {
    const updated = { ...transaction, category: 'GROCERIES' as const };
    const recategorise = vi.spyOn(transfersApi, 'recategorise').mockResolvedValue(updated);
    const onRecategorised = vi.fn();
    render(<TransactionDetail transaction={transaction} onClose={() => {}} onRecategorised={onRecategorised} />);
    expect(categorySelect().value).toBe('BILLS');

    await choose('GROCERIES');

    expect(recategorise).toHaveBeenCalledWith('t1', 'GROCERIES');
    expect(onRecategorised).toHaveBeenCalledWith(updated);
    expect(categorySelect().value).toBe('GROCERIES');
  });

  it('puts the old category back and says why when saving fails', async () => {
    // Held open so the optimistic value renders before the request fails, as it would for real.
    let failSave: (error: unknown) => void = () => {};
    const pending = new Promise<TransactionResponse>((_, reject) => {
      failSave = reject;
    });
    vi.spyOn(transfersApi, 'recategorise').mockReturnValue(pending);
    render(<TransactionDetail transaction={transaction} onClose={() => {}} />);

    await choose('GROCERIES');
    expect(categorySelect().value).toBe('GROCERIES');
    expect(categorySelect().disabled).toBe(true);

    await act(async () => {
      failSave(new ApiRequestError(404, { code: 'TRANSFER_NOT_FOUND', message: 'Transfer not found' }));
    });

    expect(categorySelect().disabled).toBe(false);
    expect(categorySelect().value).toBe('BILLS');
    expect(screen.getByRole('alert').textContent).toBe('Transfer not found');
  });
});
