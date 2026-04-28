## Context

Today `Main : NavKey` is the post-auth root. Its content is `MainScreen` — a placeholder showing greetings and a refresh button (`app/src/main/java/net/kikin/nubecita/ui/main/MainScreen.kt`). The outer navigation surface is wired with Navigation 3: `:app/Navigation.kt` hosts a `NavDisplay` reading from a Hilt `@Singleton Navigator` (`:core:common:navigation`); feature modules contribute entries via a `Set<EntryProviderInstaller>` multibinding collected through `NavigationEntryPoint`. Two feature modules exist today — `:feature:login:impl` and `:feature:feed:impl` — and both contribute through that single multibinding.

Three feature epics (search, chats, profile/settings) are blocked on having a top-level navigation surface to land in. A fourth (post-detail) is sized to assume that surface exists. The team also wants a tablet-/foldable-ready UX from day one rather than retrofitting form-factor adaptivity per feature.

The Nav3 team's official `multiple-backstacks` and `common-ui` recipes converge on a `TopLevelBackStack`-shape state holder: per-tab back stacks plus an active-tab pointer, flattened into one back stack for the `NavDisplay` to render. The recipe also gives process-death persistence "for free" via `rememberSerializable` + `rememberNavBackStack` — which conveniently subsumes the work tracked in **nubecita-3it**.

## Goals / Non-Goals

**Goals:**

- Adaptive multi-tab shell: `NavigationBar` on phones swaps to `NavigationRail` (or larger drawer) on foldables/tablets, automatically, via `NavigationSuiteScaffold`.
- Four top-level destinations: `Feed | Search | Chats | You`. Initial start tab: Feed.
- Per-tab back stacks preserved across tab switches.
- State survives configuration changes and process death.
- Logout cleanly drops all per-tab state.
- Stub-friendly: feature epics for search, chats, profile can land their `:impl` modules without restructuring the shell.
- Reinforce MVI: tab-internal navigation flows through `UiEffect`, not through navigator injected into VMs.

**Non-Goals:**

- Deep linking (intent handling, URL schemes, start-on-tab semantics). Separate epic.
- Settings as its own module. Stays a sub-route inside `:feature:profile:api` until growth justifies a split.
- Real `:feature:search:impl`, `:feature:chats:impl`, `:feature:profile:impl` modules. Only api stubs land here; `:app` provides placeholder Composables for empty tabs.
- Adaptive list-detail layouts (e.g. tablet two-pane post-detail). `NavigationSuiteScaffold` is chrome only; per-screen list-detail belongs to the post-detail epic.
- Refactoring `:core:common:Navigator` from `@Singleton` to `@ActivityRetainedScoped`. The Nav3 recipe uses `@ActivityRetainedScoped`; the repo's existing pattern is `@Singleton`. Out of scope; flagged for a follow-up bd issue.

## Decisions

### D1. Layered navigation: outer `Navigator` + inner `MainShellNavState`

Two separate state holders, one per layer:

- **Outer**: existing `:core:common:Navigator` Hilt singleton. Owns `Splash → Login → Main`. Untouched.
- **Inner**: new `MainShellNavState` (Compose-owned). Owns the four top-level tabs and their per-tab back stacks. Lives for the lifetime of the `Main` NavEntry.

Two separate `NavDisplay` calls: outer in `app/Navigation.kt` (unchanged structure), inner inside `MainShell`.

**Alternatives considered:**

