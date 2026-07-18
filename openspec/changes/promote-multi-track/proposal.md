## Why

Every push to `main` uploads a signed AAB to the Play **internal** track, and a manual gated workflow promotes an internal build to **production**. Nothing feeds the **closed** (`alpha`) and **open** (`beta`) testing tracks, so they are stale — external testers never see current builds. We want to graduate an already-uploaded internal build to closed and/or open testing on demand, reusing the exact gated-promotion machinery production already uses (no rebuild).

## What Changes

- Generalize the single-purpose `promote_production` fastlane lane into one parameterized `promote(tracks:)` lane that promotes an already-uploaded internal versionCode to any of `alpha` / `beta` / `production`, looping over the selected tracks. **BREAKING** (internal tooling only): the `promote_production` lane name is removed; its sole caller (`promote.yaml`) is updated to call `promote`.
- Switch the promotion mechanism from `track_promote_to` (which requires the build to still be the active release on a source track) to **direct-target** promotion: `upload_to_play_store(track: <target>, version_code: <vc>, skip_upload_aab: true)` pulls the artifact from the App Bundle Library. This removes the "superseded internal build is no longer promotable" failure mode.
- Add three boolean `workflow_dispatch` inputs to `promote.yaml` (`to_closed` / `to_open` / `to_production`) so a single dispatch can target any combination of tracks, assembled into a comma-separated list and passed to the lane. The existing `production` environment approval gate covers the whole batch (one approval).
- Rollout % and in-app-update priority apply to **production only**; testing tracks always go to 100% of testers with no priority. A build already live on a testing track is skipped (no redundant re-promote); only production re-runs to advance its staged rollout.
- Update the `promote-to-production` Claude skill to ask which tracks to target (multiSelect) and to only ask for rollout/priority when production is selected.

## Capabilities

### New Capabilities
- `play-track-promotion`: Gated promotion of an already-uploaded internal Play build to one or more downstream tracks (closed testing / open testing / production) in a single dispatch, with per-track rollout, priority, and idempotency rules, and reused localized changelogs.

### Modified Capabilities
<!-- None — no existing openspec capability covers release/promotion. -->

## Impact

- `fastlane/Fastfile` — `promote_production` lane → parameterized `promote` lane (direct-target, per-track loop). `resolve_promote_target` unchanged.
- `.github/workflows/promote.yaml` — new boolean inputs, a track-assembly step, run-name + summary include the track list, promote job passes `tracks:`. WIF auth, `concurrency`, and the `production` environment gate unchanged.
- `.claude/skills/promote-to-production/SKILL.md` — track-selection question + gated rollout/priority questions + updated dispatch command.
- No app code, no MVI/Compose/Hilt/Room/Coil surface touched — this is release-tooling only.
- External dependency: Google Play Developer API semantics (via fastlane `supply`); no new libraries.

## Non-goals

- Auto-feeding closed/open on every CI release (promotion stays manual and gated).
- Per-track approval gates or separate GitHub environments (one shared `production` gate).
- Staged rollout to testing tracks (they always go 100%).
- Any change to the internal-upload path (`fastlane internal` / `release.yaml`).
