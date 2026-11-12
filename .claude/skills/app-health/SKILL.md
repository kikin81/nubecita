---
name: app-health
description: >
  Run a read-only weekly app-health checkup for the nubecita Bluesky client —
  pulling GA4 (analytics-mcp), Firebase Crashlytics (firebase MCP), and
  RevenueCat (revenuecat MCP), computing 7-day week-over-week deltas, flagging
  regressions, and writing a LOCAL (uncommitted) markdown report plus a terminal
  summary. Use this whenever the user asks how the app is doing / app health /
  "health check" / "checkup", wants to see login or sign-in error rates, feed or
  engagement numbers, how many users see the paywall or upgrade to Pro,
  conversion / MRR / churn, crash-free rate, ANRs, or "what crashes should we
  fix" — even if they don't say the word "health". Also trigger on "how are our
  analytics / metrics looking", "any crashes to fix this week", or a request to
  review Crashlytics/GA4/RevenueCat together.
---

# app-health — analytics + crashlytics + revenuecat checkup

A read-only pulse on how the live app is doing. It gathers signals from three
places at once — Google Analytics 4 (product events), Firebase Crashlytics
(stability), and RevenueCat (real Pro revenue) — compares this week to last,
and tells you plainly what's healthy and what to fix. The value is the
*synthesis*: a login-error spike, a paywall that people see but don't convert,
and a new crash are three separate dashboards normally; here they land in one
report with a prioritized "fix this" list.

**Announce at start:** "Using the app-health skill to pull GA4 + Crashlytics +
RevenueCat and build this week's health report."

## When to use

Trigger on any request about live app health, e.g.:

- "How's the app doing?" / "run a health check" / "app checkup"
- "How many people are hitting login errors?" / sign-in failure rate
- "How's engagement / feed activity looking?"
- "How many users see the paywall? How many upgrade to Pro?" / conversion / MRR / churn
- "Any crashes we should fix?" / crash-free rate / ANRs
- "Give me the weekly analytics + crashlytics rundown"

This is a **reporting** skill — it never writes to GA4, Firebase, or RevenueCat,
and never changes app code.

## Read this first

The exact events, funnels, MCP tool calls, GA4 dimensions/metrics, and flag
thresholds live in **`references/metrics.md`** — the query catalog. Read it
before running the battery; it is the source of truth for *what* to query. This
SKILL.md is the *procedure*. Keeping them split means the catalog can grow
(new events, new thresholds) without touching the workflow.

## Time window

Default: **last 7 full days vs the prior 7 days** (week-over-week). The user may
pass a different window as an argument — e.g. `/app-health 14d` → last 14 days
vs prior 14. Always compare to an equal-length prior period so every number
carries a delta. Use *full* days (exclude today, which is partial) so the two
windows are comparable. State the exact date ranges in the report header.

## Workflow

### 0. Preflight — discover the projects (no hardcoded IDs)

This repo is public, so IDs are never baked into the skill. Discover them live,
and degrade gracefully if a source is unavailable (a headless/cron session may
not have interactive MCP auth — note it and continue with the rest):

- **GA4** — `mcp__analytics-mcp__get_account_summaries` → find the nubecita
  property; keep its `property_id`. (Optionally
  `mcp__analytics-mcp__get_property_details` /
  `mcp__analytics-mcp__get_custom_dimensions_and_metrics` to see which event
  params are registered as custom dimensions — see the catalog's note on
  param-level queries.)
- **Firebase/Crashlytics** — `mcp__plugin_firebase_firebase__firebase_list_apps`
  (and `mcp__plugin_firebase_firebase__firebase_get_project`) to get the Android
  `appId` Crashlytics needs.
- **RevenueCat** — `mcp__revenuecat__list-projects` → the nubecita project id.

If a source can't be reached, record it under "Data gaps" in the report and
skip its section rather than aborting the whole run.

### 1. Resolve the window

Compute the two date ranges (current + prior) from the window arg (default 7d)
in `YYYY-MM-DD`. Everything downstream uses these.

### 2. Run the query battery

Work through `references/metrics.md` section by section, issuing the MCP calls it
specifies for BOTH the current and prior windows. The six areas:

1. **Auth & login** — success vs error rate, error by reason/stage, the redirect
   funnel, and the spurious-logout signals (`session_cleared` invalid_grant,
   `session_read_error*`).
   **Split `session_cleared` by `appVersion` — never report the aggregate.**
   Old builds linger for weeks carrying already-fixed logout mechanisms and
   dominate the totals, so the aggregate makes a healthy release look broken.
   Pair it with `days_since_login` (usable from 1.333.0 on): `14_plus` is
   ordinary expiry, `0`/`1` means a healthy session was rejected. Catalog
   §1a has the fixed-in versions, the amplification check, and the breadcrumb
   signature to look for — read it before calling a logout number good or bad.
   When someone *reports* a logout, §1a's FIRST step is the `first_open` /
   `app_remove` check on their device: a reinstall or downgrade wipes the
   session and fires none of the four auth signals, so the absence of a
   `session_cleared` is not evidence that nothing happened.
