## ADDED Requirements

### Requirement: Overlay controls MUST remain legible against arbitrary media

Controls drawn on top of video or imagery MUST carry their own backing treatment so they stay
legible against any frame, including a fully white one. A bare icon tinted `Color.White` with no
background is NOT an acceptable overlay control.

The backing treatment MUST hold a contrast ratio of at least **4.5:1** between the control's
foreground (icon and any count label) and the treated background, measured against a worst-case
white video frame.

#### Scenario: Control over a white video frame

- **WHEN** an overlay control is rendered over a fully white frame
- **THEN** its backing treatment renders behind the icon and label
- **AND** the measured foreground-to-treated-background contrast ratio is at least 4.5:1

#### Scenario: Control over a black video frame

- **WHEN** an overlay control is rendered over a fully black frame
- **THEN** the icon and label remain legible at the same 4.5:1 floor
- **AND** the control's shape boundary remains discernible from the media behind it

#### Scenario: Control over mid-tone moving content

- **WHEN** an overlay control is rendered over content whose luminance varies across the control
- **THEN** the treatment holds the 4.5:1 floor across the control's whole area
- **AND** no region of the foreground falls below the floor

### Requirement: The overlay control component set lives in `:designsystem`

`:designsystem` SHALL own the overlay control components. Feature modules SHALL consume them and
MUST NOT hand-roll an overlay control.

The component set SHALL cover both layouts already present in the app: an **icon-only** control
(the fullscreen player's back / skip / play-pause / mute / PiP buttons) and an **icon-with-count**
control stacked vertically (the trending feed's rail cells: like, repost, bookmark, reply, share,
overflow and mute).

#### Scenario: Feature module renders an overlay control

- **WHEN** a feature module needs a control over media
- **THEN** it calls a `:designsystem` overlay control composable
- **AND** it does not construct its own background, scrim, alpha, or effect for that control

#### Scenario: No hand-rolled overlay controls remain in adopted modules

- **WHEN** `:feature:videos:impl` and `:feature:videoplayer:impl` are inspected after adoption
- **THEN** no overlay control declares its own `Color.White`-tinted `IconButton` without a
  `:designsystem` backing treatment

### Requirement: The public API MUST NOT expose the backing treatment

The overlay control components' public API MUST NOT expose how a control is backed. It MUST NOT
carry a scrim color, an alpha, a blur parameter, a quality mode, or a backdrop-source handle, and
MUST NOT reference any third-party effect library type.

Callers SHALL describe what the control *is* — its icon, label, count, toggled state and action —
never how it is rendered. The component SHALL resolve its own treatment internally.

This is what allows the treatment to change — including gaining background blur in a later change,
or varying by API level or surface — without editing any call site.

#### Scenario: No treatment parameters on the public API

- **WHEN** an overlay control composable's signature is inspected
- **THEN** it exposes no parameter describing background, scrim, alpha, blur, or backdrop source

#### Scenario: Treatment changes without call-site churn

- **WHEN** the backing treatment is changed, replaced, or varied by API level
- **THEN** no feature-module source file requires an edit

#### Scenario: No effect-library type reaches feature modules

- **WHEN** feature modules are inspected for third-party blur or effect library imports
- **THEN** there are none, and no such library appears on a feature module's dependency list

### Requirement: The backing treatment MUST NOT require sampling the media behind it

The treatment MUST render correctly without reading the pixels beneath the control, and MUST NOT
depend on the media surface being capturable.

This keeps the surface type a pure playback concern: a surface may use
`SURFACE_TYPE_TEXTURE_VIEW` or `SURFACE_TYPE_SURFACE_VIEW`, and may be switched between them for
battery or compositing reasons, without affecting its controls. Content drawn by the window
compositor in a separate hardware layer — notably a `SurfaceView` — cannot be sampled from the
Compose render tree at all, so a treatment that required sampling would be unavailable on exactly
the surface that most needs legible controls.

#### Scenario: Media renders into a SurfaceView

- **WHEN** the underlying media renders into a `SurfaceView`
- **THEN** the overlay control renders its treatment correctly
- **AND** its appearance and layout are identical to the same control over a `TextureView`

#### Scenario: Surface type is flipped by a later battery pass

- **WHEN** a surface is changed from `SURFACE_TYPE_TEXTURE_VIEW` to `SURFACE_TYPE_SURFACE_VIEW`
- **THEN** its overlay controls render unchanged
- **AND** no `:designsystem` or feature-module source change is required

### Requirement: The backing treatment MUST NOT introduce per-frame GPU work

The treatment as shipped MUST be a plain draw operation. It MUST NOT capture the backdrop, sample
the render tree, or perform per-frame GPU work proportional to the media behind it.

A treatment that does introduce such work — background blur being the motivating example — MUST be
introduced by a change that carries the before/after measurement required by the
`benchmark-macrobenchmark` capability, and MUST hold the project's 120 Hz frame budget.

#### Scenario: Scrim treatment is drawn

- **WHEN** an overlay control renders
- **THEN** its treatment performs no backdrop capture and no render-tree sampling

#### Scenario: Overlay adoption does not regress frame timing

- **WHEN** the video-overlay benchmark is run before and after adoption
- **THEN** frame timing is unchanged within run-to-run noise

### Requirement: Committed screenshot baselines MUST reflect the shipped treatment

Committed screenshot baselines for overlay controls SHALL capture the treatment as users see it,
and SHALL be treated as evidence of the shipped appearance.

If a future treatment cannot be rendered by the screenshot host — a platform blur being the
motivating example — the baselines MUST NOT be regenerated in an attempt to capture it, that
treatment MUST be verified on a device instead, and the discrepancy MUST be recorded so a baseline
is not mistaken for the shipped look.

#### Scenario: Screenshot test renders an overlay control

- **WHEN** an overlay control screenshot test runs on the host
- **THEN** the rendered baseline matches what ships on a device
- **AND** the test passes without requiring any platform effect

#### Scenario: A host-unrenderable treatment is introduced later

- **WHEN** a treatment the host cannot render is added
- **THEN** it is verified on a physical device or emulator
- **AND** the committed baselines are not regenerated to chase it
