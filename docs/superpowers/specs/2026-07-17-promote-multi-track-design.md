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

- **D3 — Source track is always `internal`.** Every CI build lands on `internal`
  and stays there; all promotes source from it via `track_promote_to`.

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

Rename the lane and accept a comma-separated `tracks` option. Keep the existing
version-code resolve (`nubecita_resolve_promote_version_code`), the three-locale
changelog validation, and the `already_on_target` promote-vs-advance logic —
applied **per track** in a loop.

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
    rollout  = target == "production" ? prod_rollout : "1.0"
    priority = target == "production" ? prod_priority : nil
    UI.message("#{already_on_target ? 'Advancing' : 'Promoting'} versionCode #{vc} → #{target} @ #{rollout}")
    upload_to_play_store(
      track:            already_on_target ? target : "internal",
      track_promote_to: already_on_target ? nil    : target,
      version_code: vc,
      rollout: rollout,
      in_app_update_priority: already_on_target ? nil : priority,
      metadata_path: nubecita_metadata_android_dir,
      skip_upload_changelogs: false,
      skip_upload_apk: true, skip_upload_aab: true,
      skip_upload_metadata: true, skip_upload_images: true, skip_upload_screenshots: true,
    )
  end
end
```

- Keep a thin `promote_production` alias **or** update all callers — decide in the
  plan. Current sole caller is `promote.yaml`, so updating it directly is clean.
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
  (`alpha,beta,production`) and fails if empty.
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

## Open implementation risk (verify, do not assume)

Play promotion semantics: confirm a versionCode remains promotable **from
`internal`** to a *second* track after it has already been promoted off internal
once (e.g. the same build going internal→alpha, then later internal→beta). The
current internal→production flow works, which is evidence it's fine, but the
"source = internal always" rule (D3) depends on it. If Play drops the release
from internal's active set after promotion, add a fallback that sources each
track from wherever the versionCode currently lives. **Verify with a real
dispatch** (e.g. promote a build to `alpha` only, then to `beta` only) before
relying on batch multi-track promotes.

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
