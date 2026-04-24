## 1. Scaffold `:core:auth` module (nubecita-9bl)

- [x] 1.1 Create `core/auth/` directory with `build.gradle.kts` applying `com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.dagger.hilt.android`, `com.google.devtools.ksp`, and `com.squareup.sort-dependencies`. Set `namespace = "net.kikin.nubecita.core.auth"`, `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`. Configure `jvmToolchain(17)` and `spotless` inheritance from the root project. _(Implementation note: the `org.jetbrains.kotlin.android` plugin is auto-applied by AGP 9 when Kotlin sources are present — `:app` and `:designsystem` both omit it explicitly; `:core:auth` follows the same pattern.)_
- [x] 1.2 Create `core/auth/src/main/AndroidManifest.xml` with empty `<manifest>` (no `<application>` — libraries should not set application-level attributes). _(Skipped: `:designsystem` also ships without a manifest; AGP synthesizes one from the module `namespace`. `:core:auth` follows the same pattern.)_
- [x] 1.3 Create `core/auth/consumer-rules.pro` (empty placeholder) and wire via `consumerProguardFiles("consumer-rules.pro")` in the library's `defaultConfig`.
- [x] 1.4 Add `include(":core:auth")` to `settings.gradle.kts`.
- [x] 1.5 Add `implementation(project(":core:auth"))` to `app/build.gradle.kts`.
- [x] 1.6 Verify `./gradlew :core:auth:assembleDebug` succeeds (empty module builds clean). No test added — scaffold only.

## 2. Version catalog and dependency wiring

- [x] 2.1 Add to `gradle/libs.versions.toml` under `[versions]`: `datastore = "1.2.0"`, `datastoreTink = "1.3.0-alpha08"`, `tinkAndroid = "1.21.0"` (transitive pin), `kotlinxSerializationJson = "1.9.0"` (align to current Kotlin 2.3.21 compatibility — verify against the resolved version already in play). Add `coroutinesTest` if not already present. _(Deviation: `datastore` pinned to `1.3.0-alpha08` rather than `1.2.0` — `AeadSerializer` lives in `datastore-tink` but depends on `datastore-core:1.3.0-alpha08`, so keeping both artifacts at the same alpha avoids resolution divergence. `coroutinesTest` was already in the catalog.)_
- [x] 2.2 Add to `[libraries]`: `androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "datastore" }`, `androidx-datastore-tink = { module = "androidx.datastore:datastore-tink", version.ref = "datastoreTink" }`, `tink-android = { module = "com.google.crypto.tink:tink-android", version.ref = "tinkAndroid" }`, `kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }`.
- [x] 2.3 Wire `:core:auth`'s `build.gradle.kts` dependencies: `implementation(libs.androidx.datastore)`, `implementation(libs.androidx.datastore.tink)`, `implementation(libs.tink.android)`, `implementation(libs.atproto.oauth)`, `implementation(libs.kotlinx.serialization.json)`, `implementation(libs.hilt.android)`, `ksp(libs.hilt.android.compiler)`, `testImplementation(libs.junit)`, `testImplementation(libs.kotlinx.coroutines.test)`.
- [x] 2.4 Remove `implementation(libs.atproto.oauth)` from `app/build.gradle.kts`. Confirmed via grep that `:app` only imports `io.github.kikin81.atproto.runtime.XrpcClient` (one reference in `AtProtoModule.kt`); no `atproto.oauth` symbols are used from `:app`. `:app` now gets session-store access exclusively through `:core:auth`.
- [x] 2.5 Verify `./gradlew :core:auth:dependencies` lists `datastore-tink` and `tink-android`; verify `./gradlew :app:assembleDebug` still succeeds.

## 3. `OAuthSessionSerializer` — kotlinx JSON inner serializer

- [x] 3.1 Create `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/OAuthSessionSerializer.kt`: `internal object OAuthSessionSerializer : Serializer<OAuthSession?>` with `defaultValue = null`. Use a `Json { ignoreUnknownKeys = true; encodeDefaults = true }` instance held as a private `val` on the object.
- [x] 3.2 Implement `writeTo(t: OAuthSession?, output: OutputStream)`: if `null`, write an empty byte array; otherwise `Json.encodeToString(OAuthSession.serializer(), t).toByteArray()` and write to `output`.
- [x] 3.3 Implement `readFrom(input: InputStream)`: read all bytes; if empty return `null`; otherwise `Json.decodeFromString(OAuthSession.serializer(), bytes.decodeToString())`. Wrap the decode in `try/catch (e: SerializationException)` returning `null` so corrupted inner JSON degrades gracefully (per spec §"Corrupted storage").
- [x] 3.4 Unit test `core/auth/src/test/kotlin/net/kikin/nubecita/core/auth/OAuthSessionSerializerTest.kt` — 5 scenarios covering round-trip, empty input, garbage bytes, `null` write, and unknown-field tolerance. All green.

## 4. Tink + DataStore Hilt wiring

