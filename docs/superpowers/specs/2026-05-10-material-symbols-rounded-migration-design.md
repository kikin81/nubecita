# Material Symbols Rounded migration — design

**Date:** 2026-05-10
**bd:** `nubecita-68g` — *designsystem: migrate from material-icons-extended to Material Symbols Rounded variable font*
**Driver:** the `androidx.compose.material:material-icons-extended` library is deprecated by Google (removed from the latest Material 3 release; superseded by Material Symbols). Independently, Nubecita's design doc specifies Material Symbols Rounded with a FILL-axis toggle for active/inactive states — the React side (`nubecita/Primitives.jsx Icon`) already wires this via Google Fonts; Android needs parity.
**Scope:** single PR — vendor the variable font, build a `NubecitaIcon` composable + `NubecitaIconName` enum in `:designsystem`, migrate ~39 distinct glyph references across 21 files in 6 modules, drop `material-icons-extended` from every `build.gradle.kts`, refresh per-feature screenshot baselines, codify the icon contract in `openspec/specs/design-system/spec.md`.

## Why

Two converging pressures:

1. Google deprecated the icons library. From their announcement: "We have stopped publishing updates to this library and it has been removed from the latest Material 3 library release… the library can increase the build time of your apps significantly as it includes all the various icons that may not be needed." Continuing on it is a planned dead-end.
2. Our design language calls for **Material Symbols Rounded**, not Material Icons. The two are different artifacts: Icons are static `ImageVector` drawables in five styles; Symbols is a single variable font with **FILL** (0..1, outlined↔filled), **Weight** (100..700), **Grade** (-25..200, low/high emphasis), and **Optical Size** (20..48dp) axes. The FILL axis specifically replaces the Filled/Outlined pair pattern with one continuous variable — and that's how the React side already does active/inactive state on the bottom tab bar.

A migration also lets us delete `material-icons-extended` from every module (~hundreds of KB of mostly-unused vector drawables) and replace it with a single subsetted variable font (~50 KB after subsetting).

## Decisions

### 1. Pre-subset the variable font via a developer-facing script — ship only the ~30 glyphs we use

The upstream variable font `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` from <https://github.com/google/material-design-icons/tree/master/variablefont> is **~14.9 MB** — it bundles all ~3,800 Material Symbols glyphs in a single variable file. Shipping the full font would dominate Nubecita's APK size and is a non-starter for an Android client where install metrics matter.

The path forward: pre-subset the font down to just the codepoints declared in `NubecitaIconName`, using `pyftsubset` (from the `fonttools` Python package), via a developer-facing script (`scripts/update_material_symbols.sh`). The script:

1. Curls the upstream 14.9 MB font into a gitignored cache (`build/icon-cache/`).
2. Extracts the `\uXXXX` codepoints from `designsystem/src/main/kotlin/.../icon/NubecitaIconName.kt` (single source of truth).
3. Runs `pyftsubset` to keep only those codepoints + every variation axis (`fvar`, `STAT`, `gvar`).
4. Writes the resulting ~50 KB subset to `designsystem/src/main/res/font/material_symbols_rounded.ttf`.

The script is **not** wired into the Gradle build. Adding `python3` + `fonttools` as a build-time dep would force every CI runner and contributor machine to install Python + pip-install fonttools just to compile the Android project — a portability nightmare. Instead, the workflow on icon changes is:

1. Add a new entry to `NubecitaIconName` (with the upstream codepoint).
2. Run `./scripts/update_material_symbols.sh`.
3. Commit the regenerated `material_symbols_rounded.ttf` alongside the enum change.

The Android build only ever sees the small subset font. No Python on CI. No 14.9 MB blob in the repo.

Variable-font font variations require API 26+; Nubecita's `minSdk = 28` covers that comfortably. Compose's `androidx.compose.ui.text.font.FontVariation.Settings(...)` is the runtime API and works on the subsetted font (axes are preserved by `pyftsubset` by default).

### 2. API shape — `NubecitaIcon(name = ...)`

Selected for compile-time safety, enum exhaustiveness, and direct parity with the React `Primitives.jsx Icon` API.

```kotlin
enum class NubecitaIconName(internal val codepoint: String) {
    Close(""),
    Search(""),
    // … one entry per glyph the codebase actually uses (~39 entries).
}

@Composable
fun NubecitaIcon(
    name: NubecitaIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,            // FILL axis: 0f / 1f
    weight: Int = 400,                  // wght axis: 100..700
    grade: Int = 0,                     // GRAD axis: -25..200 (high emphasis)
    opticalSize: Dp = 24.dp,            // opsz axis: 20..48
    tint: Color = LocalContentColor.current,
)
```

