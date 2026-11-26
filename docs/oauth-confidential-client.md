# Confidential OAuth client: operations runbook

Nubecita signs in to Bluesky as an AT Protocol OAuth client. A *public* client's session ends two weeks after sign-in. A *confidential* client's lasts up to two years, and to be one the app sends four requests through a small backend that holds the client's signing key.

This page is how to run that backend, test against it, rotate its keys, and flip the production client. Design and rationale: `openspec/changes/add-confidential-oauth-client/`.

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

Three settings can be changed on the running service without a deploy. The next deploy resets them.

```bash
gcloud run services update oauthmediator --region us-central1 --project nubecita-2a4c1 \
  --update-env-vars MEDIATOR_BYPASS=true
```

| Variable | Effect | When |
|---|---|---|
| `MEDIATOR_BYPASS` | Every request is answered "not forwarded, use the direct path"; no authorization server is contacted. | The backend is misbehaving **and the production client is still public**. After the flip there is no direct path, so this only turns errors into different errors. |
| `MEDIATOR_ENFORCE_SIGN_IN_ATTESTATION` | Sign-ins without a valid App Check token are refused. Off = recorded only. | Turn on at the flip, after reviewing the pass rate. |
| `MEDIATOR_STRICT_REFRESH_ATTESTATION` | Refreshes and sign-outs without one are refused too. | Leave off. An attestation outage would otherwise stall every signed-in user. |

### Alerts

| Alert | Means | First move |
|---|---|---|
| OAuth backend: errors | The backend is failing requests itself. Apps keep their sessions but cannot refresh through it. | Read the function's logs. While the client is public, turn on bypass. |
| OAuth backend: relayed invalid_grant spike | Authorization servers are ending sessions at an unusual rate. | Look for a reused `jti`, a wrong signing key, or a manifest change. Compare with `session_cleared` in GA4. |
| OAuth backend: sign-in attestation failures | Many sign-ins lack a valid App Check token. | Check Play Integrity quota and the App Check console. Do not turn enforcement on until this is understood. |

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

These must be registered as GA4 custom dimensions before they can be queried (the analytics MCP is read-only; use the local Admin API script).

Reading them:

- `token_path = direct_fallback` in volume means the backend was unreachable or in bypass.
- `outcome = mediator_error` is the backend answering for itself. It never ends a session.
- `session_cleared` with `client_auth = confidential` and a low `days_since_login` is a loss a confidential session should not have had. Investigate each one.

## Observed on a device

Pixel 11 Pro, debug build, real account on `bsky.social`, backend at `https://auth.nubecita.app`. Each line was read from both the device's HTTP log and the backend's request log.

| Date | Client | What | Result |
|---|---|---|---|
| 2026-11-26 | test (confidential) | Sign-in | Sign-in start `400` (nonce) then `201`, code exchange `200`, all relayed from the server and signed with `test-1`. The browser returned to the app through the `https://nubecita.app/oauth-redirect/` App Link. Requests to the PDS went direct. |
| 2026-11-26 | test (confidential) | Forced refresh (`BadAccessToken`) | `400` (nonce) then `200`, relayed, `test-1`, `private_key_jwt`. Still signed in. About 0.35 s per request. |
| 2026-11-26 | test (confidential) | Sign-out | Revocation `400` (nonce) then `200`, relayed, `test-1`, `private_key_jwt`. |
| 2026-11-26 | rehearsal (public) | Sign-in, 14:44 PST | Sign-in start `201` and code exchange `200`, relayed, with the backend reporting `none`: it attached an assertion signed with `rehearsal-1` and the server ignored it. Consent page shown. **This is the rehearsal session**; day 15 is 2026-12-11. |
| 2026-11-26 | rehearsal (public) | Forced refresh | `400` (nonce) then `200`, relayed, `none`. One refresh. The app sent no key id with it: a public session stores none. |

Two things that run were not designed to show, and did:

- **No consent screen.** The server remembers which clients an account has approved by client id and scope, not by the manifest's contents. The test client's id had been approved in May under an earlier manifest at the same URL, so the browser leg completed in 0.65 s with no prompt. By the same rule, changing the production manifest should not make existing users approve the app again.
- **Two refreshes on one cold start, 0.4 s apart.** A request that left with the old access token while the refresh was in flight was rejected after it finished, and the SDK refreshed again (`nubecita-kjmk`). Both succeeded. It matters at the flip: see the guard in the checklist below.

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
| 1 | A session created while the client is public refreshes through the backend after the flip, without re-auth | Not yet run |
| 2 | A build with no backend URL cannot refresh after the flip, is not signed out, and resumes once rebuilt with the URL | Not yet run |
| 3 | The server accepts requests carrying an assertion while the client is public | **Observed** (2026-11-26, Pixel 11 Pro, `bsky.social`) for a sign-in start, a code exchange and a refresh: each succeeded with the backend reporting `none`. |
| 4 | A `web` client completes sign-in through the App Link redirect | **Observed** (2026-11-26, Pixel 11 Pro, test client): full sign-in, code returned through the App Link, exchanged through the backend. The redirect was automatic (see above), so the consent-page tap was not exercised for a `web` client. |
| 5 | Two refreshes in quick succession right after the flip (sizes the flip guard) | Not yet run |
| 6 | On a disposable session: reverting to public deletes a bound session | Belongs to the flip issue (`nubecita-xg9g`) |
| 7 | The session from outcome 1 is still valid on day 15 | Belongs to the flip issue |

A surprise in any of 1 to 4 stops the work and returns to design.

## Flipping production (issue `nubecita-xg9g`)

**Irreversible.** Once a session has refreshed as confidential, reverting the manifest deletes it on its next refresh. Do not start unless every line is true, and state the evidence for each in the pull request.

- [ ] Rehearsal outcomes 1 to 5 recorded above; outcome 6 observed on a disposable session
- [ ] The rehearsal session from outcome 1 is still valid more than 14 days after its sign-in
- [ ] At least 95% of 7-day active users are on a version that uses the backend (exclude `sdk_gphone*` devices and the `OnePlus8Pro` bot traffic)
- [ ] At least 14 days since that version reached full rollout
- [ ] Replay-safe refresh (`nubecita-dcvt.9`) is live
- [ ] The redundant second refresh (`nubecita-kjmk`) is fixed in the shipped SDK, or the decision to flip without the fix is written down. Unfixed, the minimum refresh interval below refuses the second refresh on affected cold starts and one request fails each time.
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
