# Material Symbols Rounded Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the deprecated `androidx.compose.material:material-icons-extended` library with the Material Symbols Rounded variable font, exposed via a `NubecitaIcon(name = NubecitaIconName.X, filled = …, …)` composable in `:designsystem`, and migrate every call site in 6 modules.

**Architecture:** Vendor Google's Apache-2.0 `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` as a `:designsystem` font asset. Build a thin `NubecitaIcon` composable that renders glyphs via Compose `Text` + `FontFamily` + `FontVariation.Settings`. Glyph identities are a small typed enum (`NubecitaIconName`) with codepoints verified at build time. RTL handling for directional icons is a per-call-site `Modifier.mirror()` (no auto-mirror).

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AGP variable-font support (minSdk 28 ≥ API 26 requirement), JUnit 5 + AssertK + UnconfinedTestDispatcher for VM tests, `androidx.compose.ui.test.junit4.createAndroidComposeRule<ComponentActivity>()` for Compose UI tests, `@PreviewNubecitaScreenPreviews` for screenshot fixtures.

**Prerequisites:** None — `nubecita-68g` is the entry point. The design doc at `docs/superpowers/specs/2026-05-10-material-symbols-rounded-migration-design.md` is authoritative for unresolved scope/decision questions.

**Branch:** Continue work on the `chore/nubecita-68g-designsystem-migrate-from-material-icons-extended` branch (which carries the design doc this plan implements).

---

## File structure

| Path | Action | Responsibility |
|---|---|---|
| `scripts/update_material_symbols.sh` | Create | Developer-facing script: curls the upstream 14.9 MB variable font, runs `pyftsubset` to slim it to the codepoints in `NubecitaIconName`, writes the ~50 KB result to `designsystem/src/main/res/font/`. Run manually after icon-list changes. |
| `LICENSES/LICENSE-material-symbols.txt` | Create | Apache 2.0 license text + upstream URL + subset workflow docs. NOT in `res/font/` — Android's resource compiler rejects non-`.ttf`/`.xml` files there. |
| `.gitignore` | Modify | Add `build/icon-cache/` to keep the cached upstream 14.9 MB font out of git. |
| `designsystem/src/main/res/font/material_symbols_rounded.ttf` | Create | Subsetted Apache-2.0 variable font (~50 KB after Task 3 runs the script). |
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt` | Create | `enum class NubecitaIconName(val codepoint: String)` with one entry per glyph in use (~37 entries). |
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIcon.kt` | Create | `@Composable fun NubecitaIcon(...)` that renders the glyph via `Text` + variable-font axes. |
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/Mirror.kt` | Create | `Modifier.mirror()` extension for RTL-flip on AutoMirrored sites. |
| `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconNameTest.kt` | Create | JVM unit tests pinning every codepoint as a single Unicode scalar. |
| `designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconInstrumentationTest.kt` | Create | Compose UI test: `contentDescription` exposes a single a11y node; `filled = true` vs `false` produce different rendered output. |
| `designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/MirrorInstrumentationTest.kt` | Create | Compose UI test: `Modifier.mirror()` flips horizontally only when `LayoutDirection.Rtl`. |
| `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconShowcaseScreenshotTest.kt` | Create | Single grid fixture rendering every enum entry × {outlined, filled} × `@PreviewNubecitaScreenPreviews` = 6 baselines. |
| `designsystem/build.gradle.kts` | Modify | Drop `material-icons-extended` (after `:designsystem` call sites migrate); add `androidx.compose.ui.test.junit4` androidTest dep. |
| `app/build.gradle.kts` | Modify | Drop `material-icons-extended`. |
| `feature/composer/impl/build.gradle.kts` | Modify | Drop `material-icons-extended`. |
| `feature/feed/impl/build.gradle.kts` | Modify | Drop `material-icons-extended`. |
| `feature/postdetail/impl/build.gradle.kts` | Modify | Drop `material-icons-extended`. |
| `feature/mediaviewer/impl/build.gradle.kts` | Modify | Drop `material-icons-extended`. |
| `gradle/libs.versions.toml` | Modify | Remove `androidx-compose-material-icons-extended` entry. |
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt` | Modify | Migrate icon call sites. |
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbed.kt` | Modify | Migrate icon call sites. |
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostStat.kt` | Modify | Migrate icon call sites. |
| `feature/composer/impl/src/main/kotlin/.../ComposerScreen.kt` | Modify | Migrate icon call sites. |
| `feature/composer/impl/src/main/kotlin/.../internal/ComposerAttachmentChip.kt` | Modify | Migrate icon call sites. |
| `feature/composer/impl/src/main/kotlin/.../internal/ComposerLanguageChip.kt` | Modify | Migrate icon call sites. |
| `feature/composer/impl/src/main/kotlin/.../internal/ComposerReplyParentSection.kt` | Modify | Migrate icon call sites. |
| `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt` | Modify | Migrate icon call sites. |
| `feature/feed/impl/src/main/kotlin/.../ui/FeedDetailPlaceholder.kt` | Modify | Migrate icon call sites. |
| `feature/feed/impl/src/main/kotlin/.../ui/FeedEmptyState.kt` | Modify | Migrate icon call sites. |
| `feature/feed/impl/src/main/kotlin/.../ui/FeedErrorState.kt` | Modify | Migrate icon call sites. |
| `feature/feed/impl/src/main/kotlin/.../ui/PostCardVideoEmbed.kt` | Modify | Migrate icon call sites. |
| `feature/postdetail/impl/src/main/kotlin/.../PostDetailScreen.kt` | Modify | Migrate icon call sites. |
| `feature/mediaviewer/impl/src/main/kotlin/.../MediaViewerScreen.kt` | Modify | Migrate icon call sites. |
| `app/src/main/java/net/kikin/nubecita/shell/MainShell.kt` | Modify | Migrate icon call sites. |
| `openspec/specs/design-system/spec.md` | Modify | New requirement: "Iconography uses Material Symbols Rounded via NubecitaIcon" with 4 scenarios. |

---

## Task 1: Add the font-subset developer script + license + cache gitignore

**Files:**
- Create: `scripts/update_material_symbols.sh`
- Create: `LICENSES/LICENSE-material-symbols.txt`
- Modify: `.gitignore` (add `build/icon-cache/`)

The full upstream variable font is **~14.9 MB** — way too big to ship in the APK. Instead this task vendors a **developer-facing subset script** that runs `pyftsubset` (from `fonttools`) to slim the font down to ~50 KB containing only the codepoints declared in `NubecitaIconName`. The script is invoked manually whenever an icon is added; it is **not** wired into the Gradle build (would force every CI runner + contributor machine to install Python + fonttools).

This task only adds the script + license + gitignore. The script is **not run** here because `NubecitaIconName.kt` doesn't exist yet — Task 2 creates it. Task 3 is where the script actually runs.

- [ ] **Step 1: Confirm `pyftsubset` availability (local check, not committed)**

Verify the contributor's machine has fonttools available:

```bash
command -v pyftsubset && pyftsubset --help | head -3
```

Expected: prints help. If not installed:

```bash
python3 -m pip install --user fonttools brotli
```

(Brotli is a fonttools optional dep that lets it handle compressed font tables. The Material Symbols font uses standard tables, but `pyftsubset` warns without brotli — install once to silence it.)

- [ ] **Step 2: Write the subset script**

Create `scripts/update_material_symbols.sh`:

