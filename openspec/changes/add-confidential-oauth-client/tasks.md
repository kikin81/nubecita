# Tasks

Epic: `nubecita-dcvt`. Each numbered group is one bd child and one pull request; the repository and the bd id are in the heading. Groups 6–8 are the nubecita `gh stack`; the others are standalone PRs in their own repositories. This change's own docs PR is `nubecita-lsh0`.

## 1. SDK: token-endpoint transport seam (`kikin81/atproto-kotlin`) — `nubecita-dcvt.1` — kikin81/atproto-kotlin#238

- [x] 1.1 Add `TokenEndpointTransport`, `TokenEndpointRequest`, `TokenEndpointResponse` (with origin, key id, auth method, replayed flag) and a "request not sent" exception type to the `oauth` module; verify with a unit test that constructs each type and that the public API dump (`apiCheck`) is updated.
- [x] 1.2 Add the default direct transport that posts to the authorization server, using the single-use client for code exchange and refresh and the general client for PAR and revoke; verify with MockEngine tests that each request kind produces the same form, headers and URL as the current implementation.
- [x] 1.3 Route `AtOAuth` PAR, code exchange and revoke through the transport, including the DPoP nonce retry; verify the existing `AtOAuth` test suite passes unchanged and add a test that a fake transport sees exactly two PAR sends during a nonce challenge.
- [x] 1.4 Route `DpopAuthProvider` refresh through the transport and make any response whose origin is not the authorization server retryable; verify with new tests that a mediator-origin body containing `invalid_grant` does not clear the session, and that an authorization-server-origin `invalid_grant` still does.
- [x] 1.5 Add `issuer`, `clientAuthKid`, `clientAuthMethod` and `refreshInDoubt` to `OAuthSession` and `clientAuthKid` to `PendingAuth`, all with defaults; store a session's key id only from a response that reports the client as confidential, and never replace it; verify with tests that a session serialized by the previous release deserializes, that a key id reported alongside a public method is not stored, that one reported alongside a confidential method is, and that a different later key id is ignored.
- [x] 1.5a Persist `refreshInDoubt = true` before sending a refresh through a non-direct transport, pass it on the request, and clear it when a definitive answer arrives (in the same save as the rotated tokens on success); verify with tests that the flag is on disk before the transport is called, survives a simulated process restart, and is cleared by both a successful and a relayed-error answer but not by a transport failure.
- [x] 1.6 Send the stored key id and the issuer (derived from the token endpoint origin when null) on every request; verify with a test that a code exchange after a simulated process restart names the key id from the PAR.
- [x] 1.7 Document the transport in the module README and the `atproto-oauth` skill reference; verify the documented snippet compiles as a test source.
- [ ] 1.8 Publish to mavenLocal and confirm nubecita builds against it with `-PuseMavenLocal=true` (`./gradlew :app:assembleDebug`); then open the PR, and after merge cut the release.

## 2. Backend: scaffold, deploy pipeline, keys (`kikin81/nubecita-web`) — `nubecita-dcvt.2` — kikin81/nubecita-web#25

- [x] 2.1 Add `functions/` (Node 22, TypeScript, firebase-functions v2, vitest, eslint) with a single `onRequest` function that answers 404 on every path; verify `npm test` and `npm run lint` pass and the function starts in the Firebase emulator.
- [ ] 2.2 Add `infra/provision.sh`, an idempotent script that creates the KMS key ring and the test, rehearsal and production signing keys, the function's service account with only KMS signer on those keys, the Workload Identity pool bound to this repository's default branch, and the budget alert; verify by running it twice against the project and confirming the second run changes nothing. (Upgrading the project to the Blaze plan is a manual step for Francisco, done first.)
- [x] 2.3 Add a script that exports each key's public half as a JWK with its `kid`; verify the output for the test key validates as an ES256 public JWK in a unit test using a recorded fixture.
- [ ] 2.4 Extend CI to run functions lint, tests and build on pull requests; verify a PR with a failing test goes red.
- [ ] 2.4a Add Firebase Hosting with a single rewrite of every path to the function and the `auth.nubecita.app` custom domain (one DNS-only record in the Cloudflare zone, added by Francisco or with his approval); verify `curl https://auth.nubecita.app/anything` returns the function's 404 and that a custom request header reaches the function.
- [ ] 2.5 Add the deploy workflow: default branch only, federated credentials, a protected `production` environment requiring approval; verify a deploy of the 404 function succeeds from `main` and that the workflow cannot be dispatched from another branch.
- [ ] 2.6 Enable secret scanning and push protection on the repository and add `CODEOWNERS` for `functions/`, `infra/` and `oauth/`; verify with `gh api repos/kikin81/nubecita-web` that both settings read enabled.

## 3. Backend: mediation endpoints (`kikin81/nubecita-web`) — `nubecita-dcvt.3`

