## Context

Three things meet in this PR:

1. **The Compose UI side**: `LoginScreen` already emits a `LaunchCustomTab(url)` effect. Nobody listens.
2. **The Android intent side**: When Bluesky's auth server redirects to `net.kikin.nubecita:/oauth-redirect`, Android delivers the intent to whatever activity claims the scheme. We have neither an intent filter nor a handler.
3. **The DI side**: `AtOAuth.completeLogin(redirectUri)` exists in `:core:auth`'s wrapped `AuthRepository`. Nobody calls it.

The mechanical wiring is straightforward. The one architectural question is how a NavEntry-scoped `@HiltViewModel` (`LoginViewModel`) communicates "go back, you're authenticated" to the back stack without (a) breaking the `@IntoSet EntryProviderInstaller` pattern (no per-call-site callbacks at the entry-registration site), (b) coupling the VM to Activity-scoped Android navigation classes, or (c) requiring callers of `LoginScreen` to plumb a callback through `:app`.

The answer adopted here is the same pattern Now in Android uses for cross-feature back-stack ownership: invert the back-stack ownership from `MainNavigation` (which currently calls `rememberNavBackStack(Main)`) to a Hilt-bound `Navigator` singleton. `MainNavigation` reads from it; ViewModels mutate it.

## Goals / Non-Goals

**Goals:**

- End-to-end OAuth: user enters handle → Custom Tab opens authorization URL → redirect comes back through deep-link → `completeLogin` exchanges the code for tokens → user lands off the Login destination.
- Establish `Navigator` as the cross-module navigation seam every subsequent feature uses.
- Keep the `@IntoSet EntryProviderInstaller` pattern intact (no per-call-site callbacks).
- Preserve the existing screenshot baselines — no UI rendering changes.

**Non-Goals:**

- Auth-gated routing decisions (where the user lands post-login is `nubecita-30c`).
- Sign-out / `AuthRepository.signOut` (also `nubecita-30c`).
- End-to-end PAR validation against a live bsky.social authorization server (instrumented test under `nubecita-16a`).
- Account switching / multi-session (session store is single-blob by design).
- A standalone `:core:navigation` module — `Navigator` lives in `:core:common` until cross-feature concerns appear.

## Decisions

### 1. `Navigator` lives in `:core:common`, owns the back stack, Hilt-bound `@Singleton`

**Decision:**

```kotlin
interface Navigator {
    val backStack: SnapshotStateList<NavKey>
    fun goTo(key: NavKey)
    fun goBack()
    fun replaceTo(key: NavKey)
}

internal class DefaultNavigator @Inject constructor() : Navigator {
    override val backStack: SnapshotStateList<NavKey> = mutableStateListOf(Main)
    override fun goTo(key: NavKey) { backStack.add(key) }
    override fun goBack() { backStack.removeLastOrNull() }
    override fun replaceTo(key: NavKey) { backStack.clear(); backStack.add(key) }
}
```

`MainNavigation` reads the back stack from the injected `Navigator` instead of calling `rememberNavBackStack(Main)`. Hilt binding installs `DefaultNavigator → Navigator` in `SingletonComponent`.

**Alternatives considered:**

- **`ActivityRetainedComponent` scope.** More technically correct (back stack is an Activity concern, not a process concern). We have one MainActivity; the practical difference is nil. Singleton is simpler.
- **A composition local + `provideNavBackStack` at the `MainNavigation` level.** Doesn't reach ViewModels.
- **`SavedStateHandle`-driven navigation.** Awkward for "navigate back" actions and doesn't fit Nav 3's typed-key model.
- **Wait for `nubecita-30c` to add it.** Postpones a decision that ck0 needs *now* to handle `LoginSucceeded` cleanly.

**Rationale:** Matches NiA's pattern. ~30 lines of code. Single source of truth for navigation; both Composables and ViewModels mutate the same back stack. `MainNavigation` doesn't lose anything — it just delegates ownership.

**Note on Compose semantics:** the `SnapshotStateList<NavKey>` is observable from Compose because mutations trigger snapshot reads. `MainNavigation` passes `navigator.backStack` directly to `NavDisplay`. No additional subscribe / `collectAsState` needed.

### 2. `Navigator.backStack` is initialized with the start destination at construction

