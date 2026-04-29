# core-common-navigation Specification

## Purpose
TBD - created by archiving change add-oauth-flow. Update Purpose after archive.
## Requirements
### Requirement: `:core:common` provides a `Navigator` Hilt singleton owning the app back stack

`:core:common` SHALL expose a public `Navigator` interface and an internal `DefaultNavigator` implementation, bound `@Singleton` in Hilt's `SingletonComponent`. The interface SHALL expose:

- `val backStack: SnapshotStateList<NavKey>` — the live back stack, observable from Compose.
- `fun goTo(key: NavKey)` — appends `key` to the top of the stack.
- `fun goBack()` — removes the top of the stack (no-op if empty).
- `fun replaceTo(key: NavKey)` — clears the stack and appends `key`.

The default implementation SHALL initialize `backStack` containing the application's start destination (`Main`).

#### Scenario: ViewModel injects Navigator and pops the back stack

- **WHEN** a `@HiltViewModel` declares a constructor parameter of type `Navigator` and calls `navigator.goBack()` from a coroutine launched in `viewModelScope`
- **THEN** the top entry SHALL be removed from the back stack and `MainNavigation` SHALL render the new top destination on the next frame

#### Scenario: Empty stack `goBack()` is a no-op

- **WHEN** `navigator.goBack()` is called on an empty back stack
- **THEN** no exception SHALL be thrown and the stack SHALL remain empty

### Requirement: `MainNavigation` reads its back stack from the injected Navigator

`:app`'s `MainNavigation` composable SHALL obtain the navigation back stack from the `Navigator` Hilt singleton (via the existing `EntryPoint` pattern or constructor-injected wrapper) instead of calling `rememberNavBackStack(...)` locally. The `NavDisplay` `backStack` parameter SHALL receive `navigator.backStack` directly.

#### Scenario: Back-stack mutation from outside MainNavigation is observed

- **WHEN** any class with access to the `Navigator` (a ViewModel, a `LaunchedEffect`) calls `navigator.goTo(SomeNavKey)` while `MainNavigation` is composed
- **THEN** `NavDisplay` SHALL render the new destination on the next composition pass

#### Scenario: `MainNavigation` does not maintain a private back stack

- **WHEN** the `MainNavigation` source is inspected
- **THEN** it SHALL NOT call `rememberNavBackStack(...)`; the back stack passed to `NavDisplay` SHALL come from the Hilt-bound `Navigator`

### Requirement: `:core:common:navigation` provides `MainShellNavState` Compose-owned multi-tab state holder

`:core:common:navigation` SHALL expose a public `MainShellNavState` class and a `@Composable rememberMainShellNavState(startRoute: NavKey, topLevelRoutes: List<NavKey>): MainShellNavState` factory. `topLevelRoutes` SHALL be a `List` (not `Set`) because the factory issues one `rememberNavBackStack(key)` call per element in iteration order and Compose keys those `remember` slots by composer position; a reordered iteration would re-associate persisted stacks with the wrong keys. The factory SHALL `require` that `topLevelRoutes` contains unique elements and includes `startRoute`. The class SHALL hold:

- `topLevelKey: NavKey` — the active tab, mutable.
- A per-top-level-route map of back stacks (`NavBackStack<NavKey>` per route).
- A flattened `backStack: SnapshotStateList<NavKey>` view suitable for passing to `NavDisplay.backStack`, computed across the active tabs in "exit through home" order.

The class SHALL expose:

- `addTopLevel(key: NavKey)` — switch active tab; preserve the outgoing tab's stack.
- `add(key: NavKey)` — push `key` onto the active tab's stack.
- `removeLast()` — pop. If the popped key is a top-level route, the active tab SHALL switch back toward the start route per the recipe's "exit through home" rule.

The factory SHALL persist `topLevelKey` via `rememberSerializable(... NavKeySerializer ...)` and per-tab back stacks via `rememberNavBackStack(...)`, so configuration change and process death restore the prior state.

The class SHALL NOT be `@Inject`-able. It is intended to be created inside a Composable's body.

#### Scenario: Tab switch preserves outgoing stack

- **WHEN** `addTopLevel(Search)` is called from a state where the active tab is Feed and the Feed stack contains `[Feed, Profile("alice")]`
- **THEN** `topLevelKey` SHALL become `Search`, and a subsequent `addTopLevel(Feed)` SHALL restore Feed with the stack `[Feed, Profile("alice")]` intact

