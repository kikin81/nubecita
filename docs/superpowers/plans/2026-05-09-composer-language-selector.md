# Composer Language Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-post language selector to the composer via an `AssistChip` (leading globe icon + dynamic label) that opens a checkbox-list picker, capped at 3 BCP-47 tags, with the device-locale fallback when the user does not override.

**Architecture:** `ComposerState` gains `selectedLangs: List<String>? = null`. The composer screen adds a `ComposerOptionsChipRow` between the text field and attachment row, hosting a `ComposerLanguageChip`. Tapping the chip opens an adaptive picker — `ModalBottomSheet` at Compact width, `Popup` over an M3 `Surface` at Medium / Expanded — built from a stateless `LanguagePickerContent`. `ComposerViewModel` injects the `LocaleProvider` from `wtq.12` and exposes `deviceLocaleTag: String` so the chip and the picker render consistent fallback labels. Submit passes `state.selectedLangs` directly to `PostingRepository.createPost(langs = ...)`; nullable semantics keep the `wtq.12` repo-side default reachable.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt assisted injection, JUnit5 + `UnconfinedTestDispatcher` + `assertk` for VM tests, `androidx.compose.ui.test.junit4.createComposeRule()` for Compose UI tests, `@PreviewNubecitaScreenPreviews` (from PR #145) for screenshot fixtures.

**Prerequisites:** PR #146 (`nubecita-wtq.12` — device-locale `langs` default) MUST be merged to main before starting. This plan depends on:
- `net.kikin.nubecita.core.posting.internal.LocaleProvider` interface
- `net.kikin.nubecita.core.posting.internal.JvmLocaleProvider` Hilt binding
- `langs: List<String>?` parameter on `PostingRepository.createPost`

If `git log --oneline --all | grep nubecita-wtq.12` does not show the merged commit on main, stop and merge #146 first.

**Branch:** Continue work on the `docs/composer-language-selector-design` branch (which carries the design doc this plan implements). Rebase onto main after #146 merges.

---

## File structure

| Path | Action | Responsibility |
|---|---|---|
| `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/BlueskyLanguageTags.kt` | Create | Verbatim port of bsky-app's `LANGUAGES` constant — the canonical list of selectable BCP-47 tags. |
| `core/posting/src/test/kotlin/net/kikin/nubecita/core/posting/BlueskyLanguageTagsTest.kt` | Create | Smoke-test the list — non-empty, immutable, every entry round-trips through `Locale.forLanguageTag`. |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/ComposerState.kt` | Modify | Add `selectedLangs: List<String>? = null` field. |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/ComposerEvent.kt` | Modify | Add `LanguageSelectionConfirmed(tags: List<String>)` variant. |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt` | Modify | Inject `LocaleProvider`, expose `deviceLocaleTag: String`, reducer for `LanguageSelectionConfirmed` with cap-of-3 guard, pass `state.selectedLangs` to `createPost`. |
| `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt` | Modify | Add tests for the reducer + the Submit pipeline. Update `newViewModel(...)` helper to inject a `fixedLocaleProvider`. |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerLanguageChip.kt` | Create | Stateless `@Composable AssistChip` — leading globe icon + dynamic label per the design doc's three label states. |
| `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerLanguageChipTest.kt` | Create | Compose UI tests for the three label states + tap fires `onClick`. |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerOptionsChipRow.kt` | Create | Horizontal row container hosting the language chip (and future composer chips). |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePickerContent.kt` | Create | Stateless `@Composable` — search field + `LazyColumn` of language rows + `Done`/`Cancel` footer. The picker's *content*, independent of how it's wrapped. |
| `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePickerContentTest.kt` | Create | Compose UI tests for search filtering, cap-of-3 disabled-checkbox state, Done/Cancel dispatching. |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePicker.kt` | Create | Adaptive wrapper — `ModalBottomSheet` at Compact, `Popup`-over-`Surface` at Medium/Expanded. Calls `LanguagePickerContent` inside both branches. |
| `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreen.kt` | Modify | Render `ComposerOptionsChipRow` between text field and attachment row; host the picker behind a `var showPicker by rememberSaveable { mutableStateOf(false) }`. |
| `feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreenLanguageChipScreenshotTest.kt` | Create | Screenshot fixtures for the chip's three label states using `@PreviewNubecitaScreenPreviews`. |
| `feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/LanguagePickerScreenshotTest.kt` | Create | Screenshot fixtures for picker-open at all width classes + picker-with-cap-reached. |
| `openspec/specs/feature-composer/spec.md` | Modify | New requirement: "Composer language chip exposes a per-post BCP-47 override" with 7 scenarios. |

---

## Task 1: Port the Bluesky language tag list

**Files:**
- Create: `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/BlueskyLanguageTags.kt`
- Test: `core/posting/src/test/kotlin/net/kikin/nubecita/core/posting/BlueskyLanguageTagsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package net.kikin.nubecita.core.posting

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlueskyLanguageTagsTest {
    @Test
    fun list_isNonEmptyAndContainsCommonTags() {
        assertTrue(BLUESKY_LANGUAGE_TAGS.isNotEmpty(), "BLUESKY_LANGUAGE_TAGS must not be empty")
        // Smoke-check a handful of common tags. Don't enumerate the full
        // list — that's brittle and the upstream source authoritative.
        listOf("en", "es", "ja", "fr", "de", "pt").forEach { tag ->
            assertTrue(BLUESKY_LANGUAGE_TAGS.contains(tag), "Expected $tag in BLUESKY_LANGUAGE_TAGS")
        }
    }

    @Test
    fun every_tag_roundTripsThroughLocale() {
        // Every entry must be a syntactically valid BCP-47 tag — same
        // validity check the PostingRepository uses on caller-supplied
        // langs. Anything that resolves to "und" would silently drop at
        // submit time and is a porting error.
        BLUESKY_LANGUAGE_TAGS.forEach { tag ->
            val roundTripped = Locale.forLanguageTag(tag).toLanguageTag()
            assertEquals(
                false,
                roundTripped == "und",
                "Tag '$tag' does not parse as BCP-47 (got 'und' from Locale.forLanguageTag)",
            )
        }
    }

    @Test
    fun list_isImmutable() {
        assertTrue(
            BLUESKY_LANGUAGE_TAGS is kotlinx.collections.immutable.ImmutableList<*>,
            "BLUESKY_LANGUAGE_TAGS must be an ImmutableList for Compose stability",
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:posting:testDebugUnitTest --tests "net.kikin.nubecita.core.posting.BlueskyLanguageTagsTest"`

Expected: FAIL with `Unresolved reference: BLUESKY_LANGUAGE_TAGS` (file doesn't exist).

- [ ] **Step 3: Port the list from bsky-app**

Open the bsky-app source: <https://github.com/bluesky-social/social-app/blob/main/src/locale/languages.ts>. Copy each entry's `code2` (or `code3` if `code2` is empty) field — these are the BCP-47 tags. As of this writing the list is ~75 entries.

Create `core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/BlueskyLanguageTags.kt`:

```kotlin
package net.kikin.nubecita.core.posting

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * BCP-47 language tags shown by Nubecita's per-post language picker —
 * a verbatim port of `bsky-app`'s `LANGUAGES` constant
 * (https://github.com/bluesky-social/social-app/blob/main/src/locale/languages.ts).
 *
 * Mirroring upstream gives Nubecita parity with what users see in the
 * official Bluesky client. Resync with upstream when their list grows
 * or shrinks (rare in practice — once a year tops).
 *
 * Display names are NOT shipped here. UI consumers render each tag via
 * `Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())` so
 * users on, say, a French phone see "Anglais" instead of "English"
 * without us shipping translation tables.
 */
val BLUESKY_LANGUAGE_TAGS: ImmutableList<String> =
    persistentListOf(
        "ar", "ca", "zh", "cs", "nl", "en", "fi", "fr", "de", "el",
        "he", "hi", "hu", "id", "it", "ja", "ko", "ms", "no", "pl",
        "pt", "ro", "ru", "es", "sv", "tr", "uk", "vi",
        // ...continue verbatim from upstream LANGUAGES.code2 entries.
        // The list above is illustrative — the implementer must replace
        // it with the full upstream list in this commit.
    )
```

The illustrative list above must be replaced with the full upstream entries before committing. The smoke test in step 1 validates the list shape without pinning exact contents.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:posting:testDebugUnitTest --tests "net.kikin.nubecita.core.posting.BlueskyLanguageTagsTest"`

Expected: PASS, all 3 tests.

- [ ] **Step 5: Commit**

```bash
git add core/posting/src/main/kotlin/net/kikin/nubecita/core/posting/BlueskyLanguageTags.kt \
        core/posting/src/test/kotlin/net/kikin/nubecita/core/posting/BlueskyLanguageTagsTest.kt
git commit -m "feat(core/posting): port Bluesky LANGUAGES list as BLUESKY_LANGUAGE_TAGS

Verbatim port of bsky-app's LANGUAGES constant — the canonical list of
BCP-47 tags Nubecita's per-post language picker offers. Mirroring
upstream keeps parity with what users see in the official Bluesky
client.

Smoke-tested for non-empty, common-tag presence, and BCP-47 validity
(every entry round-trips through Locale.forLanguageTag without
resolving to 'und').

Refs: nubecita-oae"
```

---

## Task 2: Add `selectedLangs` field to `ComposerState`

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/ComposerState.kt`
- Test: `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Find the existing `initialState_inNewPostMode_*` test (or equivalent name) in `ComposerViewModelTest.kt` and add a new sibling test next to it:

```kotlin
@Test
fun initialState_hasNullSelectedLangs() = runTest {
    val vm = newViewModel(replyToUri = null)
    assertThat(vm.state.value.selectedLangs).isNull()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerViewModelTest.initialState_hasNullSelectedLangs"`

Expected: FAIL with `Unresolved reference: selectedLangs`.

- [ ] **Step 3: Add the field**

In `state/ComposerState.kt`, add `selectedLangs` to the `data class ComposerState(...)` declaration. Place it after `replyParentLoad` and before `submitStatus` so the field order roughly matches the temporal flow (route → text → attachments → reply → langs → submit):

```kotlin
data class ComposerState(
    // ...existing fields...
    val replyToUri: String? = null,
    val replyParentLoad: ParentLoadStatus? = null,
    val selectedLangs: List<String>? = null,
    val submitStatus: ComposerSubmitStatus = ComposerSubmitStatus.Idle,
    val typeahead: TypeaheadStatus = TypeaheadStatus.Idle,
    // ...existing fields...
) : UiState
```

Add a KDoc comment above the field:

```kotlin
    /**
     * Per-post BCP-47 language tags chosen by the user via the
     * language chip + picker. `null` means "user has not touched the
     * picker; let `PostingRepository.createPost` derive the device-
     * locale default per `nubecita-wtq.12`'s contract." A non-null
     * list (including `emptyList()`) is an explicit caller override
     * passed through verbatim.
     */
    val selectedLangs: List<String>? = null,
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerViewModelTest.initialState_hasNullSelectedLangs"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/ComposerState.kt \
        feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt
git commit -m "feat(feature/composer): add ComposerState.selectedLangs field

Nullable List<String>; null means use repo's device-locale default,
non-null is an explicit per-post override passed through to createPost.

Refs: nubecita-oae"
```

---

## Task 3: Add `LanguageSelectionConfirmed` event

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/ComposerEvent.kt`

- [ ] **Step 1: Write the failing test**

Add to `ComposerViewModelTest.kt`:

```kotlin
@Test
fun languageSelectionConfirmed_event_isAComposerEventVariant() {
    // Compile-time check: this test fails to compile if the variant
    // doesn't exist. Smoke-test for the event surface.
    val event: ComposerEvent = ComposerEvent.LanguageSelectionConfirmed(tags = listOf("en"))
    assertThat(event).isInstanceOf(ComposerEvent.LanguageSelectionConfirmed::class)
    assertThat((event as ComposerEvent.LanguageSelectionConfirmed).tags).containsExactly("en")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:composer:impl:compileDebugUnitTestKotlin`

Expected: FAIL with `Unresolved reference: LanguageSelectionConfirmed`.

- [ ] **Step 3: Add the event variant**

Edit `state/ComposerEvent.kt`. Add inside the `sealed interface ComposerEvent : UiEvent { ... }` block, near the end (before any `companion object` if present):

```kotlin
    /**
     * Dispatched by the language picker when the user taps `Done` after
     * choosing 0..3 BCP-47 tags. The reducer assigns `tags` to
     * `ComposerState.selectedLangs`, defensively no-op'ing when
     * `tags.size > 3` (the picker UI also enforces the cap by
     * disabling unchecked checkboxes once 3 are selected).
     *
     * `tags = emptyList()` is honored — it means "user explicitly
     * cleared all languages", surfaced to `PostingRepository.createPost`
     * as `langs = emptyList()`, which the repository serializes as the
     * `langs` field omitted entirely (per `nubecita-wtq.12`'s contract).
     */
    data class LanguageSelectionConfirmed(val tags: List<String>) : ComposerEvent
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerViewModelTest.languageSelectionConfirmed_event_isAComposerEventVariant"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/state/ComposerEvent.kt \
        feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt
git commit -m "feat(feature/composer): add LanguageSelectionConfirmed event

Dispatched by the picker when the user confirms a 0..3-tag selection.
Reducer + Submit wiring lands in subsequent commits.

Refs: nubecita-oae"
```

---

## Task 4: Inject `LocaleProvider` into `ComposerViewModel` and expose `deviceLocaleTag`

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt`
- Modify: `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `ComposerViewModelTest.kt`:

```kotlin
@Test
fun deviceLocaleTag_reflectsInjectedLocaleProvider() = runTest {
    val vm = newViewModel(replyToUri = null, deviceLocaleTag = "ja-JP")
    assertThat(vm.deviceLocaleTag).isEqualTo("ja-JP")
}
```

The `deviceLocaleTag = "ja-JP"` parameter on `newViewModel(...)` is new — Step 3 wires the helper to accept it.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:composer:impl:compileDebugUnitTestKotlin`

Expected: FAIL with `Cannot find a parameter with this name: deviceLocaleTag` (or `Unresolved reference: deviceLocaleTag` against `vm`).

- [ ] **Step 3: Update VM constructor + helper**

In `ComposerViewModel.kt`:

1. Add the import:

```kotlin
import net.kikin.nubecita.core.posting.internal.LocaleProvider
```

(`LocaleProvider` is `internal` to `:core:posting` but its enclosing module is the only place that ships an implementation — Hilt resolves the binding cross-module through the module's `@InstallIn(SingletonComponent::class)` declaration. The visibility narrowing is fine because consumers don't construct it directly; Hilt does.)

2. Append `localeProvider: LocaleProvider` to the `@Inject`-resolved constructor parameter list, after `actorTypeaheadRepository` (the existing last parameter). Per the unified-composer spec's *"Constructor seam is append-only"* requirement, never reorder existing parameters.

3. Inside the class body, expose:

```kotlin
    /**
     * BCP-47 tag for the device's primary locale, captured once at VM
     * construction. The language chip and the language picker both
     * read this for fallback display when [ComposerState.selectedLangs]
     * is `null` — keeping a single VM-level read of [LocaleProvider]
     * means screen-level UI tests can configure the value through a
     * fake `LocaleProvider` (mirroring the unit-test pattern in
     * `DefaultPostingRepositoryTest`) instead of fighting the JVM's
     * `Locale.getDefault()`.
     */
    val deviceLocaleTag: String = localeProvider.primaryLanguageTag()
```

In `ComposerViewModelTest.kt`, update the `newViewModel(...)` helper signature:

```kotlin
private fun newViewModel(
    replyToUri: String? = null,
    postingRepository: PostingRepository = fakeRepository(),
    parentFetchSource: ParentFetchSource = fakeParentFetchSource(),
    actorTypeaheadRepository: ActorTypeaheadRepository = fakeActorTypeaheadRepository(),
    deviceLocaleTag: String = "en-US",
): ComposerViewModel = ComposerViewModel(
    route = ComposerRoute(replyToUri = replyToUri),
    postingRepository = postingRepository,
    parentFetchSource = parentFetchSource,
    actorTypeaheadRepository = actorTypeaheadRepository,
    localeProvider = fixedLocaleProvider(deviceLocaleTag),
)

private fun fixedLocaleProvider(tag: String): LocaleProvider =
    object : LocaleProvider {
        override fun primaryLanguageTag(): String = tag
    }
```

(If `newViewModel(...)` doesn't already exist as a private helper in this test file, fold its current direct-construction sites into one. The construction surface should be a single helper for forward compatibility.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest`

Expected: PASS — including pre-existing tests that exercise the VM. No regression.

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt \
        feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt
git commit -m "feat(feature/composer): inject LocaleProvider, expose deviceLocaleTag

Append-only constructor extension per the unified-composer spec's seam
requirement. The chip and the picker read deviceLocaleTag instead of
each pulling LocaleProvider directly — single VM-level read site.

Refs: nubecita-oae"
```

---

## Task 5: Reducer for `LanguageSelectionConfirmed` with cap-of-3 guard

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt`
- Modify: `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `ComposerViewModelTest.kt`:

```kotlin
@Test
fun languageSelectionConfirmed_singleTag_updatesState() = runTest {
    val vm = newViewModel(replyToUri = null)
    vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = listOf("ja-JP")))
    assertThat(vm.state.value.selectedLangs).containsExactly("ja-JP")
}

@Test
fun languageSelectionConfirmed_multipleTags_updatesState() = runTest {
    val vm = newViewModel(replyToUri = null)
    vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = listOf("ja-JP", "en-US", "es-MX")))
    assertThat(vm.state.value.selectedLangs).containsExactly("ja-JP", "en-US", "es-MX")
}

@Test
fun languageSelectionConfirmed_emptyList_isHonored() = runTest {
    // Explicit empty != null. Caller is saying "I want no langs on
    // this post"; reducer must honor that distinct from the no-touch
    // null state.
    val vm = newViewModel(replyToUri = null)
    vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = emptyList()))
    assertThat(vm.state.value.selectedLangs).isNotNull().isEmpty()
}

@Test
fun languageSelectionConfirmed_overCap_isNoOp() = runTest {
    // The picker UI defends with disabled checkboxes; the reducer
    // defends defensively for any caller that bypasses the UI or sends
    // a malformed event.
    val vm = newViewModel(replyToUri = null)
    val before = vm.state.value.selectedLangs
    vm.handleEvent(
        ComposerEvent.LanguageSelectionConfirmed(tags = listOf("a", "b", "c", "d")),
    )
    assertThat(vm.state.value.selectedLangs).isEqualTo(before)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerViewModelTest.languageSelectionConfirmed*"`

Expected: 4 FAILs — `state.selectedLangs` doesn't change because the reducer has no case for the new event yet.

- [ ] **Step 3: Add the reducer case**

In `ComposerViewModel.kt`, find the `handleEvent` method's `when (event)` block. Add a branch:

```kotlin
            is ComposerEvent.LanguageSelectionConfirmed -> {
                if (event.tags.size > MAX_LANGS) {
                    // Defensive guard. The picker UI also enforces the
                    // cap by rendering unchecked checkboxes as
                    // `enabled = false` once 3 are selected. This
                    // catches programmatic dispatch (tests, future
                    // automation) that bypasses the UI.
                    Timber.tag(TAG).w("LanguageSelectionConfirmed over cap (%d > %d) — ignoring", event.tags.size, MAX_LANGS)
                    return
                }
                setState { copy(selectedLangs = event.tags) }
            }
```

Inside the class's `companion object`, add the constant:

```kotlin
        private const val MAX_LANGS = 3
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerViewModelTest.languageSelectionConfirmed*"`

Expected: PASS, all 4.

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt \
        feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt
git commit -m "feat(feature/composer): reduce LanguageSelectionConfirmed with cap-of-3 guard

Explicit empty-list selection honored as a distinct override (not a
fallback to device locale). Over-cap dispatches no-op as defense
against programmatic callers bypassing the picker UI.

Refs: nubecita-oae"
```

---

## Task 6: Submit pipeline passes `state.selectedLangs` to `createPost`

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt`
- Modify: `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

The existing `ComposerViewModelTest` likely uses a `fakeRepository()` that records `createPost` invocations. Extend the fake to record the `langs` argument, then add tests:

```kotlin
@Test
fun submit_withNullSelectedLangs_callsCreatePostWithNullLangs() = runTest {
    val recorded = AtomicReference<List<String>?>(MISSING)  // sentinel for "never called"
    val repo = recordingRepository { langs -> recorded.set(langs) }
    val vm = newViewModel(replyToUri = null, postingRepository = repo)
    vm.textFieldState.edit { replace(0, 0, "hello") }
    Snapshot.sendApplyNotifications()
    testScheduler.runCurrent()
    vm.handleEvent(ComposerEvent.Submit)
    testScheduler.advanceUntilIdle()
    assertThat(recorded.get()).isNull()
}

@Test
fun submit_withExplicitSelectedLangs_callsCreatePostWithThoseLangs() = runTest {
    val recorded = AtomicReference<List<String>?>(MISSING)
    val repo = recordingRepository { langs -> recorded.set(langs) }
    val vm = newViewModel(replyToUri = null, postingRepository = repo)
    vm.textFieldState.edit { replace(0, 0, "konnichiwa") }
    Snapshot.sendApplyNotifications()
    testScheduler.runCurrent()
    vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = listOf("ja-JP", "en-US")))
    vm.handleEvent(ComposerEvent.Submit)
    testScheduler.advanceUntilIdle()
    assertThat(recorded.get()).containsExactly("ja-JP", "en-US")
}

