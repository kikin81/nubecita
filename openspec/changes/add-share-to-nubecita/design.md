# Design — Share to Nubecita (inbound Android share target)

**Status:** Approved in brainstorm (2026-07-15), incl. team review. v1 scope = text/links + a single image.

## Context

When a user shares from Chrome / a news app / Gallery, Android sends an `ACTION_SEND` intent to any app whose manifest declares a matching `<intent-filter>`. Nubecita declares none today, and even if the intent arrived, `MainActivity.handleIntent` branches on `intent.data` (a URI) — an `ACTION_SEND` intent has no `data`, only `EXTRA_TEXT` / `EXTRA_STREAM`, so it falls into the `uri == null` branch and is swallowed by `handleFcmExtrasTap`.

This design makes Nubecita a share target that lands the shared content in the post composer. It is grounded in four research passes over Android's official docs (share intents, Sharing Shortcuts, share-sheet security + latest platform) and a scoping of Nubecita's own composer/entry-point code. Citations are `developer.android.com`.

## Decision 1 — Reuse the single-Activity + deep-link router (not a `ShareReceiverActivity`)

`MainActivity` gets the `ACTION_SEND` intent-filter and a new branch in `handleIntent`. Rejected alternative: a dedicated exported `ShareReceiverActivity` (the `targetClass` a future Sharing-Shortcuts `<share-target>` would name).

Rationale: `MainActivity` is *already* an exported, untrusted entry point handling `ACTION_VIEW` deep links and OAuth redirects, so validated `ACTION_SEND` parsing there is idiomatic, not a new risk class. A second Activity fights Nubecita's deliberate single-Activity model, adds task-affinity/visual-trampoline complexity, and still has to solve the same "get content into the composer" problem. `ShareReceiverActivity` returns only if/when Sharing Shortcuts lands (a separate future capability).

## Decision 2 — The auth gate comes for free via the buffered deep-link channel

`DefaultDeepLinkRouter` is a `Channel(BUFFERED)` drained **only** after `MainShell` mounts — i.e. once the outer flow reaches `Main` (signed-in). So `deepLinkRouter.publish(ComposerRoute(...))` from the `ACTION_SEND` branch inherits the exact same behavior as an inbound deep link: a share received while signed-out (or mid-splash) **buffers** until auth/onboarding resolves, then opens the composer. No separate cold-start or auth-gating logic is written. A share that is never followed by a sign-in is dropped (acceptable).

The composer is a `@MainShell` `adaptiveDialog` route (full-screen on Compact, centered Dialog on Medium/Expanded), pushed via `navState.add(ComposerRoute(...))`. Reusing the router means the share opens the composer with the same presentation as the in-app compose FAB.

## Decision 3 — Payload → composer mapping

- **Shared URL** → seed the composer text field with the URL. The composer's existing `ExternalLinkDetector` / `TextLinkScanner` (`snapshotFlow` over the field) auto-generates the link card (which shows the fetched title). We do **not** also inject `EXTRA_SUBJECT` as text — the card already surfaces the title. The user types commentary above it.
- **Shared plain text (no URL)** → seed the text field verbatim.
- **Shared single image** → copy into app storage → `ComposerAttachment(appOwnedUri, verifiedMime)` → dispatch `ComposerEvent.AddAttachments`.
- **Screenshot + URL (both `EXTRA_TEXT` and `EXTRA_STREAM` in one share)** → follow the composer's existing "**images win**" rule: attach the image and keep the URL as plain text (no card while an image is attached; `externalLink`/GIF are already mutually exclusive with attachments).

## Decision 4 — `ComposerRoute` carries the prefill (route param, not a side-channel)

The copied, app-owned image URI travels as a **string param on `ComposerRoute`**, alongside the initial-text param. Rejected alternative: a Hilt-scoped holder the VM reads. Because `ComposerRoute` is a `@Serializable NavKey`, Compose Navigation restores it across process death, and the image reference (now an *app-owned* file URI with no transient grant) restores with it. A side-channel holder would be lost on process death and reintroduce exactly the fragility the app-owned copy exists to remove.

