# Proposal

## Why

Nubecita signs people out 14 days after they sign in. On 2026-11-25 a production build was logged out exactly 14 days after sign-in: a background refresh got `invalid_grant` "Session expired" with `days_since_login=14`. That is not a bug. It is the session limit the AT Protocol authorization server gives every *public* OAuth client, and refreshing does not extend it.

*Confidential* clients — ones that authenticate with a signing key held on a server — get a 2-year session and a 3-month refresh window instead. Bluesky has said (atproto discussion #2656) that a mobile app paired with a token-mediating backend counts as a confidential `web` client. This change makes Nubecita that kind of client, so a session lasts up to two years.

## What Changes

- **New backend service: a token-mediating backend.** A small Firebase function that the app calls for the four OAuth requests that need client authentication (pushed authorization request, code exchange, refresh, revocation). It adds a signed client assertion and forwards the request to the user's authorization server, relaying the answer unchanged. The signing key lives in Cloud KMS and cannot be exported. All XRPC traffic keeps going straight from the app to the PDS.
- **The app sends those four requests through the backend** instead of directly to the authorization server. While production client metadata still says "public", the app falls back to the direct path when the backend provably did not forward a request.
- **A backend problem can never sign anyone out.** Only an `invalid_grant` that the authorization server itself issued may clear a session.
- **Replay-safe refresh.** The backend remembers each refresh response briefly, so a retry of a refresh whose response was lost gets the same answer instead of reaching the authorization server as a replay (which deletes the session). This is the failure tracked in epic `nubecita-09xt`, which nothing on the client could fix.
- **Sign-in requires a verified app and device** (Firebase App Check with Play Integrity) once the client is confidential. Refresh and sign-out are checked and logged but not blocked.
- **The SDK (`atproto-kotlin`) gains a pluggable token-endpoint transport** and four new optional session fields: the authorization server's issuer, the id of the key a session is bound to, the client's reported authentication method, and a flag marking a refresh whose outcome is unknown.
- **Published client metadata changes in a later, gated step**: `application_type` becomes `web`, `token_endpoint_auth_method` becomes `private_key_jwt`, the public key is embedded, and the `app.nubecita:/oauth-redirect` custom-scheme redirect is removed. **BREAKING** for app builds that predate this change: after the switch they can no longer refresh or sign in until they update. Their sessions are not deleted, and updating resumes them.
- **Test and rehearsal client metadata** are published alongside the production file, so nothing is ever tested against the production client.
- **Privacy policy** gains a line: sign-in tokens and IP addresses pass through a Nubecita server.

## Non-goals

- Routing XRPC, chat, or any other API traffic through the backend.
- Waiting for, or depending on, proposal 0010 (client-assertion backend). Its server change has been a draft since May 2025.
- The graceful renewal prompt ("Reconnect @handle" shortly before a session expires). That is a separate standalone issue; this change only exposes the fact it needs (whether a session is on the 14-day or the 2-year limit).
- Flipping production client metadata. This change builds and ships everything needed and rehearses the flip on a throwaway client. The flip itself is a separate, gated follow-up issue (see design.md, Migration Plan).
- Multi-account support, or any change to where sessions are stored on the device.
- Protecting the authorization-code exchange against lost responses. A lost code exchange costs one retry of the sign-in, not a session.

## Capabilities

### New Capabilities

- `oauth-token-mediation-backend`: the backend service — which requests it accepts, how it authenticates the client to the authorization server, what it relays, what it refuses, what it stores and logs, and how it behaves when something fails.
- `core-auth-token-mediation`: how the app routes OAuth client-authenticated requests through the backend, when it may fall back to the direct path, how it treats backend failures, and what it remembers per session.
- `oauth-client-registration`: the published OAuth client metadata files (production, test, rehearsal), the signing keys they advertise, and the rules for changing them.

### Modified Capabilities

- `core-auth-oauth-bindings`: the `AtOAuth` binding is additionally constructed from the token-mediation transport and its build-configurable backend URL.

## Impact

- **`kikin81/atproto-kotlin`** (`oauth` module): new public transport interface, default direct implementation, four new optional fields on `OAuthSession` and one on `PendingAuth`. Additive; a minor release.
- **`auth.nubecita.app`**: a new hostname (Firebase Hosting custom domain, one DNS record in the Cloudflare zone) that fronts the backend, so its location can change without an app release.
- **`kikin81/nubecita-web`**: new `functions/` source tree, deploy workflow using Workload Identity Federation, two new metadata files, privacy policy text, repository security settings (secret scanning, push protection, a protected deploy environment).
- **Google Cloud project `nubecita-2a4c1`**: Blaze plan, one Cloud Functions (2nd gen) service, a Cloud KMS key ring with one signing key per client, a Firestore database for the replay cache, a budget alert, and alert policies.
- **`:core:auth`**: new transport implementation, attestation-token provider interface, fallback and failure classification, telemetry dimensions. No change to session storage format beyond the SDK's new optional fields.
- **`:app`**: build-config fields for the backend URL, the Firebase App Check implementation of the attestation provider, a debug-only cleartext exception for the local emulator host.
- **`:feature:login:impl`**: one new error message (with es-419 and pt-BR translations) for a device that cannot be verified.
- **Baseline deviations**: none in MVI, Compose, Hilt, Room or Coil. The deviation is architectural: this is the first Nubecita-operated server on the sign-in path, and the first use of Firestore and Cloud KMS.
- **Operations**: a new runbook (`docs/oauth-confidential-client.md`) covering the kill switch, key rotation, the flip checklist and its gates.
