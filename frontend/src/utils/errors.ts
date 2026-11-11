import { API_BASE_URL, ApiRequestError } from '../api/client';

/**
 * Turns a backend error code into something a person can act on.
 *
 * The server's codes are stable and machine-readable, which is right for an
 * API and wrong for a human: "INSUFFICIENT_FUNDS" is not a sentence. Anything
 * without an entry here falls back to the server's own message, which is
 * already written in plain language.
 */
const MESSAGES: Record<string, { title: string; body?: string }> = {
  AUTH_INVALID_CREDENTIALS: {
    title: 'Incorrect username or password',
    body: 'Check your details and try again.',
  },
  AUTH_ACCOUNT_LOCKED: {
    title: 'Account temporarily locked',
    body: 'Too many failed attempts. Try again in 15 minutes.',
  },
  AUTH_ACCOUNT_NOT_ACTIVE: {
    title: 'This account is not active',
    body: 'Contact support to restore access.',
  },
  AUTH_REFRESH_TOKEN_INVALID: {
    title: 'Your session has ended',
    body: 'Please sign in again.',
  },
  USERNAME_ALREADY_EXISTS: { title: 'That username is taken', body: 'Try a different one.' },
  EMAIL_ALREADY_EXISTS: { title: 'That email is already registered', body: 'Sign in instead?' },
  PASSWORD_MISMATCH: { title: 'Current password is incorrect' },
  INSUFFICIENT_FUNDS: {
    title: 'Not enough funds',
    body: 'This transfer is larger than the balance available on that account.',
  },
  TRANSFER_LIMIT_EXCEEDED: {
    title: 'Transfer limit reached',
    body: 'This would exceed your per-transfer, daily or hourly limit.',
  },
  CURRENCY_MISMATCH: {
    title: 'Currencies do not match',
    body: 'Both accounts must hold the same currency.',
  },
  SAME_ACCOUNT_TRANSFER: { title: 'Choose a different destination', body: 'You cannot pay an account from itself.' },
  ACCOUNT_NOT_FOUND: { title: 'Account not found', body: 'Check the account number and try again.' },
  ACCOUNT_FROZEN: { title: 'That account is frozen', body: 'It cannot send or receive money right now.' },
  ACCOUNT_CLOSED: { title: 'That account is closed' },
  RISK_BLOCKED: {
    title: 'Transfer blocked',
    body: 'Our checks flagged this payment. Contact support if you expected it to go through.',
  },
  IDEMPOTENCY_KEY_CONFLICT: {
    title: 'This payment was already submitted',
    body: 'The details changed since the first attempt. Start a new transfer.',
  },
  IDEMPOTENCY_REQUEST_IN_PROGRESS: {
    title: 'Still processing',
    body: 'Your previous attempt is being completed. Give it a moment.',
  },
  BENEFICIARY_ALREADY_EXISTS: { title: 'Payee already saved' },
  BENEFICIARY_NOT_FOUND: { title: 'Payee not found' },
  INVALID_TRANSFER_STATE: { title: 'That is no longer possible', body: 'This transfer has already moved on.' },
  TRANSFER_ALREADY_REVERSED: { title: 'Already reversed' },
  RATE_LIMIT_EXCEEDED: { title: 'Too many attempts', body: 'Wait a moment before trying again.' },

  // --- Verification -------------------------------------------------------
  // "Wrong" and "too late" call for different words: reporting an expired code
  // as an incorrect one sends people hunting for a typo that is not there.
  VERIFICATION_CODE_INVALID: {
    title: 'The code is incorrect',
    body: 'Check the digits and try again.',
  },
  VERIFICATION_CODE_EXPIRED: {
    title: 'This code has expired',
    body: 'Request a new one — codes last five minutes.',
  },
  VERIFICATION_TOO_MANY_ATTEMPTS: {
    title: 'Too many attempts',
    body: 'This code is no longer usable. Request a new one.',
  },
  VERIFICATION_RESEND_TOO_SOON: {
    title: 'A code was just sent',
    body: 'Wait for the countdown before asking for another.',
  },
  VERIFICATION_REQUIRED: {
    title: 'Verify your number first',
    body: 'That verification is no longer valid. Start again.',
  },
  VERIFICATION_NOT_FOUND: {
    title: 'Nothing to verify',
    body: 'Request a new code and try again.',
  },
  PHONE_NUMBER_INVALID: {
    title: 'That does not look like a phone number',
    body: 'Include the country code, for example +998 90 123 45 67.',
  },
  PHONE_ALREADY_EXISTS: {
    title: 'That number is already registered',
    body: 'Sign in with it instead?',
  },
  DELIVERY_FAILED: {
    title: 'We could not send the message',
    body: 'Check the number and try again in a moment.',
  },

  // --- Cards ---------------------------------------------------------------
  CARD_NOT_FOUND: { title: 'Card not found' },
  CARD_NOT_ACTIVE: {
    title: 'That is not possible for this card',
    body: 'It has been cancelled, or is not in a state that allows it.',
  },
  CARD_EXPIRED: { title: 'This card has expired', body: 'Order a replacement to keep using it.' },
  CARD_LIMIT_REACHED: {
    title: 'You already have the maximum number of cards',
    body: 'Cancel one you no longer use to make room.',
  },
  CARD_INVALID_LIMITS: {
    title: 'Those limits do not work together',
    body: 'The monthly limit must be at least the daily one.',
  },
  CARD_CONTROLS_INVALID: {
    title: 'Those controls do not work together',
    body: 'A channel limit cannot be higher than the daily limit that already caps it.',
  },

  // --- Savings -------------------------------------------------------------
  SAVINGS_GOAL_NOT_FOUND: { title: 'Goal not found' },
  SAVINGS_GOAL_ARCHIVED: {
    title: 'This goal is archived',
    body: 'Restore it before adding money.',
  },
  SAVINGS_GOAL_NOT_EMPTY: {
    title: 'There is still money in this goal',
    body: 'Withdraw it first, then archive the goal.',
  },
  SAVINGS_GOAL_LIMIT_REACHED: {
    title: 'You have the maximum number of goals',
    body: 'Archive one you have finished with to make room.',
  },

  // --- Budgets -------------------------------------------------------------
  BUDGET_NOT_FOUND: { title: 'Budget not found' },
  BUDGET_ALREADY_EXISTS: { title: 'You already have a budget for this category' },

  // --- Standing orders -----------------------------------------------------
  SCHEDULED_TRANSFER_NOT_FOUND: { title: 'Scheduled payment not found' },
  SCHEDULED_TRANSFER_NOT_MODIFIABLE: {
    title: 'This payment has finished',
    body: 'Cancelled and completed orders can no longer be changed.',
  },
  SCHEDULED_DATE_IN_PAST: {
    title: 'Choose today or later',
    body: 'A scheduled payment cannot start in the past.',
  },

  // --- Money requests ------------------------------------------------------
  MONEY_REQUEST_NOT_FOUND: { title: 'Request not found' },
  MONEY_REQUEST_NOT_PENDING: {
    title: 'This request has already been settled',
    body: 'It was paid, declined or withdrawn.',
  },
  MONEY_REQUEST_EXPIRED: {
    title: 'This request has expired',
    body: 'Ask the sender to raise a new one.',
  },

  // --- Support -------------------------------------------------------------
  SUPPORT_TICKET_NOT_FOUND: { title: 'Ticket not found' },
  SUPPORT_TICKET_CLOSED: {
    title: 'This ticket is closed',
    body: 'Open a new one to continue the conversation.',
  },

  // --- Sessions ------------------------------------------------------------
  SESSION_NOT_FOUND: {
    title: 'That session has already ended',
    body: 'It may have expired or been signed out elsewhere.',
  },

  VALIDATION_ERROR: { title: 'Check the details entered' },
  FORBIDDEN: { title: 'Not permitted', body: 'You do not have access to this.' },
  INTERNAL_ERROR: { title: 'Something went wrong', body: 'Please try again in a moment.' },
};

