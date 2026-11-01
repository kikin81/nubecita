# Post overflow sheet + pin your own post — design

- **Date:** 2026-10-31
- **Epic:** `nubecita-iwjz` (pinned post — display + pin/unpin your own post)
- **Children, in stack order:**
  1. `nubecita-iwjz.3` — overflow menu becomes a grouped bottom sheet (post cards + vertical videos)
  2. `nubecita-iwjz.4` — Mute thread / Unmute thread actually mutes the thread
  3. `nubecita-iwjz.2` — Pin to your profile / Unpin from profile
- **Already shipped:** `nubecita-iwjz.1` — a profile's pinned post renders at the top of its Posts tab.

## Intent

Today the "…" on a post opens a Material `DropdownMenu` anchored to the action row. It is
hard to reach with a thumb on a tall phone, is already seven rows long, and has no room
to grow. Nubecita also has no way to pin one of your own posts, which the official
Bluesky app offers as the first row of its post menu.

The work matches the official app's post sheet: a bottom sheet of rounded, grouped
cards, with the label on the left and an icon on the right. "Pin to your profile" sits at
the top for your own posts, and destructive actions sit at the bottom.

## Goals

1. **One shared overflow sheet** replaces both overflow dropdowns:
   - `PostOverflowAffordance` in `designsystem/.../component/PostCard.kt` (used by Feed,
     Post detail, Profile and every other `PostCard` host)
   - `feature/videos/impl/.../ui/VideoOverflowMenu.kt` (vertical video feed)