```bash
#!/usr/bin/env bash
#
# Subsets the upstream Material Symbols Rounded variable font down to
# only the codepoints declared in NubecitaIconName.kt, and writes the
# result to designsystem/src/main/res/font/material_symbols_rounded.ttf.
#
# Re-run whenever NubecitaIconName gains or loses an entry. The
# generated font is checked in alongside the enum change as one
# commit so reviewers see the subset font diff.
#
# Why a script and not a Gradle task: requiring `python3` + fonttools
# on every CI runner and contributor machine is fragile. Subsetting
# is a one-shot operation per icon-list change; pre-computing keeps
# the Android build pure-Kotlin.
#
# Requirements:
#   - python3 with fonttools installed (pip install fonttools brotli)
#   - curl (to fetch the upstream font on first run / cache miss)
#
# Usage:
#   ./scripts/update_material_symbols.sh

set -euo pipefail

if ! command -v pyftsubset >/dev/null 2>&1; then
    cat >&2 <<EOF
pyftsubset (from fonttools) not found. Install with:
    python3 -m pip install --user fonttools brotli
Then re-run this script.
EOF
    exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
ENUM_FILE="$REPO_ROOT/designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt"
OUTPUT_FONT="$REPO_ROOT/designsystem/src/main/res/font/material_symbols_rounded.ttf"
CACHE_DIR="$REPO_ROOT/build/icon-cache"
UPSTREAM_FONT="$CACHE_DIR/material_symbols_rounded_full.ttf"
UPSTREAM_URL='https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf'

if [ ! -f "$ENUM_FILE" ]; then
    echo "Enum file not found at $ENUM_FILE" >&2
    echo "(Task 2 of bd-68g must land before this script can run.)" >&2
    exit 1
fi

# Extract \uXXXX codepoints from NubecitaIconName.kt
codepoints=$(grep -oE '\\u[A-F0-9]{4}' "$ENUM_FILE" | sort -u)
if [ -z "$codepoints" ]; then
    echo "No \\uXXXX codepoints found in $ENUM_FILE" >&2
    exit 1
fi
codepoint_count=$(echo "$codepoints" | wc -l | tr -d '[:space:]')
unicode_args=$(echo "$codepoints" | sed 's/\\u/U+/' | tr '\n' ',' | sed 's/,$//')
echo "Found $codepoint_count codepoints in NubecitaIconName.kt"

# Fetch upstream font into the cache (skip if already cached)
mkdir -p "$CACHE_DIR"
if [ ! -f "$UPSTREAM_FONT" ]; then
    echo "Downloading upstream font (~14.9 MB) into $CACHE_DIR ..."
    curl -L --fail -o "$UPSTREAM_FONT" "$UPSTREAM_URL"
fi

upstream_size=$(stat -f%z "$UPSTREAM_FONT" 2>/dev/null || stat -c%s "$UPSTREAM_FONT")
if [ "$upstream_size" -lt 1000000 ]; then
    echo "Upstream font is suspiciously small ($upstream_size bytes); refusing to subset." >&2
    echo "Delete $UPSTREAM_FONT and re-run." >&2
    exit 1
fi

# Run pyftsubset. Preserve all variable axes (FILL/GRAD/opsz/wght);
# drop hinting (icons render at fixed sizes); desubroutinize for
# reliable rendering across Compose's Skia backend.
echo "Subsetting to $codepoint_count codepoints ..."
pyftsubset "$UPSTREAM_FONT" \
    --output-file="$OUTPUT_FONT" \
    --unicodes="$unicode_args" \
    --layout-features='*' \
    --no-hinting \
    --desubroutinize \
    --recommended-glyphs

output_size=$(stat -f%z "$OUTPUT_FONT" 2>/dev/null || stat -c%s "$OUTPUT_FONT")
output_kb=$((output_size / 1024))
echo "Wrote $OUTPUT_FONT ($output_kb KB)"

if [ "$output_kb" -gt 200 ]; then
    echo "WARNING: subset font is unusually large (>200 KB). Inspect for unexpected glyphs." >&2
fi
```

Make it executable:

```bash
chmod +x scripts/update_material_symbols.sh
```

- [ ] **Step 3: Write the license file**

Create `LICENSES/LICENSE-material-symbols.txt`. Note the path is `LICENSES/`, not `designsystem/src/main/res/font/` — Android's resource compiler rejects non-`.ttf` / non-`.xml` files inside `res/font/` and will fail the build.

```
Material Symbols Rounded

Copyright Google LLC. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License").
You may obtain a copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License.

Source:
  https://github.com/google/material-design-icons
  variablefont/MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf

The shipped font at designsystem/src/main/res/font/material_symbols_rounded.ttf
is a subset (~50 KB) of the upstream ~14.9 MB variable font, generated
by ./scripts/update_material_symbols.sh from the codepoints declared
in NubecitaIconName.kt.

Re-run the script after changing NubecitaIconName entries; commit the
regenerated font alongside the enum change.
```

- [ ] **Step 4: Add the cache directory to `.gitignore`**

Append to `.gitignore`:

```
# Cached upstream variable font for scripts/update_material_symbols.sh.
# The 14.9 MB font lives here; only the ~50 KB subset under
# designsystem/src/main/res/font/ is checked in.
build/icon-cache/
```

(If `.gitignore` already has a `build/` rule that covers nested dirs, this entry is redundant but explicit and safe.)

- [ ] **Step 5: Verify the script's shellcheck passes**

```bash
command -v shellcheck && shellcheck scripts/update_material_symbols.sh
```

