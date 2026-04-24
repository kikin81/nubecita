## ADDED Requirements

### Requirement: Module provides an `OAuthSessionStore` Hilt binding

The `:core:auth` module SHALL provide a singleton-scoped Hilt binding for `io.github.kikin81.atproto.oauth.OAuthSessionStore`. Any `@AndroidEntryPoint`, `@HiltViewModel`, or other Hilt-managed class in `:app` or downstream modules SHALL be able to obtain an `OAuthSessionStore` via constructor injection without referencing `:core:auth` internals (Tink, DataStore, or the concrete implementation class).

#### Scenario: Consumer injects the store

- **WHEN** a `@HiltViewModel`-annotated class in `:app` declares a constructor parameter of type `OAuthSessionStore`
- **THEN** Hilt SHALL resolve it to the singleton instance provided by `:core:auth` at graph construction time

#### Scenario: Implementation class is not leaked

- **WHEN** `:app` source is inspected
- **THEN** it SHALL not reference the concrete `EncryptedOAuthSessionStore` class, `DataStore`, Tink, or `OAuthSessionSerializer` — only the `OAuthSessionStore` interface from the `atproto-oauth` library

### Requirement: Session round-trips through `save` and `load`

The store SHALL persist an `OAuthSession` via `save(session)` such that a subsequent `load()` returns an `OAuthSession` whose every field (including `accessToken`, `refreshToken`, `did`, `handle`, `pdsUrl`, `tokenEndpoint`, `revocationEndpoint`, `clientId`, `dpopPrivateKey`, `dpopPublicKey`, `authServerNonce`, `clockOffsetSeconds`, `pdsNonce`) is byte-for-byte equal to the saved value. The `ByteArray` fields holding DPoP key material SHALL survive encoding and decoding without truncation or mutation.

#### Scenario: Freshly-saved session loads identically

- **WHEN** a fully-populated `OAuthSession` is passed to `save(session)` and `load()` is then called
- **THEN** the returned `OAuthSession` SHALL equal the saved one under both `==` (which the library defines as `did + accessToken` match) and a field-by-field comparison

#### Scenario: DPoP keypair bytes survive round trip

- **WHEN** `save(session)` is called with non-empty `dpopPrivateKey` and `dpopPublicKey` byte arrays
- **THEN** the bytes returned by `load()?.dpopPrivateKey` and `load()?.dpopPublicKey` SHALL be identical in length and content to the saved arrays

### Requirement: Sign-out clears persisted session

After `clear()` completes, `load()` SHALL return `null`. Subsequent `save(newSession)` followed by `load()` SHALL return `newSession` — clearing MUST NOT permanently disable the store.

#### Scenario: Load after clear returns null

- **WHEN** `save(session)` is called and then `clear()` is called
- **THEN** the next `load()` SHALL return `null`

#### Scenario: Store is reusable after clear

- **WHEN** `clear()` is called on a previously-populated store, and then `save(newSession)` is called
- **THEN** the next `load()` SHALL return `newSession`

### Requirement: Encryption at rest

The persisted session file on disk SHALL NOT contain any of the session's JWT tokens, DPoP key material, or PDS URL as plaintext. Every byte the DataStore file commits SHALL pass through an AEAD cipher keyed from an `AndroidKeyStore`-managed master key.

#### Scenario: No access-token plaintext on disk

- **WHEN** `save(session)` is called with a recognizable access-token payload and the backing DataStore file is read raw
- **THEN** the file's byte contents SHALL NOT contain the access-token string, the refresh-token string, or the PDS URL as UTF-8 substrings

_(Runtime verification is deferred to the instrumented-test bucket under `nubecita-16a`; this requirement is binding from shipping day regardless of where it is tested.)_

### Requirement: Corrupted storage degrades to "no session"

If decrypting, deserializing, or parsing the persisted session fails for any reason — including `GeneralSecurityException`, `AEADBadTagException`, `kotlinx.serialization.SerializationException`, or `android.security.keystore.KeyPermanentlyInvalidatedException` — `load()` SHALL return `null` rather than propagate the exception to the caller. The app SHALL NOT crash on a corrupted session file.

#### Scenario: Decrypt failure returns null

- **WHEN** the underlying cipher raises `GeneralSecurityException` while reading the session
- **THEN** `load()` SHALL return `null` and SHALL NOT rethrow

#### Scenario: Malformed JSON returns null

- **WHEN** decrypted bytes fail to parse as a valid `OAuthSession` JSON payload
- **THEN** `load()` SHALL return `null` and SHALL NOT rethrow

#### Scenario: Key invalidation recovers

- **WHEN** the Keystore master key is permanently invalidated and `load()` is called, followed by `save(newSession)` and `load()`
- **THEN** the first `load()` SHALL return `null`, the subsequent `save(newSession)` SHALL succeed (regenerating the keyset as needed), and the final `load()` SHALL return `newSession`

### Requirement: Session survives process death

When the app process is killed and restarted, a previously-saved session SHALL be readable. The store SHALL NOT rely on in-memory-only state for persistence.

#### Scenario: Load after process restart

- **WHEN** `save(session)` is called, the hosting app process is terminated, the app is relaunched, and `load()` is called on a freshly-injected `OAuthSessionStore`
- **THEN** `load()` SHALL return a session equal to the originally-saved one

_(Runtime verification is deferred to the instrumented-test bucket under `nubecita-16a`; this requirement is binding from shipping day regardless.)_

### Requirement: Backup exclusion for session artifacts

The session DataStore file and the Tink keyset `SharedPreferences` file SHALL be excluded from Android Auto Backup, device-to-device transfer, and cloud backup. The `:app` manifest SHALL reference XML rules provided by `:core:auth` that list these exclusions for both `<cloud-backup>` and `<device-transfer>` domains in the modern `data-extraction-rules` format and the legacy `full-backup-content` format.

#### Scenario: Manifest references the backup-exclusion XML

- **WHEN** `:app`'s merged `AndroidManifest.xml` is inspected
- **THEN** the `<application>` element SHALL carry both `android:dataExtractionRules` and `android:fullBackupContent` attributes pointing at XML resources that contain `<exclude>` entries for the session DataStore file path and the Tink keyset `SharedPreferences` file path

### Requirement: `:app` does not take direct dependencies on Tink or DataStore

The `:app` module's `build.gradle.kts` SHALL NOT declare `implementation` or `api` dependencies on `androidx.datastore:*`, `com.google.crypto.tink:*`, or `io.github.kikin81.atproto:oauth` (the session-store interface is re-exported only as needed via `:core:auth`). `:app` gains access to `OAuthSessionStore` exclusively through `implementation(project(":core:auth"))`.

#### Scenario: App build file inspection

- **WHEN** `app/build.gradle.kts` is parsed
- **THEN** it SHALL declare `implementation(project(":core:auth"))` and SHALL NOT declare any `androidx.datastore`, `com.google.crypto.tink`, or direct `atproto-oauth` dependency
