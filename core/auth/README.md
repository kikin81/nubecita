# `:core:auth`

Cross-cutting auth library for Nubecita. UI-free: no Compose, no activities,
no theming — this module only owns auth state and the plumbing that persists
it securely.

## Public surface

Exactly one binding is exposed to downstream modules via Hilt:

```kotlin
@Inject constructor(val store: io.github.kikin81.atproto.oauth.OAuthSessionStore)
```

Everything else — the encrypted DataStore, the Tink keyset manager, the
kotlinx-serialization JSON serializer — is `internal` to this module.

## What this module persists

A single `OAuthSession` (from `atproto-oauth`) containing access + refresh
JWTs, DPoP keypair bytes, and PDS / auth-server metadata. One session per
device; sign-out wipes it.

## Crypto

- Keyset managed by Tink's `AndroidKeysetManager`, stored in the
  `nubecita_core_auth_keyset` `SharedPreferences` file.
- Keyset itself encrypted by an `AndroidKeyStore` master key with URI
  `android-keystore://nubecita_oauth_session_master_key`. **Reserved** —
  do not reuse this alias for any other data inside `:core:auth`.
- Per-record AEAD via `AeadSerializer` (AES-256-GCM) with
  `associatedData = "nubecita.oauth.session.v1"` as a ciphertext-swap guard.
- On `KeyPermanentlyInvalidatedException` during keyset build, the keyset
  prefs file is deleted and the build is retried once. A second failure
  crashes by design — there is no recoverable state at that point.

## Consumer wiring required in `:app`

`:app`'s `<application>` tag must reference the backup-exclusion XML files
provided by this module:

```xml
<application
    android:dataExtractionRules="@xml/auth_data_extraction_rules"
    android:fullBackupContent="@xml/auth_backup_rules"
    ...>
```

These exclude `datastore/oauth_session.pb` and
`nubecita_core_auth_keyset.xml` from Auto Backup and device-to-device
transfer, so the session ciphertext never leaves the device. Resource
merging happens automatically — the XML files live here, the references
live in `:app`'s manifest.

## Testing

JVM unit tests (`./gradlew :core:auth:testDebugUnitTest`) cover serializer
round-trip, store contract (`save`/`load`/`clear`), corruption tolerance,
and file-backed DataStore round-trip without Keystore.

On-device scenarios (plaintext-check on disk, process-kill survival,
real-Keystore round trip, backup-exclusion verification via `adb bmgr`) are
tracked under bd `nubecita-16a`.

## Scope boundaries

In: session persistence, Hilt bindings for the store.
Out (future additions under their own bd issues):

- `AtOAuth` Hilt provider (`nubecita-4g7`)
- `AuthRepository` (replacement for the superseded `nubecita-ym3`)
- Deep-link callback parser (`nubecita-ck0`)
- Login UI — lives outside `:core:*` in a feature module / `:app`
