## Context

PostCard ships and renders fixture data; nothing in the app actually
fetches a Bluesky timeline yet. The auth stack is complete (`AtOAuth`
issues authenticated `XrpcClient` instances via `createClient()`), and
the design system has every primitive a feed needs. What's missing is
the headless layer between the network and the UI: a feed-data
abstraction, a stable contract for the eventual screen, and the
ViewModel that orchestrates load / refresh / append.

The screen itself (`LazyColumn`, `PullToRefreshBox`, append-on-scroll
trigger, scroll-position retention via `SaveableStateHolder`) is a
separate ticket (`nubecita-1d5`). Keeping the two split lets us land
a small headless PR with comprehensive unit tests, then the screen
PR comes back with the full preview / screenshot-test trio against a
known-good contract — which is the test-coverage convention recorded
in agent memory after PR #42 (PostCard) merged.

This is also the first VM in the repo with non-trivial async state
(initial-load vs refresh vs append, with three independent error
modes). Shipping it on top of the existing JUnit 4 + hand-rolled-fakes
test stack would compound friction — every new feature VM after this
would need the same plumbing. So this change also subsumes
`nubecita-e1a` and migrates the entire repo to JUnit 5 + Turbine +
MockK in one shot.

## Goals / Non-Goals

**Goals:**

- Land a `FeedViewModel` whose unit tests cover initial-load
  success/error, refresh success/error-with-data-preserved, append
  success / failure / end-of-cursor, retry, and the load-idempotency
  invariant.
- Establish `XrpcClientProvider` as the canonical injection surface
  for authenticated `XrpcClient` — every future feature module that
  needs an authed call site goes through it.
- Amend the MVI convention to allow sealed sum types for
  mutually-exclusive view modes; document the rule on the
  `mvi-foundation` spec and in `CLAUDE.md` so the next VM author
  doesn't have to relitigate this.
- Cut the entire repo over to JUnit 5 + Turbine + MockK.
- Set the per-feature module pattern (`:feature:<name>:api` for
  `NavKey`-only, `:feature:<name>:impl` for screens / VMs / Hilt /
  data layer colocated until duplication forces promotion).

**Non-Goals:**

- No real `FeedScreen` composable — placeholder that just shows
  `state.toString()` so the Hilt graph compiles end-to-end. The real
  screen with the preview / screenshot-test trio ships in
  `nubecita-1d5`.
- No write-path interactions. `OnLikeClicked` / `OnRepostClicked` /
  `OnReplyClicked` / `OnShareClicked` events are part of the
  `FeedEvent` sealed interface so the screen task can wire callbacks
  with no contract churn, but their VM handlers are no-ops in this
  change.
- No promotion of `FeedRepository` or `FeedViewPostMapper` to a
  `:core:feed` module. Colocate inside `:feature:feed:impl` until a
  second consumer (post detail, search) needs the same code, per
  `nubecita-4u5`'s bd notes.
- No Paging 3. Manual cursor-based pagination per `nubecita-1d5`'s
  explicit design.
- No MockK adoption beyond what tests in this PR need. Hand-rolled
  fakes remain acceptable; MockK is wired in as an option, not
  mandated.

## Decisions

### Decision 1 — `XrpcClientProvider` lives in `:core:auth`, not a new `:core:network`

`AtOAuth.createClient()` is a `suspend` function that constructs a
fresh DPoP-signed `XrpcClient` from the persisted session, so the
provider must (a) be `suspend` and (b) cache the result keyed by the
active session DID. Three options:

- **A — Add `XrpcClientProvider` to `:core:auth`** (chosen).
- B — Inject `AtOAuth` directly; every authed feature copies the
  caching dance.
- C — Create a new `:core:network` module to own it, with `:core:auth`
  depending on `:core:network`.

Chose A because the provider's only collaborators (`AtOAuth`,
`SessionStateProvider`) already live in `:core:auth`. A new module
for one interface + one impl is premature; if a future
`:core:network` capability emerges (e.g., a shared HTTP retry policy,
non-auth XRPC client variants), the provider can move there without
breaking consumers (the interface is the import surface).

### Decision 2 — `FeedRepository` is internal to `:feature:feed:impl`

The repository interface is package-internal. No other module imports
it. If post-detail or search later needs to fetch posts, the bd
issue's "promote to `:core:feed` when a second consumer appears" rule
fires — at that point, the interface migrates and `:feature:feed:impl`
gains a dependency on the new core module. Avoiding the abstraction
now means we don't pre-design the promoted interface against a single
known consumer.

### Decision 3 — `FeedLoadStatus` is a sealed sum type, not flat booleans

