## ADDED Requirements

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