**Decision:** `mutableStateListOf(Main)` at field initialization. The start destination is hard-coded in `DefaultNavigator`.

**Alternatives considered:**

- **Configurable start destination via Hilt qualifier.** Premature — we have one start destination today.
- **`Navigator.start(key: NavKey)` initialization method called by `MainActivity`.** Adds a setup ordering concern that's easy to get wrong.

**Rationale:** Single application, single start destination. When that changes (auth-gated routing under `nubecita-30c` will probably want to start at `Login` if no session, `Main` otherwise), `DefaultNavigator` becomes a small computation over the session state — but that's 30c's call. Today: hard-code.

### 3. `OAuthRedirectBroker` is a `Channel`, not a `SharedFlow`

**Decision:**

```kotlin
interface OAuthRedirectBroker {
    val redirects: Flow<String>
    suspend fun publish(redirectUri: String)
}

internal class DefaultOAuthRedirectBroker @Inject constructor() : OAuthRedirectBroker {
    private val channel = Channel<String>(Channel.BUFFERED)
    override val redirects: Flow<String> = channel.receiveAsFlow()
    override suspend fun publish(redirectUri: String) { channel.send(redirectUri) }
}
```

Singleton-scoped Hilt binding.

**Alternatives considered:**

- **`MutableSharedFlow<String>(replay = 1)`.** Replays the last redirect to every collector. We *don't* want that — if the user signs out and re-enters Login while the same `LoginViewModel` is somehow alive, we'd accidentally re-process the prior redirect.
- **`StateFlow<String?>`.** Same replay-on-subscribe concern.
- **No buffering.** A redirect that arrives before `LoginViewModel.init` runs (cold-start deep-link case) would be dropped.

**Rationale:** Channel + `receiveAsFlow()` gives us at-most-once delivery to a single consumer with buffering for the cold-start scenario. The `BUFFERED` channel size (default 64) is far more than enough — at most one redirect is in flight at a time. Single-consumer semantics match the actual use case (only one `LoginViewModel` is ever active at a time given our nav graph).

### 4. `LoginViewModel` collects from the broker in `init`, not in response to an event

**Decision:**

```kotlin
init {
    viewModelScope.launch {
        broker.redirects.collect { uri ->
            authRepository.completeLogin(uri)
                .onSuccess { sendEffect(LoginEffect.LoginSucceeded) }
                .onFailure { failure ->
                    setState { copy(errorMessage = LoginError.Failure(failure.message)) }
                }
        }
    }
}
```

**Alternatives considered:**

- **`LoginEvent.OAuthRedirectReceived(uri)` event dispatched by the screen composable.** Requires the screen to also be a broker subscriber, doubling the surface. The redirect doesn't have a UI source — it comes from outside the screen lifecycle entirely.
- **A `BroadcastReceiver`-style API where MainActivity calls a function on a Hilt-injected listener.** Same idea as the broker, more verbose.

**Rationale:** The broker IS the redirect's source-of-truth; collecting in `init` is the most direct expression of "this VM cares about redirects for as long as it's alive." `viewModelScope` cancels collection automatically when the VM is cleared.

### 5. Custom Tab launch lives in the `LoginScreen` composable, not the ViewModel

**Decision:**

```kotlin
@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator = remember(...) { /* from EntryPoint */ }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.LaunchCustomTab ->
                    CustomTabsIntent.Builder().build().launchUrl(context, effect.url.toUri())
                LoginEffect.LoginSucceeded -> navigator.goBack()
            }
        }
    }
    LoginScreen(state = state, onEvent = viewModel::handleEvent, modifier = modifier)
}
```

**Alternatives considered:**

- **VM holds an `Activity` / `Context` and launches the Custom Tab.** Couples VM to Android lifecycle classes; breaks unit-testability.
- **Pass `onLaunchCustomTab: (String) -> Unit` callback into `LoginScreen`.** Caller (`MainNavigation`) would have to provide the closure, breaking the `@IntoSet` zero-callback contract.

**Rationale:** MVI convention — VM emits typed effects, the composable performs the side effect using the Compose context already at hand. `CustomTabsIntent.launchUrl` accepts a `Context`, not an `Activity`.

### 6. `MainActivity` uses `launchMode="singleTask"` and handles the redirect in both `onCreate` and `onNewIntent`

**Decision:**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { /* MainNavigation */ }
    handleIntent(intent)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
}