2. **Engagement** — active users + the interaction events (feed views, post/actor
   interactions, posting, search, video, share, PiP).
3. **Paywall & Pro** — the GA paywall funnel *paired with RevenueCat actuals*
   (real conversions, MRR, active subs, restore success, churn). GA shows intent;
   RevenueCat shows money.
4. **Stability** — crash-free users & sessions %, ANR rate, and the top issues to
   fix (with console links). Crash-free % comes back **directly** from
   `crashlytics_get_report`; don't derive it. An empty report means zero crashes
   (100% crash-free), which is a result, not a gap. For any issue worth
   explaining — especially a session clear — enrich it with
   `android signals crashes describe` (catalog §4a): it prints the custom keys
   and breadcrumb logs that distinguish the two spurious-logout signatures. Keep
   CLI numbers out of the WoW tables; its window is rolling, not calendar.
5. **Network errors** — `api_error` grouped by endpoint × error code, status
   class and app version, plus the refresh and video network signals (catalog
   §5). Users affected % is the headline, not the event count. Two traps: it
   only sees HTTP error responses (timeouts and offline never reach it), and
   before the build carrying it ships there is no data — say "not shipped
   yet", not 0. Read §5's table before calling a group ours: 5xx and
   `UpstreamFailure` are usually Bluesky's side.
6. **Decision breakdowns** — the custom dimensions nothing above reads, one
   question at a time (catalog §6): how often readers hit the catch-up cap and
   on which feed, translation health by language and failure reason, which
   notification types people tap, which paywall plan is picked versus checked
   out, and the §5 error groups reshaped for the dashboard. These are product
   questions, not health checks — they get 🟢/🟡 at most, and a question whose
   event has not shipped yet says so rather than reporting 0.

Batch calls where the MCP allows. Don't invent numbers — if a metric returns
nothing, say so.

### 3. Compute deltas + verdicts

For each metric: current, prior, and the WoW delta (absolute + %). Assign a
health verdict using the thresholds in the catalog:

- 🟢 healthy / improving or within noise
- 🟡 worth watching (soft threshold crossed, or a modest adverse move)
- 🔴 action needed (hard threshold crossed, or a sharp adverse move)

Rates matter more than raw counts — a login-error *count* rising with traffic is
fine; the *rate* rising is not. Prefer per-active-user or per-attempt rates.

### 4. Synthesize the report

Write the report using the template below. Lead with the verdict and the top 3
things to fix — that's what the reader wants first. Every 🔴/🟡 needs a one-line
"why it matters" and a concrete next step (e.g. a Crashlytics issue link, or
"login_error reason=oauth_config jumped — check the client-metadata config").

### 5. Emit outputs

- **Terminal**: the health summary + the top-3 fix list + any 🔴s. Concise.
  When a spurious-logout number moves, say which app versions moved — "up 40%"
  without the version split is not a finding.
- **File**: the full report to `health-reports/<YYYY-MM-DD>.md` (the run date).
- **Dashboard**: append this week to the running Artifact titled
  **"Nubecita app health"**. Find it with the `Artifact` tool,
  `action: "list"` — its URL is deliberately not written down here, because
  this repo is public and the Artifact holds real user metrics. Ask the user
  for the link if the listing doesn't show it.

  **The `Artifact` / `ArtifactData` tools exist only in the Claude desktop
  app.** A terminal Claude Code session has neither, and the Claude Docs tools
  cannot open a Dashboard-type Artifact (they refuse it as `access`). From a
  terminal session: run the checkup, then stage in `health-reports/dashboard/`
  the new week's row *alone* as `weekly-row.json` — never a whole
  `weekly.json`, which a terminal session cannot build because it cannot read
  the live rows — plus the replacement `findings.json` and `breakdowns.json`
  (both are rewritten wholesale each run) and any page change, and hand off
  the publishing to a Claude Code session in the desktop app. That session
  does the append: read the live `weekly.json`, add the staged row, and upload
  the merged file. Its live `files/index.html` is the source of truth — read
  it there before changing the page, because a local copy goes stale as soon
  as the desktop session edits the chart.

  It is a Dashboard-type Artifact, mobile-first, and it is the thing to look at
  for trend; the markdown file stays the full written record. Keep the same
  Artifact every week so the series grows — never create a second one.

  Each completed week is a closed period, so the data is two attached JSON
  files rather than live queries:

  | dataset | file | shape |
  |---|---|---|
  | `weekly` | `weekly.json` | one row per week, metrics as columns — the trend spine |
  | `findings` | `findings.json` | this week's ranked findings, replaced each run |
  | `breakdowns` | `breakdowns.json` | this week's decision breakdowns (catalog §6), replaced each run |

  To update: read the current `weekly.json` (its url is in the `datasets/weekly`
  document, `Artifact` `action: "read"` with that `path`), append one row for
  the new week, re-upload all three files (`Artifact` `action: "publish"` with
  `asset: true`, one call each — a JSON file is approved on its own), then one
  `ArtifactData` `batch` updating `datasets/weekly`, `datasets/findings` and
  `datasets/breakdowns` with the new `url` and `updated`. The page itself needs no change: it reads
  whatever rows the file holds. Pin every write with the `if_version` you read.

  Network errors (catalog §5) add four `weekly.json` columns —
  `api_error_users_pct`, `api_error_per_user`, `api_error_5xx_share`,
  `refresh_lost_response_pct` — and their top groups go into `findings.json`
  as one finding per group worth naming (endpoint, code, users, likely cause).

  Decision breakdowns (catalog §6) add `breakdowns.json` and three headline
  columns to the week's row — `catch_up_cap_users_pct`,
  `translate_error_rate_pct`, `notification_taps_pu`. The first run that writes
  `breakdowns.json` also needs a one-time page change: a `datasets/breakdowns`
  document and one panel per question on `files/index.html` (question as the
  title, headline and verdict, then a small table of `rows` with the prior
  week beside each). After that the page reads whatever questions the file
  holds, so a new question is a catalog change only.

  Add a metric by adding its column to `weekly.json` *and* a tile or row to
  `files/index.html`; older weeks simply lack the key. Leave a metric you did
  not measure out of the row rather than writing a zero — a zero is a reading,
  an absent key is not.

  The Artifact is private to the user's Claude account. It holds real user
  metrics, so it is the one place outside `health-reports/` they live: never
  put them in the repo, and don't share the Artifact on the user's behalf.

