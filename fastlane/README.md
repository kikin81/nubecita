# fastlane

Fastlane orchestrates Play Console uploads (and, later, store-listing changes)
for Nubecita.

## Lanes

| Lane                     | What it does                                                                                                                                                                                                                            |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `internal`               | Builds a signed release AAB (`./gradlew bundleRelease`) and uploads it to the Play Console **internal** track. Skips listing metadata, images, and screenshots; uploads release notes from `PLAY_RELEASE_NOTES` (placeholder if unset). Optionally sets `IN_APP_UPDATE_PRIORITY`. |
| `promote`                | Promotes an existing **internal** version code (no rebuild) to one or more tracks — `alpha` (closed testing), `beta` (open testing), `production` — passed as `tracks:"alpha,beta,production"`, uploading the committed localized changelogs. Rollout/priority apply to production only; testing tracks go 100% and skip a redundant re-promote. See [Promote to production](#promote-to-production). |
| `resolve_promote_target` | Prints `RESOLVED_VERSION_CODE=<n>` for the code the `promote` lane would target (auto-detect helper for the promote workflow's confirm step). Reads nothing, uploads nothing.                                                          |

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
- `IN_APP_UPDATE_PRIORITY` — optional in-app update priority `0`–`5` for the
  internal upload (blank → omitted → Play default `0`). A manual `release`
  workflow dispatch can set it; the automatic push path leaves it unset.

## Promote to production

`promote` promotes an existing internal version code (no rebuild) to one or more
downstream tracks — `alpha` (closed testing), `beta` (open testing),
`production` — via `tracks:"…"`, uploading the committed localized changelogs.
It targets the version code directly from the App Bundle Library (no
`track_promote_to`), so a build that a newer one has superseded on internal is
still promotable. Rollout and update priority apply to **production only**;
`alpha`/`beta` always go to 100% of testers with no priority, and a build already
live on a testing track is skipped (no redundant re-promote).

```bash
# auto-detect latest internal version code, promote to production @ 10%, default priority:
bundle exec fastlane promote tracks:production
# catch up both testing tracks (100% to testers):
bundle exec fastlane promote tracks:alpha,beta
# explicit code to all three; production @ 50% force IMMEDIATE (testers still 100%):
bundle exec fastlane promote tracks:alpha,beta,production version_code:142 rollout:0.5 priority:5
```

Re-running with a higher `rollout` and `tracks:production` advances an in-progress
production rollout (the lane detects the code is already on production). Priority
is set only on the initial production promote — it is immutable per release.

Release notes are the committed files
`fastlane/metadata/android/<locale>/changelogs/default.txt` (en-US / es-419 /
pt-BR). Edit them via a PR before promoting; generic notes are intentionally
reused across routine releases, so there is no staleness check.

### CI

`.github/workflows/promote.yaml` (`workflow_dispatch`) runs a `resolve` job that
echoes the target version code + the three changelogs to the run summary, then a
`promote` job gated by the **`production`** GitHub environment (required
reviewers → "Approve and deploy"). The `resolve` job reuses the `release`
environment for read-only auth.

### One-time setup (required before first use)

1. Create a **`production`** GitHub environment → **Required reviewers** + a
   deployment-branch policy of `main`.
2. Add `GCP_WORKLOAD_IDENTITY_PROVIDER` + `GCP_SERVICE_ACCOUNT` as **`production`
   environment** secrets (they are otherwise `release`-scoped; `release` keeps its
   copies for the `resolve` job).
3. **GCP side:** ensure the Workload Identity provider's attribute condition / the
   service account's `principalSet` accepts the **`production`** environment, not
   just `release` — otherwise the `promote` job fails authentication.
4. Confirm the service account's Play Console permission can write the
   **production** track (not internal-only).

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
