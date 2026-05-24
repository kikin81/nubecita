# Color Token Cascade — Workstream 3a (PostCard + Feed) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn feed posts into discrete `Surface(surfaceContainer)` cards sitting on the screen-canvas `surface`. Shift recessed-inset embed tokens to match the spec. Make `FeedScreen`'s `Scaffold` explicit about its canvas paint. This is the first workstream that changes pixels users will see — the spec's "expressive M3" intent finally lands.

**Architecture:**

- **PostCard stays a stateless leaf component** — no `wrapInCard` parameter. The decision to paint a card wraps at the caller, where the canvas owner lives (the feed for a `Single` item, the thread-group wrapper for a chain or cluster).
- **`Single` posts wrap individually** in `Surface(surfaceContainer, shape = nubecitaShapes.medium)` at the feed call site. Thread chains and reply clusters wrap their **whole group** in one outer Surface so the connector lines stay visually continuous (per the design decision: "a thread IS one card").
- **HorizontalDivider drops everywhere** in the PostCard family. With cards-on-canvas + inter-card gap, the divider is redundant noise.
- **Token shifts** per the spec contract: any "recessed inset" embed inside a post body moves to `surfaceContainerLow`.

**Tech Stack:** Jetpack Compose Material 3, M3 surface tokens, AGP screenshot testing.

**Spec:** `docs/superpowers/specs/2026-05-23-color-token-cascade-design.md` (§"Architecture / The call-site migration" — PostCard family + Scaffold containerColor)

**Reference page:** `docs/design-system/surface-roles.md`

**bd:** Create a new bd issue `nubecita-XXXX` of type `feature`, title "Wrap feed posts as surfaceContainer cards + shift embed tokens (workstream 3a)". Branch off `main` as `feat/nubecita-XXXX-<slug>` via the `bd-workflow` skill's start flow.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `designsystem/src/main/kotlin/.../component/PostCard.kt` | Modify | Drop the inter-post `HorizontalDivider` at line 177 (no longer needed; gap replaces it). No Surface wrap inside PostCard — callers own that. |
| `designsystem/src/main/kotlin/.../component/PostCardShimmer.kt` | Modify | Drop the `HorizontalDivider` at line 49 (same reason). |
| `designsystem/src/main/kotlin/.../component/PostCardExternalEmbed.kt` | Modify | `surfaceContainer` → `surfaceContainerLow` (line 67) — recessed inset role. |
| `designsystem/src/main/kotlin/.../component/PostCardRecordUnavailable.kt` | Modify | `surfaceContainerHighest` → `surfaceContainerLow` (line 50) — recessed inset role. |
| `designsystem/src/main/kotlin/.../component/PostCardUnsupportedEmbed.kt` | Modify | `surfaceContainerHighest` → `surfaceContainerLow` (line 41) — recessed inset role. |
| `designsystem/src/main/kotlin/.../component/ThreadCluster.kt` | Modify | Wrap the outer `Column` (line 81) in `Surface(color = surfaceContainer, shape = nubecitaShapes.medium)`. The 3 inner PostCards stay un-wrapped — group is one card. |
| `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt` | Modify | (1) Add `containerColor = colorScheme.surface` to the Scaffold (~line 454). (2) Add `verticalArrangement = Arrangement.spacedBy(8.dp)` to BOTH `LazyColumn`s (loading list at ~line 513 and feed list at ~line 577). (3) Wrap `Single` PostCard call (~line 722) in `Surface(surfaceContainer, shape = nubecitaShapes.medium)`. (4) Wrap `SelfThreadChain`'s inner `Column` (~line 768) in the same Surface. (5) Wrap each `PostCardShimmer` (~line 518) in the same Surface. |
| `designsystem/src/screenshotTestDebug/reference/.../NubecitaScreenPreviewThemeScreenshotTestKt/` | Delete (`rm -rf`) | Bundled cleanup of the orphan baselines left by the wrapper rename in PR #290 — see [[workstream-3a-pending-baseline-cleanup]] memory. |

**Out of scope** (not touched in this PR):

- **PostDetailScreen / PostCard at the detail surface** — workstream 3b. PostDetail's PostCard call should NOT wrap in Surface (the post IS the screen's primary content); since PostCard no longer auto-wraps, this is automatically correct after 3a.
- **Profile feed / search posts** — those screens use PostCard too; their wrapping decision belongs in their respective workstreams (3f profile, 3e search).
- **BlockedPostCard / NotFoundPostCard** — already at `surfaceContainer` internally; no change. Feed's `Blocked` / `NotFound` branches render them without an outer wrap (their internal background is the card).
- Other Scaffold call sites — workstreams 3b–g.

