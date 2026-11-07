## ADDED Requirements

### Requirement: The viewer shows the post's author and caption

The viewer SHALL show the post's author and text in a bottom overlay once the post has loaded, with the caption limited to three lines.

#### Scenario: A post with text

- **WHEN** the viewer has loaded a post that has text
- **THEN** the overlay shows the author and at most three lines of the text

#### Scenario: A post with no text

- **WHEN** the viewer has loaded a post with no text
- **THEN** the overlay shows the author and no caption

### Requirement: The overlay fades with the viewer's controls

The overlay and its gradient SHALL appear and disappear together with the top controls, fading in place.

#### Scenario: Tapping the image

- **WHEN** the reader taps the image while the controls are visible
- **THEN** the top controls, overlay and gradient fade out together

### Requirement: The overlay leads to the author and the post

Tapping the author SHALL open their profile, and tapping the caption SHALL open the post. If that post is already on top of the inner stack, tapping the caption SHALL close the viewer without opening a second copy.

#### Scenario: Opening the post from a profile's media

- **WHEN** the reader taps the caption in a viewer opened from a profile's Media tab
- **THEN** the viewer closes and the post detail screen opens

#### Scenario: Opened from the post itself

- **WHEN** the reader taps the caption in a viewer opened from that post's detail screen
- **THEN** the viewer closes and the post detail screen is not pushed again

### Requirement: The overlay offers post actions

The overlay SHALL offer reply, repost, like, bookmark and share, and SHALL show the same state as the post elsewhere in the app.

#### Scenario: Liking a post

- **WHEN** the reader taps like in the viewer
- **THEN** the like count and state update immediately

### Requirement: Comments open in a sheet over the viewer

The reply action SHALL open the comments sheet. While it is open the controls SHALL NOT auto-fade, and Back SHALL close the sheet before the viewer. The sheet's composer action SHALL open the composer above the viewer.

#### Scenario: Back with the sheet open

- **WHEN** the reader presses Back with the comments sheet open
- **THEN** the sheet closes and the viewer stays open

## MODIFIED Requirements

### Requirement: Tap-to-toggle chrome with auto-fade and per-image alt-text sheet

The viewer SHALL render overlay chrome containing a close button, page indicator (if `images.size > 1`), and ALT badge (if `altText != null`). Chrome MUST be visible on entry and auto-fade after 5 seconds of inactivity, where using an overlay action counts as activity and an open comments or repost sheet suspends the fade. Tapping the image MUST toggle `isChromeVisible`. Tapping the ALT badge MUST open a `ModalBottomSheet` displaying full alt text. Chrome MUST use Compose `AnimatedVisibility` and `ModalBottomSheet`.

#### Scenario: Chrome visible on entry then auto-fades

- **WHEN** the viewer enters `Loaded` and five seconds pass with no user interaction
- **THEN** `isChromeVisible` transitions to `false`; the close button, page indicator, and ALT badge are no longer rendered

#### Scenario: Tap on image toggles chrome

- **WHEN** the user single-taps the image
- **THEN** `OnTapImage` dispatches; `isChromeVisible` toggles; if it transitions to `true`, the auto-fade timer resets

#### Scenario: Page change re-shows chrome

- **WHEN** the user swipes to a new page
- **THEN** `OnPageChanged(index)` fires; `isChromeVisible` is set to `true`; the auto-fade timer resets

#### Scenario: ALT badge opens bottom sheet with full alt text

- **WHEN** the current image's `altText` is non-null and the user taps the `ALT` badge
- **THEN** `OnAltBadgeClick` dispatches; `isAltSheetOpen` becomes `true`; a `ModalBottomSheet` renders the full alt text in a scrollable container

#### Scenario: ALT badge absent when no alt text

- **WHEN** the current image's `altText` is null
- **THEN** the `ALT` badge MUST NOT render in the chrome overlay; the close button and page indicator (if applicable) remain

#### Scenario: Page indicator absent for single-image posts

- **WHEN** `state.images.size == 1`
- **THEN** the page indicator (`"1 / 1"`) MUST NOT render — the close button and (conditionally) the `ALT` badge are the only chrome elements
