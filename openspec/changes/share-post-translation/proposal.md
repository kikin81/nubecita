Tracking: bd epic `nubecita-kb52`. PR 1 `nubecita-kb52.1`, PR 2 `nubecita-kb52.2`, PR 3 `nubecita-za60`.

## Why

A reader who taps a translated post in the feed lands on a post detail screen in the original language, with no way to translate it or its replies. Translation only exists inside the feed. Its decision logic and its state live in `:feature:feed:impl`, which no other feature may depend on, and its results live in a screen-scoped map that is gone the moment the feed leaves composition. The Room cache built for it in #1100 (`post_translation`) is never read or written, so nothing survives a restart either.

Every other surface that shows posts (search, bookmarks, liked posts, profiles) has the same gap. Fixing post detail by copying the feed's code would be the first of four copies.

## What Changes

- New `:core:post-translation` module owns *what* a post's translation state is: the coordinator (moved from the feed, unchanged in behaviour) and an app-wide `PostTranslations` store that every surface reads and writes. The store reads the `post_translation` cache before calling the engine and writes every completed translation to it.
- New `:core:post-translation-ui` module owns *when* translation runs on a screen: a `rememberPostTranslations(listState, visiblePosts, …)` helper carrying the feed's settle gate unchanged, and the auto-translate offer dialog with its strings (moved from the feed, translations included).
- The feed moves onto both modules. No behaviour change: same gate, same states, same analytics, same screenshots.
- Post detail translates its ancestors, focus post and replies under the same rules as the feed: languages on the reader's auto list translate on settle with the shimmer, other foreign languages show the chip, and a chip tap makes the same once-per-language offer.
- A translation made on any surface shows on every surface that displays that post, including after a restart. "Show original" is shared across surfaces for the rest of the session.
- Follow-ups (separate bd issues, not this change): wire search, bookmarks, liked posts and profiles to the same helper.

## Non-goals

- No new translation behaviour. Detection, the settle gate, Wi-Fi gating, the offer-once rule and facet reconciliation are unchanged; this change makes them reusable.
- No translation in the composer, notifications, widgets or Wear.
- "Show original" is not persisted across process death (as today).
- No change to `PostCard` or `PostTranslationUi`. Both already carry translation for any host.

## Capabilities

### New Capabilities
- `post-translation`: one app-wide answer to "what does this post's translation look like", shared by every surface that shows posts, backed by the persistent cache, and kept consistent with the reader's language preferences as they change.

### Modified Capabilities
- `feature-feed-translation`: already-translated posts are served from the shared store and the persistent cache, not a screen-local map, so they survive leaving the feed and restarting the app; the reader's "show original" choice holds across surfaces for the session.
- `feature-postdetail`: thread posts (ancestors, focus, replies) translate under the same rules as the feed.

## Impact

- **New modules:** `:core:post-translation` (library + Hilt; depends on `:core:translation`, `:core:database`, `:core:preferences`, `:core:analytics`, `:core:common`, `:data:models`, and `compose-runtime` for snapshot state) and `:core:post-translation-ui` (library + Compose + Hilt).
- **`:feature:feed:impl`:** loses `translation/` (coordinator, state, tests) and the offer dialog and strings; `FeedViewModel` injects `PostTranslations` instead of the coordinator.
- **`:feature:postdetail:impl`:** gains a dependency on both modules; `PostDetailViewModel` exposes `PostTranslations` the same way the feed does today; `PostDetailScreen` passes translation state to each thread `PostCard`.
- **`:core:database`:** `TranslationDao` gains its first caller. No schema change.
- **Architecture notes:**
  - The store holds Compose **snapshot state in a `@Singleton`**, not a `StateFlow<Map>`. One flow carrying every post's state would recompose every visible card on each change, against the 120hz requirement; a snapshot map recomposes only the card whose entry changed. That is the reason for the `compose-runtime` dependency in a non-UI module, which `:data:models` already takes for its stability annotations.
  - The ViewModel exposing an injected dependency to its screen follows the feed's existing precedent (`FeedViewModel.translationCoordinator`). Translation state stays outside `UiState`, as it does today.
