# Composer language selector — design

**Date:** 2026-05-09
**bd:** `nubecita-oae` — *feat(feature/composer): per-post language selector via toolbar globe icon*
**Driver:** V1 follow-up to `nubecita-wtq.12` (PR #146 — device-locale `langs` default). Adds a per-post override picker so users can attach up to 3 BCP-47 language tags, matching how the official Bluesky client tags multilingual content.
**Scope:** M3 `AssistChip` (globe leading icon + dynamic label) in a dedicated chip row in the composer → tap opens a bottom-sheet / popup picker → updates `ComposerState.selectedLangs` → flows through to `PostingRepository.createPost(langs = ...)`.

## Why

`wtq.12` shipped a device-locale default. Common case (post-in-your-own-language) is correct without UI. Power-users — bilingual / multilingual posters, anyone publishing in a language other than their device default — currently have no way to override. Without a picker, posts in the "wrong" language get filtered out of locale-curated Bluesky feeds the user is actually trying to reach.

## Decisions

### 1. Source of the language list — port Bluesky's official `LANGUAGES` constant

The AT Protocol lexicon does NOT provide a `getLanguages` / `listLanguages` endpoint — verified by grepping `~/code/kikinlex/generator/lexicons`. The protocol's `"format": "language"` constraint is purely a BCP-47 string-format validator at the field level; any tag is valid, the appview decides what to do with it. Bluesky's official client must be hardcoding its picker list.

Three candidate sources were considered and rejected:

- **`Locale.getAvailableLocales()`**: 190+ entries on Android, varies by manufacturer (Samsung / Pixel / Xiaomi each ship different sets), surfaces dev pseudo-locales (`en-XA`) and obscure regional dialects. Long, noisy, varying — wrong UX shape.
- **A hand-curated Nubecita list**: we'd diverge from what users see in the official client; risk omitting niche languages that Bluesky's curators included for good product reasons.
- **Mirror Bluesky's `bsky-app` `LANGUAGES` constant verbatim** *(picked)*: ~75 BCP-47 tags Bluesky has already curated. Users transitioning from the official client see the same options. Sync cost is one drive-by edit when their list changes (rare in practice). Stored in `:core:posting/.../BlueskyLanguageTags.kt` as a `val`-level constant — posting-domain data, not UI.

Display-name rendering uses the Android framework's existing localization: `Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())`. A French phone shows "Anglais"; an English phone shows "English". Zero translation tables shipped.

### 2. State ownership — `ComposerState.selectedLangs: List<String>? = null`

Flat field on `ComposerState`, matching how every other authoring field is shaped (`attachments`, `replyParentLoad`, `submitStatus`, etc). The nullable semantics are deliberate:

- `null` — user hasn't touched the picker. At submit, VM passes `langs = null` to `PostingRepository.createPost`, and `wtq.12`'s repo-side device-locale default kicks in. Backwards-compatible with everything that ships today.
- non-null list (incl. empty) — user explicitly committed a selection in the picker. VM passes that exact list. Empty list = "deliberately no langs", honored without device-locale fallback.

This avoids the `hasUserOverriddenLanguage: Boolean` anti-pattern (a parallel flag that has to stay in sync with the list field).

`ComposerEvent` gains:

```kotlin
data class LanguageSelectionConfirmed(val tags: List<String>) : ComposerEvent
```

Reducer: `state.copy(selectedLangs = event.tags)`. Cap of 3 enforced defensively in the reducer (no-op if `event.tags.size > 3`); UI also enforces via disabled checkboxes (next decision).

`ComposerViewModel.handleEvent(Submit)` becomes:

```kotlin
postingRepository.createPost(
    text = textFieldState.text.toString(),
    attachments = state.attachments,
    replyTo = state.replyParentLoad?.refsOrNull(),
    langs = state.selectedLangs,                // ← new
)
```

### 3. UI surface — width-class branching, mirrors `ComposerDiscardDialog`

The picker is built as a stateless content composable wrapped by an adaptive container:

| Width class | Wrapping primitive | Rationale |
|---|---|---|
| **Compact** (phone) | `ModalBottomSheet { LanguagePickerContent(...) }` | Bottom sheets are the standard Compose pattern for mobile pickers — easy thumb reach, native scrolling for ~75-row lists. |
| **Medium / Expanded** (foldable + tablet) | `Popup { Surface(widthIn(max = 480.dp)) { LanguagePickerContent(...) } }` | The composer is itself a Compose `Dialog` at Medium/Expanded (per `wtq.7`'s adaptive container). Stacking a `ModalBottomSheet` inside a `Dialog` would either clip to the dialog's window or paint a second scrim layer — same problem `ComposerDiscardDialog` solved by using a `Popup` for the inner overlay. We reuse that pattern verbatim. The 480dp width cap matches the discard-dialog width budget at Expanded. |

Selection-while-the-picker-is-open is **local to the picker** — `var draft by remember { mutableStateOf(initial) }`. Only on Done does it dispatch into VM state. Flipping checkboxes inside the picker without confirming is a no-op against `ComposerState`. Cancel + drag-down-dismiss + scrim-tap all map to the same "no commit" path.

### 4. Initial selection on open

The chip and the picker both need the device-locale fallback to render labels and preselected checkboxes. To keep `LocaleProvider` access centralized, `ComposerViewModel` reads it once at init and exposes the resolved tag as a stable property (`val deviceLocaleTag: String`). The chip and the picker take it as a constructor parameter:

```kotlin
val initial = state.selectedLangs ?: listOf(viewModel.deviceLocaleTag)
```

On first open the picker shows the device locale checked; on subsequent opens it shows whichever set the user last committed. Neither the chip nor the picker accesses `LocaleProvider` directly — `wtq.12`'s abstraction keeps a single VM-level read site, and screen-level UI tests stay deterministic by configuring the VM's resolved `deviceLocaleTag` rather than fighting the JVM's `Locale.getDefault()`.

### 5. UI details

| Decision | Resolution |
|---|---|
| **Entry-point composable** | M3 `AssistChip` with leading globe icon + dynamic label, NOT a bare `IconButton`. Rationale: the chip surfaces the active language at a glance (no need to open the picker just to check what's currently set), and the chip-row layout is forward-compatible with the next composer chips (visibility/threadgate, drafts) without further chrome changes. `AssistChip(leadingIcon = { Icon(Globe, ...) }, label = { Text(displayText, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.widthIn(max = 200.dp))`. The 200dp width cap defends against pathological display names like `"Norwegian Bokmål, Esperanto, Cantonese"`. |
| **Chip placement** | Dedicated chip row between the text field (or typeahead surface, when active) and the attachment chips: `TextField → (TypeaheadList) → ComposerOptionsChipRow → AttachmentRow → IME`. The row hosts only the language chip in V1 — when `nubecita-86m`'s toolbar lands, the row migrates into the toolbar's content slot wholesale. The top-app-bar action area stays for true navigation chrome (close button + future drafts entry icon). |
| **Chip label when `selectedLangs == null`** | Shows the resolved device-locale display name — e.g., `🌐 English` if the device locale is `en-US`. Rationale: the chip should always tell the truth about what's about to be sent. `wtq.12`'s repo-side default means a `null` selection still ships the device locale on the record; a `🌐 Add language` placeholder would imply an empty state that doesn't actually exist. |
| **Chip label when `selectedLangs.size == 1`** | First (only) tag's localized display name — `🌐 Japanese` for `["ja-JP"]` on an English device. |
| **Chip label when `selectedLangs.size >= 2`** | First tag's display name + `"+N"` overflow — `🌐 English +1` (2 selected), `🌐 English +2` (3 selected at cap). Predictable chip width regardless of selection count; matches the official Bluesky client. The full list is revealed when the user re-opens the picker. |
| **Search** | Top-of-picker `TextField`. Filter applies to BOTH the localized display name AND the BCP-47 tag, so `"en"` finds English and `"anglais"` finds English when the device is French. ~75 entries scrolls fine without search, but typing 2-3 chars is faster than thumb-scrolling — standard picker UX. |
| **Sort order** | Currently-selected tags pinned at top; then the device-locale tag (if not already selected); then everything else alphabetical by `getDisplayName(Locale.getDefault())`. Selection-pinning matches what users expect when re-opening. |
| **Cap-of-3 enforcement** | When `selectedLangs.size == 3` the unchecked checkboxes render `enabled = false` (greyed out via M3 disabled tonal treatment). No snackbar — silent disabled state matches the standard Compose idiom and Bluesky does the same. The reducer also defends defensively for any event that would push past 3. |
| **Done / Cancel** | Bottom-aligned action row in the picker — `Done` (filled, dispatches `LanguageSelectionConfirmed`); `Cancel` (text, no event). Drag-down on the bottom sheet, scrim tap on the popup, and back-press all map to Cancel. |

### 6. Module + file layout

| Where | What |
|---|---|
| `core/posting/src/main/kotlin/.../BlueskyLanguageTags.kt` | New — `val BLUESKY_LANGUAGE_TAGS: ImmutableList<String>` ported verbatim from bsky-app's `LANGUAGES` constant. |
| `feature/composer/impl/src/main/kotlin/.../internal/LanguagePickerContent.kt` | New — pure stateless `@Composable`. `LazyColumn` of `LanguagePickerRow`s + search field + Done/Cancel footer. Takes `(allTags, draft, onToggle, onConfirm, onDismiss)`. |
| `feature/composer/impl/src/main/kotlin/.../internal/LanguagePicker.kt` | New — adaptive wrapper choosing `ModalBottomSheet` vs `Popup` by `currentWindowAdaptiveInfoV2()`. |
| `feature/composer/impl/src/main/kotlin/.../internal/ComposerLanguageChip.kt` | New — `AssistChip` (globe leading icon + dynamic label per decision 5), takes `(selectedLangs, deviceLocaleTag, onClick)`. Pure stateless composable. |
| `feature/composer/impl/src/main/kotlin/.../internal/ComposerOptionsChipRow.kt` | New — horizontal row container hosting the language chip (and, in the future, visibility/threadgate / drafts chips). V1 ships with one occupant; the row exists as the layout slot so future chips don't introduce more chrome. |
| `feature/composer/impl/src/main/kotlin/.../state/ComposerState.kt` | Modified — add `selectedLangs: List<String>? = null` field. |
| `feature/composer/impl/src/main/kotlin/.../state/ComposerEvent.kt` | Modified — add `LanguageSelectionConfirmed(tags: List<String>)`. |
| `feature/composer/impl/src/main/kotlin/.../ComposerViewModel.kt` | Modified — handle the new event; pass `state.selectedLangs` to `createPost`. The VM also exposes `deviceLocaleTag: String` (computed once at init from the injected `LocaleProvider`) so the chip and the picker can render consistent fallback labels without each pulling `LocaleProvider` separately. |
| `feature/composer/impl/src/main/kotlin/.../ComposerScreen.kt` | Modified — render `ComposerOptionsChipRow` between the text field and `ComposerAttachmentRow`. Tap on the language chip flips a `var showPicker by rememberSaveable { mutableStateOf(false) }` flag that hosts the picker. |
| `openspec/specs/feature-composer/spec.md` | Modified — new requirement describing the picker UI contract. Direct edit (matches PR #144 / #146 pattern). |

### 7. Toolbar coupling — deliberately deferred

`nubecita-86m` (the M3 `HorizontalFloatingToolbar`) is the natural eventual home for these composer options, but bundling this PR with the toolbar refactor would balloon the blast radius. For V1 the language chip lives in `ComposerOptionsChipRow` between the text field and the attachment row. When `nubecita-86m` ships, the chip-row migrates wholesale into the toolbar's content slot — no spec change, no state-shape change. The chip itself can either remain an `AssistChip` (toolbars are friendly to chips) or be replaced by an `IconButton` with the chip's label as a tooltip; that's a `nubecita-86m`-time UX choice, not a V1 commitment.

### 8. Test plan

- **Unit (`ComposerViewModelTest`)**: `LanguageSelectionConfirmed(tags)` reducer maps to `state.selectedLangs == tags`. Cap-of-3 defensive path: a 4-tag event no-ops. Submit with `selectedLangs == null` → fake repo receives `langs = null`. Submit with `selectedLangs == listOf("ja-JP")` → fake repo receives `langs = listOf("ja-JP")`.
- **Compose UI (`LanguagePickerContentTest`)**: search filter narrows by tag and by display name. Disabled checkboxes when 3 are selected. Done dispatches the right tags. Cancel doesn't dispatch.
- **Screenshot fixtures (`@PreviewNubecitaScreenPreviews` from PR #145)**: picker-open at Compact (bottom sheet), picker-open at Foldable + Tablet (popup overlay over composer dialog). Picker-with-3-selected showing the disabled-checkbox treatment. Picker with search active. Composer-with-chip-row at Compact + Foldable + Tablet showing the chip in `null` / 1-selected / 3-selected states (drives the chip's label-truncation budget).
- **Composer screen integration**: chip label state at `null` (device-locale display name), `selectedLangs.size == 1` (single display name), `selectedLangs.size == 3` (`"<first> +2"` overflow).

### 9. Acceptance

- Tapping the language chip opens the picker preselected with whichever set is currently effective (override or device-locale fallback).
- Selecting up to 3 languages and tapping Done updates `ComposerState.selectedLangs` and the chip's label.
- The chip's label always reflects what `createPost` is about to send: when `selectedLangs == null` it shows the device-locale display name; when non-null it shows the first selected language's display name (with `+N` overflow when more than one is selected).
- Submission with non-null `selectedLangs` carries those exact tags on the post record (verified via Compose UI test against a fake `PostingRepository`).
- Submission with `null selectedLangs` continues to derive from the device locale per `wtq.12`.
- `openspec validate --all --strict` passes.

## Out of scope (file as separate bd issues if pursued)

- **D3 persistence** — "remember last-selected langs across composer sessions". Lands with `nubecita-4ok` (drafts) work or its own DataStore slot keyed by signed-in DID.
- **Auto-detection** of post language from text content (cld3 / character-set heuristics). The official Bluesky client does this; we ship V1 with manual selection and add detection if feeds report tagging-accuracy issues.
- **Display-language fine-tuning** — region-qualified tags via `getDisplayCountry`, e.g., `"English (United States)"` vs `"English"`. Defer until users complain.
- **Search input localization beyond `Locale.getDefault()`**. Standard display-name approach is enough.
- **Migrating `ComposerOptionsChipRow` into the M3 `HorizontalFloatingToolbar`** — `nubecita-86m`. Tracked separately; the V1 chip-row ships between the text field and the attachment row.
