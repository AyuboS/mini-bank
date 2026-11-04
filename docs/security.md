# Security

What is protected, how, and what is deliberately left out of scope.

---

## Authentication

### Passwords

- **BCrypt at cost 12**, one step above Spring's default of 10. That roughly quadruples the work an
  offline cracker does per guess, at a login cost of a few tens of milliseconds.
- Policy: 12–72 characters, with upper case, lower case, a digit and a special character. The upper
  bound is not arbitrary — BCrypt silently truncates beyond 72 bytes, so accepting more would give
  the user a false sense of strength.
- Plain-text passwords are never stored, never logged, and never returned. `LoginRequest` and
  `ChangePasswordRequest` override `toString()` so they cannot leak through a stack trace.
- Changing a password revokes **every** session. A password change is what a user does when they
  believe they are compromised; leaving old refresh tokens alive would defeat the point.

### Username enumeration

A login for a username that does not exist still performs a BCrypt verification against a fixed
dummy hash before failing. Without it, "no such user" would return in microseconds and "wrong
password" in tens of milliseconds, and the endpoint would be a username oracle. Both paths return
the same `AUTH_INVALID_CREDENTIALS`.

### Brute force

Five consecutive failures lock the account for fifteen minutes (both configurable). The lock
releases itself; no operator action is needed.

The counter is incremented in its **own transaction**. A failed login ends by throwing, and an
increment inside that transaction would be rolled back by the very exception that reports the
failure — the protection would look correct while never triggering. `LoginAttemptService` exists
solely to get this right.

### Tokens

| | Access token | Refresh token |
|---|---|---|
| Format | JWT, HMAC-SHA signed | 256 bits of `SecureRandom`, base64url |
| Lifetime | 15 minutes | 7 days |
| Storage server-side | None (stateless) | SHA-256 hash only |
| Revocable individually | No | Yes |
| Rotates on use | n/a | Yes, every time |

Refresh tokens are stored as **hashes**, so a database dump yields nothing an attacker can present
back. SHA-256 rather than BCrypt is correct here: the token is already 256 bits of cryptographic
randomness, there is no low-entropy secret to slow-hash, and lookup must be a single indexed read.

**Reuse detection.** Every refresh burns the presented token and issues a new one in the same
family. If a token that was already rotated away is presented again, two parties hold it — the safe
assumption is theft, so the entire family is revoked and both are logged out. A stolen token is
therefore useful only until the legitimate client next refreshes.

The revocation commits in its own transaction (`TokenFamilyRevoker`). Reuse detection ends by
throwing, and a revocation inside that transaction would be rolled back by the same exception.
**This bug was present during development**: the endpoint correctly returned 401 while silently
leaving the stolen family usable. It was caught by an integration test that asserted the sibling
token also stopped working, and that test now guards the behaviour permanently.

Tokens carry a `typ` claim, so a refresh token cannot be presented as a bearer token.

Access tokens cannot be revoked individually. That is a deliberate trade: revocation is handled by
the stateful refresh chain, which keeps the hot path free of database reads. The window is bounded
by the 15-minute lifetime. Instant revocation would need a denylist.

---

### One-time codes

Codes are six digits — a space of a million — so everything protecting them is about limiting how
many guesses reach that space and how long it stays open.

| Control | Value | Why |
|---|---|---|
| Lifetime | 5 minutes | The window an intercepted code is useful for |
| Attempts | 5 | 1-in-200,000 per code, and the code is burned at the fifth |
| Resend cooldown | 60 seconds | Stops the endpoint being used to bill or flood a handset |
| Live codes per destination | **exactly one** | A resend replaces; it does not accumulate |
| Endpoint rate limit | 5 per 15 min | A second ceiling, independent of the per-destination one |

Stored as a **keyed HMAC**, never as plaintext and never as a bare digest. A bare SHA-256 of six
digits is trivially reversed by precomputation; the server key is what makes the stored value
worthless on its own. Not BCrypt: a code is protected by being short-lived and attempt-limited, not
by being expensive to verify, and BCrypt would put tens of milliseconds of deliberate work on an
endpoint meant to be cheap.

