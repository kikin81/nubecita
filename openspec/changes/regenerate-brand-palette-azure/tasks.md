# Tasks — Regenerate the brand palette (Azure)

Tracked as bd `nubecita-bvff`. Implementation lands as **one** standalone PR
(see design D6); this openspec change goes to `main` on its own beforehand.

Branch: `feat/nubecita-bvff-regenerate-brand-palette-azure`

## 1. Prove the guard discriminates (before touching the palette)

- [ ] 1.1 Rewrite `designsystem/src/test/kotlin/.../ColorSchemeTest.kt` to assert the contrast *property* rather than hex literals: a WCAG 2.1 helper, then a parameterised assertion over all six schemes. The asserted pair set MUST include, at ≥ 4.5:1: every `*Container`/`on*Container` and accent/`on*` pair; `surface`/`onSurface` and `surface`/`onSurfaceVariant`; every `surfaceContainer*`/`onSurface`; both `inverseSurface` pairs; and — critically — **`primary`, `secondary` and `tertiary` each as a foreground on `surface`**, which is where two of the four defects live. Plus `outline` vs `surface` at ≥ 3:1. **Test updated: `ColorSchemeTest`.**
- [ ] 1.2 Run `./gradlew :designsystem:testDebugUnitTest --tests '*ColorSchemeTest*'` against the **unmodified** palette and confirm it FAILS on exactly these four pairs, all light-mode: `primary`/`onPrimary` (4.01:1), `secondary`/`onSecondary` (3.90:1), `surface`/`primary` (3.92:1), `surface`/`secondary` (3.81:1). Dark mode must show no failure. Paste the output into the PR body. A guard that passes before the fix proves nothing (see design D5); a guard that fails on a *different* set means the measurements in the spec are wrong and must be re-derived before the palette is touched — do not adjust the expectation to match.
- [ ] 1.3 Add a `NubecitaSemanticColors` contrast case asserting `likeAccent`, `repostAccent`, `supporterAccent`, `success` and `warning` each clear 4.5:1 against dark `surface`. **Test added: `ColorSchemeTest.semanticAccentsMeetContrastOnDarkSurface`.**

## 2. Palette and color schemes