## Report template

Use this structure (scale each section to what the data shows; drop a section
only if its source was unreachable, noting it under Data gaps):

```markdown
# Nubecita app health — <YYYY-MM-DD>
Window: <current range> vs <prior range> (Nd WoW) · Sources: GA4 / Crashlytics / RevenueCat

## Verdict: 🟢|🟡|🔴 <one line>
**Fix this week**
1. …
2. …
3. …

## Auth & login  🟢|🟡|🔴
- Login success rate, login_error rate (by top reason/stage), redirect-funnel drop-off
- Spurious logouts (session_cleared invalid_grant), session read errors
<table of metric | current | prior | Δ | verdict>

## Engagement  🟢|🟡|🔴
- Active users, view_feed, interact_post/actor/feed, create_post, search_perform, video_play, share, pip_attempt
<table>

## Paywall & Pro  🟢|🟡|🔴
- GA funnel: paywall_viewed → paywall_plan_selected → paywall_checkout_started → (paywall_purchase_cancelled | paywall_purchase_error) (drop-off at each step)
- RevenueCat: new conversions, trial→paid, MRR, active subs, restore success, churn
<table>

## Stability  🟢|🟡|🔴
- Crash-free users %, crash-free sessions %, ANR rate
- Top crashes/ANRs to fix (title · users affected · Δ · console link)
<table>

## Network errors  🟢|🟡|🔴
- api_error users affected %, per active user; 401 / other 4xx / 5xx split
- Top groups: endpoint · error code · events · users · Δ · likely cause (catalog §5)
- Newest app version vs the rest; refresh lost_response share; video network errors
<table>

## Decision breakdowns
- One line per question (catalog §6): the question, its headline, 🟢/🟡, and the
  top rows — e.g. "Catch-up cap: 2.1% of readers saw a gap (prior 1.4%), all on
  following, 1.2 per reader → gap fill not yet worth building"
- Name the decision each number points at; say "not shipped yet" or "low
  volume" where that is the honest reading

## Data gaps
- <any source/metric that was unavailable this run>
- <registered dimensions no question reads — candidates to archive>
```

## Safety & privacy (important)

- **Read-only.** Never call a write/mutate tool on GA4, Firebase, or RevenueCat
  (no `update-*`, `create-*`, `crashlytics_update_issue`, `crashlytics_create_note`,
  etc.). This is a checkup, not a change.
- **Never commit the report, and never mutate tracked files.** It holds real
  user metrics and this is a **public repo**. Before writing, **verify**
  `health-reports/` is gitignored (`git check-ignore health-reports/`). If it is
  NOT ignored, **stop and ask the user** to add `health-reports/` to `.gitignore`
  first — do not write the report, and do not edit `.gitignore` (or any tracked
  file) yourself. This skill is reporting-only; silently changing the working
  tree would be surprising and, worse, writing before the ignore exists risks
  committing real metrics to a public repo. Never `git add` the report. If asked
  to share it, surface the local path or paste the summary — don't push it.
- Do not print raw user identifiers. The app's events are already bucketed/PII-free
  by design (e.g. `login_error` carries a reason, never the handle) — keep it that
  way: report rates and counts, not individuals.

## Graceful degradation

Any single source being unavailable (auth expired, headless session, MCP not
connected) must not sink the run. Note it under **Data gaps**, produce the
sections you *can*, and say the report is partial. A partial pulse beats none.
