# Nubecita — Daily App Health Brief

**Date:** 2026-07-14 · **Run:** scheduled (`nubecita-daily-brief`) · **Status:** ⚠️ Blocked — no data source

## TL;DR

The daily health check could **not pull any live data** this run. Both data sources the task
depends on — the **Firebase MCP** and the **Google Analytics MCP** — are **not connected**, and
neither shows up in the connector registry. Until one is connected, this task can only report on
what the app is *configured* to emit, not on live numbers.

## What I checked

- **Referenced "stats skill":** There is no dedicated stats/analytics *skill* in the repo. The
  closest source of truth is the analytics design doc
  (`docs/superpowers/specs/2026-05-30-app-analytics-design.md`), which I used to determine what
  metrics a health check *should* cover.
- **Firebase MCP:** not present in the available tool set.
- **Google Analytics MCP:** not present in the available tool set.
- **Connector registry:** searching `firebase`, `crashlytics`, `google analytics`, `app health`
  returned **zero** connectors — so there is nothing to suggest connecting, either.

## What the app is wired to report (once a source is connected)

Firebase project **`nubecita-2a4c1`**. Crashlytics, Analytics, and App Check are integrated.

**Auto-collected (Firebase, no code):** `first_open`, `session_start`, `user_engagement`,
`app_update`, `app_remove`, `os_update`, crashes (`app_exception`), FCM `notification_*`.

**Custom v1 events (zero-PII, typed):**
`login`, `view_feed` (feed_type), `interact_post` (action_type/source_surface),
`create_post` (has_media/is_reply/is_quote), `search_perform` (search_scope/from_recent).

**User properties:** `theme_preference`, `is_self_hosted`, `notifications_enabled`.

Note: the `bench` build flavor binds a No-Op analytics client, so only **production** builds emit
data — worth confirming the metrics you look at are from the production flavor.

## Suggested health-check metrics for future runs

Once a source is connected, a good daily brief would track:

- **Stability:** crash-free users % and crash-free sessions % (Crashlytics), top new/regressed
  crash clusters, ANR rate. Flag any day-over-day drop in crash-free %.
- **Reach:** DAU / new users / `first_open`, sessions per user (GA4).
- **Engagement loop:** `view_feed`, `interact_post`, `create_post`, `search_perform` volumes and
  trend vs. prior day.
- **Retention / releases:** adoption of the latest `app_update`, retention cohort movement.

## Recommended fix

Connect one of the following so this scheduled task can pull live data:

1. A **Firebase MCP** with access to project `nubecita-2a4c1` (Crashlytics + Analytics), and/or
2. A **Google Analytics (GA4) MCP** for the linked GA4 property.

After connecting, re-run `nubecita-daily-brief` and it will populate the metrics above.

---
*Generated autonomously; no live metrics were available this run.*
