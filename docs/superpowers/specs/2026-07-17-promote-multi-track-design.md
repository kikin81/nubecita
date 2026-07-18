# Promote to closed / open testing (multi-track promotion)

**Date:** 2026-07-17
**Status:** Approved — ready for implementation plan

## Problem

Every push to `main` (via semantic-release → `release.yaml`) uploads a signed
`bundleProductionRelease` AAB to the Play **internal** track through
`fastlane internal`. Production is fed by a separate manual, gated
`Promote to Production` workflow (`promote.yaml` → `fastlane promote_production`),
which uses `track_promote_to` to move an already-uploaded internal versionCode to
production **without rebuilding**.

The **closed testing** (`alpha`) and **open testing** (`beta`) tracks are fed by
nothing, so they are stale. We want to graduate an internal build to closed
and/or open testing on demand, reusing the exact machinery production already
uses (`track_promote_to` + localized changelog upload + a gated approval).

## Decisions

- **D1 — Manual promote, like production.** Closed/open are *not* auto-fed on
  every release. They are selectable destinations of the existing gated promote
  workflow. This reuses the deliberate "graduate a build" pattern and keeps
  tester-facing noise low. (Rejected: auto-publish every CI build to closed/open
  — 10+×/day is noise and burns the graduation signal.)

- **D2 — One generalized workflow + one parameterized lane.** Extend
  `promote.yaml` and generalize `promote_production` → `promote(tracks:)` rather
  than cloning per-track workflows/lanes. The lane is already ~95%
  track-agnostic. (Rejected: separate `promote-closed.yaml` / `promote-open.yaml`
  — three copies of near-identical logic to keep in sync.)

