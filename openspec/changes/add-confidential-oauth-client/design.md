# Design

## Context

See proposal.md for motivation. This section records what the design rests on.

### What the authorization server does (read from source, not yet exercised)

Checked against `bluesky-social/atproto` at `665c90c` (2026-11-25), package `oauth-provider`. Every item below is from reading code. The rehearsal (Decision 13) is what turns them into observed facts, and nothing in production changes before it.

| Behaviour | Where |
|---|---|
| Public clients: 14-day session, 14-day refresh. Confidential: 2-year session, 3-month refresh. | `oauth-constants.ts`, `client/client.ts` `sessionLifetime` / `refreshLifetime` |
| Lifetime is judged against the client's *current* metadata, from the session's original creation time. | `oauth-provider.ts` `validateRefreshGrant` |
| `application_type: native` must use `none`; `web` rejects custom-scheme and loopback redirects but accepts https App Links. | `client/client-manager.ts` L424–445, L474–620 |
| Client authentication runs *before* the refresh token is looked up. A missing or bad assertion fails with `invalid_request` / `invalid_client` and the session is not touched. | `oauth-provider.ts` `token` → `authenticateClient` |
| A session created under `none` may refresh with `private_key_jwt`; the refresh rebinds it to that key. | `compareClientAuth` (`case 'none'`), `token-manager.ts` `rotateToken` |
| Once bound, a refresh with a different `kid`, `alg` or key fails and the session is deleted. So does a refresh with `none`. | `compareClientAuth`, `refreshTokenGrant` catch block |
| While metadata says `none`, a supplied client assertion is ignored. | `client.ts` `authenticate` returns early for `none` |
| Assertion: `sub` = client id, `aud` = issuer, `iat` at most 60 s old, `jti` required and single-use, `kid` header required. A reused `jti` fails with `invalid_grant` (before token lookup). | `client.ts` `authenticate`, `authenticateClient` |
| Revocation also authenticates the client. PAR is mandatory. | `revoke`, `metadata/build-metadata.ts` |
| Client metadata is cached for 10 minutes per server process. | `oauth-provider.ts` constructor |
| A replayed refresh token deletes the session. | `token-manager.ts` `consumeRefreshToken` |

### What exists in our code today

- The app's default redirect is already `https://nubecita.app/oauth-redirect/` (a verified App Link). The custom scheme is only a legacy fallback, reached through the GitHub Pages fallback page.
- `AtOAuth` and `DpopAuthProvider` (SDK) POST straight to the authorization server. Code exchange and refresh use a dedicated `tokenHttpClient` that never re-sends a dropped POST; PAR and revoke use the general client.
- `DpopAuthProvider.failRefresh` clears the session only on `invalid_grant`; everything else is retryable. That is the property this design extends to the backend.
- `OAuthSession` stores the token endpoint but not the issuer, and has no notion of a client key.
- `:core:auth` is a flavored module (`bench` / `production`, plus `authHarness` source sets).
- `nubecita.app` is static GitHub Pages behind Cloudflare DNS. Firebase project `nubecita-2a4c1` already provides App Check (Play Integrity in release, debug provider in debug).

## Goals / Non-Goals

**Goals**

- Sessions qualify for the 2-year confidential-client lifetime.
- No backend failure, bug or outage can clear a session on a device.
- The path from today to confidential never signs out a user on a current build, and never deletes a straggler's session.
- The signing key cannot be copied out of Google Cloud.
- A lost refresh response no longer ends a session.

**Non-Goals**

- The backend holding sessions, DPoP keys, or anything that would let it act as a user.
- Any change to XRPC routing, session storage format (beyond new optional fields), or multi-account behaviour.
- Changing production client metadata inside this change (Decision 15).

**Anti-goal**

- A design that works but leaves the app's sessions resting on a reading of the rules Bluesky has not endorsed.

## Approaches

Three ways to become a confidential client were considered.

