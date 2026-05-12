# Profile Bead D Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the own-profile screen with a functional Posts tab in `:feature:profile:impl`, replacing the `:app`-side `Profile(handle = null)` placeholder.

**Architecture:** A `Profile` `NavKey` resolves through `ProfileNavigationModule`'s `@MainShell` `EntryProviderInstaller` to a stateful `ProfileScreen` Composable. `ProfileScreen` collects `ProfileViewModel`'s `StateFlow<ProfileScreenViewState>` and effects, then renders `ProfileScreenContent` — a stateless host that builds one `Scaffold` → `PullToRefreshBox` → `LazyColumn` with the hero card as the first item, sticky pill tabs as a `stickyHeader`, and the active tab body contributed via `LazyListScope` extensions. The Replies / Media tabs render a themed "arrives soon" placeholder; only the Posts tab is functional in this bead.

**Tech Stack:** Kotlin · Jetpack Compose with Material 3 Expressive · `:designsystem.BoldHeroGradient` + `:designsystem.ProfilePillTabs` + `:designsystem.PostCard` · Hilt (assisted-inject ViewModel via `hiltViewModel<VM, VM.Factory>(creationCallback = …)`) · `kotlinx.collections.immutable` · JUnit 5 + Turbine + AssertK + mockk for VM/unit tests · `com.android.tools.screenshot.PreviewTest` for screenshot baselines · `createAndroidComposeRule<ComponentActivity>()` for the instrumentation smoke test.

**Predecessor design:** `docs/superpowers/specs/2026-05-12-profile-bead-d-design.md`. The design doc is the source of truth for the file layout, the contract extension, and the visual decisions; refer back to it when something is ambiguous.

---

## File Structure

```
feature/profile/impl/
├── build.gradle.kts                                    # MODIFY: nothing — deps already in place from Bead C
├── src/
│   ├── main/
│   │   ├── kotlin/net/kikin/nubecita/feature/profile/impl/
│   │   │   ├── ProfileContract.kt                      # MODIFY: + ProfileEvent.RetryTab(tab)
│   │   │   ├── ProfileViewModel.kt                     # MODIFY: + onRetryTab handler in handleEvent
│   │   │   ├── ProfileScreen.kt                        # CREATE: stateful host, effect collector
│   │   │   ├── ProfileScreenContent.kt                 # CREATE: stateless Scaffold + LazyColumn
│   │   │   ├── ProfileTabsConfig.kt                    # CREATE: the PillTab<ProfileTab> definitions
│   │   │   ├── di/
│   │   │   │   └── ProfileNavigationModule.kt         # MODIFY: replace inert provider with real entry
│   │   │   └── ui/
│   │   │       ├── ProfileHero.kt                      # CREATE: orchestrator + 3 header lifecycle branches
│   │   │       ├── ProfileStatsRow.kt                  # CREATE
│   │   │       ├── ProfileMetaRow.kt                   # CREATE
│   │   │       ├── ProfileActionsRow.kt                # CREATE (own variant only in Bead D)
│   │   │       ├── ProfilePostsTabBody.kt              # CREATE: LazyListScope extension
│   │   │       ├── ProfileEmptyState.kt                # CREATE
│   │   │       ├── ProfileErrorState.kt                # CREATE
│   │   │       ├── ProfileLoadingState.kt              # CREATE
│   │   │       └── ProfileTabPlaceholder.kt            # CREATE (Replies/Media "arrives soon")
│   │   └── res/values/strings.xml                      # CREATE: new resource file
│   ├── test/kotlin/net/kikin/nubecita/feature/profile/impl/
│   │   └── ProfileViewModelTest.kt                     # MODIFY: + RetryTab test case
│   ├── screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/
│   │   ├── ProfileScreenContentScreenshotTest.kt      # CREATE: 8 fixtures × 2 themes
│   │   └── ui/
│   │       ├── ProfileStatsRowScreenshotTest.kt        # CREATE
│   │       ├── ProfileMetaRowScreenshotTest.kt         # CREATE
│   │       ├── ProfileActionsRowScreenshotTest.kt      # CREATE
│   │       └── ProfileTabPlaceholderScreenshotTest.kt  # CREATE
│   └── androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/
│       └── ProfileScreenInstrumentationTest.kt         # CREATE: Edit-tap → snackbar
└── (no consumer-rules.pro change — already in place from Bead C)

app/src/main/java/net/kikin/nubecita/shell/
└── MainShellPlaceholderModule.kt                       # MODIFY: split Profile out, keep Settings
```

---

## Task 1: Contract extension — `ProfileEvent.RetryTab(tab)`

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileContract.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt`
- Test: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt`

- [ ] **Step 1: Write the failing test in `ProfileViewModelTest.kt`**

Append this test inside `ProfileViewModelTest` class (after the existing `LoadMore issues a getAuthorFeed call with the correct per-tab cursor` test):

```kotlin
@Test
fun `RetryTab re-launches initial tab load for the named tab`() =
    runTest(mainDispatcher.dispatcher) {
        // First call fails (initial load), second call succeeds (the retry).
        val firstResult = Result.failure<ProfileTabPage>(IOException("net down"))
        val secondResult = Result.success(EMPTY_PAGE)
        val repo =
            FakeProfileRepository(
                headerResult = Result.success(SAMPLE_HEADER),
                tabResults =
                    mapOf(
                        ProfileTab.Posts to firstResult,
                        ProfileTab.Replies to Result.success(EMPTY_PAGE),
                        ProfileTab.Media to Result.success(EMPTY_PAGE),
                    ),
            )
        val vm = newVm(repo = repo)
        advanceUntilIdle()
        assertTrue(
            vm.uiState.value.postsStatus is TabLoadStatus.InitialError,
            "after init the Posts tab MUST be in InitialError",
        )
        // Flip the fake's result for the next call.
        repo.tabResults = repo.tabResults.toMutableMap().apply {
            put(ProfileTab.Posts, secondResult)
        }
        val priorPostsCalls = repo.tabCalls[ProfileTab.Posts]!!.get()

        vm.handleEvent(ProfileEvent.RetryTab(ProfileTab.Posts))
        advanceUntilIdle()

        assertEquals(
            priorPostsCalls + 1,
            repo.tabCalls[ProfileTab.Posts]!!.get(),
            "RetryTab MUST issue exactly one additional fetchTab call",
        )
        assertTrue(
            vm.uiState.value.postsStatus is TabLoadStatus.Loaded,
            "after RetryTab succeeds the Posts tab MUST be in Loaded",
        )
    }
```

This requires `FakeProfileRepository.tabResults` to be mutable. Promote the existing `private val tabResults: Map<ProfileTab, Result<ProfileTabPage>>` to `var tabResults: Map<ProfileTab, Result<ProfileTabPage>>` inside `FakeProfileRepository`. The other test methods don't mutate it — safe change.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest --tests \
  "net.kikin.nubecita.feature.profile.impl.ProfileViewModelTest.RetryTab*"
```

Expected: FAIL with `Unresolved reference: RetryTab` (compile error on `ProfileEvent.RetryTab`).

- [ ] **Step 3: Add `RetryTab` to the `ProfileEvent` sealed interface**

In `ProfileContract.kt`, inside `sealed interface ProfileEvent : UiEvent { … }`, append:

```kotlin
    /** User tapped an inline Retry affordance in a tab's InitialError state. */
    data class RetryTab(
        val tab: ProfileTab,
    ) : ProfileEvent
```

Place it after the existing `LoadMore` event and before `FollowTapped`.

- [ ] **Step 4: Wire the handler in `ProfileViewModel.handleEvent`**

In `ProfileViewModel.kt`, inside `override fun handleEvent(event: ProfileEvent)`'s `when` block, add a new branch (place it after `is ProfileEvent.LoadMore -> onLoadMore(event.tab)` and before the stubbed actions):

```kotlin
                is ProfileEvent.RetryTab -> onRetryTab(event.tab)
```

Below `private fun onLoadMore(tab: ProfileTab)` (at the appropriate location in the `// -- Internals` section), add:

```kotlin
        /**
         * Tab-level retry from the inline error state. Re-launches the
         * initial-load path for the named tab via the same code path
         * `launchInitialLoads` uses on first composition. No header
         * refresh — header has its own retry affordance.
         */
        private fun onRetryTab(tab: ProfileTab) {
            val actor = resolveActor() ?: return
            launchInitialTabLoad(actor, tab)
        }
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest --tests \
  "net.kikin.nubecita.feature.profile.impl.ProfileViewModelTest.RetryTab*"
```

