## Context

Bluesky's verification lives in `app.bsky.actor.defs#verificationState`, present on `profileViewBasic` (feed post authors) and `profileViewDetailed` (profile screen). Our pinned `atproto-models 9.7.3` already generates `ProfileViewBasic.verification: VerificationState?` with `verifiedStatus`, `trustedVerifierStatus` (each `"valid" | "invalid" | "none"`), and `verifications: List<VerificationView { uri, issuer(DID), isValid, createdAt }>`. Nothing in our UI pipeline carries this yet: `AuthorUi` has only did/handle/displayName/avatarUrl. The change threads verification from the wire through `:data:models`, the two mappers, and two render surfaces, plus a profile-only explanation sheet. No SDK bump.

## Goals / Non-Goals

**Goals:**
- Surface both verification tiers (Verified, Trusted Verifier) in feed and profile with a single reusable badge atom.
- On profile, let users tap the badge to learn what it means and who issued it.
- Keep the badge a pure presentational atom in `:designsystem`; keep the stateful sheet feature-local.
- Add no network cost to normal feed/profile rendering.

**Non-Goals:**
- Verifying other accounts (issuing verifications).
- A settings toggle to hide badges.
- Badges on non-post/non-profile surfaces (search rows, mention chips, notifications) — V2.
- Any moderation/label coupling.

## Decisions

### D1 — Model verification as a small enum + optional detail object, in `:data:models`
`enum VerifiedBadge { None, Verified, TrustedVerifier }` is the render contract. `AuthorUi` gains `verifiedBadge: VerifiedBadge = None` (feed needs only the glyph). The profile detail model gains a richer `VerificationUi(badge, verifiers: ImmutableList<VerifierUi>)`, where `VerifierUi(did, handle, displayName?, verifiedAt)` feeds the sheet. `VerifierUi` represents a **resolved** verifier — `handle`/`displayName` come from the issuer-DID resolution (D5), so the list is populated only *after* resolution, not by the mapper. Invalid verifications are dropped by the mapper (no `isValid` field needed on the UI type), and a DID that fails to resolve is skipped rather than shown as a bare DID.
- *Why:* the feed path stays cheap (one enum), while the profile path carries the list the sheet needs. Both derive from the same mapping rule, so there is one source of truth for precedence.
- *Alternative rejected:* two booleans (`isVerified`, `isTrustedVerifier`) — pushes precedence logic into every call site; an enum makes "exactly one badge" unrepresentable-if-invalid and matches the render decision directly.

### D2 — Badge derivation rule lives in the mappers, computed once
`trustedVerifierStatus == "valid"` → `TrustedVerifier`; else `verifiedStatus == "valid"` → `Verified`; else `None`. Applied in `:core:feed-mapping` (author mapper) and `:feature:profile:impl/AuthorProfileMapper`. Status is compared as an exact string against `"valid"` (the SDK models these open `knownValues` enums as `String`), so any unknown future value degrades safely to `None`.
- *Why:* one tiny pure function, unit-testable, identical on both surfaces.

### D3 — Reusable `VerificationBadge` atom in `:designsystem`, sheet in `:feature:profile:impl`
`:designsystem` already depends on `:data:models` and hosts `PostCard`, so the stateless `VerificationBadge(badge, size, ...)` atom belongs there and is consumed by both `PostCard` (feed) and `ProfileHero` (feature). The tap-to-explain sheet is stateful (loads verifiers, error/loading), VM-wired, and carries feature strings, so it stays in `:feature:profile:impl`. It is NOT a `:core:*-ui` concern (`:core:post-interactions-ui` is for interaction wiring, not presentation).
- *Alternative rejected:* putting the atom in `:core:post-interactions-ui` or a new core-ui module — a badge has no interaction/domain wiring; it's a design-system atom.

### D4 — Two Material Symbols glyphs, single-glyph font append
Add `check_circle` (Verified) and `verified` (Trusted Verifier rosette) to the design-system icon font via the established careful single-glyph append — never a full-font regen (past regressions). Tint verified-blue via a design-system token, not the theme accent, so the badge reads as a platform signal.

### D5 — Lazy issuer resolution on sheet-open via batched `getProfiles`
The response gives only issuer DIDs; names require `app.bsky.actor.getProfiles` (`GetProfilesRequest`, batch ≤25). Resolve **only when the sheet opens**, not on every profile load, so the common case (viewing a profile, never tapping) adds zero network. The profile VM exposes a `loadVerifiers` action with explicit loading/error state; the sheet renders explanatory copy immediately and swaps a spinner → list (or an unavailable message) as resolution completes.
- *Alternative rejected:* resolving issuers eagerly during profile load — adds a network round-trip to every profile view for a panel most users never open.

### D6 — MVI wiring for the sheet
Badge tap → a `Loginless` UI event on the profile VM that (a) flips sheet-visible state and (b) triggers `loadVerifiers`. Follows the existing per-screen sealed `UiEvent`/`UiState` shape; the sheet reads a flat sub-state (`verifiersLoading`, `verifiers`, `verifiersError`). No new event bus.

### D7 — Fixtures + tests per layer
`:data:models` ships `VerifiedBadge`/`VerificationUi` fixtures for each state. Mapper unit tests cover the derivation rule and verifier-list building. Profile VM tests cover lazy resolution (success/empty/error) — bound network timeouts at the Ktor `HttpTimeout` boundary, never `withTimeout` on the injected dispatcher (known test-hang footgun). Screenshot baselines cover `PostCard` (both tiers), `ProfileHero` badge, and the sheet (loading + loaded). The compose gate runs `compose-expert` since new `@Composable`s are added.

## Risks / Trade-offs

- **AppView-scoped truth** → the sheet copy frames verification as "verified on Bluesky," and absence renders no badge (never a negative claim); documented in the spec so a self-hosted/other-AppView user isn't mislabeled.
- **Open `knownValues` enum drift** (Bluesky adds a new status) → exact `"valid"` match degrades unknowns to `None`; no crash, just no badge until we add handling.
- **Extra tap-time latency for issuer names** → mitigated by showing copy instantly and resolving in the background; failure is non-blocking.
- **Icon-font regression risk** → mitigated by the single-glyph append discipline and screenshot baselines for any glyph drift.
- **Screenshot churn** → adding a badge shifts the display-name row; expect to regenerate `PostCard`/`ProfileHero` baselines (feature-module baselines are CI-rendered; use the `update-baselines` label if local/CI diverge).

## Open Questions

- Exact verified-blue token value and badge sizes (feed vs hero) — resolve against the design-system palette during `.1`/`.2`; not blocking.
- Date formatting in the sheet (relative vs absolute "May 2025") — pick during `.4` using existing time utils.