1. **Token-mediating backend (chosen by Francisco).** The app sends PAR, code exchange, refresh and revoke to our backend, which adds the assertion and forwards. This is the arrangement matthieusieben described in #2656 as making the client `web`: "the entity obtaining the tokens is the back-end". Costs: tokens transit the backend; it must fetch arbitrary authorization servers safely; an extra hop sits on the refresh path.
2. **Assertion signer only.** The backend returns a 60-second assertion and the app calls the authorization server itself. Works today because the server does not bind an assertion to a request. No token transit, no outbound fetches, no extra hop. Rejected because it claims `web` while the device still obtains the tokens — the misrepresentation #2656 warns about — and it is proposal 0010 without the DPoP binding that makes 0010 acceptable.
3. **Wait for proposal 0010.** Rejected: its server change (`atproto#3889`) is a draft, untouched since June 2025.

The SDK seam (Decision 4) is narrow enough that approach 2 could replace approach 1 later without touching `AtOAuth`.

## Decisions

Decisions marked **(Francisco)** were made in the brainstorm. The rest are the author's calls and are open to correction.

### 1. Backend platform: Firebase Functions 2nd gen, source in `kikin81/nubecita-web` (Francisco)

App Check is already in the same project, deploys can use Workload Identity Federation, and the emulator fits the test plan. The function and the metadata it must match live in one repository and change in one pull request.

- One `onRequest` function, three paths (`/par`, `/token`, `/revoke`), Node 24, TypeScript. (Node 24 rather than 22: the Functions runtime for 22 is deprecated on 2027-04-30.)
- 1 vCPU, concurrency 80 (the work is all I/O), max 10 instances, min 0 instances to start.
- Alternatives: a Cloudflare Worker (no cold starts and no private-network reach, but a readable Worker secret, hand-rolled App Check verification and a long-lived deploy token); the Fly push gateway (would put the key in a service that takes calls from any PDS); AWS (nothing of ours runs there).

### 2. Signing key in Cloud KMS, non-exportable

One asymmetric signing key (`EC_SIGN_P256_SHA256`, giving `ES256`) per client: production, test, rehearsal. The function calls `asymmetricSign` per assertion and converts the DER signature to the JOSE form. The `kid` is a short label mapped to a KMS key version in function configuration.

- Why not `defineSecret`: a leaked key forces rotation, and rotation signs out every session bound to the key. A key that cannot be exported can be misused only while the function is compromised.
- Cost: a KMS call per forwarded request (two during a nonce retry). Latency is measured in task 3.6; if p95 on the refresh path is unacceptable the fallback is `defineSecret` with the same interface.
- Local development signs with the *test* key through the developer's own Google credentials. No private key file exists anywhere.

Francisco chose "Firebase" when offered Firebase+KMS, Firebase+`defineSecret`, or a Worker; KMS is the author's recommendation carried forward and should be confirmed at review.

### 3. Wire contract between app and backend

The app sends the OAuth form it would have sent to the authorization server, unchanged, plus headers:

```
POST {backend}/par | /token | /revoke
Content-Type: application/x-www-form-urlencoded     (the OAuth form, including client_id)
DPoP: <proof, target = the authorization server's own endpoint>
Nubecita-Issuer: https://bsky.social
Nubecita-Client-Auth-Kid: <kid>                     (when the session or sign-in has one)
X-Firebase-AppCheck: <token>                        (required for sign-in when enforcing)
```

Relayed response — upstream status and body, plus:

```
Nubecita-Mediator-Origin: upstream
Nubecita-Client-Auth-Kid: <kid used>
Nubecita-Client-Auth-Method: none | private_key_jwt
Nubecita-Upstream-Date: <the authorization server's Date header>
DPoP-Nonce: <relayed>
Nubecita-Replayed: 1                                (only when served from the replay store)
```

Backend-produced response — a 4xx/5xx with:

```
Nubecita-Mediator-Origin: mediator
{"error": "mediator_<reason>", "forwarded": false | true | "unknown"}
```

Reasons: `not_found`, `bypass`, `attestation_required`, `unknown_client`, `unknown_key`, `issuer_rejected`, `rate_limited` (also used for the minimum refresh interval), `upstream_timeout`, `upstream_unreachable`, `in_progress`, `internal`. The `mediator_` prefix guarantees no backend error can be read as an OAuth error. Header and reason names are the builder's to adjust; the three properties that must hold are in the specs (origin marking, forwarded flag, never `invalid_grant`).

