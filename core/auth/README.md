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

### Failure modes

| Where | What happens | Result for callers |
|---|---|---|
| `EncryptedOAuthSessionStore.load()` catches `IOException`, `GeneralSecurityException`, `SerializationException` from the DataStore flow | Store degrades to "no session." | `load()` returns `null`; the app falls back to a signed-out state. |
| `AuthDataStoreModule` catches `GeneralSecurityException` at keyset bootstrap (covers `KeyPermanentlyInvalidatedException`) | Deletes `nubecita_core_auth_keyset` SharedPreferences and retries the build once. If the retry succeeds, any previously persisted session is lost and the store degrades to "no session" on the next `load()`. | Transparent recovery on first boot after a biometric/lockscreen reset. |
| Same layer, **second** consecutive `GeneralSecurityException` at keyset bootstrap | Propagates from the `@Provides` function, which fails Hilt graph construction at first injection. | **App crashes.** This is an unrecoverable environmental failure (Keystore unusable, corrupted keyset material); silently swallowing it would mean silently dropping future writes. If you hit this in practice, the device Keystore is in a state no app-side recovery can paper over. |

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
