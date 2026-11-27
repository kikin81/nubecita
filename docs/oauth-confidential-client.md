# Confidential OAuth client: operations runbook

Nubecita signs in to Bluesky as an AT Protocol OAuth client. A *public* client's session ends two weeks after sign-in. A *confidential* client's lasts up to two years, and to be one the app sends four requests through a small backend that holds the client's signing key.

This page is how to run that backend, test against it, rotate its keys, and flip the production client. Design and rationale: `openspec/changes/archive/2026-11-26-add-confidential-oauth-client/`.

## What exists

| Thing | Where |
|---|---|
| Backend source, provisioning, client manifests | `kikin81/nubecita-web` (`functions/`, `infra/`, `oauth/`) |
| Backend URL the app calls | `https://auth.nubecita.app` (Firebase Hosting rewrite to the function) |
| Function | `oauthMediator`, Cloud Functions 2nd gen, `us-central1`, project `nubecita-2a4c1` |
| Signing keys | Cloud KMS key ring `oauth-client-auth` (location `us`): `nubecita-prod`, `nubecita-test`, `nubecita-rehearsal`. Non-exportable. |
| Client manifests | `https://nubecita.app/oauth/client-metadata.json` (production), `-test.json`, `-rehearsal.json` |
| App-side transport | `:core:auth` `MediatedTokenEndpointTransport`; SDK seam in `atproto-kotlin` (`TokenEndpointTransport`) |
| Alerts | Cloud Monitoring, to `alerts@nubecita.app` (Cloudflare Email Routing) |

The backend mediates exactly four requests: pushed authorization request (`/par`), code exchange and refresh (`/token`), revocation (`/revoke`). Everything else the app does goes straight to the PDS.

### The two rules everything rests on

1. **Only the authorization server can end a session.** Every backend response carries `Nubecita-Mediator-Origin`: `upstream` when it is the server's own answer relayed unchanged, `mediator` when the backend produced it. The app clears a session only on an `invalid_grant` marked `upstream`. A backend error, an outage, an unmarked response: all retryable.
2. **A refresh token that may be spent never takes the direct path.** The app marks a refresh as "in doubt" on disk before sending it. If the answer is lost, later attempts go only to the backend.

## Local development

```bash
# In kikin81/nubecita-web
cd functions && npm ci && npm run serve     # emulator on http://127.0.0.1:5001

# One-time: local Google credentials, so the emulator can sign with the TEST key
gcloud auth application-default login --project nubecita-2a4c1
```

Send a real sign-in start through the emulator (spends nothing, creates no session):

```bash
node infra/smoke-par.mjs http://127.0.0.1:5001/nubecita-2a4c1/us-central1/oauthMediator \
  https://nubecita.app/oauth/client-metadata-test.json
```

Expect a relayed `400 use_dpop_nonce`, then a relayed `201` with a `request_uri`.

Point a debug build of the app at it:

```bash
adb -s <serial> reverse tcp:5001 tcp:5001
./gradlew :app:assembleProductionDebug \
  -PoauthClientMetadataUrl=https://nubecita.app/oauth/client-metadata-test.json \
  -PoauthMediatorUrl=http://localhost:5001/nubecita-2a4c1/us-central1/oauthMediator
adb -s <serial> install -r app/build/outputs/apk/production/debug/app-production-debug.apk
```

Install with `adb -s <serial>`, never a Gradle `install…` task: those install on every attached device, including the emulators that hold real sessions.

- Debug builds allow plain http to `localhost`, `127.0.0.1` and `10.0.2.2` only. Release builds allow none.
- Use a real device. The `Pixel_10_Pro` and `Pixel_10_Pro_Fold` emulators hold real sessions: never install a test build on them or clear their data.
- Never test against the production client id. The test client is confidential; the rehearsal client exists only for the flip rehearsal below.
- `production` builds use `https://auth.nubecita.app` unless told otherwise. `-PoauthMediatorUrl=<url>` points one build somewhere else; passing it empty (`-PoauthMediatorUrl=`) gives a build that talks to the authorization server directly and requests no attestation token. The `bench` flavor never uses the backend: it is offline.
- A debug build has no Play Integrity verdict, so the backend records its attestation as `missing`. That is harmless while sign-in enforcement is off. Once it is on, register the build's App Check debug token in the Firebase console or debug builds cannot sign in.
- To force a refresh on a device, build with `-PauthTestHarness=true` (only possible on a machine with no release keystore) and use the `auth-harness` skill's `BadAccessToken` scenario.

