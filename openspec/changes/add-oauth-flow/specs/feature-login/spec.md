## ADDED Requirements

### Requirement: `LoginViewModel` collects from `OAuthRedirectBroker` and completes login

`LoginViewModel` SHALL inject `OAuthRedirectBroker` and `AuthRepository` and, in its `init` block, launch a coroutine in `viewModelScope` that collects `broker.redirects`. For each emitted URI it SHALL call `authRepository.completeLogin(uri)`:

- on success, it SHALL emit `LoginEffect.LoginSucceeded`;
- on failure, it SHALL set `state.errorMessage = LoginError.Failure(failure.message)` and SHALL NOT emit a navigation effect.

The collection SHALL persist for the lifetime of the VM and SHALL stop when `viewModelScope` is cancelled (i.e., when the VM is cleared).

#### Scenario: Broker emission triggers completeLogin and emits LoginSucceeded

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.success(Unit)`
- **THEN** `LoginViewModel.effects` SHALL emit `LoginEffect.LoginSucceeded` exactly once

#### Scenario: completeLogin failure populates errorMessage instead of effect

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.failure(IllegalStateException("invalid code"))`
- **THEN** the VM's `state.errorMessage` SHALL become `LoginError.Failure("invalid code")` and `LoginEffect.LoginSucceeded` SHALL NOT be emitted

### Requirement: `LoginEffect.LoginSucceeded` signals post-login navigation

`LoginEffect` SHALL include a `data object LoginSucceeded : LoginEffect` variant. It SHALL carry no payload — the destination is the screen's responsibility, not the VM's.

#### Scenario: Effect is a singleton object

- **WHEN** `LoginEffect.LoginSucceeded` is referenced from any module that depends on `:feature:login:impl`
- **THEN** it SHALL be the same instance across references (sealed-interface `data object` semantics)

### Requirement: `LoginScreen` `LaunchedEffect` handles both side-effecting effects

The stateful `LoginScreen()` overload SHALL collect `viewModel.effects` inside a single `LaunchedEffect(viewModel)` and dispatch:

- `LoginEffect.LaunchCustomTab(url)` → `CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())`, where `context` is `LocalContext.current`.
- `LoginEffect.LoginSucceeded` → `navigator.goBack()` (where `navigator` is the Hilt-bound `Navigator` from `:core:common`, obtained via the existing `EntryPoint` mechanism or `hiltViewModel`-style helper).

Subsequent variants of `LoginEffect` added in future PRs SHALL be handled here; the `when` must remain exhaustive.

#### Scenario: LaunchCustomTab opens the URL in a Custom Tab

- **WHEN** the VM emits `LoginEffect.LaunchCustomTab("https://bsky.social/oauth/authorize?...")`
- **THEN** `LoginScreen` SHALL invoke `CustomTabsIntent.launchUrl(context, ...)` with that URL

#### Scenario: LoginSucceeded pops the Login destination off the stack

- **WHEN** the VM emits `LoginEffect.LoginSucceeded` while `Login` is the top of the back stack
- **THEN** `LoginScreen` SHALL invoke `navigator.goBack()` and the back stack SHALL no longer contain `Login`

### Requirement: `:app` AndroidManifest captures the OAuth redirect via deep link

`app/src/main/AndroidManifest.xml` SHALL declare:

- `android:launchMode="singleTask"` on the `MainActivity` element so OAuth redirects re-deliver to the existing activity instance via `onNewIntent` instead of spawning a new task.
- A new `<intent-filter>` on `MainActivity` matching `<action android:name="android.intent.action.VIEW" />`, `<category android:name="android.intent.category.DEFAULT" />`, `<category android:name="android.intent.category.BROWSABLE" />`, `<data android:scheme="net.kikin.nubecita" />`. The scheme MUST equal the app's `applicationId` and the `redirect_uris` registered in `client-metadata.json`.

The existing `LAUNCHER` intent filter SHALL remain untouched.

#### Scenario: Manifest declares both intent filters

- **WHEN** `app/src/main/AndroidManifest.xml` is parsed
- **THEN** the `<activity android:name=".MainActivity">` element SHALL contain both the `LAUNCHER` filter (existing) and the `VIEW` + `BROWSABLE` filter for `net.kikin.nubecita`

### Requirement: `MainActivity` publishes captured redirect URIs to `OAuthRedirectBroker`

`MainActivity` SHALL inject `OAuthRedirectBroker` and SHALL handle the OAuth redirect intent in both `onCreate` (cold-start case) and `onNewIntent` (warm-start case). When an incoming intent's `data` is non-null and its `scheme` equals `net.kikin.nubecita`, `MainActivity` SHALL:

1. Launch a coroutine on `lifecycleScope` calling `broker.publish(intent.data.toString())`.
2. Set `intent.data = null` to prevent configuration changes (rotation, theme switch) from re-firing the redirect handler.

Intents whose scheme is not `net.kikin.nubecita` (e.g., the `LAUNCHER` MAIN intent on cold-start) SHALL be ignored by this handler.

#### Scenario: Warm-start redirect publishes through the broker

- **WHEN** an authenticated browser invokes `net.kikin.nubecita:/oauth-redirect?code=abc&state=xyz` and Android delivers it to the running `MainActivity` via `onNewIntent`
- **THEN** `MainActivity` SHALL call `broker.publish("net.kikin.nubecita:/oauth-redirect?code=abc&state=xyz")` and SHALL clear `intent.data`

#### Scenario: Cold-start redirect publishes through the broker

- **WHEN** the same redirect arrives while the app process is dead and Android cold-starts `MainActivity` with the redirect intent
- **THEN** `MainActivity.onCreate`'s intent handler SHALL publish the URI; the `LoginViewModel`'s `init`-time collector SHALL receive the buffered emission once it subscribes
