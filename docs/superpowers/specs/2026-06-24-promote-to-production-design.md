# Promote-to-production workflow — design

**Repo:** nubecita (Android). **Issue:** `nubecita-heij`. **Branch:** `feat/nubecita-heij-promote-to-production`.

## Purpose

Promote an **already-built, already-internal-tested** AAB from the Play **internal** track to
**production** — without rebuilding — at a **staged rollout**, carrying **localized release notes**
(en-US / es-419 / pt-BR), with an optional **in-app update priority** so a critical release can force
the IMMEDIATE in-app-update flow. A human reviews the go-live as a GitHub **deployment approval**.

This is artifact *promotion*, not a new build: re-running Gradle on a manual trigger would mint a new
AAB (same versionCode, different hash → Play rejects it; or, if commits landed, ship unvalidated code).
The Play Developer API moves the exact bytes already validated on internal.

## Grounding (current repo state)

- **`.github/workflows/release.yaml`** runs on every push to `main` (semantic-release). Its `playstore`
  job builds + uploads the **production** AAB to the **internal** track via `bundle exec fastlane internal`.
  Auth is **Workload Identity Federation** (`google-github-actions/auth@v3` with
  `workload_identity_provider` + `service_account`), and the **Appfile omits `:json_key`** so supply
  falls through to ADC (`GOOGLE_APPLICATION_CREDENTIALS`). The WIF secrets are **`release`-environment
  scoped**, with a `main`-only deployment-branch policy. The bound service account already holds Play
  **Android Publisher** permissions (it writes the internal track today).
- **`fastlane/Fastfile`** — the `internal` lane runs `gradle(task: "bundleProductionRelease")` then
  `upload_to_play_store(track: "internal", …)` with a tmpdir-synthesized **en-US-only** changelog from
  `PLAY_RELEASE_NOTES`. The `upload_screenshots` lane already uses
  `google_play_track_version_codes(track: "internal")` and a `nubecita_metadata_android_dir` helper that
  resolves `fastlane/metadata/android` (locale-rooted) robustly. `MARKETING_LOCALES = en-US, es-419, pt-BR`.
- **App locales:** the app ships `values` (en), `values-b+es+419` (es), `values-pt-rBR` (pt-BR) — the
  release-note locale set matches.
- **In-app updates** (`nubecita-cf13`, merged): `UpdatePolicy` escalates to IMMEDIATE when
  `updatePriority >= 4`; today every release is priority 0 (the `internal` lane never sets it). The
  promote workflow is the natural place to set production priority.

## Decisions (locked)

1. **Artifact promotion, no Gradle** in the promote path.
2. **Staged, parameterized rollout** — a `rollout` fraction input (default `0.1`); advance by re-running
   with a higher fraction; halt/resume in the Play Console.
3. **Auto-detect the version code, but confirm before promote** — default = latest internal versionCode
   (overridable by an explicit input); a `resolve` job echoes the resolved code + notes; a human approves
   the `promote` job before it fires.
4. **Update priority on both surfaces** — the promote workflow sets the **production** release priority;
   the `internal` lane gains an optional priority for the (rare, manual) internal case. Both default to 0.
5. **Localized release notes from committed files** — `fastlane/metadata/android/<locale>/changelogs/default.txt`
   for en-US / es-419 / pt-BR, overwritten per release; uploaded by the promote lane. The auto-internal
   path is unchanged (English only).
6. **Go-live gate = GitHub deployment approval** on a new **`production`** environment with required
   reviewers. (It cannot live on `release`: required reviewers there would pause the ~10×/day automatic
   internal releases.)

## Architecture

Two new artifacts, two small edits, one committed content tree:

