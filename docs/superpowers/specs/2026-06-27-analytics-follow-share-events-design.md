# Analytics: follow + share events (nubecita-049f.8)

**Status:** Approved design — ready for implementation plan.
**Tracks:** nubecita-049f.8 (epic nubecita-049f — App analytics v1).
**Supersedes for this scope:** the "Later (v2+)" placeholders `follow_account` and `share (sanitized)` in `docs/superpowers/specs/2026-05-30-app-analytics-design.md` (§ event roadmap).

## Problem

After nubecita-049f.6 (PR #614) wired `interact_post` for like/repost across Feed/PostDetail/Profile, three post-interaction analytics gaps remained. Data from GA4 (property 534903647, last 30 days) sized them:

- **Follow / unfollow** — no event type exists; `ProfileViewModel.launchFollow/launchUnfollow` fire nothing. **0% coverage**, despite ~425 profile screen-views/month (the primary follow surface).
- **Share / copy-link** — every `SharePost` / `CopyPermalink` effect (Feed/PostDetail/Profile) carries no analytics. **0% coverage** on high-traffic surfaces (feed ~948, post_detail ~431, profile ~425 views/month).
- **Granular reply/quote** — captured today only as booleans on the low-volume `create_post` (~24/month). **Out of scope here** (low value; revisit later).

Follow and share are the two true blind spots. This change adds PII-free events for both.

## Locked constraints (from the v1 analytics design)

These are inherited, not re-litigated:

1. **No raw-string escape hatch.** `AnalyticsClient` exposes only `log(event: AnalyticsEvent)`; every event is a sealed `AnalyticsEvent` whose `params` are built from typed enum/boolean fields.
2. **PII NEVER sent** (locked decision #4): no DIDs, AT-URIs, handles, follower/blocked identities, free text. Identifying values are replaced by enums/booleans/buckets.
3. **`:core:analytics` is dependency-free** — no `:data:models`. New enums are defined locally in `AnalyticsEvent.kt`; the call site maps domain types onto them.

### Rejected: an external reviewer's `item_id` / `target_user_id` params

A reviewer proposed GA4's `share` event with `item_id` (post id) and an `interact_actor` event with `target_user_id` (actor id). **Both are rejected**: `item_id` is a post AT-URI (embeds a DID) and `target_user_id` is an actor DID — exactly the PII locked decision #4 forbids, and they would fail the No-PII guard test. They are also unimplementable without adding a free-form string field, which the typed API forbids by design. This mirrors the existing precedent where `search`→`search_perform` (drops `search_term`) and `feed_uri`→`feed_type` (drops the URI). Per-item share attribution is intentionally not collected; high-cardinality URIs would sample into GA4's "(other)" bucket and be unusable anyway. The reviewer's *structure* (a generic actor event + the GA4 `share` event name) is adopted; only the two ID params are dropped.

## Design

Two new sealed `AnalyticsEvent` data classes in `core/analytics/.../AnalyticsEvent.kt`, plus two local enums. Surfaces reuse the existing `PostSurface` enum (`feed | post_detail | profile | search`).

### Event 1 — `interact_actor`

User-to-user interaction. Generic verb (mirrors `interact_post`) so future mute/block fold in as `action_type` values rather than new events.

```kotlin
enum class ActorAction(val wire: String) {
    Follow("follow"),
    Unfollow("unfollow"),
    // Future (moderation analytics): Mute/Unmute/Block/Unblock.
}

data class InteractActor(
    val action: ActorAction,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name = "interact_actor"
    override val params = mapOf(
        "action_type" to Str(action.wire),       // matches InteractPost's param name
        "source_surface" to Str(surface.wire),
    )
}
```

### Event 2 — `share`

GA4 **recommended** event name (not reserved; gets built-in GA4 share reporting). PII-free: no `item_id`.

```kotlin
enum class ShareMethod(val wire: String) {
    ShareSheet("share_sheet"),   // system share sheet
    CopyLink("copy_link"),       // copy-permalink
}

// Named `Share` (not `SharePost`) to match the wire name `share` and avoid
// shadowing the existing `FeedEffect.SharePost` / `ProfileEffect.SharePost` UI effects.
data class Share(
    val method: ShareMethod,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name = "share"
    override val params = mapOf(
        "method" to Str(method.wire),
        "content_type" to Str("post"),           // GA4-standard; constant today, future-proofs sharing profiles/feeds
        "source_surface" to Str(surface.wire),
    )
}
```

### Call sites — fire-and-forget on tap (mirrors `InteractPost`)

`InteractPost` fires optimistically at the tap, before the network call, with direction read from pre-tap state (`ProfileViewModel:176-204`). The new events follow the same pattern:

| Event | Where | Trigger |
|---|---|---|
| `interact_actor(follow/unfollow, Profile)` | `ProfileViewModel` follow toggle (`launchFollow`/`launchUnfollow` decision point, ~`:495-541`) | on tap; direction from pre-tap `ViewerRelationship` |
| `share(share_sheet, surface)` | `OnShareClicked` → `SharePost` effect — Profile + PostDetail VMs, and the Feed `PostCard.onShare` path | on tap |
| `share(copy_link, surface)` | `OnShareLongPressed` → `CopyPermalink` effect — Profile + PostDetail VMs | on tap |

Follow is **Profile-only today**; `source_surface` is still included for consistency with `interact_post` and forward-room as feed/search follow affordances ship (it will read `profile` until then). Share genuinely varies across feed/post_detail/profile.

## Scope

**In:** `interact_actor` (follow/unfollow), `share` (share_sheet + copy_link), wired at all current call sites; model + validator + per-VM unit tests; GA4 custom-dimension registration for the new params.

**Out:** mute/block (add `ActorAction` values when moderation analytics ship); granular reply/quote on `interact_post` (low volume); per-item share attribution (PII).

## GA4 custom dimensions

`action_type`, `source_surface`, and `method` are **already registered** (049f.10 — `action_type`/`source_surface` shared with `interact_post`; `method` shared with login). **`content_type`** is the one **new** param needing registration. Forward-only, so register when this ships: add a `("content_type", "Content Type")` row to the local `~/.config/nubecita/analytics/register_ga4_dimensions.py` and run `--apply`. (`share`/`interact_actor` raw event counts work immediately without registration.)

## Testing

- **Model** (`AnalyticsModelTest`): `interact_actor` and `share` produce the expected `name` + `params` maps; add to the **No-PII guard test** (all param values are enum-derived `Str`/`Bool`, no free text).
- **Validator** (`AnalyticsValidatorTest`): both events + all param names pass; add to the "all events pass validation" list.
- **Per-VM** (`ProfileViewModel`, `PostDetailViewModel`, and Feed share path): using the existing `RecordingAnalyticsClient` fakes, assert the correct event/enum fires on the follow toggle (both directions), share-click, and long-press-copy. Direction (follow vs unfollow) is asserted from pre-tap state.
- No screenshot/instrumented changes (analytics is non-visual).

## Risks / notes

- `interact_actor`'s `source_surface` is constant (`profile`) until non-Profile follow affordances exist — accepted for cross-event consistency.
- The `AnalyticsValidator` checks param **names**, not values — it is not the PII backstop. The typed API + the No-PII guard test are. (Relevant because `item_id`/`target_user_id` would have passed the name regex.)
- `RecordingAnalyticsClient` is duplicated across modules (nubecita-049f.7). This change adds assertions in modules that already have a copy; it does **not** consolidate — that's 049f.7's job and is kept separate.