New params (all `String?`, default null — preserving the existing 3-arg constructor's call sites):

```kotlin
@Serializable
data class ComposerRoute(
    val replyToUri: String? = null,
    val quotePostUri: String? = null,
    val mentionHandle: String? = null,
    val sharedText: String? = null,      // NEW — seeds TextFieldState (URL auto-cards)
    val sharedImageUri: String? = null,  // NEW — app-owned file URI → AddAttachments
) : NavKey
```

## Decision 5 — Security (mandatory; the entry point is world-launchable)

`MainActivity` is already exported (for `ACTION_VIEW` deep links + LAUNCHER). Adding an `ACTION_SEND` intent-filter **widens its world-launchable surface** — any app can now launch it with a share payload — so every share extra is attacker-controlled. Requirements (sources: [Receiving content from other apps](https://developer.android.com/training/sharing/receive), [Sending content to other apps](https://developer.android.com/training/sharing/send), [Behavior changes: Android 14](https://developer.android.com/about/versions/14/behavior-changes-14), and [App security best practices](https://developer.android.com/privacy-and-security/security-best-practices)):

1. **Validate `EXTRA_TEXT`.** Allowlist scheme `http`/`https` only; reject `javascript:`/`file:`/`content:`/`intent:`; cap length. A non-URL text share is seeded verbatim (as text), but is never *treated as a URL*.
2. **`content://` from `EXTRA_STREAM` under the temporary per-URI grant only.** **Validate the URI authority** — a malicious sender can point a `content://` at Nubecita's *own* provider (confused-deputy); reject a URI whose authority is our own. Read via `ContentResolver` off-main-thread. Never `takePersistableUriPermission` or re-grant onward.
3. **Bounded copy.** Enforce a hard byte ceiling during the read/copy loop (guards storage exhaustion / OOM from an endless stream). Reject — do not truncate — anything over the cap.
4. **Actual-type verification.** Do not trust the intent's declared `type`. Verify with `ContentResolver.getType(uri)` **and** a magic-byte header sniff; only a confirmed decodable image becomes a `ComposerAttachment`. Otherwise drop the image and keep the text.
5. **`runCatching` all IO, but rethrow `CancellationException`.** Expired grant / provider crash / `FileNotFoundException` → fail closed: no attachment, generic user-facing error, composer still opens. Because `runCatching` on a suspend function also catches `CancellationException`, the `onFailure`/`getOrElse` block MUST rethrow it (`if (it is CancellationException) throw it`) so structured coroutine cancellation isn't swallowed — matching the established pattern in `DefaultAuthRepository`.
6. **Strip the share extras after publishing.** Right after `deepLinkRouter.publish(...)`, **mutate the current intent in place** — remove `EXTRA_TEXT`/`EXTRA_STREAM` and clear `clipData` — so a rotation/keyboard-resize `onNewIntent`/re-read can't replay the composer. Mutate in place (matching how `handleIntent` already consumes one-shot data, e.g. `intent.data = null`); do **not** replace the whole intent via `setIntent(Intent())`, which would drop flags/type/clipData that other handling may rely on.
7. **Re-validate on every entry.** `onNewIntent` (warm boot, `singleTask`) bypasses `onCreate` validation — re-run the full parse+validate there.
8. **No new exported components.** Only `MainActivity` (already exported) handles the intent. Any `PendingIntent` built is `FLAG_IMMUTABLE`.

## Decision 6 — Copied-image lifecycle & cleanup

Copies live in `filesDir/composer_shares/<uuid>.<ext>` (deterministic across process death; `cacheDir` can be OS-evicted mid-session). A `SharedMediaStore` seam owns `copyIn` / `delete` / `sweepOrphans`, keeping the lifecycle in one testable place. Cleanup fires on:

1. **Publish success** — after the blob uploads and the post is created, the local copy is dead weight → delete in the completion path.
2. **Discard / dismiss** — the composer's `ViewModel.onCleared()` (fires when the nav entry is popped — back-press, dialog scrim, nav-away — but **not** on config change, which the VM survives) deletes the copy if the post wasn't published.
3. **Attachment removed in-composer** — user taps ✕ on the shared image → delete immediately (now unreferenced).
4. **Boot-time orphan sweep** — `sweepOrphans()` on startup deletes anything in the subdir older than ~24 h — the backstop for process-death-without-`onCleared`. This is what bounds the directory.

**Graceful missing-file:** the composer tolerates the copy being gone on restore (rare eviction, or a stale route after a long background) — folds into Decision 5's `runCatching`: a dangling attachment degrades to "image unavailable," never a crash.

**Drafts handoff:** v1 treats a shared image as ephemeral. If the composer-drafts epic (`nubecita-4ok`) later persists shared media into a saved draft, cleanup must defer to the draft's lifecycle rather than delete on dismiss — an explicit handoff, out of scope here.

## Components (the three testable units)

| Unit | Module | Responsibility | Depends on |
|---|---|---|---|
| `ShareIntentParser` (+ validator) | `:core:posting` (pure) | `Intent`-in → typed `SharedContent` (Text / Url / Image / UrlWithImage / Invalid); scheme allowlist, length cap, authority reject. No IO. | Android `Intent` (value read only), JVM-testable |
| `SharedMediaStore` | `:core:posting` | `copyIn(uri): Uri?` (null on reject/fail — byte cap, `getType` + magic-byte verify, `runCatching` rethrowing `CancellationException`), `delete(uri)`, `sweepOrphans()`. Owns `filesDir/composer_shares/`. | `Context`, `ContentResolver`, injected `@IoDispatcher` |
| `ComposerRoute` prefill + VM seeding | `:feature:composer:{api,impl}` | New route params; VM seeds `TextFieldState` + dispatches `AddAttachments`; wires the 4 cleanup triggers. | existing composer |

The `MainActivity` `ACTION_SEND` branch is the thin glue: call `ShareIntentParser`, call `SharedMediaStore.copyIn` for an image, `deepLinkRouter.publish(ComposerRoute(...))`, strip extras.

## Testing

- **Unit (`:core:posting`)**: `ShareIntentParser` — URL scheme allowlist (accept http/https, reject javascript/file/content/intent), length cap, plain-text vs URL, screenshot+URL (both extras), own-authority reject, malformed/empty extras. `SharedMediaStore` — byte-cap rejection, MIME mismatch (declared image/png but non-image bytes) rejection, `runCatching` on IO failure, `sweepOrphans` age boundary.
- **Unit (`:feature:composer:impl`)**: VM seeds text from `sharedText` (and it auto-cards via the scanner); dispatches `AddAttachments` from `sharedImageUri`; "images win" when both present; cleanup called on publish / onCleared / attachment-remove.
- **Instrumented (`:app` or `:feature:composer`, `run-instrumented` label)**: an `ACTION_SEND` `text/plain` intent → composer opens prefilled (reference: `LoginScreenInstrumentationTest`'s intent pattern). Add the `run-instrumented` label to the PR.
- **Screenshot (`:feature:composer:impl`)**: composer prefilled with a URL (link card), with plain text, with a single image attachment.
- **On-device bench smoke**: `adb shell am start -a android.intent.action.SEND --es android.intent.extra.TEXT "https://example.com" -t text/plain net.kikin.nubecita` → composer opens carded. (Bench flavor fakes sign-in, so the buffered-router auth gate is satisfied.)

## Risks / open verification

- **`content://` read on the app-owned copy vs the transient grant.** After `copyIn`, the composer must open the *app-owned* URI (no grant needed), never the original `content://`. Verify the attachment upload path reads the copied URI.
- **`onCleared` timing for cleanup.** Confirm the composer VM is scoped to the nav entry (so `onCleared` == entry popped), not to an outer scope — otherwise dismiss-cleanup won't fire when expected.
- **New strings need es-419 + pt-BR** in the same change or CI `MissingTranslation` fails (run the touched module's own lint).
