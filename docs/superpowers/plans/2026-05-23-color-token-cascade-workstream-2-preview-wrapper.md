# Color Token Cascade — Workstream 2 (Preview Wrapper) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `NubecitaCanvasPreviewTheme` — a screenshot/preview-only theme wrapper that paints the screen-canvas role — and migrate every **screen-level** `*ScreenshotTest.kt` fixture to use it. Component-level fixtures (atoms like avatars, buttons, single rows, post cards) stay on `NubecitaTheme(dynamicColor = false)` because the wrapper's `Modifier.fillMaxSize()` would balloon atoms with intrinsic-fill behavior. After this lands, dark-mode baselines for screen-level fixtures (that previously fractured into transparent components over a white canvas) finally show the expected dark canvas.

**Architecture:** The wrapper composes `NubecitaTheme(darkTheme, dynamicColor = false)` + `Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface)`. `dynamicColor = false` keeps Layoutlib's baseline rendering deterministic across emulator setups. `fillMaxSize()` guarantees full canvas coverage on screen-level fixtures so a component using custom `LayoutModifier` or edge-to-edge drawing can't leave a transparent gutter that the IDE paints white. Production composables and androidTest interaction tests stay on `NubecitaTheme` — only **screen-level** `*ScreenshotTest.kt` fixtures migrate to the wrapper.

**Tech Stack:** Jetpack Compose Material 3, AGP screenshot testing (`com.android.tools.screenshot.PreviewTest`), Kotlin.

**Spec:** `docs/superpowers/specs/2026-05-23-color-token-cascade-design.md` (§"Architecture / The preview/screenshot theme wrapper")

**Reference page:** `docs/design-system/surface-roles.md`

**bd:** Create a new bd issue `nubecita-XXXX` of type `feature`, title "Add NubecitaCanvasPreviewTheme and migrate screen-level screenshot test fixtures". Branch off `main` as `feat/nubecita-XXXX-<slug>` via the `bd-workflow` skill's start flow.