Expected: PASS.

- [ ] **Step 6: Run the full VM test class to check for regressions**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest
```

Expected: all 8 tests pass (7 from Bead C + the 1 new RetryTab test).

- [ ] **Step 7: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileContract.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModel.kt \
        feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt
git commit -m "feat(feature/profile/impl): add ProfileEvent.RetryTab for per-tab inline retry

Bead D needs an event the ProfileErrorState's Retry button can dispatch
without refreshing the header. Additive contract change — Bead C tests
continue to pass.

Refs: nubecita-s6p.4"
```

---

## Task 2: String resources

**Files:**
- Create: `feature/profile/impl/src/main/res/values/strings.xml`

- [ ] **Step 1: Create the resource directory and file**

```bash
mkdir -p feature/profile/impl/src/main/res/values
```

Then create `feature/profile/impl/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Tab labels (consumed by ProfileTabsConfig.kt) -->
    <string name="profile_tab_posts">Posts</string>
    <string name="profile_tab_replies">Replies</string>
    <string name="profile_tab_media">Media</string>

    <!-- Actions row -->
    <string name="profile_action_edit">Edit profile</string>
    <string name="profile_action_overflow">More options</string>
    <string name="profile_action_follow">Follow</string>
    <string name="profile_action_message">Message</string>

    <!-- Snackbar copy for stubbed actions (ShowComingSoon) -->
    <string name="profile_snackbar_edit_coming_soon">Edit profile — coming soon</string>
    <string name="profile_snackbar_follow_coming_soon">Follow — coming soon</string>
    <string name="profile_snackbar_message_coming_soon">Direct messages — coming soon</string>

    <!-- Snackbar copy for ShowError -->
    <string name="profile_snackbar_error_network">Network error. Pull to refresh.</string>
    <string name="profile_snackbar_error_unknown">Unable to load profile.</string>

    <!-- Stats row labels -->
    <string name="profile_stats_posts_label">Posts</string>
    <string name="profile_stats_followers_label">Followers</string>
    <string name="profile_stats_follows_label">Following</string>

    <!-- Meta row content descriptions -->
    <string name="profile_meta_link_content_description">Website</string>
    <string name="profile_meta_location_content_description">Location</string>
    <string name="profile_meta_joined_content_description">Joined date</string>

    <!-- Hero accessibility -->
    <string name="profile_avatar_content_description">Profile photo of %1$s</string>

    <!-- Tab body — empty states (per-tab copy) -->
    <string name="profile_posts_empty_title">No posts yet</string>
    <string name="profile_posts_empty_body">When this user posts, you\'ll see it here.</string>
    <string name="profile_replies_empty_title">No replies yet</string>
    <string name="profile_replies_empty_body">When this user replies, you\'ll see it here.</string>
    <string name="profile_media_empty_title">No media yet</string>
    <string name="profile_media_empty_body">When this user posts media, you\'ll see it here.</string>

    <!-- Tab body — error states -->
    <string name="profile_error_network_title">Can\'t reach Bluesky</string>
    <string name="profile_error_network_body">Check your network and try again.</string>
    <string name="profile_error_unknown_title">Something went wrong</string>
    <string name="profile_error_unknown_body">An unexpected error occurred while loading.</string>
    <string name="profile_error_retry">Try again</string>

    <!-- Tab body — Bead D placeholders for Replies/Media -->
    <string name="profile_tab_placeholder_replies_title">Replies tab arrives soon</string>
    <string name="profile_tab_placeholder_media_title">Media tab arrives soon</string>
    <string name="profile_tab_placeholder_body">Coming in the next release.</string>

    <!-- Header inline-error -->
    <string name="profile_header_error_title">Couldn\'t load profile</string>
    <string name="profile_header_error_retry">Try again</string>
</resources>
```

- [ ] **Step 2: Verify the build picks up the resource file**

```bash
./gradlew :feature:profile:impl:assembleDebug
```

Expected: BUILD SUCCESSFUL. (Resources are now bound to `R.string.*` in the generated `R.java`.)

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/main/res/values/strings.xml
git commit -m "feat(feature/profile/impl): add Bead D string resources

Adds tab labels, action labels, snackbar copy, stats/meta/error/empty
copy, and the temporary Replies/Media tab placeholder copy. Strings
follow the codebase convention of feature-prefixed keys (compare to
:feature:feed:impl/res/values/strings.xml).

Refs: nubecita-s6p.4"
```

---

## Task 3: `ProfileTabsConfig.kt` — pill tab definitions

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileTabsConfig.kt`

- [ ] **Step 1: Create `ProfileTabsConfig.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.tabs.PillTab

/**
 * Builds the immutable list of `PillTab<ProfileTab>` rendered by
 * `:designsystem.ProfilePillTabs` in the profile screen. Labels are
 * resolved via `stringResource` at composition time so locale changes
 * recompose the row; the resulting list is `remember`'d so identity
 * is stable across recompositions when the resolved strings haven't
 * changed.
 *
 * Order matches the spec: Posts, Replies, Media (left-to-right LTR).
 * Icon glyphs use the NubecitaIcons catalog — Article for posts,
 * Reply for replies, Image for media.
 */
@Composable
internal fun rememberProfilePillTabs(): ImmutableList<PillTab<ProfileTab>> {
    val postsLabel = stringResource(R.string.profile_tab_posts)
    val repliesLabel = stringResource(R.string.profile_tab_replies)
    val mediaLabel = stringResource(R.string.profile_tab_media)
    return remember(postsLabel, repliesLabel, mediaLabel) {
        persistentListOf(
            PillTab(value = ProfileTab.Posts, label = postsLabel, iconName = NubecitaIconName.Article),
            PillTab(value = ProfileTab.Replies, label = repliesLabel, iconName = NubecitaIconName.Reply),
            PillTab(value = ProfileTab.Media, label = mediaLabel, iconName = NubecitaIconName.Image),
        )
    }
}
```

- [ ] **Step 2: Verify the icon names exist in `NubecitaIconName`**

```bash
grep -E "Article|Reply|Image" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt
```

Expected: 3 matches showing `Article`, `Reply`, `Image` enum entries. **If any are missing**, pick the closest available entry (e.g. `Notes` instead of `Article`, `ChatBubble` instead of `Reply`, `Photo` instead of `Image`) — verify via the same grep and update the `iconName = …` parameters accordingly. The pillrow only cares about the icon's existence, not specific naming.

- [ ] **Step 3: Compile to verify the file is valid**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileTabsConfig.kt
git commit -m "feat(feature/profile/impl): add ProfileTabsConfig pill tab list

Hoists the three pill tabs into a single rememberProfilePillTabs()
helper used by ProfileScreenContent. Lets the screen pass an
ImmutableList<PillTab<ProfileTab>> to :designsystem.ProfilePillTabs
without inlining the locale-aware string resolution.

Refs: nubecita-s6p.4"
```

---

## Task 4: `ProfileStatsRow.kt`

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileStatsRow.kt`
- Test: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileStatsRowScreenshotTest.kt`

- [ ] **Step 1: Create `ProfileStatsRow.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Inline stats row rendered inside the hero: one Text with three
 * locale-aware short-scale-abbreviated counts separated by `·`.
 *
 * Spec mandates the inline shape ("MUST NOT be present" for the chip
 * variant) — see openspec/changes/add-profile-feature/specs/
 * feature-profile/spec.md "Inline stats and meta row replace the
 * chip variant".
 */
@Composable
internal fun ProfileStatsRow(
    postsCount: Long,
    followersCount: Long,
    followsCount: Long,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val posts = formatCompact(postsCount, locale)
    val followers = formatCompact(followersCount, locale)
    val follows = formatCompact(followsCount, locale)
    val postsLabel = stringResource(R.string.profile_stats_posts_label)
    val followersLabel = stringResource(R.string.profile_stats_followers_label)
    val followsLabel = stringResource(R.string.profile_stats_follows_label)
    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "$posts $postsLabel · $followers $followersLabel · $follows $followsLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Locale-aware short-scale abbreviation via java.text.CompactNumberFormat
 * (API 30+). The project minSdk is 26 — fall back to vanilla NumberFormat
 * on pre-30 devices (no abbreviation; raw count). Compact formatting is
 * an aesthetic enhancement, not a correctness requirement.
 */
private fun formatCompact(value: Long, locale: Locale): String =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        java.text.CompactDecimalFormat
            .getInstance(locale, java.text.CompactDecimalFormat.Style.SHORT)
            .format(value)
    } else {
        NumberFormat.getInstance(locale).format(value)
    }
