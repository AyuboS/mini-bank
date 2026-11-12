import { newIdempotencyKey } from '../api/client';

/**
 * Decides which idempotency key goes with a payment request.
 *
 * The server pays once per key. So a retry of the same payment must reuse its
 * key (the first try may have gone through), and a payment whose details were
 * changed is a different payment and needs a new one.
 */
export function createPaymentAttempt(newKey: () => string = newIdempotencyKey) {
  let key = newKey();
  let sentPayload: string | null = null;

  return {
    keyFor(payload: object): string {
      const serialised = JSON.stringify(payload);
      if (sentPayload !== null && sentPayload !== serialised) key = newKey();
      sentPayload = serialised;
      return key;
    },
    /** The server gave a final answer; the next payment starts fresh. */
    finish(): void {
      key = newKey();
      sentPayload = null;
    },
  };
}
