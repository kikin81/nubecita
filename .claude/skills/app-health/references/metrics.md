# app-health query catalog

The exact events, MCP calls, funnels, and thresholds the checkup runs. SKILL.md
is the procedure; this is *what* to query. Grounded in the app's real event
taxonomy (`core/analytics/AnalyticsEvent.kt`) — if that file gains events, add
them here.

## Contents
- [Conventions](#conventions)
- [1. Auth & login](#1-auth--login)
- [2. Engagement](#2-engagement)
- [3. Paywall & Pro](#3-paywall--pro)
- [4. Stability](#4-stability)
- [Week-over-week method](#week-over-week-method)
- [Flag thresholds](#flag-thresholds)

## Conventions

**GA4 (analytics-mcp).** All product signals are **custom events** (see the wire
names below). Query with `mcp__analytics-mcp__run_report`:
- `date_ranges`: two ranges — current + prior (equal length). The tool returns
  both side by side, which gives the WoW delta in one call.
- `dimensions`: usually `eventName` (and `customEvent:<param>` for a breakdown —
  see the param note).
- `metrics`: `eventCount`, `activeUsers`, `totalUsers`, `eventCountPerActiveUser`.
- `dimension_filter`: restrict to the event(s) for the section.
- Use `mcp__analytics-mcp__run_funnel_report` for the ordered funnels (auth
  redirect, paywall) so drop-off at each step is explicit.

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
| paywall plan | `customEvent:plan` |
| paywall_restore outcome | `customEvent:outcome` |
| pip outcome | `customEvent:pip_outcome` |

There's also a **`customUser:is_pro`** user-scoped dim — useful to segment any
metric by Pro vs free. If a param isn't registered, report the event total and
note "breakdown unavailable (dimension not registered)" rather than failing.

**Rates over counts.** Traffic grows; counts grow with it. Prefer
`eventCountPerActiveUser` or an explicit ratio (errors ÷ attempts) so a healthy
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
- Login success per active user is a fine coarse "are people getting in" check.
- **Auth redirect funnel** (`run_funnel_report`, ordered):
  `login_redirect_launched` → `login_redirect_returned` → `login`. Big drop
  `launched→returned` = users not completing the browser redirect (e.g. the
  debug-build App Link break, or a broker misconfig).
- **Spurious logouts** — the headline auth-health metric (epic nubecita-09xt):
  `session_cleared` where `reason=invalid_grant`, per active user, WoW. Any
  sustained rise is 🔴 — it means real users are being kicked out. Pair with
  `session_read_error_terminal` and `auth_keyset_regenerated` (both should be
  near-zero; a rise signals the encrypted-session path is failing).

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
- Each event: `eventCount` + `eventCountPerActiveUser`, WoW. The per-user view is
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

**GA paywall funnel** (`run_funnel_report`, ordered):
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
First read the guide once: `mcp__plugin_firebase_firebase__firebase_read_resources
["firebase://guides/crashlytics/reports"]` (the report tool asks for it).

- ⚠️ **There is no crash-free-% report via this MCP.** The available reports are
  `topIssues` / `topVariants` / `topVersions` / `topOperatingSystems` /
  `topAndroidDevices` — each returns `eventsCount`, `impactedUsersCount`, and
  `sessionsCount`, not a crash-free rate. So **derive** it: run
  `mcp__plugin_firebase_firebase__crashlytics_get_report` with
  `report: "topIssues"` and `filter.issueErrorTypes: ["FATAL"]` for the window,
  then estimate against the GA totals for the same window:
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