private val MISSING = listOf("__never_called_sentinel__")

private fun recordingRepository(captureLangs: (List<String>?) -> Unit): PostingRepository =
    object : PostingRepository {
        override suspend fun createPost(
            text: String,
            attachments: List<ComposerAttachment>,
            replyTo: ReplyRefs?,
            langs: List<String>?,
        ): Result<AtUri> {
            captureLangs(langs)
            return Result.success(AtUri("at://did:plc:test/app.bsky.feed.post/abc"))
        }
    }
```

The `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` calls are required because `ComposerViewModel` observes `textFieldState.text` via `snapshotFlow` — without those two manual ticks the VM doesn't see the test's text edit (per the `feedback_compose_snapshot_in_unit_tests` memory in `MEMORY.md`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerViewModelTest.submit_with*SelectedLangs*"`

Expected: FAIL — the test's `recorded` value mismatches because `createPost`'s call site doesn't yet pass `state.selectedLangs`. (Compile may also fail if the existing `fakeRepository()` doesn't accept the new `langs` parameter on `createPost`; if so the override now satisfies the contract for the new tests.)

- [ ] **Step 3: Wire the Submit pipeline**

In `ComposerViewModel.kt`, find the call site that invokes `postingRepository.createPost(...)` (likely inside the `Submit` event handler). Add the `langs` argument:

```kotlin
            postingRepository.createPost(
                text = textFieldState.text.toString(),
                attachments = state.value.attachments,
                replyTo = (state.value.replyParentLoad as? ParentLoadStatus.Loaded)?.toReplyRefs(),
                langs = state.value.selectedLangs,
            )
```

Match the existing argument names + indentation. The new `langs = state.value.selectedLangs` is the only new line.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest`

Expected: PASS — both new tests + every pre-existing test (no regressions).

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModel.kt \
        feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerViewModelTest.kt
git commit -m "feat(feature/composer): pass state.selectedLangs through to createPost

Submit pipeline now plumbs the override straight from ComposerState to
PostingRepository. null preserves the wtq.12 device-locale default;
non-null is verbatim explicit override.

Refs: nubecita-oae"
```

---

## Task 7: `ComposerLanguageChip` composable + Compose UI tests

**Files:**
- Create: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerLanguageChip.kt`
- Create: `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerLanguageChipTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `ComposerLanguageChipTest.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ComposerLanguageChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nullSelection_showsDeviceLocaleDisplayName() {
        composeRule.setContent {
            ComposerLanguageChip(
                selectedLangs = null,
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
        composeRule.onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun singleSelection_showsThatLangsDisplayName() {
        composeRule.setContent {
            ComposerLanguageChip(
                selectedLangs = listOf("ja-JP"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
        composeRule.onNodeWithText("Japanese").assertIsDisplayed()
    }

    @Test
    fun multiSelection_showsFirstNamePlusOverflowCount() {
        composeRule.setContent {
            ComposerLanguageChip(
                selectedLangs = listOf("en-US", "ja-JP", "es-MX"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
        composeRule.onNodeWithText("English +2").assertIsDisplayed()
    }

    @Test
    fun tap_invokesOnClick() {
        var clickCount = 0
        composeRule.setContent {
            ComposerLanguageChip(
                selectedLangs = null,
                deviceLocaleTag = "en-US",
                onClick = { clickCount++ },
            )
        }
        composeRule.onNodeWithText("English").performClick()
        assertTrue(clickCount == 1, "Expected 1 click, got $clickCount")
    }
}
```

