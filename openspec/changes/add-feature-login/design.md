## Context

Nubecita today has `:app` + `:designsystem` + `:core:auth`. Navigation is a single-entry `MainNavigation` composable inside `:app` that drops `MainScreen` into a `NavDisplay` with one destination. There is no login UI, no `AtOAuth` Hilt binding, and no pattern for feature modules.

This change establishes the multimodule feature pattern we intend to repeat for every subsequent feature (home, feed, profile, compose, search). The shape is dictated by three constraints:

- **Nav 3 is the navigation library.** Already on `androidx.navigation3-runtime:1.1.1`. The Google-documented multimodule pattern is the `api`/`impl` split with `@IntoSet` multibinding of `EntryProviderInstaller` functions. Reference: developer.android.com/guide/navigation/navigation-3/modularize, `android/nav3-recipes`, NiA's Dec-2025 migration (PRs #1902 + #2003).
- **OAuth-only auth strategy.** Finalized 2026-04-24 (bd memory `auth-strategy-is-oauth-only-app-password-path`). Login UI is one handle field and a "Sign in with Bluesky" button — no password, no OAuth-vs-app-password toggle.
- **`nubecita-ck0` is a separate PR.** Custom Tab launch and deep-link callback handling live in MainActivity, not in the feature module. This change emits the `LaunchCustomTab(url)` effect but stops short of consuming it.

Stakeholders: `nubecita-4g7`'s `AtOAuth` Hilt binding is a hard prerequisite for any OAuth work, and `nubecita-uf5`'s UI is the first consumer. `nubecita-ck0` and `nubecita-30c` both gate on this landing.

## Goals / Non-Goals

**Goals:**

- Land the `:feature:*:api` / `:feature:*:impl` split with `@IntoSet EntryProviderInstaller` multibinding — the pattern every subsequent feature module will copy.
- Ship a compiled, testable `LoginViewModel` that drives `AuthRepository.beginLogin(handle)` and produces a `LaunchCustomTab(url)` effect.
- Hilt-bind `AtOAuth` inside `:core:auth` with `clientMetadataUrl` sourced from `BuildConfig`, keeping `:app` ignorant of atproto-oauth construction details.
- Introduce `AuthRepository` as the narrow domain seam the ViewModel depends on — clean unit-test shape today, open door for `completeLogin` / `signOut` tomorrow.
- Wire Nav 3's three required `NavEntryDecorator`s in `:app`'s `NavDisplay` so `hiltViewModel()` inside any NavEntry gets its own `ViewModelStore` and state survives recomposition / configuration change.

**Non-Goals:**

- `CustomTabsIntent.launchUrl(...)` wiring. The effect is defined here; the MainActivity collector is `ck0`.
- `AuthRepository.completeLogin(redirectUri)` — also `ck0`.
- `AuthRepository.currentSession: Flow<AuthState>` and `signOut()` — deferred to the splash/auth-gate work (`nubecita-30c`).
- Assisted injection / `@AssistedFactory` for the `LoginViewModel`. The `Login` NavKey carries no arguments, so assisted injection is premature.
- A `:core:navigation` module. Postponed until the second feature module lands (or until a cross-feature concern — deep-link router, `SceneStrategy` registry — actually appears).
- Any attempt to verify OAuth end-to-end against a real bsky.social authorization server. The metadata URL is live (from `nubecita-e16`) but real PAR requires Custom Tab launching (ck0) and is not testable in unit tests.
- Changing the existing `Main` destination. It stays a placeholder in `:app` until `nubecita-e9s` extracts a real home feature.

## Decisions

### 1. Two Gradle modules per feature: `:feature:login:api` and `:feature:login:impl`

**Decision:** `:feature:login:api` contains **only** the `@Serializable data object Login : NavKey` type (plus any future cross-feature contracts). `:feature:login:impl` contains `LoginContract`, `LoginViewModel`, `LoginScreen`, and the Hilt `@Module` with the `@IntoSet EntryProviderInstaller` binding. `:app` depends on `:feature:login:impl` via `implementation(project(...))`, which transitively pulls `:feature:login:api`. Other feature modules that need to link to the login destination depend only on `:feature:login:api`.