**Screen-level vs component-level — the test:** A fixture is *screen-level* if its intent is to render against a full-bleed canvas — screens, full-screen states, dialogs, panes. A fixture is *component-level* if it tests a single atom or molecule that should render at its natural bounds — avatars, buttons, single rows, chips, post cards, message bubbles. Painting a full canvas under a component-level fixture causes intrinsic-fill atoms (`NubecitaAvatar` placeholder, shimmer, image placeholders) to balloon to the preview viewport.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/NubecitaCanvasPreviewTheme.kt` | Create | The wrapper composable. Public; lives next to other preview helpers. |
| `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/preview/NubecitaCanvasPreviewThemeScreenshotTest.kt` | Create | Self-test: light + dark fixtures that demonstrate the canvas paint, so regression in the wrapper itself shows up as a baseline change. |
| 21 × screen-level `*ScreenshotTest.kt` | Modify (mechanical) | Swap import + call site. Identical recipe per file (see Task 4). |

**Screen-level fixtures to migrate** (21 total — explicit list):

```
app/src/screenshotTest/java/net/kikin/nubecita/shell/composer/ComposerOverlayScreenshotTest.kt
app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellChromeScreenshotTest.kt
app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellListDetailScreenshotTest.kt
feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContentScreenshotTest.kt
feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreenContentScreenshotTest.kt
feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerDiscardDialogScreenshotTest.kt
feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreenScreenshotTest.kt
feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/LanguagePickerScreenshotTest.kt
feature/feed/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreenScreenshotTest.kt
feature/feed/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/feed/impl/ui/FeedEmptyStateScreenshotTest.kt
feature/feed/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/feed/impl/ui/FeedErrorStateScreenshotTest.kt
feature/login/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/login/impl/LoginScreenScreenshotTest.kt
feature/mediaviewer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/mediaviewer/impl/MediaViewerScreenScreenshotTest.kt
feature/moderation/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/moderation/impl/ReportDialogScreenshotTest.kt
feature/onboarding/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/onboarding/impl/OnboardingScreenScreenshotTest.kt
feature/postdetail/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailScreenScreenshotTest.kt
feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt
feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/SettingsStubScreenScreenshotTest.kt
feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreenScreenshotTest.kt
feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/SearchTypeaheadContentScreenshotTest.kt
feature/videoplayer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videoplayer/impl/ui/VideoPlayerContentScreenshotTest.kt
```

Each migrates from `NubecitaTheme(dynamicColor = false) {` to `NubecitaCanvasPreviewTheme {` plus an import swap (see Task 4 for the exact recipe).

**Stays on `NubecitaTheme(dynamicColor = false)`** — every other `*ScreenshotTest.kt` file (~53 component-level fixtures): all `:designsystem/src/screenshotTest/kotlin/.../component/*ScreenshotTest.kt` (PostCard, PostCardExternalEmbed, NubecitaAvatar, NubecitaLogo, NubecitaPrimaryButton, BlockedPostCard, NotFoundPostCard, Shimmer, ThreadConnector, etc.), `tabs/ProfilePillTabs`, `icon/NubecitaIconShowcase`, `hero/BoldHeroGradient`, `TypographyScale`, plus all the per-feature UI atoms (`MessageBubble`, `ConvoListItem`, `DaySeparatorChip`, `ProfileTopBar`, `ProfileStatsRow`, `ProfileMetaRow`, `ProfileMediaTabBody`, all `:feature:search:impl/ui/*ScreenshotTest.kt`, `FeedAppendingIndicator`, `PostCardVideoEmbed`, `PostCardQuotedPostWithVideo`, `PostCardRecordWithMediaEmbedWithVideo`, `ComposerScreenLanguageChip`). Painting a full canvas under these would balloon any intrinsic-fill child.

**Out of scope** (production code keeps `NubecitaTheme`):

- `app/src/main/java/net/kikin/nubecita/MainActivity.kt`
- `feature/login/impl/src/main/.../LoginScreen.kt`
- `feature/onboarding/impl/src/main/.../OnboardingScreen.kt`
- `feature/postdetail/impl/src/main/.../PostDetailScreen.kt`
- `feature/videoplayer/impl/src/main/.../VideoPlayerContent.kt`
- `feature/moderation/impl/src/main/.../ReportDialogPreviews.kt`
- `:designsystem`'s `src/main/.../preview/*.kt` showcase composables (BoldHeroGradientPreview, ColorRolesPreview, ProfilePillTabsPreview, MotionShowcasePreview, ShapeScalePreview, TypographyScalePreview)
- Any `src/androidTest/.../*Test.kt` (instrumentation/interaction tests; not screenshot fixtures)

---

## Preconditions

1. Workstream 1 has landed: `git show origin/main:docs/design-system/surface-roles.md > /dev/null` exits 0.
2. Working tree is clean (`git status` shows no tracked changes).
3. Current branch is `main` and up to date (`git pull --ff-only`).

---

## Task 1: Create the bd issue and branch

- [ ] **Step 1: Create the bd issue**

```bash
cat > /tmp/bd-screen-preview-theme-desc.md <<'EOF'
Materializes workstream 2 of the color-token-cascade spec (nubecita-kkla):
add NubecitaCanvasPreviewTheme wrapper + migrate every *ScreenshotTest.kt
fixture to use it. After this lands, dark-mode baselines that previously
fractured (white canvas under transparent components) finally show the
expected dark canvas.

Spec: docs/superpowers/specs/2026-05-23-color-token-cascade-design.md
Plan: docs/superpowers/plans/2026-05-23-color-token-cascade-workstream-2-preview-wrapper.md
Reference: docs/design-system/surface-roles.md

21 screen-level fixtures migrate; component-level fixtures stay on
NubecitaTheme. PR carries the `update-baselines` label so CI regenerates
the dark-mode reference images for the migrated fixtures.
EOF
bd create "Add NubecitaCanvasPreviewTheme and migrate screen-level screenshot test fixtures" \
  --type feature --priority 2 \
  --body-file /tmp/bd-screen-preview-theme-desc.md \
  --json | jq -r '.id'
```
Expected: prints a new bd id like `nubecita-XXXX`. Record as `BD_ID`.

- [ ] **Step 2: Branch + claim**

Invoke `bd-workflow` skill: "start `<BD_ID>`". Verifies tree-clean + on main; creates `feat/<BD_ID>-add-nubecitascreenpreviewtheme-and-migrate-every-scre`; runs `bd update <BD_ID> --claim`.

---

## Task 2: Write the wrapper

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/NubecitaCanvasPreviewTheme.kt`

- [ ] **Step 1: Write the file**

Use the `Write` tool with this exact content:

```kotlin
package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot + preview theme wrapper. Paints the screen-canvas role
 * (`MaterialTheme.colorScheme.surface`) at full size so dark-mode `@Preview`
 * fixtures stop fracturing into transparent components over the IDE's
 * default white canvas.
 *
 * Use this in every `*ScreenshotTest.kt` fixture instead of [NubecitaTheme]
 * directly. Production composables and androidTest interaction tests stay
 * on [NubecitaTheme] — the production `Scaffold` / modal `Surface` paints
 * the canvas in real app code; this wrapper exists only to substitute for
 * that paint in headless preview/screenshot rendering.
 *
 * Two pinned defaults:
 *
 * - `dynamicColor = false` — Layoutlib's dynamic-color fallback varies
 *   across emulator configurations, so screenshot baselines stay
 *   deterministic only with the brand color scheme.
 * - `Modifier.fillMaxSize()` on the `Surface` — guarantees full canvas
 *   coverage so a component using a custom `LayoutModifier` or edge-to-edge
 *   drawing can't leave a transparent gutter the IDE renderer paints white.
 *
 * Full role contract: `docs/design-system/surface-roles.md`.
 */
@Composable
fun NubecitaCanvasPreviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    NubecitaTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    }
}
```

- [ ] **Step 2: Verify spotless on the new file**

```bash
./gradlew :designsystem:spotlessKotlinCheck
```
Expected: `BUILD SUCCESSFUL`. Fix any ktlint flag and rerun; never bypass with `--no-verify`.

- [ ] **Step 3: Commit**

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/NubecitaCanvasPreviewTheme.kt
git commit -m "$(cat <<'COMMIT'
feat(designsystem): add NubecitaCanvasPreviewTheme wrapper

Screenshot + preview theme wrapper that paints the screen-canvas role
(MaterialTheme.colorScheme.surface) at full size. Pinned to dynamicColor
= false for deterministic baselines; uses Modifier.fillMaxSize() to
guarantee full canvas coverage so components using custom LayoutModifier
or edge-to-edge drawing can't leave a transparent gutter.

Used by *ScreenshotTest.kt fixtures only; production composables and
androidTest interaction tests stay on NubecitaTheme.

Workstream 2 of nubecita-kkla.

Refs: <BD_ID>
COMMIT
)"
```

