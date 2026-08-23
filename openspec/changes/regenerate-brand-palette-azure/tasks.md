# Tasks — Regenerate the brand palette (Azure)

Tracked as bd `nubecita-bvff`. Implementation lands as **one** standalone PR
(see design D6); this openspec change goes to `main` on its own beforehand.

Branch: `feat/nubecita-bvff-regenerate-brand-palette-azure`

## 1. Prove the guard discriminates (before touching the palette)

- [ ] 1.1 Rewrite `designsystem/src/test/kotlin/.../ColorSchemeTest.kt` to assert the contrast *property* rather than hex literals: a WCAG 2.1 helper, then a parameterised assertion over all six schemes covering every on/container pair (≥ 4.5:1) and `outline` vs `surface` (≥ 3:1). **Test updated: `ColorSchemeTest`.**
- [ ] 1.2 Run `./gradlew :designsystem:testDebugUnitTest --tests '*ColorSchemeTest*'` against the **unmodified** palette and confirm it FAILS on exactly three pairs — light `primary`/`onPrimary`, light `surface`/`primary`, light `surface`/`secondary`. Paste the failure output into the PR body. A guard that passes before the fix proves nothing (see design D5).
- [ ] 1.3 Add a `NubecitaSemanticColors` contrast case asserting `likeAccent`, `repostAccent`, `supporterAccent`, `success` and `warning` each clear 4.5:1 against dark `surface`. **Test added: `ColorSchemeTest.semanticAccentsMeetContrastOnDarkSurface`.**

## 2. Palette and color schemes

