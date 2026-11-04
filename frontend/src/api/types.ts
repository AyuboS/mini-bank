/** Shapes returned by the MBank API. Kept in step with the OpenAPI document by hand. */

export type UserRole = 'CUSTOMER' | 'ADMIN';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type CurrencyCode = 'EUR' | 'USD' | 'GBP';
export type TransferStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REVERSED';
export type EntryType = 'DEBIT' | 'CREDIT';
export type AccountPurpose = 'SPENDING' | 'SAVINGS';

/**
 * What a payment was for.
 *
 * Thirteen buckets, matching the server's enum exactly. SALARY, SAVINGS and
 * TRANSFER are structural rather than discretionary — see `CATEGORY_META` in
 * utils/categories.ts for which of them count as spending.
 */
export const TRANSACTION_CATEGORIES = [
  'GROCERIES',
  'DINING',
  'SHOPPING',
  'TRANSPORT',
  'BILLS',
  'ENTERTAINMENT',
  'TRAVEL',
  'HEALTH',
  'EDUCATION',
  'SALARY',
  'SAVINGS',
  'TRANSFER',
  'OTHER',
] as const;
export type TransactionCategory = (typeof TRANSACTION_CATEGORIES)[number];

export type CardType = 'PHYSICAL' | 'VIRTUAL';
export type CardStatus = 'ACTIVE' | 'FROZEN' | 'EXPIRED' | 'CANCELLED';
export type CardNetwork = 'VISA' | 'MASTERCARD';

/** The seven skins. The backend stores the name; the stylesheet owns what it looks like. */
export const CARD_DESIGNS = [
  'AURORA',
  'MIDNIGHT',
  'OCEAN',
  'SUNSET',
  'CRYSTAL',
  'CARBON',
  'HOLOGRAPHIC',
] as const;
export type CardDesign = (typeof CARD_DESIGNS)[number];

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  phoneVerified: boolean;
  emailVerified: boolean;
  role: UserRole;
  status: string;
  createdAt: string;
  lastLoginAt?: string;
}

export interface AuthenticationResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  user: UserResponse;
}

export interface AccountResponse {
  id: string;
  accountNumber: string;
  currency: CurrencyCode;
  /** SAVINGS when the account backs a goal; the wallet groups it separately. */
  purpose: AccountPurpose;
  status: AccountStatus;
  /** Serialised as a JSON number by the API; kept as a string here to avoid float rounding. */
  balance: string;
  createdAt: string;
}

export interface CardResponse {
  id: string;
  accountId: string;
  accountNumber: string;
  currency: CurrencyCode;
  label: string;
  type: CardType;
  status: CardStatus;
  design: CardDesign;
  network: CardNetwork;
  /** Already masked by the server: the full number is never in a list response. */
  maskedNumber: string;
  lastFour: string;
  cardholderName: string;
  expirationMonth: number;
  expirationYear: number;
  dailyLimit?: string;
  monthlyLimit?: string;
  /** The five channel switches and the four caps, in one object. */
  controls: CardControls;
  frozenAt?: string;
  createdAt: string;
}

/**
 * What a card may be used for.
 *
 * Enforced server-side on every card operation. The switches here reflect that
 * state; they do not create it.
 */
export interface CardControls {
  onlinePayments: boolean;
  contactless: boolean;
  atmWithdrawals: boolean;
  international: boolean;
  magneticStripe: boolean;
  dailyLimit?: string;
  monthlyLimit?: string;
  atmLimit?: string;
  onlineLimit?: string;
}

/** Returned only by an explicit reveal, and never held in state longer than the modal is open. */
export interface CardDetailsResponse {
  cardId: string;
  cardholderName: string;
  number: string;
  expirationMonth: number;
  expirationYear: number;
  verificationValue: string;
  /** Always true. These are demo credentials and the interface says so on screen. */
  simulated: boolean;
  revealedAt: string;
}

export interface CardSpendingResponse {
  cardId: string;
  currency: CurrencyCode;
  spentToday: string;
  spentThisMonth: string;
  dailyLimit?: string;
  monthlyLimit?: string;
}

export type VerificationPurpose = 'LOGIN' | 'REGISTRATION' | 'CONTACT_VERIFICATION';

/**
 * The answer to "send me a code".
 *
 * Carries no hint of whether an account exists at the destination — an unknown
 * number produces exactly this shape.
 */
export interface VerificationChallenge {
  destination: string;
  expiresAt: string;
  resendAvailableInSeconds: number;
  deliverySimulated: boolean;
  /** DEVELOPMENT ONLY, and absent unless the deployment opts in. */
  developmentCode?: string;
}

export interface VerificationTicket {
  destination: string;
  token: string;
  expiresAt: string;
}

export interface RiskAssessmentResponse {
  decision: 'ALLOW' | 'REVIEW' | 'BLOCK';
  riskScore: number;
  reasons: string[];
}