---

## Task 3: Write the wrapper self-test

**Files:**
- Create: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/preview/NubecitaCanvasPreviewThemeScreenshotTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package net.kikin.nubecita.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * Pins the canvas paint produced by [NubecitaCanvasPreviewTheme] in both
 * light and dark mode. A transparent content slice (`Text` only) on top
 * of the wrapper proves the wrapper itself owns the canvas — if the
 * wrapper regresses (loses `fillMaxSize()`, drops the `Surface`, etc.),
 * one or both of these baselines will diff and CI will catch it.
 */
@PreviewTest
@Preview(name = "canvas-preview-theme-light", showBackground = true)
@Preview(
    name = "canvas-preview-theme-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NubecitaCanvasPreviewThemeCanvasScreenshot() {
    NubecitaCanvasPreviewTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("canvas")
        }
    }
}
```

- [ ] **Step 2: Verify spotless**

```bash
./gradlew :designsystem:spotlessKotlinCheck
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify the screenshot test compiles**

```bash
./gradlew :designsystem:compileDebugScreenshotTestKotlin
```
Expected: `BUILD SUCCESSFUL`. The test compiles even though the baseline images don't exist yet — those generate via the `update-baselines` CI label after PR push.

- [ ] **Step 4: Commit**

```bash
git add designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/preview/NubecitaCanvasPreviewThemeScreenshotTest.kt
git commit -m "$(cat <<'COMMIT'
test(designsystem): add screenshot self-test for NubecitaCanvasPreviewTheme

Pins the canvas paint produced by the wrapper in light + dark. A
transparent content slice (Text only) proves the wrapper itself owns
the canvas; if the wrapper regresses (loses fillMaxSize, drops Surface,
etc.), one or both of these baselines will diff and CI will catch it.

Refs: <BD_ID>
COMMIT
)"
```

---

## Task 4: Migrate the 21 screen-level screenshot test fixtures

**Files:** the 21 screen-level `*ScreenshotTest.kt` files listed in the "Screen-level fixtures to migrate" section above. Component-level fixtures (everything else) stay on `NubecitaTheme(dynamicColor = false)`.

The migration is two literal find-and-replace operations applied to the explicit screen-fixture list. No semantic changes.

- [ ] **Step 1: Write the screen-fixture list to a file**

