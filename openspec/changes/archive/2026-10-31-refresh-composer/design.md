## Context

The composer (`:feature:composer:impl`, `ComposerScreenContent` in `ComposerScreen.kt`) renders a `TopAppBar` (close; counter + Post), then a non-scrolling `Column`: reply parent card, `OutlinedTextField` ("Compose post" label), inline mention suggestions, `ComposerOptionsChipRow` (language / audience / GIF chips), the attachment row (add-image button + reorderable thumbnail chips), and the link / GIF / video / quote cards. The route is tagged `adaptiveDialog()`: full-screen on Compact, a 640dp dialog card on Medium/Expanded. Nothing handles IME insets and the body does not scroll, so on short screens content ends up behind the keyboard.

The signed-in account's avatar and display name are resolved today only inside `DefaultOutgoingPostStore` (`SessionStateProvider` → `ActorProfileRepository.fetchProfile(did)`), privately.

Full product design and the spike's findings: `docs/superpowers/specs/2026-10-30-composer-refresh-design.md`. Epic `nubecita-5nbm`.

## Goals / Non-Goals

**Goals:**
- One shared, cached current-account source (multi-account groundwork).
- Identity row, borderless field, scrollable body with a single IME owner, quiet summary line.
- M3 Expressive `HorizontalFloatingToolbar` (standard colors) with Post as its FAB, riding the IME in both presentations.
- Taller attachment thumbnails in the existing strip.

**Non-Goals:**
- Account switching; new authoring actions; threadgate/postgate changes; reply-card redesign; a photo carousel.

## Decisions

**D1 — Shared `CurrentAccountRepository` in `:core:profile`, not a per-VM fetch.**
A `StateFlow<CurrentAccount?>` seeded from `SessionState.SignedIn.handle` immediately and refreshed once per session via `ActorProfileRepository.fetchProfile`; cleared on sign-out; app-scoped (`@Singleton`, application scope). *Alternative:* copy the fetch into `ComposerViewModel` (the spike did). Rejected: a network call per composer open, a second copy of `DefaultOutgoingPostStore`'s logic, and no single place for a future account switcher to update. `:core:profile` (not `:core:auth`) because it already owns `ActorProfileRepository` and is the documented home of "the future multi-account picker".

**D2 — Bench fake makes `:core:profile` flavored.** Screenshot/bench runs need a deterministic identity. *Alternative:* fake only at the composer. Rejected: the outgoing-post card and future switcher want the same fake. Cost: task names become `…ProductionDebug…`.

**D3 — `BasicTextField(state = textFieldState, decorator = …)` for the borderless field.** Keeps the sanctioned `TextFieldState` editor exception, the `composer_text_field` test tag, focus requester, and `contentReceiver` image paste. *Alternative:* `TextField` with transparent container/indicator colors. Rejected: still reserves label/indicator space and padding that fight the avatar alignment.

**D4 — Single IME owner, floating element.** `Scaffold(contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime))`; body in a `Box` with `.padding(inner).consumeWindowInsets(inner)`; scrolling `Column` with bottom padding ≥ toolbar height + margin; toolbar `.align(BottomCenter).imePadding().padding(bottom = 16.dp)`. Proven by the spike on the Fold cover screen and inside the unfolded `adaptiveDialog()` card. *Alternative:* toolbar in `Scaffold.bottomBar` (CLAUDE.md's pinned-input pattern). Rejected: a floating toolbar overlays content rather than reserving a bar's height, and the FAB sits beside, not inside, a bar.

**D5 — Toolbar always visible.** No `ExitAlwaysFloatingToolbarScrollBehavior`. Post must never scroll away.

**D6 — Post FAB semantics.** `FloatingToolbarDefaults.StandardFloatingActionButton` has no `enabled`; emulate with reduced alpha + `semantics { disabled() }` + no-op click when `canPost` is false; `NubecitaWavyProgressIndicator` while `Submitting` (the pre-commit guard rejects raw `CircularProgressIndicator`).

**D7 — Summary line replaces chip-state at a glance.** `labelSmall` / `onSurfaceVariant` line under the field: the language label (same formatter as today's chip, incl. `+N` overflow) and, for top-level posts, the audience label. Not tappable; the toolbar owns actions.

**D8 — Mention suggestions stay inline.** Brought into view with `BringIntoViewRequester` when they appear; the scrolling body's bottom padding keeps them clear of the toolbar. *Alternative:* overlay above the toolbar. Rejected: new z-order/inset surface for no proven need.

## Risks / Trade-offs

- [M3 Expressive toolbar API is experimental (1.5.0-beta02)] → isolate in one private composable; opt-in at that call site only.
- [Typeahead near the bottom of a long post on short screens was not covered by the spike] → explicit device-pass step in `nubecita-5nbm.2`.
- [`:core:profile` becoming flavored breaks unqualified task names in scripts/docs] → grep for `:core:profile:` task references when flipping it.
- [Removing the over-limit outline weakens the over-limit cue] → counter error tone + Post disabled remain; screen-reader announcement unchanged.
- [Screenshot baseline churn across the composer suite] → CI bot regeneration (`update-baselines`), reviewed per PR.

## Migration Plan

Three stacked PRs (`gh stack`), landed atomically: `nubecita-5nbm.1` (account source, no UI change) → `.2` (identity row, borderless field, scrolling body, summary line; chips and top-bar Post still present) → `.3` (floating toolbar, chips and top-bar Post removed, taller thumbnails). Each layer builds and is coherent on its own. Rollback: revert the stack's squash commit.

## Open Questions

None. Owner decisions are recorded in the design doc.
