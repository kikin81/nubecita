## Why

Nubecita's feature ViewModels and repositories need to call AT Protocol lexicons (`com.atproto.identity.resolveHandle`, feed / profile lookups via the AppView) long before user login lands. Every feature that hand-rolls its own `XrpcClient` would duplicate Ktor engine choice, base URL selection, and lifecycle. The atproto-kotlin library is already on the classpath after `nubecita-6il`; we just need a Hilt-provided anonymous client so feature code can `@Inject XrpcClient` and move on.

The authenticated path (OAuth-backed `XrpcClient` via `AtOAuth.createClient()`) lives in a separate follow-up (`nubecita-4g7`) because it blocks on a secure session store (`nubecita-nss`) and a hosted `client-metadata.json` (`nubecita-e16`). Splitting the anonymous and authenticated bindings means feature VMs that only need public reads can start landing now.

## What Changes

- Add `io.ktor:ktor-client-okhttp:3.4.0` to the version catalog and `:app` module (the atproto runtime transitively pulls Ktor core but not an engine). Version matches the `ktor-client-core:3.4.0` the atproto-kotlin 5.0.1 runtime already resolves transitively, so the engine aligns with the rest of the Ktor graph without forcing Gradle conflict resolution. OkHttp chosen over CIO for Android-specific HTTP/2 maturity, connection pooling under cellular/Wi-Fi transitions, and future alignment with Coil (which also uses OkHttp).
- New Hilt module `net.kikin.nubecita.data.AtProtoModule` (object, `@InstallIn(SingletonComponent::class)`) with two `@Singleton` providers:
  - `HttpClient` built on the Ktor OkHttp engine (`HttpClient(OkHttp)`).
  - `XrpcClient` constructed with `baseUrl = "https://public.api.bsky.app"` and the shared `HttpClient` — no auth provider (library default is `NoAuth`).
- New JUnit 4 unit test `AtProtoClientTest.resolveHandle_bskyApp_returnsDid` that mirrors the module's wiring locally (no Hilt on the test path, per existing DI spec), calls `IdentityService(client).resolveHandle(ResolveHandleRequest(Handle("bsky.app")))`, and asserts a `did:plc:…` DID comes back. Network-dependent — offline CI runs will fail; acceptable for a smoke test.

### Non-goals

- **No authenticated `XrpcClient`, `AtOAuth`, or `OAuthSessionStore` bindings.** Deferred to `nubecita-4g7` which is correctly blocked on `nubecita-nss` + `nubecita-e16`.
- **No `@HiltAndroidTest` instrumented test** that literally `@Inject`s `XrpcClient`. Instrumented tests are resource-heavy and must not run on every PR — the CI gating (via `ReactiveCircus/android-emulator-runner`) is tracked as `nubecita-16a` and runs only on `push` to `main`, `workflow_dispatch`, and a PR label. Hilt compile-time graph validation during `./gradlew assembleDebug` covers the "bindings resolve" half until then.
- **No multiple base URLs / BuildConfig-flavored endpoints.** `public.api.bsky.app` is hard-coded for now; moving the URL to a build-time source becomes relevant only when authenticated flows land (per-user PDS routing) — handled in `nubecita-4g7`.
- **No Ktor plugins beyond the engine.** No logging interceptor, no content-negotiation install — the library's `XrpcClient` owns JSON serialization internally; adding plugins on the shared `HttpClient` risks double-encoding.

## Capabilities

### New Capabilities
- `atproto-networking`: Defines the contract for how the app reaches the AT Protocol network — the `XrpcClient` surface, the shared Ktor `HttpClient` + engine, base-URL policy for anonymous (public AppView) versus authenticated (per-user PDS) calls, and the invariant that feature code never constructs these types directly.

### Modified Capabilities
- `dependency-injection`: Extends the Hilt module inventory with `AtProtoModule`. Adds a requirement that `XrpcClient` and `HttpClient` are `@Singleton`-scoped and installed in `SingletonComponent`, and that consumers obtain them via constructor `@Inject` (never inline construction).

## Impact

- **Code**: One new file (`app/src/main/java/net/kikin/nubecita/data/AtProtoModule.kt`, ~25 LOC) and one new test file (`app/src/test/java/net/kikin/nubecita/data/AtProtoClientTest.kt`, ~20 LOC).
- **Dependencies**: `io.ktor:ktor-client-okhttp:3.4.0` added via `libs.versions.toml` (Apache-2.0, JetBrains-maintained; brings in Square's OkHttp transitively). Version matches the `ktor-client-core:3.4.0` the atproto-kotlin 5.0.1 runtime pulls transitively. Sonatype check: policy-compliant, not malicious, not EOL, no vulnerabilities.
- **Tests**: One JVM unit test; no screenshot-test or instrumented-test impact. Offline CI runs of the new test will fail (network dependency) — acceptable until `nubecita-16a` lands the instrumented replacement.
- **Downstream unblocks**: `nubecita-4g7` (authenticated Hilt bindings), `nubecita-16a` (instrumented test CI), and any feature VM that needs public AppView reads (e.g., `nubecita-4u5` FeedContract, handle → DID resolution flows).