```bash
cat > /tmp/screen-fixtures.txt <<'EOF'
app/src/screenshotTest/java/net/kikin/nubecita/shell/composer/ComposerOverlayScreenshotTest.kt
app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellChromeScreenshotTest.kt
app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellListDetailScreenshotTest.kt
feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContentScreenshotTest.kt
feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsScreenContentScreenshotTest.kt
feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerDiscardDialogScreenshotTest.kt
feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreenScreenshotTest.kt
feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/LanguagePickerScreenshotTest.kt
feature/feed/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreenScreenshotTest.kt
feature/feed/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/feed/impl/ui/FeedEmptyStateScreenshotTest.kt
feature/feed/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/feed/impl/ui/FeedErrorStateScreenshotTest.kt
feature/login/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/login/impl/LoginScreenScreenshotTest.kt
feature/mediaviewer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/mediaviewer/impl/MediaViewerScreenScreenshotTest.kt
feature/moderation/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/moderation/impl/ReportDialogScreenshotTest.kt
feature/onboarding/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/onboarding/impl/OnboardingScreenScreenshotTest.kt
feature/postdetail/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/postdetail/impl/PostDetailScreenScreenshotTest.kt
feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt
feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/SettingsStubScreenScreenshotTest.kt
feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreenScreenshotTest.kt
feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/SearchTypeaheadContentScreenshotTest.kt
feature/videoplayer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/videoplayer/impl/ui/VideoPlayerContentScreenshotTest.kt
EOF
wc -l /tmp/screen-fixtures.txt
```
Expected: prints `21`. Verify every path exists with `while IFS= read -r p; do test -f "$p" || echo "MISSING: $p"; done < /tmp/screen-fixtures.txt` (no output = all present).

- [ ] **Step 2: Inspect the current call-shape uniformity (sanity check)**

```bash
while IFS= read -r p; do grep -h "NubecitaTheme(" "$p"; done < /tmp/screen-fixtures.txt | sort -u
```
Expected output: exactly one line — `        NubecitaTheme(dynamicColor = false) {`. If more shapes appear, **stop** and re-evaluate per-call edits before continuing.

- [ ] **Step 3: Apply the import + call-site replacement to each screen fixture**

```bash
while IFS= read -r p; do
  sed -i '' 's|^import net\.kikin\.nubecita\.designsystem\.NubecitaTheme$|import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme|' "$p"
  sed -i '' 's|NubecitaTheme(dynamicColor = false) {|NubecitaCanvasPreviewTheme {|g' "$p"
done < /tmp/screen-fixtures.txt
```
Expected: silent. Verify with `git diff --stat /tmp/screen-fixtures.txt` shows exactly 21 files modified.

- [ ] **Step 4: Sanity-check the diff**

```bash
# Confirm no remaining NubecitaTheme(...) calls in the screen fixtures
while IFS= read -r p; do grep -n "NubecitaTheme(" "$p" 2>/dev/null; done < /tmp/screen-fixtures.txt
```
Expected: no output. If any lines print, those are unmigrated call sites — investigate by hand.

```bash
# Confirm component-level fixtures still use NubecitaTheme
component_count=$(grep -rl "NubecitaTheme(dynamicColor = false) {" --include="*ScreenshotTest.kt" . 2>/dev/null | grep -v "/build/" | wc -l)
echo "component fixtures still on NubecitaTheme: $component_count"
```
Expected: ~53 (every `*ScreenshotTest.kt` that is NOT in `/tmp/screen-fixtures.txt`).

