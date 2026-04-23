## 1. Dependency wiring

- [x] 1.1 Run Sonatype vulnerability + license + policy check on `pkg:maven/io.ktor/ktor-client-cio@3.4.0`; stop and flag to the user if it's malicious, end-of-life, or policy-non-compliant.
- [ ] 1.2 Add `ktor = "3.4.0"` under `[versions]` in `gradle/libs.versions.toml` (placed in artifact-name alphabetical order per existing convention).
- [ ] 1.3 Add `ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }` under `[libraries]` in `gradle/libs.versions.toml`.
- [ ] 1.4 Add `implementation(libs.ktor.client.cio)` to `app/build.gradle.kts` in the existing `dependencies { ... }` block (sort-dependencies plugin will normalize order on the first build).
- [ ] 1.5 Run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and confirm `io.ktor:ktor-client-cio:3.0.0` resolves; no other `io.ktor:ktor-client-*` engine artifact appears (verifies `atproto-networking` spec: "CIO engine is the sole Ktor engine on the classpath").

## 2. Hilt module

- [ ] 2.1 Create `app/src/main/java/net/kikin/nubecita/data/AtProtoModule.kt` as an `object` annotated `@Module` + `@InstallIn(SingletonComponent::class)` with two `@Provides @Singleton` functions: `provideHttpClient(): HttpClient = HttpClient(CIO)` and `provideAnonymousXrpcClient(httpClient: HttpClient): XrpcClient = XrpcClient(baseUrl = APPVIEW_URL, httpClient = httpClient)`.
- [ ] 2.2 Add `private const val APPVIEW_URL = "https://public.api.bsky.app"` at file scope with a one-line comment explaining that this is the public AppView gateway serving unauthenticated lexicons (WHY, not WHAT — per `CLAUDE.md` comment rule).
- [ ] 2.3 Run `./gradlew :app:assembleDebug` and confirm Hilt compile-time validation passes (verifies `atproto-networking` spec: "Compile-time graph resolution" and `dependency-injection` delta: "Module is annotated and installed").

## 3. Unit test

- [ ] 3.1 Create `app/src/test/java/net/kikin/nubecita/data/AtProtoClientTest.kt` — a JUnit 4 test class with a single `@Test fun resolveHandle_bskyApp_returnsDid()` that wraps a `runBlocking { ... }` body, constructs `XrpcClient` locally with the same `baseUrl = "https://public.api.bsky.app"` + `HttpClient(CIO)` wiring as the module (no Hilt on the test path, per existing `dependency-injection` spec), calls `IdentityService(client).resolveHandle(ResolveHandleRequest(handle = Handle("bsky.app")))`, and asserts the returned `response.did.value` starts with `"did:plc:"`.
- [ ] 3.2 Run `./gradlew :app:testDebugUnitTest --tests 'net.kikin.nubecita.data.AtProtoClientTest'` and confirm it passes (verifies `atproto-networking` spec: "Feature code resolves a handle" scenario, substituting direct construction for the `@Inject` path per the documented test-strategy trade-off).

## 4. Style, lint, and validation gates

- [ ] 4.1 Run `./gradlew spotlessCheck sortDependencies lint` and fix any findings (Spotless/ktlint may reorder imports, `sortDependencies` may normalize the `implementation(libs.ktor.client.cio)` placement).
- [ ] 4.2 Run `openspec validate add-atproto-hilt-module` and confirm the change is still valid.
- [ ] 4.3 Run `git grep -n 'XrpcClient(' app/src/main` and confirm the only match is inside `AtProtoModule.kt` (verifies `atproto-networking` spec: "No inline XrpcClient construction in feature code").

## 5. PR and archive

- [ ] 5.1 Commit each logical step with Conventional Commit subjects and `Refs: nubecita-ipa` in the footer.
- [ ] 5.2 Push the branch and open a PR with `Closes: nubecita-ipa` in the body; PR title is the first commit's subject (squash-merge convention).
- [ ] 5.3 After the PR merges, on `main`: run `openspec archive add-atproto-hilt-module` to fold the delta specs into `openspec/specs/` and move the change folder under `openspec/changes/archive/`, then close `nubecita-ipa` in beads.
