## Why

Signing in to Bluesky requires storing an OAuth session — access + refresh JWTs plus a DPoP keypair — on the device, durably and encrypted. Without that persistence the app cannot stay logged in across process death, and no other auth work (Hilt-bound `AtOAuth`, `AuthRepository`, feed screen, profile) can land.

The obvious Android answer until recently was `androidx.security:security-crypto.EncryptedSharedPreferences`. It was deprecated in April 2025, shipped its final 1.1.0 in July 2025 as a terminal release, and Google's migration guidance is "use direct Android Keystore." In March 2026 Google shipped `androidx.datastore:datastore-tink` — a first-party `AeadSerializer` for Proto/typed DataStore that uses Tink AES-256-GCM with an Android Keystore-wrapped master key. That's the blessed 2026 path and the one we're adopting.

The auth strategy was also narrowed: OAuth-only, no app-password fallback (see nubecita-ym3 closed-as-superseded, nubecita-ck0 bumped to P1).

## What Changes

- **New `:core:auth` Android library module** (nubecita-9bl), rooted at `core/auth/`. Establishes the `:core:*` convention for non-UI cross-cutting libraries (mirror of `:designsystem`). `:app` adds `implementation(project(":core:auth"))`.
- **Encrypted OAuth session store** (nubecita-nss) — an implementation of the `io.github.kikin81.atproto.oauth.OAuthSessionStore` interface backed by a `DataStore<OAuthSession?>` wrapped in Tink's `AeadSerializer`. Single encrypted blob on disk; master key lives in `AndroidKeyStore`.
- **Hilt bindings in `:core:auth`**: the module provides the `OAuthSessionStore` binding into `SingletonComponent`. `:app` does not see Tink or DataStore internals.
- **New dependencies** on the version catalog: `androidx.datastore:datastore:1.3.0-alpha08` and `androidx.datastore:datastore-tink:1.3.0-alpha08` (both share a single `datastore` version entry — same release cadence, same pattern as the catalog's existing `androidxLifecycle`), `com.google.crypto.tink:tink-android`, and `org.jetbrains.kotlinx:kotlinx-serialization-json`.
- **Backup exclusions** in `:app` — `android:dataExtractionRules` and `android:fullBackupContent` excluding the session DataStore file and Tink keyset SharedPreferences so the encrypted blob is never copied off-device.
- **Graceful degradation on corrupted state** — decrypt failures, key invalidation, and garbled JSON all map to `load()` returning `null` rather than throwing. The store never crashes the app.

## Capabilities

### New Capabilities

- `core-auth-session-storage`: persisting a single AT Protocol OAuth session (access/refresh JWTs + DPoP keypair) encrypted at rest, with sign-out semantics and corruption-tolerant reads. Exposes the library's `OAuthSessionStore` interface via Hilt so `AtOAuth` and downstream auth components can consume it without knowing the storage backend.

### Modified Capabilities

_None._ No existing capability in `openspec/specs/` (`atproto-networking`, `dependency-injection`) owns requirements for session persistence today.

## Non-Goals

- **App-password auth.** Dropped in favor of OAuth-only on 2026-04-24 (nubecita-ym3 closed). If re-introduced later it would be a separate change.
- **The OAuth flow itself.** PAR, DPoP signing, browser handoff, redirect-URI callback handling all live in nubecita-ck0 and `atproto-oauth`'s `AtOAuth` class — this change only persists what that flow produces.
- **Revocation server calls on sign-out.** `clear()` wipes the local blob; revoking the refresh token at the authorization server is `AtOAuth.signOut()`'s job (nubecita-4g7 / nubecita-ck0).
- **StrongBox-backed keys.** Tink's `AndroidKeysetManager` doesn't expose `setIsStrongBoxBacked`. Standard TEE-backed Keystore is sufficient; revisit only on a security review finding.
- **Biometric-gated access.** `setUserAuthenticationRequired(false)` by default — we need background token refresh to work. Biometric gating would be a UX feature for a later change.
- **Instrumented-test coverage for on-device invariants.** Full Keystore + real-process-death tests are deferred to nubecita-16a (the emulator-runner CI bucket). This change ships JVM unit tests.
- **Login UI.** That is `:feature:auth` / `:app/ui/login`, and stays Compose-native. `:core:auth` is Compose-free.

## Deviations from Baseline

- **Persistence: DataStore + Tink, not Room.** The baseline stack lists Room for persistence. Room is the right tool for relational app data (feeds, drafts, cached profiles) but wrong for "one encrypted blob with a sign-out wipe." DataStore is the Google-recommended key-value replacement for SharedPreferences, and `datastore-tink` is the current official AEAD story. Room remains the right call for everything else.
- **Alpha dependency.** `androidx.datastore:datastore-tink:1.3.0-alpha08` is pre-stable. Accepted risk: API churn before `1.3.0` stable. Mitigation: keep the adapter layer (`EncryptedOAuthSessionStore` + `OAuthSessionSerializer`) thin so a migration — if ever required — touches ~2 files.

## Impact

- **New module**: `core/auth/` (library).
- **Version catalog**: `gradle/libs.versions.toml` gains a single `datastore` version entry shared by both `androidx.datastore:datastore` and `androidx.datastore:datastore-tink`, plus `tinkAndroid` and `kotlinxSerializationJson` version entries and the corresponding library aliases.
- **`:app` build.gradle.kts**: adds `implementation(project(":core:auth"))`.
- **`:app` AndroidManifest.xml**: gains `android:dataExtractionRules` + `android:fullBackupContent` attributes pointing at XML files provided by this change.
- **`:app` Hilt graph**: gains an injectable `OAuthSessionStore`. No existing `@Provides`/`@Binds` conflict today.
- **settings.gradle.kts**: new `include(":core:auth")`.
- **Spotless / ktlint / lint**: new module picks up existing ruleset (verify in tasks).
- **Downstream unblocks**: nubecita-4g7 (authenticated XrpcClient via AtOAuth) and nubecita-ck0 (OAuth flow) both depend on this store being available.
