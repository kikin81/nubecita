# AvatarGroup component + NubecitaAvatar fallback consolidation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a canonical `AvatarGroup` facepile to `:designsystem`, make `NubecitaAvatar` the single owner of the hue+initial avatar fallback, and consolidate the ~4 duplicated fallback call sites and the notifications `StackedAvatarRow` onto it.

**Architecture:** A pure `avatarHueFor` moves down to `:core:common` so `:designsystem` can own avatar rendering without depending on `:core:profile`. `NubecitaAvatar` gains an optional `AvatarFallback(hue, initial)`. `AvatarGroup` composes `NubecitaAvatar` in an overlapping `Row` with a `+N` overflow pill, driven by a pure `avatarGroupSplit` function. All existing single-avatar fallbacks and the notifications facepile route through these.

**Tech Stack:** Kotlin 2.3, Jetpack Compose + Material 3 Expressive, Coil 3, `kotlinx.collections.immutable`, JUnit 5, AGP screenshot tests (`com.android.compose.screenshot`).

**Spec:** `docs/superpowers/specs/2026-06-18-avatar-group-component-design.md` · **bd:** nubecita-364e · **branch:** `feat/nubecita-364e-avatar-group-component`

**Group-chat feature is OUT of scope** — this delivers the component + consolidation only.

---

## File Structure

**Created:**
- `core/common/src/main/kotlin/net/kikin/nubecita/core/common/avatar/AvatarHue.kt` — relocated pure `avatarHueFor`.
- `core/common/src/test/kotlin/net/kikin/nubecita/core/common/avatar/AvatarHueTest.kt` — relocated determinism test.
- `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroup.kt` — `AvatarGroup`, `AvatarGroupSplit`, `avatarGroupSplit`.
- `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroupSplitTest.kt` — overflow-split unit test.
- `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/AvatarFallbackTest.kt` — `avatarFallbackFor` unit test.
- `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroupScreenshotTest.kt`
- `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/NubecitaAvatarFallbackScreenshotTest.kt`

**Modified:**
- `designsystem/src/main/kotlin/.../component/NubecitaAvatar.kt` — add `AvatarFallback` + `fallback` param + `avatarFallbackFor`; promote `DEFAULT_AVATAR_SIZE` to `internal`.
- `core/profile/.../AvatarHue.kt` + its test — deleted (moved).
- ~14 importer files — re-point `import net.kikin.nubecita.core.profile.avatarHueFor` → `core.common.avatar.avatarHueFor`.
- `feature/notifications/impl/.../ui/StackedAvatarRow.kt` — deleted; callers use `AvatarGroup`.
- `feature/chats/impl/.../ui/ConvoListItem.kt`, `.../ChatScreenContent.kt` (ChatTopBarAvatar), `.../ui/RecipientRow.kt` — route through `NubecitaAvatar(fallback=…)`.
- `feature/settings/impl/.../ui/SettingsAvatar.kt` — route through `NubecitaAvatar(fallback=…)`.

**Deleted:** `core/profile/.../AvatarHue.kt`, `core/profile/.../AvatarHueTest.kt`, `feature/notifications/impl/.../ui/StackedAvatarRow.kt`.

---

## Task 1: Move `avatarHueFor` to `:core:common`

**Files:**
- Create: `core/common/src/main/kotlin/net/kikin/nubecita/core/common/avatar/AvatarHue.kt`
- Create: `core/common/src/test/kotlin/net/kikin/nubecita/core/common/avatar/AvatarHueTest.kt`
- Delete: `core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/AvatarHue.kt`
- Delete: `core/profile/src/test/kotlin/net/kikin/nubecita/core/profile/AvatarHueTest.kt`
- Modify (re-point imports): the importers listed in Step 4.

- [ ] **Step 1: Create the relocated function**

`core/common/src/main/kotlin/net/kikin/nubecita/core/common/avatar/AvatarHue.kt`:

