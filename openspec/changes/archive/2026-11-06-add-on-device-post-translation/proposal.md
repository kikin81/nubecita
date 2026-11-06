# Add On-Device Post Translation

> Beads epic: `nubecita-s6xk` (tasks `.1`–`.7`). Source design: `docs/superpowers/specs/2026-11-05-on-device-post-translation-design.md`.

## Why

A reader scrolling their timeline hits a post in a language they don't read. Today the only answer is the overflow sheet's "Translate post", which deep-links out to the Google Translate app — and in practice arrives there with no text, so the reader gets an empty translator and has lost their place in the feed.

The target is in-place translation: the post body swaps to the reader's language, in the feed, with no network round-trip and no app switch.

This epic previously scoped itself to **post detail only**, on the stated grounds that translating in the feed "risks the 120hz scrolling requirement". A spike measured that assumption on a Pixel 11 Pro (120Hz panel, bench flavor) and it does not hold, provided translation is gated on scroll-idle:

| Run | Frames | Janky | p50 | p90 | p99 |
|---|---|---|---|---|---|
| Control (translation off) | 1644 | **2.01%** | 5 ms | 8 ms | 23 ms |
| Translation on | 1638 | **1.77%** | 5 ms | 8 ms | 23 ms |
| Translation on (second run) | 969 | **1.65%** | 5 ms | 8 ms | 26 ms |

The "on" runs included two concurrent cold model downloads. The difference is inside run-to-run noise, so this change is **feed-first**; post detail becomes the additive follow-up (its cost was measured too — a 24-post thread, warm model, is 877 ms total / 36 ms per post).

## What Changes

- New `:core:translation` module: an SDK-agnostic seam (`TranslationEngine`, `LanguageIdentifier`, `TranslationModelRepository`) with an ML Kit implementation. No ML Kit type escapes the module, mirroring how `:core:billing` contains RevenueCat.
- `PostUi` gains `langs` (the record's ISO-639 tags), plumbed through `:core:feed-mapping` and `:core:feed-cache`. Used as a **hint**, never a gate.
- A **language chip** on untranslated foreign posts in the feed ("Translate from Japanese"), on the card rather than in the overflow sheet. Tapping it translates that post and offers to make the language automatic.
- With a language on the auto list: the original renders first, a shimmer covers the body while the call is in flight, and the body **swaps in place** with an animation. Dispatch is gated on `listState.isScrollInProgress` going false — **nothing translates during a fling**.
- **Facet restoration** by count-matched ordinal mapping, never offset remapping. Translation mangles facets in measured, language-specific ways (`#PalacesandGardens` → `#palaceandGarden`; `@alice` → `@ alice`; Japanese breaks URLs and translates hashtag text). A facet kind whose count cannot be reconciled renders as **plain text** rather than linking somewhere wrong.
- A **Translation settings sub-page**: master toggle (default **off**), auto-translate languages, never-translate languages ("languages I read" — which doubles as the reader-language source, see below), and downloaded languages with their actual on-disk sizes and a delete action.
- The master toggle changes **how detection works**, not whether the chip exists. Off (default): detection is the record's `langs` alone — a field comparison, no model, no inference, **zero cost** to a reader who never translates, and the chip still appears so the feature is discoverable. On: ML Kit language identification additionally catches unlabelled posts, and auto-translation runs on scroll-idle.
- Translations cached in `:core:database` keyed by **(post URI, target language)**, bounded and evictable.
- **The reader's languages come from the device plus one local list.** `app.bsky.actor` at SDK 9.13.0 has no content-language preference (thirteen `*Pref` types, none language-related), so the epic's original "prefer the atproto content-language preference" is unimplementable. Resolution: device `LocaleListCompat` union the never-translate list, which does double duty as "languages I read".
- Model downloads are **Wi-Fi only** and per-language consented, with the reason surfaced rather than failing silently.
- `"Translated from <language> · Google"` plus `"Show original"` shown permanently wherever a translation is displayed.
- New user-facing strings with `es-419` + `pt-BR` translations.

Scope is **feed posts only**. Threads, quoted posts, DMs, profile bios and alt text are explicitly out of scope and additive later. No breaking changes.

## Capabilities

### New Capabilities
- `core-translation`: the SDK-agnostic translation boundary — engine, language identification, model lifecycle (Wi-Fi-gated download, on-disk size reporting, delete, close-on-background), and the rule that no engine type crosses the module edge.
- `feature-feed-translation`: the feed's translation surface — language chip, scroll-idle dispatch, in-place swap with shimmer, attribution and reversal, progressive opt-in prompt, and the facet-restoration contract that governs what a tap does in translated text.
- `feature-settings-translation`: the Translation settings sub-page — master toggle, auto-translate and never-translate lists, and the downloaded-languages storage surface.

### Modified Capabilities
- `data-models`: `PostUi` gains `langs`.

## Impact

- **New module**: `:core:translation` (`nubecita.android.library` + `nubecita.android.hilt`; depends on `com.google.mlkit:translate`, `com.google.mlkit:language-id`, `kotlinx-coroutines-play-services`, `:data:models`). Needs an empty `consumer-rules.pro` or CI Build/Lint fails.
- **Modified**: `:data:models` (`PostUi.langs` + fixtures), `:core:feed-mapping`, `:core:feed-cache` (retain `langs`; bench fixture gains foreign-language posts), `:core:database` (translation cache table + schema bump + `@AutoMigration`), `:core:preferences` (translation preferences), `:feature:feed:impl` (chip, swap, scroll-idle controller), `:feature:settings:impl` (Translation sub-page), `:designsystem` (chip + attribution row in `PostCard`).
- **Storage**: ML Kit models land in `no_backup/com.google.mlkit.translate.models/` and count as app data. Measured at **~45 MB per language** (Japanese 62 MB) because ML Kit downloads both directions of a pair; five languages cost **241.8 MB**. This is why the feature defaults off and why storage is a first-class settings surface.
- **Memory**: the spike reached ~400 MB RSS with five translators loaded. Translators are closed when the app backgrounds.
- **Network**: none at translation time. Model download only, Wi-Fi only.
- **Tests**: `:core:translation` unit tests against fakes; property tests for facet restoration; VM unit tests; screenshot baselines for chip / shimmer / translated states, generated by CI's `update-baselines` label.
- **Retained**: the existing `googleTranslateUrl` overflow deep link stays as the fallback for language pairs ML Kit does not support.
