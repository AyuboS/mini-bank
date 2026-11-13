import { describe, expect, it } from 'vitest';
import { createPaymentAttempt } from './paymentAttempt';

function counter() {
  let next = 0;
  return () => `key-${++next}`;
}

describe('createPaymentAttempt', () => {
  it('reuses the key when the same payment is retried', () => {
    const attempt = createPaymentAttempt(counter());
    const payment = { to: 'MB-1', amount: '10.00' };
    expect(attempt.keyFor(payment)).toBe('key-1');
    expect(attempt.keyFor({ ...payment })).toBe('key-1');
  });

  it('uses a new key once the payment details change', () => {
    const attempt = createPaymentAttempt(counter());
    expect(attempt.keyFor({ to: 'MB-1', amount: '10.00' })).toBe('key-1');
    expect(attempt.keyFor({ to: 'MB-1', amount: '12.00' })).toBe('key-2');
    expect(attempt.keyFor({ to: 'MB-1', amount: '12.00' })).toBe('key-2');
  });

  it('uses a new key after a final answer, even for an identical payment', () => {
    const attempt = createPaymentAttempt(counter());
    const payment = { to: 'MB-1', amount: '10.00' };
    expect(attempt.keyFor(payment)).toBe('key-1');
    attempt.finish();
    expect(attempt.keyFor(payment)).toBe('key-2');
  });
});
