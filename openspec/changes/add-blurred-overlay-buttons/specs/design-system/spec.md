## ADDED Requirements

### Requirement: Icon action controls drawn over media MUST use the shared overlay components

Feature modules MUST consume `:designsystem`'s overlay control components for any **icon action
control** drawn over media, and MUST NOT declare a bare `IconButton` tinted `Color.White` — or any
equivalent background-less control — over video or imagery.

An *icon action control* is a tappable control whose visual content is an icon, optionally with a
short count or label beneath it. The requirement deliberately does NOT extend to other chrome that
happens to sit over media — seek bars, progress indicators, author chips, captions and text
overlays — which have their own layout and legibility needs and are out of scope here. The shared components own the backing treatment
and its API-level selection. This extends the prohibition on hand-rolled styling to overlays.

This exists because the app previously carried two divergent implementations of the same idea —
`:feature:videos:impl`'s `VideoRailAction` and `:designsystem`'s horizontal `PostStat` — with a
third media surface sharing neither. Overlay legibility is a design-system concern, not a
per-feature one.

#### Scenario: Feature module renders a control over video

- **WHEN** a feature module renders an interactive control on top of a video surface
- **THEN** it calls a `:designsystem` overlay control composable
- **AND** it declares no background, scrim, alpha, or blur of its own for that control

#### Scenario: No background-less overlay controls remain

- **WHEN** `:feature:videos:impl` and `:feature:videoplayer:impl` are inspected after adoption
- **THEN** every interactive control drawn over media resolves to a `:designsystem` overlay
  component
- **AND** none renders a white-tinted icon with no backing treatment
