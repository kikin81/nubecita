## ADDED Requirements

### Requirement: `PinnedFeedsRepository` reads the user's pinned feeds from `SavedFeedsPrefV2`

The system SHALL expose a `PinnedFeedsRepository` in a new `:core:feeds` module
(`nubecita.android.library` + `nubecita.android.hilt`) that returns the user's pinned
feeds as an ordered `ImmutableList<PinnedFeedUi>`. The repository MUST call
`ActorService.getPreferences` (`app.bsky.actor.getPreferences`), locate the
`SavedFeedsPrefV2` entry, filter its `items` to those with `pinned == true`, and preserve
the stored order. It MUST split items by `SavedFeed.type`: `"timeline"` → a Following
entry, `"feed"` → a generator entry, `"list"` → a list entry. For `"feed"` entries it
MUST hydrate display name and avatar by batch-calling `FeedService.getFeedGenerators`
(`app.bsky.feed.getFeedGenerators`). The repository MUST be the only layer that reads
saved-feeds preferences; feature modules depend on `:core:feeds`, never on
`getPreferences` directly.

#### Scenario: Pinned feeds are returned in stored order

- **WHEN** `getPreferences` returns a `SavedFeedsPrefV2` whose `items` are
  `[timeline(following, pinned), feed(A, pinned), feed(B, pinned=false), list(L, pinned)]`
- **THEN** `PinnedFeedsRepository` returns exactly `[Following, A, L]` in that order, and
  `B` (not pinned) is omitted

#### Scenario: Generator metadata is hydrated

- **WHEN** the pinned set contains generator feed URIs `[A, B]`
- **THEN** the repository batch-calls `getFeedGenerators(feeds = [A, B])` and each returned
  `PinnedFeedUi` of kind `Generator` carries the `GeneratorView.displayName` and
  `GeneratorView.avatar` for its URI

#### Scenario: Items split by type

- **WHEN** a `SavedFeed` has `type = "list"`
- **THEN** its `PinnedFeedUi` has `kind == FeedKind.List`, and a `type = "timeline"` item
  with `value = "following"` yields `kind == FeedKind.Following`

### Requirement: Default fallback is Following plus Discover

The system SHALL fall back to a default chip set of exactly Following and Discover when the
account has no `SavedFeedsPrefV2`, an empty pinned set, or the `getPreferences` call fails.
Discover MUST resolve to the well-known `whats-hot` feed generator URI exposed as a single
named constant in `:core:feeds`. The Following and Discover defaults MUST render from local
glyphs (`Home`, `LocalFireDepartment`) without requiring a network hydration call, so they
are usable offline and on first launch.

#### Scenario: New account with no saved-feeds preference

- **WHEN** `getPreferences` returns no `SavedFeedsPrefV2` entry
- **THEN** the repository result is `[Following, Discover]`

#### Scenario: Preferences fetch fails

- **WHEN** the `getPreferences` call throws or returns a failure
- **THEN** the repository yields `[Following, Discover]` and signals a non-fatal error to
  the caller (so the Feed remains usable)

### Requirement: `PinnedFeedUi` is the UI-ready feed-chip model

The system SHALL define a `@Stable` `PinnedFeedUi` data class in `:data:models`
(`net.kikin.nubecita.data.models`) carrying at least `id: String`, `uri: String`,
`kind: FeedKind`, `displayName: String`, and `avatarUrl: String?`, plus a `FeedKind` enum
with cases `Following`, `Generator`, and `List`. The model MUST obey the existing
`:data:models` constraints (no service abstractions; Compose only via stability
annotations) and MUST ship fixture factories for preview/test use.

#### Scenario: Following entry carries no remote avatar

- **WHEN** a `PinnedFeedUi` of `kind == FeedKind.Following` is constructed
- **THEN** its `avatarUrl` is `null` (the chip renders the local `Home` glyph)

#### Scenario: Fixtures exist

- **WHEN** preview or test code references `PinnedFeedUi` fixtures
- **THEN** fixture factories for each `FeedKind` are available alongside the model
  definition

### Requirement: The last-selected feed is remembered across launches

The system SHALL persist the last-selected feed URI and expose it for restoration on cold
start. Persistence MUST use the non-encrypted DataStore in `:core:preferences` via a
`lastSelectedFeedUri: Flow<String?>` read and a `suspend fun setLastSelectedFeedUri(uri:
String)` write. On startup the selection MUST be validated against the current pinned set;
if the persisted URI is no longer pinned, the system MUST fall back to the Following feed.

#### Scenario: Selection is restored

- **WHEN** the user last viewed feed URI `art` and relaunches the app while `art` is still
  pinned
- **THEN** the main Feed opens with `art` selected

#### Scenario: Stale selection falls back to Following

- **WHEN** the persisted `lastSelectedFeedUri` is no longer present in the pinned set
- **THEN** the main Feed opens with Following selected