```

- [ ] **Step 2: Create the screenshot test**

`feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileStatsRowScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "stats-typical-light", showBackground = true)
@Preview(name = "stats-typical-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileStatsRowTypicalScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileStatsRow(postsCount = 412, followersCount = 2_142, followsCount = 342)
    }
}

@PreviewTest
@Preview(name = "stats-large-light", showBackground = true)
@Preview(name = "stats-large-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileStatsRowLargeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileStatsRow(postsCount = 1_412, followersCount = 1_400_000, followsCount = 12_345)
    }
}
```

- [ ] **Step 3: Generate the screenshot baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL; 4 new PNGs under `feature/profile/impl/src/screenshotTest/screenshotTest-updates/` (or wherever the gradle plugin emits — same path other modules use).

- [ ] **Step 4: Move the baselines to the canonical location**

```bash
# The validateDebugScreenshotTest task compares against the canonical
# reference directory. Promote the updates by re-running validate:
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: 4 baselines validate as PASS (since `updateDebugScreenshotTest` writes them to the reference location directly).

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileStatsRow.kt \
        feature/profile/impl/src/screenshotTest/
git commit -m "feat(feature/profile/impl): add ProfileStatsRow with screenshot baselines

Inline stats row per the design spec — locale-aware short-scale via
CompactDecimalFormat (API 30+ with NumberFormat fallback). Screenshot
fixtures cover the typical and large-count rendering paths.

Refs: nubecita-s6p.4"
```

---

## Task 5: `ProfileMetaRow.kt`

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMetaRow.kt`
- Test: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMetaRowScreenshotTest.kt`

- [ ] **Step 1: Create `ProfileMetaRow.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Up to three optional meta rows rendered as `NubecitaIcon` (14 dp) +
 * Text pairs. Rows whose data is `null` are not rendered (no
 * placeholder, no spacing). If all three are null the Column renders
 * nothing (zero height) — the caller never sees a wasted 0 dp slot.
 *
 * Icon glyphs: Link / Location / Calendar from the NubecitaIcons
 * catalog. If any glyph is missing from the catalog, the file
 * `:designsystem/src/main/kotlin/.../icon/NubecitaIconName.kt` is the
 * source of truth — pick the closest available entry.
 */
@Composable
internal fun ProfileMetaRow(
    website: String?,
    location: String?,
    joinedDisplay: String?,
    modifier: Modifier = Modifier,
) {
    if (website == null && location == null && joinedDisplay == null) return
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (website != null) {
            MetaRowEntry(
                iconName = NubecitaIconName.Link,
                contentDescription = stringResource(R.string.profile_meta_link_content_description),
                text = website,
            )
        }
        if (location != null) {
            MetaRowEntry(
                iconName = NubecitaIconName.Location,
                contentDescription = stringResource(R.string.profile_meta_location_content_description),
                text = location,
            )
        }
        if (joinedDisplay != null) {
            MetaRowEntry(
                iconName = NubecitaIconName.Calendar,
                contentDescription = stringResource(R.string.profile_meta_joined_content_description),
                text = joinedDisplay,
            )
        }
    }
}

@Composable
private fun MetaRowEntry(
    iconName: NubecitaIconName,
    contentDescription: String,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NubecitaIcon(
            name = iconName,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 2: Verify the icon names**

```bash
grep -E "\bLink\b|\bLocation\b|\bCalendar\b" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt
```

If any are missing, substitute the closest available entry — for example `Link` may not exist, in which case use `Public` (URL/website affordance), `Location` may be `Place`, `Calendar` may be `Event`. Pick the closest available; the meta row's intent is communicated by the icon's affordance, not the exact name.

- [ ] **Step 3: Create the screenshot test**

`feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMetaRowScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "meta-all-light", showBackground = true)
@Preview(name = "meta-all-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMetaRowAllScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileMetaRow(
            website = "alice.example.com",
            location = "Lima, Peru",
            joinedDisplay = "Joined April 2023",
        )
    }
}

@PreviewTest
@Preview(name = "meta-joined-only-light", showBackground = true)
@Preview(name = "meta-joined-only-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMetaRowJoinedOnlyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileMetaRow(
            website = null,
            location = null,
            joinedDisplay = "Joined April 2023",
        )
    }
}
```

- [ ] **Step 4: Generate + validate the baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMetaRow.kt \
        feature/profile/impl/src/screenshotTest/
git commit -m "feat(feature/profile/impl): add ProfileMetaRow with screenshot baselines

Three conditional meta entries (link / location / joined). Hidden when
their respective data is null per the spec scenario \"Meta row hides
absent optional fields\".

Refs: nubecita-s6p.4"
```

---

## Task 6: `ProfileActionsRow.kt` (own variant only)

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsRow.kt`
- Test: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsRowScreenshotTest.kt`

- [ ] **Step 1: Create `ProfileActionsRow.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Hero actions row.
 *
 * In Bead D only the `ownProfile = true` branch is fully wired (Edit +
 * overflow). The `ownProfile = false` branch renders the same affordances
 * defensively — Bead F replaces this with the Follow + Message + overflow
 * row. Until then, other-user navigation reaches a screen that still
 * renders the Edit row; not a primary user journey in Bead D.
 *
 * The overflow icon's onClick is a deliberate no-op in Bead D — Bead F
 * wires a real DropdownMenu containing the Settings entry. The icon's
 * contentDescription communicates the affordance for accessibility.
 */
@Composable
internal fun ProfileActionsRow(
    ownProfile: Boolean,
    onEdit: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onEdit) {
            Text(text = stringResource(R.string.profile_action_edit))
        }
        IconButton(onClick = onOverflow) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.profile_action_overflow),
            )
        }
        // `ownProfile = false` branch (Follow + Message + overflow) lands
        // in Bead F. Bead D ignores the param defensively for now.
        @Suppress("UNUSED_PARAMETER")
        ownProfile
    }
}
```

Note: the `@Suppress("UNUSED_PARAMETER")` + the trailing `ownProfile` line silences lint warning that `ownProfile` is unused — Bead F will wire it to a real branch. If lint is configured to fail on unused parameters even with the suppression, drop both lines and accept the warning; Bead F restores meaningful usage.

- [ ] **Step 2: Verify `NubecitaIconName.MoreVert` exists**

```bash
grep -E "\bMoreVert\b" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt
```

If missing, substitute `MoreHoriz` or `Menu`. Update the icon name in step 1 accordingly.

- [ ] **Step 3: Create the screenshot test**

`feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsRowScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "actions-own-light", showBackground = true)
@Preview(name = "actions-own-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileActionsRowOwnScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileActionsRow(ownProfile = true, onEdit = {}, onOverflow = {})
    }
}
```

- [ ] **Step 4: Generate + validate baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileActionsRow.kt \
        feature/profile/impl/src/screenshotTest/
git commit -m "feat(feature/profile/impl): add ProfileActionsRow (own variant)

Bead D ships the own-variant only (Edit + overflow). Other-user variant
(Follow + Message) lands in Bead F. Overflow icon onClick is a no-op
until Bead F wires a real DropdownMenu containing Settings.

Refs: nubecita-s6p.4"
```

---

## Task 7: Empty / Error / Loading state composables + placeholder

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileEmptyState.kt`
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileErrorState.kt`
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileLoadingState.kt`
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholder.kt`
- Test: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholderScreenshotTest.kt`

- [ ] **Step 1: Create `ProfileEmptyState.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Per-tab empty state. Used when `TabLoadStatus.Loaded.items.isEmpty()`.
 * Bead D consumes this for the Posts tab; Bead E reuses for Replies /
 * Media — the per-tab copy is keyed off the `tab` parameter so the
 * call sites in Bead E don't need to change anything.
 */
