## MODIFIED Requirements

### Requirement: Translation never runs during a scroll
Translation work SHALL be dispatched only when the feed's `LazyListState.isScrollInProgress` transitions to `false`, for visible items only, on `Dispatchers.Default`.

#### Scenario: The 120hz bar holds
- **WHEN** the same scroll is measured on a 120Hz device with translation off and on
- **THEN** janky frames stay within noise (measured 2.01% off vs 1.77% and 1.65% on, p50 5 ms throughout, with cold model downloads running concurrently), and any change to this gate is re-measured rather than argued

#### Scenario: Nothing dispatched mid-fling
- **WHEN** the reader flings the timeline past twenty foreign-language posts
- **THEN** no translation is started until the list comes to rest, and then only for the posts still visible

#### Scenario: Already-translated posts are not re-translated
- **WHEN** the list settles over a post already translated into the current target language, on this or any other surface, in this process or a previous one
- **THEN** the shared or cached translation is used and no engine call is made

### Requirement: Attribution and reversal are always present on a translation
Wherever translated text is displayed, the UI SHALL show the source language and credit the engine, and SHALL offer a one-tap return to the original. The reader's choice to show the original SHALL persist for the session, survive scrolling the post out of view and back, and apply on every surface that shows the post.

#### Scenario: Reader returns to the original and it sticks
- **WHEN** the reader taps "Show original", scrolls away and scrolls back
- **THEN** that post still shows the original
