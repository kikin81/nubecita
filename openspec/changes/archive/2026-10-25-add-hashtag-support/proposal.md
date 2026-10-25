## Why

Users have asked for hashtag support, and today hashtags only half-work in Nubecita:

- **Posts we publish don't carry tags.** The composer's `DefaultFacetExtractor` emits only mention and link facets, so a `#tag` typed in Nubecita goes out as plain text. Tag search, and every other client's tag view, can't find it.
- **Tags in other people's posts are dead ends.** `PostCard` colors a `FacetTag` like a link but makes it untappable (the comment at `PostCard.kt` reads: "tag search needs a query route").

Every mainstream Bluesky client makes tags tappable, including the official app and Skywalker, Graysky, Tokimeki and deck.blue. Research done on 2026-10-24 is summarized in `design.md`.

## What Changes

- **Publish tag facets.** The composer emits `app.bsky.richtext.facet#tag` for `#hashtags` and for `$CASHTAGS`, the latter as tag `$TICKER`. The detection rules match the official client's regexes: a tag must start a word, digit-only tags are rejected, trailing punctuation is stripped, and tags are capped at 64 graphemes.
- **Hashtag screen.** A new `Hashtag(tag, author?)` route in `:feature:search` shows posts tagged `#tag`.
  - It has Top and Latest tabs with paging.
  - It applies the same moderation filtering as post search.
  - When `author` is set, it shows an optional "From @handle" filter.
  - It has a share action that shares `https://bsky.app/hashtag/<tag>`.
  - It is backed by `app.bsky.feed.searchPostsV2` with the structured `hashtags` filter.
- **Tappable tags.** `FacetTag` spans in post text become clickable through a new `FacetTarget.Tag`. Every host that renders post text routes a tag tap to the Hashtag screen, carrying the post author so the screen can offer the author filter.

## Non-goals

These are tracked as follow-up bd issues outside this epic:

- **Muting a tag (`nubecita-gt3m`).** It depends on muted-words support (`nubecita-th1l`). Nubecita doesn't yet read, write or apply `mutedWordsPref`, and the AppView doesn't filter muted words server-side, so a "Mute #tag" button would do nothing.
- **Long-press tag menu (`nubecita-gt3m`).** A tap goes straight to the screen, matching the official native app. The menu only becomes worthwhile once it can offer Mute.
- **Hashtag autocomplete in the composer (`nubecita-9j60`).**
- **Pinning a tag as a feed tab (`nubecita-tyxd`).** `savedFeed` has no tag type, so a pin would be per-device.
- **Composer syntax highlighting** of tags, mentions and links while typing. The composer highlights nothing today, and that is a separate change.
- **Editing the outline `tags` record field.** The official client doesn't expose it either.
- **Trending topics.**

## Capabilities

### New Capabilities
- `hashtags`: covers three things:
  - publishing tag and cashtag facets (detection rules);
  - the Hashtag route and screen (data source, tabs, author filter, share);
  - tag-tap routing from rendered post text.

### Modified Capabilities
<!-- None: existing specs don't define tag behavior; all hashtag requirements live in the new capability. -->

## Impact

- **`:core:posting`**: `DefaultFacetExtractor` gains tag and cashtag detection, as a new pure `TagDetector` helper.
- **`:data:models`**: `FacetTarget.Tag(tag, authorHandle?)`.
- **`:designsystem`**: `PostCard` rich text turns `FacetTag` spans into `LinkAnnotation.Clickable`. This affects `PostCard` screenshot baselines only if styling changes, and it shouldn't.
- **`:feature:search:api` / `:impl`**: new `Hashtag` NavKey, `HashtagViewModel` and `HashtagScreen`, plus a `searchHashtag` repository method and the bench fake. `:feature:search:impl` is flavored, so it needs a bench implementation.
- **Hosts handling `FacetTarget`**: feed, post detail, profile, bookmarks, notifications, chats and search results each add a `Tag` branch, since a `when` over the sealed interface won't compile without one.
- **New strings** (en, es-419, pt-BR): screen title, tab labels, "From @handle" filter, share, and empty/error copy.
- **No architecture deviations.** It stays on MVI, Hilt and Navigation 3. The screen joins `MainShell`'s list-detail scene like the existing search results.
- **No new dependencies.** `SearchPostsV2Request` already ships in atproto-kotlin `models` 9.13.0.
