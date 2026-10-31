## ADDED Requirements

### Requirement: Composer shows the posting account

`ComposerScreenContent` SHALL render the signed-in account from `CurrentAccountRepository` (via `ComposerState.author`) as a 40dp avatar to the left of the text field, with the account's display name on one line above the field in `labelMedium` / `onSurfaceVariant`, ellipsized. When the account has no display name the handle MUST be shown. Content below the field (suggestions, attachments, cards, quote) aligns to the avatar's text column.

#### Scenario: Display name shown beside the avatar

- **GIVEN** `state.author.displayName == "Alice"`
- **WHEN** the composer renders
- **THEN** an avatar for Alice and the text `"Alice"` appear above the text field

#### Scenario: Handle fallback

- **GIVEN** `state.author.displayName` is blank and `handle == "alice.bsky.social"`
- **WHEN** the composer renders
- **THEN** the name line reads `"alice.bsky.social"`

### Requirement: Composer text field is borderless

The text field SHALL render with no outline and no floating label: only the placeholder ("What's on your mind?") and the text, on the screen's `surface`. It MUST remain driven by the VM-owned `TextFieldState` (`BasicTextField(state = …)`), keep `testTag("composer_text_field")`, auto-focus, and accept pasted images.

#### Scenario: No outline or label

- **WHEN** the composer renders with an empty draft
- **THEN** no outline or "Compose post" label is drawn; only the placeholder text is visible

#### Scenario: Image paste still attaches

- **WHEN** an image is pasted into the field
- **THEN** it is added as an attachment, as before

### Requirement: Composer body scrolls and keeps content clear of the keyboard and toolbar

The composer body SHALL scroll vertically. The screen MUST have a single IME owner: `Scaffold(contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime))`, the body consuming the scaffold padding, and the floating toolbar applying `imePadding()`. The scrolling content MUST carry bottom padding at least the toolbar's height plus its margin, so its last element can scroll clear of the toolbar, in both the full-screen and the `adaptiveDialog()` presentations.

#### Scenario: Long post scrolls

- **GIVEN** a draft longer than the visible area with the keyboard open
- **WHEN** the user keeps typing
- **THEN** the body scrolls and the cursor stays above the toolbar

#### Scenario: Last card reachable

- **GIVEN** a reply with a quoted post at the bottom
- **WHEN** the user scrolls to the end
- **THEN** the quoted post is fully visible above the toolbar

#### Scenario: Dialog presentation

- **GIVEN** the composer is shown as the Medium/Expanded `adaptiveDialog()` card with the keyboard open
- **WHEN** it renders
- **THEN** the floating toolbar sits above the keyboard, not behind it

### Requirement: Composer actions live in an M3 floating toolbar with Post as its FAB

The composer SHALL render a `HorizontalFloatingToolbar` with `FloatingToolbarDefaults.standardFloatingToolbarColors()`, anchored bottom-center, holding the Add image, GIF, Language and Audience actions (each with an accessibility label), with Post as the toolbar's `StandardFloatingActionButton`.

#### Scenario: Toolbar actions open the existing pickers

- **WHEN** the Language action is tapped
- **THEN** the existing language picker opens (same for GIF and Audience)

#### Scenario: Audience uses the Settings icon and is hidden in reply mode

- **GIVEN** `state.replyToUri != null`
- **WHEN** the composer renders
- **THEN** the Audience action (Settings icon) is absent from the toolbar

### Requirement: Floating toolbar is always visible and rides the keyboard

The floating toolbar SHALL stay on screen at all times (no scroll-away behaviour): at the bottom of the composer when the keyboard is closed and directly on top of the keyboard when it is open.

#### Scenario: Toolbar stays visible while scrolling

- **WHEN** the user scrolls the body
- **THEN** the toolbar and the Post FAB remain on screen

#### Scenario: Toolbar sits on the keyboard

- **WHEN** the keyboard opens
- **THEN** the toolbar moves up to sit directly above it

### Requirement: Floating toolbar keeps the existing action enablement rules

The toolbar actions SHALL keep today's rules: GIF disabled while photos are attached, Add image unavailable while a GIF is attached, and every action disabled while a post is submitting.

#### Scenario: GIF disabled with photos

- **GIVEN** one or more photos are attached
- **WHEN** the composer renders
- **THEN** the GIF action is disabled

#### Scenario: Actions disabled while submitting

- **GIVEN** `state.submitStatus == ComposerSubmitStatus.Submitting`
- **WHEN** the composer renders
- **THEN** every toolbar action is disabled

### Requirement: Composer shows language and audience on a summary line

Under the text field the composer SHALL render one quiet line (`labelSmall` / `onSurfaceVariant`) with the current language label (same formatting as the former chip, including `"+N"` overflow) and, for top-level posts, the audience label, joined by `" · "`. The line is not interactive.

#### Scenario: Default top-level post

- **GIVEN** `selectedLangs == null`, device locale `en-US`, default audience, `replyToUri == null`
- **WHEN** the composer renders
- **THEN** the summary line reads `"English · Visible to all"` (localized)

#### Scenario: Reply omits audience

- **GIVEN** `replyToUri != null`
- **WHEN** the composer renders
- **THEN** the summary line shows only the language label