```kotlin
package net.kikin.nubecita.core.common.avatar

/**
 * Deterministic hue in `0..359` derived from `did + first char of handle`.
 * Seeds the initials-disc avatar fallback so the same user paints identically
 * on every surface. `Math.floorMod` (not `abs % 360`) because
 * `abs(Int.MIN_VALUE) == Int.MIN_VALUE`; floorMod is non-negative for all input.
 *
 * Lives in `:core:common` so both feature modules and `:designsystem`
 * (which renders the fallback) can share it without a layering inversion.
 */
fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return Math.floorMod(seed.hashCode(), 360)
}
```

- [ ] **Step 2: Create the relocated test (copy the pinned values verbatim)**

First read the current test to copy its exact cases:

Run: `cat core/profile/src/test/kotlin/net/kikin/nubecita/core/profile/AvatarHueTest.kt`

Then create `core/common/src/test/kotlin/net/kikin/nubecita/core/common/avatar/AvatarHueTest.kt` with the identical test bodies but the new package (`net.kikin.nubecita.core.common.avatar`) and import. The pinned values MUST remain alice→47, bob→307, miku→279 (the move changes nothing computationally).

- [ ] **Step 3: Delete the old files**

```bash
git rm core/profile/src/main/kotlin/net/kikin/nubecita/core/profile/AvatarHue.kt \
       core/profile/src/test/kotlin/net/kikin/nubecita/core/profile/AvatarHueTest.kt
```

- [ ] **Step 4: Re-point every importer**

For each file below, change `import net.kikin.nubecita.core.profile.avatarHueFor` → `import net.kikin.nubecita.core.common.avatar.avatarHueFor`:

- `core/actors/src/bench/kotlin/net/kikin/nubecita/core/actors/internal/BenchFakeBlockRepository.kt`
- `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/DefaultBlockRepository.kt`
- `feature/settings/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/settings/impl/SettingsScreenScreenshotTest.kt`
- `feature/settings/impl/src/test/kotlin/net/kikin/nubecita/feature/settings/impl/SettingsViewModelTest.kt`
- `feature/settings/impl/src/main/kotlin/net/kikin/nubecita/feature/settings/impl/SettingsContract.kt`
- `feature/settings/impl/src/main/kotlin/net/kikin/nubecita/feature/settings/impl/SettingsViewModel.kt`
- `feature/settings/impl/src/main/kotlin/net/kikin/nubecita/feature/settings/impl/ui/SettingsAvatar.kt`
- `feature/profile/impl/src/bench/kotlin/net/kikin/nubecita/feature/profile/impl/data/BenchProfileMapper.kt`
- `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/data/AuthorProfileMapper.kt`
- `feature/chats/impl/src/bench/kotlin/net/kikin/nubecita/feature/chats/impl/data/BenchChatsMapper.kt`
- `feature/chats/impl/src/bench/kotlin/net/kikin/nubecita/feature/chats/impl/data/BenchFakeChatRepository.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/RecipientRow.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ConvoMapper.kt`
- `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt`

Sweep to confirm none remain:

Run: `grep -rln 'core.profile.avatarHueFor\|core\.profile\.AvatarHue' --include='*.kt' . | grep -v /build/`
Expected: no output.

- [ ] **Step 5: Verify `:core:profile` no longer needs to export it and modules still resolve**

Each importing module already depends on `:core:common` (verify quickly):

Run: `for m in core/actors feature/settings/impl feature/profile/impl feature/chats/impl; do echo "== $m"; grep -c 'core:common' $m/build.gradle.kts; done`
Expected: each prints `1` or more. If any prints `0`, add `implementation(project(":core:common"))` to that module's `build.gradle.kts` (sorted) — but all four are expected to already depend on it.

- [ ] **Step 6: Compile + run the relocated test**

Run: `./gradlew :core:common:testDebugUnitTest --tests "net.kikin.nubecita.core.common.avatar.AvatarHueTest"`
Expected: PASS (alice→47, bob→307, miku→279).

Run: `./gradlew :core:actors:test :feature:settings:impl:test :feature:profile:impl:test :feature:chats:impl:test`
Expected: BUILD SUCCESSFUL (all importers compile against the new location).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(core-common): move avatarHueFor from :core:profile

