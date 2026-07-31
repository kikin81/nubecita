## Why

Nubecita's bottom-nav surface has no way to see who liked, replied to, mentioned, quoted, reposted, or followed the user — they have to leave the app or guess. Without a notifications surface the app is hostile to retention: every interaction someone has with the user is invisible until they manually check their own profile or PostDetail. This change ships the polling-based in-app notifications tab that the meta-epic [[nubecita-1fy]] explicitly carved out as the Play Store launch slice. It also positions the surface as the foreground-fallback target for the FCM pipeline that lands in the sibling slice 2 epic.

## What Changes

- New top-level destination **Notifications** added between Search and Chats in the bottom-nav suite. `MainShell.TopLevelDestinations` grows from 4 entries to 5: Feed | Search | Notifications | Chats | Profile.
- New `:feature:notifications:api` module exposing a `NotificationsTab : NavKey` and a qualifier-tagged `@MainShell EntryProviderInstaller` (api/impl split per repo convention).
- New `:feature:notifications:impl` module — `NotificationsScreen`, `NotificationsViewModel` (MVI base per [[nubecita-aew]]), repository wrapping atproto-kotlin's `NotificationService`, mapper that aggregates same-reason notifications into multi-actor rows.
- App-foregrounded **60-second polling** of `app.bsky.notification.getUnreadCount` via a Hilt-singleton `NotificationsUnreadCountStore`, started/stopped by a `ProcessLifecycleOwner`-scoped observer. The bottom-nav Notifications icon is wrapped in a `BadgedBox` reading from this store.
- **5 filter chips** (M3 `FilterChip`s) above the LazyColumn — All | Mentions | Reposts | Follows | Likes — single-select, mapping to the lexicon's `reasons[]` param. Mentions = `mention + reply + quote`; Reposts = `repost + repost-via-repost`; Likes = `like + like-via-repost`.
- **Bluesky-style row rendering**: stacked avatars + chevron disclosure for aggregated rows, reason-colored icon at the left edge, mini preview of the subject post (text + thumbnail grid) hydrated via a batched `getPosts` call per page.
- **Mark-read on tab exit**: calls `app.bsky.notification.updateSeen(seenAt = now)` when the user navigates away from the tab, preserving the unread visual distinction during the visit.
- **Deep-link routing** to PostDetail (post-shaped reasons), Profile (follow / verified / starterpack-joined), or no-op (unknown). Uses the existing `LocalMainShellNavState` pattern — VMs never touch the navigator.
- Design system additions: 4 new `NubecitaIconName` entries (`AlternateEmail`, `FormatQuote`, `ExpandMore`, `Verified`); optional 5th (`Group`); one-codepoint correction for `Notifications` (`` → ``).
- `:data:models` additions: `NotificationItemUi` sealed type (Single | Aggregated), `NotificationReason` enum, `NotificationFilter` enum.
- Follow-up wire-up of [[nubecita-8487]]: `AtUriToDeepLink`'s unknown-collection `else -> null` branch flips to `nubecita://notifications` once this tab exists. Landed in the same PR or sibling.

## Capabilities

### New Capabilities

- `feature-notifications`: In-app notifications surface — bottom-nav tab, polling unread-count badge, paginated listNotifications, reason-grouped aggregation, filter chips, deep-link routing to source posts/profiles, mark-read on tab exit.

### Modified Capabilities

- `app-navigation-shell`: `TopLevelDestinations` grows from 4 to 5 entries; the Notifications icon wraps in a `BadgedBox` reading from a Hilt-injected unread-count `StateFlow`.
- `data-models`: Adds `NotificationItemUi` (sealed Single/Aggregated), `NotificationReason` enum, `NotificationFilter` enum, and accompanying fixture factories.
- `design-system`: Adds 4 required `NubecitaIconName` entries plus a `NotificationReasonIcon` composable that maps `NotificationReason` to the correct glyph + tint. Corrects the `Notifications` codepoint to the canonical `` so the FILL axis renders the activity dot.

## Impact

- **New Gradle modules**: `:feature:notifications:api`, `:feature:notifications:impl`. Both apply the existing `nubecita.android.feature` (impl) and `nubecita.android.library` (api) convention plugins. Settings registration in `settings.gradle.kts`.
- **DI wiring**: New Hilt module(s) in `:feature:notifications:impl` providing the repository and the `@MainShell EntryProviderInstaller`. App-level binding of `NotificationsUnreadCountStore`; `ProcessLifecycleOwner` observer registration in `NubecitaApplication`.
- **Networking**: Uses `NotificationService.listNotifications` / `getUnreadCount` / `updateSeen` and a batched `FeedService.getPosts(uris[])` from atproto-kotlin 9.0.1 — all three notification ops are already generated.
- **Material Symbols font**: Re-run `./scripts/update_material_symbols.sh` after adding new enum entries so the subset font includes the new glyphs.
- **Resources**: New string resources (`main_shell_tab_notifications`, filter-chip labels, empty / error copy, reason-row format strings with quantity variants for "and N others"). Existing strings file location.
- **Tests**: Unit tests for VM, mapper, repository; previews + screenshot tests for each new composable (`@PreviewNubecitaScreenPreviews` for the screen, plain `@Preview` for rows / chips / sheet). The PR will need the `update-baselines` label.
- **Follow-ups**: [[nubecita-8487]] (AtUriToDeepLink fallback) ships in the same PR; [[nubecita-1fy.2]] (per-reason settings), [[nubecita-1fy.3]] (multi-account), priority-toggle, push-pipeline integration (slice 2), and channel set (slice 3) are explicitly **out of scope** for this change.
- **bd reference**: This change implements [[nubecita-1fy.1]] under the meta-epic [[nubecita-1fy]].
