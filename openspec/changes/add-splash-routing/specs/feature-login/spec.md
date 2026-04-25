## MODIFIED Requirements

### Requirement: `LoginScreen` `LaunchedEffect` handles both side-effecting effects

The stateful `LoginScreen()` overload SHALL collect `viewModel.effects` inside a single `LaunchedEffect(viewModel)` and dispatch:

- `LoginEffect.LaunchCustomTab(url)` → `CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())`, where `context` is `LocalContext.current`.
- `LoginEffect.LoginSucceeded` → **no-op**. Post-login destination routing is owned by `MainActivity`'s reactive observer of `SessionStateProvider.state`; the screen no longer pops the back stack itself. The branch remains in the `when` so the compiler enforces exhaustiveness when future `LoginEffect` variants are added.

Subsequent variants of `LoginEffect` added in future PRs SHALL be handled here; the `when` must remain exhaustive.

#### Scenario: LaunchCustomTab opens the URL in a Custom Tab

- **WHEN** the VM emits `LoginEffect.LaunchCustomTab("https://bsky.social/oauth/authorize?...")`
- **THEN** `LoginScreen` SHALL invoke `CustomTabsIntent.launchUrl(context, ...)` with that URL

#### Scenario: LoginSucceeded does not directly mutate the back stack

- **WHEN** the VM emits `LoginEffect.LoginSucceeded`
- **THEN** `LoginScreen` SHALL NOT invoke any `Navigator` method directly; the post-login destination swap SHALL come from `MainActivity`'s reactive observer of `SessionStateProvider.state` transitioning to `SignedIn`

#### Scenario: Post-login routing produces a single-entry back stack with Main

- **WHEN** `AuthRepository.completeLogin` succeeds (the VM emits `LoginSucceeded` and `SessionStateProvider` transitions to `SignedIn`)
- **THEN** `Navigator.backStack` SHALL contain exactly `[Main]` (the prior `[Login]` having been replaced via `navigator.replaceTo(Main)` from `MainActivity`'s collector)