These tests pin `deviceLocaleTag = "en-US"` so display-name rendering is deterministic regardless of the runner's `Locale.getDefault()`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerLanguageChipTest*"`

Expected: FAIL with `Unresolved reference: ComposerLanguageChip`.

- [ ] **Step 3: Implement the composable**

Create `internal/ComposerLanguageChip.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.widthIn
import java.util.Locale

/**
 * Tap target for the per-post language picker. Shows the localized
 * display name of either the user's explicit selection
 * ([selectedLangs] non-null) or the device-locale fallback
 * ([deviceLocaleTag], used when [selectedLangs] is null).
 *
 * Multi-selection (>= 2 tags) shows the first selected tag's display
 * name plus a `+N` overflow ("English +1", "English +2") — predictable
 * chip width regardless of how many tags are selected. The full list
 * is revealed when the user re-opens the picker.
 *
 * The 200dp width cap defends against pathological display names
 * (`"Norwegian Bokmål"` and similar). Beyond the cap the label
 * truncates with `…`; the user's full selection is still in
 * [selectedLangs] and visible the next time the picker opens.
 */
@Composable
internal fun ComposerLanguageChip(
    selectedLangs: List<String>?,
    deviceLocaleTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = remember(selectedLangs, deviceLocaleTag) {
        chipLabelFor(selectedLangs, deviceLocaleTag)
    }
    AssistChip(
        onClick = onClick,
        modifier = modifier.widthIn(max = 200.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        label = {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

private fun chipLabelFor(selectedLangs: List<String>?, deviceLocaleTag: String): String {
    if (selectedLangs.isNullOrEmpty()) {
        return displayName(deviceLocaleTag)
    }
    val first = displayName(selectedLangs.first())
    return when (val extras = selectedLangs.size - 1) {
        0 -> first
        else -> "$first +$extras"
    }
}

private fun displayName(tag: String): String =
    Locale.forLanguageTag(tag)
        .getDisplayName(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*ComposerLanguageChipTest*"`

