## ADDED Requirements

### Requirement: Comments Sheet Trigger and Continuous Playback
The video player surfaces (Vertical Reel Player and Feed Detail Player) MUST provide a comment action that displays an in-context Comments Sheet without halting ExoPlayer video playback or navigating away from the current video.

#### Scenario: Reel Player action rail triggers the comments sheet
- **WHEN** the viewer taps the Comment button on the vertical reel action rail
- **AND** the pager is settled on the current page (`pagerState.settledPage == page` and `!pagerState.isScrollInProgress`)
- **THEN** the Comments Sheet animates open to the `PartiallyExpanded` detent (~50% height)
- **AND** the video continues playing video frames and audio uninterrupted
- **AND** the underlying `VerticalPager` disables swipe gestures (`userScrollEnabled = false`)

#### Scenario: Comment trigger is ignored while reel pager is scrolling
- **WHEN** the viewer taps the Comment button while the reel pager is actively scrolling (`pagerState.isScrollInProgress == true`)
- **THEN** the tap is ignored and the Comments Sheet does not open until the page settles

#### Scenario: Feed Detail Player triggers the comments sheet
- **WHEN** the viewer taps the Comment action in the detail player action group
- **THEN** the Comments Sheet animates open
- **AND** media playback and transport controls remain interactive

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

### Requirement: Adaptive Presentation (Compact BottomSheet vs Expanded SideSheet)
The Comments UI MUST adapt based on `WindowWidthSizeClass`.

#### Scenario: Compact window displays bottom sheet
- **WHEN** the app runs in `WindowWidthSizeClass.Compact` (phones portrait)
- **THEN** the comments UI renders as a bottom sheet anchoring to bottom detents over the video

#### Scenario: Medium or Expanded window displays split side sheet
- **WHEN** the app runs in `WindowWidthSizeClass.Medium` or `WindowWidthSizeClass.Expanded` (tablets, foldables unfolded, landscape)
- **THEN** the comments UI renders as a docked right-hand side panel constrained between 360dp and 440dp width
- **AND** the video player occupies the left pane maintaining its aspect ratio
- **AND** no modal dimming scrim obscures the video player

#### Scenario: Soft keyboard in two-pane mode resizes side sheet without compressing video
- **WHEN** the user focuses a text input within the side sheet in two-pane mode
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

#### Scenario: System back press dismisses the sheet before exiting the screen
- **WHEN** the comments sheet is open and the user presses system back or performs a predictive back gesture
- **THEN** the back gesture is consumed to dismiss the sheet
- **AND** the video player remains open and playing

### Requirement: Comments Thread States (Loaded, Empty, Error)
The Comments Sheet MUST handle empty and failure states gracefully.

#### Scenario: Empty comments thread displays call to action
- **WHEN** a video has zero replies
- **THEN** the comments list displays an empty state banner stating "No comments yet. Be the first to reply!"

#### Scenario: Thread fetch error displays retry affordance
- **WHEN** fetching replies fails due to a network error
- **THEN** the comments sheet displays an error banner with a "Retry" button that re-invokes the fetch

### Requirement: Phase 1 Docked Comment Pill and Composer Navigation
In Phase 1, the sheet MUST feature a docked `"Add a comment..."` pill that opens the full composer destination while preserving player and sheet state on return.

#### Scenario: Tapping docked pill launches composer
- **WHEN** the viewer taps the `"Add a comment..."` pill at the bottom of the comments sheet
- **THEN** the app navigates to `ComposerRoute(replyToUri = videoPostUri)`

#### Scenario: Returning from composer after posting restores sheet and increments comment count
- **WHEN** the composer submits a reply and returns to the video player
- **THEN** the comments sheet is still open in its previous detent
- **AND** the newly submitted reply is optimistically inserted at the top of the comments list
- **AND** the parent video's comment count badge on the action rail increments immediately
- **AND** background revalidation is dispatched to sync the full thread silently

#### Scenario: Returning from composer after cancelling restores sheet without mutation
- **WHEN** the viewer cancels the composer without submitting and returns via back navigation
- **THEN** the comments sheet remains open in its previous detent with its exact scroll position preserved
- **AND** no optimistic comments are inserted
