# Color token cascade & screenshot canvas fix

**Date**: 2026-05-23
**Status**: Approved (brainstorm); plan to be written next.
**Trigger**: Dark-mode screenshot baselines for `SettingsStubContent` fracture — root canvas paints white while individual `SegmentedListItem` rows paint dark `surfaceContainer`. Symptom is a screenshot fixture issue; the underlying causes are (a) the preview/screenshot harness doesn't paint any canvas surface and (b) the Material 3 surface-token cascade has never been documented as a contract, so call sites have drifted.

## Goals

1. Fix the screenshot canvas: dark-mode baselines for stateless `*Content` slices show the expected dark canvas without per-fixture workarounds.
2. Define a canonical token-cascade contract that assigns one *role* to each M3 surface token, so future code can pick a token by reasoning about depth rather than guessing.
3. Migrate every existing call site to conform to the contract; regenerate the affected screenshot baselines per surface.
4. Enforce the most mechanizable parts of the contract with a custom detekt rule, so future drift fails CI rather than slipping in.

## Non-goals

- Not redesigning Material 3 color theory or the brand palette (`NubecitaPalette` stays as-is).
- Not introducing a `LocalAppTheme` light/dark switcher — that lives in the Settings Display section change (`nubecita-37to.3`).
- Not building a semantic `nubecitaSurface(role)` alias layer (rejected during brainstorm in favor of doc + lint).
- Not branding "me" vs "them" chat bubbles with different containers — that's a separate visual decision worth its own brainstorm.
- Not assigning roles to `surfaceDim` / `surfaceBright` / `surfaceContainerLowest`; they're reserved and lint forbids usage outside the design system internals.

## The token-cascade contract

Each role names one M3 token. Reading order top→bottom is back→front in the visual stack. Anything that maps multiple tokens to a single role is a bug.

| Depth role | M3 token | What lives there |
|---|---|---|
| Screen canvas | `surface` | Scaffold root, modal root, full-screen routes. Every screen paints exactly one of these. |
| Item card | `surfaceContainer` | Discrete content objects sitting on the canvas: post cards (new), settings section cards, chat convo rows, the various `BlockedPostCard` / `NotFoundPostCard` post-position placeholders. |
| Recessed inset | `surfaceContainerLow` | Anything nested *inside* an item card: quoted posts, external link embeds, "record unavailable" / "unsupported embed" placeholders inside a post body. *In dark mode this token is darker than `surfaceContainer`, producing the carved-in feel.* |
| Raised affordance | `surfaceContainerHigh` | Things sitting on top of an item card: chat message bubbles, day-separator chips, video-poster top gradient stop. |
| Strong fill | `surfaceContainerHighest` | Thumbnail placeholders, shimmer base, image-load placeholders, character-counter track, disabled fills. Independent of nesting depth — a thumbnail placeholder inside a recessed inset still maps here. |
| Reserved | `surfaceDim`, `surfaceBright`, `surfaceContainerLowest` | Documented unused in v1; lint rejects external usage. |

The simple structural rule: **the recessed-inset role applies uniformly to anything nested directly inside an item card** (excluding strong-fill placeholders, which sit at the strong-fill role regardless of nesting).

`background` is treated as a synonym for `surface` — values are equal in both light and dark schemes today, and the contract picks `surface`. Lint forbids `colorScheme.background` outside design-system internals.

## Architecture

Three pieces ship across four sequenced workstreams.

### 1. The preview/screenshot theme wrapper

New helper in `:designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/`:

```kotlin
@Composable
fun NubecitaCanvasPreviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    NubecitaTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    }
}
```

`Modifier.fillMaxSize()` guarantees the canvas covers the full test-render bounds, so a component using custom `LayoutModifier` or edge-to-edge drawing can't accidentally leave a transparent gutter the IDE paints white.

Usage rules:

- **Screen-level** `*ScreenshotTest.kt` fixtures (screens, full-screen states, dialogs, panes — anything whose intent is to render against a full-bleed canvas) use `NubecitaCanvasPreviewTheme`. The wrapper paints the screen-canvas role, fixing the dark-mode fracture without per-fixture juggling. **Component-level** fixtures (atoms like avatars, buttons, single rows, post cards, message bubbles) stay on `NubecitaTheme(dynamicColor = false)` — the wrapper's `Modifier.fillMaxSize()` would cause atoms with intrinsic-fill behavior to balloon to the preview viewport instead of rendering at natural bounds.
- `dynamicColor = false` keeps screenshot baselines deterministic — Layoutlib's dynamic-color fallback varies across emulator configurations.
- Production composables stay untouched. `NubecitaTheme` remains a colorScheme provider only; canvas paint is the preview/test concern, paid for in production by `Scaffold` or the modal `Surface`. A production screen that "forgets" its Scaffold shows up as broken in its own screenshot, not silently fixed.
- `*Content` slices do NOT paint their own `Surface(color = surface)`. The wrapper (Scaffold in production, modal Surface in production tablet mode, `NubecitaCanvasPreviewTheme` in tests) is the canonical paint owner. Painting `surface` from a content slice would clobber a tablet-modal Surface that wants to be `surface` (or an explicitly-typed wrapper).