2. **Own posts stop offering actions aimed at yourself.** On a post where
   `viewer.isOwnPost` is true, these rows are removed: "Report post", "Report @author",
   "Mute @author" / "Unmute", and "Block @author" / "Unblock". This is a bug today (see the
   2026-10-31 screenshot of the profile's Posts tab).
3. **Mute thread actually mutes the thread, and it's a toggle.** Today `MuteThread` and
   `UnmuteThread` only show a "coming soon" snackbar
   (`DefaultPostInteractionHandler.onOverflowAction`). In this epic:
   - Map `app.bsky.feed.defs#viewerState.threadMuted` into `ViewerStateUi`.
   - The row reads "Mute thread" or "Unmute thread".
   - Tapping it calls `app.bsky.graph.muteThread` or `unmuteThread` for real. See D7.
4. **Pin / Unpin your own post** from every surface that shows the sheet: Feed, Post
   detail, Profile and Videos.
   - Pinned state comes from the server's `viewerState.pinned`, mapped into `ViewerStateUi`.
   - We don't derive or track it ourselves, per the "prefer atproto APIs over app
     logic" rule.

## Non-goals

- **"Mute words & tags"** and **"Edit interaction settings"**: both are in the official
  sheet and both are out of scope.
- **The official app's other conditional rows**: "Hide post for me", "Hide reply for
  everyone", "Show more / less like this", the algo-labelling rows, and the logged-out
  "Sign in to view post".
- **In-place translation.** Translate keeps opening an external translator (epic
  `nubecita-s6xk` owns in-place translation).
- **Other menus.** The repost menu in `PostCard` (Repost / Quote) and the ProfileHero
  overflow stay as dropdowns.

## Anti-goals (would be a failure even if it "works")

- **Pinning clobbers the profile.** The pin write is a read-modify-write of
  `app.bsky.actor.profile`. If a pin drops displayName, description, avatar, banner,
  labels, joinedViaStarterPack, website links or any other field, the feature is broken.
  The write uses `swapRecord` like `DefaultProfileRepository.updateProfile`, so a
  concurrent edit fails rather than being overwritten.
- **Scrolling gets slower.** 120 Hz scrolling is a hard requirement. A collapsed overflow
  must cost nothing on the scroll path: no `ModalBottomSheet` and no sheet state in
  composition while it is closed, and no per-card `rememberModalBottomSheetState`.
  Today's dropdown is lazy (pinned by `DropdownMenuLazinessTest`), and the sheet must be
  just as lazy, with a test that proves it.
- **Stale pin state between screens.** Pinning post B while post A is pinned must show A
  as "Pin" and B as "Unpin" on every surface that has them loaded. The profile's
  pinned-post slot must also update without a manual refresh.

## Decisions

### D1 — Pin eligibility = any post you authored (confirmed against social-app)

Confirmed in `bluesky-social/social-app`, file
`src/components/PostControls/PostMenu/PostMenuItems.tsx` on `main`, 2026-10-31.

- The pin group renders under `isAuthor`, which means `post.author.did ===
  currentAccount.did`. The official app doesn't exclude replies or quotes.
- The label is `post.viewer.pinned ? "Unpin from profile" : "Pin to your profile"`.

`nubecita-iwjz.2`'s description guessed "top-level only". This design follows the
official app instead, as that ticket asked. Gate on `viewer.isOwnPost`.

### D2 — Row order and groups

Each numbered item is one rounded group card. Rows in a group are stacked the way
`NubecitaListGroup` already stacks them. The order follows social-app, filtered to what
we support.

**Own post** (`isOwnPost`):

1. Pin to your profile / Unpin from profile
2. Translate (only if text isn't blank) · Copy post text
3. Mute thread / Unmute thread
4. Delete post

**Someone else's post:**

1. Translate (only if text isn't blank) · Copy post text
2. Mute thread / Unmute thread
3. Mute @handle / Unmute @handle · Block @handle / Unblock @handle · Report post · Report @handle

Where our wording differs from social-app, we keep our existing strings ("Mute @handle"
rather than "Mute account"). We also keep the Unblock row, which social-app hides once
you're blocking. Both keep current behaviour and avoid new translations. The new
strings are listed in D6.

### D3 — Sheet shape and laziness (builder's call within these bounds)

- **Component.** One composable in `:designsystem`, rendered by both the `PostCard` host
  and the video page. The input is the list of actions, already grouped. Each row has a
  label, an icon and an action.
- **Video menu.** `VideoOverflowMenu.kt` already builds its action list from the same
  rules as `PostCard`. Move that rule set into one shared pure function, so the two
  surfaces can't drift apart and so it can be unit-tested without Compose. The function
  takes `PostUi` and returns the grouped actions.
- **Library.** Use M3 `ModalBottomSheet`, with group cards built from `NubecitaListGroup`
  / `NubecitaListItem`. Follow the surface-role table: the sheet is a windowed surface,
  so tonal elevation is allowed.
- **Icons.** Icons are Material Symbols from the subset font. Add any missing glyphs by
  codepoint (see the icon memory), and never regenerate the whole font.
- **Laziness.** Hold the open state per card, as `expanded` is held today, and compose
  `ModalBottomSheet` only while it is open. Replace `DropdownMenuLazinessTest` with an
  equivalent test for the sheet.
- **Order of events on tap.** Tapping a row hides the sheet and then dispatches the
  action. A destructive row (Delete) keeps the confirmation dialog it already has.

### D4 — Pin write path

`PostInteractionHandler` (`:core:post-interactions`) owns the pin action, because every
surface that hosts the sheet already delegates to it.

1. Add `PostOverflowAction.PinPost` and `UnpinPost`.
2. `DefaultPostInteractionHandler.onOverflowAction` routes them to a new repository call:
   - `setPinnedPost(strongRef?)` — `null` means unpin.
   - The call is a read-modify-write of the viewer's `app.bsky.actor.profile` record:
     `getRecord`, then `putRecord` with `swapRecord`.
3. **Where that code lives (builder's call).** The read-modify-write logic currently lives
   in `:feature:profile:impl`'s `DefaultProfileRepository.updateProfile`, which feature
   modules can't reach from `:core`. Either extract a shared profile-record writer into
   `:core:profile` and have `updateProfile` reuse it, or put a narrow pin writer in
   `:core:profile`. Don't duplicate the read-modify-write, and don't make
   `:core:post-interactions` depend on a feature module.
4. **Optimistic, with rollback.** Like the like/repost flow, `PostInteractionsCache`
   updates `viewer.isPinned` right away:
   - Pinning B sets `isPinned = true` on B and `false` on any other cached post that was
     pinned.
   - On failure, both flags are restored and an error snackbar shows, through the
     handler's existing interaction effects.
   - On success, the snackbar reads "Post pinned" or "Post unpinned".
5. **Profile refresh** *(Claude's call)*. After a successful pin or unpin, the profile
   screen's pinned-post slot (`ProfileState.pinnedPost`) must update.
   - `ProfileViewModel` already merges `PostInteractionsCache.state` into its posts. Use
     that same merge: when the cache reports a post by the profile's DID whose
     `isPinned` changed, set `pinnedPost` from it (or clear it on unpin).
   - Do **not** move `ownProfileUpdates` out of `:feature:profile:impl` into
     `:core:profile`. That would make it an app-wide event stream, which is the kind
     of bus this repo avoids.
6. **Repeated taps.** Ignore repeat taps while a pin write is in flight. social-app
   disables the row while a pin is pending.
7. **Server lag after a successful pin** *(Claude's call)*. A refresh that comes back
   from the server with an out-of-date `pinned` value must not undo a pin that just
   succeeded.
   - Treat the pin flag exactly like `isLikedByViewer`. `PostInteractionsCache.seed`
     already keeps the local value of a like while its write is in flight and after it
     succeeds. The pin flag follows the same rule, with the same expiry.
   - This is the cache's existing optimistic layer applied to one more field, not new
     state the app tracks on its own. The server's `viewerState.pinned` is still the
     source of truth whenever no local write is involved.
8. **Unpin clears only if the profile still points at this post** *(Claude's call)*.
   - The writer takes the post being unpinned, not a bare `null`. After reading the
     record, it clears `pinnedPost` only if `pinnedPost.uri` equals that post's URI.
   - If the record already points elsewhere (pinned from another device, or a stale
     view), the write is skipped and the call succeeds, so the local flag on this post
     just clears.
   - This stops a stale "Unpin" tap from silently unpinning a different post.

### D5 — Mapping

In `:core:feed-mapping`, map `viewerState.pinned` to `ViewerStateUi.isPinned` and
`viewerState.threadMuted` to `ViewerStateUi.isThreadMuted`. Both default to `false`
when absent. Update the bench fixtures so at least one own post comes back pinned and
one post has its thread muted. The bench smoke test needs both.

### D7 — Thread mute write path (decided by the user, 2026-10-31: wire the real call)

1. **Which thread.** The thread is identified by its root post: `record.reply.root.uri`,
   or the post's own URI when it isn't a reply. This matches social-app's `rootUri`.
   `PostUi` doesn't carry the reply root today, so `:core:feed-mapping` adds it (e.g.
   `replyRootUri: String?` on `PostUi`; the name is the builder's call).
2. **Repository.** A new `ThreadMuteRepository` with `mute(rootUri)` and
   `unmute(rootUri)` wraps `MuteThreadRequest` / `UnmuteThreadRequest`.
   - It lives beside `MuteRepository` in `:core:actors`, or in `:core:post-interactions`
     (builder's call).
   - It gets a bench fake, so the bench build stays offline.
3. **Optimistic, with rollback.** Route `MuteThread` / `UnmuteThread` through the
   handler, off the coming-soon branch. `UnblockAuthor` stays coming-soon; that's out of
   scope.
   - Flip `isThreadMuted` right away on **every cached post with the same root**, not
     just the post that was tapped. On failure, restore it and show an error snackbar.
   - Keep the flag through server lag, the same way as the pin flag (D4.7).
   - Ignore repeat taps on the same root while a write is in flight.
4. **Snackbars.**
   - Success: "Thread muted" / "Thread unmuted".
   - Failure: "Couldn't mute thread" / "Couldn't unmute thread".

### D6 — New strings (es-419 + pt-BR in the same commit)

- "Pin to your profile"
- "Unpin from profile"
- "Post pinned"
- "Post unpinned"
- "Couldn't pin post" (and the matching unpin failure, if it's worded separately)
- "Thread muted"
- "Thread unmuted"
- "Couldn't mute thread"
- "Couldn't unmute thread"

"Unmute thread" already exists as `moderation_action_unmute_thread`. Run the touched
module's own lint; `:app` lint misses `MissingTranslation` in library
modules.

## Delivery

- One `gh stack`:
  - PR 1 = `iwjz.3` (sheet + `isPinned` / `isThreadMuted` / reply-root mapping),
    targets `main`.
  - PR 2 = `iwjz.4` (real thread mute, D7), stacked on PR 1.
  - PR 3 = `iwjz.2` (pin), stacked on PR 2.
  - Land all three with `gh stack merge`; the epic cuts one release.
- **What each PR ships before the next lands:**
  - PR 1: the Mute/Unmute thread row label follows the server, but tapping it still
    shows "coming soon". `isPinned` is mapped but unused, and there is no pin row.
  - PR 2: the thread row really mutes and unmutes.
  - PR 3: the pin row appears.

**Testing:**

- **Unit tests:**
  - the grouped-action rule function: own vs others' post, blank text, muted/blocked
    pairs, thread muted, pinned
  - the handler: pin/unpin optimistic flip, swapping pinned A → B, rollback on failure,
    repeat-tap guard
  - the handler: thread mute/unmute flips every cached post with the same root, sends
    the root URI (not the reply's own URI) when muting from a reply, rolls back on
    failure, and ignores repeat taps
  - the record writer: read-modify-write keeps every other field, sends `swapRecord`,
    clears on unpin
- **Screenshot tests:** the sheet in light and dark, for an own post (pinned and not)
  and someone else's post (muted and blocked variants).
- **Device pass on the bench build:**
  - open the sheet from Feed, Post detail, Profile and Videos
  - mute a thread from a reply, then confirm the root post's sheet reads "Unmute thread"
  - pin a post, then confirm Profile shows it pinned and the sheet reads "Unpin"
  - unpin it
  - confirm the persisted state change in a screenshot, not just the tap

## Left to the builder

- The exact composable and API names.
- Whether the sheet shows a drag handle and a title. The official sheet has a handle and
  no title.
- Which Material Symbols glyph to use for each row.
- Where the pin writer lives in `:core:profile` (D4.3).
- How the profile slot refreshes, within the "no new bus" rule (D4.5).