The backend always attaches an assertion, whatever the metadata says. While the client is public the server ignores it, so the later metadata change needs no backend or app release. If the rehearsal shows a server rejecting an assertion for a public client, the fallback is to attach it only when the client's published metadata registers `private_key_jwt`; the worst case is then up to 10 minutes of `invalid_request` refreshes, which are retryable and consume nothing.

Separately, and always, the backend fetches the client's own published metadata (5-minute cache) to fill `Nubecita-Client-Auth-Method`. That report can lag or lead an individual server process by up to its 10-minute cache. Its two consumers tolerate that: the renewal prompt (Decision 14) and key-id storage (Decision 5).

**Cache skew at the flip is a session-loss path and needs a guard.** Server processes cache client metadata independently for 10 minutes, and GitHub Pages adds its own 10-minute cache in front. If a session's first post-flip refresh lands on a process that already sees `private_key_jwt` (binding it) and a second refresh within the window lands on a process still caching `none`, that second refresh is judged as `none` against a bound session and the session is deleted. Normal use refreshes about once per access-token lifetime, so two refreshes inside the window are rare, but not impossible. The guard is in Decision 9 (minimum refresh interval). A sign-in that straddles the skew (PAR on one process, code exchange on another) fails once and succeeds on retry; no session is lost.

### 4. SDK seam: a token-endpoint transport

`atproto-kotlin` gains a public interface that carries one client-authenticated OAuth request and returns one response:

```kotlin
interface TokenEndpointTransport {
    suspend fun send(request: TokenEndpointRequest): TokenEndpointResponse
}
```

- `TokenEndpointRequest`: kind (PAR, code exchange, refresh, revoke), the authorization server endpoint URL, the issuer, the form parameters, the DPoP proof, and the session's key id if any.
- `TokenEndpointResponse`: status, body, DPoP nonce, server date, **origin** (authorization server or mediator), the key id used, the reported auth method, and whether it was a replayed response.
- A "request provably not sent" failure is a dedicated exception type so the existing pre-send logic keeps working.
- The default implementation posts directly, preserving today's behaviour and the two-client split (`tokenHttpClient` for the single-use requests).
- `AtOAuth` and `DpopAuthProvider` route all four requests through the transport. `failRefresh` gains one rule: a response whose origin is not the authorization server is always retryable.
- `OAuthSession` gains `issuer: String? = null`, `clientAuthKid: String? = null`, `clientAuthMethod: String? = null` and `refreshInDoubt: Boolean = false`. `PendingAuth` gains `clientAuthKid: String? = null`. All have defaults, so stored sessions from older versions load unchanged. A null issuer is derived from the token endpoint's origin.
- `refreshInDoubt` is persisted as true *before* a refresh is sent through a transport that is not the direct one, and cleared when a definitive answer arrives (saved together with the rotated tokens on success, so the success path costs one extra write, about hourly). It survives process death. The request carries it to the transport, which must not use a direct path for an in-doubt token (Decision 6).

The SDK stays free of Firebase and of our wire contract: the Nubecita transport lives in `:core:auth`. Alternative considered: a generic "mediated" transport in the SDK. Rejected until a second consumer exists.

### 5. Per-session key binding

A session is bound for life to the key that signed its first confidential refresh (or its PAR, for sessions created after the flip). The backend is stateless about sessions, so the app carries the binding: it stores the key id the backend reports and sends it back every time. The backend refuses rather than substitutes when it lacks a named key, because a wrong key deletes the session.

The app stores a session's key id only from a response that also reports the client as confidential, and never replaces it afterwards. While the client is public the server binds nothing, so nothing is pinned. (An in-progress sign-in always records the key id of its PAR, because the code exchange minutes later must match it.) Because the reported method can disagree with a server process for a few minutes around the flip, the runbook forbids changing the default key within 24 hours of a flip.

Rotation follows from this:

- **Routine** (optional): add a new key to the metadata and the backend, make it the default. Existing sessions keep their key. Retire the old key two years after it stopped being the default.
- **Emergency**: removing a key from the metadata makes every session bound to it unusable within about 10 minutes. There is no migration path. This is the revocation lever Bluesky intends confidential clients to have.

