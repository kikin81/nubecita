## Context

Bluesky profile links (2026-10-22) are modeled as one `app.bsky.actor.link` record per link (`tid` key; `url`, `createdAt`, optional `title` ≤ 40 graphemes, optional `icon` blob png/jpeg/webp ≤ 100 KB, self-`labels`). The `app.bsky.actor.profile` record's `links` array of `strongRef`s (max 10) chooses which links show and in what order. `ProfileViewDetailed.links` returns resolved `ProfileLinkView`s (`uri`, `cid`, `url`, `title?`, `icon?` CDN URL, `labels`), with taken-down links already omitted. `atproto-kotlin` 9.13.0 carries all of these types.

The official client's implementation (`bluesky-social/social-app` → `src/features/profileLinks/`) is the behavioral reference: CardyB favicon proxy, a support-provider domain table, a URL-shortener list, and a create/put/reuse → profile write → delete-removed save order.

Current state in Nubecita: `ProfileHero` renders bio (`BioLinks.kt`, Custom Tab open) and `ProfileMetaRow` (legacy `website`). `DefaultProfileRepository.updateProfile` does read → merge managed keys onto the raw JSON → compare-and-swap `putRecord`, preserving unknown keys. Edit Profile is a `verticalScroll` `Column` seeded synchronously from route args. `:core:moderation` `ContentModerator` decides the four global media labels. `sh.calvin.reorderable` 3.1.0 is already used by `ManageFeedsScreen`.

Full design narrative: `docs/superpowers/specs/2026-10-23-profile-links-design.md`.

## Goals / Non-Goals

**Goals:**
- Render links on every profile at parity with the official app (pills under bio, support emphasis, overflow).
- Add / edit / remove links with favicons in Edit Profile.
- Reorder links by drag, accessibly.

**Non-Goals:**
- Bundled support-provider logos (`nubecita-t158`), Safelink check (`nubecita-zjwi`), Report link (`nubecita-6yej`).
- Changes to the legacy `website` field.
- Extending `:core:moderation` label coverage.

## Decisions

### 1. Overflow: 4 pills + "+N", no measurement
Render the first four links, then "+N" when more exist. **Alternative**: the official app measures pills and folds to exactly two rows (`SubcomposeLayout`-style). Rejected — measurement adds a layout pass on a 120 Hz-critical screen and makes screenshots width-dependent; the cost is an occasional third row on narrow phones with long titles.

### 2. "+N" opens a `ModalBottomSheet`
Lists all links as full-width rows. Mirrors `VerificationSheet`: stateless `*SheetContent` carries the screenshot baselines; the sheet wrapper doesn't. **Alternative**: expand the row inline — rejected, it pushes the tabs down and fights the hero's fixed-height layout.

### 3. New `NubecitaLinkChip` in `:designsystem`
Standard (outlined) + emphasized (filled) variants. **Alternative**: stock M3 `AssistChip` per call site — rejected; three call sites (row, overflow, editor) and a custom emphasized style justify one component with its own fixtures. Not a new module. Emphasized color avoids `tertiary` (reserved for auxiliary decoration) and must pass AA.

### 4. Support links: ported domain table, pure Kotlin
Host equals or is a subdomain of the provider domain, with optional path prefix. Lives in `:feature:profile:impl` (single consumer) with unit tests. Logos deferred; favicon or globe fills the icon slot.

### 5. Moderation: reuse `ContentModerator`, hide on WARN or HIDE
Exactly the coverage posts get. A blurred pill has no sensible UI, so WARN hides (matches official). **Alternative**: add `!hide`/labeler support — cross-cutting, out of scope.

### 6. Editor reads link records from the PDS, with sealed `LinksLoadStatus`
Profile views carry icon URLs, not blob refs, and re-saving needs the blob ref. Load `profile/self` → refs → each link record in parallel; skip `RecordNotFound`. `Loading`/`Failed` disable link editing; Save stays available and treats links as **unchanged** — critical, so an empty unloaded list can never be saved as "delete everything". Links' dirty baseline is set on `Loaded`.

### 7. Favicon: fetch at confirm, upload at Save
`GET cardyb.bsky.app/v1/favicon?url=…&fresh=true`, 8 s timeout, accept only 2xx + `image/png` + ≤ 100 KB; bytes kept in the draft (also the preview, since the CDN can't serve an unreferenced blob). **Alternative**: upload at confirm like the official app — rejected; the PDS garbage-collects unreferenced blobs, so a long-open draft could reference a collected blob, and discarded drafts would leak uploads. Upload failure at Save drops the icon, never the save. Bench: no network. Fetcher location (next to `CardyBExternalLinkMetadataRepository` in `:core:posting` vs. `:feature:profile:impl` data layer) is the implementer's call.

### 8. Save order: link records → profile CAS write → delete removed
Extend `updateProfile` with a links change type mirroring `ImageChange` (`Unchanged` | `Replaced(list)`). For `Replaced`: upload pending icons; per link reuse / `putRecord` same rkey / `createRecord`; merge `links` (remove key when empty) into the existing merged JSON and CAS-write; then delete unreferenced originals, swallowing failures. **Alternative**: `applyWrites` for atomicity — rejected, strongRefs need the new records' CIDs, which aren't known until written. Orphans from a failed profile write are invisible and accepted.

### 9. Reorder with `ReorderableColumn`
Edit Profile isn't lazy and holds ≤ 10 links, so the library's non-lazy `ReorderableColumn`. Draft-only; save writes the profile only. Move up / move down custom accessibility actions; haptics as in `ManageFeedsScreen`.

## Risks / Trade-offs

- [Third pill row on narrow phones] → accepted trade for deterministic layout; covered by a phone-width screenshot fixture with long titles.
- [Unloaded list saved as empty → mass delete] → `LinksLoadStatus` gate + `Unchanged` default; dedicated unit test.
- [Orphaned link records / blobs after failed save] → invisible to everyone; matches official behavior.
- [CardyB outage or slow site] → 8 s timeout, link still added without icon.
- [Interface change misses the bench implementor] → compile `benchDebug` locally; grep all `ProfileRepository` implementors.
- [Look-alike support domains] → strict host/subdomain match with unit tests for spoof cases.
- [Custom title disguises destination] → host in accessibility semantics + long-press full URL; Safelink deferred.

## Migration Plan

No data migration. Ships as one `gh stack` (`nubecita-plp4.1` render → `.2` edit → `.3` reorder) merged atomically; one minor release. Rollback = revert the squash commits; link records written by Nubecita remain valid for other clients.

## Open Questions

None blocking.
