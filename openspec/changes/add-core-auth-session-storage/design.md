## Context

Nubecita needs encrypted, durable, on-device storage for a single AT Protocol OAuth session (access JWT + refresh JWT + DPoP keypair + endpoint metadata). The `atproto-oauth` library (5.0.1, already on the classpath) defines the data model (`OAuthSession`, `@Serializable`) and the persistence interface (`OAuthSessionStore { suspend load(); suspend save(); suspend clear() }`). This change implements that interface on Android and wires it into Hilt.

The landscape shifted twice recently:

1. **April 2025**: Google deprecated `androidx.security:security-crypto` (`EncryptedSharedPreferences`). Stable `1.1.0` shipped July 2025 as a terminal release. The deprecation note points consumers at "existing platform APIs and direct use of Android Keystore" with no drop-in successor.
2. **March 2026**: Google shipped `androidx.datastore:datastore-tink:1.3.0-alpha07` (alpha08 by our pin date) — a first-party `AeadSerializer` that wraps a user-provided `DataStore.Serializer<T>` with Tink AES-256-GCM, using an `AndroidKeyStore`-wrapped master key.

Today is 2026-04-24. We are building greenfield (no install base) with minSdk 26. The auth strategy was narrowed to OAuth-only on 2026-04-24 (see bd memory), so we persist exactly one blob shape — `OAuthSession` — with no polymorphic discriminator.

Stakeholders downstream: `nubecita-4g7` (Hilt bindings for `AtOAuth` + authenticated `XrpcClient`) and `nubecita-ck0` (OAuth flow) both consume `OAuthSessionStore` via Hilt. This change must produce a singleton-scoped `OAuthSessionStore` binding usable from any `@AndroidEntryPoint` / `@HiltViewModel`.

## Goals / Non-Goals

**Goals:**

- Persist a single `OAuthSession` encrypted at rest; survive process death and app restart.
- Clear all persisted session state on sign-out so no stale tokens remain.
- Degrade to "signed out" on any storage-layer failure (corruption, decrypt error, key invalidation) rather than crash the app.
- Keep the `:app` module Compose-focused — zero direct dependency on Tink, DataStore, or `atproto-oauth`'s storage internals. `:app` sees only the `OAuthSessionStore` interface.
- Establish the `:core:*` module convention for non-UI cross-cutting libraries.

**Non-Goals:**

- App-password auth. Dropped on 2026-04-24; if re-introduced it is a separate change.
- OAuth flow mechanics (PAR, DPoP signing, browser handoff, deep-link callback). Those live in `atproto-oauth`'s `AtOAuth` + `nubecita-ck0`.
- Server-side revocation on sign-out. `AtOAuth.signOut()`'s responsibility.
- Biometric-gated access to the session. Would block background token refresh; defer as a future UX feature.
- StrongBox-backed master key. Not exposed by Tink's `AndroidKeysetManager` today; standard TEE-backed is sufficient.
- Full on-device invariants (real Keystore round trip, process-kill survival, plaintext-check on disk). Deferred to `nubecita-16a` (instrumented-test CI bucket).

## Decisions

### 1. New `:core:auth` Android library module, not code in `:app`

**Decision:** Create a new module `core/auth/` with `com.android.library`. `:app` adds `implementation(project(":core:auth"))` and nothing else.

**Alternatives considered:**
- **Inline in `:app` under `net.kikin.nubecita.data.auth`.** Lower ceremony today, but (a) pulls Tink / DataStore / `atproto-oauth` into `:app`'s direct dep graph, (b) misses the pattern we want to repeat for `nubecita-4g7`, the `AuthRepository`, the deep-link parser, and any future `:core:network` / `:core:datastore` modules, (c) delays the inevitable refactor.
- **`:libs:auth`.** Same shape as `:core:auth`; chosen name is `:core:*` because it matches the Now-in-Android convention of "foundational, non-UI capability" better than the vaguer "libs."

**Rationale:** `:designsystem` already set the non-`:app` module precedent. Auth is growing into a bounded context (this change + the three follow-up issues already filed) that maps cleanly to a module. Forcing an explicit module boundary also means `internal` visibility actually constrains something — the Tink/serializer internals stay hidden from `:app`.