- [ ] 2.1 Add `LauncherBlue = Color(0xFF0A7AFF)` to `NubecitaPalette` as a fixed brand constant, documented like `VerifiedBlue`: deliberately NOT theme-derived, NOT a tonal-ramp stop, and the single source for the frozen identity blue. **Do this first** — tasks 2.2 and 2.3 depend on it. `app/src/main/res/values/colors.xml` (`brand_sky_blue`) already holds the same literal for the launcher icon and system splash and is left untouched; add a comment on each pointing at the other so they cannot drift.
- [ ] 2.2 Repoint every site that means "the brand identity blue" from `NubecitaPalette.Sky50` to `NubecitaPalette.LauncherBlue`. There are more of these than the two outside `:designsystem` — the full list is `designsystem/.../component/LogoImageVector.kt:206,214` (the mark's stroke accents — this is brand artwork, not theme chrome), `designsystem/.../component/NubecitaLogo.kt:50,58,68` (preview backgrounds and tint, plus the KDoc at lines 23 and 27), `designsystem/src/screenshotTest/.../NubecitaLogoScreenshotTest.kt:31,41`, and `app/src/main/java/.../Navigation.kt:76` (the in-app splash placeholder, which must keep matching the system splash window background it hands off from). **Screenshot test updated: `NubecitaLogoScreenshotTest`.**
- [ ] 2.3 Change `feature/onboarding/impl/.../OnboardingScreen.kt:309` to pass `tint = MaterialTheme.colorScheme.primary` **explicitly**, and remove the now-unused `NubecitaPalette` import. Unlike the splash placeholder this is in-app chrome shown after the handoff, so it should follow the active theme — including wallpaper-derived color under `AppTheme.Dynamic`, which a pinned constant defeats. **Do NOT simply delete the tint argument**: `NubecitaLogomark`'s real default is `Color.Unspecified`, which applies no `ColorFilter` and renders the mark multi-colour — a white cloud body, which would be invisible against onboarding's near-white background. (The committed spec's claim that the default is `colorScheme.primary` is stale; see task 3.4.)
- [ ] 2.4 Replace the `Sky` ramp in `NubecitaPalette` with the HCT-generated Sky ramp (hue 255, chroma 72). Keep the `Sky0`…`Sky100` naming and keep **every** stop — do NOT delete stops that look unused, since `Sky50` in particular is referenced from `:app`, `:feature:onboarding:impl` and `:designsystem` artwork, and deleting it breaks compilation in three modules.
- [ ] 2.5 Replace `Peach*` with `Lagoon*` (hue 215, chroma 40) and `Lilac*` with `Orchid*` (hue 318, chroma 45). Confirm with `git grep -n "Peach\|Lilac" -- '*.kt'` that no reference survives outside `:designsystem`.
- [ ] 2.6 Regenerate the `Neutral*` (hue 255, chroma 5) and `NeutralVariant*` (hue 250, chroma 9) ramps.
- [ ] 2.7 Rewrite `nubecitaLightColorScheme()` at Material 3 tonal stops — accents at 40 / 100 / 90 / 10 — replacing the tone-50 assignments that caused the AA failures.
- [ ] 2.8 Rewrite `nubecitaDarkColorScheme()` with accents at 80 / 20 / 30 / 90 and the deepened surface ramp: `surface` `#090B0E`, `surfaceContainerLowest` `#030406`, `surfaceContainerLow` `#111317`, `surfaceContainer` `#171A1D`, `surfaceContainerHigh` `#222427`, `surfaceContainerHighest` `#2C2E32`, `surfaceBright` `#3C3E42`.
- [ ] 2.9 Regenerate the four contrast variants (`light/darkMediumContrast`, `light/darkHighContrast`) from the new ramps, preserving the existing mechanical-derivation comment. Delete the two `// palette has no Lilac20 / Lilac10` workaround comments — the Orchid ramp supplies every stop.
- [ ] 2.10 Confirm `Theme.kt` needs no edit, and that `VerifiedBlue` (`#208BFE`) and every `NubecitaSemanticColors` constant are unchanged.
- [ ] 2.11 Run `./gradlew :designsystem:testDebugUnitTest` — `ColorSchemeTest` must now PASS. **Tests updated: `ColorSchemeTest`, `NubecitaThemeTest`** (the latter's expected `primary` becomes `#0061A6` light / `#A0C9FF` dark).

## 3. Reference tokens and design-system docs

- [ ] 3.1 Regenerate `openspec/references/design-system/colors_and_type.css` — the token source the `design-system` spec names as canonical. Rename the `--peach-*` block to `--lagoon-*` and `--lilac-*` to `--orchid-*`, and update the `PRIMARY (M3 key color)` comment from `--sky-50` to `--sky-40`. Type tokens are untouched.
- [ ] 3.2 Add the two new rules to `docs/design-system/surface-roles.md`: the accent-container adjacency ban — no two of `primaryContainer` / `secondaryContainer` / `tertiaryContainer` may sit adjacent, since *every* such pairing measures ~1:1 in both modes — with the filled-plus-container remedy and its ~5:1 figures and the `tertiary`-is-auxiliary constraint (with the chroma 43 vs 37 figure). Note both are review-enforced, matching the reserved-token precedent.
- [ ] 3.3 Audit existing usages for the new adjacency rule: `git grep -n "primaryContainer" -- '*/src/main/**/*.kt'` and check none renders adjacent to a `secondaryContainer` fill. Fix any hit, or record in the PR body that there were none.
- [ ] 3.4 Correct the stale `NubecitaLogomark` description carried in the committed `design-system` spec, which this change already modifies. Three claims in it are false against `NubecitaLogo.kt` / `LogoImageVector.kt`: the default tint is `Color.Unspecified`, not `MaterialTheme.colorScheme.primary`; the backing asset is the `LogoImageVector` Compose `ImageVector`, not a `nubecita_logomark.xml` drawable (no such file exists in the repo); and the mark is multi-colour — white cloud body, pink bow `#F7AAC9` / `#E36DA0`, plus the two identity-blue stroke accents — not a single-colour silhouette. The delta spec in this change carries the corrected text. Verify with `find . -name 'nubecita_logomark*' -not -path '*/build/*'` returning nothing.

## 4. Screenshot baselines

Regenerate per module, then diff against `HEAD` and confirm the change is the
palette rather than antialiasing noise. Local `validate*ScreenshotTest` fails on
macOS even on a clean tree — CI's `screenshot` job is the authority.

- [ ] 4.1 Unflavored modules — `./gradlew :designsystem:updateDebugScreenshotTest` (241 baselines), then `:core:image` (2) and `:app` (22).
- [ ] 4.2 Unflavored feature modules — `updateDebugScreenshotTest` for `:feature:composer:impl` (114), `:feature:settings:impl` (29), `:feature:login:impl` (26), `:feature:moderation:impl` (26), `:feature:postdetail:impl` (24), `:feature:mediaviewer:impl` (18), `:feature:videoplayer:impl` (14), `:feature:videos:impl` (14), `:feature:onboarding:impl` (12), `:feature:paywall:impl` (12), `:feature:bookmarks:impl` (8), `:feature:feeds:impl` (4).
- [ ] 4.3 Flavored modules — `updateProductionDebugScreenshotTest` (NOT the plain name, which fails as ambiguous) for `:feature:chats:impl` (174), `:feature:search:impl` (122), `:feature:notifications:impl` (108), `:feature:profile:impl` (94), `:feature:feed:impl` (83).
- [ ] 4.4 Verify the regeneration actually took: scan the committed PNGs for the new accent `#0061A6` (light) / `#A0C9FF` (dark). A module whose baselines contain neither did not regenerate and would read as a false pass (design D7). Record the per-module result in the PR body. **Do NOT treat the presence of `#0A7AFF` as the failure signal** — after task 2.2 that blue legitimately survives as `LauncherBlue` in the logomark and splash-placeholder baselines, so it is expected in `:designsystem` and `:app` and its absence there is the actual regression.
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