export interface TransferResponse {
  id: string;
  reference: string;
  type: string;
  status: TransferStatus;
  fromAccountNumber: string;
  toAccountNumber: string;
  amount: string;
  currency: CurrencyCode;
  description?: string;
  /** Null when the server could not place the payment confidently. */
  category?: TransactionCategory;
  failureReason?: string;
  risk?: RiskAssessmentResponse;
  createdAt: string;
  completedAt?: string;
}

export interface TransactionResponse {
  transferId: string;
  reference: string;
  type: string;
  status: TransferStatus;
  direction: EntryType;
  counterpartyAccountNumber: string;
  amount: string;
  currency: CurrencyCode;
  description?: string;
  category?: TransactionCategory;
  createdAt: string;
  completedAt?: string;
}

export interface LedgerEntryResponse {
  id: string;
  accountId: string;
  accountNumber: string;
  entryType: EntryType;
  amount: string;
  currency: CurrencyCode;
  /** The account balance immediately after this posting was applied. */
  balanceAfter: string;
  createdAt: string;
}

export interface BeneficiaryResponse {
  id: string;
  accountNumber: string;
  nickname: string;
  holderName?: string;
  /** Pinned by the customer. Favourites sort to the top of the list. */
  favourite: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type UserStatus = 'ACTIVE' | 'LOCKED' | 'SUSPENDED' | 'DELETED';
export type AuditOutcome = 'SUCCESS' | 'FAILURE';

/** A user as an administrator sees them. The phone number arrives already masked. */
export interface AdminUserResponse {
  id: string;
  username: string;
  email: string;
  fullName: string;
  maskedPhoneNumber?: string;
  role: UserRole;
  status: UserStatus;
  failedLoginAttempts: number;
  lockoutUntil?: string;
  lastLoginAt?: string;
  createdAt: string;
}

export interface AuditLogResponse {
  id: string;
  userId?: string;
  username?: string;
  action: string;
  outcome: AuditOutcome;
  entityType?: string;
  entityId?: string;
  ipAddress?: string;
  requestId?: string;
  metadata?: Record<string, string>;
  createdAt: string;
}

export interface EffectiveLimits {
  perTransferMax: string;
  dailyMax: string;
  maxTransfersPerHour: number;
  customised: boolean;
}

/** The uniform error body every endpoint returns on failure. */
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  requestId?: string;
  errors?: { field: string; message: string }[];
}

/* =============================================================================
   Savings
   ========================================================================== */

export type SavingsGoalStatus = 'ACTIVE' | 'ACHIEVED' | 'ARCHIVED';

/** The six accents a goal can be drawn in. Matches the server's validation. */
export const GOAL_ACCENTS = ['violet', 'cyan', 'emerald', 'amber', 'coral', 'blue'] as const;
export type GoalAccent = (typeof GOAL_ACCENTS)[number];

export interface SavingsGoalResponse {
  id: string;
  /** The dedicated account holding the money. Progress is its balance. */
  accountId: string;
  accountNumber: string;
  name: string;
  targetAmount: string;
  saved: string;
  remaining: string;
  /** 0–100, rounded down, so a goal never claims to be complete before it is. */
  progressPercent: number;
  currency: CurrencyCode;
  deadline?: string;
  status: SavingsGoalStatus;
  accent: string;
  createdAt: string;
}

/* =============================================================================
   Budgets
   ========================================================================== */

export type BudgetState = 'OK' | 'NEARING' | 'OVER';

export interface BudgetResponse {
  id: string;
  category: TransactionCategory;
  currency: CurrencyCode;
  amount: string;
  /** Computed from real transactions at read time, never stored. */
  spent: string;
  /** Floors at zero: an overspend leaves nothing, not a debt. */
  remaining: string;
  /** Capped at 100, for drawing a bar. */
  usedPercent: number;
  /** Uncapped, so an overspend is legible as e.g. 140. */
  usedPercentRaw: number;
  state: BudgetState;
}

/* =============================================================================
   Analytics
   ========================================================================== */

export interface CategoryTotal {
  /** Null when the payment was never categorised. */
  category?: TransactionCategory;
  amount: string;
  /** Share of total spending, 0–100, one decimal. */
  share: string;
  transactionCount: number;
}

export interface DailyPoint {
  date: string;
  spending: string;
  income: string;
}

export interface LargestTransaction {
  transferId: string;
  reference: string;
  description?: string;
  category?: TransactionCategory;
  amount: string;
  counterpartyAccountNumber: string;
  completedAt: string;
}

export interface MonthlySummary {
  period: string;
  currency: CurrencyCode;
  income: string;
  spending: string;
  net: string;
  /**
   * Money moved between the customer's own accounts, savings included. In
   * neither income nor spending, which is what stops a month of saving reading
   * as a month of overspending.
   */
  internalMovement: string;
  transactionCount: number;
  byCategory: CategoryTotal[];
  /** One point per day of the month, including the empty ones. */
  series: DailyPoint[];
  largest?: LargestTransaction;
}

