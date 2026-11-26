# Spec Delta

## Purpose

The token-mediating backend is the server half of Nubecita's confidential OAuth client. It authenticates the client to a user's AT Protocol authorization server on the app's behalf, so that sessions qualify for the confidential-client lifetime, without ever being able to use the tokens it handles.

## ADDED Requirements

### Requirement: The backend mediates exactly four OAuth requests

The backend SHALL accept pushed authorization requests, authorization-code exchanges, refresh-token exchanges and token revocations, and SHALL forward each to the corresponding endpoint of the user's authorization server. It SHALL NOT accept or forward any other request, including XRPC calls.

#### Scenario: A refresh is forwarded to the token endpoint

- **WHEN** the app sends a refresh-token exchange naming an authorization server
- **THEN** the backend SHALL forward it to that server's token endpoint and relay the response

#### Scenario: An unrelated path is refused

- **WHEN** a request arrives for any path other than the four mediated requests
- **THEN** the backend SHALL answer with a not-found error and SHALL make no outbound request

### Requirement: The backend adds a client assertion to every forwarded request

Every forwarded request SHALL carry a JWT client assertion signed with the key registered for the request's `client_id`. The assertion SHALL name the `client_id` as issuer and subject, the authorization server's issuer as audience, an issued-at time of now, an expiry no more than 60 seconds later, and a `jti` that is never reused. Its header SHALL carry the key id.

#### Scenario: Assertion claims are correct

- **WHEN** the backend forwards a request for client `C` to an authorization server whose issuer is `I`
- **THEN** the assertion SHALL have `iss` and `sub` equal to `C`, `aud` equal to `I`, an `iat` within a few seconds of now, and a `kid` header naming the signing key

#### Scenario: A retried request gets a fresh assertion

- **WHEN** the app retries a request after the authorization server asked for a new DPoP nonce
- **THEN** the backend SHALL sign a new assertion with a new `jti` for the retry

#### Scenario: An unknown client is refused

- **WHEN** a request names a `client_id` the backend holds no key for
- **THEN** the backend SHALL refuse it without making any outbound request

### Requirement: The signing key cannot leave the key service

The private key used for client assertions SHALL be held in a managed key service that performs the signature and does not permit export of the key material. No private key material SHALL exist in the repository, in deployment configuration, or in the service's memory.

#### Scenario: The service signs without holding the key

- **WHEN** the backend needs to sign an assertion
- **THEN** it SHALL request a signature from the key service and SHALL NOT read key material

#### Scenario: Repository inspection finds no key material

- **WHEN** the repository and its deployment configuration are inspected
- **THEN** they SHALL contain only public keys and key identifiers

### Requirement: A session's signing key is chosen by the caller and never substituted

The app MAY name the key id a session is bound to. When it does, the backend SHALL sign with exactly that key. If the backend does not hold that key, it SHALL refuse the request without forwarding it. When no key id is named, the backend SHALL sign with the client's current default key. Every relayed response SHALL report the key id that was used.

#### Scenario: Named key is used

- **WHEN** a refresh names key id `K1` and the backend holds `K1` and a newer default `K2`
- **THEN** the assertion SHALL be signed with `K1` and the response SHALL report `K1`

#### Scenario: Unknown named key is refused, not replaced

- **WHEN** a refresh names a key id the backend does not hold
- **THEN** the backend SHALL answer with its own error, SHALL NOT forward the request, and SHALL NOT sign with a different key

#### Scenario: No key named

- **WHEN** a request names no key id
- **THEN** the backend SHALL sign with the default key and report its key id in the response

### Requirement: DPoP proofs and nonces pass through unchanged

The backend SHALL forward the app's DPoP proof to the authorization server byte-for-byte and SHALL relay the authorization server's `DPoP-Nonce` and server time back to the app. It SHALL NOT create, alter or re-sign a DPoP proof.

#### Scenario: Nonce challenge is relayed

- **WHEN** the authorization server answers with a `use_dpop_nonce` error and a `DPoP-Nonce` header
- **THEN** the app SHALL receive the same status, error body and nonce value

### Requirement: Relayed responses are faithful and marked

The backend SHALL relay the authorization server's status code and body unchanged, and SHALL mark every response as either relayed from the authorization server or produced by the backend itself. A backend-produced error SHALL NOT use an OAuth `error` value of `invalid_grant`.

#### Scenario: Authorization server error is relayed as-is

- **WHEN** the authorization server answers a refresh with `invalid_grant`
- **THEN** the app SHALL receive that same status and body, marked as relayed