| Piece | Type | Responsibility |
|---|---|---|
| `promote_production` lane (`fastlane/Fastfile`) | new | Resolve/pin a versionCode; promote internal→production (or advance an in-progress production rollout) at a `rollout`; set optional `in_app_update_priority`; upload the committed localized changelogs |
| `.github/workflows/promote.yaml` | new | `workflow_dispatch`; `resolve` job (echo) → `promote` job (gated) |
| `fastlane/metadata/android/{en-US,es-419,pt-BR}/changelogs/default.txt` | new | Committed production release notes per locale |
| `internal` lane (`fastlane/Fastfile`) | edit | Read optional `IN_APP_UPDATE_PRIORITY` env (nil → omit → Play default 0) |
| `release.yaml` | edit | Add optional `in_app_update_priority` `workflow_dispatch` input, flowed into the `internal` step's env; the automatic push path leaves it unset |

### `promote_production` lane

```ruby
desc "Promote an internal version code to production at a rollout fraction, with localized notes."
lane :promote_production do |options|
  # CLI args arrive as strings; empty string (e.g. priority:"") must normalize to nil,
  # because Ruby treats "" as truthy and `"" || fallback` would NOT fall through.
  blank = ->(v) { v.nil? || v.to_s.strip.empty? ? nil : v.to_s.strip }
  rollout  = blank.(options[:rollout]) || "0.1"          # "0.01".."1.0"
  UI.user_error!("Rollout must be in (0.0, 1.0] (got: #{rollout})") unless rollout.to_f > 0.0 && rollout.to_f <= 1.0
  priority = blank.(options[:priority])&.to_i            # nil → supply omits → Play default 0
  UI.user_error!("Priority must be 0..5 (got: #{priority})") if priority && !priority.between?(0, 5)
  vc = (blank.(options[:version_code])&.to_i || google_play_track_version_codes(track: "internal").max)&.to_i
  vc || UI.user_error!("No internal version code found to promote")

  # Every locale must have a non-empty default.txt within Play's per-locale cap,
  # else supply would ship a partial set or fail the upload mid-request.
  %w[en-US es-419 pt-BR].each do |loc|
    f = File.join(nubecita_metadata_android_dir, loc, "changelogs", "default.txt")
    text = File.exist?(f) ? File.read(f).strip : ""
    UI.user_error!("Missing/empty changelog: #{f}") if text.empty?
    UI.user_error!("Changelog #{loc} exceeds #{PLAY_CHANGELOG_MAX_CHARS} chars (#{text.length})") if text.length > PLAY_CHANGELOG_MAX_CHARS
  end

  already_prod = google_play_track_version_codes(track: "production").map(&:to_i).include?(vc)
  UI.message("#{already_prod ? "Advancing rollout for" : "Promoting"} versionCode #{vc} → production @ #{rollout}")

  upload_to_play_store(
    track: already_prod ? "production" : "internal",       # first run promotes; re-run advances rollout
    track_promote_to: already_prod ? nil : "production",
    version_code: vc,
    rollout: rollout,
    # Priority is immutable per release; only set it on the initial promote, never on a
    # rollout-advance run (re-sending it would attempt to mutate the existing release).
    in_app_update_priority: already_prod ? nil : priority,
    metadata_path: nubecita_metadata_android_dir,          # committed en-US/es-419/pt-BR changelogs
    skip_upload_changelogs: false,                         # the localized-notes upload
    skip_upload_apk: true, skip_upload_aab: true,
    skip_upload_metadata: true,                            # listing text / images untouched
    skip_upload_images: true, skip_upload_screenshots: true,
  )
end
```

- **Auth** inherits ADC/WIF via the Appfile (no `:json_key`) — identical to `internal`.
- **Auto-detect + advance** reuse the existing `google_play_track_version_codes` helper: first run promotes
  internal→production; a re-run with a higher `rollout` detects the code is already on production and
  updates the production rollout (one lane, no separate bump lane).
- **`metadata_path: nubecita_metadata_android_dir`** points at the locale-rooted committed tree
  (`fastlane/metadata/android/<locale>/…`); only changelogs upload (all other `skip_upload_*` true).

### `internal` lane edit (priority)

Read an optional priority and pass it through; unset on the automatic path → Play default 0:

```ruby
# inside lane :internal, in the upload_to_play_store(...) call
in_app_update_priority: (ENV["IN_APP_UPDATE_PRIORITY"]&.strip&.then { |v| v.empty? ? nil : v.to_i }),
```

