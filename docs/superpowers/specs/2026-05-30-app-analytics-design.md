# App analytics: `:core:analytics` Firebase wrapper + v1 events

- **bd epic:** nubecita-049f (children `.1`–`.5`)
- **Date:** 2026-05-30
- **Status:** design approved (brainstorm), ready to file + implement

## Problem

Nubecita has Firebase Analytics wired (BOM `34.14.0`, project `nubecita-2a4c1`, App Check +
Crashlytics live) but **nothing calls `logEvent`/`setUserProperty` anywhere** — only a
`FirebaseInitTest` verifies the instance. We have no product analytics. We also want to avoid
coupling the whole app to Firebase: if we later switch analytics providers (PostHog, Amplitude,
self-hosted), it should be a **one-module change**, not an app-wide refactor.

## Scope

**In (v1):** a provider-agnostic `:core:analytics` module wrapping Firebase Analytics; manual
`screen_view` for our Navigation 3 UI; 5 high-signal events; 3 user properties; a no-op path for
bench/test builds.

**Out:** the full event catalog (deferred to v2+, see below); a second provider implementation
(the abstraction makes it possible, but we ship only Firebase now); server-side GA4 config
(Data Redaction, conversions) — operational, not code.

## Key decisions (from brainstorm)

1. **Typed sealed event API, no raw escape hatch.** Features call
   `analytics.log(InteractPost(action = Like, surface = Feed))`, never `logEvent(name, map)`.
   Every event/param is an enum / boolean / bucketed value. This makes PII leakage *structurally
   impossible*, keeps us under GA4's caps by construction, and centralizes wire-naming in one place.
2. **Lean v1.** Module + manual `screen_view` + 5 events + 3 user properties. The rest are cheap
   incremental follow-ups once the pattern is proven end-to-end.
3. **Flavor split for NoOp.** Matching `:core:auth`/`:core:preferences`: the `production` flavor
   binds the real `FirebaseAnalyticsClient`; the `bench` flavor binds `NoOpAnalyticsClient` so
   screenshot/baseline/bench runs send zero analytics. Tests swap via `@TestInstallIn`.
4. **Privacy is structural.** Analytics NEVER receives: post/reply/quote/message text, user handles,
   DIDs, AT-URIs, raw search queries, PDS hostnames, follower/blocked identities, profile field
   values. Identifying fields are replaced with enums/booleans/bucketed counts (`feed_uri` →
   `feed_type` enum; `pds_host` → `is_self_hosted` bool; `search_term` → `search_scope` enum).

## Architecture

### Module
`:core:analytics` — `nubecita.android.library` + `nubecita.android.hilt`, `environment` flavor
dimension (`production`/`bench`). `firebase-analytics` is a **`productionImplementation`** dependency,
so `bench` builds don't even link Firebase. Namespace `net.kikin.nubecita.core.analytics`.

### Public API (flavor-shared `src/main`; no Firebase types escape)
```kotlin
interface AnalyticsClient {
    fun log(event: AnalyticsEvent)
    fun setUserProperty(property: UserProperty)
    fun logScreen(screen: AnalyticsScreen)
}
```
- `AnalyticsEvent` is a sealed type exposing a **neutral** `name: String` + `params: Map<String,
  AnalyticsValue>`, where `AnalyticsValue = Str | LongVal | DoubleVal | BoolVal`. Naming/params live
  once in the model; each provider impl only translates the neutral form to its SDK (Firebase →
  `Bundle`; a future PostHog → its map). No provider re-derives names.
- `UserProperty` is a sealed type (`Theme`, `SelfHosted`, `NotificationsEnabled`, …).
- `AnalyticsScreen` is an enum of stable route names (`Feed`, `Search`, `PostDetail`, `Composer`,
  `Profile`, `EditProfile`, `Settings`, `Chats`, …).

### v1 events (all params enum/bool — zero PII)
| Event | Params | Std vs custom | Fired at |
|---|---|---|---|
| `login` | `method` (`oauth`) | GA4 recommended | `LoginViewModel` on OAuth completion |
| `view_feed` | `feed_type` (`following`/`discover`/`custom`/`list`/`video`) | custom | `:feature:feed:impl` load + search feeds tab |
| `interact_post` | `action_type` (`like`/`unlike`/`repost`/`unrepost`/`quote`/`reply`), `source_surface` | custom | like/repost call sites (`FeedViewModel` / `:core:post-interactions`) |
| `create_post` | `has_media`, `is_reply`, `is_quote` | custom | `DefaultPostingRepository.createPost` success |
| `search_perform` | `search_scope` (`top`/`latest`/`people`/`feeds`), `from_recent` | custom (replaces GA4 `search`) | `SearchViewModel` submit — **never the query text** |

### v1 user properties
`theme_preference` (`light`/`dark`/`system`), `is_self_hosted` (bool — *not* the host),
`notifications_enabled` (bool). 3 of GA4's 25-property budget.

### Firebase implementation (`src/production`)
`FirebaseAnalyticsClient` wraps `FirebaseAnalytics` (provided via Hilt
`@Provides FirebaseAnalytics.getInstance(context)`). `FirebaseAnalytics.logEvent` is already
non-blocking, so call sites invoke `analytics.log(...)` directly — no coroutine/dispatcher needed.
A **debug-only `require()` validator** checks every event/param/property name against GA4 rules
(snake_case, starts with a letter, ≤40 chars event/param + ≤24 user-property, reserved prefixes
`firebase_`/`google_`/`ga_`, reserved event names) so a malformed event fails in unit tests, not
silently in prod.

