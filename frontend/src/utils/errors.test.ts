import { describe, expect, it } from 'vitest';
import { ApiRequestError } from '../api/client';
import { mustKeepIdempotencyKey } from './errors';

describe('mustKeepIdempotencyKey', () => {
  it('keeps the key when the request never got an answer', () => {
    expect(mustKeepIdempotencyKey(new TypeError('Failed to fetch'))).toBe(true);
  });

  it('keeps the key when the answer could not be read', () => {
    expect(mustKeepIdempotencyKey(new SyntaxError('Unexpected token <'))).toBe(true);
  });

  it('keeps the key while the first attempt is still in progress', () => {
    const error = new ApiRequestError(409, { code: 'IDEMPOTENCY_REQUEST_IN_PROGRESS' });
    expect(mustKeepIdempotencyKey(error)).toBe(true);
  });

  it.each(['INSUFFICIENT_FUNDS', 'TRANSFER_LIMIT_EXCEEDED', 'RISK_BLOCKED', 'IDEMPOTENCY_KEY_CONFLICT'])(
    'releases the key after the final refusal %s',
    (code) => {
      expect(mustKeepIdempotencyKey(new ApiRequestError(422, { code }))).toBe(false);
    },
  );
});
