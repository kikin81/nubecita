# Spec Delta

## Purpose

Defines how the app sends OAuth requests that need client authentication through the token-mediating backend, when it may use the direct path instead, and how it keeps a backend problem from ever ending a session.

## ADDED Requirements

### Requirement: Client-authenticated OAuth requests go through the backend

When a backend URL is configured, the app SHALL send pushed authorization requests, authorization-code exchanges, refresh-token exchanges and revocations to the backend rather than to the authorization server. Each SHALL carry the authorization server's issuer and a DPoP proof made for the authorization server's own endpoint. All other requests, including every XRPC call, SHALL continue to go directly to their servers.

#### Scenario: Refresh is mediated

- **WHEN** an access token needs refreshing and a backend URL is configured
- **THEN** the refresh SHALL be sent to the backend, with a DPoP proof whose target is the authorization server's token endpoint

#### Scenario: XRPC is not mediated

- **WHEN** the app calls any XRPC method
- **THEN** the request SHALL go directly to the PDS and SHALL NOT pass through the backend

### Requirement: The app is inert without a backend URL

When no backend URL is configured, the app SHALL send all OAuth requests directly to the authorization server exactly as before this change, and SHALL make no request to the backend and none for an attestation token.

#### Scenario: Offline benchmark build

- **WHEN** the offline benchmark build runs
- **THEN** no request to the backend SHALL be attempted

#### Scenario: Build without a configured URL

- **WHEN** a build has an empty backend URL and the user signs in
- **THEN** sign-in SHALL complete over the direct path

### Requirement: Only the authorization server can end a session

The app SHALL clear a session only when a response that the backend marks as relayed from the authorization server (or a response received directly from the authorization server) carries the OAuth error `invalid_grant`. Every backend-produced response and every transport failure SHALL be treated as temporary and SHALL leave the session intact.

#### Scenario: Backend error keeps the session

- **WHEN** a refresh through the backend returns a backend-produced error of any status
- **THEN** the session SHALL remain stored and the failure SHALL be reported as retryable

#### Scenario: Backend outage keeps the session

- **WHEN** the backend is unreachable or times out during a refresh
- **THEN** the session SHALL remain stored and a later refresh SHALL be attempted with the same refresh token

#### Scenario: Relayed invalid_grant ends the session

- **WHEN** a refresh returns a response marked as relayed whose error is `invalid_grant`
- **THEN** the session SHALL be cleared and the user routed to sign-in

#### Scenario: An unmarked response is not trusted

- **WHEN** a response from the backend URL carries `invalid_grant` but is not marked as relayed
- **THEN** the session SHALL NOT be cleared

### Requirement: The direct path is used only when the request was provably not forwarded

The app SHALL retry a mediated request directly against the authorization server only when the request never reached the backend (a connection or name-resolution failure before sending) or the backend answered that it did not forward it and the app should bypass. After any failure where the request may have been forwarded, the app SHALL NOT retry on the direct path.

#### Scenario: Connection failure falls back

- **WHEN** the app cannot connect to the backend for a refresh whose refresh token is not in doubt
- **THEN** it SHALL retry the same refresh directly against the authorization server

#### Scenario: Bypass response falls back

- **WHEN** the backend answers a request with its bypass response
- **THEN** the app SHALL retry that request directly against the authorization server

#### Scenario: Timeout does not fall back

- **WHEN** a refresh sent to the backend times out after the request was sent
- **THEN** the app SHALL NOT retry it directly, and SHALL report a retryable failure

#### Scenario: A failed direct fallback is harmless

- **WHEN** the direct retry is rejected because the client is registered as confidential
- **THEN** the rejection SHALL be treated as temporary and the session SHALL remain stored

### Requirement: A refresh token with an unknown outcome never takes the direct path

Before sending a mediated refresh, the app SHALL durably record that the refresh token's outcome is in doubt, and SHALL clear that record only when a definitive answer arrives. While a refresh token is in doubt, including after the app process has been restarted, the app SHALL NOT send it on the direct path for any reason.

#### Scenario: Lost response then unreachable backend

- **WHEN** a mediated refresh was sent and no answer arrived, and on the next attempt the backend cannot be reached
- **THEN** the app SHALL NOT retry directly, and SHALL report a retryable failure with the session kept

#### Scenario: Lost response then bypass

