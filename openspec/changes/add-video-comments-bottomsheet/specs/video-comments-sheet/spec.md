## ADDED Requirements

### Requirement: Comments Sheet Trigger and Continuous Playback
The video player surfaces (Vertical Reel Player and Feed Detail Player) MUST provide a comment action that displays an in-context Comments Sheet without halting ExoPlayer video playback or navigating away from the current video.

#### Scenario: Reel Player action rail triggers the comments sheet
- **WHEN** the viewer taps the Comment button on the vertical reel action rail
- **AND** the pager is settled on the current page (`pagerState.settledPage == page` and `!pagerState.isScrollInProgress`)
- **THEN** the post is resolved from the active filtered post list (`status.items[page]`)
- **AND** the Comments Sheet animates open to the `PartiallyExpanded` detent (~50% height)
- **AND** the video continues playing video frames and audio uninterrupted
- **AND** the underlying `VerticalPager` disables swipe gestures (`userScrollEnabled = false`)

#### Scenario: Comment trigger is ignored while reel pager is scrolling
- **WHEN** the viewer taps the Comment button while the reel pager is actively scrolling (`pagerState.isScrollInProgress == true`)
- **THEN** the tap is ignored and the Comments Sheet does not open until the page settles

#### Scenario: Feed Detail Player triggers the comments sheet
- **WHEN** the viewer taps the Comment action in the detail player action group
- **THEN** the Comments Sheet animates open
- **AND** media playback and transport controls remain interactive

#### Scenario: Chrome auto-fade is suspended during initial thread loading
- **WHEN** the comments sheet is opened and the initial thread loading indicator is visible
- **THEN** the video player chrome auto-fade timer is suspended until the thread resolves or fails

### Requirement: Gesture Arbitration and Nested Scroll Isolation
The Comments Sheet MUST arbitrate vertical gestures such that dragging comments or the sheet never triggers page flips in the underlying `VerticalPager`.

#### Scenario: Scrolling comments list does not swipe the reel
- **WHEN** the comments list is scrolled vertically
- **THEN** scroll deltas are consumed entirely by the comments `LazyColumn`
- **AND** the underlying `VerticalPager` receives zero scroll events

#### Scenario: Dragging down at top of comments collapses the sheet
- **WHEN** the comments list is at scroll position 0 (`firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`)
- **AND** the user drags downward
- **THEN** the sheet animates towards `Hidden`
- **AND** `VerticalPager` re-enables `userScrollEnabled = true` only after the sheet has completely settled in `Hidden` and exit animation has finished

### Requirement: Adaptive Presentation (Compact BottomSheet vs Medium and Expanded SideSheet)
The Comments UI MUST adapt based on `WindowWidthSizeClass`.

#### Scenario: Compact window displays bottom sheet
- **WHEN** the app runs in `WindowWidthSizeClass.Compact` (phones portrait)
- **THEN** the comments UI renders as a bottom sheet anchoring to bottom detents over the video
- **AND** the sheet container zeroes default window insets (`windowInsets = WindowInsets(0, 0, 0, 0)`) to prevent double-insetting with child navigation bar padding

#### Scenario: Medium and Expanded window displays split side sheet
- **WHEN** the app runs in `WindowWidthSizeClass.Medium` (tablets portrait, foldables unfolded) or `WindowWidthSizeClass.Expanded` (tablets landscape, desktop)
- **THEN** the comments UI renders as a docked right-hand side panel constrained between 360dp and 440dp width
- **AND** the video player occupies the left pane maintaining its aspect ratio
- **AND** no modal dimming scrim obscures the video player

#### Scenario: Soft keyboard in two-pane mode resizes side sheet without compressing video
- **WHEN** the user focuses a text input within the side sheet in Medium or Expanded two-pane mode
- **THEN** `WindowInsets.ime` padding is applied to the comments side sheet container
- **AND** the left-pane video viewport dimensions remain unchanged

### Requirement: Video Looping and Auto-Advance Invariant
The video playback engine MUST maintain continuous playback and loop behavior while comments are viewed.

#### Scenario: Active video loops continuously while comments are open
- **WHEN** the active video reaches its duration end while the comments sheet is open
- **THEN** the video loops back to the start and continues playing
- **AND** any feed auto-advance mechanism is suspended while the comments sheet remains open

### Requirement: Playback and Lifecycle Coordination
Video playback and lifecycle transitions MUST coordinate with the Comments Sheet.

#### Scenario: Entering Picture-in-Picture instantly dismisses the comments sheet
- **WHEN** the user or system triggers Picture-in-Picture mode while the comments sheet is open
- **THEN** the comments sheet instantly drops state to `Hidden` without playing an exit animation
- **AND** the PiP window displays only the clean video surface without a captured half-collapsed sheet

#### Scenario: Predictive back gesture animates sheet dismissal
- **WHEN** the comments sheet is open and the user performs a predictive back gesture
- **THEN** `PredictiveBackHandler` collects gesture progress and translates the sheet offset smoothly
- **AND** on commit, the sheet dismisses and the video player remains open and playing

### Requirement: Comments Thread States (Loaded, Empty, Error)
The Comments Sheet MUST handle empty and failure states gracefully.

#### Scenario: Empty comments thread displays call to action
- **WHEN** a video has zero replies
- **THEN** the comments list displays an empty state banner stating "No comments yet. Be the first to reply!"

#### Scenario: Thread fetch error displays retry affordance
- **WHEN** fetching replies fails due to a network error
- **THEN** the comments sheet displays an error banner with a "Retry" button that re-invokes the fetch

### Requirement: Phase 1 Docked Comment Pill and Cross-Shell Composer Navigation
In Phase 1, the sheet MUST feature a docked `"Add a comment..."` pill with accessibility semantics that opens the full composer destination across both `@MainShell` and `@OuterShell` back stacks.

#### Scenario: Docked comment pill accessibility contract
- **WHEN** the docked comment pill is composed
- **THEN** it exposes `Role.Button` with an accessibility action label "Add a comment"
- **AND** internal avatar and glyphs have `contentDescription = null` to prevent duplicate announcements

#### Scenario: Reel Player navigates to composer via MainShell
- **WHEN** the viewer taps the docked pill from the Reel Player
- **THEN** the app pushes `ComposerRoute(replyToUri = videoPostUri)` onto `LocalMainShellNavState`

#### Scenario: Feed Detail Player navigates to composer via OuterShell
- **WHEN** the viewer taps the docked pill from the Feed Detail Player
- **THEN** the app pushes `ComposerRoute(replyToUri = videoPostUri)` onto `LocalAppNavigator` (outer shell)

#### Scenario: Returning from composer after posting increments comment count and hydates thread
- **WHEN** the composer submits a reply and emits `ComposerSubmitEvent`
- **THEN** the parent video's `replyCount` badge on the action rail increments immediately (+1)
- **AND** the comments sheet triggers an immediate hydration refetch via `PostThreadRepository.getPostThread(uri)`
- **AND** the comments sheet remains open in its previous detent

#### Scenario: Returning from composer after cancelling restores sheet without mutation
- **WHEN** the viewer cancels the composer without submitting and returns via back navigation
- **THEN** the comments sheet remains open in its previous detent with its exact scroll position preserved
- **AND** no optimistic comments or badge increments occur
