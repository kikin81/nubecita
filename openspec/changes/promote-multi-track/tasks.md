## 1. Fastfile — parameterized `promote` lane

- [ ] 1.1 Add a `PROMOTE_TRACKS = %w[alpha beta production].freeze` allowlist and rename `promote_production` → `promote`, accepting a comma-separated `tracks` option; parse/validate it (non-empty, all in allowlist).
- [ ] 1.2 Keep the existing versionCode resolve (`nubecita_resolve_promote_version_code`), the rollout `(0.0, 1.0]` guard, the priority `0..5` guard, and the three-locale changelog validation (present / non-empty / within cap) — run validation once, before the loop.
- [ ] 1.3 Implement the per-track loop with direct-target promotion (D3): `upload_to_play_store(track: target, version_code: vc, skip_upload_aab: true, …)` — no `track_promote_to`, no `internal` source track.
- [ ] 1.4 Apply the per-track rules (D6): production uses input rollout + priority (priority only when `!already_on_target`); alpha/beta use rollout `1.0`, priority nil; `next` (skip) when `already_on_target && target != "production"`.
- [ ] 1.5 Confirm `bundle exec fastlane lanes` lists `promote` and no longer lists `promote_production`; `resolve_promote_target` unchanged.

## 2. `promote.yaml` — multi-track inputs, assembly, loop

- [ ] 2.1 Replace/extend `workflow_dispatch` inputs with the three booleans `to_closed` / `to_open` / `to_production` (default production true), keeping `rollout`, `version_code`, `in_app_update_priority`.
- [ ] 2.2 Add the `Assemble target tracks` step (booleans → comma list, `::error::` + exit 1 when empty) exposing `steps.assemble_tracks.outputs.list`.
- [ ] 2.3 Add the track list to the resolve job's `$GITHUB_STEP_SUMMARY` block; interpolate the list into the promote job `name` (e.g. "Promote v<vc> to <list>").
- [ ] 2.4 Update the promote step to call `bundle exec fastlane promote tracks:"<list>" …`; leave WIF auth, `concurrency: promote-production`, and the `production` environment gate unchanged.
- [ ] 2.5 Verify with `actionlint` (pre-commit) that the workflow parses; sanity-check the assembly step logic locally (checked booleans → list, empty guard) with a small bash harness.

## 3. `promote-to-production` skill — track selection

- [ ] 3.1 Add a multiSelect track question (Closed / Open / Production; default Production) mapping to the three boolean `-f` inputs.
- [ ] 3.2 Gate the rollout + priority questions so they are only asked when Production is among the selected tracks.
- [ ] 3.3 Update the assembled dispatch command to pass `-f to_closed=… -f to_open=… -f to_production=…` (plus rollout/priority when production is selected), and update the run-summary hand-off copy to mention the resolved track list.

## 4. Verification (real dispatch)

- [ ] 4.1 First real dispatch `to_closed=true` only on a current internal build → confirm it lands on closed testing in Play Console with rendered changelogs (verifies direct-target attach, D3).
- [ ] 4.2 Multi-track dispatch (all three) → confirm a single approval, correct rollout split (testers 100%, production staged), and the idempotent testing-track skip on a re-run.
- [x] 4.3 Confirm the closed track id is the default `alpha` — **done (2026-07-17, Play Console)**: closed = `alpha`, open = `beta`, no custom tracks; allowlist/mapping correct as designed.