**Alternatives considered:**
- **Single `:feature:login` module.** Simpler on day one, but the `nav3-recipes` sample has an explicit issue (#205) documenting the failure mode: if the `NavKey` lives in `:impl`, cross-feature navigation to that destination forces a compile dependency on `:impl`, which defeats module isolation. The api/impl split is the documented workaround.
- **One `:feature:login:api` module, inline the impl in `:app`.** Keeps module count low but collapses the pattern we're explicitly establishing for future features. Feature `:impl` modules become the natural home for MVI contracts, screens, previews, and tests; dropping them into `:app` now would force a messy extraction later.

**Rationale:** Matches Google's documented Nav 3 modularization guide and NiA's post-migration layout. One-time cost: ~5 files of build-gradle + manifest boilerplate per feature module pair. Recouped immediately when the second feature module lands.

### 2. `EntryProviderInstaller` typealias in `:app`, not `:core:navigation`

**Decision:** Declare `typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit` in a `:app` source file (e.g., `app/src/main/java/net/kikin/nubecita/navigation/EntryProviderInstaller.kt`). `:app` also owns the Hilt `EntryPoint` that reads the `Set<@JvmSuppressWildcards EntryProviderInstaller>` multibinding.

**Alternatives considered:**
- **New `:core:navigation` module.** Over-engineering on day one per the agent research. The typealias is 1 line and `Navigator` (a back-stack holder) isn't needed yet. Would manufacture a module with one file just to justify its existence.
- **Stash in `:core:auth`.** Wrong module — `:core:auth` is the auth bounded context, not navigation plumbing. Would make the module do two things.

**Rationale:** NiA and `nav3-recipes` both keep these declarations in `:app` until cross-feature concerns force extraction. CLAUDE.md will gain a note documenting the intended future home so the next author doesn't duplicate the typealias elsewhere.

### 3. `AuthRepository` interface in `:core:auth`, wrapping `AtOAuth`

**Decision:** Introduce an interface

```kotlin
interface AuthRepository {
    suspend fun beginLogin(handle: String): Result<String>
}
```

with an internal `DefaultAuthRepository @Inject constructor(private val atOAuth: AtOAuth) : AuthRepository` that calls `atOAuth.beginLogin(handle)` wrapped in a `runCatching { }`. `:core:auth`'s `AuthBindingsModule` adds a `@Binds` for `AuthRepository → DefaultAuthRepository`.

**Alternatives considered:**
- **Inject `AtOAuth` directly into `LoginViewModel`.** `AtOAuth` is a concrete `class` (not open, not interface) in the atproto-oauth library, so it's not mockable without adding MockK. Would block VM unit tests until `nubecita-e1a` lands.
- **Introduce `AuthRepository` with the full `beginLogin` / `completeLogin` / `currentSession` / `signOut` surface today.** Speculative — we can't test methods we don't have consumers for, and the `completeLogin` implementation depends on a redirect URI that comes from `ck0`. Keep the interface narrow and grow it as we ship.

**Rationale:** Matches the existing `DataRepository` → `FakeRepository` pattern in `MainScreenViewModelTest`. Unit tests for `LoginViewModel` fake the one method we use. `Result<String>` instead of `String?` / exception-throwing is the established shape for VM-consumed operations.

### 4. `clientMetadataUrl` from `BuildConfig`, not hard-coded

**Decision:** `:app/build.gradle.kts` declares

```kotlin
android {
    defaultConfig {
        buildConfigField(
            "String",
            "OAUTH_CLIENT_METADATA_URL",
            "\"https://kikin81.github.io/nubecita/oauth/client-metadata.json\"",
        )
    }
    buildFeatures { buildConfig = true }
}
```

and `:core:auth`'s `AtOAuthModule` reads `net.kikin.nubecita.BuildConfig.OAUTH_CLIENT_METADATA_URL` to construct `AtOAuth`.

**Alternatives considered:**
- **Hard-code the dev URL.** Forces a code edit for the prod swap and every future environment split (beta channel, internal dogfood).
- **Android string resource.** Works, but resources are a UI-oriented bucket; URL config is build-time infrastructure.
- **Product flavors with per-flavor `buildConfigField`.** Justified when we have an actual prod URL to ship. Today we have one URL; set the groundwork with a `buildConfigField` and layer flavors on later.

**Rationale:** `nubecita-4g7`'s description explicitly names this as a "key constraint." `BuildConfig` fields are the idiomatic Android pattern for this kind of per-build-variant string and let the prod swap become a one-line override in a later change.

### 5. `@HiltViewModel` only, no assisted injection (yet)

**Decision:** `LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : MviViewModel<LoginState, LoginEvent, LoginEffect>()`. Retrieved in the composable via `hiltViewModel<LoginViewModel>()` (no assisted factory, no creation callback).

**Alternatives considered:**
- **`@AssistedInject` with the `Login` NavKey as the assisted argument.** This is Nav 3's recommended pattern for passing route arguments into ViewModels (per Google's "Passing arguments" recipe). But `Login` has no arguments today — it's a `data object`, not a `data class` with fields. Introducing assisted injection to pass an always-empty key is ceremony without benefit.

**Rationale:** We'll hit assisted injection the moment a feature destination carries arguments (e.g. `ProfileKey(val did: String)`). Document the pattern in CLAUDE.md so it's discoverable; don't pre-apply it.

### 6. `LaunchCustomTab(url)` as the login-success effect, not a boolean completion flag

**Decision:** `LoginEffect` is a sealed interface with variants `LaunchCustomTab(val url: String)` and `ShowError(val message: String)`. `NavigateToFeed` is **not** on the list — the navigation from "login authorized" → "on the feed" is gated by `completeLogin` which is out of scope.

