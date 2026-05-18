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

Auth flows through Google's Application Default Credentials (ADC). The
Appfile leaves supply's `:json_key` unset, so supply falls through to
`Google::Auth.get_application_default`, which reads
`GOOGLE_APPLICATION_CREDENTIALS` (or, if unset, the gcloud well-known path
at `~/.config/gcloud/application_default_credentials.json`). The bundled
`googleauth` (1.11.2) dispatches on the credentials file's `type`:

| Type                            | Handled? | Source                                                                 |
|---------------------------------|----------|------------------------------------------------------------------------|
| `service_account`               | ✅       | Static SA key (`gcloud iam service-accounts keys create`).             |
| `authorized_user`               | ✅       | Plain `gcloud auth application-default login` (no impersonation).      |
| `external_account`              | ✅       | WIF — `google-github-actions/auth@v2` in CI; local cred-config.        |
| `impersonated_service_account`  | ❌       | `gcloud auth application-default login --impersonate-service-account`. |

Anything in the ❌ row raises `credentials type '...' is not supported`
inside ADC and falls through to supply's interactive `json_key` prompt.

### Local

`gcloud auth application-default login --impersonate-service-account=<wif-sa>`
is the natural local-dev recipe but it writes `impersonated_service_account`,
which `googleauth` 1.11.2 can't load. Pick whichever workaround fits:

1. **Local WIF cred-config** (no SA key, recommended for WIF parity):
   ```bash
   gcloud iam workload-identity-pools create-cred-config \
     projects/<project-num>/locations/global/workloadIdentityPools/<pool>/providers/<provider> \
     --service-account=<wif-sa-email> \
     --output-file=$HOME/.config/gcloud/wif-credentials.json \
     --credential-source-file=<path-to-your-oidc-token>
   export GOOGLE_APPLICATION_CREDENTIALS=$HOME/.config/gcloud/wif-credentials.json
   bundle exec fastlane internal
   ```
   Produces an `external_account` JSON, which the table above handles.

2. **Temporary SA key** (defeats WIF locally; smallest change):
   ```bash
   gcloud iam service-accounts keys create /tmp/sa.json \
     --iam-account=<wif-sa-email>
   export GOOGLE_APPLICATION_CREDENTIALS=/tmp/sa.json
   bundle exec fastlane internal
   gcloud iam service-accounts keys delete <key-id> \
     --iam-account=<wif-sa-email>
   ```

3. **Bump `googleauth`** — check whether a newer release added
   `impersonated_service_account` support, then pin it in the repo
   Gemfile ahead of fastlane's transitive constraint.

### CI

CI auth runs through Workload Identity Federation. The
`google-github-actions/auth@v2` action writes a short-lived
`external_account` credentials file and exports
`GOOGLE_APPLICATION_CREDENTIALS`; ADC reads it on the next call and
`googleauth` handles `external_account` natively. See the `release`
workflow's `playstore` job (added in `nubecita-kbmd.4`).

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
