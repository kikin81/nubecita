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
  property; keep its `property_id`. (Optionally `get_property_details` /
  `get_custom_dimensions_and_metrics` to see which event params are registered
  as custom dimensions — see the catalog's note on param-level queries.)
- **Firebase/Crashlytics** — `mcp__plugin_firebase_firebase__firebase_list_apps`
  (and `firebase_get_project`) to get the Android `appId` Crashlytics needs.
- **RevenueCat** — `mcp__revenuecat__list-projects` → the nubecita project id.

If a source can't be reached, record it under "Data gaps" in the report and
skip its section rather than aborting the whole run.

### 1. Resolve the window

Compute the two date ranges (current + prior) from the window arg (default 7d)
in `YYYY-MM-DD`. Everything downstream uses these.

### 2. Run the query battery

Work through `references/metrics.md` section by section, issuing the MCP calls it
specifies for BOTH the current and prior windows. The five areas:

1. **Auth & login** — success vs error rate, error by reason/stage, the redirect
   funnel, and the spurious-logout signals (`session_cleared` invalid_grant,
   `session_read_error*`).
2. **Engagement** — active users + the interaction events (feed views, post/actor
   interactions, posting, search, video, share, PiP).
3. **Paywall & Pro** — the GA paywall funnel *paired with RevenueCat actuals*
   (real conversions, MRR, active subs, restore success, churn). GA shows intent;
   RevenueCat shows money.
4. **Stability** — crash-free users & sessions %, ANR rate, and the top issues to
   fix (with console links).

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
- **File**: the full report to `health-reports/<YYYY-MM-DD>.md` (the run date).

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
- Active users, view_feed, interact_post/actor/feed, create_post, search, video_play, share, pip
<table>

## Paywall & Pro  🟢|🟡|🔴
- GA funnel: paywall_viewed → plan_selected → checkout_started → cancelled/error (drop-off at each step)
- RevenueCat: new conversions, trial→paid, MRR, active subs, restore success, churn
<table>

## Stability  🟢|🟡|🔴
- Crash-free users %, crash-free sessions %, ANR rate
- Top crashes/ANRs to fix (title · users affected · Δ · console link)
<table>

## Data gaps
- <any source/metric that was unavailable this run>
```

## Safety & privacy (important)

- **Read-only.** Never call a write/mutate tool on GA4, Firebase, or RevenueCat
  (no `update-*`, `create-*`, `crashlytics_update_issue`, `crashlytics_create_note`,
  etc.). This is a checkup, not a change.
- **Never commit the report.** It contains real user metrics and this is a
  **public repo**. Write it only to `health-reports/`, which must be gitignored.
  On first run, if `health-reports/` isn't in `.gitignore`, add it before writing
  (append the line `health-reports/`). Do not `git add` the report. If asked to
  share it, surface the local path or paste the summary — don't push it.
- Do not print raw user identifiers. The app's events are already bucketed/PII-free
  by design (e.g. `login_error` carries a reason, never the handle) — keep it that
  way: report rates and counts, not individuals.

## Graceful degradation

Any single source being unavailable (auth expired, headless session, MCP not
connected) must not sink the run. Note it under **Data gaps**, produce the
sections you *can*, and say the report is partial. A partial pulse beats none.