- [ ] 2.1 Replace the `Sky` ramp in `NubecitaPalette` with the HCT-generated Sky ramp (hue 255, chroma 72) and delete the now-unused stops. Keep the `Sky0`…`Sky100` naming so no call site moves.
- [ ] 2.2 Replace `Peach*` with `Lagoon*` (hue 215, chroma 40) and `Lilac*` with `Orchid*` (hue 318, chroma 45). Update the two non-`:designsystem` references — `app/src/main/java/.../Navigation.kt` and `feature/onboarding/impl/.../OnboardingScreen.kt` — if either names a renamed stop.
- [ ] 2.3 Regenerate the `Neutral*` (hue 255, chroma 5) and `NeutralVariant*` (hue 250, chroma 9) ramps.
- [ ] 2.4 Rewrite `nubecitaLightColorScheme()` at Material 3 tonal stops — accents at 40 / 100 / 90 / 10 — replacing the tone-50 assignments that caused the AA failures.
- [ ] 2.5 Rewrite `nubecitaDarkColorScheme()` with accents at 80 / 20 / 30 / 90 and the deepened surface ramp: `surface` `#090B0E`, `surfaceContainerLowest` `#030406`, `surfaceContainerLow` `#111317`, `surfaceContainer` `#171A1D`, `surfaceContainerHigh` `#222427`, `surfaceContainerHighest` `#2C2E32`, `surfaceBright` `#3C3E42`.
- [ ] 2.6 Regenerate the four contrast variants (`light/darkMediumContrast`, `light/darkHighContrast`) from the new ramps, preserving the existing mechanical-derivation comment. Delete the two `// palette has no Lilac20 / Lilac10` workaround comments — the Orchid ramp supplies every stop.
- [ ] 2.7 Confirm `Theme.kt` needs no edit, and that `VerifiedBlue` (`#208BFE`) and every `NubecitaSemanticColors` constant are unchanged.
- [ ] 2.8 Run `./gradlew :designsystem:testDebugUnitTest` — `ColorSchemeTest` must now PASS. **Tests updated: `ColorSchemeTest`, `NubecitaThemeTest`** (the latter's expected `primary` becomes `#0061A6` light / `#A0C9FF` dark).

## 3. Reference tokens and design-system docs

- [ ] 3.1 Regenerate `openspec/references/design-system/colors_and_type.css` — the token source the `design-system` spec names as canonical. Rename the `--peach-*` block to `--lagoon-*` and `--lilac-*` to `--orchid-*`, and update the `PRIMARY (M3 key color)` comment from `--sky-50` to `--sky-40`. Type tokens are untouched.
- [ ] 3.2 Add the two new rules to `docs/design-system/surface-roles.md`: the `primaryContainer`/`secondaryContainer` adjacency ban (with the measured 1.00:1 / 1.01:1 figures and the filled-plus-container remedy) and the `tertiary`-is-auxiliary constraint (with the chroma 43 vs 37 figure). Note both are review-enforced, matching the reserved-token precedent.
- [ ] 3.3 Audit existing usages for the new adjacency rule: `git grep -n "primaryContainer" -- '*/src/main/**/*.kt'` and check none renders adjacent to a `secondaryContainer` fill. Fix any hit, or record in the PR body that there were none.

## 4. Screenshot baselines

Regenerate per module, then diff against `HEAD` and confirm the change is the
palette rather than antialiasing noise. Local `validate*ScreenshotTest` fails on
macOS even on a clean tree — CI's `screenshot` job is the authority.

- [ ] 4.1 Unflavored modules — `./gradlew :designsystem:updateDebugScreenshotTest` (241 baselines), then `:core:image` (2) and `:app` (22).
- [ ] 4.2 Unflavored feature modules — `updateDebugScreenshotTest` for `:feature:composer:impl` (114), `:feature:settings:impl` (29), `:feature:login:impl` (26), `:feature:moderation:impl` (26), `:feature:postdetail:impl` (24), `:feature:mediaviewer:impl` (18), `:feature:videoplayer:impl` (14), `:feature:videos:impl` (14), `:feature:onboarding:impl` (12), `:feature:paywall:impl` (12), `:feature:bookmarks:impl` (8), `:feature:feeds:impl` (4).
- [ ] 4.3 Flavored modules — `updateProductionDebugScreenshotTest` (NOT the plain name, which fails as ambiguous) for `:feature:chats:impl` (174), `:feature:search:impl` (122), `:feature:notifications:impl` (108), `:feature:profile:impl` (94), `:feature:feed:impl` (83).
- [ ] 4.4 Verify the regeneration actually took: scan the committed PNGs for the old accent `#0A7AFF` and the new `#0061A6` / `#A0C9FF`. Any module still carrying the old accent did not regenerate and would read as a false pass (design D7). Record the per-module result in the PR body.
- [ ] 4.5 Confirm the total changed-file count is ~1147 PNGs. A materially lower number means a module was missed — unlike an ordinary change, here *every* baseline is expected to move.

## 5. Verification

- [ ] 5.1 `./gradlew :app:assembleDebug`
- [ ] 5.2 `./gradlew jacocoTestReportAggregated` — the root `testDebugUnitTest` task skips flavored modules, so it is not sufficient here.
- [ ] 5.3 `./gradlew spotlessCheck lint :app:checkSortDependencies` plus `:designsystem:lintDebug`.
- [ ] 5.4 Compile the bench variant — `./gradlew :app:assembleBenchDebug` — since five baseline modules are flavored.
- [ ] 5.5 Device pass on the physical foldable using the bench flavor: install, switch Settings → Appearance to Light, screenshot; switch to Dark, screenshot; confirm the deep dark surface and that no control is illegible. Confirm the persisted theme change actually took in each screenshot rather than assuming the tap landed.
- [ ] 5.6 Confirm the launcher icon and splash still render `#0A7AFF` and were not touched.
- [ ] 5.7 Run the `compose-expert` skill against the diff (it adds no `@Composable` lines, so this is likely a no-op — record that rather than skipping silently).

## 6. Land

- [ ] 6.1 Open the PR with `Closes: nubecita-bvff`, a Conventional-Commit title (`feat(designsystem): regenerate brand palette to Azure and fix light-theme AA failures`), the task 1.2 failure output, and the task 4.4 verification table.
- [ ] 6.2 After CI is green, confirm the `screenshot` job passed on Linux — the local macOS validate result is not evidence.
- [ ] 6.3 Re-request review after any push (`gh pr comment <n> --body "/gemini review"`), since no bot re-reviews on push and zero open threads is not evidence of review.
- [ ] 6.4 Merge, `bd close nubecita-bvff`, then archive this openspec change and reconcile the merged `design-system` and `app-theme-selection` specs — archiving concatenates rather than reconciles.
