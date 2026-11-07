# Gallery post details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The image gallery shows the post's author, caption and actions in a fading bottom overlay, and opens the comments sheet.

**Architecture:** The gallery stays an `@OuterShell` entry. Its ViewModel keeps the fetched post and delegates to `PostInteractionHandler`. Inner-shell routes go through `Navigator.closeAndOpenInMainShell`, which uses the existing `DeepLinkRouter`.

**Tech Stack:** Kotlin, Compose M3 Expressive, Hilt, Navigation 3, JUnit 5, MockK, Turbine.

**Spec:** `docs/superpowers/specs/2026-11-06-gallery-post-details-design.md`

## Global Constraints

- The gallery stays `@OuterShell`. `MediaViewerRoute` doesn't change.
- Overlay buttons come from `NubecitaOverlayControls`; no new button styles.
- New strings ship in `values`, `values-b+es+419` and `values-pt-rBR` together.
- Never commit local screenshot baselines; use the `update-baselines` label.
- ViewModels never touch navigation state; navigate through `UiEffect`.
- `:feature:videos:impl` and `:core:post-interactions-ui` flavor status is checked with `git grep`, not assumed.

## Review Focus

1. The shared image transition, entering and leaving, from the feed and from Profile > Media.
2. Pinch-zoom and swipe-down over the overlay, and paging with the overlay visible.
3. White text on a bright photo.
4. A pending route when `MainShell` is not in composition, and after logout.
5. Like or repost in the gallery, then Back: the card behind shows the same state.

---

### Task 1: Rename video comments to post comments

**Files:** `core/post-interactions-ui/.../comments/*`, `designsystem/.../adaptive/VideoCommentsSheetScaffold.kt`, and callers in `:feature:videos:impl`, `:feature:videoplayer:impl`, `:benchmark`.

- [ ] **Step 1:** Rename types and files. No behaviour change.
- [ ] **Step 2:** Run the touched modules' tests and lint. Expected: PASS, with the same test counts.
- [ ] **Step 3:** Commit as `refactor(post-interactions): rename video comments to post comments`.

### Task 2: Reach the inner stack from outer-shell screens

**Interfaces:**
- Produces: `rememberPostInteractions(..., onNavigate: (NavKey) -> Unit = default)`; `Navigator.closeAndOpenInMainShell(key: NavKey)`.

- [ ] **Step 1:** Reproduce the `VideoPlayerScreen` Profile push on bench. Record the result.
- [ ] **Step 2:** Failing tests: the call pops the outer entry and hands the key to the router.
- [ ] **Step 3:** Implement on `DefaultNavigator`.
- [ ] **Step 4:** Add `onNavigate`; move `VideoPlayerScreen` onto the slot if Step 1 crashed.
- [ ] **Step 5:** Run tests and lint. Commit as `refactor(navigation): let outer screens open inner routes`.

### Task 3: Post details overlay

**Interfaces:**
- Consumes: Task 2.
- Produces: `MediaViewerLoadStatus.Loaded.post: PostUi?`; events `AuthorTapped`, `CaptionTapped`; effects `OpenAuthor(handle)`, `OpenPost(uri)`.

- [ ] **Step 1:** Failing tests: the post is kept; the author tap emits `OpenAuthor`; the caption tap emits `OpenPost`; opened from post detail it emits `Dismiss`.
- [ ] **Step 2:** Implement, with `PostInteractionHandler by handler` bound to a new `PostSurface.MediaViewer`.
- [ ] **Step 3:** Build `PostDetailsOverlay` and the gradient inside the chrome block, on a fast effects spec.
- [ ] **Step 4:** Screenshot previews: bright image, dark image, no caption, long caption. Compile only.
- [ ] **Step 5:** Run tests and lint, compile bench. Commit as `feat(mediaviewer): show the post's details and actions`.

### Task 4: Comments sheet

- [ ] **Step 1:** Failing tests: the reply tap opens the sheet; the auto-fade doesn't fire while it is open; Back closes it first.
- [ ] **Step 2:** Host the sheet in the viewer. The composer opens on the outer stack.
- [ ] **Step 3:** Add bench comments for the gallery's post.
- [ ] **Step 4:** Device pass on the Samsung only (`R5GL75P24LL`), covering the Review Focus.
- [ ] **Step 5:** Commit as `feat(mediaviewer): open comments from the gallery`.