## Operating the backend

### Switches

Four settings can be changed on the running service without a deploy. The next deploy resets them.

```bash
gcloud run services update oauthmediator --region us-central1 --project nubecita-2a4c1 \
  --update-env-vars MEDIATOR_BYPASS=true
```

| Variable | Effect | When |
|---|---|---|
| `MEDIATOR_BYPASS` | Every request is answered "not forwarded, use the direct path"; no authorization server is contacted. | The backend is misbehaving **and the production client is still public**. After the flip there is no direct path, so this only turns errors into different errors. |
| `MEDIATOR_ENFORCE_SIGN_IN_ATTESTATION` | Sign-ins without a valid App Check token are refused. Off = recorded only. | Turn on at the flip, after reviewing the pass rate. |
| `MEDIATOR_STRICT_REFRESH_ATTESTATION` | Refreshes and sign-outs without one are refused too. | Leave off. An attestation outage would otherwise stall every signed-in user. |
| `MEDIATOR_MIN_REFRESH_INTERVAL_MINUTES` | A refresh of a token the backend handed out less than this many minutes ago is refused without contacting the server. Off when unset or `0`. A value that is not a number from 0 to 1440 fails every request until corrected, on purpose. | Only for the hour around the production flip. |

### Alerts

| Alert | Means | First move |
|---|---|---|
| OAuth backend: errors | The backend is failing requests itself. Apps keep their sessions but cannot refresh through it. | Read the function's logs. While the client is public, turn on bypass. |
| OAuth backend: relayed invalid_grant spike | Authorization servers are ending sessions at an unusual rate. | Look for a reused `jti`, a wrong signing key, or a manifest change. Compare with `session_cleared` in GA4. |
| OAuth backend: sign-in attestation failures | Many sign-ins lack a valid App Check token. | Check Play Integrity quota and the App Check console. Do not turn enforcement on until this is understood. |
| OAuth backend: replay store failures | The backend cannot read or write its refresh store (Firestore database `oauth-replay`). Unreadable: refreshes are not forwarded and apps are told to use the direct path, which stops working once the client is confidential. Unwritable: refreshes go through, but a lost response would end that session. | Check the database exists and the backend's service account still has its one conditional grant on it. |

### Replay-safe refresh

A refresh token can be used once. If the response to a refresh is lost on its way to the phone, the phone can only retry with the token it still has, and the server deletes the session when it sees a spent token again. So the backend keeps each successful refresh response for 24 hours, encrypted under a key derived from the refresh token it answered, and gives it again to a caller that proves it holds the same DPoP key. Such an answer carries `Nubecita-Replayed: 1` and is logged with `replayed: true`.

The same store makes sure a refresh token is forwarded by one request at a time: a duplicate inside 60 seconds is told to wait (`mediator_in_progress`).

The store is a Firestore database of its own, closed to every client by its rules. Nothing in it is a token or can be turned into one.

### Logs

One structured line per request, and nothing free-form: `kind`, `issuerHost`, `status`, `origin`, `reason`, `forwarded`, `upstreamError`, `latencyMs`, `kid`, `attestation`, `replayed`. No token, code, DPoP proof, assertion, DID, handle or caller address is ever logged.

`forwarded` tells you what a failure cost: `false` means the request never reached the server; `"unknown"` means it was sent and no answer came back, so a refresh token it carried may be spent.

### Deploying

Merging to `main` in `nubecita-web` (when `functions/`, `hosting/` or `firebase.json` changed) starts **Deploy backend**, which waits for approval in the `production` environment. It authenticates through Workload Identity Federation, restricted to that repository's `main`; no service-account key exists.

`infra/provision.sh` is idempotent and describes the whole setup. Two things it does that are easy to forget:

- It removes the Editor role from the project's two default service accounts. `firebase-tools` and 2nd-gen builds require the deploy identity to act as them; with Editor removed, acting as them grants almost nothing. The App Engine default account holds no roles; the Compute default account holds only the Cloud Build builder role. The undo commands are in the script.
- For some minutes after an API is enabled, calls to it fail at random. The script retries those; a first run is slower than later ones.

## Signing keys

Each client has one KMS key. A key *version* is published in the client's manifest under a `kid` (`prod-1`, `test-1`, ...), and `functions/src/config.ts` maps each `kid` to its key version.

**A session is bound for life to the key that signed its first confidential refresh.** The app stores that `kid` and sends it on every request. The backend signs with exactly that key or refuses; it never substitutes another, because the server deletes a session refreshed with a different key.

### Routine rotation (optional)

1. Create a new key version in KMS.
2. Add its public JWK to the manifest (`infra/export-jwk.sh <key> <version> <kid>`), **keeping the old one**.
3. Add the new `kid` to `config.ts` and make it `defaultKid`. Keep the old entry.
4. Deploy. New sign-ins use the new key; existing sessions keep theirs.
5. Remove the old key from the manifest and the config **two years** after it stopped being the default.

Do not change the default key within 24 hours of a manifest flip.

### Emergency rotation (a key is compromised)

Removing a `kid` from the manifest makes every session bound to it unusable within about ten minutes. There is no way to migrate them: those users must sign in again. That is the intended revocation lever, and the reason the keys cannot be exported.

A session in that state is deliberately **not** cleared by the app (its refresh fails with a client-authentication error, not `invalid_grant`). The user can sign out and in again from Settings; the renewal prompt (`nubecita-0tjs`) will offer it.

## Telemetry

| Event | New fields |
|---|---|
| `token_refresh` | `token_path` (`mediated`, `direct`, `direct_fallback`), `client_auth` (`public`, `confidential`, `unknown`), `replayed`, `issuer_kind` (`bsky_social`, `other`), outcome `mediator_error` |
| `login` | `client_auth` (`unknown` = went direct) |
| `login_error` | reason `device_verification` |
| `session_cleared` | `token_path`, `client_auth` of the refresh that cleared it |

`token_path`, `client_auth`, `replayed` and `issuer_kind` were registered as GA4 custom dimensions on 2026-11-26. Registration is not retroactive: events sent before that date cannot be broken down by them. A new field needs registering the same way (the analytics MCP is read-only; use the local Admin API script).

Reading them:

- `token_path = direct_fallback` in volume means the backend was unreachable or in bypass.
- `outcome = mediator_error` is the backend answering for itself. It never ends a session.
- `session_cleared` with `client_auth = confidential` and a low `days_since_login` is a loss a confidential session should not have had. Investigate each one.

## Observed on a device

Pixel 11 Pro unless a row says otherwise, debug build, real account on `bsky.social`, backend at `https://auth.nubecita.app`. Each line was read from both the device's HTTP log and the backend's request log.

