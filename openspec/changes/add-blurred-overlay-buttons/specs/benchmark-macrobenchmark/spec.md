## ADDED Requirements

### Requirement: A Macrobenchmark SHALL cover the video overlay with chrome visible

The suite SHALL include a benchmark exercising the trending video feed with its overlay controls
visible, measuring frame timing while video plays and the user swipes between pages.

This is the worst case for any effect drawn over media: the sampled content changes every frame
AND the user is actively interacting, so a benchmark that measures static chrome over a paused
frame does not exercise the cost being guarded.

#### Scenario: Benchmark exercises the overlay under motion

- **WHEN** the video-overlay benchmark runs
- **THEN** it plays video, keeps the overlay controls visible, and swipes between pages
- **AND** it reports frame-timing metrics for that interaction

#### Scenario: Benchmark reports against the 120 Hz budget

- **WHEN** the benchmark completes
- **THEN** its frame-timing output is comparable against the project's 8.33 ms frame budget

### Requirement: A surface adopting a GPU-cost effect MUST carry a before/after measurement

A change introducing a per-frame GPU effect over animating content MUST capture the benchmark on
the **pre-change** build before the effect is adopted, and MUST re-run it after. This covers
background blur, glass, refraction and progressive blur.

An after-number alone is not evidence: without the baseline there is nothing to attribute a
regression to, and the effect's cost cannot be separated from the surface's existing cost.

#### Scenario: Effect is adopted on a surface

- **WHEN** a per-frame GPU effect is proposed for a surface
- **THEN** the benchmark is captured on the pre-change build first
- **AND** re-run after adoption
- **AND** both numbers are recorded together

#### Scenario: Measurement shows the budget is exceeded

- **WHEN** the after-measurement exceeds the frame budget on the reference device
- **THEN** the effect is not adopted
- **AND** the surface keeps its pre-change rendering or a cheaper fallback
