## Why

A non-Bluesky user (Stav) tried Nubecita and asked "can I sign up through your app?". The current login screen fails both sides of that question:

1. **Errors are unreadable.** `LoginViewModel`'s failure arm copies `failure.message` verbatim, so when `atproto-kotlin`'s `DiscoveryChain` can't resolve a handle the user sees `Failed to resolve DID from /.well-known/atproto-did: 404` — technical, intimidating, and gives no path forward.
2. **There is no sign-up affordance.** The screen reads "Sign in to Bluesky" + placeholder `alice.bsky.social` and assumes an existing account. Bluesky doesn't support in-app account creation via OAuth against `bsky.social`; the user has to reach the web flow, but Nubecita doesn't link to it.

Both gaps are in scope of the `nubecita-lq9t.3` ("Core feature completeness") production gate. A user who mistypes a handle or has no account at all should leave the login screen with a clear next step instead of bouncing.

## What Changes

- Expand `LoginError` into a typed sum (`BlankHandle`, `HandleNotFound`, `Network`, `Generic`) and map `atproto-kotlin` exceptions in `LoginViewModel` to those variants instead of forwarding raw `Throwable.message`. `LoginError.Failure(cause: String?)` is removed; the screen no longer renders library text directly.
- Add a secondary "Don't have an account? Create one on Bluesky" affordance on `LoginScreen`, below the primary submit button. Tapping it emits a new `LoginEffect.LaunchExternalUrl(url)` (or extends `LaunchCustomTab` semantics) which the screen collects and opens via `CustomTabsIntent` — the same pattern `:feature:feed:impl` already uses for external embeds.
- Add four new string resources (one per `LoginError` variant) and two for the sign-up CTA (label + supporting copy). The VM stays Android-resource-free; the screen owns the `LoginError` → `stringResource` mapping.
- Extend tests:
  - Unit tests: one `LoginViewModel` mapping case per exception → `LoginError` variant.
  - Screenshot tests: one preview per error variant (light + dark) plus the sign-up CTA in both themes.
  - Instrumentation test: tap the sign-up CTA → assert a `CustomTabsIntent` launches with `https://bsky.app/signup`.

## Capabilities

### New Capabilities

(none — extends an existing capability)

### Modified Capabilities

- `feature-login`: the "LoginViewModel drives beginLogin..." requirement currently mandates that failure populates `errorMessage` with the exception's `message` (or a generic fallback). That requirement is replaced by typed exception mapping. A new requirement covers the sign-up affordance and the `LaunchExternalUrl` effect.

## Impact

- **Affected modules**: `:feature:login:impl` only — error sum, VM mapping, screen UI, string resources, screenshot/instrumentation tests.
- **Affected specs**: `feature-login` (delta — modifies the error-population requirement, adds a sign-up-affordance requirement).
- **Dependencies**: no new module deps. `androidx.browser:browser` (Chrome Custom Tabs) is already on `:feature:login:impl`'s classpath via the existing `LaunchCustomTab` effect. The atproto-kotlin exception types we map against are already on the classpath via `:core:auth`.
- **Out of scope**: in-app account creation via `com.atproto.server.createAccount`, multi-PDS picker UI, client-side handle-format validation beyond blank-check, live handle-availability probing. Each can be filed separately if needed.
- **Backwards compatibility**: `LoginError.Failure(cause: String?)` is removed in favor of typed variants — internal to `:feature:login:impl`, no external module references it. The `LoginEffect` `when` stays exhaustive; the screen's `LaunchedEffect` collector adds the external-URL branch.

## Non-goals

- **In-app account creation.** Bluesky's OAuth flow against `bsky.social` doesn't support `createAccount` in this app's scope today. We link out instead.
- **Multi-PDS picker.** Most users target `bsky.social`; routing to a third-party PDS picker is deferred until a real need surfaces.
- **Localized handle validation.** Catching typos like missing TLDs or double dots client-side belongs in a separate change once the server-error story is solid.
- **Deviation from MVI / Compose / Hilt baseline.** No new architectural patterns; the affordance reuses the existing `UiEffect` + `LaunchedEffect` plumbing.
