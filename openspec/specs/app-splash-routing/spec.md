# app-splash-routing Specification

## Purpose
TBD - created by archiving change add-splash-routing. Update Purpose after archive.
## Requirements
### Requirement: `:app` defines a `Splash` NavKey as the start destination

`:app` SHALL define `@Serializable data object Splash : NavKey` and SHALL update `StartDestinationModule.provideStartDestination()` to return `Splash` (replacing the prior `Main` start destination). `MainNavigation` SHALL register an `entry<Splash>` rendering an empty surface (e.g. `Box(Modifier.fillMaxSize())`); the system SplashScreen API overlays this empty render via `setKeepOnScreenCondition` while session bootstrap runs.

#### Scenario: Cold start shows the system splash, then the routed destination

- **WHEN** the user cold-starts the app and a session is present in the store
- **THEN** the system splash SHALL be visible during bootstrap, then SHALL dismiss revealing `Main` (no flicker, no momentary empty Splash composable)

#### Scenario: Cold start with no session routes to Login

- **WHEN** the user cold-starts the app and `OAuthSessionStore.load()` returns `null`
- **THEN** the system splash SHALL be visible during bootstrap, then SHALL dismiss revealing `Login`

### Requirement: `MainActivity` installs the SplashScreen API and holds it while session is Loading

`MainActivity.onCreate` SHALL:

1. Call `installSplashScreen()` BEFORE `super.onCreate(savedInstanceState)`.
2. Call `splashScreen.setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }` so the system splash remains visible until the bootstrap resolves.
3. Inject `SessionStateProvider` (`@Inject lateinit var sessionStateProvider: SessionStateProvider`).
4. After `setContent`, launch a `lifecycleScope` coroutine that calls `sessionStateProvider.refresh()` to drive the initial state read off the splash.

The keep-on-screen predicate SHALL be cheap (a synchronous `state.value` read) — it's invoked from the platform's frame callback, not a coroutine context.

#### Scenario: SplashScreen is installed before super.onCreate

- **WHEN** `MainActivity.kt` is inspected
- **THEN** the call to `installSplashScreen()` SHALL appear before `super.onCreate(savedInstanceState)` in the onCreate body

#### Scenario: Keep-on-screen predicate releases when state leaves Loading

- **WHEN** the bootstrap coroutine completes a `refresh()` and the state transitions away from `Loading`
- **THEN** the next platform frame callback SHALL evaluate the predicate as `false` and dismiss the system splash

### Requirement: `MainActivity` reactively replaces the back stack when SessionState changes

`MainActivity.onCreate` SHALL launch a second `lifecycleScope` coroutine that collects `sessionStateProvider.state` and calls:

- `navigator.replaceTo(Login)` on `SessionState.SignedOut`.
- `navigator.replaceTo(Main)` on `SessionState.SignedIn`.
- No-op on `SessionState.Loading`.

The collector SHALL run for the lifetime of the activity (not just for the bootstrap window) so subsequent state transitions — most importantly a future `signOut()` while the user is on `Main` — automatically swap the back stack to `Login` without any explicit caller-side navigation.

`navigator.replaceTo` is idempotent — emitting the same state twice produces a no-op visible change.

#### Scenario: SignedIn transition replaces Splash with Main on cold start

- **WHEN** the bootstrap completes with a present session and emits `SignedIn`
- **THEN** `Navigator.backStack` SHALL contain exactly `[Main]` (the prior `[Splash]` having been cleared by `replaceTo`)

#### Scenario: SignedOut transition replaces Splash with Login on cold start

- **WHEN** the bootstrap completes with no session and emits `SignedOut`
- **THEN** `Navigator.backStack` SHALL contain exactly `[Login]`

#### Scenario: SignedOut transition mid-session reroutes to Login

- **WHEN** the user is on `Main` and a `signOut()` triggers `SessionStateProvider.refresh()` emitting `SignedOut`
- **THEN** `MainActivity`'s collector SHALL invoke `navigator.replaceTo(Login)` and the visible destination SHALL transition to `Login` without any other code calling navigation explicitly
