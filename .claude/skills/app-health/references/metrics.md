# app-health query catalog

The exact events, MCP calls, funnels, and thresholds the checkup runs. SKILL.md
is the procedure; this is *what* to query. Grounded in the app's real event
taxonomy (`core/analytics/AnalyticsEvent.kt`) — if that file gains events, add
them here.

## Contents
- [Conventions](#conventions)
- [1. Auth & login](#1-auth--login)
- [1a. Spurious logouts](#1a-spurious-logouts--read-this-before-judging-the-number)
- [1b. Refresh-POST rate](#1b-refresh-post-rate--the-exposure-metric)
- [2. Engagement](#2-engagement)
- [3. Paywall & Pro](#3-paywall--pro)
- [4. Stability](#4-stability)
- [4a. Enriching an issue with the `android` CLI](#4a-enriching-an-issue-with-the-android-cli)
- [Week-over-week method](#week-over-week-method)
- [Flag thresholds](#flag-thresholds)

## Conventions

**GA4 (analytics-mcp).** All product signals are **custom events** (see the wire
names below). Query with `mcp__analytics-mcp__run_report`:
- `date_ranges`: two ranges — current + prior (equal length). The tool returns
  both side by side, which gives the WoW delta in one call.
- `dimensions`: usually `eventName` (and `customEvent:<param>` for a breakdown —
  see the param note).
- `metrics`: `eventCount`, `activeUsers`, `totalUsers`, `eventCountPerUser`.
  ⚠️ The per-user metric is **`eventCountPerUser`**. `eventCountPerActiveUser` does not
  exist and GA4 rejects the whole request with a 400 (*"Did you mean eventCountPerUser?"*).
- `dimension_filter`: restrict to the event(s) for the section.
- ⚠️ **`mcp__analytics-mcp__run_funnel_report` could not be made to work** (2026-11-02).
  `steps` is rejected (wants `funnel_steps`); `funnel_steps` with `event_name` is rejected
  (*"must contain either 'filter_expression' or 'event' key"*); wrapping it as
  `{"event": {"event_name": …}}` then fails with *"Failed to initialize FunnelEventFilter"*.
  **Build funnels from per-step `activeUsers` instead** — one `run_report` with an `inList`
  filter over the step events, then read the user counts in order. That gives the same
  user-level drop-off, just without GA's own funnel attribution. Note it under Data gaps.

**Param-level breakdowns need registered custom dimensions, and the dimension
name is NOT always the param name.** GA4 only splits by an event param if it's
registered as a custom dimension. **Always run
`mcp__analytics-mcp__get_custom_dimensions_and_metrics` first** and use the exact
`api_name` it returns — several dims are renamed vs the Kotlin param key. As of
last run the registered event-scoped dims include (confirm each run, they can
change):

| use it for | dimension `api_name` |
|---|---|
| login_error reason · session_cleared reason | `customEvent:reason` |
| login_error stage | `customEvent:stage` |
| session_read_error cause | `customEvent:cause` |
| login_redirect kind | `customEvent:redirect_kind` |
| view_feed feed_type | `customEvent:feed_type` |
| **interact_post action** | `customEvent:action_type` (not `action`) |
| **interact_post / video / paywall surface, paywall source** | `customEvent:source_surface` (not `surface`) |
| **interact_feed action** | `customEvent:feed_action` |
| **search_perform scope** | `customEvent:search_scope` |
| **session_cleared age bucket** | `customEvent:days_since_login` (§1a — text buckets, usable from 1.333.0) |
| paywall plan | `customEvent:plan` |
| paywall_restore outcome | `customEvent:outcome` |
| pip outcome | `customEvent:pip_outcome` |

There's also a **`customUser:is_pro`** user-scoped dim — useful to segment any
metric by Pro vs free. If a param isn't registered, report the event total and
note "breakdown unavailable (dimension not registered)" rather than failing.

**Rates over counts.** Traffic grows; counts grow with it. Prefer
`eventCountPerUser` or an explicit ratio (errors ÷ attempts) so a healthy
rate isn't mistaken for a regression. **Always pull total active users first**
(one `run_report`, no event filter, metric `activeUsers`) — it's the denominator
*and* the sanity check: a broad count drop that matches the active-user drop is a
traffic story, not a product regression.

**`prior = 0` usually means new instrumentation, not a surge.** If an event has
0 in the prior window and a healthy count now, the event was almost certainly
just deployed (e.g. `video_play` went 0→231 the week it shipped). Flag it as
"new this week — no baseline," not "+∞%". Cross-check first-seen against recent
releases if unsure.

**Prefer batching.** One `run_report` with an `inList` filter over several event
names is cheaper than one call per event.

---

## 1. Auth & login

Answers: are people getting logged in, and are we logging anyone out we
shouldn't?

**Events** (`eventName`):
| event | params | meaning |
|---|---|---|
| `login` | `method` (oauth) | successful sign-in |
| `login_error` | `reason`, `stage` | failed attempt |
| `login_redirect_launched` | — | OAuth redirect opened |
| `login_redirect_returned` | `redirect_kind` (applink/custom_scheme) | came back from redirect |
| `session_cleared` | `reason` (invalid_grant/user_sign_out/unknown), `days_since_login` | logout (spurious if invalid_grant) |
| `session_read_error` | `cause` (io/security/serialization) | encrypted session couldn't be read |
| `session_read_error_terminal` | `cause` | read failed after retries → sign-out |
| `auth_keyset_regenerated` | — | Tink keyset rebuilt (destructive) |

**`login_error` reasons** (for the breakdown): `handle_not_found`, `network`,
`oauth_config`, `unexpected`. **stages**: `begin`, `complete`.

**Queries:**
- **Don't report raw `login_error` count as a failure rate — split by `reason`
  and `stage` first.** The count is dominated by **`handle_not_found` at the
  `begin` stage**, which is just users mistyping their handle before login starts
  — benign, and it inflates the naive `login_error ÷ (login+login_error)` rate
  into something scary and meaningless (a user typos 3× then succeeds once). Seen
  live: 155 of 156 errors were `handle_not_found`/begin, with **zero**
  completion-stage failures — i.e. auth was healthy despite the big number.
- The **real** auth-failure signal is errors at the **`complete`** stage and any
  reason that isn't `handle_not_found` (`oauth_config`, `network`, `unexpected`).
  Report *that* rate. `oauth_config` is a server/config problem (act on it);
  `network` is usually user-side. Ignore `reason=(not set)` rows — those are older
  builds before the dimension was populated.
- **Check impacted *users*, not just event count.** One user in a retry loop can
  dominate the total: 138 `login_error` events landed on 1.330.2 on 2026-08-02
  from a very small number of users. A big count from one user is a support
  question; a small count spread across many is a product problem. They need
  opposite responses, and the raw number cannot tell them apart.
- Login success per active user is a fine coarse "are people getting in" check.
- **Auth redirect funnel** (per-step `activeUsers`, ordered — see the funnel note above):
  `login_redirect_launched` → `login_redirect_returned` → `login`. Big drop
  `launched→returned` = users not completing the browser redirect (e.g. the
  debug-build App Link break, or a broker misconfig).
- **Spurious logouts** — the headline auth-health metric (epic nubecita-09xt):
  `session_cleared` where `reason=invalid_grant`, per active user, WoW. Any
  sustained rise is 🔴 — it means real users are being kicked out. Pair with
  `session_read_error_terminal` and `auth_keyset_regenerated` (both should be
  near-zero; a rise signals the encrypted-session path is failing).

### 1b. Refresh-POST rate — the exposure metric

Shipped in **1.351.1** (`TokenRefreshTelemetry`, nubecita-z2gt). Earlier builds
send nothing, so always split by `appVersion` and read only instrumented ones.

```
event: token_refresh
dimensions: customEvent:outcome, customEvent:refresh_caller, customEvent:foreground, appVersion
metric: eventCount, activeUsers
```

`outcome`: `ok` (rotated), `nonce_challenge` (routine — see below),
`invalid_grant`, `rejected`, `server_error`, `not_sent` (failed before reaching
the server — the token is provably intact), `lost_response` (**failed after
sending — the server may have consumed the token; this is the precursor of a
spurious logout**). `refresh_caller`: `dm_poll`, `widget`, `push_registration`,
`untagged`.

**Count `outcome=ok` ONLY, or every number doubles.** The event fires once per
POST and a rotation takes two: the auth server rejects the first with
`use_dpop_nonce` (`outcome=nonce_challenge`), the SDK retries with the nonce,
and the second succeeds. The challenge is a 400 returned *before* the grant is
processed, so it consumes no refresh token and carries no logout risk. The
pairing is visible in the data as near-identical per-caller counts — untagged
foreground 296 `ok` / 295 `nonce_challenge`, widget 228/229, dm_poll 188/188.

**`untagged` is mostly NOT untagged background work — split it by
`customEvent:foreground`.** The flag is sampled from process importance when
the attempt *finishes*, so `untagged` + `foreground=true` is ordinary app use
(someone opened the app on an expired access token), which is unavoidable and
not a target. Only `untagged` + `foreground=false` is an unattributed
background caller, and it is small.

Two numbers to report:

1. **`lost_response` per active user.** Any non-zero value is worth naming,
   because each one is a session that may already be dead. Pair it with the
   `session_cleared` invalid_grant count — a `lost_response` followed by a
   clear on the same build is the mechanism caught in the act.
2. **Rotations per user per day** = `ok` eventCount ÷ activeUsers ÷ days. Every
   rotation is one chance to lose the response, so this is the exposure, and
   the caller split says what is driving it.

Measured 2026-10-27 → 11-01, `ok` only, decomposed:

| caller | rotations | share | reading |
|---|---|---|---|
| `untagged` + `foreground=true` | 296 | 39% | ordinary app use — not a target |
| `widget` | 228 | 30% | background |
| `dm_poll` | 188 | 25% | background |
| `untagged` + `foreground=false` | 38 | 5% | unattributed background |

**750 `ok` across 10 users = ~10.7 rotations per user per day; 61% of it is
background.** That answers spike nubecita-bhqv, whose own threshold was
"~2/day, don't bother; ~40/day, that's the whole ballgame".

Two conclusions worth not re-deriving:

- **Consolidating the background workers onto a shared token buys nothing.**
  atproto-kotlin's `DpopAuthProvider.refreshTokensSingleFlight` already does
  in-flight dedup (tokens changed while this caller waited on the mutex) and
  cross-instance dedup (`adoptStoredSessionIfRotated` reads the shared store),
  and both workers run in the app process sharing one `XrpcClient`. The rate is
  access-token-*expiry* driven — the cost of keeping the session warm around
  the clock — so the lever is **fewer warm hours**: don't run background work
  nobody consumes (nubecita-kvh0).
- **The 5% untagged background has no identified caller and does not need
  one.** `NubecitaFcmService.onMessageReceived` does no network, and
  `WearSyncRequestHandler` delegates its only network to the already-tagged
  widget worker. At ~1/user/day it is consistent with a refresh that starts in
  the foreground and completes after the app backgrounds.

`lost_response` was 0 again that week (`not_sent` 1), so the mechanism itself
is still not directly observed — the denominator is only 10 instrumented
users.

### 1a. Spurious logouts — read this before judging the number

**A reported logout is not necessarily a logged event. Check `first_open` and
`app_remove` on that device FIRST.** A reinstall, a downgrade (Play forces an
uninstall for those) or a second profile wipes the encrypted DataStore, and a
fresh install has no session to clear — so NONE of the four auth signals
(`session_cleared`, `session_read_error`, `session_read_error_terminal`,
`auth_keyset_regenerated`) fire. The user still sees a login screen and
reports it as a logout. GA4's automatic lifecycle events are the control:

```
dimensions: dateHour, eventName, appVersion      <-- dateHour, for ordering
events: first_open, app_remove, login, login_error, login_redirect_launched,
        session_cleared, session_read_error, session_read_error_terminal,
        auth_keyset_regenerated
filter: deviceModel = <their device>
range:  a month, not a week
```

On a property this small `deviceModel` usually isolates one tester on its own
(confirm with an `activeUsers`-by-`deviceModel` query first) and is not a user
identifier, so it is the right way to pin a specific report. **No `first_open`
on the build in question ⇒ the install was continuous and the session really
was lost.** Worked example, 2026-11-03: an internal tester's "logged out two
days ago" resolved to a 1.351.0 instance that `first_open`'d at Oct 30 21:00,
was signed into at Oct 31 00:00 and `app_remove`'d the same hour, while that
device's 1.361.1 sessions ran on either side of it. The internal-track install
itself had emitted no clear in 15 days across 12 builds, and had no
`first_open` of its own. Nothing was wrong with the app.

**ALWAYS split `session_cleared` by `appVersion`. The aggregate is
meaningless.** Old builds stay in the wild for weeks and carry mechanisms that
are already fixed, and they dominate the totals. Reading the aggregate makes a
healthy release look broken. Measured 2026-08-02: 1.317.0 alone produced 8–14
invalid_grant clears in a day while the current build produced **one**. The
version split is not a refinement — it is the control group.

Query shape:

```
event: session_cleared
dimensions: date, appVersion, customEvent:reason, customEvent:days_since_login
metric: eventCount
```

**`days_since_login` is the discriminator**, and it only became usable in
**1.333.0** — earlier builds report `(not set)` or empty, because the value was
sent numerically and GA4 custom dimensions are text-only (fixed in nubecita
#834). It arrives bucketed: `0`, `1`, `2_6`, `7_13`, `14_plus`.

| bucket | reading |
|---|---|
| `14_plus` | legitimate — public-client refresh tokens expire around two weeks |
| `2_6` / `7_13` | suspicious, worth watching |
| `0` / `1` | **a healthy session was rejected.** Not expiry. Token reuse. |

**Known mechanisms and the versions that fixed them** — so residue from old
builds is not mistaken for a regression:

| mechanism | fixed in |
|---|---|
| Zombie session: app kept serving a session the SDK had cleared | nubecita 1.330.3 |
| Terminal `invalid_grant` replayed by coalesced waiters (~4 clears per logout) | atproto 9.9.2 |
| Rotation orphaned by caller cancellation (see below) | atproto 9.10.1 / nubecita 1.334.1 |

**Amplification check.** One logout should produce **one** clear. Crashlytics
groups the clear non-fatal with events / impacted users / sessions counts — if
events ≈ sessions, the coalescing fix is holding; if the event count is several
times the session count, something is duplicate-clearing again. This was ~4:1 before 9.9.2 and
is the fastest way to spot a regression of it.

**The cancellation signature.** The root cause found on 2026-08-02: a refresh
runs inside whatever coroutine needed a token, and `DmPollWorker` (a
`CoroutineWorker` polling `chat.bsky.convo.getLog` every 15 min) is stopped by
WorkManager on Doze / constraint loss / execution limit. A stop landing after
the token endpoint consumed the refresh token but before the replacement was
persisted leaves the store holding a **consumed** token; the next refresh is
rejected as reuse. In the breadcrumbs it looks like this:

```
[ChatRepository] getLog failed: Job was cancelled     <-- minutes earlier
session_start                                          <-- app reopened
session cleared - invalid_grant, days_since_login=1
```

A `Job was cancelled` from a background poller shortly before a clear is the
tell. **If short-age (`0`/`1`) invalid_grant clears keep appearing on builds
≥ 1.334.1, this is NOT that bug** — the remaining suspect is process death
between rotation and persist, which needs durability work rather than another
cancellation fix. Escalate rather than re-fixing cancellation.

**The token-endpoint timeout signature (observed 2026-11-01 on 1.351.0).** The other
short-age cause, and the one seen most recently — distinguishable from cancellation only by
reading the breadcrumbs:

```
[13:09:32] [ModerationPrefs] moderation prefs refresh failed: Refresh request failed (nonce retry)
           Caused by: java.net.SocketTimeoutException: Socket timeout has expired
           [url=https://bsky.social/oauth/token, socket_timeout=30000]
[13:09:33] [PushRegistration] (un)register failed: No authenticated session
```

with the stack running `DpopAuthProvider.refreshTokensWithNonce` →
`DpopAuthProvider.failRefresh` → `EncryptedOAuthSessionStore.clear`.

A 30s socket timeout against the token endpoint is **a lost token-endpoint response**: the
server may well have processed the refresh and consumed the token while the reply never
arrived, so the next attempt is rejected as reuse. That is the known upstream
"server deletes the session on replay" behaviour — **not client-fixable, and not a
regression of the cancellation bug**. Do not re-open cancellation work on the strength of a
short-age clear until you have checked the breadcrumbs for which of the two signatures it is.

Worth noting separately: `failRefresh` treats a *socket timeout* as terminal and clears the
session, though a timeout is not evidence of `invalid_grant`. If these keep appearing, the
tractable change is upstream in `DpopAuthProvider` (retry/soft-fail on timeout) rather than
anything in nubecita.

**Two dead ends — do not re-walk them.** (1) nubecita *does* wire the SDK's
persist-failure callback; it is `onSessionPersistFailure` on `AtOAuth`, not the
SDK's inner `onPersistFailure` name. (2) A cancelled persist can never be
observed app-side: `DpopAuthProvider` rethrows `CancellationException` without
invoking the callback, so `SessionPersistFailedException` non-fatals only ever
represent *non-cancellation* durability failures. Zero of them does not mean
zero lost rotations.

**Crashlytics beats GA4 here.** For any clear worth investigating, pull the
sample event: `customKeys.session_clear_reason` carries the reason GA4 may show
as `(not set)`, and the breadcrumb trail carries the preceding minutes. Filter
`topIssues` on NON_FATAL and look for `EncryptedOAuthSessionStore.clear`.

---

## 2. Engagement

Answers: are people using the app, and how deeply?

**Events:**
| event | breakdown dim | meaning |
|---|---|---|
| `view_feed` | `customEvent:feed_type` (following/discover/list/…) | opened a feed |
| `interact_post` | `customEvent:action_type` (like/repost/reply/quote/…), `customEvent:source_surface` | acted on a post |
| `interact_actor` | `customEvent:action_type` (follow/unfollow), `customEvent:source_surface` | followed/unfollowed someone |
| `interact_feed` | `customEvent:feed_action` | pinned/saved/reordered a feed |
| `create_post` | — | composed a post |
| `search_perform` | `customEvent:search_scope` (posts/people/feeds) | ran a search |
| `video_play` | `customEvent:source_surface` | played a video |
| `share` | `customEvent:source_surface` (also `customEvent:content_type`, `method`) | shared a post |
| `pip_attempt` | `customEvent:pip_outcome` | tried Picture-in-Picture (a Pro-gated capability) |

**Queries:**
- **Active users** (`activeUsers`, and DAU/WAU if useful) — the denominator for
  everything else; report first.
- Each event: `eventCount` + `eventCountPerUser`, WoW. The per-user view is
  what tells you whether engagement *depth* moved vs just traffic.
- `view_feed` split by `feed_type` (if registered) — following vs discover mix.
- `interact_post` split by `action_type` — likes/reposts/replies balance.
- `create_post` and `search_perform` per active user are good "are power users
  active" signals. `pip_attempt` doubles as Pro-feature-usage.

A broad, correlated drop across these (not just one) usually means a
traffic/acquisition issue, not a feature bug — call that out.

---

## 3. Paywall & Pro

Answers: how many see the upgrade prompt, and how many actually pay? GA gives
*intent*; RevenueCat gives *money*. Report them together — a healthy
`paywall_viewed` with flat RevenueCat conversions is a conversion problem, not a
visibility one.

**GA paywall funnel** (per-step `activeUsers`, ordered — see the funnel note above):
`paywall_viewed` → `paywall_plan_selected` → `paywall_checkout_started` →
(`paywall_purchase_cancelled` | `paywall_purchase_error`).
| event | breakdown dim |
|---|---|
| `paywall_viewed` | `customEvent:source_surface` (settings/pip/supporter/…) |
| `paywall_plan_selected` | `customEvent:plan` |
| `paywall_checkout_started` | `customEvent:plan` |
| `paywall_purchase_cancelled` | — |
| `paywall_purchase_error` | — |
| `paywall_restore` | `customEvent:outcome` |

- Report drop-off at each step and `paywall_viewed` by `source_surface` (which
  surface drives the opens). GA has **no purchase-success event** — success lives
  in RevenueCat (below). A high `paywall_purchase_error` rate is 🔴 (Play Billing
  / RevenueCat wiring).
- **Read exposure vs conversion separately.** Live, `paywall_viewed` was ~1/week
  against thousands of new installs — so the bottleneck was people never *reaching*
  the paywall, not failing checkout. When views are tiny, say "exposure problem"
  and don't over-analyze the (near-empty) checkout funnel. Also cross-check
  against the Play Billing crash in Stability — a billing crash silently caps
  conversion.

**RevenueCat actuals** (revenuecat MCP). Use
`mcp__revenuecat__get-overview-metrics` for the headline set, and
`mcp__revenuecat__get-chart-data` (see `mcp__revenuecat__get-chart-options-schema`
for valid `chart_name`s + resolutions) for the WoW series:
- ⚠️ **`get-overview-metrics` is a fixed 28-day window**, not the 7d WoW window —
  MRR / active subs / new customers / revenue from it are 28-day figures; label
  them as such. For a true weekly comparison use `get-chart-data` with explicit
  `start_date`/`end_date` for the current and prior weeks. Its `active_users` =
  distinct Play `appUserID`s (≈ installs), not GA active users.
- **New conversions / new customers** (actual upgrades) — pair with
  `paywall_viewed` for a true view→pay rate.
- **Trial → paid conversion**, if trials are used.
- **MRR** and **active subscriptions** (`mcp__revenuecat__list-subscriptions` for
  the current count) — the "how many are Pro" number.
- **Churn / cancellations** — `get-chart-data` churn series.
- **Restore success** — cross-check GA `paywall_restore` outcome vs RevenueCat.
- Identity is anonymous Play `appUserID` (design D3) — there's no Bluesky DID to
  report, and you shouldn't try to correlate individuals.

---

## 4. Stability

Answers: is the app crashing, and what should we fix first?

**Firebase Crashlytics (firebase MCP).** Needs the Android `appId` from preflight.
First read the guide once — call `mcp__plugin_firebase_firebase__firebase_read_resources`
with `uris: ["firebase://guides/crashlytics/reports"]` (the report tool asks for it).

- ✅ **Crash-free % is returned directly — do NOT derive it.** (Corrected 2026-11-02; this
  section previously said no such report existed and gave a GA-based estimate recipe. That
  was wrong and produced a fuzzier number than the real one.) Run
  `mcp__plugin_firebase_firebase__crashlytics_get_report` with `report: "topIssues"` and
  `filter.issueErrorTypes: ["FATAL"]`, and every group's `metrics` entry carries
  `crashFreeUsersPercentage`, `crashFreeSessionsPercentage`, `totalUsersCount` and
  `totalSessionsCount` for the filtered interval. Report those as-is.
  **A response of "This report response contains no results" means zero crashes in the
  window — i.e. 100% crash-free, a measured zero, not a data gap.** Say so plainly.
  Crashlytics `totalUsersCount` and GA4 `activeUsers` are different populations and will not
  match; don't reconcile them. The old derivation, kept only for the case where the direct
  fields are ever absent:
  - `impactedUsers = min(Σ impactedUsersCount, activeUsers)` — **clamp the sum**:
    a user can appear in several issues, so the raw sum is an upper bound and can
    exceed `activeUsers`; clamping keeps `crash-free ≥ 0`.
  - `crash-free users ≈ (activeUsers − impactedUsers) ÷ activeUsers`.
  - Optionally the same for sessions:
    `crash-free sessions ≈ (gaSessions − min(Σ sessionsCount, gaSessions)) ÷ gaSessions`
    using GA `sessions`.
  Say it's an **estimate** — Crashlytics and GA are different populations. Do the
  same for the **prior** window (set both `intervalStartTime`/`intervalEndTime`,
  ISO-8601, within 90 days) to get the WoW move — that delta is the real signal
  even if the absolute % is fuzzy.
- Run it a second time with `issueErrorTypes: ["ANR"]` — ANRs are first-class
  here (the app's startup init + battery/Doze posture make them a real risk; live
  we found a `RevenueCatInitializer` main-thread ANR). Optionally `["NON_FATAL"]`.
- The `topIssues` groups already carry everything the report needs per issue:
  `title`, `subtitle`, `impactedUsersCount`, `eventsCount`, `firstSeenVersion`,
  `signals` (e.g. `SIGNAL_FRESH` = new, `SIGNAL_EARLY` = crashes in first second,
  `SIGNAL_REGRESSED`), and a console `uri`. Rank **new/fresh or regressed issues
  hitting the most users** to the top of the fix list. Use
  `mcp__plugin_firebase_firebase__crashlytics_batch_get_events` /
  `mcp__plugin_firebase_firebase__crashlytics_list_events` only if you need a
  stack trace for one issue.

The "top crashes/ANRs to fix" list is often the single most actionable part of
the report — make each row a clear candidate: what it is, how many it hits,
whether it's growing, and where to look.

### 4a. Enriching an issue with the `android` CLI

The `android signals` command group (Android CLI, already authenticated against this
project) is **a complement to the MCP, not a replacement**. Use it for *investigation*, keep
the MCP for the *windowed WoW numbers*.

| | `android signals` | firebase MCP |
|---|---|---|
| Custom time window | ❌ fixed `--timeframe` only: `24h/7d/30d/60d/90d`, rolling from now | ✅ `intervalStartTime` / `intervalEndTime` |
| Crash-free % | ❌ not reported | ✅ direct |
| Stack + custom keys + **breadcrumb logs** | ✅ one command | ✖ needs `crashlytics_batch_get_events` |
| Version / device / API facets | ✅ one command | ✖ several reports |

Because its `7d` is rolling from *now* and the report's window is calendar-aligned, its
counts will differ slightly from the MCP's. **Never mix the two in one table** — quote MCP
numbers in the metric tables, and use the CLI only for the per-issue detail underneath.

```bash
APP=1:231886524292:android:39f7396cfb287cb01297d1   # from `android signals context`

# the issue list. Its issueId values already carry the `firebase/` prefix
# (e.g. firebase/f4234f9e1aed6f5667d0710a0b35ff88) — pass one through verbatim.
android signals crashes list --firebase-app=$APP --timeframe=7d --format=json

# the useful one: stack + KEYS (e.g. session_clear_reason) + LOGS (breadcrumbs)
android signals crashes describe <issueId> --firebase-app=$APP --timeframe=7d

# which versions an issue type is landing on
android signals crashes facets app-version --firebase-app=$APP --timeframe=7d --type=non-fatal
```

`describe` is what makes §1a's signature check cheap: it prints the breadcrumb trail for the
sample event, which is where the `Job was cancelled` vs `SocketTimeoutException` distinction
actually shows up. Run it on any session-clear issue before judging the cause.

---

## Week-over-week method

- Two equal windows: current = last N full days (exclude today), prior = the N
  days before that.
- For each metric report **current, prior, Δ absolute, Δ %**. GA4 `run_report`
  with two `date_ranges` returns both — subtract.
- Guard small numbers: a 0→3 move is "+∞%" noise, not a trend. For low-volume
  metrics (Pro conversions, rare errors) show absolute counts and say "low
  volume — interpret with care" rather than a dramatic percentage.
- A metric moving *with* active users (same per-user rate) is 🟢 even if the raw
  count changed. Always sanity-check against the active-user delta.

## Flag thresholds

Guidelines, not gospel — use judgment and the per-user-rate lens. When unsure,
🟡 and explain, rather than forcing 🟢/🔴.

| Signal | 🟡 watch | 🔴 act |
|---|---|---|
| Login success rate | drops 2–5 pts WoW | drops >5 pts, or <90% |
| `login_error` rate (per attempt) | up ~25% WoW | up >50%, or one reason dominates (esp. `oauth_config`) |
| Spurious logouts (`session_cleared` invalid_grant / user) | any sustained rise | sharp rise, or `session_read_error_terminal`/`auth_keyset_regenerated` climbing |
| Redirect funnel `launched→returned` | drop-off up ~10% | drop-off up >20% |
| Engagement per active user (broad) | one key event down ~15% | correlated drop across many, or a core event down >30% |
| Paywall view→convert (GA×RevenueCat) | down ~20% WoW | halves, or `paywall_purchase_error` a large share of checkouts |
| MRR / active subs | flat while views grow | declining WoW |
| Churn | ticks up | rising trend |
| Crash-free users % | 99.0–99.5%, or −0.2pt WoW | <99.0%, or a sharp drop |
| ANR rate | rising | a new/climbing ANR hitting many users |
| Any Crashlytics issue | steady, moderate reach | new issue spiking, or one hitting a large user share |