### `screen_view` for Navigation 3
Firebase's automatic `screen_view` is Activity/Fragment-based and won't see our Compose
destinations, so:
1. Disable it (manifest `<meta-data
   android:name="google_analytics_automatic_screen_reporting_enabled" android:value="false"/>`).
2. A `TrackScreenViews` composable hooks into **both** `NavDisplay` hosts (`app/Navigation.kt`
   outer + `MainShell` inner), observes each back stack's **top `NavKey`**, and emits a **de-duped**
   `logScreen(...)` mapped to a stable `AnalyticsScreen` route enum — never instance args. This lives
   at the Composable host layer, never in a ViewModel (consistent with `LocalMainShellNavState` /
   `LocalTabReTapSignal`). The `AnalyticsClient` reaches the host via Activity injection + a
   `CompositionLocal` (or a passed callback).

### DI + test seam
`src/production/.../di/AnalyticsModule.kt` binds `FirebaseAnalyticsClient` + provides
`FirebaseAnalytics`; `src/bench/.../di/AnalyticsModule.kt` binds `NoOpAnalyticsClient`. Both are
**public** modules so feature instrumentation tests can `@TestInstallIn(replaces =
[AnalyticsModule::class])`. `NoOpAnalyticsClient` lives in `src/main` so unit tests reuse it.
Analytics calls are fire-and-forget — they must never block MVI flows or throw into the UI.

## Privacy / PII policy

The typed API is the primary defense. Reinforced by: the debug name validator; the rule that any
identifying value (URI, DID, handle, host, query, free text, raw count) is replaced by an enum /
boolean / bucket before it can reach a param; and (operationally, later) GA4 Data Redaction +
IP anonymization as a server-side backstop. Any new event/param added later must pass the same bar.

## v1 vs later

**v1 (this epic):** the 5 events + 3 user properties + `screen_view` above — the core engagement
loop (read → interact → compose → search → auth) with zero PII and mostly single call sites.

**Later (v2+ — separate small epics/issues):** `share` (sanitized), `follow_account`, `edit_profile`,
`open_thread`, `open_profile`, `open_media_viewer`, `play_video`, `open_from_notification`,
`open_deep_link`, `compose_abandon`, `search_select_result`, `list_action`, `discover_feed_action`,
`chat_action`, `onboarding_step`, `tutorial_begin`/`complete`, `sign_up`, `theme_change`,
`change_server`, `app_error_shown`; user properties `active_labelers_bucket`,
`follower_count_bucket`, `feed_personalization`, `app_language`. (Firebase already auto-collects
`first_open`, `session_start`, `user_engagement`, `app_update`, `app_remove`, `os_update`,
`app_exception`/crashes, and FCM `notification_*` — we do not re-implement those.)

## Testing

- **Module (unit):** each `AnalyticsEvent`/`UserProperty` → expected neutral name + params; the
  debug name validator rejects bad names / reserved prefixes; `NoOpAnalyticsClient` is inert.
- **Wiring (unit/feature):** the call-site fires the right typed event on success (verified with a
  fake `AnalyticsClient` capturing events) — e.g. `create_post` carries the correct `has_media`/
  `is_reply`/`is_quote`.
- **No-PII guard:** a test asserting no v1 event/param value is a free-form string (only enums/
  bools/bucketed values), enforced by the typed model.
- **On-device:** verify events land in Firebase **DebugView** (`adb shell setprop
  debug.firebase.analytics.app net.kikin.nubecita`).

## Epic breakdown (children)

1. **`:core:analytics` module + typed framework.** Module scaffold (flavor split), `AnalyticsClient`
   interface, sealed `AnalyticsEvent`/`UserProperty`/`AnalyticsScreen` + `AnalyticsValue`, the 5 v1
   events + 3 user properties as types, `FirebaseAnalyticsClient` (production), `NoOpAnalyticsClient`
   (bench/tests), Hilt DI, the debug validator, unit tests. `:app` depends on it. *(ready first; all
   others depend on it.)*
2. **Manual `screen_view` (Nav3).** Disable auto reporting; `TrackScreenViews` on both `NavDisplay`
   hosts; `NavKey → AnalyticsScreen` mapping; de-dupe. *(needs 1.)*
3. **Engagement events.** Wire `view_feed`, `interact_post`, `create_post`. *(needs 1.)*
4. **Entry events.** Wire `login`, `search_perform`. *(needs 1.)*
5. **User properties.** Set `theme_preference`, `is_self_hosted`, `notifications_enabled`. *(needs 1.)*

## References

GA4/Firebase official docs informed the taxonomy and limits:
- Firebase auto-collected events — https://support.google.com/firebase/answer/9234069
- GA4 recommended events — https://support.google.com/analytics/answer/9267735
- Event naming rules + reserved names/prefixes — https://support.google.com/analytics/answer/13316687
- Collection & configuration limits (500 events, 25 params, 25 user properties, name lengths) — https://support.google.com/analytics/answer/9267744
- PII best practices — https://support.google.com/analytics/answer/6366371
- Firebase manual screenviews — https://firebase.google.com/docs/analytics/screenviews