Internally renders via `Text(text = name.codepoint)` with a `FontFamily(Font(R.font.material_symbols_rounded, variationSettings = FontVariation.Settings(...)))`. `contentDescription` plumbs through `Modifier.semantics`. Tinting via `Text(color = tint)`. Display size via `Modifier.size(opticalSize)`.

### 3. Glyph table — vendor only the ~39 we use

Material Symbols ships ~3,800 glyphs; we use 39 distinct references (37 unique base glyphs after collapsing Filled/Outlined pairs). Vendor only those entries in the `NubecitaIconName` enum. Keeps the surface small, grep-able, and forces every new icon to be an explicit, reviewed addition rather than a freebie from a generated table.

Codepoints come from Google's `MaterialSymbolsRounded.codepoints` file (one line per glyph: `<name> <hex>`). We hand-pick the ~39 we need; a comment in the enum file points at the upstream source for refresh.

### 4. Filled/Outlined pairs collapse to one symbol with `filled` Boolean

The current code has six pairs — `Home`/`Home`, `Search`/`Search`, `Person`/`Person`, `Favorite`/`FavoriteBorder`, `ChatBubble`/`ChatBubbleOutline`, `Edit`/`Edit` — that exist solely so we can render two visual states. After migration, both states share a single enum entry (`NubecitaIconName.Home`) and the call site passes `filled = isActive`. Reduces enum size + makes the active/inactive intent explicit at the call site.

### 5. AutoMirrored handling — manual `Modifier.mirror()` per call site

Material Symbols doesn't auto-mirror in RTL the way `Icons.AutoMirrored.*` does. The current `Icons.AutoMirrored.*` call sites (ArrowBack, Article, Reply, VolumeOff, VolumeUp) get an explicit `Modifier.mirror()` (a small extension we add in `:designsystem` that flips horizontally when `LocalLayoutDirection.current == LayoutDirection.Rtl`). Per-site decision keeps the RTL behavior visible and reviewable rather than hiding it inside the icon.

### 6. `filled` as `Boolean` (not `Float`) in V1

The FILL axis is continuous (0f..1f) and the variable font supports any value, but V1 ships with a `Boolean` API to keep call sites simple. Animated transitions (e.g. tap-to-favorite scrubbing FILL from 0f→1f over 200ms) are a follow-up — expose `fillFraction: Float` later if a designer asks for it. Internally we still pass a Float to `FontVariation.Settings` (`if (filled) 1f else 0f`).

### 7. `weight` and `grade` defaults — 400 / 0

Match Google Material Symbols defaults. `weight` is Compose's `FontVariation.weight(400)`; `grade` is `FontVariation.Setting("GRAD", 0f)`. Per-call overrides supported but unused in V1; documented for the design system's future evolution.

### 8. File layout

