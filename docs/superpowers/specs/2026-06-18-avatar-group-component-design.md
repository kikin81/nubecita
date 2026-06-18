# AvatarGroup design-system component + NubecitaAvatar fallback consolidation

**Bead:** nubecita-364e
**Date:** 2026-06-18
**Status:** design approved, pending implementation plan

## Problem

Bluesky added group chats. Group conversations have multiple members and no single
"other user," but Nubecita has no way to render a set of people as one compact unit.
The group-chat row and thread header (a separate effort) need a members facepile.

Separately, the hue+initial avatar fallback — a colored disc with the person's initial,
shown when someone has no avatar image — is copy-pasted across ~4 call sites, and a
near-duplicate facepile (`StackedAvatarRow`) already lives in `:feature:notifications:impl`.
`NubecitaAvatar` itself only renders a flat grey placeholder.

This change introduces one canonical `AvatarGroup` in `:designsystem`, makes
`NubecitaAvatar` the single owner of avatar rendering (photo **and** fallback), and
consolidates the existing duplication onto it.

Out of scope: wiring `AvatarGroup` into the group-chat row/header and the group-chat
view/send feature itself. This change delivers the component + the consolidation only;
the group-chat feature consumes it later.

## Decisions

1. **One canonical facepile.** `AvatarGroup` lives in `:designsystem`. The existing
   `StackedAvatarRow` (notifications) is deleted and replaced by it.
2. **Takes `ImmutableList<AuthorUi>`.** `:designsystem` already depends on `:data:models`
   and uses `AuthorUi` (e.g. `PostCard`), so the component accepts the domain UI model
   directly — no caller-side mapping boilerplate.
3. **Overflow = up to 4 avatars + a "+N" pill** (Atlassian-style). The avatar cap is a
   `maxVisible` param defaulting to 4; when `members > maxVisible`, a `+(size − maxVisible)`
   pill is appended as an extra bubble (it does not replace an avatar slot).
4. **Avatarless members render hue disc + initial** — the established Nubecita pattern,
   so group members stay distinguishable without photos.
5. **`NubecitaAvatar` owns the fallback.** It gains a first-class optional `fallback`
   (hue + initial). `AvatarGroup`, the migrated `StackedAvatarRow` use case, and all ~4
   single-avatar call sites route through it; their copy-pasted fallback code is deleted.
6. **`avatarHueFor` moves to `:core:common`.** The pure, dependency-free `did + handle → hue`
   function is relocated from `:core:profile` (a domain/network module `:designsystem`
   must not depend on) to `:core:common`, which `:designsystem` already depends on. This
   lets `AvatarGroup` compute the full photo-or-fallback itself from `AuthorUi`.

## Component design

### Visual

```
photo  hue+B  hue+C  hue+D   pill
[IMG ]( B  )( C  )( D  )( +5 )
```

- Each bubble has a `1.5.dp` ring in `colorScheme.surface` so circles read as discrete.
- **Left-on-top z-order**: the first avatar is fully visible on the left; each subsequent
  bubble tucks behind the previous one to the right; the pill sits at the far right.
- Overlap ≈ 30% of `avatarSize`.
- Pill: `surfaceContainerHigh` background, `onSurfaceVariant` text, `labelSmall`, same ring.

### Public API (`:designsystem`)

```kotlin
@Composable
fun AvatarGroup(
    avatars: ImmutableList<AuthorUi>,
    contentDescription: String?,   // caller-supplied; applied via merged semantics
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    avatarSize: Dp = DEFAULT_AVATAR_SIZE,
)
```

- Renders `min(maxVisible, avatars.size)` avatars, each photo-or-(hue+initial).
- When `avatars.size > maxVisible`, appends a `+(avatars.size − maxVisible)` pill.
- `contentDescription` is supplied by the caller (the design system does not own the
  domain plural strings); applied with `Modifier.semantics(mergeDescendants = true)`.
- A pure helper computes the overflow split and is unit-tested directly.

### `NubecitaAvatar` fallback

```kotlin
@Composable
fun NubecitaAvatar(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_AVATAR_SIZE,
    fallback: AvatarFallback? = null,
)

@Immutable
data class AvatarFallback(val hue: Int, val initial: Char?)
```

- `fallback != null` + null (or failed) image → hued disc `Color.hsv(hue, 0.5f, 0.55f)`
  with `initial` centered, text color chosen by `Color.luminance() > 0.5f`.
- `fallback == null` → today's flat `surfaceContainerHighest` placeholder, so existing
  callers that don't pass a fallback are unchanged.
- `AuthorUi` → `AvatarFallback` mapping lives in `:designsystem` using the relocated
  `avatarHueFor`: `hue = avatarHueFor(did, handle)`,
  `initial = (displayName.ifBlank { handle }).firstOrNull()?.uppercaseChar()`.

## Migration (this change)

- **`:core:profile` → `:core:common`:** move `avatarHueFor` + `AvatarHueTest`; re-point
  importers (~5 sites). Pure move, no behavior change; the pinned hue values (alice→47,
  bob→307, miku→279) must still hold.
- **`:feature:notifications:impl`:** delete `StackedAvatarRow`; callers use `AvatarGroup`.
  The notifications plural content-description string is passed in via `contentDescription`.
- **Single-avatar call sites** → `NubecitaAvatar(fallback = …)`, deleting their inline
  hue+initial code: `ChatTopBarAvatar`, `ConvoListItem`, `RecipientRow` (`RecipientAvatar`),
  `SettingsAvatar`.
- **Screenshots:** add the `AvatarGroup` baseline set; rebaseline notifications, chat,
  settings, and profile fixtures affected by the routing change.

## Testing

- **Screenshot tests** (`:designsystem`, `@PreviewWrapper(NubecitaComponentPreview::class)`):
  1 avatar; 2–4 avatars (no pill); overflow (`+N`); all-fallback (no photos); mixed
  photo + fallback; light + dark.
- **Unit test** for the overflow-split helper (visible-count / overflow-count math).
- **Relocated `AvatarHueTest`** continues to pin determinism after the move.
- Existing migrated-screen screenshot tests re-validate against new baselines.

## Non-goals

- Per-avatar click / interactivity (the component is presentational; the row/header owns taps).
- Group name, member management, or any group-chat data flow.
- Coil error-state styling beyond reusing the fallback for load failures.