Comparison is constant-time, so response timing cannot be used to recover a code one digit at a
time.

The attempt counter commits in its own transaction. This is not a detail: the check ends by
throwing, and counting inside that transaction meant the rollback undid the increment, leaving the
limit permanently unreached. See `VerificationAttemptService`.

### Not revealing who banks here

`POST /auth/phone/code` answers `202` with an identically shaped body whether or not the number
belongs to an account, and sends nothing at all for one that does not — which also stops the
endpoint being used to send messages to strangers. A number that is on a profile but never verified
is treated as not registered, so typing somebody else's number into a profile cannot start
delivering them sign-in codes.

Registration is the one exception: a number already in use is reported as such, because the
alternative is a customer who verifies a code and then cannot proceed with no explanation. The same
is already true of a taken username or email.

### Message content

An SMS or email from MBank never contains a password and never contains any personal detail beyond
the one the recipient just typed in. The development providers log the message body deliberately —
that is how a developer completes a local sign-in — which is exactly why **a production instance
refuses to start while either provider is still the development stub**, or while
`EXPOSE_VERIFICATION_CODES` is enabled.

---

## Card data

The most sensitive data in the system, and the module is arranged around holding as little of it as
possible.

| Asset | Treatment |
|---|---|
| Full number | AES-256-GCM ciphertext, fresh IV per card, decrypted only by an audited reveal |
| Number uniqueness | Keyed HMAC fingerprint; the plaintext is never compared |
| BIN and last four | Stored in the clear — they identify a scheme and a card, not a credential |
| **Verification value** | **Not stored at all.** Derived on demand from a server-side key |
| Cardholder name, expiry | Stored plainly; neither is secret |

GCM rather than CBC: it authenticates as well as encrypts, so a tampered ciphertext fails to decrypt
instead of producing a different valid-looking number.

Three keys derived from one root under distinct domain labels — encryption, fingerprinting,
verification value — so compromising one purpose does not yield the others.

**Reveal** is a `POST`, never a `GET`: the response carries a card number, and a `GET` would be
cacheable by intermediaries, linkable, and replayable from browser history. It answers with
`Cache-Control: no-store`, is limited to 10 calls an hour per customer, and writes an audit record
on **every** call including the successful ones — which is what makes a leak investigable. The web
client holds the values in component state only, clears them when the dialog closes, and clears them
after a minute regardless.

Administrators have no route to a customer's card. The ownership check has no admin bypass, unlike
account lookups, because no workflow here needs one.

These are demo credentials. MBank has no payment processor and no scheme membership; the API marks
every reveal `simulated: true` and the interface says so on the same screen as the numbers.

---

## Authorization

Two layers, both server-side:

1. **Role-based**, at the URL level in `SecurityConfig` and again with `@PreAuthorize` on each admin
   controller. The duplication is intentional: if a refactor moves or renames a route, the
   annotation still holds.
2. **Ownership**, in the service layer. Every read that takes an account id goes through
   `AccountService.getAccessibleAccount`, the single place ownership is enforced. Scattering that
   check across controllers is how insecure direct object references get introduced — one endpoint
   is written without it and nobody notices.

Transaction history is scoped **in the query**, not filtered after loading. A customer's search is
restricted to their own account ids before it reaches the database, so there is no moment at which
another customer's rows sit in memory waiting for a check somebody might forget to write.

### Not-found rather than forbidden

Requesting another customer's account returns `404 ACCOUNT_NOT_FOUND`, not `403`. Answering `403`
would confirm that the id exists, which is itself a disclosure.

### Administrators cannot spend

An administrator may read any account — that is the job. An administrator may **not** initiate a
transfer from a customer account; `TransferService` checks ownership specifically, and `AdminIT`
asserts the refusal.

---

## Input handling

- **Bean Validation** on every request DTO, with a custom `@StrongPassword` constraint.
- **Unknown JSON properties are rejected.** A request posting `{"amount": 10, "role": "ADMIN"}` gets
  a 400 rather than having the stray field silently ignored. This is a mass-assignment guard as much
  as a strictness setting.