/**
 * Must a retry of this failed payment reuse its idempotency key?
 *
 * Yes whenever the server may have acted on the request: it is still working
 * on it, or no readable answer came back at all (network failure, bad gateway).
 * A coded refusal is final, so the next attempt may start with a new key.
 */
export function mustKeepIdempotencyKey(error: unknown): boolean {
  if (error instanceof ApiRequestError) {
    return error.code === 'IDEMPOTENCY_REQUEST_IN_PROGRESS';
  }
  return true;
}

export interface FriendlyError {
  title: string;
  body?: string;
  /** Field-level messages, so a form can mark the offending inputs. */
  fields: Record<string, string>;
}

export function toFriendlyError(error: unknown): FriendlyError {
  if (error instanceof ApiRequestError) {
    const fields: Record<string, string> = {};
    for (const violation of error.fieldErrors) {
      fields[violation.field] = violation.message;
    }

    const known = MESSAGES[error.code];
    if (known) {
      // Field detail is more specific than the generic validation copy.
      const body = error.code === 'VALIDATION_ERROR' && error.fieldErrors.length > 0
        ? error.fieldErrors.map((v) => v.message).join('. ')
        : known.body;
      return { title: known.title, ...(body ? { body } : {}), fields };
    }

    return { title: error.message, fields };
  }

  if (error instanceof TypeError) {
    /*
      fetch() rejects with a TypeError when it cannot reach the host at all —
      the API is not running, or is running somewhere else. Naming the address
      it tried turns a dead end into something the reader can act on.
    */
    const where = `${API_BASE_URL || window.location.origin}/api`;
    return {
      title: 'The MBank API is not responding',
      body: `Nothing answered at ${where}. Start the stack with "docker compose up", then try again.`,
      fields: {},
    };
  }

  return { title: 'Something went wrong', body: 'Please try again in a moment.', fields: {} };
}
