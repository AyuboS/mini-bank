# API reference

Base path `/api/v1`. Interactive documentation is served at
[`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) and the raw document at
[`/v3/api-docs`](http://localhost:8080/v3/api-docs) — both disabled under the `prod` profile.

---

## Authentication

Call `POST /api/v1/auth/login`, then send the access token on every other request:

```
Authorization: Bearer <accessToken>
```

Access tokens last 15 minutes. When one expires, exchange the refresh token via
`POST /api/v1/auth/refresh`; the refresh token is replaced each time, so always store the new one.

---

## Endpoints

### Authentication — public

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Create a customer and their first EUR account |
| `POST` | `/auth/login` | Obtain an access and refresh token |
| `POST` | `/auth/refresh` | Rotate both tokens |
| `POST` | `/auth/logout` | Revoke the refresh token and its family |
| `POST` | `/auth/phone/code` | Send a one-time code by SMS. Always `202`, whether or not the number is registered |
| `POST` | `/auth/phone/login` | Exchange a `LOGIN` code for a token pair |
| `POST` | `/auth/phone/confirm` | Exchange a `REGISTRATION` code for the ticket `/auth/register` requires |
| `POST` | `/auth/email/verify` | Redeem the token from a verification link |
| `POST` | `/security/password-reset/request` | Send a reset code. Answers identically whether or not the number is registered |
| `POST` | `/security/password-reset/verify` | Exchange the code for a single-use ticket |
| `POST` | `/security/password-reset/complete` | Set the new password and revoke every session |

### Profile — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/users/me` | The caller's own profile |
| `PATCH` | `/users/me` | Update name and phone number |
| `POST` | `/users/me/password` | Change password; revokes all sessions |
| `POST` | `/users/me/phone/code` | Send a code to the number on the profile |
| `POST` | `/users/me/phone/confirm` | Confirm that number with the code |
| `POST` | `/users/me/email/verify` | Send the verification link again |

### Cards — authenticated

A card is reachable only by the customer who owns it. Anything belonging to somebody else answers
`CARD_NOT_FOUND` rather than `403`, so the endpoints cannot be used to discover which ids exist.
There is no administrative route into this resource at all.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/cards` | The caller's cards, newest first; cancelled ones omitted |
| `GET` | `/cards/{id}` | One card |
| `POST` | `/cards` | Issue a physical or virtual card |
| `PATCH` | `/cards/{id}` | Rename, or change the design |
| `PATCH` | `/cards/{id}/freeze` | Block the card; reversible |
| `PATCH` | `/cards/{id}/unfreeze` | Unblock it |
| `PATCH` | `/cards/{id}/limits` | Set or clear the daily and monthly caps |
| `GET` | `/cards/{id}/spending` | Outflow on the attached account, today and this month |
| `POST` | `/cards/{id}/details` | **Reveal** the number and verification value. Audited, rate limited, `Cache-Control: no-store` |
| `GET` | `/cards/{id}/transactions` | History of the account behind the card |
| `DELETE` | `/cards/{id}` | Cancel permanently |

Card numbers and verification values returned by `/details` are **simulated**. MBank is connected to
no payment processor; the response says so in a `simulated` field that is always `true`.

### Accounts — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/accounts` | The caller's accounts |
| `POST` | `/accounts` | Open an additional account in another currency |
| `GET` | `/accounts/{id}` | One account |
| `GET` | `/accounts/{id}/balance` | Balance with a timestamp |
| `GET` | `/accounts/{id}/transactions` | That account's history, paged |

### Transfers — authenticated

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/transfers` | Create a transfer. **Requires `Idempotency-Key`** |
| `GET` | `/transfers/{id}` | One transfer, if the caller was party to it |
| `GET` | `/transfers/{id}/ledger` | The two ledger postings behind it |
| `GET` | `/transactions` | History across all the caller's accounts, paged and filterable |

### Beneficiaries — authenticated

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/beneficiaries` | Save a payee |
| `GET` | `/beneficiaries` | List saved payees |
| `DELETE` | `/beneficiaries/{id}` | Remove a payee (soft delete) |

### Cards — controls

| Method | Path | Purpose |
|---|---|---|
| `PATCH` | `/cards/{id}/controls` | Turn usage channels on or off. Omitted switches are left alone |

The five channels are `onlinePayments`, `contactless`, `atmWithdrawals`, `international` and
`magneticStripe`. Each is a `Boolean`, not a `boolean`: `null` means "leave this one alone", so a
client turning off one channel cannot silently restate its opinion about the other four. The stripe
defaults to off — it is the only channel with no cryptographic authentication.

### Savings — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/savings/goals` | The caller's goals, with progress |
| `POST` | `/savings/goals` | Open a goal. MBank opens a dedicated account to hold it |
| `PUT` | `/savings/goals/{id}` | Rename, or move the target or deadline |
| `POST` | `/savings/goals/{id}/contributions` | Move money in — a real settled transfer |
| `POST` | `/savings/goals/{id}/withdrawals` | Move money back out |
| `PATCH` | `/savings/goals/{id}/archive` | Archive an empty goal |

A goal has no stored balance. Progress is the backing account's balance, so the savings screen and
the ledger cannot disagree.

### Budgets — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/budgets` | Budgets with this month's progress against each |
| `PUT` | `/budgets` | Set the cap for a category, creating it if absent |
| `DELETE` | `/budgets/{id}` | Remove a budget |

`PUT` rather than `POST`: a unique constraint on (user, category, currency) means a create would fail
half the time for a client that cannot know whether one exists, and setting a cap is idempotent.

### Analytics — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/analytics/summary?month&currency&timezone` | A month of income, spending and category breakdown |
| `GET` | `/analytics/insights?currency&timezone` | Observations about the current month |

Direction is decided by account ownership, not transfer type: money moving between the caller's own
accounts is counted as neither income nor spending. `timezone` matters because a month boundary is a
local concept. Insights return figures rather than sentences, and an empty list when the history
cannot support one.

### Transactions — categories and search

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/transactions?category&q` | Filter by category, or search description and reference |
| `PATCH` | `/transfers/{id}/category` | Change what a transaction was for |

Either party to a payment may recategorise it, and both then see the change: a category describes the
payment, not one side's view of it.

### Scheduled transfers — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/scheduled-transfers` | The caller's standing orders |
| `POST` | `/scheduled-transfers` | Set one up. The payee and currency are validated now |
| `PUT` | `/scheduled-transfers/{id}` | Change amount, description or schedule |
| `PATCH` | `/scheduled-transfers/{id}/pause` | Hold it |
| `PATCH` | `/scheduled-transfers/{id}/resume` | Resume. Dates missed while paused are skipped |
| `PATCH` | `/scheduled-transfers/{id}/cancel` | Stop it for good |

Due orders execute through the ordinary transfer path, so limits, the risk engine, idempotency and
the ledger all apply. The idempotency key is derived from the order id and the due date, so a second
pass on the same day replays the stored answer instead of paying twice.

### Money requests — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/money-requests/outgoing` | Requests the caller raised |
| `GET` | `/money-requests/incoming` | Requests addressed to the caller's accounts |
| `POST` | `/money-requests` | Ask somebody for money |
| `POST` | `/money-requests/{id}/pay` | Settle one addressed to you |
| `PATCH` | `/money-requests/{id}/decline` | Decline one |
| `PATCH` | `/money-requests/{id}/cancel` | Withdraw one you raised |

A request moves nothing by existing. Only the payer can settle it, and doing so creates an ordinary
transfer from the payer's own account under the payer's own limits.

### Notifications — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/notifications?type&unreadOnly` | The caller's feed, newest first |
| `GET` | `/notifications/unread-count` | Just the badge |
| `POST` | `/notifications/{id}/read` | Mark one read |
| `POST` | `/notifications/read-all` | Mark everything read |

### Security — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/security/sessions` | Signed-in devices, one row per login |
| `DELETE` | `/security/sessions/{id}` | End one session, revoking its whole token family |
| `POST` | `/security/sessions/revoke-others` | End every other session, keeping this one |
| `GET` | `/security/login-history` | Recent sign-in activity, read from the audit trail |

A session is a token family, not a token: rotation replaces the token several times a day, and
ending "this MacBook" means ending the login.

### Statements — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/accounts/{id}/statements?month&timezone` | A month, reconciled against the ledger |
| `GET` | `/accounts/{id}/statements/export?month` | The same month as CSV |
| `GET` | `/accounts/lookup?accountNumber` | Confirmation of payee: first name and last initial |

The opening balance is derived from the ledger rather than stored, so
`opening + in − out = closing` holds by construction.

### Support — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/support/tickets` | The caller's tickets |
| `POST` | `/support/tickets` | Open one |
| `GET` | `/support/tickets/{id}` | One ticket with its message thread |
| `POST` | `/support/tickets/{id}/messages` | Reply |
| `PATCH` | `/support/tickets/{id}/close` | Close it |

### Administration — `ADMIN` role only

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/users` | List users, optionally by status |
| `GET` | `/admin/users/{id}` | One user |
| `PATCH` | `/admin/users/{id}/status` | Change status; revokes sessions if not active |
| `GET` | `/admin/users/{id}/limits` | Effective transfer limits |
| `PUT` | `/admin/users/{id}/limits` | Override limits; null fields fall back to platform defaults |
| `GET` | `/admin/accounts` | List customer accounts |
| `PATCH` | `/admin/accounts/{id}/freeze` | Freeze an account |
| `PATCH` | `/admin/accounts/{id}/unfreeze` | Unfreeze an account |
| `POST` | `/admin/accounts/{id}/deposit` | Fund an account from the system account |
| `GET` | `/admin/transfers` | Search every transfer |
| `POST` | `/admin/transfers/{id}/approve` | Settle a transfer held for review |
| `POST` | `/admin/transfers/{id}/reject` | Refuse a held transfer |
| `POST` | `/admin/transfers/{id}/reverse` | Reverse a settled transfer |
| `GET` | `/admin/audit-logs` | Search the audit trail |

### Operations

| Method | Path | Access |
|---|---|---|
| `GET` | `/actuator/health` | Public |
| `GET` | `/actuator/info` | Public |
| `GET` | `/actuator/metrics` | `ADMIN` |
| `GET` | `/actuator/prometheus` | `ADMIN` |

---

## Creating a transfer

```http
POST /api/v1/transfers
Authorization: Bearer <accessToken>
Idempotency-Key: 8f14e45f-ea4c-4f2b-9d1e-3a7c2b6d9e01
Content-Type: application/json

{
  "fromAccountId": "13d8be75-e663-4aae-90b8-fbf0d23a89bb",
  "toAccountNumber": "MB-673344234420",
  "amount": 100.50,
  "currency": "EUR",
  "description": "Rent"
}
```

**`201 Created`** — settled:

```json
{
  "id": "9c1f...",
  "reference": "TRX-2026-JND964HP",
  "type": "INTERNAL",
  "status": "COMPLETED",
  "fromAccountNumber": "MB-713410971350",
  "toAccountNumber": "MB-673344234420",
  "amount": 100.5000,
  "currency": "EUR",
  "description": "Rent",
  "risk": { "decision": "ALLOW", "riskScore": 0, "reasons": [] },
  "createdAt": "2026-08-21T01:32:23Z",
  "completedAt": "2026-08-21T01:32:23Z"
}
```

**`202 Accepted`** — held by the risk engine. The transfer exists in `PENDING` and **no money has
moved**; an administrator must approve it:

```json
{
  "reference": "TRX-2026-BHUZCHBG",
  "status": "PENDING",
  "risk": {
    "decision": "REVIEW",
    "riskScore": 60,
    "reasons": ["Transfer amount 1500 EUR is at or above the review threshold of 1000"]
  }
}
```

### Response headers

| Header | Meaning |
|---|---|
| `Idempotent-Replay` | `true` when the response came from storage rather than a fresh execution |
| `X-Request-Id` | Correlation id; quote it when reporting a problem |
| `X-RateLimit-Limit`, `X-RateLimit-Remaining` | Present on rate-limited endpoints |

---

## Idempotency

`Idempotency-Key` is required on `POST /api/v1/transfers`. Use a fresh UUID per payment attempt and
**reuse the same one** when retrying after a network failure — that is what makes the retry safe.

| Situation | Response |
|---|---|
| First use | `201` / `202`, executed normally |
| Replay, same body, after success | Original response, `Idempotent-Replay: true`, no second payment |
| Replay, same body, after a business failure | The same error |
| Replay, different body | `422 IDEMPOTENCY_KEY_CONFLICT` |
| Replay while the first is running | `409 IDEMPOTENCY_REQUEST_IN_PROGRESS` |

Keys are scoped per user and per endpoint, so two customers picking the same key never collide.

---

## Pagination, filtering and sorting

```http
GET /api/v1/transactions?page=0&size=20&sort=createdAt,desc&status=COMPLETED&minAmount=10&from=2026-01-01T00:00:00Z
```

| Parameter | Notes |
|---|---|
| `page`, `size` | Zero-based; `size` is clamped to 100 |
| `sort` | `property,direction`. Only `createdAt`, `amount`, `status`, `completedAt` are accepted; anything else is ignored rather than erroring |
| `status` | Transfer status |
| `from`, `to` | ISO-8601 instants, inclusive |
| `minAmount`, `maxAmount` | Decimal bounds |

Response envelope:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "first": true,
  "last": false
}
```

---

## Errors

Every failure uses the same body:

```json
{
  "timestamp": "2026-08-20T18:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/transfers",
  "requestId": "6856015a-3271-409c-bce5-ad74341bec56",
  "errors": [
    { "field": "amount", "message": "must be greater than zero" }
  ]
}
```

Branch on `code`, never on `message` — messages may be reworded.

### Error codes

| Code | Status | Meaning |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Request failed validation; see `errors` |
| `MALFORMED_REQUEST` | 400 | Body could not be parsed, or a parameter has the wrong type |
| `INVALID_AMOUNT` | 400 | Amount is not positive, or too precise for the currency |
| `WEAK_PASSWORD` | 400 | Password does not meet the policy |
| `PASSWORD_MISMATCH` | 400 | Current password is wrong |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | The header is missing |
| `UNAUTHENTICATED` | 401 | No credentials, or they were not accepted |
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong username or password |
| `AUTH_TOKEN_INVALID` | 401 | Token is malformed, wrongly signed, or not an access token |
| `AUTH_TOKEN_EXPIRED` | 401 | Access token has expired; refresh it |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | Refresh token unknown, expired, or already used |
| `FORBIDDEN` | 403 | Authenticated but not permitted |
| `AUTH_ACCOUNT_NOT_ACTIVE` | 403 | The user account is suspended or deleted |
| `USER_NOT_FOUND` | 404 | No such user |
| `ACCOUNT_NOT_FOUND` | 404 | No such account, or it is not yours |
| `TRANSFER_NOT_FOUND` | 404 | No such transfer, or you were not party to it |
| `BENEFICIARY_NOT_FOUND` | 404 | No such payee, or it is not yours |
| `RESOURCE_NOT_FOUND` | 404 | Unknown route |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method |
| `USERNAME_ALREADY_EXISTS` | 409 | Username taken |
| `EMAIL_ALREADY_EXISTS` | 409 | Email already registered |
| `ACCOUNT_FROZEN` | 409 | Account is frozen |
| `ACCOUNT_CLOSED` | 409 | Account is closed |
| `BENEFICIARY_ALREADY_EXISTS` | 409 | Payee already saved |
| `INVALID_TRANSFER_STATE` | 409 | Operation is not legal from the transfer's current status |
| `TRANSFER_ALREADY_REVERSED` | 409 | Already reversed |
| `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 409 | A request with this key is still running |
| `CONFLICT` | 409 | Concurrent modification, or a constraint violation |
| `AUTH_ACCOUNT_LOCKED` | 423 | Locked after repeated failed logins |
| `INSUFFICIENT_FUNDS` | 422 | Source account cannot cover the amount |
| `TRANSFER_LIMIT_EXCEEDED` | 422 | Per-transfer, daily or hourly limit reached |
| `CURRENCY_MISMATCH` | 422 | Currencies differ; there is no FX |
| `SAME_ACCOUNT_TRANSFER` | 422 | Source and destination are the same account |
| `RISK_BLOCKED` | 422 | Refused by the risk engine |
| `IDEMPOTENCY_KEY_CONFLICT` | 422 | Key reused with a different body |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests; see `Retry-After` |
| `INTERNAL_ERROR` | 500 | Unexpected failure; quote `requestId` when reporting it |

---

## Worked example

```bash
API=http://localhost:8080/api/v1

# 1. Sign in
TOKEN=$(curl -s -X POST $API/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer.a","password":"Customer#2026!A"}' | jq -r .accessToken)

# 2. Find your account
FROM=$(curl -s $API/accounts -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

# 3. Send money, with a key you can safely retry
KEY=$(uuidgen)
curl -s -X POST $API/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountNumber\":\"MB-673344234420\",\"amount\":25.50,\"currency\":\"EUR\",\"description\":\"Lunch\"}" | jq

# 4. Send it again with the same key: same result, no second payment
curl -s -X POST $API/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountNumber\":\"MB-673344234420\",\"amount\":25.50,\"currency\":\"EUR\",\"description\":\"Lunch\"}" | jq -r .reference
```