Expected: PASS, all 4 tests.

If the runner's `Locale.getDefault()` is not `en-US` (e.g., a CI machine set to French), the tests' string comparisons (`"English"`) will fail. To pin the JVM locale for these tests, add `Locale.setDefault(Locale.US)` at test-class init or inside each `@Before` — but a cleaner approach is to refactor `ComposerLanguageChip` to take a `displayLocale: Locale = Locale.getDefault()` parameter that the test pins to `Locale.US`. **For V1 the tests assume an English JVM (matching the existing `:feature:composer:impl` test pattern); add the `displayLocale` parameter only if CI determinism actually fails.**

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerLanguageChip.kt \
        feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerLanguageChipTest.kt
git commit -m "feat(feature/composer): add ComposerLanguageChip composable

AssistChip with leading globe icon + dynamic label per the design doc:
device-locale display name when selectedLangs is null, the selection's
display name when 1 tag, first display name + '+N' overflow for
multi-tag selection. 200dp width cap defends against long display
names like 'Norwegian Bokmål'.

Refs: nubecita-oae"
```

---

## Task 8: `ComposerOptionsChipRow` composable

**Files:**
- Create: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerOptionsChipRow.kt`

This task is small — no dedicated unit test (the row is a thin layout container; its visual treatment is locked by the screenshot fixtures in Task 12).

- [ ] **Step 1: Implement the row**

Create `internal/ComposerOptionsChipRow.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontal row hosting the composer's authoring chips — currently
 * just the language chip. Reserves the layout slot so future composer
 * chips (visibility / threadgate, drafts, etc.) can land without
 * adding a new vertical row to the composer's chrome stack.
 *
 * Sits between [ComposerScreen]'s text-field surface and
 * [ComposerAttachmentRow]. When `nubecita-86m`'s
 * `HorizontalFloatingToolbar` lands, this row migrates wholesale into
 * the toolbar's content slot.
 */
@Composable
internal fun ComposerOptionsChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :feature:composer:impl:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/ComposerOptionsChipRow.kt
git commit -m "feat(feature/composer): add ComposerOptionsChipRow layout slot

Reserves a horizontal row for composer authoring chips. V1 ships with
just the language chip; visibility / threadgate / drafts chips land in
the same row in follow-up PRs without further chrome changes.

Refs: nubecita-oae"
```

---

## Task 9: `LanguagePickerContent` composable + Compose UI tests

**Files:**
- Create: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePickerContent.kt`
- Create: `feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePickerContentTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `LanguagePickerContentTest.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguagePickerContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tags = persistentListOf("en", "ja", "es", "fr")

    @Test
    fun search_filtersByDisplayName() {
        composeRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf(),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithContentDescription("Search languages").performTextInput("Span")
        composeRule.onNodeWithText("Spanish").assertIsDisplayed()
        // Japanese should be filtered out
        composeRule.onAllNodesWithText("Japanese").assertCountEquals(0)
    }

    @Test
    fun atCapOfThree_uncheckedRowsAreDisabled() {
        composeRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf("en", "ja", "es"),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = {},
                onDismiss = {},
            )
        }
        // The unchecked row (French) should be disabled.
        composeRule.onNodeWithText("French").assertIsNotEnabled()
    }

    @Test
    fun done_dispatchesCurrentDraftSelection() {
        var captured: List<String>? = null
        composeRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf("ja"),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(listOf("ja"), captured)
    }

    @Test
    fun cancel_invokesDismissWithoutDispatch() {
        var confirmed = 0
        var dismissed = 0
        composeRule.setContent {
            LanguagePickerContent(
                allTags = tags,
                draftSelection = persistentListOf("ja"),
                deviceLocaleTag = "en-US",
                onToggle = {},
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ },
            )
        }
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(confirmed == 0, "Cancel must not dispatch confirm")
        assertTrue(dismissed == 1, "Cancel must invoke onDismiss exactly once")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*LanguagePickerContentTest*"`

Expected: FAIL with `Unresolved reference: LanguagePickerContent`.

- [ ] **Step 3: Implement the composable**

Create `internal/LanguagePickerContent.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

private const val MAX_LANGS = 3

/**
 * Stateless picker content — the `LazyColumn` of language rows + a
 * search field at the top + a Done/Cancel footer at the bottom.
 *
 * `draftSelection` is the picker's local working selection
 * (independent of `ComposerState.selectedLangs`). [onToggle] flips
 * a single tag's checkbox; [onConfirm] commits the final list back to
 * the VM via `ComposerEvent.LanguageSelectionConfirmed`; [onDismiss]
 * is fired for Cancel + scrim-tap + drag-down + back-press.
 *
 * Search filters by both BCP-47 tag (`"en"` matches English) and
 * localized display name (`"anglais"` matches English when the JVM
 * locale is French). Row order:
 *
 * 1. Currently-selected tags (alphabetical among themselves).
 * 2. The device-locale tag, if not selected.
 * 3. Everything else, alphabetical by display name in the JVM locale.
 *
 * Selection-pinning makes re-opens predictable. The cap of 3 is
 * enforced by rendering unchecked rows with `enabled = false` once
 * `draftSelection.size == MAX_LANGS` — the M3 disabled-tonal idiom.
 */
@Composable
internal fun LanguagePickerContent(
    allTags: ImmutableList<String>,
    draftSelection: ImmutableList<String>,
    deviceLocaleTag: String,
    onToggle: (String) -> Unit,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleTags = remember(allTags, draftSelection, deviceLocaleTag, query) {
        val sorted = sortTags(allTags, draftSelection, deviceLocaleTag)
        if (query.isBlank()) {
            sorted
        } else {
            sorted.filter { matchesQuery(tag = it, query = query) }.toImmutableList()
        }
    }
    val capReached = draftSelection.size >= MAX_LANGS

    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search languages" },
            singleLine = true,
            label = { Text("Search") },
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            items(items = visibleTags, key = { it }) { tag ->
                val selected = draftSelection.contains(tag)
                LanguageRow(
                    tag = tag,
                    selected = selected,
                    enabled = selected || !capReached,
                    onToggle = { onToggle(tag) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Button(onClick = { onConfirm(draftSelection.toList()) }) { Text("Done") }
        }
    }
}

@Composable
private fun LanguageRow(
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = enabled,
        )
        Spacer(Modifier.padding(horizontal = 4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(text = displayName(tag))
        }
    }
}

private fun sortTags(
    allTags: ImmutableList<String>,
    draftSelection: ImmutableList<String>,
    deviceLocaleTag: String,
): ImmutableList<String> {
    val deviceLang = Locale.forLanguageTag(deviceLocaleTag).language
    val selectedSet = draftSelection.toSet()
    val sorted = allTags.sortedBy { displayName(it) }
    val (selected, rest) = sorted.partition { it in selectedSet }
    val (deviceFirst, others) = rest.partition { it == deviceLang }
    return (selected + deviceFirst + others).toImmutableList()
}

private fun matchesQuery(tag: String, query: String): Boolean {
    val normalized = query.trim().lowercase(Locale.getDefault())
    if (normalized.isEmpty()) return true
    return tag.lowercase(Locale.getDefault()).contains(normalized) ||
        displayName(tag).lowercase(Locale.getDefault()).contains(normalized)
}

private fun displayName(tag: String): String =
    Locale.forLanguageTag(tag)
        .getDisplayName(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest --tests "*LanguagePickerContentTest*"`