### 6. Failure classification and the direct fallback

One rule: **only an `invalid_grant` whose origin is the authorization server clears a session.**

| What happened | App does |
|---|---|
| Could not connect / resolve the backend (request not sent), token not in doubt | Retry directly against the authorization server |
| Backend-produced `bypass` (`forwarded: false`), token not in doubt | Retry directly |
| Either of the above, but an earlier attempt with this refresh token had an unknown outcome | Retryable failure; no direct retry |
| Other backend-produced error | Retryable failure; session kept; no direct retry |
| Timeout or connection lost after sending | Retryable failure; session kept; no direct retry |
| Relayed response | Handled exactly as a direct response is today |

"Provably not forwarded" has to be judged per refresh token, not per request. If attempt one was mediated and its response was lost, the token may be spent; a second attempt that cannot reach the backend must not go direct, or it replays the token and the server deletes the session. The persisted `refreshInDoubt` flag (Decision 4) is what carries that across attempts and across process death. An in-doubt token waits for the backend, which is the only place that can answer it safely (Decision 9).

The direct retry is only useful while production metadata is public. After the flip it fails with `invalid_request`, which is retryable and consumes nothing, so the rule needs no knowledge of which phase the client is in. Removing the fallback is a follow-up after the flip.

**Sessions that can never refresh again are out of scope here.** Emergency key removal, or a key the backend no longer holds, leaves a session that fails every refresh but is deliberately never cleared. The user can still sign out and in again from Settings. A prompt that offers it ("Reconnect @handle") is the renewal issue's UI, so that issue gains a second trigger: a relayed client-authentication failure that persists.

### 7. Kill switch on the backend, not in Remote Config

Bypass mode is a setting on the deployed service, changeable in under two minutes without a build. It takes effect on the next request from every device, adds no SDK to the app, and costs no battery. Remote Config would wait for each device to fetch. Bypass is only meaningful before the flip, which is the only time a direct path exists.

In bypass mode the backend still serves a stored refresh response when it has one (Decision 9). Only requests it has no stored answer for get the bypass reply.

### 8. Timeouts: no less patient than today, and the backend answers first

Upstream wait 25 s < function timeout 30 s < a 40 s request timeout on the app's mediated requests. Today the app waits 30 s for the authorization server directly, so the mediated path gives a slow server about as long as it gets now; a shorter upstream limit would turn slow-but-successful refreshes into lost responses. The app gets either a relayed response or an explicit `upstream_timeout` with `forwarded: "unknown"`, rather than timing out first and learning nothing.

### 8a. The backend URL is a domain we control

Shipped builds carry the backend URL forever, and after the flip there is no fallback path. The app therefore calls `https://auth.nubecita.app`, a Firebase Hosting custom domain whose only rule rewrites every path to the function. Region, function name and instance settings can then change without a release. The Cloudflare zone needs one DNS-only record for the subdomain.

### 9. Replay-safe refresh (Francisco: include it)

Applies to `grant_type=refresh_token` only. Stored in Firestore, one document per spent refresh token.

- **Document id**: SHA-256 of the refresh token with a fixed prefix. Refresh tokens are 256 bits of randomness, so the hash reveals nothing.
- **Encryption**: AES-256-GCM with a key derived from the spent refresh token itself (HKDF, per-entry salt). The backend does not keep the spent token, so an entry cannot be decrypted by anyone holding only the database. This replaces the "envelope encryption" described in the brainstorm: it is stronger and needs no extra KMS call.
- **Single flight**: before forwarding, create the document in a `pending` state inside a transaction. If it already exists as `pending` and is younger than 60 s, answer `in_progress` without forwarding. Sixty seconds is twice the function timeout, so an older `pending` entry means the earlier attempt is certainly finished and its outcome was never recorded; only then does a new attempt take over and forward. That re-forward can still be a replay if the server completed the first attempt. It is the same risk the app runs today on every retry, now confined to this one case.
- **Upstream timeout**: the `pending` entry is left in place, so retries inside the 60 s window are held back rather than re-forwarded.
- **On upstream 200**: write the encrypted response and mark `done` *before* responding to the app. If the write fails, relay anyway.
- **On any other upstream answer** (including a nonce challenge): delete the `pending` document, since the refresh token was not consumed.
- **On a hit**: verify the DPoP proof's signature against its embedded key, that the key's thumbprint equals the stored one, that method and target match the token endpoint, and that it is recent. Then return the stored response with `Nubecita-Replayed: 1`.
- **Supersession**: each entry records a hash of the refresh token it issued. When a refresh arrives using that token, the entry is deleted — the app evidently received it.
- **Expiry**: 24 hours, enforced on read and by a Firestore TTL policy. Twenty-four hours covers the case where the process died mid-refresh and the app is next opened the following day.