- **D3 — Promote from the internal track via `track_promote_to`.** Source each
  target from `internal`: `track: "internal", track_promote_to: <target>`.
  supply's `promote_track` reads internal's live release and copies it to the
  target **without modifying internal**, so a batch can promote the same build to
  several tracks in one run. *Advance-rollout exception:* when the build is
  already on production, update it in place (`track: "production"`, no
  `track_promote_to`).
  - **⚠️ Correction (verified on PR #754's live test).** An earlier revision of
    this doc proposed "direct-target" promotion — `track: <target>` with **no**
    `track_promote_to`, on the theory supply would pull the artifact from the App
    Bundle Library. **It does not.** supply's non-promote path looks for an
    *existing* release on the target track and fails with `Unable to find the
    requested release on track - 'alpha'` when the build isn't already there
    (`supply/uploader.rb#promote_track`). The direct-target idea (adopted from a
    Gemini suggestion on PR #753) was wrong; `track_promote_to` from internal is
    required. *Known limitation of the correct approach:* the versionCode must be
    a live release on internal, so an explicit *older*, superseded build is not
    promotable — promote the latest internal build.

- **D4 — Reuse the existing `production` GitHub environment gate** for all
  targets. Single reviewer approval covers the whole batch. Accepted trade-off:
  the "Review deployments" UI label reads *production* even when promoting to
  closed/open; the run **name** disambiguates (e.g. "Promote v1301000 to
  alpha,beta"). No repo-settings changes required. (Rejected: a new `promote`
  environment — cleaner label but needs manual required-reviewer setup.)

- **D5 — Multi-track in one dispatch via boolean checkbox inputs.**
  `workflow_dispatch` has no multi-select `choice`, so we expose one `boolean`
  input per track. The `promote` job assembles the checked tracks into a list and
  the lane loops over them in a **single gated job** → one approval for the whole
  batch (not one per track; a matrix would force N approvals). A guard rejects
  "nothing checked."

- **D6 — Rollout/priority apply to production only.** You stage-rollout to real
  users, not testers. So when multiple tracks are selected:
  - `production` → `rollout` = input, `in_app_update_priority` = input
  - `alpha` / `beta` → always `rollout = 1.0` (100% of testers), priority unset
    (Play default).
  - Corollary — **idempotent skip for testing tracks.** Because alpha/beta have
    no staged rollout, there is nothing to "advance." If the versionCode is
    already live on a selected testing track, skip it (no-op) rather than
    re-promote — a redundant re-promote is a wasted Play API call and can fail as
    an already-completed release. Only `production` re-runs to advance its staged
    rollout %. (Raised by Gemini review on PR #753.)

- **D7 — Changelogs reused as-is.** Same three-locale
  (`en-US` / `es-419` / `pt-BR`) `changelogs/default.txt` validation + upload for
  every selected track, consistent with the "release notes reused by design"
  convention.

## Deliverables

### 1. `fastlane/Fastfile` — generalize `promote_production` → `promote`

Rename the lane (`promote_production` → `promote`) and accept a comma-separated
`tracks` option. Keep the existing version-code resolve
(`nubecita_resolve_promote_version_code`) and the three-locale changelog
validation. Promote each selected track **directly** (D3) in a loop, using
`already_on_target` only to (a) skip a redundant testing-track re-promote and
(b) gate the immutable production update-priority to the initial promote.

```ruby
PROMOTE_TRACKS = %w[alpha beta production].freeze  # closed / open / production

lane :promote do |options|
  tracks = nubecita_blank(options[:tracks]).to_s.split(",").map(&:strip).reject(&:empty?)
  UI.user_error!("No tracks selected") if tracks.empty?
  bad = tracks - PROMOTE_TRACKS
  UI.user_error!("Unknown track(s): #{bad.join(', ')}") unless bad.empty?

  prod_rollout = nubecita_blank(options[:rollout]) || "0.1"
  UI.user_error!("Rollout must be in (0.0, 1.0]") unless prod_rollout.to_f > 0.0 && prod_rollout.to_f <= 1.0
  prod_priority = nubecita_blank(options[:priority])&.to_i
  UI.user_error!("Priority must be 0..5") if prod_priority && !prod_priority.between?(0, 5)

  vc = nubecita_resolve_promote_version_code(options[:version_code]) ||
    UI.user_error!("No internal version code found to promote")

  # Existing three-locale changelog validation (unchanged) …

  tracks.each do |target|
    already_on_target = google_play_track_version_codes(track: target).map(&:to_i).include?(vc)
    # Testing tracks are always 100% (D6): there is no rollout to advance, so a
    # re-promote of a build already live on alpha/beta is a redundant Play API
    # call that can fail as an already-completed release. Skip it — only
    # production has a real "advance the staged rollout" re-run.
    if already_on_target && target != "production"
      UI.message("versionCode #{vc} already on #{target}; nothing to advance — skipping.")
      next
    end
    rollout = target == "production" ? prod_rollout : "1.0"
    # in_app_update_priority is IMMUTABLE per release: set it only on the INITIAL
    # promote to a track, never on a production advance-rollout re-run (Play can
    # reject a priority change mid-rollout). Testing tracks never carry priority.
    priority = (target == "production" && !already_on_target) ? prod_priority : nil
    UI.message("#{already_on_target ? 'Advancing' : 'Promoting'} versionCode #{vc} → #{target} @ #{rollout}")
    # Promote from internal via track_promote_to (D3). supply's promote_track
    # reads internal's live release and copies it to the target without modifying
    # internal, so a batch fans one build out to several tracks. The direct-target
    # path (track: <target>, no track_promote_to) was tried and FAILS — supply
    # errors "Unable to find the requested release on track" (PR #754 test).
    upload_to_play_store(
      track: already_on_target ? target : "internal",
      track_promote_to: already_on_target ? nil : target,
      version_code: vc,
      rollout: rollout,
      in_app_update_priority: priority,
      metadata_path: nubecita_metadata_android_dir,
      skip_upload_changelogs: false,
      skip_upload_apk: true, skip_upload_aab: true,
      skip_upload_metadata: true, skip_upload_images: true, skip_upload_screenshots: true,
    )
  end
end
```

- **Drop the `promote_production` name entirely** — update the sole caller
  (`promote.yaml`) to call `promote`. No alias; smaller surface, one less
  indirection. (Confirmed by Gemini review on PR #753.)
- `resolve_promote_target` lane is **unchanged**.

### 2. `.github/workflows/promote.yaml` — multi-track inputs + loop

- Replace the single `target_track` idea with three `boolean` inputs:

  ```yaml
  to_closed:     { description: "→ Closed testing (alpha)", type: boolean, default: false }
  to_open:       { description: "→ Open testing (beta)",    type: boolean, default: false }
  to_production: { description: "→ Production",             type: boolean, default: true  }
  ```

- Keep the existing `rollout`, `version_code`, `in_app_update_priority` inputs.
- A shared step assembles the checked booleans into a comma list
  (`alpha,beta,production`) and fails if empty. Reference implementation:

  ```yaml
  - name: Assemble target tracks
    id: assemble_tracks
    run: |
      TRACKS=()
      [[ "${{ inputs.to_closed }}" == 'true' ]] && TRACKS+=("alpha")
      [[ "${{ inputs.to_open }}" == 'true' ]] && TRACKS+=("beta")
      [[ "${{ inputs.to_production }}" == 'true' ]] && TRACKS+=("production")
      JOINED=$(IFS=, ; echo "${TRACKS[*]}")
      if [[ -z "$JOINED" ]]; then
        echo "::error::No target tracks selected — check at least one."
        exit 1
      fi
      echo "list=$JOINED" >> "$GITHUB_OUTPUT"
  ```

  The `promote` job then passes `tracks:"${{ steps.assemble_tracks.outputs.list }}"`
  to the lane. (Snippet from Gemini review on PR #753.)
- The `resolve` job's `$GITHUB_STEP_SUMMARY` block adds a `- tracks: **<list>**`
  line (alongside the existing versionCode + rollout/priority + the three
  changelogs).
- The `promote` job name interpolates the track list so the approval UI reads
  e.g. "Promote v1301000 to alpha,beta". It passes `tracks:"<list>"` to the lane.
- Everything else — WIF auth, `concurrency: promote-production`,
  `environment: production` gate, `resolve → promote` structure — is unchanged.

### 3. `.claude/skills/promote-to-production/SKILL.md` — track-selection step

- Add a **multiSelect** track question (Closed / Open / Production; default
  Production) mapping to the three boolean `-f` inputs.
- Gate the rollout + priority questions so they are only asked when **Production**
  is among the selected tracks (a closed-only promote shouldn't ask for a
  rollout %).
- Update the assembled dispatch command from
  `-f rollout=… -f in_app_update_priority=…` to additionally pass
  `-f to_closed=… -f to_open=… -f to_production=…`.
- Update the run-summary hand-off copy to mention the resolved track list.

## Implementation note (resolved by the first real dispatch)

PR #754's first `alpha`-only dispatch **disproved** the direct-target theory:
supply's `track: <target>` (no `track_promote_to`) path errored `Unable to find
the requested release on track - 'alpha'` because it updates an *existing* target
release rather than creating one. Corrected to `track_promote_to` sourcing from
internal (D3). `promote_track` copies internal's release to the target without
modifying internal, so batches work; the only constraint is that the versionCode
must be a live internal release (promote the latest build, not a superseded one).

## Testing / verification

- `bundle exec fastlane lanes` lists the renamed `promote` lane.
- Dry-run the input-assembly step logic (checked booleans → comma list, empty
  guard) — a `bash` unit of the step, or eyeball in a test dispatch.
- Real dispatch: promote an internal build to `alpha` only, confirm it lands on
  closed testing in Play Console and the changelogs render. Then the multi-track
  case (all three) in one dispatch → one approval, correct rollout split
  (testers 100%, production staged).
- Confirm the approval gate still fires (single approval for the batch).

## Non-goals

- No auto-feeding closed/open on every CI release (D1).
- No per-track approval gates / separate environments (D4).
- No staged rollout to testing tracks (D6).
- No changes to the internal-upload path (`fastlane internal` / `release.yaml`).