| Where | What |
|---|---|
| `designsystem/src/main/res/font/material_symbols_rounded.ttf` | New — Apache 2.0 variable font subsetted to `NubecitaIconName`'s codepoints by `scripts/update_material_symbols.sh` (~50 KB). |
| `LICENSES/LICENSE-material-symbols.txt` | New — Apache 2.0 license text + upstream URL + git SHA. NOT inside `res/font/` because Android's resource compiler rejects non-`.ttf`/`.xml` files there. |
| `scripts/update_material_symbols.sh` | New — developer-facing subset script. Curls upstream font, extracts codepoints from `NubecitaIconName.kt`, runs `pyftsubset` → writes the slim font. Run manually whenever an icon is added/removed. |
| `.gitignore` | Modify — add `build/icon-cache/` so the 14.9 MB upstream font cache stays out of git. |
| `designsystem/src/main/kotlin/.../icon/NubecitaIconName.kt` | New — `enum class NubecitaIconName(val codepoint: String)` with one entry per glyph in use. |
| `designsystem/src/main/kotlin/.../icon/NubecitaIcon.kt` | New — `@Composable fun NubecitaIcon(...)` per the API shape above. |
| `designsystem/src/main/kotlin/.../icon/Mirror.kt` | New — `Modifier.mirror()` extension for RTL-flip on AutoMirrored sites. |
| `designsystem/src/test/kotlin/.../icon/NubecitaIconNameTest.kt` | New — JVM unit test asserting every codepoint round-trips through `String.codePointAt(0)` (catches typos in the hex literal). |
| `designsystem/src/androidTest/kotlin/.../icon/NubecitaIconInstrumentationTest.kt` | New — Compose UI test: contentDescription resolves, `filled = true` vs `filled = false` produces visibly different rendered nodes (assert via tag + screenshot rule). |
| `designsystem/src/screenshotTest/kotlin/.../icon/NubecitaIconShowcaseScreenshotTest.kt` | New — single grid fixture rendering all enum entries at outlined + filled across `@PreviewNubecitaScreenPreviews` (6 baselines). Locks the visual contract and surfaces missing glyphs immediately. |
| `feature/composer/impl/.../*.kt` (~7 files) | Modified — call-site migration. |
| `feature/feed/impl/.../*.kt` (~6 files) | Modified — call-site migration. |
| `feature/postdetail/impl/.../*.kt` (~3 files) | Modified — call-site migration. |
| `feature/mediaviewer/impl/.../*.kt` (~2 files) | Modified — call-site migration. |
| `app/.../*.kt` (~3 files) | Modified — call-site migration. |
| 6 × `build.gradle.kts` | Modified — `implementation(libs.androidx.compose.material.icons.extended)` removed. |
| `gradle/libs.versions.toml` | Modified — version-catalog entry removed once nothing references it. |
| `openspec/specs/design-system/spec.md` | Modified — new requirement: "Iconography uses Material Symbols Rounded via NubecitaIcon" with scenarios for the 4 axes, the Filled/Outlined collapse, and the RTL-mirror contract. |

### 9. Test plan

- **JVM unit (`NubecitaIconNameTest`)**: every enum entry's codepoint is exactly one Unicode scalar (`codepoint.codePointCount(0, codepoint.length) == 1`); no entry is the U+0000 placeholder; the upstream-pointer KDoc reference is non-stale (string compare against the file's actual upstream URL, defensive).
- **Compose UI (`NubecitaIconInstrumentationTest`, androidTest)**: rendering with `contentDescription = "search"` exposes that on the merged a11y tree (`onNodeWithContentDescription`); rendering with `filled = false` vs `filled = true` produces nodes with different rasterized output. Run on connected device per the `feedback_run_instrumentation_tests_after_compose_work` memory rule.
- **Screenshot showcase (`NubecitaIconShowcaseScreenshotTest`)**: grid of every `NubecitaIconName` × {outlined, filled} at 24dp with the default theme tone. `@PreviewNubecitaScreenPreviews` × 1 fixture = 6 baselines.
- **Per-feature screenshot regression**: every existing fixture in `:feature:composer:impl`, `:feature:feed:impl`, `:feature:postdetail:impl`, `:feature:mediaviewer:impl`, `:app` regenerates intentionally — the visual diff is the migration. Each regen is documented in its commit body with a link to the eyeballed-OK ticket.
- **Lint + spotless + openspec validate** sweep clean.

### 10. APK size accounting (for the PR description)

Capture `:app:assembleDebug` APK size before (current main) and after (post-migration). The variable font is ~340 KB; the icons-extended artifact contributes vector drawables that are individually small but bundled per-module. Empirical delta documented in PR body — even a flat outcome is informative.

### 11. Gotchas the implementer should anticipate

This migration's tradeoffs and footguns, called out so they don't have to be re-discovered during review or after merge.

#### RTL friction (loss of `Icons.AutoMirrored`)

Standard Compose icons offer `Icons.AutoMirrored.*` — a directional-icon namespace (back arrow, reply, article, etc.) that the framework automatically flips for RTL locales. **By rendering glyphs through a font, we lose this free behavior.** Material Symbols Rounded does NOT auto-mirror in RTL.

Mitigation: every former `Icons.AutoMirrored.*` call site gets a `Modifier.mirror()` that flips horizontally when `LocalLayoutDirection.current == LayoutDirection.Rtl`. The mirror modifier is shipped in `:designsystem` (decision §5). The cost is developer discipline: when adding a new directional icon (anything that visually points left/right), the author MUST remember to add `.mirror()`. There is no compile-time enforcement — code review + RTL screenshot fixtures are the safety net.

If this becomes a recurring miss in review, a follow-up could:
- Mark certain `NubecitaIconName` entries as `directional` in the enum and have `NubecitaIcon` apply `.mirror()` automatically when `directional = true`. Loses per-site control but removes the discipline cost.
- Or wrap the modifier as a separate `NubecitaDirectionalIcon` composable that carries the mirror by default. Slight API duplication but explicit.

