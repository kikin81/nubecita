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

> **Status:** local smoke testing of `bundle exec fastlane internal` is
> currently **not supported** out of the box. The bundled `googleauth`
> (1.11.2) doesn't recognize the `impersonated_service_account`
> credential type that `gcloud auth application-default login
> --impersonate-service-account=...` writes, and supply falls through to
> an interactive `json_key_file` prompt that no impersonation flow can
> satisfy. CI (which uses an `external_account` WIF JSON) is unaffected.

If you need to run the lane locally, pick one of these workarounds:

1. **Local WIF cred-config** (no SA key, recommended for WIF parity):
   ```bash
   gcloud iam workload-identity-pools create-cred-config \
     projects/<project-num>/locations/global/workloadIdentityPools/<pool>/providers/<provider> \
     --service-account=<wif-sa-email> \
     --output-file=$HOME/.config/gcloud/wif-credentials.json \
     --credential-source-file=<path-to-your-oidc-token>
   export GOOGLE_APPLICATION_CREDENTIALS=$HOME/.config/gcloud/wif-credentials.json
   ```
   Produces an `external_account` JSON that googleauth handles natively.

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