```bash
# Confirm screen fixtures are now on the wrapper
wrapper_count=$(grep -rl "NubecitaCanvasPreviewTheme {" --include="*ScreenshotTest.kt" . 2>/dev/null | grep -v "/build/" | wc -l)
echo "fixtures using the wrapper: $wrapper_count"
```
Expected: `22` (21 screen fixtures + the wrapper's self-test).

- [ ] **Step 5: Verify spotless across every affected module**

```bash
./gradlew spotlessKotlinCheck
```
Expected: `BUILD SUCCESSFUL`. If any module reports formatting, **inspect first** — sed can leave whitespace if the source had trailing whitespace. Apply `./gradlew spotlessApply` to auto-fix, then re-verify with `spotlessKotlinCheck`.

- [ ] **Step 6: Verify screenshot test compilation across every affected module**

```bash
./gradlew :app:compileDebugScreenshotTestKotlin \
          :designsystem:compileDebugScreenshotTestKotlin \
          :feature:chats:impl:compileDebugScreenshotTestKotlin \
          :feature:composer:impl:compileDebugScreenshotTestKotlin \
          :feature:feed:impl:compileDebugScreenshotTestKotlin \
          :feature:login:impl:compileDebugScreenshotTestKotlin \
          :feature:mediaviewer:impl:compileDebugScreenshotTestKotlin \
          :feature:moderation:impl:compileDebugScreenshotTestKotlin \
          :feature:onboarding:impl:compileDebugScreenshotTestKotlin \
          :feature:postdetail:impl:compileDebugScreenshotTestKotlin \
          :feature:profile:impl:compileDebugScreenshotTestKotlin \
          :feature:search:impl:compileDebugScreenshotTestKotlin \
          :feature:videoplayer:impl:compileDebugScreenshotTestKotlin
```
Expected: `BUILD SUCCESSFUL`. Compilation failures here mean an unmigrated call site exists somewhere — go back to step 4's sanity grep.

- [ ] **Step 7: Commit the migration**

```bash
git add -A
git commit -m "$(cat <<'COMMIT'
refactor(screenshot-tests): migrate screen-level fixtures to NubecitaCanvasPreviewTheme

Targeted sweep across 21 screen-level *ScreenshotTest.kt files in :app,
:designsystem (none in v1; designsystem is all components), and 9 feature
modules. Two replacements per file:

- `import net.kikin.nubecita.designsystem.NubecitaTheme`
  → `import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme`
- `NubecitaTheme(dynamicColor = false) {`
  → `NubecitaCanvasPreviewTheme {`

Component-level fixtures (~53 atoms across designsystem + feature ui/
folders) stay on NubecitaTheme(dynamicColor = false) — the wrapper's
fillMaxSize would balloon intrinsic-fill atoms.

Production composables and androidTest interaction tests stay on
NubecitaTheme; only screenshot fixtures migrate.

Baselines will regenerate via the `update-baselines` CI label on this PR
— dark-mode previously fractured (white canvas under transparent
components) will finally show the expected dark canvas.

Refs: <BD_ID>
COMMIT
)"
```

---

## Task 5: Full local verification

**Files:** none.

- [ ] **Step 1: Verify all three expected commits are on the branch**

```bash
git log --oneline main..HEAD
```
Expected: three commits in order (newest first):
1. `refactor(screenshot-tests): migrate screen-level fixtures to NubecitaCanvasPreviewTheme`
2. `test(designsystem): add screenshot self-test for NubecitaCanvasPreviewTheme`
3. `feat(designsystem): add NubecitaCanvasPreviewTheme wrapper`

- [ ] **Step 2: Verify the diff scope**

```bash
git diff --stat main...HEAD | tail -5
```
Expected: ~23 files changed (1 wrapper, 1 self-test, 21 screen-fixture migrations). If far fewer or far more, **stop** and investigate.

- [ ] **Step 3: App graph still links**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. Catches any DI graph break or transitive issue. Should be a no-op since the wrapper is additive and migrations are file-local.

- [ ] **Step 4: Run the full pre-commit gate**

```bash
pre-commit run --all-files
```
Expected: all hooks pass (or `Skipped` for hooks with no matching files).

---

## Task 6: Push and open PR with `update-baselines` label

**Files:** none.

- [ ] **Step 1: Use the bd-workflow finish flow**

Invoke `bd-workflow` skill: "open the PR". The skill will:
- Verify branch + tree state
- `./gradlew :app:assembleDebug` (already passed in Task 5; the skill may re-run — that's fine)
- Module `lintDebug` for every affected module (`:designsystem`, `:app`, every `:feature:*:impl`). This is the right gate even for refactor-only changes; Compose lints (stability, modifier order) sometimes flag side effects of refactors
- `git push -u origin <branch>`
- `gh pr create --base main --title "<first-commit-subject>" --body "Closes: <BD_ID>"`
- Request Copilot reviewer
- **Do NOT spin up the CI cron monitor** per `feedback_ci_monitor_only_when_afk` — the user is interactive

Expected: PR URL printed. Note the PR number as `PR_NUM`.

- [ ] **Step 2: Add the `update-baselines` label**

```bash
gh pr edit <PR_NUM> --add-label update-baselines
```
Expected: silent success. Per `feedback_compose_preview_workflow`, this triggers the CI workflow that regenerates screenshot baselines and commits them back to the PR.

- [ ] **Step 3: Add the `run-instrumented` label IF any androidTest files were touched**

This plan does NOT touch androidTest files, so the label is **not** required. The check is here for paranoia — if `git diff main...HEAD -- '*/src/androidTest/*'` prints anything, **stop** and investigate. If it really should be there, add the label per `feedback_run_instrumented_label_on_androidtest_prs`.

```bash
git diff --stat main...HEAD -- '*/src/androidTest/*'
```
Expected: no output (no androidTest files changed).

- [ ] **Step 4: Report PR + next-step reminder**

After the PR opens, report the URL to the user and note:
- "Pushed with `update-baselines` label — CI will regenerate dark-mode baselines and commit them back to the PR."
- "`bd <BD_ID>` stays open until merge; close with `bd close <BD_ID>` after."

---

## Task 7: Review the regenerated baselines

This task is triggered after CI's baseline-regen workflow commits new images back to the PR. The user (or you, if asked) reviews the dark-mode diffs to confirm they look right — every dark fixture should now show the dark canvas behind the (previously transparent) components.

**Files:** none on the branch; the inspection happens in the PR's "Files changed" tab.

- [ ] **Step 1: Pull the baseline-regen commit locally**

```bash
git pull --ff-only
```
Expected: the latest commit on the branch is the auto-generated baseline-regen commit (commit author typically the release bot or a GitHub Actions bot).

- [ ] **Step 2: Spot-check a representative dark-mode baseline pair**

The screenshot test baselines live under `<module>/src/screenshotTestDebug/reference/` per module. Open one *dark* baseline that was previously known-broken (e.g. the Settings stub fixture that triggered this whole effort):

```
feature/profile/impl/src/screenshotTestDebug/reference/.../settings-signed-in-dark.png
```

Expected: the background renders dark, not white. Components no longer transparent over white canvas.

- [ ] **Step 3: Confirm no unintended light-mode regressions**

Light-mode baselines should be **byte-identical** to pre-PR (the wrapper added a `Surface` paint at `colorScheme.surface`, which in light mode is `#FDFCFF` — practically identical to the IDE's white canvas). If light-mode baselines diff, **investigate** — there's a subtle paint difference worth understanding before approving.

```bash
git diff main...HEAD -- '*/src/screenshotTestDebug/reference/**/*-light.png' | head -20
```
Expected: minimal or no diff. (Git won't show pixel diffs in CLI, but the file list helps spot the scope.)

- [ ] **Step 4: Report back to the user**

Summarize: how many fixtures regenerated, whether dark canvases look right, any light-mode surprises.

---

## Out of scope for this plan

- **Workstream 3 (a–g)** — call-site migration in production code (Scaffold `containerColor`, PostCard-as-card wrap, embed-token shifts). Seven separate PRs; plans to be written per surface as they come up.
- **Workstream 4** — custom detekt rule in `build-logic/detekt-rules/`. Plan to be written last, after all call sites conform.
- Updating production composables to use `NubecitaCanvasPreviewTheme`. That would be wrong — production owns its canvas paint via Scaffold/modal Surface.

## Risks specific to this plan

- **A `*ScreenshotTest.kt` file accidentally uses `NubecitaTheme(dynamicColor = true)` or no args at all** — the sed in step 3 only matches the exact `(dynamicColor = false)` form. The sanity-check grep in Task 4 Step 1 surfaces this before edits; the post-edit grep in Step 4 surfaces any remaining call sites. Mitigation: read the grep output before sed, and after.
- **Spotless reformatting changes more than the migration** — if any of the 21 migrated files had latent spotless issues, `spotlessApply` would fold those in too. Mitigation: run `spotlessKotlinCheck` (not `spotlessApply`) first; if it fails, inspect the failure and decide whether to fix in this PR or a precursor. If you `spotlessApply`, eyeball the diff to confirm only intended changes.
- **CI baseline-regen workflow doesn't exist or has been renamed** — the `update-baselines` label may be wired up to a different workflow than the memory recalls. Mitigation: verify the workflow runs after the label is added by watching the PR's "Checks" tab once (or asking the user). If nothing fires, capture baselines locally per module (`./gradlew :<module>:updateDebugScreenshotTest`) and commit them.
- **macOS vs Linux `sed` `-i` flag** — `sed -i ''` is BSD/macOS; GNU sed wants `sed -i ''` to be `sed -i ""` or just `sed -i`. The plan assumes macOS (the project is darwin-only for development). If running on Linux, adjust to `sed -i 's|...|...|g'` without the `''` argument.
