#!/usr/bin/env python3
"""
Idempotently register Nubecita's analytics event params as GA4 custom
dimensions (event-scoped) on the production property, via the Google
Analytics Admin API. Tracks nubecita-049f.10.

WHY THIS EXISTS
  GA4 only exposes an event param as a breakdown dimension in reports once the
  param is registered as a custom dimension. Registration is forward-only (no
  backfill), so params must be registered before the data they describe is
  worth querying. This script keeps the registered set in lock-step with the
  typed params in :core:analytics/AnalyticsEvent.kt — re-run it whenever a new
  param ships (it creates only the missing ones).

PREREQUISITES (one-time)
  1. A service-account key with EDITOR on the GA4 property. The read-only
     analytics MCP key (Viewer) can LIST but not CREATE — grant it Editor in
     GA4 Admin -> Property Access Management for an --apply run, then optionally
     demote back to Viewer. Point GA4_ADMIN_KEY_FILE at the key (default below).
  2. The Admin API enabled in the GCP project:
       gcloud services enable analyticsadmin.googleapis.com --project nubecita-2a4c1
  3. pip install google-analytics-admin   (a throwaway venv is fine)

RUN
  python3 scripts/register_ga4_dimensions.py            # dry-run: list what WOULD be created
  python3 scripts/register_ga4_dimensions.py --apply    # create the missing ones

  GA4_ADMIN_KEY_FILE=/path/to/editor-key.json python3 scripts/register_ga4_dimensions.py --apply

Custom dimensions can't be deleted (only archived), so this script never mutates
an existing one — it creates only those whose parameterName isn't yet registered.
"""

import argparse
import os
import sys

from google.analytics.admin_v1beta import AnalyticsAdminServiceClient
from google.analytics.admin_v1beta.types import CustomDimension
from google.oauth2 import service_account

PROPERTY = "properties/534903647"
KEY_FILE = os.path.expanduser(
    os.environ.get("GA4_ADMIN_KEY_FILE", "~/.config/gcloud/analytics-mcp-reader.json")
)
EDIT_SCOPE = "https://www.googleapis.com/auth/analytics.edit"

# (parameter_name, display_name) — every name mirrors a param in AnalyticsEvent.kt.
# All EVENT-scoped. Booleans arrive as numeric 0/1 and read as "0"/"1" strings.
# Keep this list in sync when a new event param ships.
DIMENSIONS = [
    ("method",         "Login Method"),            # login
    ("reason",         "Login Error Reason"),       # login_error
    ("stage",          "Login Error Stage"),        # login_error
    ("feed_type",      "Feed Type"),                # view_feed
    ("action_type",    "Post Action"),              # interact_post
    ("source_surface", "Post Surface"),             # interact_post
    ("has_media",      "Post Has Media"),           # create_post
    ("is_reply",       "Post Is Reply"),            # create_post
    ("is_quote",       "Post Is Quote"),            # create_post
    ("has_external",   "Post Has Link Card"),       # create_post (nubecita-049f.9)
    ("search_scope",   "Search Scope"),             # search_perform
    ("from_recent",    "Search From Recent"),       # search_perform
    ("plan",           "Paywall Plan"),             # paywall_plan_selected / _checkout_started
    ("outcome",        "Paywall Restore Outcome"),  # paywall_restore
]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--apply", action="store_true", help="create missing dimensions (default: dry-run)")
    args = ap.parse_args()

    if not os.path.exists(KEY_FILE):
        print(f"Key file not found: {KEY_FILE}\nSet GA4_ADMIN_KEY_FILE to an Editor-scoped SA key.", file=sys.stderr)
        return 2

    creds = service_account.Credentials.from_service_account_file(KEY_FILE, scopes=[EDIT_SCOPE])
    client = AnalyticsAdminServiceClient(credentials=creds)

    existing = {d.parameter_name for d in client.list_custom_dimensions(parent=PROPERTY)}
    print(f"Property {PROPERTY}: {len(existing)} custom dimension(s) already registered.")

    missing = [(p, n) for (p, n) in DIMENSIONS if p not in existing]
    if not missing:
        print(f"Nothing to do — all {len(DIMENSIONS)} params already registered.")
        return 0

    print(f"\n{len(missing)} to create:")
    for param, name in missing:
        print(f"  - {param:<16} -> {name!r}")

    if not args.apply:
        print("\nDry-run. Re-run with --apply to create them.")
        return 0

    print()
    for param, name in missing:
        client.create_custom_dimension(
            parent=PROPERTY,
            custom_dimension=CustomDimension(
                parameter_name=param,
                display_name=name,
                scope=CustomDimension.DimensionScope.EVENT,
            ),
        )
        print(f"  created {param} ({name})")
    print(f"\nDone — created {len(missing)} dimension(s). Collection starts now (forward-only).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