---

## Preconditions

1. Workstream 2 has landed: `git fetch origin main && git show origin/main:designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/NubecitaCanvasPreviewTheme.kt > /dev/null` exits 0.
2. Working tree is clean.
3. Current branch is `main` and up to date (`git pull --ff-only`).
4. Memory `project_workstream_3a_pending_baseline_cleanup` reviewed (or its content is now incorporated in this plan's Task 7).

---

## Task 1: Create the bd issue and branch

- [ ] **Step 1: Create the bd issue**

```bash
cat > /tmp/bd-3a-desc.md <<'EOF'
Materializes workstream 3a of the color-token-cascade spec (nubecita-kkla).

The first PR that changes user-visible pixels:

- Feed posts wrap individually in Surface(surfaceContainer) cards.
- Thread chains and reply clusters wrap their whole group in ONE Surface
  so the avatar-gutter connector lines stay visually continuous.
- HorizontalDivider drops from PostCard + PostCardShimmer.
- LazyColumn gets Arrangement.spacedBy(8.dp) for the inter-card gap.
- FeedScreen Scaffold sets containerColor = colorScheme.surface explicitly.
- PostCardExternalEmbed, PostCardRecordUnavailable, PostCardUnsupportedEmbed
  all shift to surfaceContainerLow (recessed inset role).
- PostCardQuotedPost already at surfaceContainerLow — no change.

Spec: docs/superpowers/specs/2026-05-23-color-token-cascade-design.md
Plan: docs/superpowers/plans/2026-05-24-color-token-cascade-workstream-3a-postcard-feed.md
Reference: docs/design-system/surface-roles.md

Bundled cleanup: delete the orphan NubecitaScreenPreviewThemeScreenshotTestKt
baseline directory left by the wrapper rename in PR #290.

PR carries the update-baselines label — large baseline diff expected
across feed + designsystem PostCard family screenshot tests.
EOF
bd create "Wrap feed posts as surfaceContainer cards + shift embed tokens (workstream 3a)" \
  --type feature --priority 2 \
  --body-file /tmp/bd-3a-desc.md \
  --json | jq -r '.id'
```
Expected: prints a new bd id like `nubecita-XXXX`. Record as `BD_ID`.

- [ ] **Step 2: Branch + claim**

Invoke `bd-workflow` skill: "start `<BD_ID>`". Verifies tree-clean + on main; creates the branch; runs `bd update <BD_ID> --claim`.

---

## Task 2: Drop HorizontalDivider from PostCard

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt`

- [ ] **Step 1: Verify line anchor**

```bash
grep -n "HorizontalDivider" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt
```
Expected: one match at line 177. If shifted, adjust the Edit anchor.

- [ ] **Step 2: Remove the divider + its comment + the `import HorizontalDivider`**

Use Edit on the file.

`old_string` (the divider block, including its preceding KDoc comment):
```kotlin
        // Suppress the divider when this PostCard sits inside a thread cluster
        // and is followed by another cluster post (i.e., connectBelow=true).
        // The thread connector line crossing through a horizontal divider
        // creates visual noise; the cluster's bottom card (leaf, with
        // connectBelow=false) keeps its divider so the cluster boundary is
        // still visible against the next feed item.
        if (!connectBelow) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
```

`new_string`:
```kotlin
    }
}
```

Then remove the now-unused `import androidx.compose.material3.HorizontalDivider` (verify it's no longer referenced anywhere else in the file first: `grep -n "HorizontalDivider" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt` should print only the import line).

- [ ] **Step 3: Verify compile**

```bash
./gradlew :designsystem:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. If unused-import lint fires, drop the import per step 2.

---

