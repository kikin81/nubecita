# Spike Report & Architectural Blueprint: Headless Composer Extraction (`:core:composer-engine`)

* **Issue**: [nubecita-76q8.9](file:///Users/francisco/code/nubecita) (Phase 2 spike for epic `nubecita-76q8`)
* **Author**: Antigravity / Francisco Velazquez
* **Date**: October 2026
* **Status**: Complete / Ready for Phase 2 Implementation

---

## 1. Executive Summary & Motivation

In Phase 1 of the Adaptive Video Player Comments BottomSheet & SideSheet epic (`nubecita-76q8`), we successfully introduced:
1. `VideoCommentsSheetScaffold` supporting phone bottom sheets and tablet/foldable side sheets.
2. Docked `"Add a comment..."` pill launching `ComposerRoute` via outer-shell navigation.
3. 120Hz continuous background video playback, settled-state gesture arbitration, and predictive back dismissal.

While Phase 1 preserves full composer capabilities (media attachments, GIFs, quote-posts, and drafts), navigating to the full-screen `ComposerScreen` interrupts video consumption.

**Phase 2 Goal:** Enable **inline text commenting directly within the comments sheet** so users can reply to videos without leaving the playback viewport.

To achieve this cleanly without violating Nubecita's strict modularity boundaries (`:core:*` modules cannot depend on `:feature:*` modules), we must extract the headless text composition, facet parsing, grapheme counting, and typeahead mechanics out of `:feature:composer:impl` into a reusable, lightweight module: **`:core:composer-engine`**.

---

## 2. Current Architecture & Coupling Problems

Currently, all authoring logic lives inside `:feature:composer:impl`:
* `ComposerViewModel.kt` (1,129 lines)
* `ComposerScreen.kt` (950+ lines)

### Key Couplings in `ComposerViewModel`:
1. **Full-Screen Navigation & Shells**: Deeply coupled to `LocalAppNavigator`, `@OuterShell` / `@MainShell` multibindings, and route serialization (`ComposerRoute`).
2. **Rich Media Attachments**: Tightly bound to image/video picking (`SharedMediaStore`, `VideoTranscoder`, `ImageCompressor`), camera integration, and alt-text dialogs.
3. **Drafts & Persistence**: Directly touches Room database `DraftsRepository` and session recovery.
4. **Complex Gate Settings**: Couples threadgate / postgate selection rules to composer state.
5. **Architectural Isolation**: `:core:post-interactions-ui` (which renders the comments sheet) **cannot depend** on `:feature:composer:impl` or `:feature:composer:api` for implementation logic.

---

## 3. Target Module Architecture: `:core:composer-engine`

We propose creating a pure headless engine module:

```
                      ┌────────────────────────┐
                      │  :core:composer-engine │
                      └───────────┬────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         ▼                                                 ▼
┌───────────────────────────────┐         ┌───────────────────────────────┐
│     :feature:composer:impl    │         │   :core:post-interactions-ui  │
│  (Full-screen Rich Composer)  │         │   (Inline Sheet Comment Pill) │
└───────────────────────────────┘         └───────────────────────────────┘
```

### Module Responsibilities (`:core:composer-engine`):
1. **State Ownership via Compose Foundation `TextFieldState`**:
   Direct manipulation via `TextFieldState` for zero-lag text input, avoiding state-dispatch recomposition churn.
2. **ATProtocol Grapheme & Length Enforcement**:
   Calculation of Bluesky's 300-grapheme limit using `java.text.BreakIterator.getCharacterInstance()`.
3. **Rich Text Facet Recognition**:
   Real-time regex detection and byte-offset calculation for `@handle` mentions, `#tag` hashtags, and `https://...` URLs in coordination with `:core:posting:FacetExtractor`.
4. **Mention & Tag Typeahead Pipeline**:
   Debounced `queryFlow` emitting candidate query tokens based on cursor position, delegating resolution to `ActorRepository`.
5. **Direct Posting Delegation**:
   A streamlined `submitReply()` use case that builds `app.bsky.feed.post` reply records via `PostingRepository` without attachment or draft overhead.

---

## 4. API Surface & Contract Blueprint

### 4.1 Headless Session Contract

```kotlin
package net.kikin.nubecita.core.composer.engine

import androidx.compose.foundation.text.input.TextFieldState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.data.models.AuthorUi

/**
 * Headless session managing the lifecycle, validation, typeahead, and submission
 * of text content without UI dependencies.
 */
interface HeadlessComposerSession {
    val textFieldState: TextFieldState
    val state: StateFlow<HeadlessComposerState>

    fun onMentionSelected(author: AuthorUi)
    fun onTagSelected(tag: String)
    suspend fun submit(): Result<String> // Returns created post AT URI
    fun reset()
}
```

### 4.2 State Model

```kotlin
data class HeadlessComposerState(
    val graphemeCount: Int = 0,
    val maxGraphemes: Int = 300,
    val isOverLimit: Boolean = false,
    val typeaheadQuery: TypeaheadQuery? = null,
    val typeaheadCandidates: ImmutableList<AuthorUi> = persistentListOf(),
    val isSubmitting: Boolean = false,
    val error: ComposerEngineError? = null,
)

sealed interface TypeaheadQuery {
    data class Mention(val token: String, val range: TextRange) : TypeaheadQuery
    data class Tag(val token: String, val range: TextRange) : TypeaheadQuery
}
```

---

## 5. UI Integration into Video Comments Sheet

### 5.1 Inline Expansion in `DockedCommentPill`

In Phase 1, `DockedCommentPill` is a static button `[Avatar] "Add a comment..." [Send]`.
In Phase 2:
1. **Collapsed State**: Retains the minimal 48dp pill aesthetic.
2. **Focused / Expanded State**:
   * Tapping transitions the pill into active editing mode.
   * Renders `BasicTextField(state = engine.textFieldState)` with multi-line growth (up to 4 lines before internal scroll).
   * Displays circular progress ring reflecting grapheme count (280/300 warnings, red over-limit).
   * Send button enables only when `graphemeCount in 1..300` and `!isSubmitting`.
   * Typeahead candidates pop up above the input field in a lightweight floating surface.

### 5.2 IME & Inset Coordination

* **Compact (Bottom Sheet)**:
  * When the input field gains focus, `sheetState.animateTo(SheetValue.Expanded)` ensures maximum visibility.
  * `WindowInsets.ime` smoothly lifts the docked input row above the on-screen keyboard.
  * Tapping the scrim or dragging the sheet downward automatically clears focus and hides the IME.
* **Medium & Expanded (Side Sheet)**:
  * In two-pane mode, `WindowInsets.ime` padding is strictly scoped to the side sheet container.
  * The video playing on the left pane remains completely un-squeezed and uninterrupted at 120Hz.

---

## 6. Migration Roadmap (Phase 2 Tasks)

| Step | Scope | Description |
| :--- | :--- | :--- |
| **Step 1** | `:core:composer-engine` | Create new library module, move grapheme break iterator, facet parsing helpers, and typeahead scanner. |
| **Step 2** | `:core:composer-engine` | Implement `HeadlessComposerSession` with unit tests covering cursor tracking, mention replacement, and grapheme caps. |
| **Step 3** | `:feature:composer:impl` | Refactor full-screen `ComposerViewModel` to delegate text editing and typeahead to `:core:composer-engine`. |
| **Step 4** | `:core:post-interactions-ui` | Update `DockedCommentPill` and `VideoCommentsSheetContent` to support inline text editing with `HeadlessComposerSession`. |
| **Step 5** | `:benchmark` | Run `VideoCommentsSheetBenchmark` to ensure 120Hz frame timing remains unbroken during inline keyboard input. |

---

## 7. Conclusion

Extracting `:core:composer-engine` provides the optimal architectural solution:
* Zero duplication of ATProtocol facet / grapheme logic.
* Decouples inline comment authoring from full-screen media/draft machinery.
* Preserves Nubecita's strict architectural boundary guidelines.
* Delivers the ultimate seamless commenting experience for video consumption.
