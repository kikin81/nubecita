## ADDED Requirements

### Requirement: Multi-track selection in a single gated dispatch

The promotion workflow SHALL let an operator select any non-empty combination of the closed testing (`alpha`), open testing (`beta`), and production tracks in one `workflow_dispatch`, via three boolean inputs (`to_closed`, `to_open`, `to_production`). The selected tracks SHALL be assembled into a comma-separated list and the whole batch SHALL pass through exactly one approval gate.

#### Scenario: Operator selects closed and open only

- **WHEN** an operator dispatches the workflow with `to_closed=true`, `to_open=true`, `to_production=false`
- **THEN** the workflow assembles the track list `alpha,beta`
- **AND** promotes the resolved versionCode to both `alpha` and `beta` after a single approval

#### Scenario: No track selected is rejected

- **WHEN** an operator dispatches the workflow with `to_closed=false`, `to_open=false`, `to_production=false`
- **THEN** the workflow fails with an error indicating at least one track must be selected
- **AND** no promotion is performed

#### Scenario: All three tracks in one dispatch, one approval

- **WHEN** an operator dispatches with all three booleans `true` and approves the gate once
- **THEN** the versionCode is promoted to `alpha`, `beta`, and `production` without requiring a second approval

### Requirement: Promotion sourced from the internal track

The system SHALL promote a build to each selected track from the **internal** track via `track_promote_to` (`upload_to_play_store(track: "internal", track_promote_to: <target>, version_code: <vc>, skip_upload_aab: true)`). supply resolves the release from the source (internal) track and copies it to the target **without removing it from internal**, so one dispatch can promote the same build to multiple tracks. The production "advance rollout" re-run is the sole exception: when the versionCode is already on production, the rollout is updated in place (`track: "production"`, no `track_promote_to`).

#### Scenario: Promote latest internal build to a new track

- **WHEN** the target versionCode is a live release on internal but not yet on the selected track
- **THEN** the system SHALL create the release on the selected track and SHALL leave the build present on internal

#### Scenario: Same build promoted to multiple tracks in one dispatch

- **WHEN** a batch promotes one versionCode to `alpha`, `beta`, and `production`
- **THEN** every track SHALL be sourced from internal successfully (promoting to one track SHALL NOT remove the build from internal for the next)

#### Scenario: Superseded internal build is not promotable

- **WHEN** an explicit older versionCode that is no longer a live internal release is requested
- **THEN** the promotion SHALL fail rather than silently promote the wrong build (promote the latest internal build instead)

#### Scenario: Version code resolution

- **WHEN** no explicit `version_code` is supplied
- **THEN** the system SHALL resolve the highest versionCode currently on the internal track as the promotion target

### Requirement: Rollout and update priority apply to production only

For the `production` track the system SHALL apply the operator-supplied staged rollout fraction and in-app-update priority. For `alpha` and `beta` the system SHALL always use a rollout of `1.0` (100% of testers) and SHALL NOT set an in-app-update priority.

#### Scenario: Mixed batch splits rollout correctly

- **WHEN** a dispatch selects all three tracks with rollout `0.1` and priority `3`
- **THEN** `alpha` and `beta` are promoted at rollout `1.0` with no priority
- **AND** `production` is promoted at rollout `0.1` with priority `3`

#### Scenario: Production update priority is set only on the initial promote

- **WHEN** a production build is re-promoted to advance its staged rollout
- **THEN** the in-app-update priority SHALL NOT be re-sent (it is immutable per release)

### Requirement: Idempotent skip for testing tracks

Because testing tracks have no staged rollout to advance, when the target versionCode is already live on a selected `alpha` or `beta` track the system SHALL skip that track (no Play API call) rather than re-promote it. Only `production` re-runs to advance its staged rollout.

#### Scenario: Re-selecting a testing track that already has the build

- **WHEN** a dispatch selects `alpha` for a versionCode already live on `alpha`
- **THEN** the system logs that the build is already on `alpha` and skips it without a Play API call

#### Scenario: Re-selecting production advances its rollout

- **WHEN** a dispatch selects `production` for a versionCode already live on `production` at a lower rollout
- **THEN** the system updates the production release to the new higher rollout fraction

### Requirement: Reused localized changelogs per track

Each promoted track SHALL upload the committed three-locale changelogs (`en-US`, `es-419`, `pt-BR`) from `fastlane/metadata/android/<locale>/changelogs/default.txt`. The system SHALL validate that each locale's changelog is present, non-empty, and within Play's per-locale character cap before promoting, and SHALL NOT touch listing text, images, or screenshots.

#### Scenario: Missing or empty changelog blocks the promotion

- **WHEN** any of the three locale changelogs is missing or empty
- **THEN** the promotion fails before any track is promoted, naming the offending locale

#### Scenario: Changelogs uploaded for every selected track

- **WHEN** a build is promoted to `alpha` and `production` in one dispatch
- **THEN** the localized changelogs are uploaded for both tracks and listing metadata/images/screenshots are left unchanged

### Requirement: Single reviewer gate reused across tracks

The promotion job SHALL run under the existing `production` GitHub environment gate for every target track, so all tracks share one required-reviewer approval. The resolve step SHALL surface the resolved versionCode, the selected track list, the rollout/priority, and the three changelogs before the gate, and the promote job's run name SHALL include the target track list.

#### Scenario: Approval surface shows the target before promoting

- **WHEN** the resolve job completes
- **THEN** the run summary shows the resolved versionCode, the selected tracks, the rollout/priority, and the three localized changelogs
- **AND** the promote job waits on the `production` environment approval before any track is promoted