Pure did+handle→hue helper relocated to :core:common so :designsystem can
own the avatar fallback render without depending on :core:profile. No
behavior change; pinned hues unchanged.

Refs: nubecita-364e"
```

---

## Task 2: `NubecitaAvatar` fallback + `avatarFallbackFor`

**Files:**
- Modify: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/NubecitaAvatar.kt`
- Test: `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/AvatarFallbackTest.kt`
- Screenshot: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/NubecitaAvatarFallbackScreenshotTest.kt`

- [ ] **Step 1: Write the failing unit test for `avatarFallbackFor`**

`designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/AvatarFallbackTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import net.kikin.nubecita.core.common.avatar.avatarHueFor
import net.kikin.nubecita.data.models.AuthorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AvatarFallbackTest {
    private fun author(displayName: String, handle: String = "alice.bsky.social") =
        AuthorUi(did = "did:plc:alice", handle = handle, displayName = displayName, avatarUrl = null)

    @Test
    fun `initial is the first letter-or-digit of displayName, uppercased`() {
        assertEquals('A', avatarFallbackFor(author("alice")).initial)
        assertEquals('B', avatarFallbackFor(author("🎉 bob")).initial) // emoji-led → 'B'
    }

    @Test
    fun `blank displayName falls back to the handle`() {
        assertEquals('A', avatarFallbackFor(author(displayName = "   ", handle = "alice.test")).initial)
    }

    @Test
    fun `no letter-or-digit anywhere yields a null initial`() {
        assertNull(avatarFallbackFor(author(displayName = "###", handle = "!!!")).initial)
    }

    @Test
    fun `hue matches avatarHueFor for the did and handle`() {
        val a = author("alice")
        assertEquals(avatarHueFor(a.did, a.handle), avatarFallbackFor(a).hue)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "net.kikin.nubecita.designsystem.component.AvatarFallbackTest"`
Expected: FAIL — `avatarFallbackFor` / `AvatarFallback` unresolved.

- [ ] **Step 3: Implement `AvatarFallback`, `avatarFallbackFor`, the `fallback` param, and promote the size**

Replace the contents of `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/NubecitaAvatar.kt` with:

```kotlin
package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.avatar.avatarHueFor
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Identity fallback for an avatarless person: a deterministic hue disc with the
 * person's initial. Built by [avatarFallbackFor]; rendered by [NubecitaAvatar]
 * when [model] is null.
 */
@Immutable
data class AvatarFallback(
    val hue: Int,
    val initial: Char?,
)

/** Derive the [AvatarFallback] for [member] — hue from did+handle, initial from name. */
fun avatarFallbackFor(member: AuthorUi): AvatarFallback =
    AvatarFallback(
        hue = avatarHueFor(member.did, member.handle),
        initial =
            (member.displayName.takeIf { it.isNotBlank() } ?: member.handle)
                .firstOrNull { it.isLetterOrDigit() }
                ?.uppercaseChar(),
    )

/**
 * Circular avatar primitive. Renders [model] (anything Coil resolves) cropped to
 * a circle. When [model] is null and [fallback] is non-null, renders the hue
 * disc + initial instead of the flat placeholder — the single owner of the
 * avatarless fallback used across chats, profile, settings, and AvatarGroup.
 */
@Composable
fun NubecitaAvatar(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_AVATAR_SIZE,
    fallback: AvatarFallback? = null,
) {
    if (model == null && fallback != null) {
        val hueColor = Color.hsv(fallback.hue.toFloat(), saturation = 0.5f, value = 0.55f)
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(hueColor),
            contentAlignment = Alignment.Center,
        ) {
            fallback.initial?.let { initial ->
                Text(
                    text = initial.toString(),
                    color = if (hueColor.luminance() > 0.5f) Color.Black else Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    } else {
        NubecitaAsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

internal val DEFAULT_AVATAR_SIZE = 40.dp

@Preview(name = "Avatar — placeholder", showBackground = true)
@Composable
private fun NubecitaAvatarPreview() {
    NubecitaTheme {
        NubecitaAvatar(model = null, contentDescription = null)
    }
}

@Preview(name = "Avatar — fallback", showBackground = true)
@Composable
private fun NubecitaAvatarFallbackPreview() {
    NubecitaTheme {
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 47, initial = 'A'))
    }
}
```

Note: `contentDescription` on the fallback branch is intentionally not re-applied (callers/AvatarGroup own the merged semantics); the image branch keeps it.

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "net.kikin.nubecita.designsystem.component.AvatarFallbackTest"`
Expected: PASS.

- [ ] **Step 5: Add a screenshot fixture for the fallback render**

Open the sibling test to copy its exact annotation/import header:

Run: `cat designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/NubecitaAvatarScreenshotTest.kt`

Create `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/NubecitaAvatarFallbackScreenshotTest.kt` mirroring that file's wrapper annotations (`@PreviewWrapper(NubecitaComponentPreview::class)` + `@PreviewTest`), with this fixture body:

```kotlin
@Composable
private fun NubecitaAvatarFallbackFixture() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 47, initial = 'A'))
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 307, initial = 'B'))
        NubecitaAvatar(model = null, contentDescription = null, fallback = AvatarFallback(hue = 279, initial = null))
        NubecitaAvatar(model = null, contentDescription = null) // no fallback → flat placeholder
    }
}
```

- [ ] **Step 6: Generate + validate the baseline**

Run: `./gradlew :designsystem:updateDebugScreenshotTest`
Then: `./gradlew :designsystem:validateDebugScreenshotTest`
Expected: PASS; a new PNG baseline committed under `designsystem/src/debug/screenshotTest/reference/` (or the module's baseline dir — match where `NubecitaAvatarScreenshotTest` baselines live).

- [ ] **Step 7: Commit**

```bash
git add designsystem/
git commit -m "feat(designsystem): add hue+initial fallback to NubecitaAvatar

Introduce AvatarFallback + avatarFallbackFor; NubecitaAvatar renders the
hue disc + initial when model is null and a fallback is supplied. Promote
DEFAULT_AVATAR_SIZE to internal for reuse. Unit + screenshot tests.

Refs: nubecita-364e"
```

---

## Task 3: `AvatarGroup` component

**Files:**
- Create: `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroup.kt`
- Test: `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroupSplitTest.kt`
- Screenshot: `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroupScreenshotTest.kt`

- [ ] **Step 1: Write the failing overflow-split test**

`designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroupSplitTest.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AvatarGroupSplitTest {
    @Test
    fun `under the cap shows all avatars and no overflow`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 3, overflowCount = 0), avatarGroupSplit(total = 3, maxVisible = 4))
    }

    @Test
    fun `exactly at the cap shows all avatars and no overflow`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 4, overflowCount = 0), avatarGroupSplit(total = 4, maxVisible = 4))
    }

    @Test
    fun `over the cap shows maxVisible avatars plus the remainder as overflow`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 4, overflowCount = 2), avatarGroupSplit(total = 6, maxVisible = 4))
    }

    @Test
    fun `empty is empty`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 0, overflowCount = 0), avatarGroupSplit(total = 0, maxVisible = 4))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "net.kikin.nubecita.designsystem.component.AvatarGroupSplitTest"`
Expected: FAIL — `avatarGroupSplit` / `AvatarGroupSplit` unresolved.

- [ ] **Step 3: Implement `AvatarGroup` + the split function**

`designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroup.kt`:

```kotlin
package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.AuthorUi

/** Result of splitting [avatarGroupSplit]: how many avatars to draw and the "+N" remainder. */
@Immutable
data class AvatarGroupSplit(
    val visibleAvatars: Int,
    val overflowCount: Int,
)

/**
 * When `total > maxVisible`, show `maxVisible` avatars and append a `+(total-maxVisible)`
 * pill (the pill is an extra bubble; it does NOT replace an avatar). Otherwise show all.
 */
fun avatarGroupSplit(
    total: Int,
    maxVisible: Int,
): AvatarGroupSplit =
    if (total > maxVisible) {
        AvatarGroupSplit(visibleAvatars = maxVisible, overflowCount = total - maxVisible)
    } else {
        AvatarGroupSplit(visibleAvatars = total, overflowCount = 0)
    }

/**
 * Overlapping facepile of [members] — up to [maxVisible] avatars, then a "+N" pill.
 * Each bubble is photo-or-(hue+initial) via [NubecitaAvatar], ringed in
 * `colorScheme.surface` so circles stay distinct, with the leftmost on top.
 *
 * [contentDescription] is caller-supplied (the design system doesn't own the
 * domain plural strings) and applied to the whole row via merged semantics.
 */
@Composable
fun AvatarGroup(
    members: ImmutableList<AuthorUi>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    avatarSize: Dp = DEFAULT_AVATAR_SIZE,
) {
    val split = avatarGroupSplit(total = members.size, maxVisible = maxVisible)
    val ringColor = MaterialTheme.colorScheme.surface
    val overlap = avatarSize * 0.30f
    val rowModifier =
        if (contentDescription != null) {
            modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        } else {
            modifier
        }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(-overlap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        members.take(split.visibleAvatars).forEachIndexed { index, member ->
            NubecitaAvatar(
                model = member.avatarUrl,
                contentDescription = null,
                size = avatarSize,
                fallback = avatarFallbackFor(member),
                // leftmost on top: higher zIndex for earlier items
                modifier =
                    Modifier
                        .zIndex((split.visibleAvatars - index).toFloat())
                        .border(width = 1.5.dp, color = ringColor, shape = CircleShape),
            )
        }
        if (split.overflowCount > 0) {
            Box(
                modifier =
                    Modifier
                        .zIndex(0f)
                        .size(avatarSize)
                        .clip(CircleShape)
                        .border(width = 1.5.dp, color = ringColor, shape = CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${split.overflowCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run the unit test to verify it passes**

Run: `./gradlew :designsystem:testDebugUnitTest --tests "net.kikin.nubecita.designsystem.component.AvatarGroupSplitTest"`
Expected: PASS.

- [ ] **Step 5: Add screenshot fixtures**

Create `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/AvatarGroupScreenshotTest.kt`, mirroring `NubecitaAvatarScreenshotTest`'s wrapper annotations (`@PreviewWrapper(NubecitaComponentPreview::class)` + `@PreviewTest`). Fixture bodies (build `AuthorUi`s inline; mix photos and avatarless):

```kotlin
private fun member(n: Int, withPhoto: Boolean) =
    AuthorUi(
        did = "did:plc:m$n",
        handle = "user$n.bsky.social",
        displayName = "User $n",
        avatarUrl = if (withPhoto) "https://example.test/$n.jpg" else null,
    )

@Composable
private fun AvatarGroupFixtures() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarGroup(members = persistentListOf(member(1, false)), contentDescription = null)          // single
        AvatarGroup(members = (1..3).map { member(it, false) }.toPersistentList(), contentDescription = null) // no pill
        AvatarGroup(members = (1..9).map { member(it, false) }.toPersistentList(), contentDescription = null) // overflow +5
        AvatarGroup(
            members = persistentListOf(member(1, true), member(2, false), member(3, true), member(4, false), member(5, false)),
            contentDescription = null,
        ) // mixed photo/fallback + overflow +1
    }
}
```

(Photo URLs render the Coil placeholder in the screenshot host — that's expected and stable. Imports: `kotlinx.collections.immutable.persistentListOf` / `toPersistentList`, `androidx.compose.foundation.layout.Column`.)

- [ ] **Step 6: Generate + validate the baseline**

Run: `./gradlew :designsystem:updateDebugScreenshotTest && ./gradlew :designsystem:validateDebugScreenshotTest`
Expected: PASS; new `AvatarGroup` baselines committed.

- [ ] **Step 7: Commit**

```bash
git add designsystem/
git commit -m "feat(designsystem): add AvatarGroup facepile component

Overlapping avatars (up to maxVisible=4) + a '+N' overflow pill, leftmost
on top, surface-ringed, photo-or-(hue+initial) per bubble. Pure
avatarGroupSplit unit-tested; screenshot fixtures for single/no-pill/
overflow/mixed.

Refs: nubecita-364e"
```

---

## Task 4: Migrate notifications `StackedAvatarRow` → `AvatarGroup`

**Files:**
- Delete: `feature/notifications/impl/src/main/kotlin/net/kikin/nubecita/feature/notifications/impl/ui/StackedAvatarRow.kt`
- Modify: the caller(s) of `StackedAvatarRow` (find in Step 1).
- Rebaseline: notifications screenshot tests.

- [ ] **Step 1: Find the call sites + the content-description string**

Run: `grep -rn 'StackedAvatarRow' feature/notifications/impl/src/main --include='*.kt'`
Run: `grep -rn 'avatar_stack_description\|avatar_overflow' feature/notifications/impl/src/main/res`

Note the caller composable(s) and the existing plural string resource id used for the row's content description.

- [ ] **Step 2: Replace each call site with `AvatarGroup`**

At each caller, replace `StackedAvatarRow(actors = <list>, ...)` with:

```kotlin
AvatarGroup(
    members = <list>,                 // the same ImmutableList<AuthorUi>
    contentDescription =
        pluralStringResource(
            R.plurals.notifications_avatar_stack_description, // the id from Step 1
            <list>.size,
            <list>.size,
        ),
    maxVisible = 4,
    avatarSize = <the size the caller passed, e.g. 24.dp or 32.dp>,
)
```

Add imports: `net.kikin.nubecita.designsystem.component.AvatarGroup`, `androidx.compose.ui.res.pluralStringResource`. Remove the `StackedAvatarRow` import.

- [ ] **Step 3: Delete `StackedAvatarRow` and its now-unused strings if no longer referenced**

```bash
git rm feature/notifications/impl/src/main/kotlin/net/kikin/nubecita/feature/notifications/impl/ui/StackedAvatarRow.kt
```

Run: `grep -rn 'notifications_avatar_overflow' feature/notifications/impl/src --include='*.kt'`
If the `+N` overflow string is now unreferenced (AvatarGroup renders its own `"+N"`), remove that one string resource. Keep `notifications_avatar_stack_description` (still used in Step 2).

- [ ] **Step 4: Compile + unit tests**

Run: `./gradlew :feature:notifications:impl:assembleDebug :feature:notifications:impl:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Rebaseline notifications screenshots**

Run: `./gradlew :feature:notifications:impl:updateDebugScreenshotTest && ./gradlew :feature:notifications:impl:validateDebugScreenshotTest`
Expected: PASS. The aggregated-notification facepile rows change from the old 3-avatar+pill look to the new 4-avatar+pill look.

> If these baselines are CI-rendered (differ from local), instead push and add the `update-baselines` PR label so CI regenerates them (see the repo's screenshot-rebaseline note), and skip local validate.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(notifications): use designsystem AvatarGroup, drop StackedAvatarRow

Replace the feature-local facepile with the canonical AvatarGroup. Rebaseline
the aggregated-notification screenshots (now 4 avatars + pill).

Refs: nubecita-364e"
```

---

## Task 5: Migrate chat single-avatar fallbacks (`ChatTopBarAvatar`, `ConvoListItem`)

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContent.kt` (`ChatTopBarAvatar`)
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/ConvoListItem.kt`
- Rebaseline: chat screenshot tests.

- [ ] **Step 1: Read both current fallback blocks**

Run: `grep -n 'hsv\|avatarHueFor\|isLetterOrDigit\|NubecitaAsyncImage\|NubecitaAvatar\|fun ChatTopBarAvatar\|luminance' feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContent.kt feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/ConvoListItem.kt`

Both compute `hue = avatarHueFor(...)`, an `initial` via `firstOrNull { it.isLetterOrDigit() }`, and branch on `avatarUrl != null` between `NubecitaAsyncImage` and a hued `Box` + `Text`.

- [ ] **Step 2: Replace each inline fallback with `NubecitaAvatar(fallback = …)`**

In `ConvoListItem.kt`, replace the avatar `Box`/branch with (using the row's existing 1:1 fields — `did`, `otherUserHandle`, `displayName`, `avatarUrl`):

```kotlin
NubecitaAvatar(
    model = item.avatarUrl,
    contentDescription = null,
    size = 32.dp, // keep the size the row currently uses — confirm from Step 1
    fallback =
        AvatarFallback(
            hue = avatarHueFor(did = item.otherUserDid, handle = item.otherUserHandle),
            initial =
                (item.displayName ?: item.otherUserHandle)
                    .firstOrNull { it.isLetterOrDigit() }
                    ?.uppercaseChar(),
        ),
)
```

In `ChatScreenContent.kt`'s `ChatTopBarAvatar`, replace its body the same way using the screen-state fields (`state.otherUserAvatarUrl`, `state.otherUserAvatarHue` if already computed, else `avatarHueFor(state.otherUserDid, state.otherUserHandle)`, `state.otherUserDisplayName`, `state.otherUserHandle`), keeping its current size.

Add imports: `net.kikin.nubecita.designsystem.component.NubecitaAvatar`, `net.kikin.nubecita.designsystem.component.AvatarFallback`. Remove now-unused imports (`Color`, `luminance`, `NubecitaAsyncImage`, `background`, etc.) that the deleted inline block used — let the compiler's unused-import lint guide removal.

- [ ] **Step 3: Delete the now-dead private fallback composable(s)** if `ChatTopBarAvatar` was a wrapper whose only job was the branch (inline it into the `TopAppBar` title if so).

- [ ] **Step 4: Compile + unit tests**

Run: `./gradlew :feature:chats:impl:assembleDebug :feature:chats:impl:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Rebaseline chat screenshots**

Run: `./gradlew :feature:chats:impl:updateDebugScreenshotTest && ./gradlew :feature:chats:impl:validateDebugScreenshotTest`
Expected: PASS. Pixels should be identical (same hue+initial math) — if a baseline changes it's only because the render path moved; accept it.

> Same CI-baseline caveat as Task 4 Step 5 applies.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(chats): route avatar fallbacks through NubecitaAvatar

ConvoListItem + ChatTopBarAvatar now use NubecitaAvatar(fallback=…) instead
of an inline hue+initial branch.

Refs: nubecita-364e"
```

---

## Task 6: Migrate `RecipientRow` + `SettingsAvatar` fallbacks

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/RecipientRow.kt`
- Modify: `feature/settings/impl/src/main/kotlin/net/kikin/nubecita/feature/settings/impl/ui/SettingsAvatar.kt`
- Rebaseline: profile/settings screenshot tests (and any chats recipient screenshots).

- [ ] **Step 1: Replace `RecipientAvatar`'s body**

In `RecipientRow.kt`, replace the `RecipientAvatar` composable body (the `hue`/`hueColor`/`initial`/`Box`+branch shown in the spec exploration) with:

```kotlin
@Composable
private fun RecipientAvatar(
    actor: ActorUi,
    modifier: Modifier = Modifier,
) {
    NubecitaAvatar(
        model = actor.avatarUrl,
        contentDescription = null,
        modifier = modifier,
        fallback =
            AvatarFallback(
                hue = avatarHueFor(did = actor.did, handle = actor.handle),
                initial =
                    (actor.displayName ?: actor.handle)
                        .firstOrNull { it.isLetterOrDigit() }
                        ?.uppercaseChar(),
            ),
    )
}
```

Keep the `size` the row used (pass it via `size =` if `RecipientAvatar` took one). Add `NubecitaAvatar` + `AvatarFallback` imports; drop the now-unused `Color`/`luminance`/`background`/`Text`/`NubecitaAsyncImage`/`FontWeight` imports.

- [ ] **Step 2: Replace `SettingsAvatar`'s fallback the same way**

In `SettingsAvatar.kt`, do the equivalent substitution, keeping its larger size (88.dp per the spec exploration) via `size = 88.dp`. Use its actor fields for `did`/`handle`/`displayName`/`avatarUrl`.

- [ ] **Step 3: Compile + unit tests**

Run: `./gradlew :feature:chats:impl:assembleDebug :feature:settings:impl:assembleDebug :feature:settings:impl:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Rebaseline affected screenshots**

Run: `./gradlew :feature:settings:impl:updateDebugScreenshotTest && ./gradlew :feature:settings:impl:validateDebugScreenshotTest`
Expected: PASS.

> CI-baseline caveat as Task 4 Step 5.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(chats,settings): route RecipientRow + SettingsAvatar through NubecitaAvatar fallback

Removes the last copies of the inline hue+initial fallback; NubecitaAvatar
is now the single owner.

Refs: nubecita-364e"
```

---

## Task 7: Final verification + dedup sweep

**Files:** none (verification only).

- [ ] **Step 1: Confirm zero remaining inline fallback duplication**

Run: `grep -rn 'Color.hsv' --include='*.kt' feature core | grep -v /build/`
Expected: matches only inside `designsystem/.../NubecitaAvatar.kt` (the single owner). Any other hit is a missed migration — fix it.

- [ ] **Step 2: Confirm `avatarHueFor` is only imported from `:core:common`**

Run: `grep -rn 'import .*avatarHueFor' --include='*.kt' . | grep -v /build/ | grep -v 'core.common.avatar'`
Expected: no output.

- [ ] **Step 3: Full gates**

Run: `./gradlew spotlessCheck :app:checkSortDependencies`
Run: `./gradlew :designsystem:lintDebug :feature:chats:impl:lintDebug :feature:settings:impl:lintDebug :feature:notifications:impl:lintDebug :core:common:lintDebug`
Run: `./gradlew :designsystem:testDebugUnitTest :core:common:test :feature:chats:impl:test :feature:settings:impl:test :feature:profile:impl:test :feature:notifications:impl:test :core:actors:test`
Run: `./gradlew :app:assembleDebug`
Expected: all BUILD SUCCESSFUL. (Flavored modules: use the production variant task if `testDebugUnitTest` is ambiguous, e.g. `:feature:chats:impl:testProductionDebugUnitTest`.)

- [ ] **Step 4: Validate all screenshot baselines together**

Run: `./gradlew :designsystem:validateDebugScreenshotTest :feature:notifications:impl:validateDebugScreenshotTest :feature:chats:impl:validateDebugScreenshotTest :feature:settings:impl:validateDebugScreenshotTest`
Expected: PASS (or, if CI-rendered, rely on the `update-baselines` CI label).

- [ ] **Step 5: Push and update PR #550**

```bash
git push
```

PR #550 already exists on this branch. Update its title to `feat(designsystem): AvatarGroup component + avatar-fallback consolidation`, change the body's `Refs: nubecita-364e` so the PR will `Closes: nubecita-364e` on merge, request Copilot review, and start CI monitoring (per the bd-workflow finish flow).

---

## Self-Review

**Spec coverage:** Decision 1 (canonical facepile) → Task 3 + Task 4 (delete StackedAvatarRow). Decision 2 (`ImmutableList<AuthorUi>`) → Task 3 API. Decision 3 (4 + pill) → Task 3 `avatarGroupSplit` + test. Decision 4 (hue+initial fallback) → Task 2. Decision 5 (NubecitaAvatar owns fallback; migrate all 4 call sites) → Tasks 2, 5, 6. Decision 6 (move avatarHueFor) → Task 1. Visual rules (left-on-top, 1.5dp ring, 30% overlap, pill tokens) → Task 3 code. DEFAULT_AVATAR_SIZE→internal → Task 2. Testing plan (screenshots + split unit test + relocated AvatarHueTest) → Tasks 1/2/3 + Task 7 Step 4. All covered.

**Placeholder scan:** No TBD/TODO. The two "confirm the size from Step 1" notes are explicit verification instructions, not deferred work — the substitution code is fully shown.

**Type consistency:** `AvatarFallback(hue: Int, initial: Char?)`, `avatarFallbackFor(member: AuthorUi)`, `AvatarGroupSplit(visibleAvatars, overflowCount)`, `avatarGroupSplit(total, maxVisible)`, `AvatarGroup(members, contentDescription, modifier, maxVisible, avatarSize)`, `DEFAULT_AVATAR_SIZE` (internal) — names used identically across Tasks 2, 3, 5, 6.
