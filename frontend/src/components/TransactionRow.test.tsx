import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { TransactionResponse } from '../api/types';
import { TransactionRow } from './TransactionRow';

const transaction: TransactionResponse = {
  transferId: 't1',
  reference: 'TRF-1',
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

describe('TransactionRow', () => {
  afterEach(cleanup);

  it('is a button that reports the transaction when it can be opened', () => {
    const onSelect = vi.fn();
    render(<TransactionRow transaction={transaction} onSelect={onSelect} />);

    fireEvent.click(screen.getByRole('button'));

    expect(onSelect).toHaveBeenCalledWith(transaction);
  });

  it('is not a button when there is nothing to open', () => {
    render(<TransactionRow transaction={transaction} />);

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getByText('Internet')).toBeTruthy();
  });
});
