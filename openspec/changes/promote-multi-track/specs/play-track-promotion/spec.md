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

### Requirement: Direct-target promotion without a source track

The system SHALL promote a build by referencing its versionCode directly on the target track (`upload_to_play_store(track: <target>, version_code: <vc>, skip_upload_aab: true)`), pulling the artifact from the Play App Bundle Library. It SHALL NOT use `track_promote_to` and SHALL NOT depend on the build still being the active release on any source track.

#### Scenario: Build superseded on internal is still promotable

- **WHEN** a newer build has superseded the target versionCode as internal's active release
- **THEN** the target versionCode SHALL still promote successfully to the selected track from the App Bundle Library

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
