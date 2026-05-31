# `:core:analytics`

Provider-agnostic analytics for the app. Features depend on the `AnalyticsClient`
interface only — never on Firebase types — so the backend can be swapped
(Firebase → PostHog/Amplitude/self-hosted) by changing this one module.

## Typed, PII-free by construction

There is no `log(name: String, params: Map<…>)` escape hatch. Callers pass a
typed value:

```kotlin
analytics.log(InteractPost(action = PostAction.Like, surface = PostSurface.Feed))
analytics.logScreen(AnalyticsScreen.Feed)
analytics.setUserProperty(SelfHosted(isSelfHosted = true))
```

Every event/property param is an enum, boolean, or bucketed count. Identifying
values are never accepted: `feed_uri` → `FeedType` enum, `pds_host` →
`is_self_hosted` boolean, `search_term` → `SearchScope` enum. This makes leaking
post text, handles, DIDs, AT-URIs, raw queries, or PDS hostnames **structurally
impossible**, and keeps the wire model under GA4's caps by construction.

| Type | Purpose |
|---|---|
| `AnalyticsClient` | `log` / `setUserProperty` / `logScreen` — fire-and-forget, never blocks or throws into the UI |
| `AnalyticsEvent` | sealed; v1: `Login`, `ViewFeed`, `InteractPost`, `CreatePost`, `SearchPerform` |
| `UserProperty` | sealed; v1: `Theme`, `SelfHosted`, `NotificationsEnabled` |
| `AnalyticsScreen` | enum of stable route names for `screen_view` |
| `AnalyticsValue` | neutral wire value (`Str` / `LongVal` / `DoubleVal` / `BoolVal`) each provider translates |
| `AnalyticsValidator` | debug-only GA4 name-rule checks (snake_case, length caps, reserved prefixes/names) |

## Flavor split (`environment` dimension)

Mirrors `:core:auth` / `:core:preferences`:

- **`production`** — `AnalyticsModule` binds `FirebaseAnalyticsClient` and provides
  `FirebaseAnalytics`. `firebase-analytics` is a `productionImplementation` dep, so
  the `bench` flavor never links Firebase.
- **`bench`** — `AnalyticsModule` (same FQN) binds `NoOpAnalyticsClient`, so
  screenshot / baseline-profile / Macrobenchmark runs emit zero analytics.

Both `AnalyticsModule` files are public so downstream feature instrumentation
tests can swap either via `@TestInstallIn(replaces = [AnalyticsModule::class])`.
`NoOpAnalyticsClient` lives in `src/main` so unit tests reuse it.

## Scope

This module is the framework only (bd nubecita-049f.1). Wiring `screen_view`
(`.2`), engagement/entry events (`.3`/`.4`), and user properties (`.5`) lands in
the sibling children. Design: `docs/superpowers/specs/2026-05-30-app-analytics-design.md`.