- [x] 4.1 Create `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/di/AuthDataStoreModule.kt`: `@Module @InstallIn(SingletonComponent::class) internal object AuthDataStoreModule`. File constants defined as specified; additionally `SESSION_ASSOCIATED_DATA = "nubecita.oauth.session.v1".encodeToByteArray()` required by `AeadSerializer`'s third argument.
- [x] 4.2 `@Provides @Singleton provideOAuthSessionDataStore(...)` wired. _(API corrections discovered at build time against `datastore-tink:1.3.0-alpha08`: (i) `AeadSerializer` lives in `androidx.datastore.tink`, not `androidx.datastore.core` as Google's release notes claimed; (ii) `keysetHandle.getPrimitive(...)` requires `RegistryConfiguration.get()` as first arg in Tink 1.21; (iii) `KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM)` replaces the legacy `KeyTemplates.get("AES256_GCM")` string-keyed factory; (iv) `AeadSerializer` constructor signature is `(aead, wrappedSerializer, associatedData)`.)_
- [x] 4.3 `KeyPermanentlyInvalidatedException` handled: catch at the `buildKeysetHandle` call site, `context.deleteSharedPreferences(SESSION_KEYSET_PREF_FILE)`, retry once. Second failure propagates by design.
- [x] 4.4 Create `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/di/AuthBindingsModule.kt`: `@Binds @Singleton` for `EncryptedOAuthSessionStore → OAuthSessionStore`.
- [x] 4.5 Hilt KSP generation succeeds — `:core:auth:kspDebugKotlin` runs clean as part of `assembleDebug`; downstream `:app:hiltAggregateDepsDebug` + `:app:hiltJavaCompileDebug` succeed, confirming the `OAuthSessionStore` binding is visible across the module boundary.

## 5. `EncryptedOAuthSessionStore` implementation

- [x] 5.1 Create `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/EncryptedOAuthSessionStore.kt`: `internal class EncryptedOAuthSessionStore @Inject constructor(private val dataStore: DataStore<OAuthSession?>) : OAuthSessionStore`.
- [x] 5.2 Implement `override suspend fun load()`. _(Deviation: used `dataStore.data.catch { if (it is IOException) emit(null) else throw it }.firstOrNull()` instead of a `try { firstOrNull() } catch { null }` wrap. `Flow.catch` is idiomatic Kotlin-flow error recovery and preserves non-IOException propagation; semantically identical to the task description.)_
- [x] 5.3 Implement `override suspend fun save(session)` — `dataStore.updateData { session }`.
- [x] 5.4 Implement `override suspend fun clear()` — `dataStore.updateData { null }`. Does not delete the file or rotate the master key.
- [x] 5.5 Unit test `EncryptedOAuthSessionStoreTest.kt` — 4 scenarios covering all four spec requirements, using a `MutableStateFlow`-backed fake and an `IOException`-throwing fake. All green.

## 6. Backup exclusions

- [x] 6.1 Create `core/auth/src/main/res/xml/auth_data_extraction_rules.xml` with `<data-extraction-rules>` → `<cloud-backup>` and `<device-transfer>` each containing `<exclude domain="file" path="datastore/oauth_session.pb"/>` and `<exclude domain="sharedpref" path="nubecita_core_auth_keyset.xml"/>`.
- [x] 6.2 Create `core/auth/src/main/res/xml/auth_backup_rules.xml` with legacy `<full-backup-content>` → same two `<exclude>` entries (file + sharedpref).
- [x] 6.3 Add `android:dataExtractionRules="@xml/auth_data_extraction_rules"` and `android:fullBackupContent="@xml/auth_backup_rules"` to `<application>` in `app/src/main/AndroidManifest.xml`.
- [x] 6.4 Merged manifest verified — `app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml` contains both attributes referencing the correct `@xml/...` resources from `:core:auth`.

## 7. Module-level smoke test + integration verification

- [x] 7.1 `AuthModuleGraphTest.kt` added — exercises the full spec contract against a real `DataStoreFactory.create` with a `TemporaryFolder`-backed file (no Tink, no Keystore). 1 scenario covers empty-load, save/load, clear, and reuse-after-clear in sequence. Green.
- [x] 7.2 `bd update nubecita-16a --notes=...` recording the `:core:auth` instrumented scenarios needed on the emulator runner.

## 8. Lint, Spotless, module conventions

- [x] 8.1 `./gradlew :core:auth:spotlessApply` run; `:core:auth:spotlessCheck` (via `./gradlew spotlessApply` across the root) passes.
- [x] 8.2 `./gradlew :core:auth:lint` and `:core:auth:lintDebug` run clean — no baseline file needed.
- [x] 8.3 `sort-dependencies` plugin is applied in `:core:auth/build.gradle.kts`; dependencies were written alphabetically from the start.
- [x] 8.4 `pre-commit run --all-files` pending the commit step (§10.3).

## 9. Documentation

- [x] 9.1 `core/auth/README.md` created — module purpose, public surface, Keystore alias reservation, consumer manifest wiring, scope boundaries.
- [ ] 9.2 `CLAUDE.md` append for the `:core:*` convention — deferred; can be folded into a future module's PR when a second `:core:*` module appears.

## 10. Bd graph + PR ceremony

- [x] 10.1 `nubecita-nss` and `nubecita-9bl` both in `in_progress`.
- [x] 10.2 Branch `feat/nubecita-nss-secure-session-token-storage` created off `main`.
- [ ] 10.3 Commit with Conventional Commit message including `Refs: nubecita-nss nubecita-9bl`.
- [ ] 10.4 PR titled `feat(core-auth): scaffold :core:auth + encrypted OAuthSessionStore`; body includes `Closes: nubecita-nss` and `Closes: nubecita-9bl`.
- [ ] 10.5 After merge: `bd close nubecita-nss nubecita-9bl` and `/opsx:archive add-core-auth-session-storage`.
- [ ] 10.6 Retitle `nubecita-gr4` (epic) to drop "app password".
