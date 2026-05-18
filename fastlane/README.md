# fastlane

Fastlane orchestrates Play Console uploads (and, later, store-listing changes)
for Nubecita.

## Lanes

| Lane       | What it does                                                                                                                                                                                                                            |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `internal` | Builds a signed release AAB (`./gradlew bundleRelease`) and uploads it to the Play Console **internal** track. Skips listing metadata, images, and screenshots; uploads release notes from `PLAY_RELEASE_NOTES` (placeholder if unset). |

## Prerequisites

- Ruby 3.4.9 (pinned via `.ruby-version`; install via `rbenv install 3.4.9`).
- `bundle install` from the repo root.

## Invocation

All fastlane commands run through Bundler so the locked dependency tree is
honored:

```bash
bundle exec fastlane <lane>
bundle exec fastlane lanes        # list available lanes
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

## Release-build env vars

The `internal` lane needs the same four keystore env vars `:app`'s release
buildType already consumes (`app/build.gradle.kts` → `keystoreValue()`). The
lane fails fast with a clear error if any are missing:

| Env var             | Meaning                                                       |
|---------------------|---------------------------------------------------------------|
| `KEYSTORE_FILE`     | Absolute (or `~`-prefixed) path to the release keystore JKS.  |
| `KEYSTORE_PASSWORD` | Store password.                                               |
| `KEY_ALIAS`         | Key alias inside the keystore.                                |
| `KEY_PASSWORD`      | Key password (usually identical to `KEYSTORE_PASSWORD`).      |

Optional:

- `PLAY_RELEASE_NOTES` — release notes text uploaded as the `en-US`
  changelog. Truncated to Play Console's 500-char cap. If unset, a generic
  placeholder is uploaded so the track release isn't created without notes
  (real changelog plumbing lands in `nubecita-kbmd.5`).
