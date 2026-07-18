## Context

Nubecita's release pipeline today:

- **Every push to `main`** (semantic-release → `release.yaml`) runs `fastlane internal`, building a signed `bundleProductionRelease` AAB and uploading it to the Play **internal** track (10+×/day).
- A **manual, gated** `Promote to Production` workflow (`promote.yaml` → `fastlane promote_production`) promotes an internal versionCode to **production** at a staged rollout, uploading the localized changelogs, gated behind the `production` GitHub environment's required-reviewer approval.

The **closed** (`alpha`) and **open** (`beta`) testing tracks are fed by nothing and are stale. This change extends the existing gated-promotion machinery to target those tracks too. Full prior design record: `docs/superpowers/specs/2026-07-17-promote-multi-track-design.md`.

This is **release-tooling only** — no app code, no MVI/Compose/Hilt/Room/Coil surface. Constraints: `main` protected (feature branch + PR, squash-merge); pre-commit runs actionlint on workflow YAML; the promote path authenticates to Google Cloud via WIF and calls the Play Developer API through fastlane `supply`.

## Goals / Non-Goals

**Goals:**

- Promote an already-uploaded internal build to `alpha`, `beta`, and/or `production` on demand, selectable together in one dispatch, with no rebuild.
- One approval covers the whole batch.
- Reuse the existing changelogs, WIF auth, concurrency, and environment gate.
- Do the right thing per track automatically (testers 100%, production staged; skip redundant testing re-promotes).

**Non-Goals:**

- Auto-feeding closed/open on every CI release (promotion stays manual/gated).
- Per-track approval gates or separate GitHub environments.
- Staged rollout to testing tracks.
- Any change to the internal-upload path (`fastlane internal` / `release.yaml`).

## Decisions

### D1 — Manual, gated promote (not auto-fed)

Closed/open are selectable destinations of the existing gated workflow, not auto-published on every build. Keeps the deliberate "graduate a build" pattern and low tester-facing noise. *Alternative rejected:* auto-publish every CI build to closed/open — 10+×/day is noise and burns the graduation signal.

### D2 — One generalized workflow + one parameterized lane

Generalize `promote_production` → `promote(tracks:)` and thread a track list through `promote.yaml`. *Alternative rejected:* separate `promote-closed.yaml` / `promote-open.yaml` + per-track lanes — three copies of near-identical logic to keep in sync. The `promote_production` name is dropped (no alias); its sole caller `promote.yaml` is updated directly.

### D3 — Direct-target promotion; no source track

Promote by referencing the versionCode directly on the destination track and let `supply` pull the artifact from the App Bundle Library:

```ruby
upload_to_play_store(
  track: target, version_code: vc, rollout: rollout,
  in_app_update_priority: priority,
  metadata_path: nubecita_metadata_android_dir,
  skip_upload_changelogs: false,
  skip_upload_apk: true, skip_upload_aab: true,
  skip_upload_metadata: true, skip_upload_images: true, skip_upload_screenshots: true,
)
```

A versionCode only needs to have been *uploaded* to the app (CI guarantees it — every build lands on internal), not to still be internal's *active* release. *Alternative rejected:* `track: internal, track_promote_to: target` — fails once a newer build supersedes the target on internal ("versionCode not found in source track"). We inject changelogs ourselves (D7), so losing Play's source-track note copy costs nothing.

### D4 — Reuse the existing `production` environment gate

All target tracks flow through the existing `production` GitHub environment (single required-reviewer approval for the whole batch). *Accepted trade-off:* the "Review deployments" label reads *production* even when promoting to closed/open; the run **name** disambiguates (e.g. "Promote v1301000 to alpha,beta"). *Alternative rejected:* a new `promote` environment — cleaner label but needs manual required-reviewer setup in repo Settings.

### D5 — Multi-track via boolean checkbox inputs + single-job loop

`workflow_dispatch` has no multi-select `choice`, so expose one `boolean` per track. A shell step assembles the checked booleans into a comma list and fails if empty:

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
      echo "::error::No target tracks selected — check at least one."; exit 1
    fi
    echo "list=$JOINED" >> "$GITHUB_OUTPUT"
```

The lane loops over the list in a **single gated job** → one approval (not one per track; a matrix would force N approvals). *Alternative rejected:* matrix strategy — N approvals, worse UX.

### D6 — Rollout/priority to production only; idempotent testing skip

Per-track loop body:

```ruby
tracks.each do |target|
  already_on_target = google_play_track_version_codes(track: target).map(&:to_i).include?(vc)
  # Testing tracks are always 100% (no rollout to advance): re-promoting a build
  # already live there is a redundant Play API call that can fail as already-completed.
  next if already_on_target && target != "production"
  rollout  = target == "production" ? prod_rollout : "1.0"
  # priority is IMMUTABLE per release: set only on the initial production promote.
  priority = (target == "production" && !already_on_target) ? prod_priority : nil
  upload_to_play_store(track: target, version_code: vc, rollout: rollout,
                       in_app_update_priority: priority, ... )  # (D3 body)
end
```

*Alternative rejected:* apply the operator rollout to every track — a 10% closed-testing release makes no sense; testers should get the whole build.

### D7 — Reuse the three-locale changelogs

Validate and upload `en-US` / `es-419` / `pt-BR` `changelogs/default.txt` (present, non-empty, within Play's per-locale cap) for every selected track. Listing text/images/screenshots stay untouched. Consistent with the "release notes reused by design" convention.

## Risks / Trade-offs

- **[Direct-target attach unverified in this repo]** → supply's `track: <target>, version_code: <vc>, skip_upload_aab: true` is supply's documented "change a build's track" path, but confirm on the first real `alpha`-only dispatch that it attaches the App-Bundle-Library artifact and renders the changelogs before relying on batch promotes.
- **[Gate label says "production" for closed/open promotes]** → accepted; the run name carries the actual track list (D4).
- **[Re-sending immutable production priority on a rollout advance]** → mitigated by gating priority on `!already_on_target` (D6); only the initial production promote carries priority.
- **[Empty/oversized changelog fails mid-run]** → mitigated by validating all three locales up front, before any track is promoted (D7).
- **[Custom-named closed track]** → this design assumes the default `alpha`. If a custom closed-track id is used instead, the `PROMOTE_TRACKS` allowlist and the `to_closed`→track mapping need that exact id.

## Migration Plan

1. Land the lane + workflow + skill changes on a feature branch via PR (actionlint + commitlint pre-commit gates apply).
2. First real dispatch: `to_closed=true` only, on a current internal build → confirm it appears on closed testing in Play Console with rendered changelogs (verifies D3).
3. Then a multi-track dispatch (all three) → confirm one approval, correct rollout split (testers 100%, production staged), and idempotent skip on a re-run.
4. **Rollback:** promotion is additive and manual; if the lane misbehaves, stop dispatching and (if needed) halt/roll back the production rollout in Play Console. No app artifact changes to revert.

## Open Questions

- Is the closed track the default `alpha`, or a custom-named track? (Assumed `alpha`; confirm before/at implementation — the only value that would change the track allowlist and mapping.)