- **The DTO shape is the authorization boundary.** `UpdateProfileRequest` contains only first name,
  last name and phone number. Role, status, password hash, balances and ids are not representable,
  so no amount of extra JSON can reach them.
- **No SQL is ever concatenated.** All access goes through Spring Data JPA and the Criteria API, so
  values are bound as parameters. `SecurityIT` posts an account number containing a `DROP TABLE`
  payload and asserts it is treated as data.
- **Sort properties are whitelisted.** Spring Data would otherwise sort by any entity property a
  client names, including ones outside the public contract; an unknown name also reaches the
  persistence layer as an exception rather than a validation error.
- **Page size is clamped** to 100, so no client can ask for the whole table.

---

## Transport and headers

| Header | Value | Reason |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | Stops content-type guessing |
| `X-Frame-Options` | `DENY` | No framing, so no clickjacking |
| `Referrer-Policy` | `no-referrer` | Account ids in URLs never leak to third parties |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` | The API serves JSON only; a restrictive policy costs nothing and blocks anything a future misconfiguration might render |
| `Strict-Transport-Security` | 1 year, includeSubDomains | Assumes TLS is terminated in front |

### CORS

Origins are configured explicitly through `CORS_ALLOWED_ORIGINS` and credentials are allowed. A
wildcard is **not** supported: `*` combined with credentials is rejected by browsers anyway, and
would be unsafe here. Under the `prod` profile the application refuses to start if `*` is present.

### CSRF

Disabled — deliberately, not by omission. The API is stateless and token-based: an attacker's page
cannot make a browser attach an `Authorization` header it does not have. This reasoning holds only
because the access token is never stored in a cookie. If tokens ever move to cookies, CSRF
protection must come back with them.

---

## Rate limiting

In-process token buckets on the endpoints worth attacking:

| Endpoint | Budget |
|---|---|
| `POST /api/v1/auth/login` | 5 per minute |
| `POST /api/v1/auth/register` | 3 per 5 minutes |
| `POST /api/v1/auth/refresh` | 20 per minute |
| `POST /api/v1/users/me/password` | 5 per 15 minutes |
| `POST /api/v1/transfers` | 20 per minute |
| `POST /api/v1/beneficiaries` | 10 per 5 minutes |
| `POST /api/v1/cards` | 5 per 10 minutes |
| `POST /api/v1/cards/*/details` | 10 per hour |
| `POST /api/v1/auth/phone/code` | 5 per 15 minutes |
| `POST /api/v1/auth/phone/login` | 10 per 5 minutes |
| `POST /api/v1/auth/phone/confirm` | 10 per 5 minutes |
| `POST /api/v1/auth/email/verify` | 10 per 15 minutes |

Revealing a card number is the most sensitive read in the application, so its budget is the
tightest: a stolen session should not be able to walk an entire wallet in seconds. The phone-code
budget is low because each request may put a real SMS on somebody's handset and a charge on ours;
it sits alongside the per-destination cooldown enforced in `VerificationService`, which the bucket
does not replace.

Tokens refill continuously rather than in discrete windows, which avoids the burst-at-the-boundary
weakness of fixed windows, where a caller spends a full budget at 59s and another at 61s.

Callers are keyed by user id when authenticated and by client address otherwise, so one signed-in
customer cannot exhaust the budget of everyone behind the same NAT. The socket address is used
rather than `X-Forwarded-For`: a client can set that header freely, so trusting it would let an
attacker mint a fresh bucket per request. A real deployment terminates TLS at a proxy and sets
`server.forward-headers-strategy` so the container performs the substitution from a trusted source.

**Limitation, stated plainly:** the state lives in this process. That is correct for the
single-node stack shipped here, and wrong behind a load balancer, where each node would enforce the
limit separately. Moving to a shared counter (Redis `INCR` with expiry, or a Lua token bucket) is
the next step, and only `TokenBucketRateLimiter` would change.

---

## Secrets

- No secret is committed. `JWT_SECRET`, `DB_PASSWORD` and the rest come from the environment;
  `.env.example` holds placeholders only and `.env` is gitignored.
- The JWT secret must be at least 32 characters, validated at startup so a weak configuration fails
  fast rather than running with weak signing.
- `StartupSecurityCheck` **refuses to start** a `prod` instance whose secret is one of the values
  published in this repository. Documenting "change the secret before deploying" achieves nothing on
  the day somebody forgets; a startup failure is impossible to ship past.
- Swagger UI and the OpenAPI document are disabled under the `prod` profile.

---

## Error handling and information disclosure

Every failure returns the same `ApiError` body with a stable machine-readable `code`. Stack traces,
class names and framework internals never reach a client — `SecurityIT` asserts that error bodies
contain none of `java.lang`, `org.springframework`, `com.minibank` or `Exception`.

Database constraint violations are logged (they contain table and constraint names) but returned as
a generic `409 CONFLICT`.

---

## Audit trail

Append-only, mapped `@Immutable`. Records logins, failed logins, lockouts, token refreshes, reuse
detection, password changes, account creation and freezing, every transfer outcome, beneficiary
changes and administrative actions.

Each record carries the correlation id of the request that caused it, so a client-visible
`X-Request-Id` leads straight to the audit row and the log lines.

`AuditService` offers two propagation modes, and choosing between them is a real decision:

- `record` joins the caller's transaction — a completed transfer's audit row must not survive if the
  transfer rolls back;
- `recordIndependently` commits separately — a failed login or a rejected transfer is precisely the
  case where the surrounding transaction is about to roll back, and losing that record would blind
  the security trail exactly when it matters.

---

## Data minimisation

- The password hash is never in any response. Mapping is done by hand rather than by exposing
  entities, so adding a sensitive column cannot silently start leaking it.
- Administrative user listings show **masked** phone numbers (last four digits). Support staff can
  identify a customer; a back-office list has no operational need for the full number.
- The beneficiary endpoint returns only an abbreviated holder name ("Jane S.") — enough to confirm
  the payer is paying the right person, without turning it into a directory that maps account
  numbers to full identities. That endpoint is also rate limited.

---

## Frontend

The React client enforces **navigation**, not authorization. Every endpoint it calls applies its own
rules server-side, because hiding a route in a bundle the user has already downloaded protects
nothing. `SecurityIT` verifies the server refuses directly, without a browser involved.

**Known weakness:** tokens are kept in `localStorage`, which any script running on the page can
read. This is the pragmatic choice for a demo and is called out rather than glossed over. Production
should keep the refresh token in an httpOnly, SameSite cookie and pair that with CSRF protection on
the refresh endpoint.

---

## Automated security checks

| Check | Where | What it covers |
|---|---|---|
| SpotBugs | Every build (`mvn verify`) | Static analysis; the build fails on findings |
| Checkstyle | Every build | Hygiene, including a ban on `printStackTrace()` and `System.out` |
| Trivy filesystem | `security.yml` | Dependency CVEs, secrets, misconfiguration |
| Trivy image | `security.yml` | Base image and OS package CVEs |
| CodeQL | `security.yml` | `security-extended` queries: injection, path traversal, unsafe deserialisation |
| Dependabot | Weekly | Grouped dependency updates for Maven, npm, Actions and Docker |

Trivy runs in report-only mode and uploads SARIF to code scanning. A newly disclosed CVE in a
transitive dependency should raise an alert, not block an unrelated pull request at 3am.

---

## Threats explicitly tested

`SecurityIT` and `AuthenticationIT` cover:

- unauthenticated access to every private endpoint;
- a customer reaching admin endpoints;
- a customer reaching another customer's account, balance, transactions, transfers or beneficiaries;
- expired, tampered, wrongly signed and foreign-issuer tokens;
- a forged token claiming the `ADMIN` role;
- a refresh token used as a bearer token;
- privilege escalation through mass assignment on registration and profile update;
- password hashes in responses;
- stack traces in error bodies;
- brute force and account lockout;
- refresh token reuse and family revocation;
- SQL injection payloads in account numbers;
- log forging through the correlation id header.