`CLAUDE.md`'s MVI convention says state is "flat ... never a VM-layer
sum type." That convention was written against `LoginState` whose
flags (`isLoading`, `errorMessage`) are independent — both can
coexist. A feed has *mutually-exclusive* load modes: at any instant
the VM is in exactly one of {idle, initial-loading, refreshing,
appending, initial-error}. Three booleans for the three loading
modes would make `isInitialLoading=true && isRefreshing=true`
representable but invalid; the type system would not catch the
mistake.

This change amends the MVI convention to allow sealed sum types
specifically for mutually-exclusive view modes, leaving flat booleans
as the default for independent flags. The amendment is recorded as a
delta on the `mvi-foundation` spec and in `CLAUDE.md`.

### Decision 4 — Mapper is a top-level function file with `toPostUiOrNull` returning null on malformed input

`FeedViewPost` carries `record: JsonObject` (the embedded
`app.bsky.feed.post` record), which the mapper unpacks for `text` and
`facets`. Real-world `record` payloads occasionally violate the
schema (servers in flux, lexicon evolution, etc.). Two options:

- **A — Mapper returns `PostUi?`; the repository drops nulls.** Chosen.
- B — Mapper throws; repository catches per-item.

Chose A because returning `null` is total over the *type* (every
`FeedViewPost` produces a `PostUi?`) while staying total over
**well-formed** shapes (every spec-conforming input produces a
non-null `PostUi`). The repository's `mapNotNull` is then a
single-line filter, and unit tests against malformed fixtures assert
"returns null" rather than "throws SomeSpecificException."

### Decision 5 — Initial-error is sticky in state; refresh / append errors are effects

`FeedState.loadStatus = InitialError(error)` is sticky — the screen
renders a full-screen retry layout while `posts.isEmpty()`. Refresh
and append failures *preserve* posts, do not flip any sticky state,
and emit `FeedEffect.ShowError(error)` for snackbar display. This
matches CLAUDE.md's existing rule that effects are the default for
errors and sticky state is only used when a screen genuinely needs
it (the empty-list case here qualifies; the with-data case does not).

Pagination cursor advancement is gated on append success — a failed
append leaves `nextCursor` unchanged so the user can `LoadMore`
again without losing position.

### Decision 6 — `FeedEvent` includes interaction stubs even though VM handlers are no-ops

`OnPostTapped`, `OnAuthorTapped`, `OnLikeClicked`, `OnRepostClicked`,
`OnReplyClicked`, `OnShareClicked` are part of the `FeedEvent` sealed
interface from day one. The VM handles them as no-ops (or, in the
nav cases, emits `FeedEffect.NavigateToPost` / `NavigateToAuthor`).
The screen ticket (`nubecita-1d5`) can wire `PostCallbacks` to dispatch
these events without forcing a contract change. The shape is
locked-in; the *semantics* may shift in the write-path follow-on
ticket.

This is acknowledged-as-illusory lock-in: if the write-path ticket
discovers we need optimistic UI, undo handling, or an
`OnLikeConfirmed` follow-up event, the contract will change. But
shipping the surface now means downstream code can reference the
event types instead of `TODO` callbacks.

### Decision 7 — Repo-wide JUnit 5 migration in this PR

This change subsumes `nubecita-e1a`. Three options on migration depth:

- **A — Migrate every existing test file in this PR.** Chosen.
- B — Wire JUnit 5 + `junit-vintage-engine`; only new tests use JUnit 5.
- C — Land `nubecita-e1a` first as a separate prereq PR.

Chose A because (a) the migration footprint is bounded — four test
files (`LoginViewModelTest`, `MainDispatcherRule`, `PostUiTest`,
`RelativeTimeTest`); (b) the vintage-engine path leaves the test
stack mixed indefinitely, which is exactly the friction
`nubecita-e1a` exists to remove; (c) doing it in the same PR as the
first non-trivial async VM means the new convention is exercised
immediately on the new test code.

Screenshot tests stay on the AGP-managed runner. The
`screenshotTest` source set keeps its existing JUnit 4 deps
explicitly — the AGP screenshot test runner uses JUnit 4 internally
and is not affected by `useJUnitPlatform()` on the unit-test source
set.

### Decision 8 — `MainDispatcherRule` becomes `MainDispatcherExtension`

JUnit 4's `TestRule` API doesn't exist in JUnit 5. The migration
introduces a `MainDispatcherExtension` implementing
`BeforeEachCallback` + `AfterEachCallback`, applied via
`@ExtendWith`. The extension is added to a `:core:common:test`
fixture source set (or a small published-test-fixture artifact) so
every feature module reuses it instead of redeclaring per-module.

