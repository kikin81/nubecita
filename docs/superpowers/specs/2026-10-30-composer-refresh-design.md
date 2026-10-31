# Composer refresh — design

Date: 2026-10-30
Status: draft (awaiting approval)

## Intent

The composer (`:feature:composer:impl`) still looks like a form: a boxed
`OutlinedTextField` with a floating "Compose post" label, a row of option chips
under it, and Post in the top app bar. The refresh makes it feel like a writing
surface in the Material 3 Expressive style:

- **Who is posting is visible.** The signed-in account's avatar sits beside the
  text, with its display name on a small line above the text. This is groundwork
  for multi-account support: once a user can switch accounts, the composer must
  show which one a post goes out from.
- **The text blends into the screen.** A borderless field, set straight on the
  screen background, instead of an outlined box.
- **Actions live in an M3 Expressive floating toolbar** at the bottom, with Post
  as the toolbar's floating action button.
- **Photos preview larger.** The existing thumbnail strip stays (it scrolls
  smoothly, supports drag-to-reorder and shows ALT badges) with taller
  thumbnails.
- **Language and audience stay visible at a glance** on a quiet summary line,
  since the toolbar shows them only as icons.

Prior art the owner pointed at: Meta Threads' composer (avatar column,
borderless text, large media pager, actions docked above the keyboard), and the
M3 floating toolbar docs (standard colors, toolbar + FAB).