### 2. `androidx.datastore:datastore-tink:1.3.0-alpha08` + AEAD, not hand-rolled AES-GCM and not `EncryptedSharedPreferences`

**Decision:** Use the first-party `AeadSerializer` from `datastore-tink` wrapping our own `OAuthSessionSerializer`. Tink's `AndroidKeysetManager` manages a keyset stored in a Tink-owned `SharedPreferences` file; the keyset itself is encrypted under an `AndroidKeyStore`-generated master key with URI `android-keystore://nubecita_oauth_session_master_key`.

**Alternatives considered:**
- **`EncryptedSharedPreferences`.** Deprecated as of April 2025; terminal release. Taking a new dependency on a deprecated library in 2026 is a smell even if it still functions.
- **Hand-rolled AES-256-GCM + DataStore Preferences.** ~80 lines of Keystore + `Cipher` + IV/AAD code. Technically correct but we'd be writing crypto glue in userland when the platform ships an adequate answer.
- **Google Tink directly (without `datastore-tink`)** — ~3 MB unshrunk library, ~500 KB – 1 MB post-R8, manual wiring of `AeadSerializer`. We're still paying the Tink dep via `datastore-tink`, so the split option has all the cost and none of the benefit.

**Rationale:** `datastore-tink` is the current officially-blessed AEAD-at-rest story for typed DataStore on Android. It hides correct AEAD semantics (IV handling, AAD, nonce reuse avoidance) behind a one-line wrap. Accepted risk: alpha status — mitigated by keeping the adapter (`EncryptedOAuthSessionStore` + `OAuthSessionSerializer`) thin.

### 3. `kotlinx.serialization` JSON for the inner serializer, not Protobuf

**Decision:** `OAuthSessionSerializer` uses `Json.encodeToString` / `decodeFromString` against the library's `@Serializable`-annotated `OAuthSession`. The inner payload is JSON bytes; Tink wraps those bytes with AEAD.

**Alternatives considered:**
- **Proto DataStore with a `.proto` schema for `OAuthSession`.** Requires duplicating the library's data model in protobuf, adding the `protoc` Gradle plugin, and keeping two definitions in sync when the library evolves. No size or perf win that matters for a ~1 KB blob.
- **Java serialization.** No.

**Rationale:** `OAuthSession` is already `@Serializable`; `kotlinx-serialization-json` is already on the `:app` classpath. Zero schema duplication. `Json { ignoreUnknownKeys = true }` gives us forward compatibility when the `atproto-oauth` library adds fields.

### 4. Single encrypted DataStore file, not per-field encryption

**Decision:** One `DataStore<OAuthSession?>` at `oauth_session.pb`. Whole session is one encrypted record with `defaultValue = null`.

**Alternatives considered:**
- **Per-field encryption (access token in one slot, DPoP key in another).** No security benefit — the attacker we care about (offline disk inspection on a rooted device) sees the same ciphertext density. Adds consistency problems: a partial write on process kill could leave fields from two different sessions co-resident.

**Rationale:** Session data is atomic — the access token is bound to the DPoP keypair; mixing fields from two sessions produces unusable auth. One record = one atomicity unit.

### 5. `clear()` writes `null`; it does not delete the file or the Keystore master key

**Decision:** `clear()` updates DataStore with `defaultValue` (`null`). The file is retained with an encrypted empty record. The Keystore master key is retained across sign-out/sign-in cycles.

**Alternatives considered:**
- **Delete the DataStore file on clear.** Requires filesystem operations outside DataStore's API and racing with any in-flight reads. No security benefit — the ciphertext of `null` is as opaque as an absent file to an attacker.
- **Delete the Keystore master key on clear.** Adds ~100 ms of key-gen latency on the next sign-in for no security benefit — the retained key reveals nothing about prior session contents.

**Rationale:** Simpler, uses DataStore's own atomicity guarantees, keeps the happy path (sign-out → sign-in → sign-out → ...) free of filesystem drama.

### 6. Graceful degradation on corruption, not crash

**Decision:** `OAuthSessionSerializer.readFrom` catches `SerializationException`, `GeneralSecurityException`, `AEADBadTagException`, and `KeyPermanentlyInvalidatedException`, logs the class name (not contents), and returns `null`. On `KeyPermanentlyInvalidatedException` specifically, it additionally nukes the Tink keyset SharedPreferences so the next write can recreate the keyset.

