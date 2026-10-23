# Profile links — design

Date: 2026-10-23
Status: draft (awaiting approval)

## Intent

Bluesky shipped **profile links** on 2026-10-22
([announcement](https://bsky.social/about/blog/10-22-2026-introducing-profile-links)):
up to 10 links shown as pills directly under the bio — a website, newsletter,
other socials, payment pages — replacing "link in bio" pages. Payment platforms
(Patreon, Ko-fi, …) render as prominent green pills. Owners add, title, remove,
and drag-reorder links in Edit Profile.

Nubecita should read and write these at parity with the official app, so a
profile looks the same in Nubecita as on bsky.app and a Nubecita user can manage
their own links without switching clients. The `atproto-kotlin` 9.13.0 codegen
already carries the new schema.

The official client is open source and its implementation
(`bluesky-social/social-app` → `src/features/profileLinks/`) is the behavioral
reference for this design. Where this doc says "matches the official app", that
folder is the source.

## Protocol facts (from the lexicons)

- **`app.bsky.actor.link`** — one record per link, `tid` key. Fields:
  `url` (required, https URI), `createdAt` (required), `title` (optional,
  ≤ 40 graphemes / 320 bytes; clients fall back to the domain), `icon`
  (optional blob, png/jpeg/webp, ≤ 100 KB, "the destination site's icon,
  uploaded when the link is saved"), `labels` (self-labels).
- **`app.bsky.actor.profile.links`** — array of `com.atproto.repo.strongRef`,
  **max 10**, in display order. Each ref points to a link record in the same
  repo. The profile record decides which links show and in what order.
- **Read side** — `ProfileViewDetailed.links: List<ProfileLinkView>` arrives
  resolved and ordered: `uri`, `cid`, `url`, `title?`, `icon?` (CDN URL),
  `labels`. Taken-down / unresolvable links are already omitted by the AppView.
- The legacy `profile.website` field still exists and is independent.

Kotlin types: `io.github.kikin81.atproto.app.bsky.actor.{Link, ProfileLinkView, Profile, ProfileViewDetailed}`.

## Goals

1. **Render links on every profile** (own and others') under the bio, as
   tappable pills, with the official app's support-platform emphasis.
2. **Edit links** in Edit Profile: add (URL + optional title), edit, remove —
   including favicon fetch + upload so new links carry an icon like the
   official app's.
3. **Reorder links** in Edit Profile by drag-and-drop.

## Non-goals (deferred to follow-up bd issues)

- **Bundled brand logos** for the ~35 support providers (the official app ships
  per-provider SVGs). Asset + licensing chore. Until then a support link shows
  its uploaded favicon (or the globe fallback) inside the green pill.
- **Safelink check** (`go.bsky.app/safelink/check`) at add time.
- **"Report link"** dialog.
- Any change to the legacy `website` field — it stays in `ProfileMetaRow` as is.
- Hover preview (desktop) — N/A on Android.

## Anti-goals

- Measuring pill widths to decide what fits in two rows. The overflow rule is
  deterministic (below) — no `SubcomposeLayout`/measure-then-fold.
- A half-saved profile: the profile record must never point at a link record
  that doesn't exist (create before the profile write, delete after).
- Clobbering profile fields this feature doesn't manage. The existing
  read-merge-CAS write in `DefaultProfileRepository.updateProfile` preserves
  unknown keys; links must ride that path, not a fresh `Profile` object write.
- A per-feature hand-rolled dialog/overlay host — reuse existing patterns
  (`ModalBottomSheet` like `VerificationSheet`, M3 dialogs).

## Decisions

### D1. Profile row: 4 pills + "+N", deterministic

`FlowRow` under the bio (in `ProfileHero`, between the bio and the meta row).

- **≤ 4 links** → render all.
- **> 4 links** → render the first 4, then a "+N" overflow pill (N = total − 4).
- Long titles may still wrap to a third row on narrow phones. Accepted in
  exchange for zero measurement.
- Hidden entirely when the list (after moderation filtering) is empty.

The official app instead measures and folds to 2 rows; we deliberately diverge.

### D2. Overflow → bottom sheet with all links

Tapping "+N" opens a `ModalBottomSheet` listing **all** links (up to 10) as
full-width rows: icon, title (or domain), full URL beneath. Rows are tappable
(open link) and long-pressable (copy URL / show URL — same behavior as pills).
Follow `VerificationSheet`'s split: a stateless `*SheetContent` composable
carries the screenshot baselines, the `ModalBottomSheet` wrapper does not.

### D3. Pill content and interaction

- **Label**: `title` if present, else the URL host (strip `www.`). Pills cut the
  label at **30 characters** with an ellipsis (matches the official
  `MAX_TITLE_LENGTH`); the sheet shows the full title.
- **Leading icon**: `ProfileLinkView.icon` via `NubecitaAsyncImage`; fallback
  is the globe glyph (`NubecitaIconName.Public`, already used by
  `ProfileMetaRow` for website).
- **Tap**: open in an in-app Custom Tab — reuse the bio's existing path
  (`openBioLinkInCustomTab` in `ui/BioLinks.kt`).
- **Long-press**: reveal the full URL (the announcement: "tap and hold a link to
  preview the full URL"). Builder's call on the affordance (tooltip vs. small
  sheet) — keep it consistent between pill and sheet row.
- **Accessibility**: pill `onClickLabel` = "Open <label>", content description
  includes the host so a custom title can't disguise the destination.

### D4. Support-platform emphasis

Port the official `SUPPORT_PROVIDERS` table (domain + optional `pathPrefix`,
host matched as the domain itself or a subdomain, so `ko-fi.com.example.net`
does **not** match). A matching link renders as the **emphasized** pill
variant (filled green container — builder picks the token: brand-scheme green
if one exists, else a tertiary/custom container that passes AA for its label;
see the `outline`-fails-AA lesson). All others use the **standard** (outlined)
variant. The table is pure Kotlin data, unit-tested for the subdomain and
path-prefix rules.

### D5. Design system: new `NubecitaLinkChip`

`:designsystem` has no chip component today (`SupporterBadge`,
`VerificationBadge` are glyph badges; features use stock M3 chips directly).
Add **`NubecitaLinkChip`** in `designsystem/.../component/`:

- params: `label: String`, `icon: (@Composable () -> Unit)?`,
  `emphasized: Boolean`, `onClick`, `onLongClick`, `modifier`.
- Two visual variants (standard outlined / emphasized filled).
- Reused by the profile row, the overflow pill ("+N" = standard, no icon), and
  optionally the editor list. Not a new module.
- Component-level screenshot fixtures with
  `@PreviewWrapper(NubecitaComponentPreview::class)`.

The row/overflow logic and the support-provider table live in
`:feature:profile:impl` (single consumer), not the design system.

### D6. Moderation filtering

Run each `ProfileLinkView`'s labels through the existing `:core:moderation`
`ContentModerator` — the same decision posts use — and drop the link when it
resolves to **HIDE or WARN** (the official app hides rather than blurs, since a
blurred pill has no sensible UI). Filtering happens in the mapper/VM, so the UI
only ever sees visible links.

*Designer's call:* `ContentModerator` honors only the four global media labels
(`porn`, `sexual`, `graphic-media`, `nudity`) and ignores system (`!hide`,
`!warn`) and labeler labels. Links get **exactly the coverage posts get today** —
no `:core:moderation` changes in this epic. Taken-down links are already omitted
by the AppView. Broader label coverage is a cross-cutting moderation concern for
posts and links alike; file it as a follow-up if wanted.

### D7. Data model

`ProfileHeaderUi` gains `links: ImmutableList<ProfileLinkUi>` (default empty).
`ProfileLinkUi` (in `:feature:profile:impl` unless another module needs it) is
`@Immutable`: `uri`, `url`, `label` (resolved title-or-host), `title?`,
`iconUrl?`, `isSupportLink: Boolean`. Mapped in `AuthorProfileMapper` from
`ProfileViewDetailed.links`, moderation-filtered, order preserved. Fixtures
alongside (0, 3, 4, 5, 10 links; a support link; an icon-less link; a
max-length title).

### D8. Editor reads link records from the owner's repo

Profile views carry only icon **URLs**; re-saving a link needs its icon
**blob ref**. So the editor's initial links come from the PDS, matching the
official app: `getRecord(app.bsky.actor.profile/self)` → `links` refs →
`getRecord(app.bsky.actor.link/<rkey>)` for each (in parallel); a ref whose
record is gone (`RecordNotFound`) is skipped. The editor keeps per-link:
`uri?`, `cid?`, `url`, `title?`, `icon` (blob JSON as read, opaque — saved
links only), `pendingIconBytes?` (PNG fetched at confirm, not yet uploaded —
new/URL-changed links only), and a preview source (CDN URL for saved links;
the pending bytes otherwise).

**Load status — designer's call, data-safety critical.** Edit Profile fills its
other fields synchronously from route args; the link read is async. The editor
state gets a per-screen sealed `LinksLoadStatus` (`Loading` / `Loaded` /
`Failed`):

- **Loading** → the Links section shows a placeholder; add/edit/remove/reorder
  are disabled.
- **Failed** → the section shows an inline error with **Retry**; links stay
  non-editable.
- In both, **Save remains available for the other fields and links are sent as
  `Unchanged`** — the profile's existing `links` key is preserved verbatim by the
  merge and no link record is touched. An unloaded or failed list must **never**
  be interpreted as "the user removed everything".
- The dirty-check baseline for links is set when the load succeeds; until then
  links contribute nothing to `isDirty`.

### D9. Add / edit / remove UX

A **Links** section in Edit Profile below the bio field:

- One row per link (icon, label, URL), with a remove action and tap-to-edit.
- An **"Add link"** row, hidden at 10 links.
- Add/edit opens a dialog (or sheet — builder's call, consistent with Edit
  Profile's existing patterns) with:
  - **URL** field — normalized like the official `normalizeProfileLinkUrl`
    (`patreon.com/name` → `https://patreon.com/name`); invalid → inline error,
    confirm disabled.
  - **Title** field — optional, live grapheme counter, max 40, over-limit
    disables confirm (mirror the existing bio counter).
  - **Warnings (non-blocking)**: plain `http://` link ("not secure"), and a
    known URL shortener (port the official `SHORTENER_DOMAINS` list) ("hides
    where it goes"). The user can still confirm.
  - Duplicate URL already in the list → inline error (rows are keyed by URL in
    the official app; we key by a local id but still reject exact duplicates).
- On confirm of a **new or URL-changed** link, **fetch** the favicon (D10,
  steps 1–2) before closing; show progress on the confirm button. Favicon
  failure never blocks — the link is added without an icon. Editing only the
  title keeps the existing icon blob.
- All of this edits the **draft**: nothing is written until Save; links count
  toward `isDirty` and the existing discard-changes dialog.

### D10. Favicon fetch + upload

Fetch rules match the official `useUploadLinkIcon`; **upload timing
deliberately differs** (designer's call):

1. **At dialog confirm:** `GET https://cardyb.bsky.app/v1/favicon?url=<encoded>&fresh=true`,
   no cache, **8 s** timeout.
2. Accept only HTTP 2xx with `content-type: image/png` and a body ≤ the
   lexicon's **100 KB** cap (no re-encoding); anything else → no icon. Keep the
   bytes in the draft as `pendingIconBytes` (they also drive the preview — the
   CDN can't serve a blob until a saved record references it).
3. **At Save** (D11 step 0): upload pending bytes via
   `com.atproto.repo.uploadBlob` (reuse the image upload pattern in
   `DefaultProfileRepository`).

Why not upload at confirm like the official app: the PDS garbage-collects blobs
no record references, so a draft left open before Save could point a record at
a collected blob, and every discarded draft would leave uploads behind.
Uploading inside Save keeps blob → record → profile in one sequence. An icon
upload failure at Save drops that icon (link saved without one) rather than
failing the save.

Network-free in the **bench** flavor (returns no icon, or a bundled fixture).
CardyB is already a dependency of `:core:posting` (`CardyBExternalLinkMetadataRepository`);
**builder's call** whether the favicon fetch lives next to it behind a new
interface or in `:feature:profile:impl`'s data layer — the latter is fine for a
single consumer.

### D11. Save sequence

Extends `ProfileRepository.updateProfile` with a `links` parameter (or a links
change type mirroring `ImageChange` — e.g. `Unchanged` vs. `Replaced(list)`,
so a profile edit that doesn't touch links writes nothing link-related).
When links changed, before the existing profile write:

0. Upload every `pendingIconBytes` (D10 step 3) → blob refs.
1. For each draft link, in order:
   - **Unchanged** (has `uri`+`cid`, same url/title/icon blob CID as loaded) →
     reuse its strongRef.
   - **Edited** (has `uri`) → `putRecord` at the same rkey → new strongRef.
   - **New** → `createRecord` → new strongRef.
   (Parallel is fine; order is preserved by position.)
2. Merge `links = [strongRefs]` into the profile JSON alongside the other
   managed keys; **empty list → remove the key**. Write with the existing
   compare-and-swap `putRecord`.
3. After the profile write succeeds, `deleteRecord` every originally-loaded link
   whose `uri` is not in the new refs. A delete failure is logged and swallowed —
   an unreferenced record is invisible.

If step 2 fails (incl. `InvalidSwap`), records created in step 1 are orphaned;
accepted (invisible, same as the official app). Errors map into the existing
`ProfileUpdateError` taxonomy. `ownProfileUpdates` still fires so the profile
screen refetches and shows the new links.

### D12. Reorder

Drag handles on the editor's link rows using `sh.calvin.reorderable` 3.1.0
(already used by `ManageFeedsScreen` in `:feature:feeds:impl`). Edit Profile is a
`verticalScroll` `Column`, not a `LazyColumn`, so use the library's
`ReorderableColumn` (≤ 10 items — no laziness needed). Reorder mutates draft
order only and marks the draft dirty; Save writes the new ref order (reordering
alone produces no link-record writes — just the profile write). Provide
accessible reordering (move up / move down custom actions), since drag is not
TalkBack-reachable. Haptic feedback on pick-up/drop as in `ManageFeedsScreen`.

## Delivery: one bd epic, one `gh stack`

Per `CLAUDE.md` → "Epics: stacked PRs". The openspec docs PR (this design →
`openspec/changes/add-profile-links/`, plus the bd epic/children) lands on
`main` **on its own** first, as the workflow requires; then the feature stack:

| # | bd child | Branch / PR title | Scope |
|---|---|---|---|
| 1 | render | `feat(profile): render profile links under the bio` | D1–D7: `NubecitaLinkChip`, `ProfileLinkUi` + mapper + moderation filter, support-provider table, row + "+N" sheet, bench fixture links |
| 2 | edit | `feat(profile): add, edit and remove profile links` | D8–D11: repo read of link records, favicon fetch/upload, editor section + dialog + validation/warnings, save sequence |
| 3 | reorder | `feat(profile): reorder profile links by drag` | D12 |

Each child blocks the next. One `feat:` title → one minor release for the epic.

## Constraints and conventions that apply

- `:feature:profile:impl` **is flavored** → use `lintProductionDebug`,
  `testProductionDebugUnitTest`, `updateProductionDebugScreenshotTest`. Its bench
  `ProfileRepository` implementation must implement every new/changed method
  and the bench profile fixtures should carry links (≥ 5, incl. a support link)
  so the row, "+N", and editor are smoke-testable offline. Compile the bench
  variant locally.
- `:designsystem` is unflavored (`updateDebugScreenshotTest`).
- MVI: flat `UiState`; link list is `ImmutableList`; navigation/link-open/error
  via effects; editor text fields follow whatever Edit Profile does today.
- New strings ship with **es-419 and pt-BR** translations in the same commit;
  run the module's own lint.
- Screenshot baselines: keep only images the change altered (macOS 1/255
  noise). New fixtures: profile hero with 0 / 3 / 5 links + a support link,
  overflow sheet content, editor links section (empty, partial, full = 10),
  add-link dialog (valid, invalid, http warning, shortener warning, over-limit
  title), `NubecitaLinkChip` variants.
- Unit tests: support-provider matching, shortener detection, URL
  normalization, overflow split (4 + N), mapper (order, title fallback,
  moderation filter), editor reducer (add/edit/remove/reorder/dirty/limit),
  save planner (unchanged/edited/new/removed classification), favicon
  acceptance rules.
- Compose-expert review against the cumulative stack diff before
  `gh stack merge`; device pass on the bench flavor.

## Left to the builder

- Exact pill/sheet spacing, the emphasized-variant color token (must pass AA),
  long-press affordance.
- Dialog vs. sheet for add/edit.
- Where the favicon fetcher lives (D10) and the exact `updateProfile` signature
  shape (D11).