**Alternatives considered:**
- **Effect-free model — the `LoginViewModel` launches the Custom Tab itself.** Requires injecting a `Context` or `ActivityResultLauncher` into the VM, which couples the VM to Android lifecycle classes. Rejected.
- **`NavigateToFeed` effect in this change, firing after `beginLogin` returns a URL.** Wrong semantics — the user hasn't authorized yet; we have an authorization URL, not a session.

**Rationale:** MVI convention — VM emits typed, one-shot effects; the composable (or MainActivity for deep-link-driven effects) is responsible for performing the side-effect. MainActivity's Custom Tab collector and deep-link handler are ck0's job; this change provides a cleanly-typed handoff point.

### 7. All three `NavEntryDecorator`s wired in `NavDisplay`

**Decision:** `MainNavigation`'s `NavDisplay` call passes

```kotlin
entryDecorators = listOf(
    rememberSceneSetupNavEntryDecorator(),
    rememberSavedStateNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator(),
)
```

**Alternatives considered:**
- **Wire only `rememberViewModelStoreNavEntryDecorator()`.** The minimum needed for `hiltViewModel()` to work. But omitting the other two breaks saved-state and scene restoration, documented as a common Nav 3 pitfall.

**Rationale:** Documented in the Nav 3 state-management guide as all three required together. The cost of wiring all three is zero lines beyond the decorator list; the cost of missing one surfaces as broken config-change behavior weeks later. Wire the full set now.

### 8. Don't add CLAUDE.md `:core:*` / `:feature:*` convention note in this PR

**Decision:** Defer the CLAUDE.md update to the PR that introduces the **second** feature module (any of home / feed / profile / composer).

**Alternatives considered:**
- **Add it now.** Documents the pattern earlier. Downside: the pattern isn't really validated until a second feature module actually uses it; anything we write now is prediction.

**Rationale:** Prior change (`add-core-auth-session-storage`) had a parallel task 9.2 ("document `:core:*` convention") that was similarly deferred. Consistent with that precedent; avoids premature documentation that might drift before the second feature lands.

## Risks / Trade-offs

- **Nav 3 decorator API names are evolving across alphas.** Mitigation: we're on stable 1.1.1; decorator factory names are `remember{SceneSetup|SavedState|ViewModelStore}NavEntryDecorator`. Verify exact signatures against the 1.1.1 artifacts during implementation. If any names have shifted, design-doc decision stands but task-level imports adapt.
- **`:feature:login:impl` transitive dep explosion.** `:app` implementing `:feature:login:impl` means `:app` transitively sees everything `:impl` exposes as `api()`. Mitigation: `:impl` declares `api(project(":feature:login:api"))` (deliberate — `:app` needs the `Login` key) and `implementation(...)` for everything else.
- **Hilt `@IntoSet` registration silently fails if `:app` doesn't depend on `:impl`.** Mitigation: documented in `nav3-recipes` issue #205; the module dependency is explicit in `:app/build.gradle.kts` and guarded by the integration scenario in the spec that asserts the `Login` entry is reachable at runtime.
- **`Result<String>` as the public API of `AuthRepository.beginLogin`.** Some teams avoid exposing `Result` across module boundaries because exceptions sneak through. Alternative would be a sealed `BeginLoginOutcome { Success(url); Failure(reason) }`. Accepted for v0 because the failure taxonomy isn't stable yet and we'd be inventing variants. Revisit when a second method joins the interface and we can shape a consistent error model across both.
- **No end-to-end authorization test in this PR.** Full PAR against a real auth server needs ck0's Custom Tab launch. Mitigation: the acceptance criteria here are VM + screen + DI graph correctness; E2E verification is scheduled explicitly in ck0.

## Migration Plan

Greenfield feature module and greenfield Hilt binding. No existing data, sessions, or UI to migrate. On the first run after this lands, `LoginScreen` is rendered but the Custom Tab launch is a no-op until ck0 ships.

## Open Questions

- **Exact Nav 3 decorator factory names on 1.1.1.** Verified against release artifacts at implementation time; no design implications.
- **Does `AtOAuth`'s constructor accept `HttpClient` directly or does it build one internally?** Confirmed at implementation time against the atproto-oauth 5.0.1 source at `~/code/kikinlex/oauth/src/main/kotlin/io/github/kikin81/atproto/oauth/AtOAuth.kt`. Design anticipates injection; if the constructor doesn't accept an `HttpClient`, `:core:auth`'s module provides one internally.
- **Should `:app` expose `OAUTH_CLIENT_METADATA_URL` or should `:core:auth` own the BuildConfig field?** Leaning toward `:app` owning it since URL is a build-variant concern and `:app` is the only module with application-specific configuration. Decided at implementation time based on which module actually needs the compile-time constant.