- **Minimum refresh interval (the flip guard)**: an operator setting, off by default. When set to N minutes, the backend refuses (without forwarding) a refresh whose token it issued less than N minutes ago, which it knows from the supersession record. Turned on for the hour around a metadata flip with N above the combined cache window, it makes two refreshes of one session inside the skew window impossible (Decision 3). The app still holds a fresh access token at that point, so the refusal costs nothing.

The irreducible gaps: the function dies after the server answered and before the write completes (milliseconds, where today the equivalent window is the whole mobile round trip), and a server that answers after the 25 s limit.

Until this group lands, the mediated path has no store and behaves like today's direct path with one extra datacenter hop. That is acceptable while sessions are capped at 14 days, and it is why this group is a gate for the production flip (Decision 16) even though it may land after the stack.

### 10. App Check policy (Francisco: strict at sign-in, Play Integrity)

- PAR and code exchange: refused without a valid token once enforcement is on.
- Refresh and revoke: verdict recorded, never blocking; a switch can make it strict.
- Enforcement starts in observe-only mode. The observed sign-in pass rate is one of the flip gates.

Why sign-in is the place that matters: the GitHub Pages fallback page still forwards the authorization code to `app.nubecita:`, a scheme any app can register on a device where Nubecita is absent. Without attestation another app could run a sign-in through our backend and hold a two-year session presented to the user as Nubecita. A refresh, by contrast, already requires the current refresh token and the device's DPoP key.

The app never decides enforcement itself: if it cannot obtain a token it sends the sign-in request without one, and the backend refuses or (in observe-only mode) records and proceeds.

`:core:auth` gets an `AttestationTokenProvider` interface; `:app` implements it with Firebase App Check. This keeps Firebase out of `:core:auth`, matching how analytics is abstracted.

### 11. Outbound request guard

The app supplies an issuer; the backend never accepts an endpoint URL. For each issuer it fetches `/.well-known/oauth-authorization-server`, checks the document's `issuer` matches, and caches it for 10 minutes. All outbound requests:

- https only, DNS hostname only (no IP literals), default port.
- Hostname resolved once; every resolved address checked against loopback, private, link-local (which covers the cloud metadata address), unique-local and unspecified ranges; the connection is made to the checked address.
- No redirects. Response capped at 64 KB and 10 s.

An attacker who passes attestation can still name their own server as the issuer. They receive an assertion whose audience is their own server, which no other server accepts.

### 12. Logging, limits and alerts

- Structured logs with the fields listed in the backend spec and nothing else. A test asserts that a full request/response cycle leaves no token, proof, assertion or DID in captured log output.
- Per-address limit on `/par` (in-memory, per instance: 10 per 5 minutes). Max 10 instances.
- Alerts: backend-produced 5xx rate, relayed `invalid_grant` rate, attestation failure rate. A $5 budget alert.

### 13. Test and rehearsal clients

Two extra metadata files in `nubecita-web`, each with its own KMS key:

- `oauth/client-metadata-test.json` — permanently `web` + `private_key_jwt`. Used by debug builds through the existing `-PoauthClientMetadataUrl` override.
- `oauth/client-metadata-rehearsal.json` — starts as `native` + `none`, is flipped to `web` + `private_key_jwt` during the rehearsal, and is then reverted once to observe the deletion. Never used for anything else afterwards.