This supersedes `nubecita-86m` ("Adopt M3 HorizontalFloatingToolbar for composer
authoring actions", P3, open since 2026-05-06). That issue deferred the toolbar
until there were 3+ authoring actions; there are now four (image, GIF, language,
audience).

## What the spike proved

A throwaway spike (`spike/composer-refresh`, commit `bb806b91`, local only,
never merged) was run on a Samsung Fold 8 cover screen (bench build) and the
Pixel 10 Pro Fold emulator unfolded:

- **The toolbar rides the keyboard in both presentations.** On the phone layout
  (Fold cover screen) and inside the `adaptiveDialog()` card (unfolded), the
  toolbar sits on the keyboard's top edge. No dialog-specific inset code was
  needed. The working setup: `Scaffold(contentWindowInsets =
  WindowInsets.safeDrawing.exclude(WindowInsets.ime))`, the body in a `Box` with
  `.padding(innerPadding).consumeWindowInsets(innerPadding)`, and the toolbar
  aligned `BottomCenter` with `.imePadding().padding(bottom = 16.dp)`. This is
  the single-IME-owner rule from CLAUDE.md, applied to a floating element.
- **The current composer has two latent bugs the refresh fixes.** The body
  does not scroll (no `verticalScroll`), and nothing handles the IME, so on a
  short screen the add-image button and anything below the field end up behind
  the keyboard (reproduced on the Fold cover screen on `main`).
- **The avatar data already exists.** `DefaultOutgoingPostStore` resolves the
  signed-in user's avatar and display name from `SessionStateProvider` →
  `ActorProfileRepository.fetchProfile(did)`. The spike copied that into the
  composer VM; the epic instead extracts it into a shared source (Goal 1).
  `:core:profile` has no bench fake, so the bench build shows the fallback
  (initial + handle).
- **Icons:** everything needed already exists in `NubecitaIconName`
  (`AddPhotoAlternate`, `Language`, `Settings`, `Send`). A globe for audience
  read too close to the language globe; the owner chose **Settings** for
  audience.
- **Brand spinner:** the toolbar's Post FAB must show
  `NubecitaWavyProgressIndicator` while submitting (the repo's pre-commit guard
  rejects a raw `CircularProgressIndicator`; the current `ComposerPostButton`
  already does this).

## Goals

1. **Shared current-account source.** A small, cached repository in
   `:core:profile` (working name `CurrentAccountRepository`) exposing the
   signed-in account as a `StateFlow` (DID, handle, display name, avatar URL):
   seeded from the session's handle immediately, refreshed from
   `ActorProfileRepository.fetchProfile(did)` once per session (not per
   composer open), cleared on sign-out. `DefaultOutgoingPostStore` is refactored
   onto it, the composer reads it, and a future account switcher would update
   it. A bench fake supplies a fixed account so screenshots and bench runs show
   a real-looking identity.
2. Posting identity: avatar (40dp) left of the text, display name above the
   text in `labelMedium` / `onSurfaceVariant`, falling back to the handle when
   the account has no display name.
3. Borderless text field (no outline, no floating label), placeholder "What's
   on your mind?" only.
4. Body scrolls; the toolbar never covers the last element (bottom content
   padding equal to the toolbar's height plus margin).
5. `HorizontalFloatingToolbar` with **standard** colors
   (`FloatingToolbarDefaults.standardFloatingToolbarColors()`: `surfaceContainer`
   pill) holding Add image, GIF, Language, Audience (top-level posts only); Post
   is the toolbar's `StandardFloatingActionButton`.
6. The toolbar is always visible (no hide-on-scroll), at the bottom when the
   keyboard is down and on top of the keyboard when it is up.
7. Picked photos keep today's chip strip (scroll, drag-to-reorder, ALT badge,
   remove, tap for alt text) with taller thumbnails.
8. A quiet summary line under the text field shows the current language and,
   for top-level posts, the audience (e.g. "English · Visible to all"), using
   the same labels the chips use today.
9. Works unchanged in the phone full-screen and the tablet/foldable
   `adaptiveDialog()` presentation.

## Non-goals

- Multi-account switching itself. This epic only displays the current account.
- New authoring actions (music, stickers, polls, location). Bluesky's lexicon
  has no polls or location; music/stickers are Threads-only.
- Threadgate/postgate changes. The audience picker is reused as is.
- Changing the reply parent card. It stays above the text, capped at three
  lines (already the case on `main`); the owner reviewed it on the Fold cover
  screen.
- Restyling the link, GIF, video, or quote cards beyond re-aligning them to the
  new avatar column.

## Anti-goals

- A toolbar that hides Post (scroll-away behavior, or Post anywhere other than
  the toolbar FAB once the toolbar exists).
- Content trapped behind the keyboard or the toolbar on short screens (Fold
  cover, landscape phones).
- Losing state the chips used to show at a glance.
- A per-form-factor fork of the layout. One layout, both presentations.

## Constraints

- MVI: the composer is the sanctioned editor exception (the VM owns a
  `TextFieldState`); the borderless field keeps using that `TextFieldState`
  overload (`BasicTextField(state = …)`), so cursor/IME behavior is unchanged.
- Keep the field's `testTag("composer_text_field")`, `focusRequester`
  auto-focus, and `contentReceiver(imagePasteReceiver)` (image paste) — the
  baseline-profile journey and paste support depend on them.
- `@ExperimentalMaterial3ExpressiveApi` opt-in is required at the toolbar call
  site (M3 `1.5.0-beta02`).
- Accessibility: every toolbar action needs a content description or label
  (the chips had visible text; the icons do not). The Post FAB keeps the
  existing Post semantics (disabled when `canPost` is false; the submitting
  state announced).
- Screenshot tests: `ComposerScreenScreenshotTest` and
  `ComposerScreenLanguageChipScreenshotTest` change; baselines are regenerated
  by the CI bot (`update-baselines` label), never committed from a Mac.
- `:core:profile` becomes a dependency of `:feature:composer:impl`.
- `:core:profile` is unflavored today; adding the bench fake makes it a
  flavored module (`nubecita.android.flavors` + `src/bench/`), which changes
  its Gradle task names (`…ProductionDebug…`).

## Decided (by the owner)

- Bottom layout **B**: floating toolbar with Post as its FAB (chosen over a flat
  keyboard bar and over keeping Post in the top bar).
- **Standard** toolbar colors over vibrant.
- **Display name**, small typography, on the identity line (not the handle).
- Toolbar stays at the bottom when the keyboard is down (Material placement);
  no separate inline actions row like Threads.
- Reply layout: keep today's parent card, three-line cap.
- Audience icon: **Settings**.
- Photos: **keep the existing chip strip**, taller thumbnails; no carousel. The
  spike's M3 carousel is dropped.
- Language/audience state: **a quiet summary line** under the text.
- Posting account: **a shared, cached current-account source**, not a per-VM
  fetch.
- Delivery: one epic as a `gh stack` of **3 PRs** (the shared account source added the third).

## Left to the builder (my calls, flagged)

- Character counter stays in the top app bar next to close.
- Over-limit signal without the red outline: the counter's existing error state
  only.
- Toolbar always visible; no `ExitAlwaysFloatingToolbarScrollBehavior`.
- Avatar size 40dp; name line `labelMedium` / `onSurfaceVariant`, one line,
  ellipsized.
- Thumbnail height: roughly double today's chip (about 120dp), keeping the
  aspect handling, ALT badge and remove disc as they are.
- Summary line: `labelSmall` / `onSurfaceVariant`, one line, not tappable (the
  toolbar owns the actions); hidden while the alt editor is open.
- The FAB's disabled look (the toolbar FAB has no `enabled` parameter):
  reduced alpha plus `semantics { disabled() }`, no-op click.
- @-mention suggestions stay inline under the text field (no overlay). When
  the suggestion list appears it is brought into view
  (`BringIntoViewRequester`) inside the scrolling body, and the body's bottom
  padding keeps it clear of the floating toolbar. The spike did not cover
  typeahead, so PR 1's device pass must include typing `@` near the bottom of
  a long post on the Fold cover screen.

## Delivery — `gh stack` (3 PRs)

1. **Shared current-account source** (`:core:profile`). The repository, its
   Hilt binding, a bench fake (making `:core:profile` flavored), and
   `DefaultOutgoingPostStore` moved onto it. No UI change; unit tests for
   seeding, refresh, fallback to handle and sign-out clearing.
2. **Identity + writing surface.** Composer reads the account source;
   avatar/name row; borderless `BasicTextField`; scrollable body; IME
   single-owner insets; inline mention suggestions brought into view; summary
   line. The chip row and Post-in-top-bar remain in this PR, re-aligned to the
   avatar column, so it ships coherent on its own.
3. **Floating toolbar + taller thumbnails.** `HorizontalFloatingToolbar` with
   the four actions and the Post FAB; remove the chip row, the top-bar Post
   button and the attachment row's leading add button; bottom content padding;
   a11y labels; taller attachment thumbnails; delete `nubecita-86m`'s
   placeholder comments and close that issue as superseded.

Each PR: unit tests for its state, screenshot fixtures for the new layout
(phone + tablet-dialog previews, light + dark, reply mode, with photos), and a
device pass on the Fold cover screen and an unfolded foldable (PR 2 adds typing
`@` near the bottom of a long post).