### Requirement: Attachment thumbnails are taller

The attachment strip SHALL keep its horizontal scroll, drag-to-reorder, ALT badge, remove button and tap-to-edit-alt behaviour, with thumbnails roughly 120dp tall (about double the previous chip height).

#### Scenario: Reorder still works

- **GIVEN** three attached photos
- **WHEN** the user drags the third thumbnail to the first position
- **THEN** `state.attachments` reflects the new order

## MODIFIED Requirements

### Requirement: Composer language chip exposes a per-post BCP-47 override

The system SHALL expose the per-post language override through the floating toolbar's Language action and show the current language tags (localized display name or `"+N"` overflow) on the composer's summary line. Tapping the Language action opens a multi-select picker (`ModalBottomSheet` on Compact, `Popup` on Medium/Expanded) with up to 3 selections from `BLUESKY_LANGUAGE_TAGS`. Dismissing without confirmation preserves previous state.

#### Scenario: Chip label reflects device-locale fallback when no override is set

- **GIVEN** `ComposerState.selectedLangs == null` and `ComposerViewModel.deviceLocaleTag == "en-US"`
- **WHEN** `ComposerScreen` renders
- **THEN** the summary line's language label is `"English"`

#### Scenario: Chip label reflects single explicit override

- **GIVEN** `ComposerState.selectedLangs == listOf("ja-JP")`
- **WHEN** `ComposerScreen` renders
- **THEN** the summary line's language label is `"Japanese"`

#### Scenario: Chip label shows overflow count for multi-language selection

- **GIVEN** `ComposerState.selectedLangs == listOf("en-US", "ja-JP", "es-MX")`
- **WHEN** `ComposerScreen` renders
- **THEN** the summary line's language label is `"English +2"`

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

### Requirement: Material 3 Expressive treatment for the Post button and counter

The system SHALL render the Post action as the floating toolbar's `StandardFloatingActionButton`: enabled when the draft can be posted, rendered at reduced emphasis with `semantics { disabled() }` and a no-op click when it cannot, and showing `NubecitaWavyProgressIndicator` (tap disabled) while `Submitting`. The counter arc tone shifts at 240 (warning) and 290 (error) graphemes; when `graphemeCount > 300` the counter is the over-limit signal (the borderless field has no outline).

#### Scenario: Submitting button shows wavy progress

- **WHEN** `state.submitStatus == ComposerSubmitStatus.Submitting`
- **THEN** the Post FAB renders `NubecitaWavyProgressIndicator` and taps are ignored

#### Scenario: Disabled FAB is announced as disabled

- **GIVEN** an empty draft with no attachments
- **WHEN** a screen reader focuses the Post FAB
- **THEN** it is announced as disabled and tapping it does nothing

#### Scenario: Counter color band at 240

- **WHEN** `state.graphemeCount` transitions from 239 to 240
- **THEN** the counter arc tone changes to the M3 tertiary/warn token

#### Scenario: Counter color band at 290

- **WHEN** `state.graphemeCount` transitions from 289 to 290
- **THEN** the counter arc tone changes to the M3 error token

#### Scenario: Over-limit input border

- **WHEN** `state.isOverLimit == true`
- **THEN** the counter shows the M3 error tone and the Post FAB is disabled

### Requirement: Top-bar action row reserves space for a future drafts entry point

`ComposerScreen`'s top app bar SHALL hold the close action (navigation slot) and the character counter (actions slot), positioned so that an additional icon button can be inserted beside the counter without forcing a relayout or pushing actions off-screen. The reservation MUST be noted with `// reserved for drafts entry point` near the action row. Post is not in the top bar.

#### Scenario: Top-bar layout has room to grow

- **WHEN** a hypothetical `IconButton` is inserted next to the counter in `ComposerScreen`'s top app bar
- **THEN** close, the new button and the counter render fully visible at Compact width without truncation or overflow

### Requirement: Suggestion list renders inline above the IME

When `state.typeahead` is `Suggestions` or `NoResults`, `ComposerScreenContent` SHALL render suggestions inline in an M3 `OutlinedCard` (`LazyColumn(heightIn(max = 240.dp))`) directly below the text field inside the scrolling body, and bring the list into view when it appears so it is not hidden behind the keyboard or the floating toolbar. Each row shows avatar, display name, and `@handle`, tapping which dispatches `TypeaheadResultClicked`. It does not render in `Idle` or `Querying`.

#### Scenario: Suggestions visible only in Suggestions or NoResults

- **WHEN** `state.typeahead` is `Idle` or `Querying(...)`
- **THEN** the typeahead container SHALL NOT be present in the composition

#### Scenario: Suggestion rows tap dispatches TypeaheadResultClicked

- **WHEN** a suggestion row is tapped
- **THEN** the host MUST dispatch `ComposerEvent.TypeaheadResultClicked(actor)` with the actor backing the tapped row

#### Scenario: Suggestions near the bottom of a long draft are brought into view

- **GIVEN** a long draft scrolled so the cursor is just above the toolbar
- **WHEN** typing `@al` produces `Suggestions`
- **THEN** the body scrolls so the suggestion card is visible above the floating toolbar
