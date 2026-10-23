## ADDED Requirements

### Requirement: `NubecitaLinkChip` renders tappable link pills in standard and emphasized variants

`:designsystem` SHALL provide `NubecitaLinkChip(label, icon, emphasized, onClick, onLongClick, modifier)`, a pill-shaped tappable chip with an optional leading icon. The standard variant SHALL be outlined; the emphasized variant SHALL use a filled, non-`tertiary` container whose label meets WCAG AA (4.5:1). The chip SHALL have a 48dp minimum touch target and labeled click and long-click semantics.

#### Scenario: Variants render

- **WHEN** the chip is rendered with `emphasized = false` and with `emphasized = true`, each with and without an icon
- **THEN** component screenshot fixtures (`@PreviewWrapper(NubecitaComponentPreview::class)`) capture all four in light and dark

#### Scenario: Emphasized label contrast

- **WHEN** the emphasized variant is rendered in light and dark themes
- **THEN** its label-to-container contrast ratio is at least 4.5:1

#### Scenario: Touch target

- **WHEN** a chip's visual height is below 48dp
- **THEN** its interactive bounds are still at least 48dp
