# feature-feed-translation

## ADDED Requirements

### Requirement: Translation never runs during a scroll
Translation work SHALL be dispatched only when the feed's `LazyListState.isScrollInProgress` transitions to `false`, for visible items only, on `Dispatchers.Default`.

#### Scenario: The 120hz bar holds
- **WHEN** the same scroll is measured on a 120Hz device with translation off and on
- **THEN** janky frames stay within noise (measured 2.01% off vs 1.77% and 1.65% on, p50 5 ms throughout, with cold model downloads running concurrently), and any change to this gate is re-measured rather than argued

#### Scenario: Nothing dispatched mid-fling
- **WHEN** the reader flings the timeline past twenty foreign-language posts
- **THEN** no translation is started until the list comes to rest, and then only for the posts still visible

#### Scenario: Already-translated posts are not re-translated
- **WHEN** the list settles again over a post that has already been translated into the current target language
- **THEN** the cached translation is used and no engine call is made

### Requirement: Detection uses declared language first, identification second, neither as a gate
A post SHALL be considered foreign when the record's `langs` contains no language in the reader's resolved language set. When `langs` is absent or empty **and** the master toggle is on, ML Kit language identification SHALL be consulted as a fallback. Absence of `langs` SHALL NOT by itself suppress the affordance, and identification SHALL NOT by itself be treated as authoritative.

#### Scenario: Unlabelled foreign post is still offered
- **WHEN** a post carries no `langs` and the feature is on
- **THEN** identification runs and the chip is offered if the identified language is not one the reader reads

#### Scenario: Hashtag-heavy post is not missed because of identification
- **WHEN** a post declares `de` but reads as English to the identifier (a short greeting followed by English hashtags)
- **THEN** the declared `de` wins and the chip is offered

### Requirement: The chip is free when the feature is off
With the master toggle off, detection SHALL use the record's `langs` only — a comparison against the reader's language set, with no model loaded and no inference performed — and the chip SHALL still be shown on posts that declare a language the reader does not read.

#### Scenario: A reader who never translates pays nothing
- **WHEN** the master toggle is off
- **THEN** no identification or translation model is resident, no inference runs, and the only added work per post is a string comparison

### Requirement: A tap in translated text goes to the original target or nowhere
Facet targets SHALL come from the original record, and original byte offsets SHALL NEVER be remapped onto translated text.

#### Scenario: Each facet kind is reconciled independently by count
- **WHEN** a translated post is reconciled against the original facet set
- **THEN** for each kind (link, mention, tag) separately, candidate spans are located in the output tolerating the artifacts translation introduces (`@ `, `# `, `https: //`), and if the candidate count equals the original count for that kind, each candidate's text is replaced with the original span's text and the original facet feature attached, in order

#### Scenario: Restoring a longer span does not shift the spans after it
- **WHEN** a restored span is longer than the candidate it replaces
- **THEN** every later facet still indexes its own span, because the output is built by appending segments and each byte slice is computed against the string being built, never patched in place

#### Scenario: Hashtag renders and resolves as authored
- **WHEN** a post containing `#PalacesandGardens` is translated and the engine emits `#palaceandGarden`
- **THEN** the reader sees `#PalacesandGardens` and tapping it opens the tag the author wrote

#### Scenario: Unreconcilable facets lose their links rather than acquiring wrong ones
- **WHEN** the candidate count for a facet kind does not match the original count
- **THEN** that kind renders as plain, non-tappable text, and the mismatch is counted

#### Scenario: Emoji and unfaceted text are unaffected
- **WHEN** a translated post contains emoji
- **THEN** they render unchanged

### Requirement: Original first, shimmer while working, swap in place
The original body SHALL render immediately. While a translation is in flight the body SHALL be covered by a shimmer. On completion the body SHALL swap to the translation with an animation, in place, without changing the post's identity or scroll anchor.

#### Scenario: Reader never sees an empty card
- **WHEN** a post is awaiting translation
- **THEN** the original text or a shimmer over it is shown, never blank space

### Requirement: Attribution and reversal are always present on a translation
Wherever translated text is displayed, the UI SHALL show the source language and credit the engine, and SHALL offer a one-tap return to the original. The reader's choice to show the original SHALL persist for the session and survive scrolling the post out of view and back.

#### Scenario: Reader returns to the original and it sticks
- **WHEN** the reader taps "Show original", scrolls away and scrolls back
- **THEN** that post still shows the original

### Requirement: Accepting a language states its cost and needs one prompt
Tapping the chip SHALL translate that post and offer the language as automatic in a single prompt that states the download size. Accepting SHALL add the language to the auto-translate list and enable the master toggle.

#### Scenario: Size is stated before any download
- **WHEN** the reader is offered automatic translation for a language that is not downloaded
- **THEN** the prompt names the size and the Wi-Fi requirement before anything is downloaded

#### Scenario: Accepting on cellular is not silently dropped
- **WHEN** the reader accepts while on a metered connection
- **THEN** the chip shows a waiting-for-Wi-Fi state and the download starts when Wi-Fi is available
