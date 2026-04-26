## Why

Nubecita's reason to exist is the Bluesky timeline. The PostCard composable
landed (PR #42), but it has no real data to render — every preview is a
fixture. This change builds the headless foundation that turns
`app.bsky.feed.getTimeline` responses into a stream of `PostUi` instances
and exposes them through an MVI `ViewModel` ready for the screen task
(`nubecita-1d5`) to consume.

It also closes a parallel testing-stack gap: nubecita's tests are on a
mix of JUnit 4 + hand-rolled fakes. This is the first VM with non-trivial
async state (load → refresh → append, three independent error modes), so
shipping it without proper coroutine-flow assertions (Turbine) and a
single test runtime (JUnit 5) would lock in the wrong precedent for
every future feature VM.

## What Changes

- **New `:feature:feed:api` module** exposing one `NavKey` (`Feed`).
- **New `:feature:feed:impl` module** containing:
  - `FeedContract.kt` — `FeedState`, `FeedLoadStatus` (sealed), `FeedEvent`
    (sealed, including locked-in interaction stubs), `FeedEffect`,
    `FeedError`.
  - `FeedViewModel` — extends `MviViewModel<S,E,F>`; orchestrates
    initial-load, refresh, append-on-cursor, retry, and stub interaction
    handling.
  - `FeedRepository` interface + `DefaultFeedRepository` impl wrapping
    `io.github.kikin81.atproto.app.bsky.feed.FeedService.getTimeline`.
  - `FeedViewPostMapper` — pure, total functions converting
    `FeedViewPost` → `PostUi?` (drops malformed posts; never throws).
  - Placeholder `FeedScreen` composable so the Hilt graph compiles
    end-to-end. The real screen ships in `nubecita-1d5` and replaces
    this placeholder; no `@Preview` / screenshot tests on the placeholder.
  - Hilt modules wiring the Nav3 entry and the repository binding.
- **`:core:auth` gains `XrpcClientProvider`** — the canonical injection
  surface for an authenticated `XrpcClient`. Default impl wraps
  `AtOAuth.createClient()` (suspend, DPoP-signed) with caching keyed by
  the active session DID; cache invalidates on session change.
- **MVI convention amended**: flat booleans remain the default for
  *independent* flags, but *mutually-exclusive* view modes (e.g., load
  lifecycles) are explicitly allowed to use a sealed `*Status` sum type.
  Recorded in CLAUDE.md and as a delta on the `mvi-foundation` spec.
- **Test stack overhaul** (subsumes `nubecita-e1a`):
  - Add `junit-jupiter`, `junit-jupiter-engine`, `turbine`, `mockk` to
    `gradle/libs.versions.toml`.
  - Convention plugins (`AndroidLibraryConventionPlugin`,
    `AndroidFeatureConventionPlugin`) wire `useJUnitPlatform()` and add
    JUnit 5 + JUnit-Jupiter API as default `testImplementation` deps.
  - Migrate every existing JUnit 4 unit test in the repo to JUnit 5
    (`LoginViewModelTest`, `MainDispatcherRule` → `MainDispatcherExtension`,
    `PostUiTest`, `RelativeTimeTest`, and any others discovered during
    implementation).
  - Screenshot tests stay on the AGP-managed runner (JUnit 4 internally) —
    the `screenshotTest` source set keeps its existing deps explicitly.

## Capabilities

### New Capabilities

- `feature-feed`: The Following timeline — `FeedViewModel` is the canonical
  entry point for `app.bsky.feed.getTimeline`; `FeedRepository` is the only
  layer that calls `FeedService` directly; `FeedViewPostMapper` is pure +
  total over the response shape; pagination cursor advancement is preserved
  on append failure; embed dispatch mirrors PostCard v1 scope.

### Modified Capabilities

- `core-auth-oauth-bindings`: Add `XrpcClientProvider` as the canonical
  injection surface for authenticated `XrpcClient` instances. Downstream
  feature modules MUST NOT inject `AtOAuth` to call `createClient()`
  themselves.
- `mvi-foundation`: Amend the state-shape rule to allow sealed sum types
  (`sealed interface FooStatus`) for mutually-exclusive view modes, with
  flat booleans remaining the default for independent flags.

## Impact

**Code:**
- New modules: `:feature:feed:api`, `:feature:feed:impl`.
- Modified: `:core:auth` (one new file + one Hilt binding), `:app`
  (`settings.gradle.kts` includes, Nav3 wiring updates if needed).
- `build-logic/`: convention-plugin updates for JUnit 5 wiring.
- Migrated: every existing JUnit 4 unit test file in the repo.

**Dependencies (added to `gradle/libs.versions.toml`):**
- `org.junit.jupiter:junit-jupiter` (api + engine)
- `app.cash.turbine:turbine`
- `io.mockk:mockk`

**Conventions:**
- `CLAUDE.md` MVI section gains a paragraph describing when sealed-status
  sums are appropriate vs. flat booleans.

**Bd ticket coordination:**
- Closes `nubecita-4u5` (FeedContract + FeedViewModel — primary scope).
- Closes `nubecita-e1a` (Turbine + MockK + JUnit 5 — subsumed by the
  test-stack overhaul).

**Non-goals:**
- No real `FeedScreen` composable — placeholder only. The screen +
  `LazyColumn` + `PullToRefreshBox` + scroll-position retention land in
  `nubecita-1d5`, including the full preview / screenshot-test trio.
- No write-path interactions (creating like / repost records). The
  `OnLikeClicked` / `OnRepostClicked` / `OnReplyClicked` / `OnShareClicked`
  events lock in the surface area but their VM handlers are no-ops in this
  change. Real wiring is a follow-on ticket once we add `app.bsky.feed.like`
  / `repost` createRecord paths.
- No promotion of `FeedRepository` or the mapper to a `:core:feed` module —
  colocate inside `:feature:feed:impl` until a second consumer (post
  detail, search) needs the same code.
- No Paging 3 — manual cursor-based pagination per the explicit
  `nubecita-1d5` design ("append-on-scroll pagination triggered when last
  visible item index > size - 5").