/**
 * One observation about the customer's month.
 *
 * Carries figures rather than a finished sentence: the wording is composed on
 * this side, where the locale and the money formatter live. See
 * `describeInsight` in utils/insights.ts.
 */
export interface InsightResponse {
  kind: 'NET_POSITIVE' | 'NET_NEGATIVE' | 'TOP_CATEGORY' | 'SPENDING_UP' | 'SPENDING_DOWN' | string;
  tone: 'POSITIVE' | 'NEUTRAL' | 'ATTENTION';
  category?: TransactionCategory;
  amount?: string;
  comparisonAmount?: string;
  /** Signed month-on-month change, whole percent. */
  percentChange?: number;
  /** Share of total spending, 0–100, one decimal. */
  share?: string;
}

/* =============================================================================
   Notifications
   ========================================================================== */

export type NotificationType = 'TRANSACTION' | 'SECURITY' | 'CARD' | 'BUDGET' | 'SYSTEM';
export type NotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL';

export interface NotificationResponse {
  id: string;
  type: NotificationType;
  severity: NotificationSeverity;
  title: string;
  body: string;
  entityType?: string;
  entityId?: string;
  read: boolean;
  readAt?: string;
  createdAt: string;
}

/* =============================================================================
   Standing orders
   ========================================================================== */

export type ScheduleFrequency = 'ONCE' | 'WEEKLY' | 'MONTHLY';
export type ScheduleStatus = 'SCHEDULED' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';

export interface ScheduledTransferResponse {
  id: string;
  fromAccountId: string;
  fromAccountNumber: string;
  toAccountNumber: string;
  payeeName?: string;
  amount: string;
  currency: CurrencyCode;
  description?: string;
  category?: TransactionCategory;
  frequency: ScheduleFrequency;
  nextRunOn: string;
  endsOn?: string;
  status: ScheduleStatus;
  lastRunAt?: string;
  /** Why the last attempt did not go through. Cleared by the next success. */
  lastFailureReason?: string;
  runsCompleted: number;
  createdAt: string;
}

/* =============================================================================
   Money requests
   ========================================================================== */

export type MoneyRequestStatus = 'PENDING' | 'PAID' | 'DECLINED' | 'EXPIRED' | 'CANCELLED';

export interface MoneyRequestResponse {
  id: string;
  /** Which side of the request the caller is on. */
  direction: 'OUTGOING' | 'INCOMING';
  requesterName: string;
  requesterAccountNumber: string;
  payerAccountNumber: string;
  amount: string;
  currency: CurrencyCode;
  description?: string;
  status: MoneyRequestStatus;
  expiresAt: string;
  settledTransferId?: string;
  settledAt?: string;
  createdAt: string;
}

/* =============================================================================
   Security centre
   ========================================================================== */

export interface SessionResponse {
  /** The token family. Ending this ends the login, not one rotated token. */
  id: string;
  device: string;
  ipAddress?: string;
  current: boolean;
  lastUsedAt: string;
  createdAt: string;
  expiresAt: string;
}

export interface LoginHistoryEntry {
  action: string;
  outcome: 'SUCCESS' | 'FAILURE';
  device?: string;
  ipAddress?: string;
  at: string;
}

/* =============================================================================
   Statements
   ========================================================================== */

export interface StatementResponse {
  accountId: string;
  accountNumber: string;
  currency: CurrencyCode;
  period: string;
  from: string;
  to: string;
  /** Derived from the ledger, so opening + in − out always equals closing. */
  openingBalance: string;
  totalIn: string;
  totalOut: string;
  closingBalance: string;
  transactionCount: number;
  transactions: TransactionResponse[];
}

/* =============================================================================
   Support
   ========================================================================== */

export type SupportCategory = 'CARD' | 'TRANSFER' | 'ACCOUNT' | 'SECURITY' | 'APP' | 'OTHER';
export type SupportStatus = 'OPEN' | 'AWAITING_CUSTOMER' | 'RESOLVED' | 'CLOSED';

export interface SupportMessage {
  id: string;
  fromStaff: boolean;
  authorName: string;
  body: string;
  createdAt: string;
}

export interface SupportTicketResponse {
  id: string;
  reference: string;
  subject: string;
  category: SupportCategory;
  status: SupportStatus;
  createdAt: string;
  updatedAt: string;
  /** Present only when a single ticket was requested. */
  messages?: SupportMessage[];
}

/**
 * Confirmation of payee.
 *
 * A first name and last initial only — enough for a payer to notice a mistyped
 * digit, and not enough to turn the endpoint into a directory.
 */
export interface PayeeLookupResponse {
  accountNumber: string;
  holderName: string;
  currency: CurrencyCode;
  /** Always "MBank": there is no external payment network attached. */
  bank: string;
}