### 2. The call-site migration

Concrete delta:

- **Every `Scaffold(`** sets `containerColor = MaterialTheme.colorScheme.surface` explicitly. Codifies that Scaffolds own the canvas paint and stops the silent `background` → `surface` slippage. Affects: `FeedScreen.kt:454`, `LoginScreen.kt:91`, `SearchScreen.kt:260`, `OnboardingScreen.kt`, `ComposerScreen.kt:363`, `PostDetailScreen.kt:310`, `SettingsStubScreen.kt` phone wrapper.
- **Screens with no Scaffold**: `ProfileScreen`, `ChatsScreen`, `ChatScreen`, Search sub-screens — verified per file. Either rely on `MainShell`'s outer Scaffold paint (document it inline) or add a root `Surface(color = surface)`. Production behavior stays identical; previews start rendering correctly.
- **`PostCard` (designsystem)**: open `Column` → `Surface(color = surfaceContainer, shape = nubecitaShapes.medium)`. Drop the inter-post `HorizontalDivider` at line 177. `FeedScreen`'s `LazyColumn` switches to `verticalArrangement = Arrangement.spacedBy(8.dp)`.
- **`PostCardExternalEmbed`**: `surfaceContainer` → `surfaceContainerLow` (recessed inset inside a post body).
- **`PostCardRecordUnavailable`**: `surfaceContainerHighest` → `surfaceContainerLow` (recessed inset, not a strong fill).
- **`PostCardUnsupportedEmbed`**: `surfaceContainerHighest` → `surfaceContainerLow` (same rationale).
- **`PostCardQuotedPost`** outer Surface + inner thumbnail: no change. Outer already at `surfaceContainerLow`; inner thumbnail at `surfaceContainerHighest` (strong-fill role for the placeholder rectangle).
- **`PostDetailPaneEmptyState`**: `surfaceContainerLow` → `surface` (it's a pane canvas, not a recessed inset).
- **`ComposerScreen.kt:412` inner `Surface(color = surface)`**: investigate intent — likely a redundant paint that drops. Worst case it stays as documented "explicit canvas for the IME-pinned bottom bar."
- **`SettingsStubContent`**: no change to the body. The previous fracture is fixed by routing screenshot fixtures through `NubecitaCanvasPreviewTheme`.

Items that already conform and stay put: `SettingsSection` (×2 sites), `SwitchAccountRow`, `ConvoListItem`, `BlockedPostCard`, `NotFoundPostCard`, `MessageBubble`, `DaySeparatorChip`, `NubecitaAsyncImage`, `Shimmer`, `VideoPosterEmbed`, `PostCardVideoEmbed`, `ComposerCharacterCounter`, `ComposerAttachmentChip`, `ComposerReplyParentSection`, `FeedsLoadingBody`, `PeopleLoadingBody`, `ProfileHero` ring.

### 3. The detekt rule

Custom rule lives under `build-logic/detekt-rules/`:

- Allows: `MaterialTheme.colorScheme.{surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest}` anywhere.
- Forbids: `MaterialTheme.colorScheme.{surfaceDim, surfaceBright, surfaceContainerLowest, background}` outside an allowlist (`:designsystem` internals, `MainShell`).
- Forbids: `Scaffold(` without a `containerColor =` argument — every Scaffold must declare its canvas paint role.

Intentionally narrow. It doesn't try to enforce the *nesting* rule (would need data-flow analysis); that stays a doc convention enforced by review.

## Sequencing

Each workstream is one bd-issue-plus-PR. The ordering is load-bearing because each step depends on the previous one being green.

1. **Workstream 1 — Docs.** KDoc on `Color.kt` documenting the role contract + a `docs/design-system/surface-roles.md` reference page. Pure docs PR; no code; no baseline churn.
2. **Workstream 2 — Preview wrapper.** Land `NubecitaCanvasPreviewTheme` in `:designsystem/preview/`. Migrate every `*ScreenshotTest.kt` fixture to use it. Regenerates every previously-broken dark-mode baseline (they finally show the dark canvas). PR carries the `update-baselines` label.
3. **Workstream 3 — Call-site migration.** Refactor per surface, one PR each, in this order:
   - 3a. PostCard + feed (`PostCard.kt`, `PostCardExternalEmbed`, `PostCardRecordUnavailable`, `PostCardUnsupportedEmbed`, `FeedScreen` LazyColumn arrangement, Scaffold `containerColor`)
   - 3b. PostDetail (`PostDetailScreen` Scaffold `containerColor`, `PostDetailPaneEmptyState`)
   - 3c. Composer (Scaffold `containerColor`, investigate redundant inner Surface)
   - 3d. Chats (`ChatsScreen`, `ChatScreen` root surfaces if any)
   - 3e. Search (`SearchScreen` Scaffold `containerColor`, sub-screen roots)
   - 3f. Profile / Settings stub (Scaffold `containerColor`, verify ProfileScreen)
   - 3g. Login / Onboarding (Scaffold `containerColor`)
   Each PR is small enough to review the screenshot diffs sanely.
4. **Workstream 4 — Detekt rule.** Land once all call sites conform. Rule passes immediately; future drift fails CI.

Lint-rule-first would explode CI on every WIP branch. Migration-first means each refactor PR has a focused screenshot review and the rule lands as a one-line clean addition.

## Testing & rollback

- **Screenshot baselines** regenerate per workstream-3 PR under the `update-baselines` label. The diff is one feature surface per PR (feed, post-detail, composer, chats, search, profile, login/onboarding).
- **`NubecitaThemeTest`** extends with role-mapping assertions: each role resolves to the expected M3 token in both light and dark schemes.
- **`ColorSchemeTest`** extends with a depth-ordering invariant: in dark mode, `surfaceContainerLow.luminance() < surfaceContainer.luminance() < surfaceContainerHigh.luminance() < surfaceContainerHighest.luminance()` (using Compose's built-in `Color.luminance()` extension, see "Implementation notes" below). In light mode, the inverse. Catches palette drift.
- **`NubecitaCanvasPreviewTheme`** ships with its own screenshot test demonstrating canvas paint in both modes.
- **Rollback unit**: each workstream PR is independently revertible. Workstream 2 (preview wrapper) can't be partially reverted without breaking tests but has no production-render impact.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| `PostCard`-as-card visual change is too aggressive and the team wants to revert. | Workstream 3a is a single PR — revertible in isolation without touching the contract or the lint rule. |
| The `Composer` inner Surface (line 412) has a non-obvious reason for being there. | Workstream 3c investigates before deleting; if the paint serves the IME-pinned bottom bar specifically, document and keep. |
| Detekt rule false positives in `:designsystem` internals. | Rule's allowlist is path-based on `:designsystem/src/main/` and `MainShell.kt`; can extend if needed. |
| Tablet-modal Settings (in `SettingsModalWrapper`) currently uses a `Surface` with no explicit color → defaults to `surface` + `tonalElevation = 6.dp`. The contract says modals are item cards (`surfaceContainer`). | Treat modal surfaces as "screen canvas in a window" — keep `surface` with tonal elevation. **M3 tonal elevation is allowed only on `surface` tokens representing windowed bounds: Dialog, BottomSheet, modal surfaces.** For in-layout content hierarchy (cards, embeds, rows) tonal elevation is deprecated in favor of explicit `surfaceContainer*` token steps from the role table. Avoids needing a sixth role and removes the ambiguity of "use a container token or use tonal elevation?" |
| `:designsystem` is consumed by every feature module — a Surface-wrap of `PostCard` could affect external-test callers we haven't enumerated. | Workstream 2 lands the wrapper first; workstream 3a tests against the existing screenshot suite before regenerating baselines. |

## Implementation notes resolved during spec review

The three deferred questions resolved as follows; the plan-writing step assumes these answers.

### Detekt custom-rule location

New dedicated Kotlin library module: `build-logic/detekt-rules/`. The rule class extends Detekt's `Rule` and registers via a custom `RuleSetProvider` declared through a `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider` entry. Wired from the root project as:

```kotlin
detektPlugins(project(":build-logic:detekt-rules"))
```

This keeps the rule source out of feature modules (no accidental classpath pollution), compiles before static analysis runs, and matches the existing `build-logic/` composite-build pattern.

### `ColorSchemeTest` luminance ordering

Use the built-in `androidx.compose.ui.graphics.Color.luminance()` extension (WCAG relative luminance) directly. No custom helper. Example assertion:

```kotlin
@Test
fun darkMode_surfaceLuminanceOrdering_isCorrect() {
    val scheme = nubecitaDarkColorScheme()
    assert(scheme.surfaceContainerLow.luminance() < scheme.surfaceContainer.luminance())
    assert(scheme.surfaceContainer.luminance() < scheme.surfaceContainerHigh.luminance())
    assert(scheme.surfaceContainerHigh.luminance() < scheme.surfaceContainerHighest.luminance())
}
```

A symmetric light-mode test asserts the inverse ordering between `surfaceContainer` and `surfaceContainerLow` (light-mode `Low` is brighter than `Container`).

### Search sub-screen hierarchy

`SearchScreen` is the canvas owner — its `Scaffold` paints `containerColor = surface`. The sub-screens (`SearchTypeaheadScreen`, `SearchActorsScreen`, `SearchPostsScreen`, `SearchFeedsScreen`) render as composables swapped inside the parent's content slot and do NOT declare their own Scaffolds. Each sub-screen sits transparently on the parent's canvas — either by not painting a root surface at all, or (if a root container is unavoidable for layout reasons) by wrapping in `Surface(color = Color.Transparent)` so the parent's paint shows through. Workstream 3e verifies this per-file rather than re-adding canvas paint at the sub-screen level.
