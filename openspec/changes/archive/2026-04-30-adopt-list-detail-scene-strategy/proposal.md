## Why

`MainShell` already adapts its **chrome** — `NavigationSuiteScaffold` swaps `NavigationBar` ↔ `NavigationRail` from compact to medium/expanded widths (per `add-adaptive-navigation-shell`). The **content** area, however, is single-pane on every width: a tablet or expanded foldable user browsing the Feed and tapping a post will see the post detail push full-screen, leaving the entire other half of the window empty during the transition. This is wasted real-estate on every form factor wider than a phone, and it's the core list-detail UX users expect from a native Bluesky/Threads/X-class client.

Navigation 3 ships a `ListDetailSceneStrategy` (`androidx.compose.material3.adaptive:adaptive-navigation3`) that solves this at the navigation layer rather than the screen layer: the same back stack `[Feed, PostDetail(...)]` renders full-screen on compact and as a two-pane scene on medium/expanded — automatically, with no per-host scaffold duplication. Adopting the strategy in `MainShell`'s inner `NavDisplay` puts the infrastructure in place once; subsequent feature work (post detail, profile-feed-with-detail, etc.) just declares pane metadata on its existing entries.

This change is intentionally scoped to **shell-level adaptive scaffolding**, matching the bd issue (`nubecita-5l3`) description. PostDetail's `:api` stub and `:impl` module are deferred to follow-on changes (Phase 2 and Phase 3 of the post-detail epic). After this change merges, on medium/expanded widths the user sees the existing Feed in the left pane and a "Select a post to read" placeholder in the right; on compact, behavior is unchanged.

## What Changes

- **Add a new dependency** to `gradle/libs.versions.toml`: `androidx.compose.material3.adaptive:adaptive-navigation3` (alpha; see design.md "Risks" for the supply-chain audit). Pinned, not floating.
- **Add the dep to `:app`** (and only `:app` — the inner `NavDisplay` lives there). Convention plugins are not modified; this is a single-consumer dependency until follow-on changes pull it into feature modules that mark their own entries.
- **Augment `MainShell.kt`'s inner `NavDisplay`** to pass `sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>())`. The remaining parameters (`backStack`, `onBack`, `entryDecorators`, `entryProvider`) stay as-is.
- **Mark the Feed entry as a list-pane host** in `:feature:feed:impl`'s `EntryProviderInstaller`: pass `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { FeedDetailPlaceholder() })` to the `entry<Feed>(…)` builder. The placeholder is a simple centered "Select a post to read" composable defined in `:feature:feed:impl`.
- **Add screenshot tests** for the inner pane behavior at compact / medium / expanded window-size classes — verifying the placeholder is hidden on compact and visible on medium/expanded.
- **No PostDetail entry**, no `:feature:postdetail` module, no Profile metadata (Profile is `:api`-only stub). Those are explicit Phase 2/3 work tracked under separate bd issues.

## Capabilities

### Modified Capabilities

- `app-navigation-shell`: inner `NavDisplay` gains a `ListDetailSceneStrategy`, and list-pane entries supply a detail placeholder.
- `feature-feed`: the Feed entry registered into the `@MainShell` `EntryProviderInstaller` set declares `listPane{}` metadata; a `FeedDetailPlaceholder` composable is added.

### New Capabilities

<!-- None. -->

## Impact

**Code:**

- `gradle/libs.versions.toml` — one new library alias under `androidx-compose-material3-adaptive-*`. Version pinned (see design.md for selection).
- `app/build.gradle.kts` — `implementation(libs.androidx.compose.material3.adaptive.navigation3)`.
- `app/src/main/java/net/kikin/nubecita/shell/MainShell.kt` — pass `sceneStrategies` to the inner `NavDisplay`. Update KDoc.
- `feature/feed/impl/src/main/.../FeedEntryModule.kt` (or wherever the `EntryProviderInstaller` for `Feed` is defined) — wrap the `entry<Feed>` call with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { FeedDetailPlaceholder() })`.
- `feature/feed/impl/src/main/.../ui/FeedDetailPlaceholder.kt` (new) — centered "Select a post to read" Composable + Compose preview.
- `feature/feed/impl/src/main/res/values/strings.xml` — one new string resource for the placeholder text.
- `app/src/screenshotTest/java/net/kikin/nubecita/shell/MainShellListDetailScreenshotTest.kt` (new) — three previews × three window-size classes asserting compact-no-placeholder vs. medium/expanded-with-placeholder.
- `feature/feed/impl/src/test/.../ui/FeedDetailPlaceholderTest.kt` (new) — minimal Compose-rule unit test asserting the string resource renders.

**External (manual):**

- None. No CI changes, no Firebase console changes, no signing changes.

**Dependencies / system:**

- Adds one alpha-stage dependency (`adaptive-navigation3`). The base nav3 stack (`navigation3-runtime`, `navigation3-ui`) is already alpha at `1.1.1`, so the marginal supply-chain risk is small. Sonatype reports the artifact in the AndroidX namespace as not malicious and not end-of-life; specific-version trust-score data is not yet populated by Sonatype (typical for new alpha lines). See design.md "Risks" for the upgrade-watch plan.
- Build-time impact: one extra alpha library added to `:app`'s classpath; APK delta is negligible (the strategy and metadata helpers are small).
- Runtime impact: on compact, behavior is unchanged (the strategy passes through to the existing single-pane scene). On medium/expanded with only Feed on the stack, an additional pane is composed for the placeholder — measured impact is one extra LazyLayout slot per recomposition.

## Non-goals

- **PostDetail feature module** — `:feature:postdetail:api` (Phase 2) and `:feature:postdetail:impl` (Phase 3) are separate bd issues with their own OpenSpec changes.
- **Profile-as-list-pane** — adding `listPane{}` metadata to the Profile entry requires `:feature:profile:impl` (still `:api`-only stub). That metadata lands in the eventual profile-impl epic, not here.
- **Custom pane layouts** — using `extraPane()` for three-pane experiences (e.g. inbox-style mail), customizing `PaneScaffoldDirective`, or animating pane transitions beyond `ListDetailSceneStrategy`'s defaults. The strategy's default behavior is sufficient and matches Bluesky/X conventions.
- **Search and Chats list-detail** — adding `listPane{}` to those entries is out of scope. They have no detail-class targets today; engaging the strategy would just give them a permanent empty placeholder. Revisit per-feature when a relevant detail entry is designed.
- **Deep linking into `PostDetail` from outside `MainShell`** — the outer `NavDisplay` (Splash/Login/Main) does not get a scene strategy. Detail is `@MainShell`-scoped only.
- **Promoting `FeedDetailPlaceholder` to `:designsystem`** — kept feature-local. Promotion is a YAGNI follow-up when a second list-pane host (e.g. Profile feed) needs the same shape.
- **Migrating off alpha** — pinning the alpha is acceptable; chasing each weekly bump is not. The "watch & upgrade" cadence is documented in design.md "Risks", not enforced as a task here.