#### Scenario: Backend failure is distinguishable

- **WHEN** the backend fails before or instead of forwarding a request
- **THEN** its response SHALL be marked as backend-produced, SHALL say whether the request was forwarded, and SHALL NOT contain `invalid_grant`

### Requirement: Sign-in requires a verified app and device

Pushed authorization requests and authorization-code exchanges SHALL be refused, without being forwarded, unless they carry a valid App Check token for a registered Nubecita app. An enforcement setting SHALL allow this check to run in observe-only mode, in which the verdict is recorded and the request proceeds.

#### Scenario: Sign-in without a valid token is refused when enforcing

- **WHEN** enforcement is on and a pushed authorization request arrives with a missing or invalid App Check token
- **THEN** the backend SHALL refuse it with its own error and SHALL NOT forward it

#### Scenario: Observe-only mode records and proceeds

- **WHEN** enforcement is in observe-only mode and a sign-in request arrives with an invalid App Check token
- **THEN** the backend SHALL record the failed verdict and forward the request

### Requirement: Refresh and revocation are not blocked by attestation

Refresh-token exchanges and revocations SHALL be forwarded whether or not they carry a valid App Check token. The verdict SHALL be recorded. A separate setting SHALL allow this to be made strict.

#### Scenario: Refresh proceeds without a token

- **WHEN** a refresh arrives with no App Check token and the strict setting is off
- **THEN** the backend SHALL forward it and record that the token was missing

### Requirement: Outbound requests are limited to genuine authorization servers

The app SHALL supply only an authorization server issuer, never an endpoint URL. The backend SHALL take endpoints from that issuer's own published metadata and SHALL verify the metadata names the same issuer. It SHALL only connect over https to public addresses, SHALL NOT follow redirects, and SHALL bound response size and time.

#### Scenario: Private and metadata addresses are refused

- **WHEN** an issuer's hostname resolves to a loopback, private, link-local or cloud-metadata address
- **THEN** the backend SHALL refuse the request without connecting

#### Scenario: IP-literal and non-https issuers are refused

- **WHEN** an issuer is not an https URL with a DNS hostname
- **THEN** the backend SHALL refuse the request without connecting

#### Scenario: Mismatched issuer is refused

- **WHEN** the metadata fetched from an issuer names a different issuer
- **THEN** the backend SHALL refuse the request

#### Scenario: Redirects are not followed

- **WHEN** an authorization server answers with a redirect
- **THEN** the backend SHALL NOT follow it and SHALL relay or refuse the response

### Requirement: The backend answers before the app gives up

The backend SHALL bound the time it waits for an authorization server so that it always responds to the app, with either the relayed response or a backend-produced error stating the outcome is unknown, before the app's own request timeout elapses.

#### Scenario: Slow authorization server

- **WHEN** an authorization server has not answered within the backend's upstream time limit
- **THEN** the backend SHALL answer the app with a backend-produced error marked as forwarded with unknown outcome

### Requirement: A refresh token is forwarded at most once at a time

For a given refresh token, the backend SHALL have at most one forwarded request in flight. A second request with the same refresh token that arrives while the first is in flight SHALL NOT be forwarded.

#### Scenario: Concurrent duplicate is held back

- **WHEN** two refresh requests with the same refresh token arrive at the same time
- **THEN** exactly one SHALL be forwarded, and the other SHALL receive either the first one's result or a backend-produced "try again" error

#### Scenario: Retry after an unknown outcome is held back

- **WHEN** a refresh was forwarded and the backend answered that its outcome is unknown, and the same refresh token is presented again before the first attempt could still be running
- **THEN** the backend SHALL NOT forward it again and SHALL answer with a backend-produced "try again" error

### Requirement: An operator can enforce a minimum interval between refreshes

An operator SHALL be able to set a minimum interval. While it is set, the backend SHALL refuse, without forwarding, a refresh whose refresh token it issued more recently than that interval. The setting SHALL be off by default.

#### Scenario: Second refresh inside the interval is refused

- **WHEN** the interval is set to 30 minutes and a refresh arrives using a token the backend issued 5 minutes earlier
- **THEN** the backend SHALL answer with a backend-produced "try again" error and SHALL NOT forward the request

#### Scenario: Interval off

- **WHEN** the interval is not set
- **THEN** refreshes SHALL be forwarded regardless of how recently their token was issued

### Requirement: The backend is reached at a stable address

The backend SHALL be served from a hostname under a domain Nubecita controls, so that its hosting region, service name or platform can change without any change to shipped apps.

#### Scenario: Moving the service does not change the address