The rehearsal runs on a real device with a real account (never the `Pixel_10_Pro` or `Pixel_10_Pro_Fold` emulators, which hold real sessions). It must show:

1. A session created while public refreshes through the backend after the flip without re-auth.
2. A debug build with no backend URL (standing in for an old build, since the published app cannot use the rehearsal `client_id`) cannot refresh after the flip, is not signed out, and resumes once the same install is rebuilt with the backend URL.
3. The server accepts requests carrying an assertion while the client is still public.
4. A `web` client completes sign-in through the App Link redirect.
5. Two refreshes of one session in quick succession right after the flip: record whether the second fails or deletes the session. This is informational; it sizes the flip guard's interval.
6. On a *second*, disposable session only: reverting to public deletes a bound session on its next refresh.
7. **Deferred, and a gate for the production flip rather than for the stack**: the session from outcome 1 is kept alive by refreshing through the backend and is still valid on day 15 after its sign-in. That is the only direct observation that the lifetime really is extended. The rehearsal session is therefore started as early as the backend and a debug build allow, and the revert in outcome 6 is done on a different session after day 15.

Results go into `docs/oauth-confidential-client.md`. A surprise in any of 1–4 stops the work and returns to design.

**Scope of the guarantee.** Everything above is rehearsed against `bsky.social`, which runs the reference implementation. Self-hosted PDSes on a current release behave the same. A server on an old release, or a different implementation, may not allow a public session to become confidential, in which case that user signs in once after the flip. That is the outcome originally expected for everyone, and it is accepted for this minority. Telemetry records whether the issuer is `bsky.social` or another host (host category only), so the size of that minority is known before the flip.

Local development: the function runs in the Firebase emulator; the phone reaches it with `adb reverse tcp:5001 tcp:5001`; debug builds allow cleartext to `localhost` and `10.0.2.2` only, through a debug-only network security config; the debug App Check provider supplies the token.

### 14. Renewal prompt interaction

The separate renewal issue needs to know when a session will expire. The backend reports the client's registered auth method on every relayed response and the app stores it with the session (`clientAuthMethod`). `none` means 14 days from sign-in; `private_key_jwt` means two years from sign-in and three months from the last refresh. `LoginTimestampStore` already supplies the sign-in time.

### 15. This epic ends when the stack merges; the flip is a separate gated issue

Under the repository's rule that an epic is done when its stack merges, the production metadata change cannot be a child: it happens weeks later, behind gates. So:

- **This epic** delivers the SDK change, the backend, the app changes, the test and rehearsal clients, the rehearsal itself, replay-safe refresh, the privacy policy line and the runbook. When it merges, production builds use the backend while the client is still public. Session lifetime is still 14 days.
- **Follow-up standalone issues**, created with the epic and blocked on it:
  1. Flip production client metadata (gated as in the `oauth-client-registration` spec).
  2. After the flip: remove the direct fallback and the legacy custom-scheme handling.
  3. The renewal prompt (already planned).

### 16. Gates for the flip (Francisco)

- At least 95% of 7-day active users on a backend-capable version, with `sdk_gphone*` devices and the `OnePlus8Pro` bot traffic filtered out.
- At least 14 days since that version reached full rollout. No session on an older build can outlive 14 days.
- The observed App Check pass rate for sign-ins has been reviewed.
- A release at in-app-update priority 5 is live, so older builds show the immediate-update screen. This is the entire straggler plan; no re-auth prompt is needed because updating resumes the session.

Added by the author after review, as preconditions rather than judgment calls:

- Replay-safe refresh is live (Decision 9).
- The rehearsal session is still valid on day 15 (Decision 13, outcome 7).
- The flip guard is switched on for the hour around the change, and the default key is not changed within 24 hours of it.
- The share of sessions on issuers other than `bsky.social` has been looked at.

### 17. Delivery shape

