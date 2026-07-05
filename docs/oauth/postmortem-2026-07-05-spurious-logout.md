# Post-mortem: spurious logout on 1.275.3 (`invalid_grant`)

- **Date of incident:** 2026-07-05 17:56 UTC (10:56 local)
- **App version:** 1.275.3 (`1275003`), atproto-kotlin pin `9.7.3`
- **Device:** Google Pixel 10 Pro XL, Android 17
- **Impact:** 1 user, 1 session — forced sign-out to the login screen while scrolling the Feed
- **Epic:** nubecita-09xt (spurious-logout telemetry)
- **Status:** root-caused; no app code change (the mitigating machinery is already shipped). SDK residuals tracked in [atproto-kotlin#164](https://github.com/kikin81/atproto-kotlin/issues/164).

## Summary

A user was signed out mid-session. Telemetry showed the OAuth session was **cleared** because the auth server rejected the refresh token with `invalid_grant` ("Refresh token revoked", HTTP 400). Initial hypothesis was a classic concurrent-refresh race on a single-use refresh token. Investigation showed the single-flight mitigation for that race **is already implemented and shipped** in the affected build, and that Nubecita correctly funnels all callers through one shared client. The refresh token was therefore **already dead before the burst** — most plausibly a token-durability gap across process death, or a genuine server-side revocation/expiry. The event was not a live concurrency race.

## Timeline (UTC)

| Time | Event |
|---|---|
| 17:56:16.205 | `NotificationsPolling` getUnreadCount → *No authenticated session* |
| 17:56:16.932 | `PostAudienceDefault` refresh → `invalid_grant` (HTTP 400) → `session_cleared` |
| 17:56:17.157 | `ModerationPrefs` refresh → `invalid_grant` (HTTP 400) → `session_cleared` |

Three background components each triggered a token refresh within ~1 s while the Feed was foregrounded; two produced `session_cleared { reason: invalid_grant }`. `session_clear_reason: invalid_grant` is stamped on every event.

## How the diagnosis was performed

Release builds emit almost nothing to logcat (auth routes to Crashlytics), and the logout telemetry was buffered on-device because the process was only *frozen*, never cold-restarted. Steps:

1. Confirmed via Crashlytics + GA4 that no `auth_keyset_regenerated` and no `session_read_error*` fired — ruled out Tink keyset regeneration and the read-path/splash bailout.
2. Force-stopped and relaunched the app (flushing buffered telemetry). With network up, it **still** landed on the login screen → the session was genuinely gone from disk, not a recoverable transient read error.
3. Retrieved the flushed Crashlytics event: `EncryptedOAuthSessionStore.clear` ← `DpopAuthProvider.failRefresh` ← `applyRefreshResponse` ← `refreshTokensWithNonce`, `session_clear_reason: invalid_grant`, on the user's exact device/version.

## Root cause

`clear()` was called because the refresh POST returned `invalid_grant`. AT Proto refresh tokens are **single-use / rotating** (atproto OAuth spec: *"refresh tokens are generally single-use … client implementations may need locking primitives to prevent concurrent token refresh requests"*; mechanism per RFC 9700 §4.14 rotation + reuse detection, surfaced as `invalid_grant` per RFC 6749 §5.2).

**Crucially, the affected build already single-flights refresh:**

- `DpopAuthProvider` (atproto-kotlin ≥ v9.7.1, fully in v9.7.3 via #159/#162/#163) has a per-instance `refreshMutex`, an in-flight `rotatedWhileWaiting` dedup, a store-based `adoptStoredSessionIfRotated` cross-instance dedup, a `persistRotatedSession` retry, and a `failRefresh` that clears **only** on `invalid_grant` (everything else stays transient).
- Nubecita's `DefaultXrpcClientProvider` is `@Singleton` and caches **one** `XrpcClient` / `DpopAuthProvider` per DID; there are **no** `android:process` splits, so feed, notifications, moderation, post-audience, DM poll, and widgets all share that one instance in one process.

With a shared mutex, a *valid* refresh token cannot produce two `invalid_grant`s — the first caller rotates it and the second short-circuits on `rotatedWhileWaiting`. Two `invalid_grant`s therefore mean the persisted refresh token was **already dead before the burst**. The two most likely reasons:

1. **Token durability gap across process death (most likely, partially preventable).** A prior refresh rotated the token server-side, but the process was killed in the millisecond window between the server's `200` and the DataStore write (background / low-memory). Cold start then loaded a stale, already-consumed token. The SDK's own `onPersistFailure` KDoc acknowledges this residual.
2. **Genuine server-side revocation or 2-week public-client session expiry (unpreventable; a correct logout).**

These could not be distinguished on this single event because the `days_since_login` GA4 dimension was not registered until the same day (forward-only, no backfill).

## What worked

- The single-flight machinery and the fatal-vs-transient split behaved correctly — no keyset regen, no read-path logout, no clearing on transient errors.
- The epic's telemetry (Crashlytics non-fatal + `session_cleared` GA4 event with `reason`) is what made the root cause legible at all. Telemetry-first paid off.

## What we found that is worth fixing (residuals)

Filed as **[atproto-kotlin#164](https://github.com/kikin81/atproto-kotlin/issues/164)**:

- **§1 (bug):** after the first waiter's `invalid_grant` clears the store, coalesced waiters **re-POST the same dead token** (a failed refresh doesn't mutate the in-memory `session`, and `adoptStoredSessionIfRotated` sees a now-empty store) — producing the redundant second `invalid_grant`/`clear()` we observed 236 ms apart. Fix: under the lock, if `sessionStore.load() == null` while a session is still held, fail fast without re-POSTing.
- **§2 (hardening):** `failRefresh` clears without re-checking the store for a concurrent valid rotation — matters for multi-instance/multi-process consumers. Fix: reload before clear; adopt a newer token if present.
- **§3 (inherent):** the receive→persist durability window across process death — narrow and document; not fully closeable client-side.

## Action items

| Owner | Action | Status |
|---|---|---|
| atproto-kotlin | §1 coalesced-waiter fail-fast; §2 recheck-before-clear; §3 document durability window | [#164](https://github.com/kikin81/atproto-kotlin/issues/164) filed |
| nubecita | Registered `days_since_login` + `cause` GA4 custom dimensions | Done 2026-07-05 |
| nubecita | Consider a `store_had_token` custom key at clear time to separate the durability gap (§3) from genuine revocation | Proposed |

## Lessons

1. **Verify the fix isn't already shipped before building it.** The obvious mitigation (single-flight) was already in the shipped build; the real leads were subtler. Reading the actual `DpopAuthProvider` + the DI scope, not just the stack trace, is what turned the diagnosis.
2. **Rotating single-use refresh tokens have an irreducible durability window.** A client can serialize refreshes and persist-before-return, but a process kill between server rotation and disk write will still orphan the token. Telemetry to *measure* it beats chasing a total fix.
3. **Register telemetry dimensions ahead of the data.** `days_since_login` would have distinguished expiry from a premature logout on this very event; because registration is forward-only, it couldn't.
