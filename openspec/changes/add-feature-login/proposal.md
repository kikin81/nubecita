## Why

Nubecita needs a sign-in screen that kicks off AT Protocol OAuth. This change lands the first feature module (`:feature:login`) and the Hilt surface for the `AtOAuth` flow orchestrator, establishing two patterns we will repeat for every future feature:

1. **Feature modules follow Google's Navigation 3 `api`/`impl` split** — a small `:feature:<name>:api` module holding only `@Serializable` `NavKey` types, and a `:feature:<name>:impl` module holding composables, ViewModels, and a Hilt `@Module` that contributes an `EntryProviderInstaller` via `@IntoSet` multibinding. `:app` collects the set and hands it to `NavDisplay`. This is the documented Google pattern (developer.android.com/guide/navigation/navigation-3/modularize, `android/nav3-recipes` sample) and the one NiA migrated to in December 2025.
2. **OAuth-side DI lives in `:core:auth`** — the module gains `AtOAuth` and `AuthRepository` Hilt bindings, so feature code can `@Inject AuthRepository` to drive `beginLogin` without knowing about Tink, DataStore, or the `atproto-oauth` library internals.

The user-facing problem: today the app opens directly into a placeholder `MainScreen`. There is no way to sign in, and no downstream feature (feed, profile, composer) can be built against a real authenticated `XrpcClient`.

`nubecita-uf5` (login screen + MVI) and `nubecita-4g7` (Hilt bindings for `AtOAuth`) are bundled into one PR because 4g7 is mechanically small (one `@Provides` function + a binding) and without it uf5's `LoginViewModel` has nothing to inject. Splitting the two would force a round-trip that slows both.

`nubecita-ck0` (Custom Tab launch + `MainActivity.onNewIntent` deep-link callback + `completeLogin` wiring) is explicitly **deferred** to its own PR. This change ships the `LaunchCustomTab` effect as a typed MVI effect but does not wire MainActivity to consume it yet.

## What Changes

- **New modules:**
  - `:feature:login:api` — `@Serializable data object Login : NavKey` only. Zero Compose / Hilt / coroutines imports.
  - `:feature:login:impl` — `LoginContract` (State/Event/Effect), `LoginViewModel`, `LoginScreen`, plus a Hilt `@Module` contributing an `EntryProviderInstaller` for `Login`.
- **`:core:auth` additions:**
  - `AuthRepository` interface exposing `suspend fun beginLogin(handle: String): Result<String>` (returns authorization URL). Shape designed so `completeLogin`, `currentSession`, and `signOut` slot in later without re-architecting consumers.
  - `DefaultAuthRepository` internal implementation delegating to `AtOAuth`.
  - `AtOAuthModule` Hilt module providing `AtOAuth` (reads `clientMetadataUrl` from `BuildConfig`, consumes the injected `OAuthSessionStore` and a shared `HttpClient`) and binding `AuthRepository → DefaultAuthRepository`.
