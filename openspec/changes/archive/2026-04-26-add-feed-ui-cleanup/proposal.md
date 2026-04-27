## Why

Side-by-side comparison of the production FeedScreen against the design-system reference surfaces four cleanup-grade visual bugs in shipped code. Edge-to-edge rendering — a deliberate killer-feature decision — is currently *partial*: `enableEdgeToEdge()` runs and the manifest sets `adjustResize`, but `FeedScreen`'s Scaffold padding is consumed by exactly one of four state branches, so feed content draws under the status bar on every cold start. Compounding that, `RobotoFlexFontFamily` is declared as a single `Font(resId = R.font.roboto_flex)` with no per-weight `FontVariation.Settings`, so the variable font's `wght` axis is never honored — every `FontWeight.Normal/SemiBold/Bold` request renders at the same physical weight (≈700), collapsing the entire type scale and making every body line look bold. The `AuthorLine` row in `PostCard` concatenates handle + timestamp into one Text with no truncation, so accounts with long handles (the common case in `*.bsky.social`) wrap the timestamp onto a second visual line. None of these are new features — they're broken contracts in shipped composables, and the visual gap is what users see on first launch.

## What Changes

- **`FeedScreen` propagates `Scaffold` padding to every state variant**: `InitialLoading`, `Empty`, `InitialError`, AND `Loaded` (today only the first does). `LoadedFeedContent`'s `LazyColumn` consumes the padding as `contentPadding` so the first/last items respect insets while the list still scrolls under translucent system bars. `PullToRefreshBox` indicator inset by the status bar.
- **`FeedEmptyState` and `FeedErrorState` accept and apply a `PaddingValues`** parameter so the host's Scaffold inset propagates through.
- **`LoginScreen` wraps its content in a `Scaffold` with `contentWindowInsets = WindowInsets.safeDrawing`** so the `OutlinedTextField` stays above the IME.
- **`RobotoFlexFontFamily` declares one `Font` per `FontWeight`** (Normal, Medium, SemiBold, Bold) using `variationSettings = FontVariation.Settings(FontVariation.weight(N))`. Honors the variable wght axis so `Type.kt`'s scale renders at the intended weights.
- **`PostCard.AuthorLine` splits handle + timestamp into two Texts**, displayName + handle become a `weight(1f, fill = false)` shrinking pair with `maxLines = 1, overflow = Ellipsis` on the handle, and a `Spacer(Modifier.weight(1f))` pushes the timestamp to the right edge of the row. String resource `postcard_handle_and_timestamp` splits into `postcard_handle` (plain `@%s`) and `postcard_relative_time` (kept as-is).
- Screenshot baselines for FeedScreen, PostCard, FeedEmptyState, FeedErrorState, LoginScreen regenerated against the corrected typography + layout.

## Capabilities

### New Capabilities

None. Pure cleanup of shipped behavior; no new capability surface.

### Modified Capabilities

- `feature-feed`: adds requirements covering Scaffold inset propagation across every state variant + the LazyColumn `contentPadding` contract; tightens `FeedEmptyState` / `FeedErrorState` to take a `contentPadding` parameter.
- `feature-login`: adds a requirement that `LoginScreen` wraps content in a `Scaffold` with `WindowInsets.safeDrawing` so the IME does not occlude the handle field. (If `feature-login` doesn't yet exist as a spec, the change creates it; check `openspec/specs/`.)
- `design-system`: adds requirements covering (a) `RobotoFlexFontFamily` per-weight Font declarations honoring the wght variable axis, (b) `PostCard.AuthorLine` row layout — single-row truncation with right-pinned timestamp.

## Impact

- **Code**:
  - `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt` — Scaffold padding propagation across all four state branches; `LoadedFeedContent` accepts `PaddingValues`.
  - `feature/feed/impl/src/main/kotlin/.../ui/FeedEmptyState.kt`, `FeedErrorState.kt` — accept `contentPadding: PaddingValues` parameter.
  - `feature/login/impl/src/main/kotlin/.../LoginScreen.kt` — wrap in `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`.
  - `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Fonts.kt` — replace single `Font(R.font.roboto_flex)` with per-weight Fonts using `FontVariation.weight(N)`.
  - `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/PostCard.kt` — `AuthorLine` restructured to single-row truncated layout.
  - `designsystem/src/main/res/values/strings.xml` — split `postcard_handle_and_timestamp` into `postcard_handle` + `postcard_relative_time`.
- **Screenshot baselines**: every Composable touching the changed typography (most of `:designsystem` + `:feature:feed:impl`) regenerates baselines.
- **APIs**: minor signature changes to `FeedEmptyState` / `FeedErrorState` (new optional parameter) — both `internal`, no consumers outside `:feature:feed:impl`.
- **Dependencies**: none added. `androidx.compose.ui.text.font.FontVariation` is already present via the existing Compose BOM.
- **Tests**: existing screenshot tests rebaseline; new screenshot variants for long-handle truncation + edge-to-edge inset proof.
- **Out of scope** (separate tracking): self-thread connector line (`nubecita-m28.2`); link-card embed (`nubecita-aku`); quoted-post embed (`nubecita-6vq`); TopAppBar / bottom nav and their inset handling (`nubecita-cif`); status-bar translucent scrim (lands with the app shell's TopAppBar).