@Composable
internal fun ProfileEmptyState(
    tab: ProfileTab,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (tab) {
            ProfileTab.Posts -> R.string.profile_posts_empty_title
            ProfileTab.Replies -> R.string.profile_replies_empty_title
            ProfileTab.Media -> R.string.profile_media_empty_title
        }
    val bodyRes =
        when (tab) {
            ProfileTab.Posts -> R.string.profile_posts_empty_body
            ProfileTab.Replies -> R.string.profile_replies_empty_body
            ProfileTab.Media -> R.string.profile_media_empty_body
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 2: Create `ProfileErrorState.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Tab-level inline error with a Retry button. Used when
 * `TabLoadStatus.InitialError(error)`. Header-level errors render a
 * separate variant inside ProfileHero (different layout — the hero
 * is wider and uses a card-tinted background).
 */
@Composable
internal fun ProfileErrorState(
    error: ProfileError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (error) {
            ProfileError.Network -> R.string.profile_error_network_title
            is ProfileError.Unknown -> R.string.profile_error_unknown_title
        }
    val bodyRes =
        when (error) {
            ProfileError.Network -> R.string.profile_error_network_body
            is ProfileError.Unknown -> R.string.profile_error_unknown_body
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(text = stringResource(R.string.profile_error_retry))
        }
    }
}
```

- [ ] **Step 3: Create `ProfileLoadingState.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.designsystem.component.PostCardShimmer

/**
 * Tab-level shimmer skeleton. Renders 5 PostCardShimmers stacked
 * vertically — matches the Feed module's initial-loading visual.
 * Alternates the `showImagePlaceholder` flag to mimic Feed's
 * shimmer fixture.
 */
@Composable
internal fun ProfileLoadingState(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(SHIMMER_COUNT) { index ->
            PostCardShimmer(showImagePlaceholder = index % 2 == 0)
        }
    }
}

private const val SHIMMER_COUNT = 5
```

- [ ] **Step 4: Create `ProfileTabPlaceholder.kt`**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Bead D placeholder for the Replies / Media tabs whose real bodies
 * land in Bead E. Communicates the temporary state without disabling
 * the pill tabs — the user can still tap the tab and see something
 * intentional rather than a half-empty body.
 *
 * Bead E deletes this Composable; its call sites in
 * ProfileScreenContent are replaced with the real tab body LazyListScope
 * extensions.
 */
@Composable
internal fun ProfileTabPlaceholder(
    tab: ProfileTab,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (tab) {
            ProfileTab.Replies -> R.string.profile_tab_placeholder_replies_title
            ProfileTab.Media -> R.string.profile_tab_placeholder_media_title
            // Posts tab never renders this placeholder in Bead D — the
            // tab body is fully functional. Defensive default: use the
            // Replies copy if a caller incorrectly routes Posts here.
            ProfileTab.Posts -> R.string.profile_tab_placeholder_replies_title
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.profile_tab_placeholder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 5: Create the placeholder screenshot test**

`feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholderScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileTab

@PreviewTest
@Preview(name = "tab-placeholder-replies-light", showBackground = true)
@Preview(name = "tab-placeholder-replies-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTabPlaceholderRepliesScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTabPlaceholder(tab = ProfileTab.Replies)
    }
}

@PreviewTest
@Preview(name = "tab-placeholder-media-light", showBackground = true)
@Preview(name = "tab-placeholder-media-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTabPlaceholderMediaScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTabPlaceholder(tab = ProfileTab.Media)
    }
}
```

- [ ] **Step 6: Generate + validate baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileEmptyState.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileErrorState.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileLoadingState.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholder.kt \
        feature/profile/impl/src/screenshotTest/
git commit -m "feat(feature/profile/impl): add empty/error/loading state composables

Four state composables consumed by the Posts tab body (empty / error /
loading) and the Replies/Media tabs (placeholder). The placeholder
ships only for Bead D — Bead E deletes it once the real tab bodies
arrive. Screenshot baselines included for the placeholder; the empty
and error states get coverage via the full ProfileScreenContent
screenshots in a later task.

Refs: nubecita-s6p.4"
```

---

## Task 8: `ProfilePostsTabBody.kt` — LazyListScope extension

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfilePostsTabBody.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.feed.impl.ui.FeedAppendingIndicator
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

/**
 * Contributes the Posts-tab body items to the enclosing LazyColumn —
 * an extension on [LazyListScope] (rather than a Composable that
 * opens its own LazyColumn) so the hero, sticky pill tabs, and
 * posts share one scroll surface. Nested LazyColumns break sticky
 * headers, so this shape is the only one that satisfies the design.
 *
 * Branches on [status]:
 * - [TabLoadStatus.Idle], [TabLoadStatus.InitialLoading] → loading skeletons
 * - [TabLoadStatus.InitialError] → error state + Retry
 * - [TabLoadStatus.Loaded] with empty items → empty state
 * - [TabLoadStatus.Loaded] with items → PostCards + optional appending row
 */
internal fun LazyListScope.profilePostsTabBody(
    status: TabLoadStatus,
    callbacks: PostCallbacks,
    onRetry: () -> Unit,
) {
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading -> {
            item(key = "posts-loading", contentType = "loading") {
                ProfileLoadingState()
            }
        }
        is TabLoadStatus.InitialError -> {
            item(key = "posts-error", contentType = "error") {
                ProfileErrorState(error = status.error, onRetry = onRetry)
            }
        }
        is TabLoadStatus.Loaded -> {
            if (status.items.isEmpty()) {
                item(key = "posts-empty", contentType = "empty") {
                    ProfileEmptyState(tab = ProfileTab.Posts)
                }
            } else {
                items(
                    items = status.items,
                    key = { it.postUri },
                    contentType = { item ->
                        when (item) {
                            is TabItemUi.Post -> "post"
                            is TabItemUi.MediaCell -> "media" // unreachable for Posts tab; defensive
                        }
                    },
                ) { item ->
                    when (item) {
                        is TabItemUi.Post -> PostCard(post = item.post, callbacks = callbacks)
                        is TabItemUi.MediaCell -> {
                            // Posts filter (posts_no_replies) never yields a MediaCell.
                            // Branch exists for type completeness; renders nothing.
                        }
                    }
                }
                if (status.isAppending) {
                    item(key = "posts-appending", contentType = "appending") {
                        FeedAppendingIndicator()
                    }
                }
            }
        }
    }
}
```

Note: this reuses `:feature:feed:impl/ui/FeedAppendingIndicator.kt` for the appending row. That's a cross-feature dependency — `:feature:profile:impl` must depend on `:feature:feed:impl`. Verify the dep graph before proceeding:

```bash
grep -n "feature:feed:impl\|feature.feed.impl" feature/profile/impl/build.gradle.kts
```

If `:feature:feed:impl` is NOT in the dependency block, **don't add the cross-feature dep** (creates a circular-looking graph where two feature modules depend on each other and complicates future graph maintenance). Instead, create a local `ProfileAppendingIndicator.kt` that mirrors `FeedAppendingIndicator`'s body — it's a 30-line composable. Use this fallback path:

```kotlin
// In feature/profile/impl/src/main/kotlin/.../ui/ProfileAppendingIndicator.kt
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ProfileAppendingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}
```

And swap the `FeedAppendingIndicator()` call in `ProfilePostsTabBody.kt` for `ProfileAppendingIndicator()`. The import line `import net.kikin.nubecita.feature.feed.impl.ui.FeedAppendingIndicator` also becomes unnecessary.

- [ ] **Step 2: Compile to verify**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/
git commit -m "feat(feature/profile/impl): add profilePostsTabBody LazyListScope extension

Tab body contributor that emits the loading/error/empty/loaded items
into the outer LazyColumn. LazyListScope extension shape (rather than
a Composable opening its own LazyColumn) is required for the
sticky-pill-tabs + scrolling-hero design — see
docs/superpowers/specs/2026-05-12-profile-bead-d-design.md.

Refs: nubecita-s6p.4"
```

---

## Task 9: `ProfileHero.kt` — orchestrator with three lifecycle branches

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileHero.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.kikin.nubecita.designsystem.handleStyle
import net.kikin.nubecita.designsystem.displayNameStyle
import net.kikin.nubecita.designsystem.hero.BoldHeroGradient
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.R

private val AvatarSize = 88.dp
private val AvatarRingWidth = 4.dp

/**
 * Hero card orchestrator. Three header lifecycle branches:
 *
 * 1. `header != null` → full hero (gradient backdrop + avatar + name +
 *    handle + bio + stats + meta + actions). Note: a non-null
 *    `headerError` does NOT change this rendering — `headerError` is
 *    a sticky-flat-state flag whose user-visible signal is the
 *    snackbar at the screen level. The hero stays put.
 * 2. `header == null && headerError == null` → loading skeleton.
 * 3. `header == null && headerError != null` → inline error with Retry.
 *
 * `BoldHeroGradient` from :designsystem owns the Palette extraction,
 * caching, and contrast guard. We pass the banner URL and the
 * deterministic avatarHue fallback; the gradient swaps in once the
 * Palette extraction completes (which is synchronous on cache hit).
 */
@Composable
internal fun ProfileHero(
    header: ProfileHeaderUi?,
    headerError: ProfileError?,
    ownProfile: Boolean,
    onRetryHeader: () -> Unit,
    onEditTap: () -> Unit,
    onOverflowTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        header != null -> ProfileHeroLoaded(
            header = header,
            ownProfile = ownProfile,
            onEditTap = onEditTap,
            onOverflowTap = onOverflowTap,
            modifier = modifier,
        )
        headerError != null -> ProfileHeroError(
            error = headerError,
            onRetry = onRetryHeader,
            modifier = modifier,
        )
        else -> ProfileHeroLoading(modifier = modifier)
    }
}

@Composable
private fun ProfileHeroLoaded(
    header: ProfileHeaderUi,
    ownProfile: Boolean,
    onEditTap: () -> Unit,
    onOverflowTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BoldHeroGradient(
            banner = header.bannerUrl,
            avatarHue = header.avatarHue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Avatar with surface ring
                Box(
                    modifier = Modifier
                        .size(AvatarSize + AvatarRingWidth * 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = header.avatarUrl,
                        contentDescription = stringResource(
                            R.string.profile_avatar_content_description,
                            header.displayName ?: header.handle,
                        ),
                        modifier = Modifier.size(AvatarSize).clip(CircleShape),
                    )
                }
                Text(
                    text = header.displayName ?: header.handle,
                    style = displayNameStyle,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "@${header.handle}",
                    style = handleStyle,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (header.bio != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = header.bio,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        ProfileStatsRow(
            postsCount = header.postsCount,
            followersCount = header.followersCount,
            followsCount = header.followsCount,
        )
        ProfileMetaRow(
            website = header.website,
            location = header.location,
            joinedDisplay = header.joinedDisplay,
        )
        ProfileActionsRow(
            ownProfile = ownProfile,
            onEdit = onEditTap,
            onOverflow = onOverflowTap,
        )
    }
}

@Composable
private fun ProfileHeroLoading(modifier: Modifier = Modifier) {
    // Greyed-out skeleton mimicking the loaded layout: circle for avatar,
    // two thin rectangles for displayName + handle. Doesn't depend on
    // shimmer animation — Compose previews / screenshot tests render
    // static. A live shimmer effect can be added in a follow-up if
    // telemetry shows the static skeleton feels stale.
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 14.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun ProfileHeroError(
    error: ProfileError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bodyRes =
        when (error) {
            ProfileError.Network -> R.string.profile_error_network_body
            is ProfileError.Unknown -> R.string.profile_error_unknown_body
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_header_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(text = stringResource(R.string.profile_header_error_retry))
        }
    }
}
```

- [ ] **Step 2: Verify the `:designsystem` typography imports exist**

```bash
grep -rn "displayNameStyle\|handleStyle" designsystem/src/main/kotlin/ | head -5
```

Expected: hits for both as top-level vals or extension properties on Typography. If they're not at the package root (`net.kikin.nubecita.designsystem`), update the import paths in step 1 to match. (The Bead A commit `28bd651` defined them; consult that commit if needed.)

- [ ] **Step 3: Verify Coil's `AsyncImage` import is correct**

```bash
grep -rn "io.coil.compose.AsyncImage\|coil.compose.AsyncImage\|coil3.compose.AsyncImage" feature/feed/impl/src/main 2>&1 | head -3
```

Use whichever package Feed uses. If Feed uses `coil3.compose.AsyncImage`, update the import accordingly. The above code assumes `coil.compose.AsyncImage` (Coil 2.x).

- [ ] **Step 4: Compile to verify**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileHero.kt
git commit -m "feat(feature/profile/impl): add ProfileHero with three lifecycle branches

Loaded variant renders BoldHeroGradient backdrop + avatar/name/handle/bio
+ stats/meta/actions rows. Loading variant renders a static skeleton.
Error variant renders an inline retry. Per the design, the hero stays
loaded when headerError is non-null over a previously-loaded header —
the snackbar at the screen level is the user-visible signal for that
sticky-state branch.

Refs: nubecita-s6p.4"
```

---

## Task 10: `ProfileScreenContent.kt` — stateless screen body

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs
import net.kikin.nubecita.feature.profile.impl.ui.ProfileHero
import net.kikin.nubecita.feature.profile.impl.ui.ProfileTabPlaceholder
import net.kikin.nubecita.feature.profile.impl.ui.profilePostsTabBody

private const val PREFETCH_DISTANCE = 5

/**
 * Stateless screen body. Takes the canonical
 * [ProfileScreenViewState] from Bead C plus the small set of
 * callbacks the host wires to VM events. Previews and screenshot
 * tests invoke this directly with fixture inputs — no ViewModel, no
 * Hilt graph, no live network.
 *
 * Renders one LazyColumn for the whole screen: hero as the first
 * item, sticky pill tabs as a stickyHeader, and the active tab body
 * contributed via LazyListScope extensions (Posts) or single items
 * (Replies / Media placeholders).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ProfileScreenContent(
    state: ProfileScreenViewState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    postCallbacks: PostCallbacks,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillTabs = rememberProfilePillTabs()
    val activeTabIsRefreshing = state.activeTabIsRefreshing()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = activeTabIsRefreshing,
            onRefresh = { onEvent(ProfileEvent.Refresh) },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = padding,
            ) {
                item(key = "hero", contentType = "hero") {
                    ProfileHero(
                        header = state.header,
                        headerError = state.headerError,
                        ownProfile = state.ownProfile,
                        onRetryHeader = { onEvent(ProfileEvent.Refresh) },
                        onEditTap = { onEvent(ProfileEvent.EditTapped) },
                        onOverflowTap = { /* Bead F wires Settings */ },
                    )
                }
                stickyHeader(key = "tabs", contentType = "tabs") {
                    ProfilePillTabs(
                        tabs = pillTabs,
                        selectedValue = state.selectedTab,
                        onSelect = { tab -> onEvent(ProfileEvent.TabSelected(tab)) },
                    )
                }
                when (state.selectedTab) {
                    ProfileTab.Posts ->
                        profilePostsTabBody(
                            status = state.postsStatus,
                            callbacks = postCallbacks,
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Posts)) },
                        )
                    ProfileTab.Replies ->
                        item(key = "replies-placeholder", contentType = "placeholder") {
                            ProfileTabPlaceholder(tab = ProfileTab.Replies)
                        }
                    ProfileTab.Media ->
                        item(key = "media-placeholder", contentType = "placeholder") {
                            ProfileTabPlaceholder(tab = ProfileTab.Media)
                        }
                }
            }
        }
    }

    // Pagination: gate the LoadMore dispatch on the active tab being Posts.
    // Without the gate, landing on the Replies / Media placeholder (which
    // contributes one item to the LazyColumn) immediately satisfies
    // `lastVisible > totalItemCount - PREFETCH_DISTANCE` and would fire
    // a stray LoadMore event for the Posts tab.
    val currentSelectedTab by rememberUpdatedState(state.selectedTab)
    val currentPostsStatus by rememberUpdatedState(state.postsStatus)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible > total - PREFETCH_DISTANCE
        }
            .distinctUntilChanged()
            .collect { pastThreshold ->
                if (
                    pastThreshold &&
                    currentSelectedTab == ProfileTab.Posts &&
                    currentPostsStatus is TabLoadStatus.Loaded &&
                    (currentPostsStatus as TabLoadStatus.Loaded).hasMore &&
                    !(currentPostsStatus as TabLoadStatus.Loaded).isAppending
                ) {
                    onEvent(ProfileEvent.LoadMore(ProfileTab.Posts))
                }
            }
    }
}

/**
 * Returns true when the currently-selected tab is in a `Loaded`
 * status with `isRefreshing = true`. PullToRefreshBox uses this to
 * drive its spinner.
 */
private fun ProfileScreenViewState.activeTabIsRefreshing(): Boolean {
    val status =
        when (selectedTab) {
            ProfileTab.Posts -> postsStatus
            ProfileTab.Replies -> repliesStatus
            ProfileTab.Media -> mediaStatus
        }
    return status is TabLoadStatus.Loaded && status.isRefreshing
}
```

- [ ] **Step 2: Compile to verify**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt
git commit -m "feat(feature/profile/impl): add ProfileScreenContent stateless body

One LazyColumn for the whole screen: hero as the first item, sticky
ProfilePillTabs as the second item, then the active tab body. Posts
tab dispatches to profilePostsTabBody() (LazyListScope extension);
Replies/Media render the Bead D placeholder. PullToRefreshBox wraps
the LazyColumn. Pagination dispatch is gated on selectedTab == Posts
so landing on a placeholder tab doesn't fire stray LoadMore events.

Refs: nubecita-s6p.4"
```

---

## Task 11: `ProfileScreen.kt` — stateful host

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.designsystem.component.PostCallbacks

/**
 * Stateful Profile screen.
 *
 * Owns the screen's lifecycle wiring: state collection, the single
 * effects collector that surfaces snackbars + nav callbacks, the
 * LazyListState hoisted via rememberSaveable (for back-nav and
 * config-change retention), the screen-internal SnackbarHostState,
 * and the remember-d PostCallbacks that dispatch to the VM.
 * Delegates rendering to [ProfileScreenContent], which previews and
 * screenshot tests call directly with fixture inputs.
 */
@Composable
internal fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToPost: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }

    val callbacks =
        remember(viewModel) {
            PostCallbacks(
                onTap = { post -> viewModel.handleEvent(ProfileEvent.PostTapped(post.id)) },
                onAuthorTap = { author ->
                    viewModel.handleEvent(ProfileEvent.HandleTapped(author.handle))
                },
                // Bead D ships read-only — like/repost/reply/share are no-ops here.
                // Wire to real handlers when the Profile epic adds writes.
                onLike = {},
                onRepost = {},
                onReply = {},
                onShare = {},
                onShareLongPress = {},
                onExternalEmbedTap = {},
            )
        }

    // Pre-resolve snackbar copy via stringResource() so locale changes
    // recompose. Reading via context.getString() inside the LaunchedEffect
    // would bypass Compose's resource tracking (LocalContextGetResourceValueCall).
    val errorNetworkMsg = stringResource(R.string.profile_snackbar_error_network)
    val errorUnknownMsg = stringResource(R.string.profile_snackbar_error_unknown)
    val comingSoonEdit = stringResource(R.string.profile_snackbar_edit_coming_soon)
    val comingSoonFollow = stringResource(R.string.profile_snackbar_follow_coming_soon)
    val comingSoonMessage = stringResource(R.string.profile_snackbar_message_coming_soon)

    // Wrap nav callbacks so the long-lived effect collector keys on Unit
    // (one collector for the screen's lifetime) but always calls the most
    // recent lambda the host supplied.
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToProfile by rememberUpdatedState(onNavigateToProfile)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ProfileError.Network -> errorNetworkMsg
                            is ProfileError.Unknown -> errorUnknownMsg
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.ShowComingSoon -> {
                    val msg =
                        when (effect.action) {
                            StubbedAction.Edit -> comingSoonEdit
                            StubbedAction.Follow -> comingSoonFollow
                            StubbedAction.Message -> comingSoonMessage
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is ProfileEffect.NavigateToProfile -> currentOnNavigateToProfile(effect.handle)
                ProfileEffect.NavigateToSettings -> {
                    // Bead F wires the Settings push.
                }
            }
        }
    }

    ProfileScreenContent(
        state = state,
        listState = listState,
        snackbarHostState = snackbarHostState,
        postCallbacks = callbacks,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Verify `PostCallbacks` constructor signature matches**

```bash
grep -n "class PostCallbacks\|companion object" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt | head -10
```

Expected: the `PostCallbacks` constructor accepts the lambdas in the order used above (onTap, onAuthorTap, onLike, onRepost, onReply, onShare, onShareLongPress, onExternalEmbedTap). If the signature differs (extra params, different order), update the `remember { PostCallbacks(...) }` block to match. The `:feature:feed:impl/FeedScreen.kt:122-150` block is the canonical reference if there's any ambiguity.

- [ ] **Step 3: Compile to verify**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreen.kt
git commit -m "feat(feature/profile/impl): add ProfileScreen stateful host

Mirrors :feature:feed:impl/FeedScreen.kt — state collection,
effect collector with replace-don't-stack snackbar semantics,
saveable LazyListState, remember-d PostCallbacks dispatching to
the VM. Replies/Media tab interactions and write actions are
no-ops in Bead D (Bead E/F/follow-up epics wire them).

Refs: nubecita-s6p.4"
```

---

## Task 12: Full-screen screenshot tests

**Files:**
- Create: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt`

- [ ] **Step 1: Create the screenshot test file**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import kotlin.time.Instant

/**
 * 8 fixtures × 2 themes = 16 screenshot baselines covering the full
 * ProfileScreenContent surface — hero variants (with/without banner,
 * loading, error) and Posts-tab variants (loaded, loading, empty,
 * error). Compact width only; Medium-width fixtures land in Bead F
 * once the ListDetailSceneStrategy metadata is wired.
 */
private val SAMPLE_HEADER =
    ProfileHeaderUi(
        did = "did:plc:alice",
        handle = "alice.bsky.social",
        displayName = "Alice",
        avatarUrl = null,
        bannerUrl = null,
        avatarHue = 217,
        bio = "Designer · lima → barcelona · she/her",
        location = "Lima, Peru",
        website = "alice.example.com",
        joinedDisplay = "Joined April 2023",
        postsCount = 412,
        followersCount = 2_142,
        followsCount = 342,
    )

private val SAMPLE_HEADER_WITH_BANNER =
    SAMPLE_HEADER.copy(
        // Banner URL is just a string; BoldHeroGradient handles the
        // null/blank short-circuit. We don't actually fetch in screenshot
        // tests (Coil is no-op'd in Layoutlib) — the gradient renders
        // the avatarHue fallback for both cases. The visual delta with
        // a real banner can only be verified on-device.
        bannerUrl = "https://cdn.example.com/banner.jpg",
    )

private val EMPTY_LIST = persistentListOf<TabItemUi>()

private fun sampleLoadedState(): ProfileScreenViewState =
    ProfileScreenViewState(
        handle = null,
        header = SAMPLE_HEADER,
        ownProfile = true,
        viewerRelationship = ViewerRelationship.Self,
        selectedTab = ProfileTab.Posts,
        postsStatus = TabLoadStatus.Loaded(
            items = persistentListOf(
                TabItemUi.Post(samplePostUi("a")),
                TabItemUi.Post(samplePostUi("b")),
                TabItemUi.Post(samplePostUi("c")),
            ),
            isAppending = false,
            isRefreshing = false,
            hasMore = false,
            cursor = null,
        ),
        repliesStatus = TabLoadStatus.Idle,
        mediaStatus = TabLoadStatus.Idle,
    )

private fun samplePostUi(suffix: String): PostUi =
    PostUi(
        id = "post-$suffix",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = AuthorUi(
            did = "did:plc:preview-$suffix",
            handle = "preview-$suffix.bsky.social",
            displayName = "Preview $suffix",
            avatarUrl = null,
        ),
        createdAt = Instant.parse("2026-04-26T10:00:00Z"),
        text = "Preview post $suffix — sample content for screenshot fixtures.",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 12),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

@Composable
private fun ProfileScreenContentHost(state: ProfileScreenViewState) {
    NubecitaTheme(dynamicColor = false) {
        ProfileScreenContent(
            state = state,
            listState = rememberLazyListState(),
            snackbarHostState = remember { SnackbarHostState() },
            postCallbacks = PostCallbacks.None,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "screen-hero-with-banner-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-hero-with-banner-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroWithBannerScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(header = SAMPLE_HEADER_WITH_BANNER),
    )
}

@PreviewTest
@Preview(name = "screen-hero-without-banner-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-hero-without-banner-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroWithoutBannerScreenshot() {
    ProfileScreenContentHost(sampleLoadedState())
}

@PreviewTest
@Preview(name = "screen-hero-loading-light", showBackground = true, heightDp = 800)
@Preview(name = "screen-hero-loading-dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroLoadingScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            header = null,
            headerError = null,
            postsStatus = TabLoadStatus.InitialLoading,
        ),
    )
}

@PreviewTest
@Preview(name = "screen-hero-error-light", showBackground = true, heightDp = 800)
@Preview(name = "screen-hero-error-dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenHeroErrorScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            header = null,
            headerError = ProfileError.Network,
            postsStatus = TabLoadStatus.Idle,
        ),
    )
}

@PreviewTest
@Preview(name = "screen-posts-loaded-light", showBackground = true, heightDp = 1600)
@Preview(name = "screen-posts-loaded-dark", showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsLoadedScreenshot() {
    ProfileScreenContentHost(sampleLoadedState())
}

@PreviewTest
@Preview(name = "screen-posts-loading-light", showBackground = true, heightDp = 1600)
@Preview(name = "screen-posts-loading-dark", showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsLoadingScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(postsStatus = TabLoadStatus.InitialLoading),
    )
}

@PreviewTest
@Preview(name = "screen-posts-empty-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-posts-empty-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsEmptyScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            postsStatus = TabLoadStatus.Loaded(
                items = EMPTY_LIST,
                isAppending = false,
                isRefreshing = false,
                hasMore = false,
                cursor = null,
            ),
        ),
    )
}

@PreviewTest
@Preview(name = "screen-posts-error-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-posts-error-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenPostsErrorScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(postsStatus = TabLoadStatus.InitialError(ProfileError.Network)),
    )
}
```

- [ ] **Step 2: Generate baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. 16 new PNGs written.

- [ ] **Step 3: Validate**

```bash
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/screenshotTest/
git commit -m "test(feature/profile/impl): full ProfileScreenContent screenshots

8 fixtures × 2 themes covering the hero variants (with/without banner,
loading, error) and Posts-tab variants (loaded, loading, empty, error).
Compact width only; Medium fixtures land in Bead F with the
ListDetailSceneStrategy metadata.

Refs: nubecita-s6p.4"
```

---

## Task 13: Replace `ProfileNavigationModule.kt`'s inert provider

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/di/ProfileNavigationModule.kt`

- [ ] **Step 1: Rewrite the file**

Replace the existing file's contents with:

```kotlin
package net.kikin.nubecita.feature.profile.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.ProfileScreen
import net.kikin.nubecita.feature.profile.impl.ProfileViewModel

/**
 * Real Profile entry. Bead D wires the screen for `Profile(handle = null)`
 * (own profile) and `Profile(handle = "...")` (other-user navigation
 * reaches the same screen; the actions-row branch for the latter is
 * Bead F territory).
 *
 * `ListDetailSceneStrategy.listPane{}` metadata is NOT applied here —
 * Bead F adds it so Medium-width post-taps land in the right pane.
 * Without the metadata, post-taps push PostDetailRoute onto the
 * back stack and replace the profile screen full-screen on all
 * widths in Bead D — the same behavior the previous :app placeholder
 * exhibited.
 *
 * The Settings entry stays inert in Bead D — Bead F wires the
 * Settings stub Composable + its overflow-menu entry point on the
 * profile screen.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideProfileEntries(): EntryProviderInstaller =
        {
            entry<Profile> { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
                    onNavigateToProfile = { handle -> navState.add(Profile(handle = handle)) },
                )
            }
        }

    @Provides
    @IntoSet
    @MainShell
    fun provideSettingsEntries(): EntryProviderInstaller =
        {
            // Inert in Bead D. Bead F wires the Settings stub Composable
            // here and removes the :app-side Settings placeholder.
        }
}
```

- [ ] **Step 2: Compile + run VM tests to make sure nothing regressed**

```bash
./gradlew :feature:profile:impl:assembleDebug :feature:profile:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; all VM tests pass.

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/di/ProfileNavigationModule.kt
git commit -m "feat(feature/profile/impl): wire real ProfileScreen as the Profile entry

Replaces the inert Bead C empty-lambda provider with the actual screen.
Uses the assisted-inject Hilt bridge (creationCallback) to pass the
NavKey-typed Profile route to ProfileViewModel.Factory#create. Mirrors
:feature:postdetail:impl/di/PostDetailNavigationModule.kt.

ListDetailSceneStrategy.listPane metadata stays absent in Bead D; that
+ the Settings stub land together in Bead F.

Refs: nubecita-s6p.4"
```

---

## Task 14: Remove `:app`'s Profile placeholder; keep Settings

**Files:**
- Modify: `app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt`

- [ ] **Step 1: Edit the file — split Profile out of the combined provider**

Replace `provideProfilePlaceholderEntries` with a Settings-only `provideSettingsPlaceholderEntries`:

Find and remove these lines (the combined Profile + Settings provider):

```kotlin
    @Provides
    @IntoSet
    @MainShell
    fun provideProfilePlaceholderEntries(): EntryProviderInstaller =
        {
            entry<Profile> { _ ->
                // Both `Profile(null)` (You-tab home) and `Profile(handle = "alice")`
                // (cross-tab navigation push) render the same placeholder. The real
                // :feature:profile:impl will use the handle param to load the right
                // profile.
                PlaceholderScreen(label = stringResource(R.string.main_shell_tab_you))
            }
            entry<Settings> {
                PlaceholderScreen(label = stringResource(R.string.main_shell_settings))
            }
        }
```

Replace with a Settings-only provider:

```kotlin
    @Provides
    @IntoSet
    @MainShell
    fun provideSettingsPlaceholderEntries(): EntryProviderInstaller =
        {
            // Settings stub lands in Bead F (:feature:profile:impl will
            // own the real entry). Until then, the placeholder stays
            // here.
            entry<Settings> {
                PlaceholderScreen(label = stringResource(R.string.main_shell_settings))
            }
        }
```

Also remove the now-unused `Profile` import at the top of the file:

```kotlin
import net.kikin.nubecita.feature.profile.api.Profile  // DELETE THIS LINE
```

- [ ] **Step 2: Verify the build picks up the change**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Hilt's `@MainShell @IntoSet` multibinding now resolves `Profile` through `:feature:profile:impl`'s provider (added in Task 13) instead of the deleted `:app` placeholder.

- [ ] **Step 3: Run the existing `:app` androidTest suite to verify the route reference still resolves**

```bash
# The MainShellPersistenceTest uses Profile(handle = null) as a top-level
# route. That reference is to the NavKey type in :feature:profile:api —
# unaffected by the provider move. But the test calls a route into the
# inner NavDisplay, so the new provider needs to resolve.
./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL (we're not running the test — that needs an emulator. Just verifying the test compiles).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt
git commit -m "chore(app/shell): remove Profile placeholder; :feature:profile:impl owns it

Profile(handle = null) and Profile(handle = \"...\") now resolve through
the new :feature:profile:impl @MainShell provider. Settings stays in
:app's placeholder module until Bead F wires the stub Composable.

Refs: nubecita-s6p.4"
```

---

## Task 15: Instrumentation test — Edit-tap → Coming Soon snackbar

**Files:**
- Create: `feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenInstrumentationTest.kt`

- [ ] **Step 1: Verify the androidTest source set is configured**

```bash
ls feature/profile/impl/src/androidTest 2>&1 || echo "MISSING"
```

If the directory doesn't exist, create it:

```bash
mkdir -p feature/profile/impl/src/androidTest/kotlin/net/kikin/nubecita/feature/profile/impl
```

Also check that the Bead-C `build.gradle.kts` has the androidTest dependencies. If `androidTestImplementation(libs.androidx.compose.ui.test.junit4)` is missing, add it:

```bash
grep "androidTestImplementation" feature/profile/impl/build.gradle.kts
```

If empty, append this dependency block to `feature/profile/impl/build.gradle.kts` after the existing `testImplementation` lines:

```kotlin
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
```

(Verify the exact lib aliases by grepping the same set in `feature/composer/impl/build.gradle.kts` — the composer module is the established precedent.)

- [ ] **Step 2: Create the instrumentation test**

```kotlin
package net.kikin.nubecita.feature.profile.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import org.junit.Rule
import org.junit.Test

/**
 * Smoke instrumentation test: tapping Edit on the actions row surfaces
 * the Coming Soon snackbar. Renders ProfileScreenContent directly with
 * a hand-built ProfileScreenViewState — no ViewModel, no Hilt graph,
 * no real network. The Edit tap dispatches a ProfileEvent.EditTapped
 * which the test interprets by manually showing the same snackbar copy
 * via the captured onEvent lambda; this exercises the same code path
 * the screen uses (the host's onEvent → snackbar mapping is in
 * ProfileScreen, not ProfileScreenContent — so the test asserts the
 * actions-row click delivers an EditTapped event AND that the snackbar
 * host renders the message when the test calls showSnackbar).
 *
 * Per the run-instrumented label rule (memory feedback_run_instrumented_label_on_androidtest_prs),
 * the PR must carry the run-instrumented label to fire CI's instrumented job.
 */
class ProfileScreenInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editTap_surfacesComingSoonSnackbar() {
        val editLabel = "Edit profile"
        val expectedSnackbarText = "Edit profile — coming soon"
        val capturedEvents = mutableListOf<ProfileEvent>()

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                val onEvent: (ProfileEvent) -> Unit = { event ->
                    capturedEvents += event
                    if (event is ProfileEvent.EditTapped) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(expectedSnackbarText)
                        }
                    }
                }
                ProfileScreenContent(
                    state = sampleOwnProfileState(),
                    listState = rememberLazyListState(),
                    snackbarHostState = snackbarHostState,
                    postCallbacks = PostCallbacks.None,
                    onEvent = onEvent,
                )
            }
        }

        composeTestRule.onNodeWithText(editLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(expectedSnackbarText).assertIsDisplayed()
        assert(capturedEvents.contains(ProfileEvent.EditTapped)) {
            "Edit tap MUST dispatch ProfileEvent.EditTapped; captured: $capturedEvents"
        }
    }

    private fun sampleOwnProfileState(): ProfileScreenViewState =
        ProfileScreenViewState(
            handle = null,
            header = ProfileHeaderUi(
                did = "did:plc:alice",
                handle = "alice.bsky.social",
                displayName = "Alice",
                avatarUrl = null,
                bannerUrl = null,
                avatarHue = 217,
                bio = null,
                location = null,
                website = null,
                joinedDisplay = "Joined April 2023",
                postsCount = 0L,
                followersCount = 0L,
                followsCount = 0L,
            ),
            ownProfile = true,
            viewerRelationship = ViewerRelationship.Self,
            selectedTab = ProfileTab.Posts,
            postsStatus = TabLoadStatus.Loaded(
                items = persistentListOf(),
                isAppending = false,
                isRefreshing = false,
                hasMore = false,
                cursor = null,
            ),
            repliesStatus = TabLoadStatus.Idle,
            mediaStatus = TabLoadStatus.Idle,
        )
}
```

If `kotlinx.coroutines.launch` is not on the androidTest classpath (unlikely — the `:core:testing-android` test deps wire it transitively), add `androidTestImplementation(libs.kotlinx.coroutines.android)` to `feature/profile/impl/build.gradle.kts` and re-compile.

- [ ] **Step 3: Compile the androidTest source set**

```bash
./gradlew :feature:profile:impl:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. (We're not running on a real device here — that runs in CI under the `run-instrumented` label.)

- [ ] **Step 4: Run the test locally on a connected device or emulator**

Per the `feedback_use_android_cli_for_emulators` memory, use `android emulator` to manage emulators:

```bash
# Check for a connected device first
adb devices

# If empty, list available AVDs
android emulator list
# Start one if needed
android emulator start <avd-name>

# Run the test
./gradlew :feature:profile:impl:connectedDebugAndroidTest
```

Expected: 1 test passes (the `editTap_surfacesComingSoonSnackbar`).

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/androidTest/ feature/profile/impl/build.gradle.kts
git commit -m "test(feature/profile/impl): instrumented Edit-tap → Coming Soon snackbar

Smoke test using createAndroidComposeRule<ComponentActivity>(): renders
ProfileScreenContent with a hand-built ProfileScreenViewState, taps
Edit, asserts the captured event includes EditTapped and the snackbar
host renders the expected message.

Refs: nubecita-s6p.4"
```

---

## Task 16: Open the PR with the `run-instrumented` label

**Files:** (no file changes — git only)

- [ ] **Step 1: Run the full local verification**

```bash
./gradlew \
  :feature:profile:impl:assembleDebug \
  :feature:profile:impl:testDebugUnitTest \
  :feature:profile:impl:validateDebugScreenshotTest \
  :app:assembleDebug \
  spotlessCheck lint :app:checkSortDependencies
```

Expected: BUILD SUCCESSFUL across the board.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/nubecita-s6p.4-feature-profile-impl-own-profile-screen-posts-tab
```

- [ ] **Step 3: Open the PR**

```bash
gh pr create --base main --title "feat(feature/profile/impl): own-profile screen + Posts tab body" --body "$(cat <<'EOF'
## Summary

- Ship the own-profile variant of the Profile screen with the Posts tab fully functional (load + paginate + refresh + empty/error states).
- Replies / Media tabs render a themed "arrives soon" placeholder; their real bodies ship in Bead E.
- Replace the `:app`-side `Profile(handle = null)` placeholder with `:feature:profile:impl`'s `@MainShell` entry provider. Settings placeholder stays in `:app` until Bead F.
- Adds `ProfileEvent.RetryTab(tab)` to the Bead C contract (additive) so the per-tab inline error state has a retry affordance.

Design doc: `docs/superpowers/specs/2026-05-12-profile-bead-d-design.md`

## Test plan

- [ ] `./gradlew :feature:profile:impl:testDebugUnitTest` — VM unit tests (including the new RetryTab test) pass.
- [ ] `./gradlew :feature:profile:impl:validateDebugScreenshotTest` — 16 screenshot baselines validate.
- [ ] CI's instrumented job runs `ProfileScreenInstrumentationTest` on Pixel 6 API 35 (gated by the `run-instrumented` label).
- [ ] `./gradlew :app:assembleDebug spotlessCheck lint` — green.
- [ ] Manually launch on a real device: open the You tab, verify the profile loads with the bold gradient hero, scroll through Posts, tap Edit → see the Coming Soon snackbar, tap Replies / Media → see the "arrives soon" placeholder.

Closes: nubecita-s6p.4
EOF
)"
```

- [ ] **Step 4: Add the `run-instrumented` label**

```bash
gh pr edit --add-label run-instrumented
```

Per the `feedback_run_instrumented_label_on_androidtest_prs` memory, this is required for any PR touching `*/src/androidTest/**` — CI's instrumented job is skipped without it.

- [ ] **Step 5: Verify the PR**

```bash
gh pr view --json number,url,labels,statusCheckRollup
```

Confirm:
- The PR URL is reported.
- `run-instrumented` is among the labels.
- The CI checks start running.

---

## Self-review checklist

Performed by the plan author before handoff:

**Spec coverage** — every requirement in `feature-profile/spec.md` traces to a task above:

| Spec requirement | Task |
|---|---|
| `:feature:profile:impl` resolves Profile + Settings NavKeys | Task 13 (Profile) + Task 14 (Settings stays in :app) |
| `ProfileScreenViewState` flat + per-tab sealed (Bead C) | Already shipped in Bead C |
| Hero bold-derived gradient (Bead B's `BoldHeroGradient`) | Task 9 |
| Inline stats + meta row (no chip variant) | Tasks 4 + 5 |
| Actions row stubs emit `ShowComingSoon` | Tasks 6 + 11 (effect collector) |
| Three pill tabs via `ProfilePillTabs`, sticky during scroll | Tasks 3 + 10 (stickyHeader) |
| Posts body LazyColumn with PostCards | Task 8 |
| Post tap → `NavigateToPost` (no listpane in Bead D) | Tasks 11 + 13 |
| Handle tap → cross-profile or self-tap no-op | Bead C VM handles the no-op; Tasks 11 + 13 route the effect |
| Settings stub Sign Out | Bead F |
| Screenshot-test contract | Tasks 4, 5, 6, 7, 12 |

**Placeholder scan** — no "TBD", "TODO", "implement later", or "similar to Task N" in step bodies. Every step contains complete code or a complete command.

**Type consistency** —
- `ProfileEvent.RetryTab(val tab: ProfileTab)` used identically across Tasks 1 (definition), 8 (consumed in `profilePostsTabBody`'s `onRetry`), and 10 (dispatched in `ProfileScreenContent`).
- `PostCallbacks` constructor — Task 11 declares the lambdas; Task 8's `profilePostsTabBody` passes a `PostCallbacks` parameter; the screenshot-test fixture in Task 12 uses `PostCallbacks.None`. Caller / callee aligned.
- `rememberProfilePillTabs()` returns `ImmutableList<PillTab<ProfileTab>>` (Task 3) — consumed by `ProfilePillTabs(tabs = pillTabs, selectedValue = state.selectedTab, …)` in Task 10. Matches the `:designsystem.ProfilePillTabs` signature.
- `ProfileScreenContent`'s signature in Task 10 is matched by Task 11's call site and Task 12's screenshot host. Identical parameter ordering.

**Scope check** — 16 tasks, each producing a clean commit on the `feat/nubecita-s6p.4-…` branch. Total scope matches the bead's design doc; nothing is deferred beyond what the design doc already defers to Bead E / F / follow-up epics.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-12-profile-bead-d-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
