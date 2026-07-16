## ADDED Requirements

### Requirement: Nubecita is an Android share target for text/links and a single image

Nubecita SHALL declare `ACTION_SEND` `<intent-filter>`s on `MainActivity` for `text/plain` and `image/*` (single item), so it appears in the system share sheet when another app shares a URL, text, or an image. When selected, it SHALL open the post composer prefilled with the shared content. `ACTION_SEND_MULTIPLE` and `video/*` are out of scope for v1.

#### Scenario: Sharing a link opens the composer with a link card

- **WHEN** the user shares a URL (`ACTION_SEND`, `text/plain`, `EXTRA_TEXT` = an `http`/`https` URL) to Nubecita
- **THEN** the composer opens with the URL seeded into the text field, and the existing link-scanner generates the external link card. The user can add commentary and post.

#### Scenario: Sharing plain text opens the composer with that text

- **WHEN** the user shares non-URL text
- **THEN** the composer opens with the text seeded verbatim into the field, and no link card is generated.

#### Scenario: Sharing a single image opens the composer with it attached

- **WHEN** the user shares one image (`ACTION_SEND`, `image/*`, `EXTRA_STREAM` = a `content://` URI)
- **THEN** the image is copied into app-private storage and attached to the composer as a `ComposerAttachment`; the composer opens with the image attached.

#### Scenario: Screenshot + URL — images win

- **WHEN** a share carries both `EXTRA_TEXT` (a URL) and `EXTRA_STREAM` (an image)
- **THEN** the image is attached and the URL is kept as plain text in the field with no link card (matching the composer's existing images-exclude-external-embed rule).

#### Scenario: Sharing while signed out buffers until sign-in

- **WHEN** a share is received while the user is signed out or the app is mid-splash
- **THEN** the share buffers in the existing deep-link channel and opens the composer only after auth/onboarding resolves and `MainShell` mounts. No separate cold-start logic is added. A share never followed by a sign-in is dropped.

### Requirement: The `ACTION_SEND` entry point is treated as untrusted and validated

Because an `ACTION_SEND` `<intent-filter>` makes `MainActivity` world-launchable, the share branch SHALL validate every extra before use. `MainActivity` remains the only exported component; no `ShareReceiverActivity` is introduced in v1.

#### Scenario: URL scheme is allowlisted

- **WHEN** `EXTRA_TEXT` carries a `javascript:`, `file:`, `content:`, or `intent:` value
- **THEN** it is NOT treated as a URL. A well-formed non-URL string may be seeded as plain text, but only `http`/`https` values are ever treated as links.

#### Scenario: content:// authority is validated

- **WHEN** `EXTRA_STREAM` is a `content://` URI whose authority is Nubecita's own provider
- **THEN** it is rejected (confused-deputy defense), not read.

#### Scenario: Oversized stream is rejected, not truncated

- **WHEN** the shared image stream exceeds the hard byte cap during copy
- **THEN** the copy is aborted and the image rejected (no truncation, no storage exhaustion, no OOM); the composer still opens with any valid text.

#### Scenario: Declared type is not trusted

- **WHEN** a shared stream declares `image/*` but its actual content (via `ContentResolver.getType` + magic-byte sniff) is not a decodable image
- **THEN** the image is dropped and not attached; the composer opens with any valid text.

#### Scenario: IO failure fails closed

- **WHEN** resolving/copying the `content://` throws (expired grant, provider crash, `FileNotFoundException`, `SecurityException`)
- **THEN** the failure is caught, no attachment is added, a generic error is surfaced, and the app does not crash.

#### Scenario: Share extras are stripped after consumption

- **WHEN** the share branch has published the `ComposerRoute`
- **THEN** the activity intent's `EXTRA_TEXT`/`EXTRA_STREAM` are cleared so a subsequent configuration change or `onNewIntent` re-read cannot re-trigger the composer. `onNewIntent` re-runs the full parse+validate for genuinely new shares.

### Requirement: A copied shared image has a bounded, cleaned-up lifecycle

A shared image SHALL be copied into a dedicated `filesDir/composer_shares/` subdirectory (deterministic across process death) via a `SharedMediaStore` seam, and the copy SHALL be deleted at the end of the compose session.

#### Scenario: Copy deleted on publish

- **WHEN** the post is successfully published
- **THEN** the copied file is deleted in the completion path.

#### Scenario: Copy deleted on discard

- **WHEN** the composer is dismissed without publishing (back, scrim, nav-away → `ViewModel.onCleared()`)
- **THEN** the copied file is deleted (a config change, which the VM survives, does NOT delete it).

#### Scenario: Copy deleted when the attachment is removed

- **WHEN** the user removes the shared image in the composer before posting
- **THEN** the copied file is deleted immediately.

#### Scenario: Orphans are swept

- **WHEN** the app starts
- **THEN** `sweepOrphans()` deletes files in the subdirectory older than the retention window, bounding the directory even when a prior session died without `onCleared` running.

#### Scenario: Missing copy degrades gracefully

- **WHEN** the composer is restored (e.g. after process death) but the copied file is gone
- **THEN** the attachment degrades to an "image unavailable" state; the app does not crash.

## MODIFIED Requirements

### Requirement: `ComposerRoute` supports prefill of shared content

`ComposerRoute` SHALL gain two optional params — `sharedText: String?` and `sharedImageUri: String?` (both default null) — carried on the serialized `NavKey` so Compose Navigation restores them across process death. `ComposerViewModel` SHALL seed its `TextFieldState` from `sharedText` and dispatch `AddAttachments` from `sharedImageUri`. Existing `replyToUri` / `quotePostUri` / `mentionHandle` behavior and all existing call sites are unchanged (new params default null).

#### Scenario: Existing composer entry points unaffected

- **WHEN** the composer is opened from the feed FAB, a reply, a quote, or a mention (no shared params)
- **THEN** behavior is identical to before this change; `sharedText`/`sharedImageUri` are null and ignored.
