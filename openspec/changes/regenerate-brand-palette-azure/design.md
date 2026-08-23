# Design — Regenerate the brand palette (Azure)

## Context

`:designsystem` owns the brand palette as a hand-maintained `NubecitaPalette`
object of hex literals, from which six `ColorScheme`s are assembled: light and
dark, each at standard, medium and high contrast. `Theme.kt` selects among them
based on the resolved `AppTheme` and the OS contrast setting.

Two properties of the current state shape this design:

**The palette is genuinely the single source of truth.** No production source file
outside `:designsystem` contains a raw `Color(0x…)` literal, and only two files
(`app/Navigation.kt`, `feature/onboarding/impl/OnboardingScreen.kt`) reference
`NubecitaPalette` by name. Changing the palette therefore requires no feature-code
edits at all.

**The palette is mostly invisible.** `AppTheme.Dynamic` is the default, and on
API 31+ it takes `dynamicLightColorScheme` / `dynamicDarkColorScheme` wholesale.
The brand palette renders only for users who explicitly select Light or Dark, and
on Android 11 and earlier. This bounds the user-visible impact of the change and
also bounds the value of investing in it — but the palette remains the app's
identity in screenshots, in `@Preview`, and for anyone who opts out of Material You.

The trigger is an accessibility defect. Light `primary` sits at tonal stop 50
(`#0A7AFF`), where Material 3 specifies stop 40. Measured against the light
surface it yields 3.92:1 as text and 4.01:1 as a filled button with a white label,
both under the 4.5:1 AA minimum. `Peach50` fails the same way at 3.81:1.

## Goals / Non-Goals

**Goals:**

- Bring every foreground/background pair in all six schemes to WCAG 2.1 AA.
- Replace the clashing warm-secondary palette with a cohesive cool triad.
- Deepen the dark surface ramp for OLED power draw.
- Encode the two adjacency/usage hazards found during review as spec requirements,
  so they survive the people who found them.
- Make the contrast guarantee testable, so the next palette edit cannot silently
  reintroduce the defect.

**Non-Goals:**

- Changing the dynamic-color path.
- Repainting the launcher icon or splash background.
- Adding a fourth `AppTheme` for OLED.
- Retuning `NubecitaSemanticColors`.
- Adding a lint rule for the new usage constraints.

## Decisions

### D1 — Generate the ramps from HCT coordinates, commit the output as literals

The five tonal palettes are generated with
`@material/material-color-utilities` 0.4.0 from an (HCT hue, chroma) pair each,
and the resulting stops are committed as hex literals in `NubecitaPalette` exactly
as today.

*Alternative considered: add the Kotlin port of material-color-utilities as a
runtime dependency and derive the scheme at startup.* Rejected. It adds a
dependency and per-launch work to produce values that never change, and it would
make the committed screenshot baselines depend on a transitive library version.
Generating offline keeps the runtime identical to today — the schemes stay plain
`lightColorScheme(...)` / `darkColorScheme(...)` calls — while making every value
reproducible from a recorded coordinate rather than hand-picked.

The generator script and its coordinates are recorded in this design document
rather than checked in, matching how the existing palette's CSS reference is kept
in `openspec/references/`.

### D2 — Accent tonal stops follow Material 3; vividness is not a reason to deviate

Light accents move from stop 50 to stop 40. This is the whole of the accessibility
fix, and it is why the fix cannot be shipped separately from the palette change —
tone 40 of the new Sky ramp *is* `#0061A6`.

*Alternative considered: keep stop 50 and raise contrast by darkening `onPrimary`
instead.* Rejected. `onPrimary` is already pure white in light mode; there is
nowhere further to go. Deviating from the specified stop is what caused the defect,
and the spec now fixes the mapping explicitly.

The cost is real and should be stated: the light accent becomes visibly deeper and
less vivid than today's `#0A7AFF`. That is the price of legibility at body weight.

### D3 — Hue selection is constrained by the semantic accents, not only by taste

`NubecitaSemanticColors` carries app-level constants that are not part of the brand
ramp: `error` at HCT hue 25, `repostAccent` at 157, `likeAccent` at 356. A brand
primary landing near any of these makes a themed control read as a semantic state.

Azure's hues were chosen with that constraint applied: Sky at 255 clears error by
130°, repost by 98°, and like by 79°. Two candidates evaluated during selection
were rejected or retuned on this basis — a warm primary at hue 25 collided exactly
with `error`, and a green primary at 170 sat 13° from `repostAccent`.

