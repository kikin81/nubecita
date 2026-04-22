# atproto-kotlin Hilt module (anonymous client)

- **bd issue:** `nubecita-ipa`
- **Date:** 2026-04-22
- **Library version:** `io.github.kikin81.atproto:{runtime,models,oauth}:5.0.0`

## Goal

Wire a Hilt-provided anonymous `XrpcClient` into the `:app` module so any
feature `ViewModel` / `Repository` can call AT Protocol lexicons that
don't require authentication (`com.atproto.identity.resolveHandle`,
public profile / feed lookups via the AppView, etc.) without
constructing a client themselves.

## Scope

**In scope**

- A new Hilt module, `AtProtoModule`, exposing:
  - `HttpClient` (Ktor CIO engine) as `@Singleton`
  - `XrpcClient` as `@Singleton`, constructed with the public AppView
    base URL and no auth provider (the library's `NoAuth` default)
- Ktor CIO engine dependency wired into the version catalog and
  `:app/build.gradle.kts`
- A JUnit 4 unit test in `app/src/test/` that mirrors the module's
  wiring manually, calls `IdentityService(client).resolveHandle(...)`
  against `bsky.app`, and asserts a valid DID comes back

**Out of scope** (follow-up bd issues)

- Authenticated `XrpcClient` / `AtOAuth` bindings — new bd, blocked on
  `nubecita-nss` (secure session store) and `nubecita-e16` (hosted
  client metadata JSON)
- Android instrumented test via `@HiltAndroidTest` that literally
  `@Inject`s `XrpcClient` — new bd for CI instrumented-test
  infrastructure using `ReactiveCircus/android-emulator-runner`

## Design

### Module

File: `app/src/main/java/net/kikin/nubecita/data/AtProtoModule.kt` —
sibling of the existing `DataModule.kt` so atproto-specific bindings
follow the established `data/` convention.

```kotlin
package net.kikin.nubecita.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AtProtoModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(CIO)

    @Provides
    @Singleton
    fun provideAnonymousXrpcClient(httpClient: HttpClient): XrpcClient =
        XrpcClient(baseUrl = APPVIEW_URL, httpClient = httpClient)
}

// Public AppView — serves unauthenticated lexicons like
// com.atproto.identity.resolveHandle without per-user PDS routing.
private const val APPVIEW_URL = "https://public.api.bsky.app"
```

The `APPVIEW_URL` comment is the only commentary: it explains *why*
this URL (not a user's PDS), which is the non-obvious choice a future
reader would question. Everything else is self-documenting per
`CLAUDE.md`'s comment rule.

### Gradle

Version catalog (`gradle/libs.versions.toml`):

```toml
[versions]
ktor = "3.0.0"   # matches atproto-setup skill's recommended engine version

[libraries]
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
```

App module (`app/build.gradle.kts`):

```kotlin
dependencies {
    // ... existing
    implementation(libs.ktor.client.cio)
}
```

Ktor 3.0.0 is what the atproto-kotlin `atproto-setup` skill
recommends (the library was built and validated against that
version). A sonatype check will run at implementation time to confirm
no vulnerabilities or policy violations before the coord lands.

### Test

File: `app/src/test/java/net/kikin/nubecita/data/AtProtoClientTest.kt`

Plain JUnit 4. Builds `XrpcClient` with the same `HttpClient(CIO)` +
`APPVIEW_URL` shape as the module (hard-coded locally so the test
doesn't need a Hilt test graph), wraps in `IdentityService`, calls
`resolveHandle(ResolveHandleRequest(Handle("bsky.app")))` inside
`runBlocking`, and asserts the returned DID is non-empty and starts
with `did:plc:`.

```kotlin
@Test
fun resolveHandle_bskyApp_returnsDid() = runBlocking {
    val client = XrpcClient(
        baseUrl = "https://public.api.bsky.app",
        httpClient = HttpClient(CIO),
    )
    val response = IdentityService(client).resolveHandle(
        ResolveHandleRequest(handle = Handle("bsky.app")),
    )
    assertTrue(response.did.value.startsWith("did:plc:"))
}
```

**Why not `@HiltAndroidTest`:** that requires an instrumented test
and live Android runtime. Instrumented tests are resource-intensive
and must not run on every PR — they belong in the `android-emulator-runner`
follow-up bd that will gate them on `push` to `main`,
`workflow_dispatch`, and a label trigger. Compile-time Hilt graph
validation (every `./gradlew assembleDebug`) catches wiring errors
before runtime; the unit test proves the library call actually
reaches Bluesky.

**Network dependency:** acknowledged. Offline test runs will fail.
Acceptable for the "throwaway test" phrasing in the bd acceptance
criterion. If flakiness bites, the test can be `@Ignore`'d in favor of
the instrumented-test equivalent once that lands.

## Acceptance criterion (updated)

Replaces the bd's original wording (which predates the scope split):

> - `./gradlew assembleDebug` succeeds (Hilt compile-time validates the
>   `XrpcClient` + `HttpClient` bindings).
> - `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
>   shows `io.ktor:ktor-client-cio:3.0.0` resolved.
> - `AtProtoClientTest.resolveHandle_bskyApp_returnsDid` passes against
>   the live `public.api.bsky.app` endpoint.
>
> Live `@Inject` validation defers to the instrumented-test follow-up.

## Follow-up bd issues

Created alongside this spec (one-line summaries, full descriptions in
beads):

1. **Hilt bindings for `AtOAuth` + authenticated `XrpcClient`** —
   provides the OAuth flow orchestrator and an authenticated client.
   Depends on `nubecita-nss` (encrypted session store) and
   `nubecita-e16` (hosted `client-metadata.json`). The `clientMetadataUrl`
   should be read from `BuildConfig` or a string resource so dev (GitHub
   Pages) and prod (custom domain) builds can diverge without editing
   the module.

2. **CI instrumented tests via `ReactiveCircus/android-emulator-runner`** —
   new GitHub Actions workflow that runs the `androidTest` source set
   on an emulator. Triggers: `push` to `main`, `workflow_dispatch`,
   and a PR label (e.g., `run-instrumented`). Explicitly not on every
   PR — instrumented tests are resource-intensive. First payload:
   migrate the existing `MainScreenTest.kt` under this gate, plus add
   a `@HiltAndroidTest` that `@Inject`s `XrpcClient` and calls
   `resolveHandle` end-to-end.

## Related bd updates

- `nubecita-e16` description updated to the two-step hosting plan:
  GitHub Pages at `kikin81.github.io/nubecita/oauth/client-metadata.json`
  (served from `docs/oauth/client-metadata.json`) for development;
  custom domain swap when production-bound. Swap is a one-way door —
  `client_id` must equal the hosting URL exactly, so changing it
  invalidates all existing sessions.