- [ ] 3.1 Implement the outbound guard (https only, DNS hostname, resolved-address checks, pinned connection, no redirects, size and time caps) and issuer metadata resolution with a 10-minute cache; verify with a table-driven unit test covering loopback, private, link-local, metadata-address, IP-literal, http, redirecting and issuer-mismatch cases.
- [ ] 3.2 Implement assertion minting behind a `Signer` interface with a KMS implementation (DER to JOSE signature conversion) and an in-memory test implementation; verify with unit tests that claims and header match the spec, that two assertions never share a `jti`, and that a known DER signature converts to the expected JOSE bytes.
- [ ] 3.3 Implement client and key selection from configuration (unknown client refused; named key used exactly; unknown named key refused without forwarding; default key otherwise); verify each branch with a unit test.
- [ ] 3.4 Implement `/par`, `/token` and `/revoke`: forward the form with the assertion and the untouched DPoP proof, relay status, body, nonce and upstream date, and mark origin, key id and the auth method read from the client's published metadata (5-minute cache); verify with integration tests against a local fake authorization server, including a nonce challenge followed by a retry.
- [ ] 3.5 Implement backend-produced errors with the `mediator_` prefix and the `forwarded` flag, the 25 s upstream limit inside a 30 s function timeout, and bypass mode; verify with tests that no backend-produced body contains `invalid_grant`, that a stalled upstream yields `upstream_timeout` with `forwarded: "unknown"`, and that bypass mode makes no outbound request.
- [ ] 3.6 Implement App Check verification with the enforcement and strict-refresh settings (observe-only by default) and the per-address limit on `/par`; verify the gating matrix (request kind × token state × setting) with unit tests. Deploy, then measure and record cold-start and warm latency for a refresh against `bsky.social` from two US regions.
- [ ] 3.7 Implement structured logging with the allowed fields only; verify with a test that runs a full PAR, exchange, refresh and revoke cycle and asserts the captured log output contains none of the tokens, the code, the proof, the assertion or the DID used in the test.
- [ ] 3.8 Add log-based metrics and alert policies (backend 5xx rate, relayed `invalid_grant` rate, attestation failure rate) to `infra/provision.sh`; verify the policies exist with `gcloud alpha monitoring policies list`.

## 4. Test and rehearsal client metadata (`kikin81/nubecita-web`) — `nubecita-dcvt.4`

- [ ] 4.1 Add `oauth/client-metadata-test.json` (`web`, `private_key_jwt`, `ES256`, embedded test public key, https redirects only) and `oauth/client-metadata-rehearsal.json` (starting as `native` + `none`); verify both are served with the expected content after merge.
- [ ] 4.2 Add a CI check that validates every file under `oauth/` against the authorization server's client-metadata rules (method/type pairing, redirect schemes, key presence, `client_id` equals the file's URL); verify with a test fixture that a `native` + `private_key_jwt` file fails.
- [ ] 4.3 Register the test and rehearsal clients and their key ids in the function configuration; verify a PAR for the test client through the deployed function reaches `bsky.social` and returns a `request_uri`.

## 5. Privacy policy (`kikin81/nubecita-web`) — `nubecita-dcvt.5`

- [ ] 5.1 Add a paragraph to `privacy-policy/index.html` stating that sign-in tokens and IP addresses pass through a Nubecita-operated server, what is and is not stored, and for how long; verify the page renders and the text is live before the nubecita stack merges. (Updating the Play Data safety form is a manual step for Francisco.)

## 6. App stack PR 1: SDK bump, configuration, attestation provider — inert (`kikin81/nubecita`) — `nubecita-dcvt.6`

- [ ] 6.1 Bump the atproto SDK version; verify `./gradlew :app:assembleDebug` and `:core:auth:testProductionDebugUnitTest` pass with no behaviour change.
- [ ] 6.2 Add `OAUTH_MEDIATOR_URL` as a build-config field with a `-PoauthMediatorUrl` override and an empty default, and a Hilt qualifier that provides it to `:core:auth`; verify with a unit test that an empty URL yields the direct transport, and compile the bench flavor (`:app:assembleBenchDebug`).
- [ ] 6.3 Add `AttestationTokenProvider` to `:core:auth` with a no-op implementation for the bench flavor, and the Firebase App Check implementation in `:app`; verify with a unit test that a provider failure returns no token rather than throwing.

## 7. App stack PR 2: mediated transport (`kikin81/nubecita`) — `nubecita-dcvt.7`