- *Single "God" navigator extended with tab semantics.* `Navigator.goTo(key)` would have to dispatch on key type to decide which back stack to mutate (outer vs. active tab's). Routing ambiguity, source of bugs, violates single-responsibility. Rejected.
- *Two Hilt-bound navigators side by side with parallel `goTo`/`goBack` APIs.* Cleaner than the God navigator, but two singletons with overlapping vocabulary creates a footgun: which `goBack` are you calling from a VM? Rejected.

The official Nav3 recipes (`multiple-backstacks`, `common-ui`) use a single state holder per shell, and the repo's existing `Navigator` already owns the outer shell. Layering the recipe's `TopLevelBackStack` shape inside `Main` matches both.

### D2. `MainShellNavState` is Compose-owned (not Hilt-bound)

`MainShellNavState` is created via `rememberMainShellNavState(...)` inside the `MainShell` composable's body. Its internal state uses `rememberSerializable(...) { mutableStateOf(...) }` for the active-tab pointer and `rememberNavBackStack(...)` per top-level destination — the exact shape from the `multiple-backstacks` recipe. State survives configuration change and process death.

Lifecycle: when `Main` leaves the outer back stack (e.g. `outerNavigator.replaceTo(Login)` on logout), the `Main` NavEntry's composition is destroyed and the `remember`'d `MainShellNavState` is GC'd. No per-tab residue.

**Alternatives considered:**

- *`@Singleton TabNavigator` + explicit `reset()` on logout.* Works, but couples lifecycle to one-line discipline in the auth-state observer. Forgetting it leaks per-account NavKeys across login transitions. Memory residue, not UI-reachability — but unnecessary risk when the Compose-owned alternative makes it impossible.
- *Custom Hilt component (`@MainShellScoped`).* Pure Hilt-native lifecycle scoping. Real ceremony to set up — component class, parent component, entry-point boilerplate, manual create/destroy hooks tied to NavEntry lifecycle. Worth it only if more bindings need the same scope; for a single state holder, not justified.
- *Scope to a `MainShellViewModel` via the existing `rememberViewModelStoreNavEntryDecorator`.* Hilt-injectable into VMs. But: `rememberNavBackStack` is `@Composable` and can't run inside a ViewModel. Persistence and Hilt-singleton injection are mutually exclusive in the recipe's shape. Rejected for this reason.

The Compose-owned shape is what the recipes actually do.

### D3. Hilt qualifiers `@OuterShell` / `@MainShell`, not distinct typealiases

The existing `EntryProviderInstaller` multibinding (which today lands all entries in the outer `NavDisplay`) is partitioned by qualifier:

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class OuterShell
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainShell

// :feature:login:impl
@Provides @IntoSet @OuterShell
fun provideLoginEntries(): EntryProviderInstaller = { entry<Login> { LoginScreen() } }

// :feature:feed:impl
@Provides @IntoSet @MainShell
fun provideFeedEntries(): EntryProviderInstaller = { entry<Feed> { FeedScreen(...) } }
```

`:app`'s `NavigationEntryPoint` exposes both sets:

```kotlin
@OuterShell fun outerEntryProviderInstallers(): Set<EntryProviderInstaller>
@MainShell fun mainShellEntryProviderInstallers(): Set<EntryProviderInstaller>
```

**Alternatives considered:**

- *Two distinct typealiases (`OuterEntryProviderInstaller`, `MainShellEntryProviderInstaller`).* Most discoverable at the binding site. But Dagger/Hilt's canonical mechanism for "same shape, different collection" is qualifiers. Two typealiases would also drift if the underlying shape ever evolves.
- *Sealed-class wrapper (`EntryContribution.Outer | EntryContribution.MainShell`).* One Hilt set, partitioned in `:app`. Adds wrapper indirection without buying anything qualifiers don't already give us.

Qualifiers are the canonical Dagger pattern. Discoverability concern is addressed by explicit annotation names.

### D4. `NavigationSuiteScaffold` over hand-rolled `WindowSizeClass` swap

Use `androidx.compose.material3.adaptive:adaptive-navigation-suite`. Items declared once via `navigationSuiteItems = { item(selected, onClick, icon, label) }`; the layout (`NavigationBar` / `NavigationRail` / `NavigationDrawer`) is chosen from `currentWindowAdaptiveInfo()`.

`NavigationSuiteScaffold` provides chrome only; routing stays with `MainShellNavState`. The content lambda hosts the inner `NavDisplay`.

**Alternatives considered:**

- *Hand-rolled `WindowSizeClass` swap (NowInAndroid pattern).* NIA predates `NavigationSuiteScaffold`; that's why it rolls its own. Today, the M3 primitive subsumes the pattern with less code, fewer custom-transition bugs, and free upgrades when the Material team improves the rail/drawer behavior. Picking up NIA's hand-rolled approach in 2026 would be re-implementing a stabilized library.

### D5. Stub api-only modules; `:app`-side placeholder Composables for empty tabs

`:feature:search:api`, `:feature:chats:api`, `:feature:profile:api` ship NavKeys only. No `:impl` modules. `:app` registers placeholder Composables (e.g. "Search — coming soon") for the three empty tabs via its own `@MainShell EntryProviderInstaller` providers, scoped `internal` to `:app`. When each feature's epic stands up its `:impl`, the placeholder provider in `:app` is deleted and the new `:impl` module's provider takes over — clean module boundary, no bridging artifacts.

**Alternative considered:**

- *Stub `:impl` modules now with placeholder Composables inside them.* Means three empty `:impl` modules carrying convention-plugin overhead (Hilt, Compose, screenshot tests, …). Premature scaffolding; pays off only when the feature epic lands, by which point the `:impl` module is going to be rewritten anyway.

### D6. "You" tab; `:feature:profile:api` hybrid `Profile(handle: String? = null)`

`Profile(handle: String? = null) : NavKey` — `null` handle means "current user." Tapping the You tab pushes `Profile(handle = null)`; tapping an author handle in a Feed post pushes `Profile(handle = "alice.bsky.social")`. Same module, same NavKey, same screen logic. Settings is a `Settings : NavKey` sub-route in the same module.

**Alternatives considered:**

- *Tab "Profile", separate "Account" hub module.* Splits self-profile and others-profile rendering across two modules — duplicated profile logic. Rejected.
- *Tab "Account" with profile-preview + cards (Settings, Lists, Mutes, etc.).* Adds an extra layer between the user and their profile. Non-Bluesky-native. Rejected.

"You" matches Bluesky's official terminology, which most users will recognize.

### D7. Cross-tab navigation pushes onto the active tab, never auto-switch

Tap a profile link inside a Feed post → pushes `Profile(handle = "alice")` onto the **Feed tab's** stack. Back returns to Feed home. Same as Bluesky's official client and NowInAndroid.

The alternative — tapping any author link snapping to the You tab — would be jarring. The user's mental model is "I'm browsing Feed; I'll come back."

Implementation: `:feature:profile:impl` (when it lands) provides its `Profile` entry as `@MainShell` so the inner `NavDisplay` knows how to render it; the screen the user is currently on calls `mainShellNavState.add(Profile(handle))` on the *active* tab.

### D8. Tab-internal navigation via `UiEffect.Navigate(target)`

ViewModels do not inject `MainShellNavState`. They emit a `NavigateTo(target: NavKey)` `UiEffect`; the screen composable consumes it via `LaunchedEffect` and calls `mainShellNavState.add(target)`. The same pattern as how errors flow today (CLAUDE.md MVI conventions).

This is enforced by the architecture: `MainShellNavState` is reachable only via `LocalMainShellNavState.current`, which is a CompositionLocal — VMs can't access CompositionLocals.

**Alternative considered:**

- *Inject `MainShellNavState` into VMs (mirror the outer `Navigator` pattern).* Pragmatic. But it re-introduces VMs reaching across the MVI boundary into navigation, exactly the pattern that `UiEffect` was created to avoid. The outer Navigator does this only because Splash/Login is two-step state-driven routing; tab-internal navigation is per-event UI navigation, which is `UiEffect` territory.

## Risks / Trade-offs

- **Verbosity in screens** → every screen that initiates navigation now has a `LaunchedEffect` collecting `UiEffect` and a `mainShellNavState.add(...)` call. Mitigation: this is the existing MVI error-handling pattern; same shape. Not new code.
- **`TopLevelBackStack`'s flattened-back-stack approach** → the inner `NavDisplay` sees all *active* tabs' stacks concatenated. The recipe states this is correct behavior; verify under the 120Hz scrolling bar with Macrobenchmark before declaring this epic done.
- **NavigationSuiteScaffold version drift vs. Compose BoM** → adding `androidx.compose.material3.adaptive:adaptive-navigation-suite` requires a version compatible with the existing `compose-material3` BoM. Mitigation: pin to a known-compatible version; rely on Renovate for upgrades.
- **Three stub api modules increase Gradle config time** → trivial impact (each module only declares a NavKey + uses `nubecita.android.library`), but worth measuring.
- **Adding qualifiers to existing multibinding is a breaking internal contract** → any feature module providing into the unqualified `EntryProviderInstaller` set silently stops being collected. Two existing modules affected (`:feature:login:impl`, `:feature:feed:impl`); both migrated in this change. No out-of-tree consumers.

## Migration Plan

Sequenced into three child bd tickets:

1. **Foundation (no UI).** Add `MainShellNavState` + `rememberMainShellNavState`, `LocalMainShellNavState`, `@OuterShell` / `@MainShell` qualifiers in `:core:common:navigation`. Unit tests for the state holder. No app wiring yet.
2. **Stub feature api modules (build-only).** Create `:feature:search:api`, `:feature:chats:api`, `:feature:profile:api` via the existing `nubecita.android.library` convention plugin. NavKeys exported. Build verification.
3. **MainShell + chrome integration (UI; depends on 1 + 2).** Add `MainShell.kt` with `NavigationSuiteScaffold` + inner `NavDisplay`. Wire the `@MainShell` `NavigationEntryPoint` accessor. Add `:app`-internal placeholder providers for Search/Chats/You. Migrate `:feature:login:impl` to `@OuterShell` and `:feature:feed:impl` to `@MainShell`. Update `app/Navigation.kt` so `Main` renders `MainShell()` instead of `MainScreen()`. Delete `MainScreen.kt` placeholder. Previews at compact / medium / expanded widths. Screenshot tests for chrome swap. Instrumented test verifying state survives `saveInstanceState` → `recreate()` (closes nubecita-3it). Updates CLAUDE.md MVI conventions to document the `UiEffect.Navigate` pattern.

No rollback plan needed — additive change to internal modules; reverting a child PR restores prior state.

## Open Questions

- **NavigationSuiteScaffold version** — pin a specific version of `adaptive-navigation-suite` that aligns with the current Compose BoM, or accept the BoM-resolved version? Resolve during foundation child.
- **Drawer mode behavior** — `NavigationSuiteScaffold` can render a permanent drawer at expanded widths. With four destinations, a rail is more appropriate than a drawer. Default to suppressing drawer mode (treat expanded as rail), or accept the M3 default? Resolve during MainShell child via screenshot test review.
- **Window-size-class thresholds** — accept M3 defaults or override? Default for now; revisit if foldable QA reveals issues.