#### Scenario: Process-death round-trip restores state

- **WHEN** a `MainShellNavState` is created via `rememberMainShellNavState(...)`, mutated so the active tab is Feed with stack `[Feed, Profile("alice")]`, and the hosting Composable goes through a `saveInstanceState` → `recreate()` cycle
- **THEN** the post-recreation `MainShellNavState` SHALL report `topLevelKey == Feed` and the Feed back stack `[Feed, Profile("alice")]`

### Requirement: `:core:common:navigation` exposes `LocalMainShellNavState` `CompositionLocal`

`:core:common:navigation` SHALL expose `val LocalMainShellNavState: ProvidableCompositionLocal<MainShellNavState>` with no default value. `MainShell` SHALL provide it via `CompositionLocalProvider` so that descendant Composables can call `LocalMainShellNavState.current` to obtain the active state holder.

ViewModels SHALL NOT access `LocalMainShellNavState`. CompositionLocals are not reachable from a `ViewModel` — this constraint is enforced by the type system.

#### Scenario: Descendant Composable reads MainShellNavState from CompositionLocal

- **WHEN** a screen Composable inside the inner `NavDisplay` reads `LocalMainShellNavState.current` and calls `add(Profile(handle = "alice"))`
- **THEN** the active tab's back stack SHALL gain the `Profile(handle = "alice")` entry

#### Scenario: Reading LocalMainShellNavState outside MainShell throws

- **WHEN** a Composable not hosted inside `MainShell`'s `CompositionLocalProvider` reads `LocalMainShellNavState.current`
- **THEN** an `IllegalStateException` SHALL be thrown stating that no `MainShellNavState` is provided

### Requirement: `:core:common:navigation` provides `@OuterShell` and `@MainShell` Hilt qualifier annotations

`:core:common:navigation` SHALL expose two `@Qualifier`-annotated annotations:

- `@OuterShell` — for `EntryProviderInstaller` providers contributing to the outer `NavDisplay` (Splash, Login, the `Main` wrapper entry).
- `@MainShell` — for `EntryProviderInstaller` providers contributing to the inner `NavDisplay` hosted by `MainShell` (Feed, Search, Chats, You + their sub-routes).

Both qualifiers SHALL be retained at `BINARY` level. Feature modules contributing entries SHALL annotate their `@Provides @IntoSet` declarations with exactly one of these qualifiers. `:app`'s `NavigationEntryPoint` SHALL expose two distinct accessor methods, one annotated with each qualifier, returning `Set<@JvmSuppressWildcards EntryProviderInstaller>`.

#### Scenario: Outer-shell binding is collected via @OuterShell accessor

- **WHEN** a feature module declares `@Provides @IntoSet @OuterShell fun provide…(): EntryProviderInstaller = { entry<X> { … } }`
- **THEN** the binding SHALL be retrievable via `NavigationEntryPoint.outerEntryProviderInstallers()` and SHALL NOT appear in `NavigationEntryPoint.mainShellEntryProviderInstallers()`

#### Scenario: MainShell binding is collected via @MainShell accessor

- **WHEN** a feature module declares `@Provides @IntoSet @MainShell fun provide…(): EntryProviderInstaller = { entry<X> { … } }`
- **THEN** the binding SHALL be retrievable via `NavigationEntryPoint.mainShellEntryProviderInstallers()` and SHALL NOT appear in `NavigationEntryPoint.outerEntryProviderInstallers()`

#### Scenario: Unqualified binding is no longer collected

- **WHEN** a feature module declares `@Provides @IntoSet fun provide…(): EntryProviderInstaller = { entry<X> { … } }` without either qualifier
- **THEN** the binding SHALL NOT be collected by either accessor and the entry SHALL NOT be reachable through any `NavDisplay`

### Requirement: Existing feature modules migrate to qualified bindings

`:feature:login:impl`'s `EntryProviderInstaller` provider SHALL be annotated `@OuterShell`. `:feature:feed:impl`'s `EntryProviderInstaller` provider SHALL be annotated `@MainShell`. After this change, no `:feature:*:impl` module in the repository SHALL `@Provides @IntoSet` an `EntryProviderInstaller` without either `@OuterShell` or `@MainShell`.

#### Scenario: Repository scan finds no unqualified providers

- **WHEN** the repository is scanned for `@Provides @IntoSet fun .*: EntryProviderInstaller`
- **THEN** every match SHALL also carry `@OuterShell` or `@MainShell` on the same provider declaration