- [ ] 7.1 Implement the Nubecita transport in `:core:auth` (request headers, response header parsing, origin marking, upstream date) on the single-use HTTP client with a 40 s request timeout for mediated requests; verify with MockEngine tests for each request kind and for a response missing the origin header (treated as mediator-origin).
- [ ] 7.2 Implement the direct fallback rules (not-sent failure or `bypass` → direct, but never for a refresh token that is in doubt; anything else → retryable, no direct retry); verify with unit tests for connection failure, bypass, timeout after send, other backend errors, a rejected direct retry, and both fallback triggers with an in-doubt token.
- [ ] 7.3 Send the attestation token, when one can be obtained, on every mediated request; never block a request locally for want of one; verify with tests that a refresh and a sign-in are both sent when the provider yields no token, and that a sign-in the backend refuses for attestation surfaces a distinct error type.
- [ ] 7.4 Wire the transport into the `AtOAuth` binding and the refresh path; verify the existing `:core:auth` unit tests pass and add a test that a backend-produced 500 during refresh leaves the stored session intact.
- [ ] 7.5 Add path (`mediated` / `direct` / `direct_fallback`), auth method, replayed and issuer-category (`bsky.social` / other) dimensions to token-refresh, sign-in and session-cleared telemetry and to the Crashlytics breadcrumbs; verify with unit tests on the telemetry classes that each dimension is recorded and that no token or DID is.
- [ ] 7.6 Expose the session's lifetime regime from `:core:auth` for the renewal issue; verify with a unit test that a stored `private_key_jwt` method reads as the confidential regime and null reads as public.

## 8. App stack PR 3: user-facing message, emulator access, switch on, runbook (`kikin81/nubecita`) — `nubecita-dcvt.8`

- [ ] 8.1 Add the device-verification failure message to the sign-in screen with es-419 and pt-BR translations in the same commit; verify with a `LoginViewModel` unit test for the new error mapping and a screenshot preview of the error state (baselines via the `update-baselines` label), and run `:feature:login:impl` lint for `MissingTranslation`.
- [ ] 8.2 Add a debug-only network security config permitting cleartext to `localhost` and `10.0.2.2`; verify the release manifest merge report shows no cleartext exception.
- [ ] 8.3 Run the rehearsal on a real device with a real account, using the rehearsal client and the deployed function, covering outcomes 1–5 in design.md Decision 13; start the rehearsal session as soon as a mediated debug build exists, since outcome 7 needs it to reach day 15; record what was observed for each in `docs/oauth-confidential-client.md`. Stop and return to design if any of outcomes 1–4 differs from expectation. (Outcomes 6 and 7 belong to the flip follow-up issue.)
- [ ] 8.4 Write the rest of `docs/oauth-confidential-client.md`: bypass mode, routine and emergency key rotation, alert meanings, the flip checklist and its gates, local emulator setup; verify by following the bypass and emulator sections as written.
- [ ] 8.5 Set the production default for `OAUTH_MEDIATOR_URL` to the deployed function; verify on a real device with the production client that sign-in, a forced refresh and sign-out succeed through the backend, and that turning bypass mode on makes the next refresh succeed directly.
- [ ] 8.6 Top-of-stack gate: `./gradlew :app:assembleDebug`, lint for every touched module, `jacocoTestReportAggregated`, the bench smoke on a real device, and the compose-expert review against the cumulative stack diff.

## 9. Backend: replay-safe refresh (`kikin81/nubecita-web`) — `nubecita-dcvt.9`

- [ ] 9.1 Enable Firestore, add deny-all security rules and a TTL policy on the replay collection, and grant the function's service account access to that collection only (in `infra/provision.sh`); verify a client SDK read is denied.
- [ ] 9.2 Implement the store: id derivation, token-derived AES-GCM encryption, pending/done states, supersession hash, expiry on read; verify with unit tests against the Firestore emulator, including that a raw document contains no substring of the tokens used.
- [ ] 9.3 Implement single flight around refresh forwarding (create pending in a transaction; `in_progress` for a duplicate younger than 60 s; pending left in place on upstream timeout; takeover only of an entry older than 60 s; delete pending on any non-200 upstream answer); verify with an integration test that two concurrent identical refreshes cause exactly one upstream request, and that a nonce challenge does not block the retry.
- [ ] 9.4 Implement serving a stored response after DPoP proof verification, and deletion on supersession; verify with integration tests for the lost-response retry, a wrong-key proof, a stale proof, a superseded entry and an expired entry.
- [ ] 9.5 Make a failed store write non-fatal and log whether a response was served from the store; verify with a test that injects a write failure and still receives the relayed response.
- [ ] 9.5a Serve stored responses in bypass mode, and add the operator-set minimum refresh interval (off by default) using the supersession record; verify with tests that bypass mode returns a stored response and otherwise the bypass reply, and that with the interval set a refresh of a just-issued token is refused without an upstream request.
- [ ] 9.6 Verify end to end on a real device: drop the refresh response on the device side (proxy or airplane-mode timing), retry, and confirm the session survives and telemetry records a replayed response.

## Workflow follow-up

- After the stack merges with `gh stack merge --squash --yes`: count the `Release` runs, `bd close` every child and the epic, and archive this change.
- Follow-up standalone issues, already created and linked to this change: `nubecita-xg9g` flip production metadata (carries rehearsal outcomes 6 and 7, the gates, and the flip guard; blocked on the epic and on `nubecita-dcvt.9`); `nubecita-hhw9` post-flip cleanup (remove the direct fallback and legacy custom-scheme handling); `nubecita-0tjs` renewal prompt (with the added trigger: a persistent relayed client-authentication failure).