## Task 3: Drop HorizontalDivider from PostCardShimmer

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardShimmer.kt`

- [ ] **Step 1: Verify line anchor**

```bash
grep -n "HorizontalDivider" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardShimmer.kt
```
Expected: one match at line 49 (plus the import on line 15).

- [ ] **Step 2: Read 5 lines of context around line 49** to confirm the surrounding code, then use Edit to remove the `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)` call.

- [ ] **Step 3: Drop the unused import**

```bash
grep -n "HorizontalDivider" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardShimmer.kt
```
Expected: now only the import line (line 15). Remove it.

- [ ] **Step 4: Verify compile**

```bash
./gradlew :designsystem:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 4: Token swaps in three embed composables

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbed.kt`
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardRecordUnavailable.kt`
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardUnsupportedEmbed.kt`

- [ ] **Step 1: PostCardExternalEmbed — verify anchor + swap**

```bash
grep -n "surfaceContainer\b" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbed.kt
```
Expected: line 67 — `color = MaterialTheme.colorScheme.surfaceContainer,`. Edit `surfaceContainer` → `surfaceContainerLow`.

- [ ] **Step 2: PostCardRecordUnavailable — verify anchor + swap**

```bash
grep -n "surfaceContainerHighest" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardRecordUnavailable.kt
```
Expected: line 50 — `.background(MaterialTheme.colorScheme.surfaceContainerHighest)`. Edit `surfaceContainerHighest` → `surfaceContainerLow`.

- [ ] **Step 3: PostCardUnsupportedEmbed — verify anchor + swap**

```bash
grep -n "surfaceContainerHighest" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardUnsupportedEmbed.kt
```
Expected: line 41 — `.background(MaterialTheme.colorScheme.surfaceContainerHighest)`. Edit `surfaceContainerHighest` → `surfaceContainerLow`.

- [ ] **Step 4: Verify compile**

```bash
./gradlew :designsystem:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 5: Wrap ThreadCluster's inner group in Surface

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/ThreadCluster.kt`

The 3-post cluster (root → optional fold → parent → optional fold → leaf) is one logical thread. Wrapping the outer `Column` in a single Surface gives the whole group a card boundary while the inner PostCards stack tight so connector lines stay continuous.

- [ ] **Step 1: Read the cluster's outer Column block** (around line 81) to confirm structure.

```bash
sed -n '80,110p' designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/ThreadCluster.kt
```

- [ ] **Step 2: Wrap the outer Column in Surface**

Use Edit. Expected before:
```kotlin
    Column(modifier = modifier) {
        PostCard(
            // root ...
```

Expected after:
```kotlin
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            PostCard(
                // root ...
```

(The closing `}` of the original Column becomes the closing `}` of the inner Column; the new Surface's closing `}` lives just before the function's outer closing brace.)

Add imports: `androidx.compose.material3.Surface`, `androidx.compose.foundation.layout.fillMaxWidth` if not already present.

- [ ] **Step 3: Verify compile**

```bash
./gradlew :designsystem:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 6: Update FeedScreen — Scaffold containerColor + LazyColumn arrangement + per-branch card wraps

**Files:**
- Modify: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt`

- [ ] **Step 1: Add `containerColor` to the Scaffold**

```bash
grep -n "Scaffold(" feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt
```
Expected: one match around line 454. Edit to add `containerColor = MaterialTheme.colorScheme.surface,` as a Scaffold parameter (alphabetically after `bottomBar`/`floatingActionButton` etc., per the M3 Scaffold signature).

- [ ] **Step 2: Add `Arrangement.spacedBy(8.dp)` to BOTH LazyColumns**

Loading-state LazyColumn (~line 513) and feed LazyColumn (~line 577). Each gets `verticalArrangement = Arrangement.spacedBy(8.dp),` added near `contentPadding = ...`.

Add imports: `androidx.compose.foundation.layout.Arrangement` if not already present.

- [ ] **Step 3: Wrap `Single` branch's PostCard call in Surface**

Read lines 718–731 to confirm the `Single` branch shape, then Edit. Expected before:
```kotlin
                    is FeedItemUi.Single ->
                        PostCard(
                            post = item.post,
                            callbacks = callbacks,
                            ...
                        )
```

Expected after:
```kotlin
                    is FeedItemUi.Single ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PostCard(
                                post = item.post,
                                callbacks = callbacks,
                                ...
                            )
                        }
```

Add import `androidx.compose.material3.Surface` if not already present.

- [ ] **Step 4: Wrap `SelfThreadChain` branch's inner Column in Surface**

Read lines 767–784 to confirm. Expected before:
```kotlin
                    is FeedItemUi.SelfThreadChain -> {
                        // ... comments ...
                        val chainLastIndex = item.posts.lastIndex
                        Column {
                            item.posts.forEachIndexed { index, chainPost ->
                                ...
                            }
                        }
                    }
```

Expected after:
```kotlin
                    is FeedItemUi.SelfThreadChain -> {
                        // ... comments ...
                        val chainLastIndex = item.posts.lastIndex
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                item.posts.forEachIndexed { index, chainPost ->
                                    ...
                                }
                            }
                        }
                    }
```

- [ ] **Step 5: Wrap each loading-state PostCardShimmer in Surface**

Read line 513–522 area (the loading LazyColumn's `items`). Expected before:
```kotlin
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    // (Arrangement.spacedBy already added in step 2)
                ) {
                    items(SHIMMER_COUNT) { index ->
                        PostCardShimmer(showImagePlaceholder = index % 2 == 0)
                    }
                }