**Alternatives considered:**
- **Propagate the exception.** The library's `load()` signature allows it, but surfacing a crypto exception to `AtOAuth` doesn't give `AtOAuth` anything useful to do — the only recovery is "treat as signed out," which `null` already signals. Crashing the process over a corrupted session on cold-start is worse UX than silent re-login.

**Rationale:** "No session" is already a legal state in the `OAuthSessionStore` contract; corrupted state is semantically identical to "no session" from the caller's perspective. Logging preserves diagnostic signal; returning null preserves app stability.

### 7. Backup exclusions live as XML files referenced by `:app`, not the library

**Decision:** `:app` adds `android:dataExtractionRules` and `android:fullBackupContent` attributes on its `<application>` tag, pointing at `@xml/auth_data_extraction_rules` and `@xml/auth_backup_rules`. Those XML files live in `:core:auth/src/main/res/xml/` and merge into `:app`'s resources by AGP's standard resource-merging rules.

**Alternatives considered:**
- **Library-provided `<application>` manifest attribute.** AGP manifest merger would fight us — `<application>`-level attributes conflict across libraries.
- **XML in `:app`.** Keeps them local to where they're referenced but splits ownership of "what does `:core:auth` need excluded" away from `:core:auth`.

**Rationale:** Resource merging is transparent and exactly what AGP is designed for. Ownership stays with the module that generates the files to be excluded; wiring is explicit in `:app`.

### 8. Sign-out scope is local-only in this change

**Decision:** `clear()` wipes the local blob. Revocation calls to the AT Protocol authorization server's revocation endpoint are `AtOAuth.signOut()`'s responsibility (delivered under `nubecita-4g7` / `nubecita-ck0`).

**Rationale:** Bundling network calls into a storage module would force `:core:auth` to depend on Ktor, pull in network-error-handling concerns, and blur the layer. Keep the layer: `:core:auth` is persistence; `AtOAuth` is protocol.

## Risks / Trade-offs

- **Alpha dependency on `datastore-tink:1.3.0-alpha08`.** API may churn before `1.3.0` stable. → Mitigation: the adapter (`EncryptedOAuthSessionStore` + `OAuthSessionSerializer`) is ~100 lines total; a future migration touches two files.
- **Tink SDK size impact.** `tink-android` is ~500 KB – 1 MB after R8 shrinking. → Accepted; we get actively-maintained AEAD correctness in exchange. No alternative in the "blessed path" is smaller.
- **`KeyPermanentlyInvalidatedException` from Keystore (rare — lock-screen reset, factory wipe of user data area).** → Mitigation: catch on load, wipe the Tink keyset prefs, return `null`. User re-logs in; no other user-facing breakage.
- **Auto Backup regressions if a future developer flips `android:allowBackup`.** → Mitigation: explicit `<exclude>` entries in `auth_data_extraction_rules.xml` and `auth_backup_rules.xml` keep the session excluded even if backup is re-enabled globally. Unit test or lint check is out of scope for this change; document in the file header comments.
- **Backup-rules test coverage is manifest-only.** We verify the XML files exist and the manifest attributes reference them, but runtime backup behavior is hard to test without `adb bmgr` and a physical device. Defer runtime verification to `nubecita-16a`.
- **Keystore alias collision with a future `:core:auth` user.** We hard-code the master key URI `android-keystore://nubecita_oauth_session_master_key`. → Mitigation: document the alias in a module README / package docs so the next author collaborating on `:core:auth` doesn't reuse the same slot.

## Migration Plan

Greenfield — no migration. On first sign-in after this change lands, the keyset is generated and the DataStore file is created. No existing user sessions to preserve.

## Open Questions

- **Does `datastore-tink:1.3.0-alpha08` expose `AeadSerializer` as a top-level factory, or is it nested under a helper?** Verified at implementation time; adapter shape absorbs whichever signature ships.
- **Does Tink's `AndroidKeysetManager` emit a first-run "keyset not found, generating" warning in logcat?** Historically yes; benign. If it surfaces as a lint warning, suppress narrowly.
- **Should we rotate the Tink keyset on a schedule (e.g., every 90 days)?** Not today — rotation would force a re-login and the blob is small enough that the cryptoperiod argument is weak. Revisit if a security review requires it.