V1 ships with the manual approach; revisit if RTL bugs surface.

#### Baseline-alignment quirks (`Text` is not `Icon`)

`androidx.compose.material3.Icon` renders via a `Painter` over a fixed bounding box. `NubecitaIcon` renders via `Text(text = codepoint, fontFamily = …)` — which is subject to **font metrics**: ascent, descent, leading, and Compose's default `includeFontPadding = true`. The result can be a glyph that's slightly off-center vertically, or a click target with extra invisible padding above/below the glyph.

Mitigations to apply during implementation:
1. Set `includeFontPadding = false` on the `Text` (via `style.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))`) so the glyph fills the box without the platform's default 24sp-line-height padding.
2. Use `style = LocalTextStyle.current.copy(lineHeight = opticalSize.value.sp, fontSize = opticalSize.value.sp)` — locks line height to the icon's display size so descenders don't push the box taller than expected.
3. Wrap in a `Box(modifier = Modifier.size(opticalSize), contentAlignment = Alignment.Center)` so the glyph centers inside the size constraint regardless of font metrics.

The `NubecitaIconShowcaseScreenshotTest` fixture (test plan §9) is specifically the safety net for catching alignment regressions — if a glyph drifts vertically inside the 24dp box, the baseline diff surfaces it before merge. Eyeball the showcase output during initial generation; subtle off-by-one centering is the most likely failure mode.

#### Manual codepoint upkeep (no Android Studio autocomplete)

`Icons.Filled.*` autocompletes inside Android Studio because each icon is a Kotlin property. After this migration, **adding a new icon requires a manual lookup AND a script run**: visit <https://fonts.google.com/icons?icon.style=Rounded>, find the glyph, copy its hex codepoint (e.g. `e5cd`) into a new `NubecitaIconName` enum entry, then re-run the subset script so the shipped font picks up the new glyph.

Workflow for adding a glyph:
1. Find the icon at <https://fonts.google.com/icons?icon.style=Rounded>.
2. Click the icon → side panel shows the codepoint (e.g. "Codepoint: e5cd").
3. Add `Foo("\uXXXX"),` to `NubecitaIconName` (preserving alphabetical order). The unit test `NubecitaIconNameTest.every_codepoint_isASingleScalar()` validates the format at build time.
4. Run `./scripts/update_material_symbols.sh` to regenerate `designsystem/src/main/res/font/material_symbols_rounded.ttf` with the new glyph included. The script extracts codepoints directly from the enum file — there's no second list to keep in sync.
5. Commit the enum change AND the regenerated font as one commit.

If the script is forgotten on a new-icon PR, the new enum entry will pass the unit test (the codepoint format is valid) but the showcase screenshot fixture will render that row as a missing-glyph `?` tofu — caught in screenshot review before merge.

Future automation if this becomes painful: a CI check that re-runs the script on the PR and compares the output against the committed font, failing if they differ. Not shipped in V1; we'd add it if "forgot to run the script" PRs become recurring.

#### Why we accept these costs

The pros — eliminating the deprecation, collapsing Filled/Outlined pairs into a single FILL-axis decision per call site, opening the door to animated state transitions, and matching the React side's `Primitives.jsx` API — outweigh the manual-lookup overhead and the RTL discipline cost. The screenshot test plan (per-feature regression baselines + the showcase fixture) catches the silent visual failures the new approach is exposed to. Lock and ship.

### 12. Acceptance

- Zero `import androidx.compose.material.icons` in the codebase.
- `material-icons-extended` removed from all 6 module `build.gradle.kts` declarations and from `gradle/libs.versions.toml`.
- `NubecitaIcon` is the only icon entry point in app code; `:designsystem`'s spec codifies the contract.
- All screenshot fixtures pass against regenerated baselines; new `NubecitaIconShowcaseScreenshotTest` baselines committed.
- `openspec validate --all --strict` passes.

## Out of scope (file as separate bd issues if pursued)

- **Animating the FILL axis** — V1 ships `Boolean filled`. A `fillFraction: Float` overload + spring animation lands when a designer asks.
- **Outlined / Sharp Material Symbols variants** — design specifies Rounded only.
- **Auto-generated codepoint table** from Google's `codepoints` file — defer; the manual subset is a feature, not a workaround.
- **Cross-platform glyph parity audit** with the React side's `Primitives.jsx Icon` — assume both pull from the same Material Symbols glyph set; mismatches surface during normal cross-platform feature work.