### `release.yaml` edit (priority input)

```yaml
on:
  workflow_dispatch:
    inputs:
      in_app_update_priority:
        description: "In-app update priority for this internal upload (0 = default)"
        required: false
        default: "0"
        type: choice
        options: ["0", "1", "2", "3", "4", "5"]
  push:
    branches: [main]   # push events carry no inputs → IN_APP_UPDATE_PRIORITY empty → lane omits (0)
```
…and in the `playstore` job's "Upload AAB" step `env:`, add
`IN_APP_UPDATE_PRIORITY: ${{ inputs.in_app_update_priority }}` (empty on the push path).

### `promote.yaml`

```yaml
name: Promote to Production

on:
  workflow_dispatch:
    inputs:
      rollout:
        description: "Rollout fraction (re-run higher to advance)"
        required: true
        default: "0.1"
        type: choice            # constrained set → no fat-finger (e.g. 10 vs 1.0)
        options: ["0.01", "0.05", "0.1", "0.2", "0.5", "1.0"]
      version_code:
        description: "Play versionCode to promote (blank = latest on internal)"
        required: false
        type: string
      in_app_update_priority:
        description: "Production update priority (≥4 forces IMMEDIATE; 0 = default)"
        required: true
        default: "0"
        type: choice            # only applied on the initial promote, not on rollout-advance runs
        options: ["0", "1", "2", "3", "4", "5"]

concurrency:
  group: promote-production
  cancel-in-progress: false

permissions:
  contents: read
  id-token: write

jobs:
  resolve:
    name: Resolve version code + notes
    runs-on: ubuntu-latest
    environment: release            # reuse the WIF secrets (read-only use); no reviewer gate here
    outputs:
      version_code: ${{ steps.resolve.outputs.version_code }}
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0          # full history so `git log -1 -- <changelog>` finds the last edit
      - uses: ruby/setup-ruby@v1
        with:
          bundler-cache: true
      - name: Authenticate to Google Cloud (WIF)
        uses: google-github-actions/auth@v3
        with:
          workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}
      - name: Resolve + echo
        id: resolve
        env:
          INPUT_VERSION_CODE: ${{ inputs.version_code }}
        run: |
          # Resolve via fastlane (latest internal if blank), capture the version code,
          # then write it + the three committed changelogs to the run summary so the
          # approver sees exactly what will ship before approving the promote job.
          bundle exec fastlane resolve_promote_target version_code:"${INPUT_VERSION_CODE}" \
            | tee /tmp/resolve.log
          vc=$(grep -oE 'RESOLVED_VERSION_CODE=[0-9]+' /tmp/resolve.log | tail -1 | cut -d= -f2)
          if [ -z "$vc" ]; then
            echo "::error::Failed to resolve a version code from fastlane output."
            exit 1                       # fail resolve early, don't pass an empty code to the gated job
          fi
          echo "version_code=$vc" >> "$GITHUB_OUTPUT"
          {
            echo "## Promote target"
            echo "- versionCode: **$vc**"
            for loc in en-US es-419 pt-BR; do
              f="fastlane/metadata/android/$loc/changelogs/default.txt"
              # Surface when the notes were last edited so the approver can spot stale
              # notes (e.g. "last edited 3 weeks ago" on a fresh promote) before approving.
              edited=$(git log -1 --format='last edited %cr (%h: %s)' -- "$f")
              echo "### $loc — _${edited}_"; echo '```'; cat "$f"; echo '```'
            done
          } >> "$GITHUB_STEP_SUMMARY"

  promote:
    name: Promote v${{ needs.resolve.outputs.version_code }} → production
    needs: resolve
    runs-on: ubuntu-latest
    environment: production         # required reviewers → "Approve and deploy"
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@v7
      - uses: ruby/setup-ruby@v1
        with:
          bundler-cache: true
      - name: Authenticate to Google Cloud (WIF)
        uses: google-github-actions/auth@v3
        with:
          workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}
      - name: Promote
        run: |
          bundle exec fastlane promote_production \
            version_code:"${{ needs.resolve.outputs.version_code }}" \
            rollout:"${{ inputs.rollout }}" \
            priority:"${{ inputs.in_app_update_priority }}"
```

