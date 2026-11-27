# Spec Delta

## Purpose

Defines Nubecita's published OAuth client metadata — the production file and the files used for testing — along with the signing keys they advertise and the rules for changing any of them without signing users out.

## ADDED Requirements

### Requirement: Testing never uses the production client

A separate test client metadata file SHALL be published for development and debug builds, registering a confidential `web` client with its own signing key. Debug builds SHALL be able to select it at build time. The production metadata file SHALL NOT be changed for testing.

#### Scenario: Debug build uses the test client

- **WHEN** a debug build is made with the test client selected
- **THEN** its sign-ins SHALL use the test client's `client_id` and be signed with the test key

#### Scenario: Test and production keys are distinct

- **WHEN** the test and production metadata files are compared
- **THEN** they SHALL advertise different public keys

### Requirement: The change of authentication method is rehearsed first

A rehearsal client metadata file SHALL exist that starts as a public `native` client and is then changed to a confidential `web` client. The production metadata SHALL NOT be changed until that rehearsal has shown, with a real account on a real device, what happens to a live session, to an app that still calls the authorization server directly, and to a session after the change is reverted.

#### Scenario: Live session survives the rehearsal change

- **WHEN** a session created while the rehearsal client was public refreshes through the backend after the client becomes confidential
- **THEN** the refresh SHALL succeed without the user signing in again

#### Scenario: Rehearsal results are recorded

- **WHEN** the rehearsal is complete
- **THEN** its observed outcomes SHALL be written into the runbook before the production change is proposed

### Requirement: Confidential metadata is internally consistent

A metadata file that registers `private_key_jwt` SHALL declare `application_type` `web`, a signing algorithm, at least one public key embedded in the file, and only https redirect URIs. It SHALL keep `dpop_bound_access_tokens` true and the same scope as before.

#### Scenario: No custom-scheme redirect on a confidential client

- **WHEN** a metadata file registers `private_key_jwt`
- **THEN** it SHALL NOT list `app.nubecita:/oauth-redirect` or any other non-https redirect

#### Scenario: Keys are embedded, not linked

- **WHEN** a confidential metadata file is inspected
- **THEN** its public keys SHALL be in the file itself, so that the method and the keys always change together

### Requirement: Metadata is validated before it is published

Every metadata file SHALL be checked automatically, before merge, against the rules an AT Protocol authorization server applies to client metadata.

#### Scenario: Invalid metadata fails the check

- **WHEN** a pull request makes a metadata file register `private_key_jwt` with `application_type` `native`
- **THEN** the check SHALL fail

### Requirement: A published key stays published while sessions depend on it

A public key SHALL NOT be removed from a metadata file while any session bound to it may still be alive, except as a deliberate revocation of all those sessions. Adding a new key SHALL NOT remove an existing one.

#### Scenario: Routine rotation keeps the old key

- **WHEN** a new default signing key is introduced
- **THEN** the previous key SHALL remain in the metadata and usable by the backend for two years after it stopped being the default

#### Scenario: Removal is recorded as a revocation

- **WHEN** a key is removed while sessions bound to it may still exist
- **THEN** the change SHALL state that it signs those users out

### Requirement: The production change is gated and treated as irreversible

The production metadata SHALL change from public to confidential only when every adoption, readiness and safety gate below holds, and the pull request that makes the change SHALL state the evidence for each. Once changed, it SHALL NOT be reverted to public.

#### Scenario: Adoption gates

- **WHEN** the production change is proposed
- **THEN** at least 95% of 7-day active users, excluding test devices and known bot traffic, SHALL be on a version that uses the backend, and at least 14 days SHALL have passed since that version reached full rollout

#### Scenario: Readiness gates

- **WHEN** the production change is proposed
- **THEN** the rehearsal SHALL be recorded, the rehearsal session SHALL still be valid more than 14 days after its sign-in, replay-safe refresh SHALL be live, and a release with the highest in-app-update priority SHALL be live

#### Scenario: Reviewed measurements

- **WHEN** the production change is proposed
- **THEN** the measured App Check pass rate for sign-ins and the share of sessions on authorization servers other than `bsky.social` SHALL have been reviewed

#### Scenario: Safety measures during the change

- **WHEN** the production change is merged
- **THEN** the backend's minimum refresh interval SHALL be switched on for the hour around it, and the default signing key SHALL NOT be changed within 24 hours of it

#### Scenario: Reverting is refused

- **WHEN** a change proposes returning the production client to `token_endpoint_auth_method` `none` after it has been confidential
- **THEN** the change SHALL be rejected, because it would delete every session that refreshed while the client was confidential