| Date | Client | What | Result |
|---|---|---|---|
| 2026-11-26 | test (confidential) | Sign-in | Sign-in start `400` (nonce) then `201`, code exchange `200`, all relayed from the server and signed with `test-1`. The browser returned to the app through the `https://nubecita.app/oauth-redirect/` App Link. Requests to the PDS went direct. |
| 2026-11-26 | test (confidential) | Forced refresh (`BadAccessToken`) | `400` (nonce) then `200`, relayed, `test-1`, `private_key_jwt`. Still signed in. About 0.35 s per request. |
| 2026-11-26 | test (confidential) | Sign-out | Revocation `400` (nonce) then `200`, relayed, `test-1`, `private_key_jwt`. |
| 2026-11-26 | rehearsal (public) | Sign-in, 14:44 PST | Sign-in start `201` and code exchange `200`, relayed, with the backend reporting `none`: it attached an assertion signed with `rehearsal-1` and the server ignored it. Consent page shown. **This is the rehearsal session**; day 15 is 2026-12-11. |
| 2026-11-26 | rehearsal (public) | Forced refresh | `400` (nonce) then `200`, relayed, `none`. One refresh. The app sent no key id with it: a public session stores none. |
| 2026-11-26 | production (public) | Sign-in and forced refresh, Galaxy Z Fold 8 | Sign-in start `201`, code exchange `200`, refresh `400` (nonce) then `200`: all relayed, `prod-1` attached, server reports `none`. This is what every user gets from the release that turns the backend on. |
| 2026-11-26 | production (public) | Refresh with the backend in bypass mode, Galaxy Z Fold 8 | Run against a local copy of the backend with `MEDIATOR_BYPASS=true` (the debug build pointed at it over `adb reverse`): the backend answered `503 mediator_bypass`, the app refreshed directly at `bsky.social` (`200`) and stayed signed in. The next refresh, back on the deployed backend, was relayed normally. Flipping the switch on the deployed service itself has not been exercised. |
| 2026-11-26 | production (public) | Sign-out, then sign-in again, Galaxy Z Fold 8 | Revocation `400` (nonce) then `200`, relayed, `none`. The sign-in that followed went through the backend as before. |
| 2026-11-26 | rehearsal (flipped 15:00 PST) | First refresh after the flip, 15:47 | The app refreshed on its own, 47 minutes after the manifest changed: `400` (nonce) then `200`, relayed. From then on the app sends `rehearsal-1` with every refresh, which it only does once the server has reported the client as confidential. No re-login. |
| 2026-11-26 | rehearsal (confidential) | Two forced refreshes, 16:17 and 16:18 | Both `200`, relayed, `private_key_jwt`, `rehearsal-1`. |
| 2026-11-26 | rehearsal (confidential) | Build with no backend URL, forced refresh | 18 direct refresh attempts at `bsky.social` in three seconds, every one answered `400`. The app stayed signed in and showed an empty timeline. |
| 2026-11-26 | rehearsal (confidential) | Build with the backend URL reinstalled | First refresh `200`, relayed, `private_key_jwt`; the timeline loaded. The session survived the 18 unauthenticated attempts. |
| 2026-11-26 | rehearsal (confidential) | SDK 10.5.0 build, four forced refreshes | One token request per cold start, each `200`, through the backend revision that has the replay store. |
| 2026-11-26 | production (public) | A refresh whose response is lost, Galaxy Z Fold 8 | A proxy between the app and the backend threw away the first successful refresh response. The app saw the connection drop, retried with the same (now spent) refresh token 12 ms later, and the backend answered from its store in 48 ms: `200`, `Nubecita-Replayed: 1`, nothing forwarded. The app stayed signed in, and the next refresh, with the token from the replayed answer, was a normal `200`. |

Two things that run were not designed to show, and did:

- **No consent screen.** The server remembers which clients an account has approved by client id and scope, not by the manifest's contents. The test client's id had been approved in May under an earlier manifest at the same URL, so the browser leg completed in 0.65 s with no prompt. By the same rule, changing the production manifest should not make existing users approve the app again.
- **Two refreshes on one cold start, 0.4 s apart.** A request that left with the old access token while the refresh was in flight was rejected after it finished, and the SDK refreshed again (`nubecita-kjmk`). Both succeeded. It matters at the flip: see the guard in the checklist below.
- **A build that cannot authenticate keeps trying.** With no backend URL and a confidential client, every rejected request triggered its own refresh attempt: 18 in three seconds. None can succeed and none harms the session, but after the production flip every out-of-date build does this against the user's authorization server until it is updated. That is what the forced-update gate in the checklist is for (`nubecita-uprl`).

## Rehearsal

Before production is touched, the change of authentication method is rehearsed on the rehearsal client with a real account on a real device.

How to run it:

1. Install a debug build for the rehearsal client (`-PoauthClientMetadataUrl=https://nubecita.app/oauth/client-metadata-rehearsal.json`, harness on) and sign in. The client is public at this point.
2. Force a refresh. It goes through the backend with an assertion attached, which the server must ignore (outcome 3).
3. Merge the rehearsal manifest change in `nubecita-web` (confidential, key `rehearsal-1`). Wait 20 minutes: servers cache a manifest for about ten minutes per process.
4. Force a refresh (outcome 1), and read whether a second one follows it (outcome 5).
5. Install the same build with `-PoauthMediatorUrl=` and force a refresh: it must fail without signing out (outcome 2). Reinstall the mediated build and confirm it resumes.
6. Keep that session alive on some device until day 15 (outcome 7).