Expected: clean (or absent — `shellcheck` isn't required to be installed). The script doesn't run yet because the enum file doesn't exist; Task 3 is where it runs.

- [ ] **Step 6: Commit**

```bash
git add scripts/update_material_symbols.sh \
        LICENSES/LICENSE-material-symbols.txt \
        .gitignore
git commit -m "chore(designsystem): add Material Symbols Rounded subset script

Developer-facing script that subsets the upstream 14.9 MB Material
Symbols Rounded variable font down to ~50 KB, containing only the
codepoints declared in NubecitaIconName.kt. Run manually after any
icon-list change; the regenerated font is checked in alongside the
enum.

Not wired into Gradle — keeps the Android build pure (no python3 or
fonttools required on CI runners).

Refs: nubecita-68g"
```

---

## Task 2: Add `NubecitaIconName` enum

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt`
- Test: `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconNameTest.kt`

- [ ] **Step 1: Write the failing test**

Create `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconNameTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.icon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pin-down tests for [NubecitaIconName]. Catches typos in the
 * vendored hex codepoints — every entry must resolve to exactly one
 * Unicode scalar value (a single glyph), and no entry may collide
 * with another.
 */
class NubecitaIconNameTest {
    @Test
    fun every_codepoint_isASingleScalar() {
        NubecitaIconName.entries.forEach { entry ->
            val cp = entry.codepoint
            val scalarCount = cp.codePointCount(0, cp.length)
            assertEquals(
                1,
                scalarCount,
                "${entry.name}'s codepoint '$cp' resolved to $scalarCount scalars; " +
                    "expected exactly 1 (single glyph)",
            )
        }
    }

    @Test
    fun no_codepointCollisions() {
        // Two enum entries pointing at the same codepoint is almost
        // certainly a copy-paste typo. Every glyph in the picker has
        // a distinct identity in Material Symbols.
        val byCodepoint = NubecitaIconName.entries.groupBy { it.codepoint }
        val collisions = byCodepoint.filter { it.value.size > 1 }
        assertTrue(
            collisions.isEmpty(),
            "Codepoint collisions detected: $collisions",
        )
    }

    @Test
    fun enum_isNonEmpty() {
        assertTrue(
            NubecitaIconName.entries.isNotEmpty(),
            "NubecitaIconName must declare at least one glyph",
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "net.kikin.nubecita.designsystem.icon.NubecitaIconNameTest"`

Expected: FAIL with `Unresolved reference: NubecitaIconName`.

- [ ] **Step 3: Create the enum**

Create `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt`:

```kotlin
package net.kikin.nubecita.designsystem.icon

/**
 * Identity of a glyph in the Material Symbols Rounded font shipped at
 * `R.font.material_symbols_rounded`. Vendor only the entries the app
 * actually uses — adding a new icon means adding a new entry here
 * (forces explicit, reviewed addition rather than a freebie from a
 * generated table).
 *
 * # Adding a new icon
 *
 * 1. Find the glyph at <https://fonts.google.com/icons?icon.style=Rounded>.
 * 2. Click the icon → side panel shows "Codepoint: e5cd" (4-char hex).
 * 3. Add `Foo("\uXXXX"),` below in alphabetical order. The Kotlin
 *    string literal escape format is `\uXXXX` (uppercase letters, no
 *    quotes around the hex itself).
 * 4. The unit test `NubecitaIconNameTest.every_codepoint_isASingleScalar`
 *    enforces validity at build time. Run
 *    `./gradlew :designsystem:testDebugUnitTest` to verify.
 *
 * Codepoints below sourced from Google's
 * `MaterialSymbolsRounded.codepoints` file at
 * <https://github.com/google/material-design-icons/blob/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.codepoints>.
 *
 * Filled/Outlined pairs in the legacy `Icons.Filled.X` / `Icons.Outlined.X`
 * library collapse to ONE entry here — the visual difference is
 * controlled by the `filled: Boolean` parameter on [NubecitaIcon].
 */
enum class NubecitaIconName(internal val codepoint: String) {
    Add("\uE145"),
    AddPhotoAlternate("\uE43E"),
    ArrowBack("\uE5C4"),
    Article("\uEF42"),
    Bookmark("\uE866"),
    ChatBubble("\uE0CA"),
    Check("\uE5CA"),
    Close("\uE5CD"),
    Edit("\uE3C9"),
    Error("\uE000"),
    Favorite("\uE87D"),
    Home("\uE88A"),
    Inbox("\uE156"),
    IosShare("\uE6B8"),
    Language("\uE894"),
    LockPerson("\uF8F3"),
    Menu("\uE5D2"),
    MoreHoriz("\uE5D3"),
    MoreVert("\uE5D4"),
    Notifications("\uE7F4"),
    Person("\uE7FD"),
    PersonAdd("\uE7FE"),
    PlayArrow("\uE037"),
    Public("\uE80B"),
    Repeat("\uE040"),
    Reply("\uE15E"),
    Search("\uE8B6"),
    VolumeOff("\uE04F"),
    VolumeUp("\uE050"),
    WifiOff("\uE648"),
}
```

> **Codepoint verification**: the values above are the canonical Material Symbols codepoints as of vendoring (2026-05-10). If any test in Step 4 fails, cross-check against the upstream `.codepoints` file linked in the KDoc — Google has changed codepoints once or twice in the project's history, and the unit tests are the safety net.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "net.kikin.nubecita.designsystem.icon.NubecitaIconNameTest"`

Expected: PASS, all 3 tests.

- [ ] **Step 5: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt \
        designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconNameTest.kt
git commit -m "feat(designsystem): add NubecitaIconName enum + codepoint validity tests

Typed glyph identity for the Material Symbols Rounded font. 30
entries — every glyph the app currently uses, with Filled/Outlined
pairs collapsed to a single entry (filled state belongs to the call
site, not the glyph identity).

JVM unit test enforces every codepoint resolves to exactly one
Unicode scalar — catches hex typos at build time.

Refs: nubecita-68g"
```

---

## Task 3: Run the subset script + commit the slim font

**Files:**
- Create: `designsystem/src/main/res/font/material_symbols_rounded.ttf` (the subset, ~50 KB)

Now that `NubecitaIconName.kt` exists (Task 2), the subset script (Task 1) has its codepoint source. This task runs the script, verifies the output, and commits the resulting subset font.

- [ ] **Step 1: Run the subset script**

```bash
./scripts/update_material_symbols.sh
```

Expected output:

```
Found 30 codepoints in NubecitaIconName.kt
Downloading upstream font (~14.9 MB) into build/icon-cache/ ...
Subsetting to 30 codepoints ...
Wrote /…/designsystem/src/main/res/font/material_symbols_rounded.ttf (NN KB)
```

The script's first run downloads the upstream font into `build/icon-cache/material_symbols_rounded_full.ttf`. Subsequent runs reuse the cache. Verify the subset output:

```bash
ls -la designsystem/src/main/res/font/material_symbols_rounded.ttf
```

Expected: 30–80 KB. If much larger (>200 KB) or much smaller (<10 KB), inspect — pyftsubset may have failed to drop unused glyphs (size mismatch) or matched zero codepoints (empty output).

- [ ] **Step 2: Verify the font compiles into the resources**

```bash
./gradlew :designsystem:processDebugResources
```

Expected: BUILD SUCCESSFUL. `R.font.material_symbols_rounded` is now a valid resource.

- [ ] **Step 3: Verify the font's variable axes survive subsetting**

The Compose API in Task 4 reads four axes from the font: `FILL`, `wght`, `GRAD`, `opsz`. `pyftsubset` preserves them by default (`fvar` / `STAT` tables). Sanity-check via the `fonttools` `ttx` tool:

```bash
python3 -m fontTools.ttx -t fvar -o /tmp/material_symbols_rounded.fvar.xml \
    designsystem/src/main/res/font/material_symbols_rounded.ttf
grep -E '<Axis|axisTag' /tmp/material_symbols_rounded.fvar.xml | head
```

Expected: four `<Axis>` entries with `axisTag` values `FILL`, `wght`, `GRAD`, `opsz`. If any are missing, the subset stripped them — re-run the script with `--no-prune-unicode-ranges` (or check pyftsubset version).

- [ ] **Step 4: Commit**

The font is part of the same atomic change as the enum; Task 2 committed the enum on its own, so this task commits just the font (clear single-purpose commit for "regenerate font" semantics that future icon additions will reuse).

```bash
git add designsystem/src/main/res/font/material_symbols_rounded.ttf
git commit -m "chore(designsystem): generate subset Material Symbols Rounded font

First run of scripts/update_material_symbols.sh against the 30
codepoints declared in NubecitaIconName.kt. The shipped font is
~NN KB (down from the upstream 14.9 MB) and preserves the FILL,
wght, GRAD, opsz variable axes consumed by NubecitaIcon.

Refs: nubecita-68g"
```

Replace `NN` with the size from Step 1.

---

## Task 4: Add `NubecitaIcon` composable

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIcon.kt`
- Test: `designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconInstrumentationTest.kt`

This task pulls in `androidx.compose.ui.test.junit4` as an androidTest dep on `:designsystem` (it's not currently wired). The test runs on a connected device per the `feedback_run_instrumentation_tests_after_compose_work` memory rule.

- [ ] **Step 1: Add the Compose UI test dependency to `:designsystem`**

Open `designsystem/build.gradle.kts` and append to the `dependencies { }` block:

```kotlin
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
```

These artifacts are already declared in `gradle/libs.versions.toml` (used by `:feature:composer:impl` etc.); no version-catalog change needed.

- [ ] **Step 2: Write the failing test**

Create `designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconInstrumentationTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.icon

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI coverage for [NubecitaIcon].
 *
 * Two contracts pinned:
 * 1. The composable exposes its `contentDescription` on the merged
 *    a11y tree exactly once — TalkBack reads the icon as a single
 *    semantic node.
 * 2. `filled = true` and `filled = false` produce visually distinct
 *    rasters — proving the FILL axis is wired into the variable font.
 */
class NubecitaIconInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersWithContentDescription_andIsExposedToA11y() {
        composeTestRule.setContent {
            NubecitaIcon(
                name = NubecitaIconName.Search,
                contentDescription = "search",
            )
        }
        composeTestRule
            .onNodeWithContentDescription("search")
            .assertIsDisplayed()
    }

    @Test
    fun fillTrue_rasterDiffersFromFillFalse() {
        // Render both fill states side by side, capture each, and
        // assert their bitmaps are not identical. Variable-font FILL
        // axis is a continuous shape change — the outlined and filled
        // glyphs differ in every Material Symbols icon, so any pair
        // suffices to verify the axis is being applied.
        composeTestRule.setContent {
            Row {
                NubecitaIcon(
                    name = NubecitaIconName.Favorite,
                    contentDescription = "outlined",
                    filled = false,
                    modifier = Modifier.testTag("outlined"),
                )
                NubecitaIcon(
                    name = NubecitaIconName.Favorite,
                    contentDescription = "filled",
                    filled = true,
                    modifier = Modifier.testTag("filled"),
                )
            }
        }
        val outlined = composeTestRule.onNodeWithTag("outlined").captureToImage()
        val filled = composeTestRule.onNodeWithTag("filled").captureToImage()
        // captureToImage returns ImageBitmap; convert to pixel arrays
        // for a structural comparison. Any non-zero pixel diff proves
        // the axis is active.
        val outlinedPixels = IntArray(outlined.width * outlined.height)
        val filledPixels = IntArray(filled.width * filled.height)
        outlined.readPixels(outlinedPixels)
        filled.readPixels(filledPixels)
        assertNotEquals(
            "FILL axis is not affecting the rendered glyph — outlined and filled produced identical pixels",
            outlinedPixels.toList(),
            filledPixels.toList(),
        )
    }
}
```

- [ ] **Step 3: Run test to verify it fails to compile**

Run: `./gradlew :designsystem:compileDebugAndroidTestKotlin`

Expected: FAIL with `Unresolved reference: NubecitaIcon`.

- [ ] **Step 4: Implement the composable**

Create `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIcon.kt`:

```kotlin
package net.kikin.nubecita.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kikin.nubecita.designsystem.R

/**
 * Renders a glyph from the Material Symbols Rounded variable font.
 * The composable replaces every `Icon(imageVector = Icons.X.Y, …)`
 * call site in the app — see `nubecita-68g` and the design doc at
 * `docs/superpowers/specs/2026-05-10-material-symbols-rounded-migration-design.md`.
 *
 * # Axes
 *
 * Material Symbols exposes four continuous axes; this API surfaces
 * them as parameters with sensible defaults:
 *
 * - [filled] — FILL axis (0f outlined / 1f filled). V1 ships a
 *   Boolean for the active/inactive distinction; an animated `Float`
 *   overload is a follow-up.
 * - [weight] — wght axis (100..700). Default 400 matches Google
 *   Material Symbols default; lighter for ambient hints, heavier for
 *   primary actions.
 * - [grade] — GRAD axis (-25..200). Default 0; -25 is low-emphasis
 *   on dark surfaces, 200 is high-emphasis to compensate for color
 *   contrast loss.
 * - [opticalSize] — opsz axis (20.dp..48.dp). Default 24.dp matches
 *   the Compose `Icon` default. Per-call overrides let dense lists
 *   shrink and feature CTAs grow.
 *
 * # Baseline alignment
 *
 * Rendering through `Text` (vs `androidx.compose.material3.Icon`'s
 * Painter) subjects the glyph to font metrics: ascent, descent,
 * leading, and Compose's default `includeFontPadding = true`. Three
 * mitigations applied here, per the design doc's gotcha section:
 *
 * 1. `includeFontPadding = false` strips the platform's default
 *    24sp-line-height padding so the glyph fills the text run
 *    without baked-in margins.
 * 2. `lineHeight = fontSize` locks line height to the icon's display
 *    size so descenders don't push the box taller than expected.
 * 3. The `Box(modifier = Modifier.size(opticalSize), contentAlignment
 *    = Alignment.Center)` wrapper centers the glyph regardless of
 *    residual font metrics. Vertical drift is caught by the
 *    `NubecitaIconShowcaseScreenshotTest` baselines.
 *
 * # Accessibility
 *
 * `contentDescription` is the canonical a11y label; pass `null`
 * for purely decorative icons (matches Compose `Icon` semantics).
 * The descendant `Text` uses `clearAndSetSemantics`-equivalent
 * scoping (the codepoint string is not exposed) — TalkBack reads
 * exactly the localized phrase.
 */
@Composable
fun NubecitaIcon(
    name: NubecitaIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    weight: Int = 400,
    grade: Int = 0,
    opticalSize: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val a11yModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    val variationSettings =
        FontVariation.Settings(
            FontVariation.Setting("FILL", if (filled) 1f else 0f),
            FontVariation.weight(weight),
            FontVariation.Setting("GRAD", grade.toFloat()),
            FontVariation.Setting("opsz", opticalSize.value),
        )
    val fontFamily =
        FontFamily(
            Font(
                resId = R.font.material_symbols_rounded,
                variationSettings = variationSettings,
            ),
        )
    Box(
        modifier = modifier.then(a11yModifier).size(opticalSize),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.codepoint,
            color = tint,
            fontFamily = fontFamily,
            fontSize = opticalSize.value.sp,
            // Locked line height + zero font padding — see the
            // composable's KDoc for the full reasoning.
            style =
                TextStyle(
                    lineHeight = opticalSize.value.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
        )
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

The instrumentation test requires a connected device. Per the
`feedback_run_instrumentation_tests_after_compose_work` memory rule:

```bash
adb devices
# If no device is listed: use the android skill — `android emulator list`
# then `android emulator start <name>`. Notify the user and wait
# ~60 seconds for boot to complete.
./gradlew :designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.kikin.nubecita.designsystem.icon.NubecitaIconInstrumentationTest
```

Expected: PASS, both tests, on the connected device.

- [ ] **Step 6: Commit**

```bash
git add designsystem/build.gradle.kts \
        designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIcon.kt \
        designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconInstrumentationTest.kt
git commit -m "feat(designsystem): add NubecitaIcon composable

Renders Material Symbols Rounded glyphs via Compose Text + variable
font axes (FILL/Weight/Grade/OpticalSize). Replaces the deprecated
material-icons-extended Icon(imageVector = Icons.X.Y, …) pattern
with NubecitaIcon(name = NubecitaIconName.Y, filled = …, …).

Baseline alignment guards baked in: includeFontPadding = false,
locked lineHeight, Box(size + contentAlignment = Center) wrapper.

Refs: nubecita-68g"
```

---

## Task 5: Add `Modifier.mirror()` extension

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/Mirror.kt`
- Test: `designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/MirrorInstrumentationTest.kt`

`NubecitaIcon` doesn't auto-mirror — directional icon call sites apply this opt-in modifier. Verifies in two cases (LTR + RTL).

- [ ] **Step 1: Write the failing test**

Create `designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/MirrorInstrumentationTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.icon

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Pin-down for [Modifier.mirror]:
 * - In LTR layouts, the modifier is a no-op (rendered glyph is
 *   identical to a non-mirrored render).
 * - In RTL layouts, the rendered glyph is horizontally flipped
 *   (rendered pixels differ from the LTR render).
 *
 * The directional icon `ArrowBack` is the natural fixture — its
 * left-pointing chevron makes the flip easy to confirm.
 */
class MirrorInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mirror_inLtr_isNoOp() {
        composeTestRule.setContent {
            Row {
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = "back-bare",
                    modifier = Modifier.testTag("bare"),
                )
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = "back-mirrored",
                    modifier = Modifier.testTag("mirrored").mirror(),
                )
            }
        }
        val bare = composeTestRule.onNodeWithTag("bare").captureToImage()
        val mirrored = composeTestRule.onNodeWithTag("mirrored").captureToImage()
        val barePixels = IntArray(bare.width * bare.height).also(bare::readPixels)
        val mirroredPixels = IntArray(mirrored.width * mirrored.height).also(mirrored::readPixels)
        assertEquals(
            "In LTR, Modifier.mirror() must be a no-op",
            barePixels.toList(),
            mirroredPixels.toList(),
        )
    }

    @Test
    fun mirror_inRtl_flipsHorizontally() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row {
                    NubecitaIcon(
                        name = NubecitaIconName.ArrowBack,
                        contentDescription = "back-bare",
                        modifier = Modifier.testTag("bare"),
                    )
                    NubecitaIcon(
                        name = NubecitaIconName.ArrowBack,
                        contentDescription = "back-mirrored",
                        modifier = Modifier.testTag("mirrored").mirror(),
                    )
                }
            }
        }
        val bare = composeTestRule.onNodeWithTag("bare").captureToImage()
        val mirrored = composeTestRule.onNodeWithTag("mirrored").captureToImage()
        val barePixels = IntArray(bare.width * bare.height).also(bare::readPixels)
        val mirroredPixels = IntArray(mirrored.width * mirrored.height).also(mirrored::readPixels)
        assertNotEquals(
            "In RTL, Modifier.mirror() must produce a horizontally-flipped raster",
            barePixels.toList(),
            mirroredPixels.toList(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails to compile**

Run: `./gradlew :designsystem:compileDebugAndroidTestKotlin`

Expected: FAIL with `Unresolved reference: mirror`.

- [ ] **Step 3: Implement the modifier**

Create `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/Mirror.kt`:

```kotlin
package net.kikin.nubecita.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Horizontally flips the modified composable when the surrounding
 * layout direction is RTL. In LTR the modifier is a no-op.
 *
 * Apply to directional icons (back arrow, reply chevron, etc.) — the
 * deprecated `Icons.AutoMirrored.*` namespace did this automatically;
 * Material Symbols Rounded does not, so mirroring is a per-call-site
 * opt-in. Forgetting to apply this on a directional icon means the
 * arrow points the wrong way in RTL locales — caught by RTL
 * screenshot fixtures.
 */
@Composable
fun Modifier.mirror(): Modifier =
    composed {
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            this.then(Modifier.scale(scaleX = -1f, scaleY = 1f))
        } else {
            this
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.kikin.nubecita.designsystem.icon.MirrorInstrumentationTest
```

Expected: PASS, both tests.

- [ ] **Step 5: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/Mirror.kt \
        designsystem/src/androidTest/kotlin/net/kikin/nubecita/designsystem/icon/MirrorInstrumentationTest.kt
git commit -m "feat(designsystem): add Modifier.mirror() for RTL-aware directional icons

Material Symbols Rounded doesn't auto-mirror; this opt-in modifier
flips horizontally only when LocalLayoutDirection is RTL. Apply on
directional icon call sites (ArrowBack, Reply, Article, etc.) that
were Icons.AutoMirrored.* under the deprecated library.

Refs: nubecita-68g"
```

---

## Task 6: Add `NubecitaIconShowcaseScreenshotTest`

**Files:**
- Create: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconShowcaseScreenshotTest.kt`

A grid of every glyph × {outlined, filled} at the default 24dp size. The fixture is the safety net for baseline-alignment regressions (the Gotchas §11 in the design doc) — vertical drift inside the 24dp box surfaces here before merge.

- [ ] **Step 1: Create the showcase fixture**

Create `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconShowcaseScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews

/**
 * Visual baselines for every [NubecitaIconName] entry in both
 * outlined and filled states. The fixture is intentionally exhaustive
 * — adding a new enum entry without refreshing this baseline shows up
 * as a screenshot diff, surfacing the new icon for visual review.
 *
 * Beyond glyph coverage, the showcase is the safety net for the
 * baseline-alignment gotcha documented in the design doc:
 * `Text`-rendered glyphs can drift vertically inside their box if
 * font metrics or `includeFontPadding` aren't tamed. Eyeball the
 * generated baseline once after migration; subtle off-center
 * regressions surface here before they reach production.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun NubecitaIconShowcasePreviews() {
    NubecitaTheme(dynamicColor = false) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(NubecitaIconName.entries) { entry ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NubecitaIcon(
                            name = entry,
                            contentDescription = "${entry.name} outlined",
                            filled = false,
                        )
                        NubecitaIcon(
                            name = entry,
                            contentDescription = "${entry.name} filled",
                            filled = true,
                        )
                    }
                    Text(text = entry.name)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile screenshot test source**

Run: `./gradlew :designsystem:compileDebugScreenshotTestKotlin`

Expected: PASS.

- [ ] **Step 3: Generate baselines**

Run: `./gradlew :designsystem:updateDebugScreenshotTest`

Expected: BUILD SUCCESSFUL — generates 6 baseline PNGs (1 fixture × 6 from `@PreviewNubecitaScreenPreviews`).

- [ ] **Step 4: Eyeball one baseline**

Open `designsystem/src/screenshotTestDebug/reference/net/kikin/nubecita/designsystem/icon/NubecitaIconShowcaseScreenshotTestKt/NubecitaIconShowcasePreviews_Phone Light_*.png`. Verify:

- All glyphs render (no `?` boxes / missing-glyph tofu).
- Filled vs outlined columns visibly differ for every row.
- Glyphs sit centered inside their 24dp box (no obvious top/bottom drift).
- Labels under each pair match the enum entry name.

If any glyph renders as `?` tofu, its codepoint is wrong — re-check against the upstream `.codepoints` file.

- [ ] **Step 5: Validate**

Run: `./gradlew :designsystem:validateDebugScreenshotTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add designsystem/src/screenshotTest/ \
        designsystem/src/screenshotTestDebug/reference/
git commit -m "test(designsystem): add NubecitaIcon showcase screenshot fixture

Grid of every NubecitaIconName entry × {outlined, filled} at the
default 24dp opticalSize. 6 baselines (1 fixture × Phone/Foldable/
Tablet × Light/Dark from @PreviewNubecitaScreenPreviews). Locks the
visual contract and surfaces baseline-alignment regressions before
merge.

Refs: nubecita-68g"
```

---

## Task 7: Migrate `:designsystem` call sites

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt`
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbed.kt`
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostStat.kt`

`:designsystem` migrates first because it doesn't depend on any other module's icon usage and migrating it last would force re-touching call sites that import its components.

- [ ] **Step 1: Inventory the call sites in this module**

```bash
grep -n "Icons\." designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/*.kt
```

Map every `Icons.X.Y` → `NubecitaIconName.Z` using this rubric:

| Legacy | Migration | Notes |
|---|---|---|
| `Icons.Filled.Foo` (where pair exists) | `NubecitaIcon(name = NubecitaIconName.Foo, filled = true, …)` | Filled state of a Filled/Outlined pair. |
| `Icons.Outlined.Foo` (where pair exists) | `NubecitaIcon(name = NubecitaIconName.Foo, filled = false, …)` | Outlined state. |
| `Icons.Outlined.Foo` (no pair) | `NubecitaIcon(name = NubecitaIconName.Foo, filled = false, …)` | One-off outlined icon. |
| `Icons.Default.Foo` | Map to whichever NubecitaIconName matches the glyph; usually `filled = false`. | `Icons.Default` is a Filled alias. |
| `Icons.AutoMirrored.X.Foo` | `NubecitaIcon(name = NubecitaIconName.Foo, …, modifier = Modifier.mirror())` | Add the mirror modifier so RTL still flips. |
| `BookmarkBorder` | `NubecitaIconName.Bookmark, filled = false` | Material Symbols' `bookmark` glyph fills via FILL axis. |
| `FavoriteBorder` | `NubecitaIconName.Favorite, filled = false` | Same. |
| `ChatBubbleOutline` | `NubecitaIconName.ChatBubble, filled = false` | Same. |
| `ErrorOutline` | `NubecitaIconName.Error, filled = false` | Same. |

Apply this rubric throughout the migration tasks below.

- [ ] **Step 2: Migrate each `:designsystem` file**

For each of the 3 files, replace:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SomeName
import androidx.compose.material3.Icon
// …
Icon(
    imageVector = Icons.Outlined.SomeName,
    contentDescription = "...",
    modifier = ...,
    tint = ...,
)
```

with:

```kotlin
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
// …
NubecitaIcon(
    name = NubecitaIconName.SomeName,
    contentDescription = "...",
    modifier = ...,
    tint = ...,
)
```

Filled/Outlined pairs collapse — find pairs in the same file (e.g. `Icons.Filled.Heart` next to `Icons.Outlined.Heart` for an active/inactive bottom-bar item) and convert them to one site that takes `filled = isActive`.

- [ ] **Step 3: Compile + spotless**

```bash
./gradlew :designsystem:compileDebugKotlin :designsystem:spotlessApply
```

Expected: BUILD SUCCESSFUL. If any `Unresolved reference: Icons` errors remain, re-grep and convert those sites.

- [ ] **Step 4: Refresh `:designsystem`'s screenshot baselines**

The Rounded glyphs render slightly differently from the legacy Material Icons — `:designsystem`'s existing screenshot fixtures need refreshed baselines.

```bash
./gradlew :designsystem:updateDebugScreenshotTest
./gradlew :designsystem:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL.

Eyeball at least one refreshed baseline (e.g. `PostCardScreenshotTestKt/...png`) to confirm the icon swap looks right — same composition, just Rounded glyphs in the icon slots.

- [ ] **Step 5: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/ \
        designsystem/src/screenshotTestDebug/reference/
git commit -m "refactor(designsystem): migrate icon call sites to NubecitaIcon

PostCard, PostCardExternalEmbed, PostStat — every Icon(imageVector =
Icons.X.Y, …) replaced with NubecitaIcon(name = NubecitaIconName.Y, …).
Pre-existing screenshot baselines refreshed wholesale; visual diff
is intentional (Rounded vs default Material Icons style).

Refs: nubecita-68g"
```

---

## Task 8: Migrate `:feature:composer:impl` call sites

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreen.kt`
- Modify: `feature/composer/impl/src/main/kotlin/.../internal/ComposerAttachmentChip.kt`
- Modify: `feature/composer/impl/src/main/kotlin/.../internal/ComposerLanguageChip.kt`
- Modify: `feature/composer/impl/src/main/kotlin/.../internal/ComposerReplyParentSection.kt`

- [ ] **Step 1: Add the `:designsystem` dependency if missing**

Check `feature/composer/impl/build.gradle.kts`:

```bash
grep "designsystem" feature/composer/impl/build.gradle.kts
```

If `implementation(project(":designsystem"))` is not declared, add it. (Most feature modules already depend on `:designsystem` for the theme; this is just defensive.)

- [ ] **Step 2: Migrate each file using the Task 6 rubric**

Apply the same `Icons.X.Y → NubecitaIconName.Y + filled` rubric to all four files. Look for AutoMirrored sites — `ComposerReplyParentSection` likely has the reply chevron — and add `Modifier.mirror()` to those `NubecitaIcon` modifier chains.

Special cases in this module:
- `ComposerLanguageChip.kt` uses `Icons.Filled.Language` for the globe leading icon — migrate to `NubecitaIcon(name = NubecitaIconName.Language, filled = true, …)`. The `AssistChip`'s `leadingIcon` slot accepts the composable directly; no API change needed.
- `ComposerAttachmentChip.kt` likely has a remove button with `Icons.Outlined.Close` — straight `NubecitaIconName.Close` swap.

- [ ] **Step 3: Compile + spotless**

```bash
./gradlew :feature:composer:impl:compileDebugKotlin \
          :feature:composer:impl:spotlessApply
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run pre-existing tests for regressions**

```bash
./gradlew :feature:composer:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL — every pre-existing VM test still passes (icons are pure UI, shouldn't affect VM behavior).

- [ ] **Step 5: Refresh `:feature:composer:impl`'s screenshot baselines**

```bash
./gradlew :feature:composer:impl:updateDebugScreenshotTest
./gradlew :feature:composer:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. Eyeball at least one refreshed composer fixture — the language chip, attachment chip's "Add image" affordance, and the reply chevron should all render in the Rounded style.

- [ ] **Step 6: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/ \
        feature/composer/impl/src/screenshotTestDebug/reference/
git commit -m "refactor(feature/composer): migrate icon call sites to NubecitaIcon

ComposerScreen, ComposerAttachmentChip, ComposerLanguageChip,
ComposerReplyParentSection. Reply chevron carries Modifier.mirror()
for RTL parity with the deprecated Icons.AutoMirrored.Reply that
was previously used. Pre-existing screenshot baselines refreshed.

Refs: nubecita-68g"
```

---

## Task 9: Migrate `:feature:feed:impl` call sites

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt`
- Modify: `feature/feed/impl/src/main/kotlin/.../ui/FeedDetailPlaceholder.kt`
- Modify: `feature/feed/impl/src/main/kotlin/.../ui/FeedEmptyState.kt`
- Modify: `feature/feed/impl/src/main/kotlin/.../ui/FeedErrorState.kt`
- Modify: `feature/feed/impl/src/main/kotlin/.../ui/PostCardVideoEmbed.kt`

- [ ] **Step 1: Verify `:designsystem` dependency**

```bash
grep "designsystem" feature/feed/impl/build.gradle.kts
```

If missing, add `implementation(project(":designsystem"))`.

- [ ] **Step 2: Migrate each file using the Task 6 rubric**

Notable sites in this module (cross-check against current code):
- `FeedErrorState.kt` uses `Icons.Outlined.WifiOff` and `Icons.Outlined.ErrorOutline` — migrate to `NubecitaIconName.WifiOff` and `NubecitaIconName.Error` (collapsed, no `Outline` suffix).
- `PostCardVideoEmbed.kt` uses `Icons.Outlined.PlayArrow` and the volume icons (`VolumeOff`/`VolumeUp`, both AutoMirrored) — migrate the volume sites with `Modifier.mirror()`.
- `FeedScreen.kt` may have a back/menu chevron — apply `Modifier.mirror()` on directional sites.

- [ ] **Step 3: Compile + spotless**

```bash
./gradlew :feature:feed:impl:compileDebugKotlin \
          :feature:feed:impl:spotlessApply
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run pre-existing tests for regressions**

```bash
./gradlew :feature:feed:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Refresh `:feature:feed:impl`'s screenshot baselines**

```bash
./gradlew :feature:feed:impl:updateDebugScreenshotTest
./gradlew :feature:feed:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/feed/impl/src/main/kotlin/ \
        feature/feed/impl/src/screenshotTestDebug/reference/
git commit -m "refactor(feature/feed): migrate icon call sites to NubecitaIcon

FeedScreen + FeedDetailPlaceholder + FeedEmptyState + FeedErrorState
+ PostCardVideoEmbed. Volume icons (formerly Icons.AutoMirrored.*)
carry Modifier.mirror() for RTL. Pre-existing screenshot baselines
refreshed.

Refs: nubecita-68g"
```

---

## Task 10: Migrate `:feature:postdetail:impl` call sites

**Files:**
- Modify: `feature/postdetail/impl/src/main/kotlin/.../PostDetailScreen.kt`

- [ ] **Step 1: Verify `:designsystem` dependency**

```bash
grep "designsystem" feature/postdetail/impl/build.gradle.kts
```

Add `implementation(project(":designsystem"))` if missing.

- [ ] **Step 2: Migrate the file using the Task 6 rubric**

`PostDetailScreen.kt` likely uses `Icons.AutoMirrored.Filled.ArrowBack` or `Outlined.ArrowBack` for the navigation back button — migrate to `NubecitaIconName.ArrowBack` with `Modifier.mirror()`.

- [ ] **Step 3: Compile + spotless**

```bash
./gradlew :feature:postdetail:impl:compileDebugKotlin \
          :feature:postdetail:impl:spotlessApply
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run pre-existing tests for regressions**

```bash
./gradlew :feature:postdetail:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Refresh `:feature:postdetail:impl`'s screenshot baselines**

```bash
./gradlew :feature:postdetail:impl:updateDebugScreenshotTest
./gradlew :feature:postdetail:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/postdetail/impl/src/main/kotlin/ \
        feature/postdetail/impl/src/screenshotTestDebug/reference/
git commit -m "refactor(feature/postdetail): migrate icon call sites to NubecitaIcon

Single file (PostDetailScreen). Back arrow carries Modifier.mirror()
for RTL parity. Pre-existing screenshot baselines refreshed.

Refs: nubecita-68g"
```

---

## Task 11: Migrate `:feature:mediaviewer:impl` call sites

**Files:**
- Modify: `feature/mediaviewer/impl/src/main/kotlin/.../MediaViewerScreen.kt`

- [ ] **Step 1: Verify `:designsystem` dependency**

```bash
grep "designsystem" feature/mediaviewer/impl/build.gradle.kts
```

Add if missing.

- [ ] **Step 2: Migrate the file using the Task 6 rubric**

`MediaViewerScreen.kt` likely has a close button (`Icons.Filled.Close` or `Outlined.Close` → `NubecitaIconName.Close`) plus possibly a share affordance (`Icons.Outlined.IosShare` → `NubecitaIconName.IosShare`).

- [ ] **Step 3: Compile + spotless**

```bash
./gradlew :feature:mediaviewer:impl:compileDebugKotlin \
          :feature:mediaviewer:impl:spotlessApply
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run pre-existing tests + refresh baselines**

```bash
./gradlew :feature:mediaviewer:impl:testDebugUnitTest \
          :feature:mediaviewer:impl:updateDebugScreenshotTest \
          :feature:mediaviewer:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/mediaviewer/impl/src/main/kotlin/ \
        feature/mediaviewer/impl/src/screenshotTestDebug/reference/
git commit -m "refactor(feature/mediaviewer): migrate icon call sites to NubecitaIcon

Single file (MediaViewerScreen). Pre-existing screenshot baselines
refreshed.

Refs: nubecita-68g"
```

---

## Task 12: Migrate `:app` call sites

**Files:**
- Modify: `app/src/main/java/net/kikin/nubecita/shell/MainShell.kt`

`:app` likely contains the `MainShell`'s bottom navigation — the most visible Filled/Outlined-pair use in the app (`Home`/`Home`, `Search`/`Search`, etc.). This is the biggest single visual win of the migration.

- [ ] **Step 1: Migrate the file using the Task 6 rubric**

The bottom nav typically renders:

```kotlin
NavigationBarItem(
    icon = {
        Icon(
            imageVector = if (selected) Icons.Filled.Home else Icons.Outlined.Home,
            contentDescription = stringResource(...),
        )
    },
    selected = selected,
    onClick = ...,
)
```

Migrate to:

```kotlin
NavigationBarItem(
    icon = {
        NubecitaIcon(
            name = NubecitaIconName.Home,
            contentDescription = stringResource(...),
            filled = selected,
        )
    },
    selected = selected,
    onClick = ...,
)
```

The `if (selected) Filled else Outlined` ternary collapses to `filled = selected`. Apply the same pattern to every nav-bar tab (Home, Search, ChatBubble, Person, etc.).

- [ ] **Step 2: Compile + spotless**

```bash
./gradlew :app:compileDebugKotlin :app:spotlessApply
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Refresh `:app`'s screenshot baselines**

```bash
./gradlew :app:updateDebugScreenshotTest
./gradlew :app:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. Inspect the bottom-nav baselines specifically — every tab should render in the Rounded style with the FILL-axis selection visible as a fully-filled glyph in the active tab.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ \
        app/src/screenshotTestDebug/reference/
git commit -m "refactor(app): migrate MainShell bottom-nav icons to NubecitaIcon

Bottom-nav tabs use the FILL axis to render selection state — the
if (selected) Icons.Filled.X else Icons.Outlined.X ternary collapses
to filled = selected on a single NubecitaIcon site per tab. Pre-
existing screenshot baselines refreshed.

Refs: nubecita-68g"
```

---

## Task 13: Drop `material-icons-extended` from every module

**Files:**
- Modify: `designsystem/build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `feature/composer/impl/build.gradle.kts`
- Modify: `feature/feed/impl/build.gradle.kts`
- Modify: `feature/postdetail/impl/build.gradle.kts`
- Modify: `feature/mediaviewer/impl/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

After Tasks 7–12 every call site no longer imports from `androidx.compose.material.icons` — verifiable with a grep. We can now remove the dependency from all 6 modules and from the version catalog.

- [ ] **Step 1: Verify zero remaining icons-extended imports**

```bash
grep -rln "androidx.compose.material.icons" --include="*.kt"
```

Expected: empty output (modulo `openspec/references/` reference samples — those are pinned design references, not production code, and don't import the library at runtime).

If anything in production code still matches, return to the relevant migration task and finish the file before proceeding.

- [ ] **Step 2: Remove the dependency from each module's `build.gradle.kts`**

In each of the 6 files, delete the line:

```kotlin
implementation(libs.androidx.compose.material.icons.extended)
```

The 6 files:

```
designsystem/build.gradle.kts
app/build.gradle.kts
feature/composer/impl/build.gradle.kts
feature/feed/impl/build.gradle.kts
feature/postdetail/impl/build.gradle.kts
feature/mediaviewer/impl/build.gradle.kts
```

- [ ] **Step 3: Remove the version-catalog entry**

In `gradle/libs.versions.toml`, remove the line:

```toml
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
```

(There's no `[versions]` entry for this artifact since the version comes from the Compose BOM.)

- [ ] **Step 4: Verify the project still builds**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL across every module. Any unresolved reference would mean a call site was missed in Tasks 7–12.

- [ ] **Step 5: Capture APK size delta (for the PR description)**

```bash
ls -la app/build/outputs/apk/debug/app-debug.apk
```

Note the current size; compare to the pre-migration baseline (`git show main:app/build/outputs/apk/debug/app-debug.apk` won't work since APKs aren't versioned — capture from a clean checkout of `main` separately if a precise comparison is needed). The expectation is a moderate decrease; even a flat outcome is informative.

- [ ] **Step 6: Commit**

```bash
git add designsystem/build.gradle.kts \
        app/build.gradle.kts \
        feature/composer/impl/build.gradle.kts \
        feature/feed/impl/build.gradle.kts \
        feature/postdetail/impl/build.gradle.kts \
        feature/mediaviewer/impl/build.gradle.kts \
        gradle/libs.versions.toml
git commit -m "chore(deps): drop material-icons-extended from every module

After tasks 6-11 migrated every call site to NubecitaIcon, the
deprecated library has zero references. Removed from 6 module
build.gradle.kts files and from the version catalog.

Refs: nubecita-68g"
```

---

## Task 14: Codify the contract in `design-system/spec.md`

**Files:**
- Modify: `openspec/specs/design-system/spec.md`

- [ ] **Step 1: Add the new requirement**

Locate `openspec/specs/design-system/spec.md`. Append the following requirement at the end of the `## Requirements` section (before any `## Out of scope` or trailing matter):

```markdown
### Requirement: Iconography uses Material Symbols Rounded via `NubecitaIcon`

The system SHALL render every in-app icon through the `NubecitaIcon` composable in `:designsystem` (`net.kikin.nubecita.designsystem.icon.NubecitaIcon`). Glyph identity is supplied by the typed `NubecitaIconName` enum; visual variants are controlled by the `filled: Boolean` (FILL axis), `weight: Int = 400` (wght), `grade: Int = 0` (GRAD), and `opticalSize: Dp = 24.dp` (opsz) parameters. The font asset MUST be the vendored `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` at `R.font.material_symbols_rounded`. Direct use of `androidx.compose.material.icons.*` (the deprecated `material-icons-extended` library) is forbidden in production source.

#### Scenario: Active/inactive state collapses to the FILL axis

- **GIVEN** a navigation tab with active and inactive states
- **WHEN** the tab renders its icon
- **THEN** a single `NubecitaIcon(name = NubecitaIconName.X, filled = isActive, …)` site SHALL render both states (no `if (active) Filled else Outlined` ternary against two different glyph identities)

#### Scenario: Directional icons opt into RTL mirroring

- **GIVEN** a directional icon (back arrow, reply chevron, etc.)
- **WHEN** the icon renders in an RTL locale
- **THEN** the call site SHALL apply `Modifier.mirror()` from `:designsystem`'s icon package; the modifier is a no-op in LTR

#### Scenario: Adding a new glyph requires an enum entry

- **WHEN** a feature requires a Material Symbols glyph not currently in `NubecitaIconName`
- **THEN** the contributor SHALL add a new enum entry (one line: `NewName("\uXXXX"),` with the upstream codepoint) before referencing it from a call site; no inline-codepoint usage of `NubecitaIcon` is supported

#### Scenario: Material Icons library is not a runtime dependency

- **WHEN** the project's module `build.gradle.kts` files are inspected
- **THEN** none SHALL declare `androidx.compose.material:material-icons-extended` (or any artifact under `androidx.compose.material.icons.*`); the version-catalog entry MUST also be absent
```

- [ ] **Step 2: Validate**

Run: `openspec validate --all --strict`

Expected: `Totals: 27 passed, 0 failed (27 items)` (one more than the pre-task count; adjust the totals expectation against the live count).

- [ ] **Step 3: Commit**

```bash
git add openspec/specs/design-system/spec.md
git commit -m "docs(openspec): add design-system requirement for NubecitaIcon

Codifies the icon-system contract: NubecitaIcon as the single entry
point, NubecitaIconName as the typed glyph identity, FILL-axis
collapse for active/inactive pairs, opt-in RTL mirroring on
directional icons, and the rule that the deprecated material-icons-
extended library is not a runtime dependency anywhere in the
project.

Refs: nubecita-68g"
```

---

## Task 15: Final integration check + push + open PR

**Files:** none new — verification only.

- [ ] **Step 1: Run the full lint + test + screenshot sweep**

```bash
./gradlew :designsystem:testDebugUnitTest \
          :designsystem:lint \
          :designsystem:validateDebugScreenshotTest \
          :feature:composer:impl:testDebugUnitTest \
          :feature:composer:impl:lint \
          :feature:composer:impl:validateDebugScreenshotTest \
          :feature:feed:impl:testDebugUnitTest \
          :feature:feed:impl:lint \
          :feature:feed:impl:validateDebugScreenshotTest \
          :feature:postdetail:impl:testDebugUnitTest \
          :feature:postdetail:impl:lint \
          :feature:postdetail:impl:validateDebugScreenshotTest \
          :feature:mediaviewer:impl:testDebugUnitTest \
          :feature:mediaviewer:impl:lint \
          :feature:mediaviewer:impl:validateDebugScreenshotTest \
          :app:lint \
          :app:validateDebugScreenshotTest \
          spotlessCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run all instrumentation tests on a connected device**

Per the `feedback_run_instrumentation_tests_after_compose_work` memory rule.

```bash
adb devices
# If no device connected: launch one via the android-cli skill, notify
# the user, and recheck after ~60s.
./gradlew :designsystem:connectedDebugAndroidTest \
          :feature:composer:impl:connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL — all `:designsystem` icon tests pass alongside the pre-existing composer instrumentation suite.

- [ ] **Step 3: Push the branch**

```bash
git push -u origin chore/nubecita-68g-designsystem-migrate-from-material-icons-extended
```

- [ ] **Step 4: Open the PR**

```bash
gh pr create --base main \
  --title "chore(designsystem): migrate to Material Symbols Rounded variable font" \
  --body "$(cat <<'EOF'
## Summary

Replaces the deprecated `androidx.compose.material:material-icons-extended` library with the Material Symbols Rounded variable font, exposed via a `NubecitaIcon(name = NubecitaIconName.X, filled = …, …)` composable in `:designsystem`. Every call site across 6 modules migrates; the legacy library is dropped from every `build.gradle.kts` and the version catalog.

Design: `docs/superpowers/specs/2026-05-10-material-symbols-rounded-migration-design.md`
Plan: `docs/superpowers/plans/2026-05-10-material-symbols-rounded-migration.md`

## Changes

- **`:designsystem`** — vendored Apache-2.0 `material_symbols_rounded.ttf`, new `NubecitaIcon` + `NubecitaIconName` + `Modifier.mirror()` + tests + showcase screenshot fixture, then call-site migration in `PostCard` / `PostCardExternalEmbed` / `PostStat`. New `design-system/spec.md` requirement codifies the contract with 4 scenarios.
- **Feature modules** (`composer`, `feed`, `postdetail`, `mediaviewer`) — call-site migrations + refreshed screenshot baselines.
- **`:app`** — `MainShell` bottom-nav tabs collapse the active/inactive `Icons.Filled` / `Icons.Outlined` ternary into one `NubecitaIcon(filled = selected)` site per tab.
- **Build files** — `material-icons-extended` removed from 6 modules and from `gradle/libs.versions.toml`.

## APK size

| Build | Size |
|---|---|
| Pre-migration (`main`) | <REPLACE_WITH_BASELINE_SIZE> |
| Post-migration | <REPLACE_WITH_NEW_SIZE> |
| Delta | <REPLACE_WITH_DELTA> |

## Test plan

- [x] `:designsystem:testDebugUnitTest` — `NubecitaIconNameTest` (3 tests) green; codepoint validity enforced at build time.
- [x] `:designsystem:connectedDebugAndroidTest` — `NubecitaIconInstrumentationTest` + `MirrorInstrumentationTest` green on connected device.
- [x] `:designsystem:validateDebugScreenshotTest` — new `NubecitaIconShowcaseScreenshotTest` baselines validate (6 PNGs).
- [x] All feature modules' `:validateDebugScreenshotTest` green against refreshed baselines.
- [x] All pre-existing `:testDebugUnitTest` suites green (no VM regressions).
- [x] `:app:lint`, `:designsystem:lint`, every feature `:lint` clean.
- [x] `openspec validate --all --strict` — 27/27 specs pass.
- [x] `spotlessCheck` clean.
- [x] `grep -rln "androidx.compose.material.icons" --include="*.kt"` returns zero production hits.

Closes: nubecita-68g
EOF
)"
```

Expected: PR URL printed.

- [ ] **Step 5: Add the `run-instrumented` label**

Per the `feedback_run_instrumented_label_on_androidtest_prs` memory rule, this PR touches `*/src/androidTest/**` (Tasks 3 & 4 add new instrumentation tests in `:designsystem`).

```bash
gh pr edit <PR_NUMBER> --add-label run-instrumented
```

- [ ] **Step 6: Capture and fill in the APK size delta**

Build the pre-migration baseline once for comparison, then update the PR body's APK-size table:

```bash
# Capture pre-migration size from main:
git stash
git checkout main
./gradlew :app:assembleDebug
ls -la app/build/outputs/apk/debug/app-debug.apk
# Note the size, then return to the branch:
git checkout chore/nubecita-68g-designsystem-migrate-from-material-icons-extended
git stash pop
./gradlew :app:assembleDebug
ls -la app/build/outputs/apk/debug/app-debug.apk
# Note the new size; compute delta.
```

Update the PR body's APK-size table with the captured numbers via `gh pr edit <PR_NUMBER> --body "..."` or the GitHub UI.

---

## Self-review checklist

| Check | Notes |
|---|---|
| **Spec coverage** | Decisions §1 (subset script + ship slim font) → Tasks 1 + 3; §2 (API) → Tasks 2+4; §3 (codepoint subset) → Task 2; §4 (Filled/Outlined collapse) → Task 7's rubric, applied throughout 6–11; §5 (Mirror) → Task 5; §6 (`Boolean filled` in V1) → Task 4; §7 (weight/grade defaults) → Task 4; §8 (file layout) → all tasks; §9 (test plan) → Tasks 2/4/5/6 + per-module screenshot regen; §10 (APK delta) → Task 15 step 6; §11 (gotchas) → Task 4's KDoc + Task 6's screenshot eyeball; §12 (acceptance) → Task 15 step 1's grep + lint/test sweep. |
| **Placeholder scan** | One `<REPLACE_WITH_…>` placeholder in the PR body's APK-size table — explicitly flagged for the implementer to fill in at Task 15 step 6. The `<REPLACE_WITH_SHA_FROM_STEP_2>` in Task 1's license file is similarly explicit. No silent placeholders. |
| **Type consistency** | `NubecitaIcon(name, contentDescription, modifier, filled, weight, grade, opticalSize, tint)` signature matches Tasks 4, 7–11, 13. `NubecitaIconName(internal val codepoint: String)` matches Tasks 2 + 4. `Modifier.mirror()` matches Tasks 5, 8, 10. The migration rubric in Task 7 step 1 is the source of truth all later migration tasks point at. |
| **Determinism** | Codepoints in Task 2's enum are pinned to the upstream Material Symbols values as of 2026-05-10; the `every_codepoint_isASingleScalar` test catches drift. Showcase fixture (Task 5) pins visual contract across `@PreviewNubecitaScreenPreviews`'s 6 buckets. |
| **TDD discipline** | Tasks 2, 4, 5 follow the failing-test → run-fails → impl → run-passes → commit cycle. Tasks 1, 3, 6, 7–12, 13, 14 are non-TDD (asset vendoring, screenshot fixtures, mechanical migrations, build-config edits, spec edits) — each with explicit verification steps in lieu of a test cycle. |