### D4 — Dark surface at tone 3, container steps widened

Material 3 places dark `surface` at tone 6; the current palette already sits at
5.9, so it is compliant. Moving to tone 3 is a deliberate departure for OLED power
draw, where a darker pixel draws less current.

Pure black (tone 0) was considered and rejected: Material 3 communicates depth
through tonal elevation, and at pure black there is nothing below `surface` for
`surfaceContainerLowest` to occupy. Tone 3 keeps a floor beneath the canvas while
capturing most of the power benefit.

Because the ramp starts lower, the steps are widened (1 / 3 / 6 / 9 / 14 / 19 / 26
rather than a uniform ~4-tone stride) so each depth tier stays separable. The spec
requires a minimum 3-tone gap between adjacent tiers so this cannot silently
regress.

The semantic accents were re-measured against the new `#090B0E` surface and all
clear 7.44:1 or better, so no retuning is needed.

### D5 — Tests assert the property, not the values

`ColorSchemeTest` currently asserts specific hex literals. That test would have
passed throughout the entire lifetime of the accessibility defect — it recorded
what the palette *was*, not whether it was *correct*.

It is rewritten to compute WCAG contrast over every on/container pair in all six
schemes and assert the floor. The hex-pinning assertions are kept only for
`primary` in light and dark, where the spec names an exact value.

The new test must be confirmed to fail against the pre-change palette before the
palette is edited. A test that passes both before and after proves nothing.

### D6 — One PR, not a stack

An earlier plan split this into a `gh stack`. That is wrong here: the moment
`NubecitaPalette` changes, every module's `validate*ScreenshotTest` fails, so any
child PR that lands the palette without the regenerated baselines is red. The work
is atomic and ships as one standalone PR, with this openspec change going to `main`
on its own beforehand per the repository workflow.

### D7 — Whole-module baseline regeneration is correct here, inverting the usual rule

The repository rule is to never commit a whole baseline regeneration, because
`update*ScreenshotTest` rewrites every image in a module and macOS returns most of
them with 1/255 antialiasing noise.

A palette change inverts this: every baseline legitimately changes, so there is no
noise to separate from signal, and the triage script has nothing to triage. The
risk moves to the opposite failure — a baseline that silently retains the old
accent would read as a pass. The tasks therefore include an explicit verification
that the regenerated PNGs contain the new primary and not the old one.

## Risks / Trade-offs

**A regenerated baseline silently pins the old palette** → After regeneration,
sample the committed PNGs for the old accent `#0A7AFF` and the new `#0061A6`;
a module whose baselines still contain the former did not regenerate. CI's
`screenshot` job is the authority, since local `validate*ScreenshotTest` fails on
macOS even on a clean tree.

**The light accent is visibly less vivid than today** → Accepted, and stated in the
proposal rather than hidden. Legibility at body weight is the point of the change.
Users who prefer a brighter accent have `AppTheme.Dynamic`, which is the default.

**Deep dark is off-spec and may be "corrected" later** → The spec requirement
states the deviation and its rationale explicitly, and records that the previous
value was already spec-compliant, so a future reviewer cannot mistake it for a bug.

**Dark `primary` and `secondary` are the same tone, so accent separation is
hue-only** → Structural to Material 3, not to this palette. Mitigated by the
adjacency requirement, which forbids the one composition where it becomes
invisible. Not fully solvable within the M3 role model.

**The change is invisible to most users** → Accepted. `Dynamic` is the default and
stays untouched by choice. The value is in the accessibility fix, the identity in
store screenshots, and the palette's role as the `@Preview` and screenshot-test
ground truth.

**High-contrast variants drift from the standard scheme** → The medium and high
contrast schemes derive by `.copy()` from the standard scheme and push accents
toward the ramp ends. They are covered by the same property-based contrast test,
so a ramp that cannot supply the needed stop fails the build rather than silently
degrading.

## Migration Plan

No runtime migration. The palette is compile-time constant; users see the new
colors on first launch after update, and only if they had selected Light or Dark.
There is no persisted state tied to palette values, so rollback is a straight
revert of the implementation PR.

## Open Questions

None. The launcher-icon question was resolved in favor of keeping `#0A7AFF` as a
fixed brand mark; repainting it is deferred to its own change should it ever be
wanted.
