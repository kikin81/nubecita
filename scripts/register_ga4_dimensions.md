# GA4 custom-dimension registration

`register_ga4_dimensions.py` registers the analytics event params defined in
[`:core:analytics/AnalyticsEvent.kt`](../core/analytics/src/main/kotlin/net/kikin/nubecita/core/analytics/AnalyticsEvent.kt)
as **event-scoped GA4 custom dimensions** on the production property (`534903647`).

## Why

GA4 surfaces an event param as a report breakdown **only** once it's registered as
a custom dimension, and registration is **forward-only** (no backfill). So a param
like `source_surface` is collected the moment the app fires it, but isn't queryable
until it's registered — and only accrues data from the registration time onward.
This script keeps the registered set in sync with the typed params and is safe to
re-run (it creates only the missing ones; custom dimensions can't be deleted, only
archived).

Tracks `nubecita-049f.10`.

## Usage

```bash
python3 -m venv /tmp/ga4venv && /tmp/ga4venv/bin/pip install google-analytics-admin
/tmp/ga4venv/bin/python scripts/register_ga4_dimensions.py            # dry-run
/tmp/ga4venv/bin/python scripts/register_ga4_dimensions.py --apply    # create missing
```

### Prerequisites

1. **Editor on the property.** The read-only analytics-MCP service account
   (`~/.config/gcloud/analytics-mcp-reader.json`, Viewer) can *list* dimensions but
   not *create* them. For an `--apply` run, grant it **Editor** in
   GA4 Admin → Property Access Management, run the script, then optionally demote it
   back to Viewer. Override the key path with `GA4_ADMIN_KEY_FILE=/path/to/key.json`.
2. **Admin API enabled:** `gcloud services enable analyticsadmin.googleapis.com --project nubecita-2a4c1`

## Adding a new param

When a new event param ships in `AnalyticsEvent.kt`, add a `(parameter_name, display_name)`
row to `DIMENSIONS` in the script and re-run with `--apply`.
