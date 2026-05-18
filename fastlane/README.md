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

Auth flows through Google's Application Default Credentials (ADC) and the
gcloud well-known path. **Do not set `GOOGLE_APPLICATION_CREDENTIALS`** —
fastlane's `upload_to_play_store` auto-maps that env var to its
`json_key_file` parameter, which only accepts `service_account`-type JSON.
WIF (`external_account`) and local impersonation
(`impersonated_service_account`) JSONs both fail there with
"Invalid Google Credentials file provided - no credential type found."

The lane defensively `ENV.delete`s `GOOGLE_APPLICATION_CREDENTIALS` if it
notices the file isn't a `service_account` JSON; the recipes below avoid
setting it in the first place.

### Local

Mint Application Default Credentials that impersonate the same WIF-bound
service account CI uses:

```bash
gcloud auth application-default login \
  --impersonate-service-account=<wif-sa-email>
```

That command writes `~/.config/gcloud/application_default_credentials.json`
(gcloud's well-known location, which is exactly where ADC looks when
`GOOGLE_APPLICATION_CREDENTIALS` is unset). No exports needed.

The `<wif-sa-email>` is whatever the `GCP_SERVICE_ACCOUNT` repo/environment
secret points at — ask in `#release` if you don't have it. The service
account must have the `Service Account Token Creator` role granted to your
user for impersonation to succeed.

If you have a stale `GOOGLE_APPLICATION_CREDENTIALS` set from a previous
session, run `unset GOOGLE_APPLICATION_CREDENTIALS` before invoking the
lane — the Fastfile preflight will also strip it, but starting clean keeps
the logs quieter.

After the gcloud login, `bundle exec fastlane <lane>` will authenticate
against Play Console using the impersonation chain.

### CI

CI auth runs through Workload Identity Federation. The
`google-github-actions/auth@v2` action writes a short-lived
`external_account` credentials file to a temp path and exports
`GOOGLE_APPLICATION_CREDENTIALS`. The lane preflight detects the non-SA
type and unsets the env var; **kbmd.4** is responsible for copying the
temp file to the gcloud well-known path on the runner before invoking the
lane so ADC can still find it.

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

## A note on iterative local smoke testing

A successful `bundle exec fastlane internal` upload locks the current
`versionCode` on Play Console's internal track. Subsequent uploads at the
same `versionCode` are rejected — Play Console requires strictly increasing
codes within a track.

Do **not** manually bump `versionName` / `versionCode` to work around this.
Both are computed by semantic-release on `main` from Conventional Commit
history (and by `parseVersionCode()` in `app/build.gradle.kts`); a manual
bump drifts from that history and the next CI release will collide.

For repeat smoke tests against the same `versionCode`, route through Play
Console's **Internal App Sharing** instead (a separate distribution channel
that doesn't lock `versionCode`s). A future lane wrapping
`upload_to_play_store_internal_app_sharing` is the right shape; track it as
a follow-up to `nubecita-kbmd.3` if iterative debugging becomes routine.