```

Expected after:
```kotlin
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(SHIMMER_COUNT) { index ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PostCardShimmer(showImagePlaceholder = index % 2 == 0)
                        }
                    }
                }
```

- [ ] **Step 6: Verify compile**

```bash
./gradlew :feature:feed:impl:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

## Task 7: Delete orphan baseline directory from PR #290

Per the [[workstream-3a-pending-baseline-cleanup]] memory.

- [ ] **Step 1: Verify the directory exists + contents**

```bash
ls designsystem/src/screenshotTestDebug/reference/net/kikin/nubecita/designsystem/preview/NubecitaScreenPreviewThemeScreenshotTestKt/
```
Expected: 2 PNG files with `screen-preview-theme-{light,dark}` in their names.

- [ ] **Step 2: Confirm no test references the directory name**

```bash
grep -rn "NubecitaScreenPreviewThemeScreenshotTestKt" --include="*.kt" . 2>/dev/null | grep -v "/build/"
```
Expected: no output (no test class is named this anymore — the rename in PR #290 left only orphan baselines).

- [ ] **Step 3: Delete the orphan directory**

```bash
rm -rf designsystem/src/screenshotTestDebug/reference/net/kikin/nubecita/designsystem/preview/NubecitaScreenPreviewThemeScreenshotTestKt
```

- [ ] **Step 4: Verify**

```bash
find designsystem/src/screenshotTestDebug/reference -name "*ScreenPreviewTheme*" -type f
```
Expected: no output. The `NubecitaCanvasPreviewTheme*` baselines (current source of truth) remain in the sibling `NubecitaCanvasPreviewThemeScreenshotTestKt/` directory.

---

## Task 8: Full local verification

**Files:** none.

- [ ] **Step 1: Spotless across everything touched**

```bash
./gradlew :designsystem:spotlessKotlinCheck :feature:feed:impl:spotlessKotlinCheck
```
Expected: `BUILD SUCCESSFUL`. If formatting fails, run `:designsystem:spotlessApply` + `:feature:feed:impl:spotlessApply` then re-check.

- [ ] **Step 2: Lint the two modules**

```bash
./gradlew :designsystem:lintDebug :feature:feed:impl:lintDebug
```
Expected: `BUILD SUCCESSFUL`. If new lint errors fire (esp. Compose `ComposeModifierMissing` on any new helper), investigate.

- [ ] **Step 3: App graph still links**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Unit tests pass**

```bash
./gradlew :designsystem:testDebugUnitTest :feature:feed:impl:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`. Any test that asserts on the now-removed `HorizontalDivider` will fail here — update assertions to match the new card structure.

- [ ] **Step 5: Pre-commit gate**

```bash
pre-commit run --all-files
```
Expected: all hooks pass.

---

## Task 9: Commit, push, and open PR

**Files:** none.

- [ ] **Step 1: Stage in four logical commits** (3 work commits + 1 cleanup)

```bash
git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt \
        designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardShimmer.kt
git commit -m "$(cat <<'COMMIT'
refactor(designsystem): drop HorizontalDivider from PostCard family

With workstream 3a wrapping feed posts in Surface(surfaceContainer)
cards separated by Arrangement.spacedBy(8.dp), the inter-post
HorizontalDivider is redundant. Drop from both PostCard and
PostCardShimmer (the loading-state mirror).

Refs: <BD_ID>
COMMIT
)"

git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardExternalEmbed.kt \
        designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardRecordUnavailable.kt \
        designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCardUnsupportedEmbed.kt
git commit -m "$(cat <<'COMMIT'
refactor(designsystem): shift post embeds to surfaceContainerLow (recessed inset role)

Per the color-token-cascade contract, anything nested directly inside
an item card uses the recessed-inset role (surfaceContainerLow), with
strong-fill placeholders the only exception. Three of the embed
composables were on tokens that don't fit:

- PostCardExternalEmbed: surfaceContainer → surfaceContainerLow
  (was item-card role; correct role inside a post is recessed inset)
- PostCardRecordUnavailable: surfaceContainerHighest → surfaceContainerLow
  (was strong-fill role; correct role is recessed inset)
- PostCardUnsupportedEmbed: surfaceContainerHighest → surfaceContainerLow
  (same)

PostCardQuotedPost already at surfaceContainerLow — no change.

Refs: <BD_ID>
COMMIT
)"

git add designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/ThreadCluster.kt \
        feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/FeedScreen.kt
git commit -m "$(cat <<'COMMIT'
feat(feed): wrap posts in Surface(surfaceContainer) cards

The visible shape of the M3 surface-token cascade contract lands here:
feed posts now render as discrete Surface(surfaceContainer, shape =
shapes.medium) cards on the screen's surface canvas, separated by
LazyColumn Arrangement.spacedBy(8.dp).

Thread chains and reply clusters wrap their WHOLE GROUP in a single
outer Surface so the avatar-gutter connector lines stay visually
continuous — the decision rule from the brainstorm: "a thread IS one
card." Single posts wrap individually; the chain/cluster composables
stack their inner PostCards inside one outer Surface.

FeedScreen.Scaffold now sets containerColor = colorScheme.surface
explicitly (no longer relying on M3's silent default of background).

Loading-state PostCardShimmers also wrap in the same Surface so the
loading list previews the final card geometry.

Refs: <BD_ID>
COMMIT
)"

# Cleanup commit (separate so the PR's logical history is reviewable)
git add -A
git commit -m "$(cat <<'COMMIT'
chore(designsystem): delete orphan NubecitaScreenPreviewTheme baselines

Bundled cleanup from PR #290's rename. Removes 2 stale baseline PNGs
under NubecitaScreenPreviewThemeScreenshotTestKt/ that no fixture
references after the rename (NubecitaCanvasPreviewThemeScreenshotTestKt/
holds the current source of truth).

Refs: <BD_ID>
COMMIT
)"
```

- [ ] **Step 2: Use the bd-workflow finish flow**

Invoke `bd-workflow` skill: "open the PR". The skill will run preflight (`:app:assembleDebug` already passed in Task 8, but the skill may re-run), push, `gh pr create`, request Copilot reviewer. **Do NOT spin up the CI cron monitor** per `feedback_ci_monitor_only_when_afk`.

- [ ] **Step 3: Add `update-baselines` label**

```bash
gh pr edit <PR_NUM> --add-label update-baselines
```

Expect a big baseline regen — every `*ScreenshotTest.kt` that renders a PostCard, PostCardExternalEmbed, PostCardRecordUnavailable, PostCardUnsupportedEmbed, ThreadCluster, or FeedScreen diffs.

- [ ] **Step 4: Don't push anything else while the baseline workflow is running.** Per the lesson from PR #290: the workflow's `git push` doesn't pull-rebase first, so a parallel push triggers `! [rejected]` and the workflow fails. Wait for the regen commit to land before any follow-up push.

---

## Task 10: Review regenerated baselines

After CI commits the regen, spot-check.

- [ ] **Step 1: Pull**

```bash
git pull --ff-only
```

- [ ] **Step 2: Open the Feed dark-mode baseline + a single Settings dark baseline**

Single feed post should now show a clearly defined `surfaceContainer` card on the `surface` canvas with 8dp gap to its neighbors. Thread chains/clusters should show as ONE card. Embeds inside posts should be subtly recessed (surfaceContainerLow). No HorizontalDivider anywhere.

- [ ] **Step 3: Sanity-check no light-mode regressions**

Light mode's `surface` is `#FDFCFF`; `surfaceContainer` is `#F1EFF4`. The card boundary is subtler than dark mode but should still be visible.

---

## Out of scope for this plan

- **PostDetailScreen Scaffold containerColor + PostDetailPaneEmptyState token swap** — workstream 3b.
- **Composer / Chats / Search / Profile / Login / Onboarding** Scaffold containerColor — workstreams 3c–g.
- **Custom detekt rule** for reserved-token rejection — workstream 4.
- **PostCard `wrapInCard` parameter** — rejected during plan-writing. The wrapping decision lives at the caller (the canvas owner). PostCard stays a stateless leaf component.

## Risks specific to this plan

- **Thread connector geometry breaks across the new Surface wrap.** The `Modifier.threadConnector` draws Y-relative lines on the PostCard's own bounds. With chains wrapped in ONE outer Surface and inner PostCards stacked tight (no `spacedBy` inside), the line continuity should hold — but it's worth spot-checking the chain dark baseline carefully.
- **Big baseline diff (~100+ images).** Manageable per-fixture; reviewer needs to skim the dark-mode diffs to confirm intent. The big-diff PR is the entry cost of "expressive M3" — subsequent workstreams' diffs are smaller because they reuse the same role contract.
- **PostDetail's PostCard render starts looking different.** PostDetail uses PostCard but doesn't wrap it. After 3a, PostCard no longer self-wraps, so PostDetail's post will NOT show a Surface card — which is actually correct for PostDetail (the post IS the screen's primary content; it doesn't sit on a canvas as a "card"). Verify PostDetail screenshots regen sensibly; if they look wrong, escalate to 3b's plan rather than fixing here.
- **Mid-PR push race against `update-baselines` workflow.** Resolved by Task 9 Step 4: don't push during the regen window.