A small `resolve_promote_target` helper lane prints `RESOLVED_VERSION_CODE=<n>` (the same auto-detect
logic) so the `resolve` job can surface it without promoting. (`priority:""` / `version_code:""` arrive
as empty strings → the lane treats empty as nil.)

### Approval / review model — the ideal flow

Two purpose-built checkpoints, no heavyweight release PR:

1. **Notes PR (content review).** Edit `changelogs/{en-US,es-419,pt-BR}/default.txt` with this release's
   notes (English from the GitHub Release; es-419 + pt-BR translated) → PR → review the wording in the
   diff → merge to `main`.
2. **Dispatch.** Actions → *Promote to Production* → Run (rollout `0.1`; set priority `4`/`5` only for a
   critical release).
3. **`resolve`** prints versionCode + release name + the three changelogs to the run summary.
4. **Deployment approval (go-live review).** The `promote` job waits on the `production` environment; the
   reviewer opens the run, confirms the resolved code + notes, clicks **Approve and deploy** (or Reject).
5. **Staged rollout.** Promotes at 10%; re-dispatch at `0.5` / `1.0` to advance (each pauses for the same
   approval). Halt in Console anytime.

## Error handling / safety

- **Deployment approval** on `production` (the approver sees the resolved code + notes first).
- Lane **hard-errors** if no internal versionCode exists or any locale's `default.txt` is missing/empty.
- `concurrency: promote-production` prevents overlapping promotes.
- Staged **0.1** rollout caps blast radius; halt/resume in Console.
- The WIF service-account token gates the actual Play write; the input WIF values aren't load-bearing
  secrets (the GCP-side binding is).
- **Halt collision (documented behavior, not a bug):** if you HALT a rollout in the Play Console and then
  re-dispatch to advance it, supply pushes the new fraction and implicitly sets the track status back to
  `inProgress` — i.e. the action is a **hard override** that silently un-halts. Advance deliberately; to
  keep a release halted, don't re-dispatch it.

## One-time setup (manual; not code)

1. Create a **`production`** GitHub environment → **Required reviewers** (you) + deployment-branch policy
   = `main`.
2. Add `GCP_WORKLOAD_IDENTITY_PROVIDER` + `GCP_SERVICE_ACCOUNT` as **`production` environment** secrets
   (they are currently `release`-scoped; the `release` env keeps its copies for the `resolve` job).
3. **GCP-side WIF binding:** confirm the Workload Identity Pool provider's attribute condition / the SA's
   IAM `principalSet` accepts assertions from the **`production`** environment, not just `release`. If the
   binding mandates `attribute.environment == "release"` (or an `environment:release`-scoped principalSet),
   the `promote` job fails authentication until `production` is added GCP-side. (The `resolve` job runs in
   `release`, so it keeps working regardless.)
4. Confirm the WIF service account's Play Console permission is **not** restricted to the internal track
   only — it must be able to write the production track (`edits.tracks.update` on `production`).

## Testing

- The lane logic (track selection, versionCode resolution, changelog guard) is plain Ruby; it is
  exercised by a **first real gated promote** at a low rollout (e.g. `0.01`) that is immediately halted in
  Console — fastlane has no offline Play fake. The `resolve` job's echo is verifiable on any dispatch
  without promoting (it never writes to Play).
- No app/Gradle/unit/screenshot impact (no app code changes).

## Out of scope

- Machine translation of notes (committed files only); a separate `bump_rollout` lane (the single lane
  auto-detects already-on-production); changing the internal-release cadence; any non-Play distribution.

## Conventions

bd workflow (`nubecita-heij`), Conventional Commits (lowercase-leading), `Refs: nubecita-heij`;
pull-first/push-last bd/Dolt discipline (`docs/beads-multi-machine.md`).
