## Why

The feed, search, and profile surfaces render every post's media unconditionally — they ignore the AT Protocol moderation **labels** carried on `PostView.labels` and the user's content-filter **preferences**. An app that surfaces sexual / graphic content to everyone, unfiltered and uncontrollable, is a Google Play **UGC + mature-content policy** violation and a launch blocker. We need to honor the user's content-label preferences and give them a screen to configure them, mirroring the official Bluesky app.

Tracked by epic `nubecita-twmt` (this change covers child `nubecita-twmt.1`: the `:core:moderation` foundation; the settings screen, mapping, and render surfaces follow in `twmt.2`–`twmt.4`).

## What Changes

- New **`:core:moderation`** capability: a pure `ContentModerator` that turns (a post's labels × the user's prefs) into a per-media decision (**Show / Warn / Filter-from-feed**), plus a `ModerationPreferencesRepository` that fetches, caches, and write-throughs the user's `app.bsky.actor` preferences.
- Models the 4 global content-label categories — `porn`, `sexual`, `graphic-media`, `nudity` — and the **"Enable adult content" master gate** (`adultContentPref.enabled`, default **off**), including the rule that an adult label with the master off is force-hidden with **no override**.
- Reads/writes preferences via `app.bsky.actor.getPreferences` / `putPreferences`, decoding the `preferences` **array** as raw JSON (the SDK mis-models it as an object) — reusing the established `:core:feeds` pattern — and doing whole-array **read-modify-write** on save.

No user-visible change in this child on its own (no UI wired yet); it is the foundation the later children consume. No breaking changes.

## Capabilities

### New Capabilities
- `content-moderation`: the domain model (content labels, visibilities, the adult gate), the pure moderation decision, and the preferences fetch/cache/sync repository that the settings screen and feed/search/profile surfaces build on.

### Modified Capabilities
<!-- None in this child. Later children (twmt.2–.4) will touch settings, feed-mapping/data-models, and the feed/search/profile/post-detail render surfaces. -->

## Impact

- New module `:core:moderation` (added to `settings.gradle.kts`; `nubecita.android.library` + `nubecita.android.hilt`, `environment` flavor split like `:core:feeds`).
- Depends on `:core:auth` (`XrpcClientProvider`), `:core:common`, atproto models/runtime, kotlinx-serialization-json.
- No change to `:data:models` / `:core:feed-mapping` / feature modules in this child.
