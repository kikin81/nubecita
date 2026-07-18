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

### D3 — Promote from the internal track via `track_promote_to`

Promote each selected track from the **internal** track (where CI lands every build):

```ruby
upload_to_play_store(
  track: already_on_target ? target : "internal",
  track_promote_to: already_on_target ? nil : target,
  version_code: vc, rollout: rollout,
  in_app_update_priority: priority,
  metadata_path: nubecita_metadata_android_dir,
  skip_upload_changelogs: false,
  skip_upload_apk: true, skip_upload_aab: true,
  skip_upload_metadata: true, skip_upload_images: true, skip_upload_screenshots: true,
)
```

**Correction (verified on PR #754's live test):** an earlier revision of this design used a "direct-target" call — `track: target` with **no** `track_promote_to` — on the theory that supply would pull the artifact from the App Bundle Library. It does **not**: supply's non-promote path looks for an *existing* release on `track` to update and fails with `Unable to find the requested release on track - 'alpha'` when the build isn't already there (confirmed in `supply/uploader.rb#promote_track`). The correct primitive is `track_promote_to`, sourcing from internal.

`promote_track` reads the release from the source (internal) track and writes it to the target **without modifying the source** (`deactivate_on_promote` is an accepted option but unused in this code path), so promoting to one track leaves the build on internal and a batch can promote it to several tracks in one run. *Known limitation:* the versionCode must be a live release on internal — an explicit *older*, superseded build is not promotable; promote the latest internal build. *Advance-rollout exception:* when the build is already on production, update the existing production release in place (`track: "production"`, no `track_promote_to`).

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

- **[Promoting an old, superseded internal build fails]** → `track_promote_to` requires the versionCode to be a live release on internal; an explicit older version_code that a newer build has replaced is not promotable. Mitigation: default to the latest internal build (the resolver does), and the failure is loud (`Track 'internal' doesn't have any releases`), not silent.
- **[Direct-target attach does NOT work — resolved]** → an earlier revision used `track: <target>` with no `track_promote_to`; PR #754's live test proved supply errors `Unable to find the requested release on track` because that path updates an existing release rather than creating one. Fixed by sourcing from internal via `track_promote_to` (D3).
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

- ~~Is the closed track the default `alpha`, or a custom-named track?~~ **Resolved (2026-07-17):** Play Console shows the closed track is literally `alpha` (default), and open testing is the default `beta` (no custom-named tracks). The allowlist `%w[alpha beta production]` and the `to_closed`→`alpha` / `to_open`→`beta` mapping are correct as designed. (Authoritative programmatic recheck if ever needed: `google_play_track_version_codes(track: "beta")` via the Play-linked service account — not plain gcloud, which lacks the androidpublisher scope.)