Expected: PASS, all 4 tests.

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePickerContent.kt \
        feature/composer/impl/src/test/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePickerContentTest.kt
git commit -m "feat(feature/composer): add LanguagePickerContent stateless picker

Search field + LazyColumn of language rows + Done/Cancel footer. Sort
order pins selected tags first, then device locale, then alphabetical.
Cap of 3 enforced via enabled = false on unchecked rows. Search
matches both BCP-47 tag and localized display name.

Refs: nubecita-oae"
```

---

## Task 10: `LanguagePicker` adaptive wrapper

**Files:**
- Create: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePicker.kt`

This task wraps `LanguagePickerContent` in `ModalBottomSheet` (Compact) or `Popup` (Medium/Expanded). The width-class branching is identical to `ComposerDiscardDialog.kt:62-65`. No dedicated unit test — visual behavior is locked by the screenshot fixtures in Task 12.

- [ ] **Step 1: Implement the wrapper**

Create `internal/LanguagePicker.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch

/**
 * Adaptive wrapper around [LanguagePickerContent]. At Compact width
 * uses [ModalBottomSheet]; at Medium / Expanded uses [Popup] over an
 * M3 [Surface] card — same width-class branching pattern as
 * [ComposerDiscardDialog], sidestepping the double-scrim issue when
 * the composer is itself a Compose `Dialog`.
 *
 * Manages the picker's local draft selection: tap-toggle mutates
 * [draft] without dispatching to the VM; only [onConfirm] commits the
 * draft. Drag-down on the bottom sheet, scrim-tap on the popup, and
 * Cancel all map to [onDismiss] without commit.
 *
 * The picker's initial selection is computed by the caller (typically
 * `state.selectedLangs ?: listOf(viewModel.deviceLocaleTag)`) and
 * passed in as [initialSelection].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun LanguagePicker(
    allTags: ImmutableList<String>,
    initialSelection: ImmutableList<String>,
    deviceLocaleTag: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(initialSelection) {
        mutableStateOf(initialSelection.toList())
    }
    val toggle: (String) -> Unit = { tag ->
        draft = if (draft.contains(tag)) draft - tag else draft + tag
    }
    val isCompact =
        !currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (isCompact) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
        ) {
            LanguagePickerContent(
                allTags = allTags,
                draftSelection = draft.toImmutableList(),
                deviceLocaleTag = deviceLocaleTag,
                onToggle = toggle,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
    } else {
        Popup(onDismissRequest = onDismiss, alignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = modifier.widthIn(max = 480.dp),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    LanguagePickerContent(
                        allTags = allTags,
                        draftSelection = draft.toImmutableList(),
                        deviceLocaleTag = deviceLocaleTag,
                        onToggle = toggle,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :feature:composer:impl:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/internal/LanguagePicker.kt
git commit -m "feat(feature/composer): add LanguagePicker adaptive wrapper

ModalBottomSheet at Compact, Popup over Surface at Medium/Expanded.
Manages local draft selection so toggling checkboxes doesn't dispatch
to the VM until the user taps Done. Same width-class branching pattern
as ComposerDiscardDialog.

Refs: nubecita-oae"
```

---

## Task 11: Wire chip + picker into `ComposerScreen`

**Files:**
- Modify: `feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreen.kt`

- [ ] **Step 1: Find the insertion point**

In `ComposerScreen.kt`'s `ComposerScreenContent` composable, locate the call to `ComposerAttachmentRow(...)`. The chip row goes immediately above it.

- [ ] **Step 2: Add the chip + picker**