| bd child | Repository | PR |
|---|---|---|
| OpenSpec change | nubecita | standalone docs PR to `main` |
| SDK transport seam | atproto-kotlin | own PR, then a release |
| Backend scaffold, deploy pipeline, repo security, KMS keys, `auth.nubecita.app` | nubecita-web | own PR |
| Backend mediation endpoints | nubecita-web | own PR |
| Test and rehearsal metadata + validation check | nubecita-web | own PR |
| App: SDK bump, config, attestation provider (inert) | nubecita | stack PR 1 |
| App: mediated transport, classification, fallback, key id, telemetry | nubecita | stack PR 2 |
| App: device-verification message, debug emulator access, backend URL on, runbook | nubecita | stack PR 3 |
| Rehearsal | — | evidence in the runbook, committed in stack PR 3 |
| Privacy policy line | nubecita-web | own PR, live before the stack merges |
| Replay-safe refresh | nubecita-web | own PR; last, and may land after the stack |

The nubecita stack is three PRs. At least one is titled `feat` so the epic cuts a release that reaches Play.

## Risks / Trade-offs

- **The server behaves differently from the source reading** → Nothing in production changes before the rehearsal. The rehearsal client exists to absorb surprises.
- **The flip cannot be undone** → It is outside this epic, gated, and rehearsed including the revert.
- **Metadata cache skew at the flip can delete a session that refreshes twice inside the window** → The flip guard (Decision 9) refuses the second refresh. The rehearsal measures the real behaviour first.
- **A server that is not the reference implementation may not upgrade public sessions** → Those users sign in once after the flip. Their share is measured beforehand.
- **A response lost on the mediated path followed by a direct retry would replay the token** → The persisted in-doubt flag (Decision 4) keeps an in-doubt token off the direct path.
- **A session that can never refresh again is never cleared automatically** → Deliberate. Manual sign-out works; a reconnect prompt is added to the renewal issue.
- **Backend outage after the flip** → Apps cannot refresh until it returns; sessions survive up to three months of failed refreshes. Alerts on error rate; min instances and a second region are available if it proves necessary.
- **A backend bug produces a real `invalid_grant`** (for example a reused `jti`, or signing with the wrong key) → `jti` is a fresh random value per request with a test; the backend refuses instead of substituting keys; an alert watches the relayed `invalid_grant` rate; bypass mode exists while the client is public.
- **Cold starts stall the first refresh after idle** → Measured in task 3.6; one warm instance costs a few dollars a month.
- **Devices that cannot pass Play Integrity cannot sign in after the flip** → Accepted by Francisco. Measured in observe-only mode first; refresh is never blocked.
- **Play Integrity quota or App Check outage** → Cannot block refresh. Could block new sign-ins; App Check token lifetime is reviewed against daily active users before enforcement is turned on.
- **Tokens now transit a server we run** → They are DPoP-bound to a key the server never sees; nothing is logged; the replay store is unreadable without the spent token; the privacy policy says so.
- **All mediated requests reach authorization servers from Google's addresses** → The default upstream limits key confidential clients by client id (3000 PARs per 5 minutes) and public clients by client id plus address (50 per 5 minutes), which is ample for sign-in volume. Individual servers may configure differently.
- **Public repository holds the backend source** → No secret exists to leak; deploys need federation plus an approval; secret scanning and push protection are on.

## Migration Plan

1. Land the SDK seam and release it.
2. Stand up the backend with the test client; develop the app changes against it in the emulator and then the deployed function.
3. Run the rehearsal on the rehearsal client. Record results.
4. Publish the privacy policy line.
5. Merge the nubecita stack. Production builds now mediate while the client is public; the direct fallback and bypass mode are the safety net. Rollback at this stage is turning bypass mode on.
6. Land replay-safe refresh on the backend.
7. (Follow-up issue) When the gates hold, ship the priority-5 release, then merge the production metadata change. There is no rollback after this step.
8. (Follow-up issue) Remove the direct fallback and legacy custom-scheme handling.

## Open Questions

- Which region the function runs in. Decide by measuring round-trip time to `bsky.social` from two US regions during task 3.6.
- Whether to pay for one warm instance. Decide from cold-start measurements once real traffic flows.
- What App Check token lifetime to set. Decide from daily active users against the Play Integrity quota before enforcement is turned on.
- Whether the fallback page should stop forwarding codes to the custom scheme after the flip. Decide in the post-flip cleanup issue.