Exact placement defers to implementation — if `:core:common` doesn't
already publish test fixtures, the simplest path is a tiny new
`:core:testing` module that depends only on `kotlinx-coroutines-test`
+ `junit-jupiter-api`.

## Risks / Trade-offs

- **`record: JsonObject` parsing surprises** → Mitigated by checking
  fixtures into `:feature:feed:impl/src/test/resources/` (recorded
  from the live `public.api.bsky.app` AppView) and unit-testing
  `extractText` / `extractFacets` against them. Real-world
  malformed-record cases get added to the fixture set as they appear.
- **JUnit 5 vs AGP screenshot test runner conflict** → Mitigated by
  keeping the `screenshotTest` source set's deps explicit (JUnit 4)
  while only the unit-test source set switches to
  `useJUnitPlatform()`. Verified by running
  `:designsystem:compileDebugScreenshotTestKotlin` after the test-stack
  commit lands.
- **Cache invalidation in `XrpcClientProvider` is racy** → Mitigated
  by guarding `cached` + `cachedForDid` reads/writes with a `Mutex`
  and unit-testing concurrent `authenticated()` calls under
  `runTest`. Cache invalidation hooks into `SessionStateProvider`'s
  state flow collected on a Singleton-scoped `CoroutineScope`
  (existing in `:core:auth`'s graph; verify before reusing).
- **MVI convention change cascades to existing VMs** → No-op.
  `LoginState`'s flat-boolean shape stays correct under the amended
  convention because its flags are independent (a login can be
  `isLoading=true && errorMessage != null` mid-error-clear). Only
  *future* VMs with mutually-exclusive modes follow the new pattern.
- **Repo-wide test migration breaks CI on a hidden test file** →
  Mitigated by running `./gradlew testDebugUnitTest` repo-wide as the
  last step of the test-stack commit. The migration commit must be
  independently green before any feature code lands on top.
- **Interaction-stub events lock in the wrong contract** →
  Acknowledged. The write-path follow-on ticket is allowed to break
  the stub contract if it discovers the shape is wrong. The benefit
  (screen wiring at known event names) outweighs the lock-in cost
  for `nubecita-1d5`.

## Migration Plan

This is greenfield foundation work, no production-runtime migration.
Local migration order on the branch (each commit independently
buildable + green):

1. `chore(testing)`: add Turbine + MockK + JUnit 5 to
   `gradle/libs.versions.toml` + convention plugin wiring. Add
   `:core:testing` (or equivalent fixture surface) with
   `MainDispatcherExtension`.
2. `chore(testing)`: migrate every existing JUnit 4 test in the repo
   to JUnit 5. Repo green on JUnit 5.
3. `feat(core-auth)`: add `XrpcClientProvider` interface, default
   impl, Hilt binding, cache-invalidation wiring + unit tests.
4. `docs(mvi)`: amend MVI convention in `CLAUDE.md` + record on the
   `mvi-foundation` spec delta. Pure text change; no code.
5. `feat(feature-feed)`: scaffold `:feature:feed:api` +
   `:feature:feed:impl` modules; build files + Hilt skeleton.
6. `feat(feature-feed)`: `FeedViewPostMapper` + tests (pure functions
   only, no VM yet).
7. `feat(feature-feed)`: `FeedRepository` interface +
   `DefaultFeedRepository` + tests.
8. `feat(feature-feed)`: `FeedContract` + `FeedViewModel` + the full
   VM unit-test suite from the test plan.
9. `feat(feature-feed)`: placeholder `FeedScreen` + Nav3 entry +
   `:app` integration smoke (the Hilt graph resolves end-to-end).

Rollback strategy: revert the merge commit. The change is purely
additive at the runtime level — the new module isn't reachable from
the existing nav graph until `nubecita-1d5` wires it in.

## Open Questions

- `:core:testing` vs published test fixtures from `:core:common` for
  the shared `MainDispatcherExtension`. Resolve in implementation
  step 1 — whichever path Hilt + the convention plugin support more
  ergonomically wins.
- Whether `SessionStateProvider` already exposes a `currentDid()`
  helper or its equivalent. If not, the implementation step adds the
  smallest possible read-only accessor (returns
  `OAuthSession.did?.raw` or null). Decision deferred to step 3.
- Whether the existing `:core:auth` Hilt graph already exposes a
  Singleton-scoped `CoroutineScope` for collecting the session flow
  inside `DefaultXrpcClientProvider`. If not, add the binding in
  step 3; this is internal plumbing and doesn't change the public
  interface.