- **WHEN** a mediated refresh was sent and no answer arrived, and on the next attempt the backend answers with its bypass response
- **THEN** the app SHALL NOT retry directly

#### Scenario: Process death mid-refresh

- **WHEN** the app process dies after sending a mediated refresh and before storing the answer
- **THEN** after restart the refresh token SHALL still be recorded as in doubt

#### Scenario: Definitive answer clears the doubt

- **WHEN** a mediated refresh receives a relayed answer, successful or not
- **THEN** the in-doubt record SHALL be cleared

### Requirement: Each session remembers the key it is bound to

The app SHALL store with a session the key id the backend reported, but only from a response that also reports the client as confidential. It SHALL store with each in-progress sign-in the key id reported for its pushed authorization request. It SHALL send the stored key id with every later mediated request for that session or sign-in. Once stored for a session, the key id SHALL NOT be replaced by a different one.

#### Scenario: Key id is stored on first confidential response

- **WHEN** a session with no stored key id completes a mediated refresh and the response reports the client as confidential with key id `K1`
- **THEN** `K1` SHALL be stored with the session

#### Scenario: Key id is not pinned while the client is public

- **WHEN** a mediated refresh response reports the client as public
- **THEN** no key id SHALL be stored with the session

#### Scenario: Stored key id is sent back

- **WHEN** a session with stored key id `K1` refreshes
- **THEN** the request SHALL name `K1`

#### Scenario: Code exchange uses the key from the pushed request

- **WHEN** a sign-in's pushed authorization request reported key id `K1`
- **THEN** the code exchange for that sign-in SHALL name `K1`, even if the process was restarted in between

#### Scenario: Sessions from before this change still work

- **WHEN** a session stored by an earlier app version, with no key id and no issuer, is loaded
- **THEN** it SHALL load successfully and its next refresh SHALL be mediated, with the issuer derived from its token endpoint

### Requirement: Sign-in sends an attestation token and explains a refusal

Mediated sign-in requests SHALL carry an App Check token when one can be obtained, and SHALL still be sent without one when it cannot: the backend, not the app, decides whether a sign-in may proceed. When the backend refuses a sign-in because the app or device could not be verified, the sign-in screen SHALL show a message saying so, in the user's language, and SHALL NOT report it as a network or credentials error.

#### Scenario: Verified device signs in

- **WHEN** a sign-in is started on a device that yields a valid App Check token
- **THEN** the mediated requests SHALL carry it

#### Scenario: Token unavailable at sign-in

- **WHEN** a sign-in is started and obtaining an App Check token fails
- **THEN** the sign-in requests SHALL still be sent, without a token, so that the backend's observe-only mode can record the failure and let the sign-in proceed

#### Scenario: Unverifiable device sees a specific message

- **WHEN** the backend refuses a sign-in for failed verification
- **THEN** the sign-in screen SHALL show the device-verification message

### Requirement: A missing attestation token never blocks a refresh

If an App Check token cannot be obtained, the app SHALL still send refresh and revocation requests, without the token.

#### Scenario: Attestation service is unavailable

- **WHEN** a refresh is due and obtaining an App Check token fails
- **THEN** the refresh SHALL be sent without one

### Requirement: The app knows which session lifetime applies

The app SHALL record, per session, whether the backend last reported the client as public or confidential, and SHALL expose that to other parts of the app.

#### Scenario: Lifetime regime is recorded

- **WHEN** a mediated refresh response reports the client as confidential
- **THEN** the session SHALL be recorded as being on the confidential-client lifetime

### Requirement: Telemetry separates mediated from direct requests

Token refresh, sign-in and session-cleared telemetry SHALL record which path was used (mediated, direct, or direct after fallback), whether the client was reported as public or confidential, whether a stored response was served, and whether the authorization server is `bsky.social` or another host. Telemetry SHALL NOT include tokens, DIDs, key material or the name of any other host.

#### Scenario: Fallback is visible

- **WHEN** a refresh succeeds on the direct path after a failed attempt to reach the backend
- **THEN** its telemetry SHALL record the path as direct after fallback

#### Scenario: Session-cleared event carries the path

- **WHEN** a session is cleared after a relayed `invalid_grant`
- **THEN** the session-cleared event SHALL record that the refresh was mediated and whether the client was confidential
