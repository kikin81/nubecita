# fastlane

Fastlane orchestrates Play Console uploads (and, later, store-listing changes)
for Nubecita. This scaffold is intentionally lane-free — the `internal` lane
lands in a follow-up issue (`nubecita-kbmd.3`).

## Prerequisites

- Ruby 3.4.9 (pinned via `.ruby-version`; install via `rbenv install 3.4.9`).
- `bundle install` from the repo root.

## Invocation

All fastlane commands run through Bundler so the locked dependency tree is
honored:

```bash
bundle exec fastlane <lane>
bundle exec fastlane lanes        # list available lanes (currently empty)
```

## Google credentials

`fastlane/Appfile` resolves the Play Console service-account JSON from
`GOOGLE_APPLICATION_CREDENTIALS`. Both local and CI flows must export it; the
two flows differ only in how the file gets created.

### CI

CI auth runs through Workload Identity Federation. The
`google-github-actions/auth@v2` step writes a short-lived credentials file and
exports `GOOGLE_APPLICATION_CREDENTIALS` automatically — no further setup
needed. See the `release` workflow's `playstore` job (added in
`nubecita-kbmd.4`).

### Local

For local Play Console uploads, mint Application Default Credentials that
impersonate the same WIF-bound service account CI uses:

```bash
gcloud auth application-default login \
  --impersonate-service-account=<wif-sa-email>

export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/gcloud/application_default_credentials.json"
```

The `<wif-sa-email>` is whatever the `GCP_SERVICE_ACCOUNT` repo/environment
secret points at — ask in `#release` if you don't have it. The service account
must have the `Service Account Token Creator` role granted to your user for
impersonation to succeed.

After both commands, `bundle exec fastlane <lane>` will authenticate against
Play Console the same way CI does.
