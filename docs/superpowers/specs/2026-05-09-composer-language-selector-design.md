# Composer language selector — design

**Date:** 2026-05-09
**bd:** `nubecita-oae` — *feat(feature/composer): per-post language selector via toolbar globe icon*
**Driver:** V1 follow-up to `nubecita-wtq.12` (PR #146 — device-locale `langs` default). Adds a per-post override picker so users can attach up to 3 BCP-47 language tags, matching how the official Bluesky client tags multilingual content.
**Scope:** Globe icon in the composer chrome → bottom-sheet / popup picker → updates `ComposerState.selectedLangs` → flows through to `PostingRepository.createPost(langs = ...)`.

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

```kotlin
val initial = state.selectedLangs ?: listOf(localeProvider.primaryLanguageTag())
```

The picker reads the `LocaleProvider` we added in `wtq.12` via Hilt (its second consumer). On first open the picker shows the device locale checked; on subsequent opens it shows whichever set the user last committed. The picker never sees the JVM's raw `Locale.getDefault()` — `LocaleProvider`'s test-friendliness keeps screen-level UI tests deterministic across machines.

### 5. UI details

| Decision | Resolution |
|---|---|
| **Search** | Top-of-picker `TextField`. Filter applies to BOTH the localized display name AND the BCP-47 tag, so `"en"` finds English and `"anglais"` finds English when the device is French. ~75 entries scrolls fine without search, but typing 2-3 chars is faster than thumb-scrolling — standard picker UX. |
| **Sort order** | Currently-selected tags pinned at top; then the device-locale tag (if not already selected); then everything else alphabetical by `getDisplayName(Locale.getDefault())`. Selection-pinning matches what users expect when re-opening. |
| **Cap-of-3 enforcement** | When `selectedLangs.size == 3` the unchecked checkboxes render `enabled = false` (greyed out via M3 disabled tonal treatment). No snackbar — silent disabled state matches the standard Compose idiom and Bluesky does the same. The reducer also defends defensively for any event that would push past 3. |
| **Done / Cancel** | Bottom-aligned action row in the picker — `Done` (filled, dispatches `LanguageSelectionConfirmed`); `Cancel` (text, no event). Drag-down on the bottom sheet, scrim tap on the popup, and back-press all map to Cancel. |
| **Globe icon visual state** | `selectedLangs == null` → bare globe icon. `selectedLangs.size == 1` → globe + small label below it showing the tag's `Locale.getLanguage()` uppercase (e.g., `"EN"`, `"JA"`). `selectedLangs.size >= 2` → globe + first tag's code + `"+N"` (`"EN +1"`, `"EN +2"`). Mirrors how the official Bluesky client surfaces the active selection. |

### 6. Module + file layout

| Where | What |
|---|---|
| `core/posting/src/main/kotlin/.../BlueskyLanguageTags.kt` | New — `val BLUESKY_LANGUAGE_TAGS: ImmutableList<String>` ported verbatim from bsky-app's `LANGUAGES` constant. |
| `feature/composer/impl/src/main/kotlin/.../internal/LanguagePickerContent.kt` | New — pure stateless `@Composable`. `LazyColumn` of `LanguagePickerRow`s + search field + Done/Cancel footer. Takes `(allTags, draft, onToggle, onConfirm, onDismiss)`. |
| `feature/composer/impl/src/main/kotlin/.../internal/LanguagePicker.kt` | New — adaptive wrapper choosing `ModalBottomSheet` vs `Popup` by `currentWindowAdaptiveInfoV2()`. |
| `feature/composer/impl/src/main/kotlin/.../internal/ComposerLanguageIconButton.kt` | New — globe `IconButton` with the dynamic label state described in 5. |
| `feature/composer/impl/src/main/kotlin/.../state/ComposerState.kt` | Modified — add `selectedLangs: List<String>? = null` field. |
| `feature/composer/impl/src/main/kotlin/.../state/ComposerEvent.kt` | Modified — add `LanguageSelectionConfirmed(tags: List<String>)`. |
| `feature/composer/impl/src/main/kotlin/.../ComposerViewModel.kt` | Modified — handle the new event; pass `state.selectedLangs` to `createPost`. |
| `feature/composer/impl/src/main/kotlin/.../ComposerScreen.kt` | Modified — wire the icon into the existing top-app-bar action row (the slot reserved by the unified-composer spec for "future drafts entry icon"); host the picker with a `var showPicker by rememberSaveable { mutableStateOf(false) }` flag. |
| `openspec/specs/feature-composer/spec.md` | Modified — new requirement describing the picker UI contract. Direct edit (matches PR #144 / #146 pattern). |

### 7. Toolbar coupling — deliberately deferred

`nubecita-86m` (the M3 `HorizontalFloatingToolbar`) is the natural eventual home for the globe icon, but bundling this PR with the toolbar refactor would balloon the blast radius. For V1 the icon lives in the top-app-bar action slot. When `nubecita-86m` ships, the icon's `IconButton` moves from top-bar to toolbar in a 5-line refactor — no spec change, no state-shape change.

### 8. Test plan

- **Unit (`ComposerViewModelTest`)**: `LanguageSelectionConfirmed(tags)` reducer maps to `state.selectedLangs == tags`. Cap-of-3 defensive path: a 4-tag event no-ops. Submit with `selectedLangs == null` → fake repo receives `langs = null`. Submit with `selectedLangs == listOf("ja-JP")` → fake repo receives `langs = listOf("ja-JP")`.
- **Compose UI (`LanguagePickerContentTest`)**: search filter narrows by tag and by display name. Disabled checkboxes when 3 are selected. Done dispatches the right tags. Cancel doesn't dispatch.
- **Screenshot fixtures (`@PreviewNubecitaScreenPreviews` from PR #145)**: picker-open at Compact (bottom sheet), picker-open at Foldable + Tablet (popup overlay over composer dialog). Picker-with-3-selected showing the disabled-checkbox treatment. Picker with search active.
- **Composer screen integration**: globe-icon visual state at null / 1-selected / 3-selected.

### 9. Acceptance

- Tapping the globe icon opens the picker preselected with whichever set is currently effective (override or device-locale fallback).
- Selecting up to 3 languages and tapping Done updates `ComposerState.selectedLangs` and the globe icon's label.
- Submission with non-null `selectedLangs` carries those exact tags on the post record (verified via Compose UI test against a fake `PostingRepository`).
- Submission with `null selectedLangs` continues to derive from the device locale per `wtq.12`.
- `openspec validate --all --strict` passes.

## Out of scope (file as separate bd issues if pursued)

- **D3 persistence** — "remember last-selected langs across composer sessions". Lands with `nubecita-4ok` (drafts) work or its own DataStore slot keyed by signed-in DID.
- **Auto-detection** of post language from text content (cld3 / character-set heuristics). The official Bluesky client does this; we ship V1 with manual selection and add detection if feeds report tagging-accuracy issues.
- **Display-language fine-tuning** — region-qualified tags via `getDisplayCountry`, e.g., `"English (United States)"` vs `"English"`. Defer until users complain.
- **Search input localization beyond `Locale.getDefault()`**. Standard display-name approach is enough.
- **Migrating the icon into the M3 `HorizontalFloatingToolbar`** — `nubecita-86m`. Tracked separately; the V1 picker ships in the top-app-bar action slot.