- **`:app` additions:**
  - `EntryProviderInstaller` typealias declaration (lives in `:app` until a second feature module justifies a shared `:core:navigation`).
  - Hilt `EntryPoint` for collecting the `Set<EntryProviderInstaller>` multibinding into `MainNavigation`.
  - `MainNavigation` rewrites `NavDisplay` to wire all three required `NavEntryDecorator`s (scene-setup, saveable, viewmodel-store) and to spread the installer set into `entryProvider { }`.
  - New `Main` NavKey stays in `:app` (not yet promoted to a feature module; it's a placeholder until `nubecita-e9s` adds a real home graph).
  - `BuildConfig` field `OAUTH_CLIENT_METADATA_URL`, pre-populated with the development GitHub Pages URL shipped under `nubecita-e16` (`https://kikin81.github.io/nubecita/oauth/client-metadata.json`).
- **Version catalog:** add `androidx-navigation3-entry-interop` or equivalent if required for the new decorator APIs; pin exact versions when implementing.

## Capabilities

### New Capabilities

- `feature-login`: the login experience — a single screen that takes a Bluesky handle, calls `AuthRepository.beginLogin`, and emits a `LaunchCustomTab(url)` effect. Covers the UI, the MVI contract, the Nav 3 api/impl module split, and the Hilt-contributed `NavEntry`. Does **not** cover Custom Tab launching in MainActivity, deep-link callback handling, or post-login navigation to the feed.
- `core-auth-oauth-bindings`: Hilt bindings for the OAuth flow orchestrator — `AtOAuth` (constructed from a BuildConfig-sourced client metadata URL, the injected `OAuthSessionStore`, and a shared Ktor `HttpClient`) and a narrow `AuthRepository` interface that consumers inject instead of the concrete library class. Distinct capability from `core-auth-session-storage` (which owns the session-storage layer below this); grows as `completeLogin`, `currentSession`, and `signOut` are added in future PRs.

### Modified Capabilities

_None._ `core-auth-session-storage`'s existing requirements (OAuthSessionStore Hilt binding, save/load, sign-out clearing, encryption, corruption tolerance, backup exclusion, `:app` dep isolation) are unchanged.

## Non-Goals

- **Custom Tab launching** — tracked under `nubecita-ck0`. This change emits the `LaunchCustomTab(url)` effect; wiring `CustomTabsIntent.launchUrl(...)` + `MainActivity.onNewIntent` + the redirect callback lives in ck0.
- **`completeLogin` path** — same. `AuthRepository.completeLogin` is intentionally not on the interface yet; adding it is part of ck0.
- **Session-aware routing / auth-gated navigation** — tracked under `nubecita-30c` (splash + auth-gated routing). This change lets a user *start* login; the feed/home landing after success is out of scope.
- **A real `:feature:home` module.** The `Main` NavKey stays in `:app` as a placeholder destination until `nubecita-e9s` (top-level Navigation graph) extracts it into a proper feature module.
- **A shared `:core:navigation` module.** Per agent research and NiA precedent, the `EntryProviderInstaller` typealias lives in `:app` until cross-feature concerns (deep-link router, `SceneStrategy` registry, typed `NavKey` sealed root) actually justify the module.
- **MockK / Turbine / JUnit 5 adoption** — tracked under `nubecita-e1a`. Tests in this change use the current JUnit 4 + coroutines-test + fake-interface pattern, matching `MainScreenViewModelTest`.
- **`AndroidManifest.xml` intent-filter for the `net.kikin.nubecita` redirect scheme** — `nubecita-ck0`.
- **Dynamic color / Material You tuning of the login screen.** Uses the already-shipped `NubecitaTheme` tokens as-is.

## Deviations from Baseline

- **New feature-module convention.** CLAUDE.md's module list today is `:app` + `:designsystem` + `:core:auth`. This change adds the `:feature:*:api` / `:feature:*:impl` split. CLAUDE.md will gain a short note under "Conventions" naming the pattern so the next feature (home, feed, profile) doesn't rediscover it.
- **`EntryProviderInstaller` typealias in `:app`.** The long-term home is a shared module, but the Nav 3 community guidance (including `nav3-recipes`) is explicit that a `:core:navigation` module is not required on day one. We follow that guidance and note in CLAUDE.md where it lives today so a future author knows to relocate it when the second feature module arrives.

## Impact

- **New modules** at `feature/login/api/` and `feature/login/impl/` with manifests, build files, and namespaces `net.kikin.nubecita.feature.login.api` / `net.kikin.nubecita.feature.login.impl`.
- **`:core:auth` build.gradle.kts** gains `implementation(libs.ktor.client.okhttp)` (or a shared HTTP client dep — TBD during design) so `AtOAuth` can be constructed. Current public API surface is unchanged.
- **`:app` build.gradle.kts** gains `implementation(project(":feature:login:impl"))` and a `buildConfigField` declaration.
- **`:app` Hilt graph** gains `Set<EntryProviderInstaller>` as a multibound dependency. No existing bindings collide.
- **`settings.gradle.kts`:** new `include(":feature:login:api")` and `include(":feature:login:impl")`.
- **`app/src/main/AndroidManifest.xml`:** unchanged in this PR. The `net.kikin.nubecita` deep-link intent filter lands with ck0.
- **Downstream unblocks:** `nubecita-ck0` (Custom Tab launching + deep-link callback) and `nubecita-30c` (splash / auth-gated routing) can both proceed after this change lands.