private fun handleIntent(intent: Intent) {
    val uri = intent.data ?: return
    if (uri.scheme == REDIRECT_SCHEME) {
        lifecycleScope.launch { broker.publish(uri.toString()) }
        intent.data = null  // consume so rotation doesn't re-fire
    }
}
```

`launchMode="singleTask"` so the redirect re-delivers to the existing instance via `onNewIntent` instead of spawning a new MainActivity.

**Rationale:** Standard Android pattern; mirrors the `atproto-oauth` skill's reference implementation. Consuming `intent.data` after handling prevents the redirect from re-firing on configuration changes (rotation, theme switch) and double-invoking `completeLogin`.

### 7. `LoginSucceeded` is a typed effect, not a state field

**Decision:** Add `data object LoginSucceeded : LoginEffect`. The screen reacts via `LaunchedEffect`. Not stored in `LoginState`.

**Alternatives considered:**

- **`LoginState.signedIn: Boolean`.** Would require a state→nav side effect inside the composable that fires once and then remembers the firing — exactly the shape effects exist for.

**Rationale:** Matches the established MVI convention (one-shot transitions are effects, persistent UI state is state). No payload — the navigation target is whatever the screen's `LaunchedEffect` decides (today: `navigator.goBack()`).

## Risks / Trade-offs

- **`Navigator` is a Hilt singleton, but `SnapshotStateList` is a Compose-runtime construct.** Compose can read it from any thread that holds a snapshot context, which any composable does. ViewModels mutating it from `viewModelScope` is fine (Compose snapshot-applies on the next frame). Risk: someone mutates the list from a non-Compose-aware coroutine in a way that races with composition. → Mitigation: doc comment on `Navigator` notes that mutations should originate from `viewModelScope` or the composition; MainActivity uses `lifecycleScope` for broker.publish (separate thing — the broker's emission goes through `viewModelScope` in the VM, which is what touches the list).
- **`Channel.BUFFERED` is 64 by default.** If 65+ redirects arrive without a consumer, sends suspend. Realistic? No — there's at most one redirect per OAuth flow. Documented for the next maintainer.
- **`MainActivity.handleIntent` runs on the main thread; `broker.publish` is `suspend`.** We launch in `lifecycleScope` (Main dispatcher). The Channel send is non-blocking until the buffer fills. No risk of jank.
- **`launchMode="singleTask"` changes activity-stacking behavior app-wide.** Side effect: deep-linked Activities can't easily be opened in a new task. Acceptable — we have one MainActivity and no plans for multi-window.
- **The `redirect_uris` claimed in `client-metadata.json` is `net.kikin.nubecita:/oauth-redirect`.** If a malicious app on the same device claims the same scheme, it could intercept the redirect. → Mitigation: full mitigation is App Links with assetlinks.json (HTTPS-based redirect). Bluesky's auth server accepts custom schemes per RFC 8252; the security model is "private scheme on a single-user device." Acceptable for v1; revisit when/if we need PCI-grade isolation.
- **Inverting `MainNavigation`'s back-stack ownership might break the screenshot test for `MainScreen`.** The screenshot target renders `MainScreenContent` directly (no `MainNavigation`), so it's unaffected. Verified before starting.

## Open Questions

- **What's the right `goBack()` behavior when the back stack is empty (e.g., user clears app data, lands directly on `Login`, completes login)?** Current implementation: `removeLastOrNull` is safe but leaves an empty stack which `NavDisplay` will treat as "no destination." For ck0 we treat this as "shouldn't happen in production" — the start destination is `Main`, so the stack is never empty before login. `nubecita-30c` will resolve this when it adds auth-gated routing (the start destination becomes conditional on session presence).
- **Should the `Navigator`'s start destination be configurable per-test?** Not yet — JVM unit tests for `DefaultNavigator` use the default; instrumented tests will inject their own.
- **Is there a `NavBackStack` type from Nav 3 we should use instead of raw `SnapshotStateList<NavKey>`?** `rememberNavBackStack` returns a `NavBackStack` — but it's a `@Composable` function and tied to the composition. Using a plain `SnapshotStateList<NavKey>` keeps `Navigator` Compose-runtime-free at the binding level. Verified that `NavDisplay` accepts `List<NavKey>`, so the raw list works.