- **WHEN** the backend is redeployed to a different region
- **THEN** apps SHALL reach it at the same URL as before

### Requirement: A lost refresh response can be recovered

After a successful refresh, the backend SHALL keep the response so that a later request presenting the same, now-spent refresh token and a valid DPoP proof from the same key receives that same response instead of being forwarded. The response SHALL be retrievable for 24 hours unless superseded earlier.

#### Scenario: Retry after a lost response

- **WHEN** a refresh succeeded at the authorization server but the app never received the response, and the app retries with the same refresh token and a fresh DPoP proof from the same key
- **THEN** the backend SHALL return the stored response, marked as relayed, and SHALL NOT contact the authorization server

#### Scenario: Wrong DPoP key gets nothing

- **WHEN** a request presents a spent refresh token with a DPoP proof from a different key, or with an invalid or stale proof
- **THEN** the backend SHALL NOT return the stored response

#### Scenario: Entry is removed once superseded

- **WHEN** the app refreshes using the refresh token from a stored response
- **THEN** the backend SHALL remove that stored response

#### Scenario: Entry expires

- **WHEN** 24 hours have passed since a response was stored
- **THEN** the backend SHALL no longer return it

### Requirement: Stored responses are unreadable without the spent refresh token

A stored refresh response SHALL be encrypted so that it cannot be decrypted by someone who has access to the storage but not to the spent refresh token it answers. The backend SHALL NOT store any refresh token, access token or authorization code in readable form.

#### Scenario: Storage inspection reveals no tokens

- **WHEN** the replay store's contents are read directly
- **THEN** no entry SHALL contain a token, a code, or anything from which one can be derived without the spent refresh token

#### Scenario: A failed store does not fail the refresh

- **WHEN** a refresh succeeds at the authorization server but storing the response fails
- **THEN** the backend SHALL still relay the response to the app

### Requirement: Logs never contain credentials or identities

The backend SHALL log only the request type, the authorization server's hostname, status, upstream OAuth error code, latency, key id, attestation verdict, and whether a stored response was served. It SHALL NOT log request or response bodies, tokens, codes, DPoP proofs, client assertions, DIDs or handles.

#### Scenario: A refresh leaves no secrets in the log

- **WHEN** a refresh is processed, successfully or not
- **THEN** the log entries for it SHALL contain none of the refresh token, access token, DPoP proof, client assertion or account DID

### Requirement: An operator can tell apps to bypass the backend

An operator SHALL be able to switch the backend into bypass mode within two minutes without changing code. In bypass mode the backend SHALL make no outbound request. It SHALL still return a stored refresh response when it has one for the request, and SHALL answer every other request with a backend-produced response that says the request was not forwarded and that the app should use the direct path.

#### Scenario: Bypass mode refuses without forwarding

- **WHEN** bypass mode is on and a mediated request arrives for which no stored response exists
- **THEN** the backend SHALL answer with the bypass response and SHALL make no outbound request

#### Scenario: Bypass mode still recovers a lost response

- **WHEN** bypass mode is on and a refresh arrives whose refresh token has a stored response
- **THEN** the backend SHALL return the stored response rather than the bypass response

### Requirement: The backend reports the client's registered authentication method

Every relayed response SHALL state whether the client's published metadata registers it as public or confidential, as last read by the backend within the previous five minutes, so the app can tell which session lifetime applies.

#### Scenario: Confidential client is reported

- **WHEN** the client's published metadata registers `private_key_jwt`
- **THEN** relayed responses SHALL report the client as confidential

### Requirement: Sign-in starts are rate limited and the service is bounded

The backend SHALL limit the rate of pushed authorization requests per caller address and SHALL cap the number of service instances, so that abuse cannot exhaust the client's quota at authorization servers or run up unbounded cost.

#### Scenario: Burst of sign-in starts from one address

- **WHEN** one address sends more pushed authorization requests than the limit allows
- **THEN** the excess SHALL be refused with a backend-produced rate-limit error and SHALL NOT be forwarded

### Requirement: Deployment is restricted and keyless

The backend SHALL be deployable only from the default branch of its repository, through short-lived federated credentials and an approval step. The running service's identity SHALL be permitted only to sign with the client keys and to use the replay store.

#### Scenario: No long-lived deploy credential exists

- **WHEN** the repository's secrets and workflows are inspected
- **THEN** no service-account key or other long-lived cloud credential SHALL be present

#### Scenario: A pull request cannot deploy

- **WHEN** a workflow runs for a pull request or a non-default branch
- **THEN** it SHALL NOT be able to deploy the backend