| # | Outcome | Status |
|---|---|---|
| 1 | A session created while the client is public refreshes through the backend after the flip, without re-auth | **Observed** (2026-11-26, Pixel 11 Pro): the session from 14:44 refreshed at 15:47, 16:17, 16:18 and 16:19 as `private_key_jwt` with no re-login. |
| 2 | A build with no backend URL cannot refresh after the flip, is not signed out, and resumes once rebuilt with the URL | **Observed** (2026-11-26): every direct attempt answered `400`, the app stayed signed in, and the same session refreshed again as soon as the build with the URL was back. |
| 3 | The server accepts requests carrying an assertion while the client is public | **Observed** (2026-11-26, Pixel 11 Pro, `bsky.social`) for a sign-in start, a code exchange and a refresh: each succeeded with the backend reporting `none`. |
| 4 | A `web` client completes sign-in through the App Link redirect | **Observed** (2026-11-26, Pixel 11 Pro, test client): full sign-in, code returned through the App Link, exchanged through the backend. The redirect was automatic (see above), so the consent-page tap was not exercised for a `web` client. |
| 5 | Two refreshes in quick succession right after the flip (sizes the flip guard) | **Not observed where it matters.** Nothing refreshed in the first 47 minutes after the flip, so the window in which servers can disagree about the client was never exercised. Refreshes 36 seconds apart an hour later were both fine. Needs a second rehearsal client and forced refreshes through the first 20 minutes; do it before the production flip. |
| 6 | On a disposable session: reverting to public deletes a bound session | Belongs to the flip issue (`nubecita-xg9g`) |
| 7 | The session from outcome 1 is still valid on day 15 | Belongs to the flip issue |

A surprise in any of 1 to 4 stops the work and returns to design.

## Flipping production (issue `nubecita-xg9g`)

**Irreversible.** Once a session has refreshed as confidential, reverting the manifest deletes it on its next refresh. Do not start unless every line is true, and state the evidence for each in the pull request.

- [ ] Rehearsal outcomes 1 to 5 recorded above (5 still needs its run inside the first 20 minutes after a flip, on a second rehearsal client); outcome 6 observed on a disposable session
- [ ] The rehearsal session from outcome 1 is still valid more than 14 days after its sign-in
- [ ] At least 95% of 7-day active users are on a version that uses the backend (exclude `sdk_gphone*` devices and the `OnePlus8Pro` bot traffic)
- [ ] At least 14 days since that version reached full rollout
- [ ] Replay-safe refresh (`nubecita-dcvt.9`) is live (deployed 2026-11-26 and shown to recover a lost response on a device; confirm it is still enabled and its alert is quiet)
- [ ] The redundant second refresh (`nubecita-kjmk`) is fixed in the build users have: it shipped in 1.388.1 (SDK 10.5.0), so this is covered by the 95% line above once that line is measured against 1.388.1 or later.
- [ ] The sign-in App Check pass rate has been reviewed, and App Check token lifetime checked against the Play Integrity quota
- [ ] The share of sessions with `issuer_kind = other` has been reviewed (a non-reference server may make those users sign in once)
- [ ] A release at in-app-update priority 5 is live, so older builds see the update screen

On the day:

1. Set the backend's minimum refresh interval for the hour around the change (above the combined metadata cache window, about 20 minutes).
2. Merge the manifest change: `application_type` `web`, `token_endpoint_auth_method` `private_key_jwt`, `token_endpoint_auth_signing_alg` `ES256`, the production public key embedded, and `app.nubecita:/oauth-redirect` removed. The validator in `oauth/` must pass.
3. Turn on `MEDIATOR_ENFORCE_SIGN_IN_ATTESTATION`.
4. Watch `token_refresh` by `client_auth`, the relayed `invalid_grant` alert, and `session_cleared`.
5. Do not change the default signing key for 24 hours.

After the flip, `nubecita-hhw9` removes the direct fallback and the legacy custom-scheme handling.