Add to the file's imports:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.posting.BLUESKY_LANGUAGE_TAGS
import net.kikin.nubecita.feature.composer.impl.internal.ComposerLanguageChip
import net.kikin.nubecita.feature.composer.impl.internal.ComposerOptionsChipRow
import net.kikin.nubecita.feature.composer.impl.internal.LanguagePicker
```

Inside the `ComposerScreenContent` (or whatever the inner stateless host is named), add a `showPicker` flag near the top of the function body alongside the existing `showDiscardDialog` flag:

```kotlin
var showPicker by rememberSaveable { mutableStateOf(false) }
```

Add the chip row immediately above the attachment row in the composable layout:

```kotlin
ComposerOptionsChipRow {
    ComposerLanguageChip(
        selectedLangs = state.selectedLangs,
        deviceLocaleTag = deviceLocaleTag,
        onClick = { showPicker = true },
    )
}
ComposerAttachmentRow(/* ...existing args... */)
```

`deviceLocaleTag` is a new parameter on `ComposerScreenContent` — add it as the next parameter after the existing VM-derived ones, and have the public-API host (`ComposerScreen`) pass `viewModel.deviceLocaleTag`.

Render the picker at the bottom of the composable, conditional on `showPicker`:

```kotlin
if (showPicker) {
    LanguagePicker(
        allTags = BLUESKY_LANGUAGE_TAGS,
        initialSelection = (state.selectedLangs ?: listOf(deviceLocaleTag)).toImmutableList(),
        deviceLocaleTag = deviceLocaleTag,
        onConfirm = { tags ->
            onEvent(ComposerEvent.LanguageSelectionConfirmed(tags))
            showPicker = false
        },
        onDismiss = { showPicker = false },
    )
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :feature:composer:impl:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 4: Run all unit tests + Compose UI tests**

Run: `./gradlew :feature:composer:impl:testDebugUnitTest`

Expected: PASS — including all pre-existing tests.

- [ ] **Step 5: Commit**

```bash
git add feature/composer/impl/src/main/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreen.kt
git commit -m "feat(feature/composer): wire language chip + picker into ComposerScreen

ComposerOptionsChipRow renders between text-field surface and
ComposerAttachmentRow. Tap on the chip flips a rememberSaveable flag
that hosts LanguagePicker. Confirm dispatches LanguageSelectionConfirmed
via the standard event-routing path; dismiss leaves state untouched.

Refs: nubecita-oae"
```

---

## Task 12: Screenshot fixtures

**Files:**
- Create: `feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/ComposerScreenLanguageChipScreenshotTest.kt`
- Create: `feature/composer/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/composer/impl/LanguagePickerScreenshotTest.kt`

- [ ] **Step 1: Create chip screenshot fixtures**

Create `ComposerScreenLanguageChipScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.ComposerLanguageChip

/**
 * Visual baselines for [ComposerLanguageChip]'s three label states.
 * Pinning `deviceLocaleTag = "en-US"` keeps the rendered display name
 * deterministic across CI runners regardless of the JVM's
 * `Locale.getDefault()` (Layoutlib defaults vary by manufacturer).
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguageChipNullSelectionScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ComposerLanguageChip(
                selectedLangs = null,
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguageChipSingleSelectionScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ComposerLanguageChip(
                selectedLangs = listOf("ja-JP"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguageChipMultiSelectionScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ComposerLanguageChip(
                selectedLangs = listOf("en-US", "ja-JP", "es-MX"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
    }
}
```

- [ ] **Step 2: Create picker screenshot fixtures**

Create `LanguagePickerScreenshotTest.kt`:

```kotlin
package net.kikin.nubecita.feature.composer.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.posting.BLUESKY_LANGUAGE_TAGS
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.LanguagePickerContent

/**
 * Visual baselines for the picker's content composable across all
 * width classes. The adaptive wrapper [LanguagePicker] is NOT rendered
 * directly because Layoutlib doesn't render `ModalBottomSheet` /
 * `Popup` reliably; the same `LanguagePickerContent` is rendered
 * inside both wrappers in production, so locking the content's visual
 * treatment is sufficient.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguagePickerInitialScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        LanguagePickerContent(
            allTags = BLUESKY_LANGUAGE_TAGS,
            draftSelection = persistentListOf("en"),
            deviceLocaleTag = "en-US",
            onToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguagePickerCapReachedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        LanguagePickerContent(
            allTags = BLUESKY_LANGUAGE_TAGS,
            draftSelection = persistentListOf("en", "ja", "es"),
            deviceLocaleTag = "en-US",
            onToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
```

- [ ] **Step 3: Compile screenshot test source**

Run: `./gradlew :feature:composer:impl:compileDebugScreenshotTestKotlin`

Expected: PASS.

- [ ] **Step 4: Generate baselines**

Run: `./gradlew :feature:composer:impl:updateDebugScreenshotTest`

Expected: BUILD SUCCESSFUL — generates 30 PNGs total (5 fixtures × `@PreviewNubecitaScreenPreviews`'s 6 entries: Phone/Foldable/Tablet × Light/Dark).

- [ ] **Step 5: Eyeball the baselines**

Inspect a representative subset:

- `LanguageChipNullSelectionScreenshot_Phone Light_*.png` → chip renders `🌐 English`.
- `LanguageChipMultiSelectionScreenshot_Tablet Light_*.png` → chip renders `🌐 English +2`.
- `LanguagePickerCapReachedScreenshot_Foldable Dark_*.png` → 3 checked rows; unchecked rows visibly disabled (dimmer); search field at top; Done/Cancel footer at bottom.

If any baseline looks wrong, do not commit — diagnose and fix the visual treatment, then regenerate.

- [ ] **Step 6: Validate**

Run: `./gradlew :feature:composer:impl:validateDebugScreenshotTest`

Expected: PASS — fixtures match baselines.

- [ ] **Step 7: Commit**

```bash
git add feature/composer/impl/src/screenshotTest/ \
        feature/composer/impl/src/screenshotTestDebug/reference/
git commit -m "test(feature/composer): add screenshot fixtures for language chip + picker

Five fixtures × 6 (size × theme) baselines via PreviewNubecitaScreenPreviews:

* LanguageChipNullSelectionScreenshot
* LanguageChipSingleSelectionScreenshot
* LanguageChipMultiSelectionScreenshot
* LanguagePickerInitialScreenshot
* LanguagePickerCapReachedScreenshot

Picker fixtures render LanguagePickerContent directly because Layoutlib
doesn't render ModalBottomSheet / Popup reliably. The same content
composable is rendered inside both wrappers in production, so locking
its visual treatment is sufficient.

Refs: nubecita-oae"
```

---

## Task 13: Update `feature-composer/spec.md`

**Files:**
- Modify: `openspec/specs/feature-composer/spec.md`

- [ ] **Step 1: Add the new requirement**

Locate the existing requirement *"Submitted records carry a `langs` field derived from the device's primary locale"* (added in PR #146). Insert the new requirement immediately after it:

```markdown
### Requirement: Composer language chip exposes a per-post BCP-47 override

The system SHALL render an M3 `AssistChip` (leading globe icon, dynamic label) inside a `ComposerOptionsChipRow` between `ComposerScreen`'s text-field surface and `ComposerAttachmentRow`. The chip's label SHALL reflect what the next `PostingRepository.createPost` call will send: when `state.selectedLangs == null` the label is the localized display name of `ComposerViewModel.deviceLocaleTag` (resolved from the injected `LocaleProvider`); when `state.selectedLangs.size == 1` the label is the localized display name of the selected tag; when `state.selectedLangs.size >= 2` the label is the first tag's display name plus `"+N"` overflow (`"+1"` or `"+2"`).

Tapping the chip SHALL open a multi-select picker preselected with `state.selectedLangs ?: listOf(deviceLocaleTag)`. The picker SHALL be a `ModalBottomSheet` at Compact width and a `Popup` overlaying an M3 `Surface(widthIn(max = 480.dp))` at Medium / Expanded width — the same width-class branching `ComposerDiscardDialog` uses to avoid the double-scrim problem when the composer is itself a Compose `Dialog`. The picker SHALL enforce a cap of 3 selections by rendering unchecked checkboxes as `enabled = false` once `draftSelection.size == 3`. The reducer for `ComposerEvent.LanguageSelectionConfirmed(tags)` SHALL also defensively no-op when `tags.size > 3`.

Selection-while-the-picker-is-open SHALL be local to the picker's draft state. Tapping `Done` dispatches `LanguageSelectionConfirmed(tags)`; tapping `Cancel`, dragging the bottom sheet down, scrim-tapping the popup, or pressing back SHALL dismiss without dispatching. The list of selectable tags SHALL be the static `BLUESKY_LANGUAGE_TAGS` constant in `:core:posting`, sorted with currently-selected tags pinned at the top, then the device-locale tag (if not selected), then everything else alphabetical by `Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())`.

#### Scenario: Chip label reflects device-locale fallback when no override is set

- **GIVEN** `ComposerState.selectedLangs == null` and `ComposerViewModel.deviceLocaleTag == "en-US"`
- **WHEN** `ComposerScreen` renders
- **THEN** the chip's label is `"English"`

#### Scenario: Chip label reflects single explicit override

- **GIVEN** `ComposerState.selectedLangs == listOf("ja-JP")`
- **WHEN** `ComposerScreen` renders
- **THEN** the chip's label is `"Japanese"`

#### Scenario: Chip label shows overflow count for multi-language selection

- **GIVEN** `ComposerState.selectedLangs == listOf("en-US", "ja-JP", "es-MX")`
- **WHEN** `ComposerScreen` renders
- **THEN** the chip's label is `"English +2"`

#### Scenario: Cap-of-3 enforced as disabled checkboxes

- **GIVEN** the language picker is open and the user has checked 3 languages
- **WHEN** the user inspects an unchecked language row
- **THEN** that row's checkbox is rendered with `enabled = false`

#### Scenario: Picker dismiss without confirm leaves state unchanged

- **GIVEN** the language picker is open with `state.selectedLangs == null`, the user toggles two checkboxes inside the picker, and then taps `Cancel` (or drags the sheet down)
- **WHEN** the dismiss completes
- **THEN** `ComposerState.selectedLangs` is still `null`

#### Scenario: Submit with non-null selection passes it verbatim to createPost

- **GIVEN** `ComposerState.selectedLangs == listOf("ja-JP", "en-US")` and `Submit` succeeds
- **WHEN** `PostingRepository.createPost` is invoked
- **THEN** the call's `langs` parameter is `listOf("ja-JP", "en-US")`

#### Scenario: Submit with null selection falls back to repo's device-locale default

- **GIVEN** `ComposerState.selectedLangs == null` and `Submit` succeeds
- **WHEN** `PostingRepository.createPost` is invoked
- **THEN** the call's `langs` parameter is `null` (the repository's `LocaleProvider` then derives the device-locale default per `nubecita-wtq.12`'s contract)
```

- [ ] **Step 2: Validate**

Run: `openspec validate --all --strict`

Expected: `Totals: 26 passed, 0 failed (26 items)`.

- [ ] **Step 3: Commit**

```bash
git add openspec/specs/feature-composer/spec.md
git commit -m "docs(openspec): add feature-composer requirement for language chip + picker

Codifies the chip's three label states, the picker's adaptive UI surface
(ModalBottomSheet at Compact, Popup at Medium/Expanded), the cap-of-3
disabled-checkbox enforcement, and the dismiss-without-confirm contract.
Seven scenarios cover device-locale fallback, single override, multi-tag
overflow, cap UX, dismiss semantics, and the null/non-null Submit paths
through the wtq.12 repo contract.

Refs: nubecita-oae"
```

---

## Task 14: Final integration check + push + open PR

**Files:** none new — verification only.

- [ ] **Step 1: Run the full lint + test suite for affected modules**

Run:

```bash
./gradlew :core:posting:testDebugUnitTest \
          :core:posting:lint \
          :feature:composer:impl:testDebugUnitTest \
          :feature:composer:impl:lint \
          :feature:composer:impl:validateDebugScreenshotTest \
          :designsystem:lint \
          spotlessCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin docs/composer-language-selector-design
```

- [ ] **Step 3: Open the PR**

```bash
gh pr create --base main \
  --title "feat(feature/composer): per-post language selector via toolbar globe chip" \
  --body "$(cat <<'EOF'
## Summary

Closes the deferred per-post override UI from \`nubecita-wtq.12\` (PR #146). Adds an M3 \`AssistChip\` in the composer chrome that opens a checkbox picker — users attach up to 3 BCP-47 language tags, capped at 3 to match the official Bluesky client.

Design: \`docs/superpowers/specs/2026-05-09-composer-language-selector-design.md\`

## Changes

- **\`:core:posting\`** — new \`BLUESKY_LANGUAGE_TAGS\` constant (verbatim port of bsky-app's \`LANGUAGES\`).
- **\`:feature:composer:impl\`** — \`ComposerState.selectedLangs\` field, \`LanguageSelectionConfirmed\` event, VM exposes \`deviceLocaleTag\`, \`ComposerLanguageChip\`, \`ComposerOptionsChipRow\`, \`LanguagePickerContent\`, \`LanguagePicker\` adaptive wrapper, \`ComposerScreen\` integration.
- **Spec** — new requirement in \`feature-composer/spec.md\` with 7 scenarios.

## Test plan

- [x] \`./gradlew :core:posting:testDebugUnitTest\` — BLUESKY_LANGUAGE_TAGS smoke tests
- [x] \`./gradlew :feature:composer:impl:testDebugUnitTest\` — VM reducer + Submit pipeline + Compose UI tests
- [x] \`./gradlew :feature:composer:impl:validateDebugScreenshotTest\` — 30 new baseline PNGs validated
- [x] \`openspec validate --all --strict\` — 26/26 specs pass
- [x] \`./gradlew spotlessCheck\` — clean

Closes: nubecita-oae
EOF
)"
```

Expected: PR URL printed.

---

## Self-review checklist

| Check | Notes |
|---|---|
| **Spec coverage** | Requirement *"Composer language chip exposes a per-post BCP-47 override"* with 7 scenarios → covered by Tasks 5, 6, 7, 9, 11, 13. State + event additions → Tasks 2, 3. VM injection → Task 4. Module layout → all tasks. Test plan determinism → Tasks 7, 12 (deviceLocaleTag pinned to "en-US"). |
| **Placeholder scan** | One illustrative-list note in Task 1 explicitly flags itself ("must be replaced with the full upstream entries before committing") and is followed by a smoke test that doesn't pin exact contents. No silent placeholders. |
| **Type consistency** | `ComposerLanguageChip(selectedLangs, deviceLocaleTag, onClick)` signature matches Tasks 7/11/12. `LanguagePickerContent(allTags, draftSelection, deviceLocaleTag, onToggle, onConfirm, onDismiss)` matches Tasks 9/10/12. `ComposerEvent.LanguageSelectionConfirmed(tags: List<String>)` matches Tasks 3/5/11/13. `BLUESKY_LANGUAGE_TAGS: ImmutableList<String>` matches Tasks 1/11/12. |
| **Determinism** | Both unit (`ComposerLanguageChipTest`) and screenshot tests pin `deviceLocaleTag = "en-US"`; the chip's display-name rendering uses `Locale.getDefault()` so a non-English JVM would still render localized labels — this is called out in Task 7 with the recommended remediation (add a `displayLocale` parameter only if CI determinism actually fails). |
| **TDD discipline** | Every code task has the failing-test → run-fails → impl → run-passes → commit cycle. Tasks 8, 10 (small layout containers) skip the test cycle and are explicitly justified — their behavior is locked by the screenshot fixtures in Task 12. |
