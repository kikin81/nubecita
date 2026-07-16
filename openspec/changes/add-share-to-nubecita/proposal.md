## Why

Users constantly find things worth posting *outside* Nubecita — an article in Chrome, a story in a news app, a photo in Gallery. Today the only path is: copy the link, switch to Nubecita, open the composer, paste. Every competitor and the official Bluesky app short-circuit that with a system **share sheet** entry: tap Share → pick the app → land in a composer with the content already there.

Nubecita has no inbound share support. A shared `ACTION_SEND` intent currently reaches `MainActivity` and is silently dropped (it has no `intent.data`, so it falls into the `uri == null` FCM branch and is swallowed). The manifest comments already anticipate this ("future share sheet upgrades", "a future share extension").

This also serves the growth thesis from the 2026-07-15 app-health review: reducing friction to *create* is one of the few real engagement levers, and "share an article to post about it" is a high-intent creation path that costs the user almost nothing.

## What Changes

Make Nubecita an Android **share target** for **text/links and a single image** (v1). When a user shares from another app, Nubecita appears in the share sheet and opens the post composer prefilled with the shared content.

### Entry point (`:app`)

- Add `ACTION_SEND` intent-filters to `MainActivity` for `text/plain` and `image/*` (single item; **not** `ACTION_SEND_MULTIPLE` in v1).
- Add an `ACTION_SEND` branch to `MainActivity.handleIntent` (before the `uri == null` early return) that: validates the payload, copies a shared image into app-private storage, publishes a prefilled `ComposerRoute` through the existing `DefaultDeepLinkRouter`, then **strips the share extras** off the activity intent so a config-change `onNewIntent`/re-read can't re-trigger.

### Payload parsing + validation (`:core:posting`, pure)

- A pure `ShareIntentParser` / validator: extract `EXTRA_TEXT` and `EXTRA_STREAM`; **allowlist the URL scheme** (`http`/`https` only), cap length; classify into a typed `SharedContent` model. No Android framework state — unit-testable on the JVM.

### App-owned media copy + lifecycle (`:core:posting`)

- A `SharedMediaStore` seam: `copyIn(uri): Uri` (bounded read with a hard byte cap, `runCatching` around IO, **actual-type verification** via `ContentResolver.getType` + magic-byte sniff), `delete(uri)`, `sweepOrphans()`. Copies live in a dedicated `filesDir/composer_shares/` subdirectory (deterministic across process death, unlike `cacheDir`).

### Composer prefill (`:feature:composer`)

- Extend `ComposerRoute` (today `replyToUri` / `quotePostUri` / `mentionHandle`) with prefill params: an initial text/URL string and an app-owned image URI string. `ComposerViewModel` seeds `TextFieldState` (a URL auto-generates a link card via the existing `ExternalLinkDetector` scanner) and dispatches `AddAttachments` for the image. Cleanup of the copied file fires on publish success, discard (`onCleared`), attachment-removal, and a boot-time orphan sweep.

## Capabilities

### New Capabilities

- `share-target`: Nubecita as an inbound Android share destination (receiving `ACTION_SEND` and routing it into the composer).

### Modified Capabilities

- `feature-composer`: `ComposerRoute` gains prefill params; `ComposerViewModel` consumes them. No change to existing reply/quote/mention behavior.

## Impact

- **Affected modules**: `:app` (manifest + `MainActivity` entry branch), `:core:posting` (`ShareIntentParser`, `SharedMediaStore`), `:feature:composer:{api,impl}` (route params + VM seeding). No new external dependencies (`androidx.core` `IntentCompat` is already available).
- **New strings**: a small number of user-facing error strings (e.g. "Couldn't attach that image") — requires `values-b+es+419` + `values-pt-rBR` translations in the same change (repo `MissingTranslation` guard).
- **Security surface**: adds a world-launchable, untrusted `ACTION_SEND` entry point. The design's security requirements are mandatory, not optional (scheme allowlist, `content://` authority validation, byte cap, actual-type verification, extra-stripping, re-validate on `onNewIntent`).
- **Backwards compatibility**: additive. Existing `ACTION_VIEW` deep links, OAuth redirect, and FCM-tap handling in `MainActivity` are untouched; the new branch sits before the `uri == null` path.

## Non-goals (v1)

- **Multiple images (`ACTION_SEND_MULTIPLE`) and video.** Deferred — they add carousel state, chunked/async copy, and video thumbnailing/constraints on top of the core routing + security work.
- **Sharing Shortcuts / Direct Share row ("share directly to a DM").** A separate future capability built on `ShortcutManagerCompat`; not required for share-into-composer and explicitly on the roadmap after v1.
- **Draft persistence of a shared image.** v1 treats a shared image as ephemeral. The composer-drafts epic (`nubecita-4ok`) owns persisting shared media into a saved draft, including its cleanup lifecycle — noted as an explicit handoff.
- **Custom share-sheet actions / rich preview / share-result callbacks.** Sender-side enhancements, out of scope.
