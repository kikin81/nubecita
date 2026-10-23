## Why

Bluesky shipped **profile links** on 2026-10-22: up to 10 titled links shown as pills under the bio (website, newsletter, other socials, payment pages), with payment platforms emphasized in green. Today a Nubecita user sees none of them on anyone's profile and can't manage their own without switching to the official app. The `atproto-kotlin` 9.13.0 codegen already carries the new `app.bsky.actor.link` record, `profile.links` strongRef list, and `ProfileViewDetailed.links` view, so this is purely client work.

bd epic: `nubecita-plp4`. Source design: `docs/superpowers/specs/2026-10-23-profile-links-design.md`.

## What Changes

- Render a profile's links under the bio on every profile: a `FlowRow` of at most **4 pills + a "+N" overflow pill** (deterministic, no width measurement); "+N" opens a bottom sheet with all links. Support platforms (Patreon, Ko-fi, …) render as an emphasized pill. Links hidden by the viewer's moderation prefs are dropped. Tap opens a Custom Tab; long-press reveals the full URL.
- New `:designsystem` component **`NubecitaLinkChip`** (standard + emphasized variants).
- Edit Profile gains a **Links** section: add / edit / remove links (URL normalization, ≤ 40-grapheme title, non-blocking http + URL-shortener warnings, duplicate rejection), with the site's favicon fetched via CardyB and uploaded on Save.
- Save writes link records (create / put / reuse) before the existing compare-and-swap profile write, then deletes removed records. An unloaded or failed link list never writes anything link-related.
- Drag-and-drop **reordering** of links in Edit Profile (`sh.calvin.reorderable`), with accessible move up / move down actions.

## Non-goals

- Bundled brand logos for support providers (`nubecita-t158`) — support links show their favicon until then.
- Safelink check at add time (`nubecita-zjwi`).
- "Report link" dialog (`nubecita-6yej`).
- Any change to the legacy `website` field / `ProfileMetaRow`.
- Broadening `:core:moderation` label coverage — links get exactly the coverage posts get.

## Capabilities

### New Capabilities

- `profile-links`: reading, rendering, editing, saving, and reordering `app.bsky.actor.link` records on the profile and in Edit Profile.

### Modified Capabilities

- `design-system`: adds the `NubecitaLinkChip` component requirement.

## Impact

- **Modules**: `:designsystem` (new component + screenshot fixtures); `:feature:profile:impl` (mapper, `ProfileHeaderUi`, hero row, overflow sheet, Edit Profile VM/contract/screen, `ProfileRepository` + `DefaultProfileRepository`, bench `ProfileRepository` + fixtures, strings es/pt).
- **APIs**: `com.atproto.repo.{getRecord, createRecord, putRecord, deleteRecord, uploadBlob}` on `app.bsky.actor.link` / `app.bsky.actor.profile`; new outbound call to `cardyb.bsky.app/v1/favicon`.
- **Interface change**: `ProfileRepository.updateProfile` gains a links parameter and a new own-links read — every implementor (production + bench) must be updated.
- **Baseline deviations**: none from MVI / Compose / Hilt / Room / Coil. Editor link load status is a per-screen sealed `LinksLoadStatus` per the MVI sealed-status rule. Favicon upload timing deliberately differs from the official app (upload at Save, not at confirm).
- **Delivery**: one `gh stack` of three `feat(profile)` PRs (`nubecita-plp4.1` → `.2` → `.3`); one minor release.
