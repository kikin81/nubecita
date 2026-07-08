## Why

Bluesky ships an on-protocol account-verification system (trusted verifiers issue verifications; accounts gain a verified or trusted-verifier status), and `atproto-models 9.7.3` — our current pin — already surfaces it on every profile and feed author. We render none of it today, so verified accounts and organizations look identical to unverified ones, and users have no signal of authenticity. The data is in hand; only the client plumbing and UI are missing.

## What Changes

- Introduce a UI model for verification status (`VerifiedBadge` enum + `VerificationUi`/`VerifierUi`) in `:data:models`, carried on `AuthorUi` (feed) and the profile detail model.
- Map the atproto `VerificationState` into that model in the feed author mapper (`:core:feed-mapping`) and the profile mapper (`:feature:profile:impl`).
- Add two Material Symbols glyphs to the design-system icon font and a reusable, stateless `VerificationBadge` atom in `:designsystem`.
- Render a small (non-interactive) badge next to the author's display name in `PostCard` (feed, thread cluster, post detail).
- Render a larger, tappable badge next to the display name in `ProfileHero`; tapping opens a `ModalBottomSheet` that explains what verification means and lists who verified the account and when.
- Resolve verifier identities: the response carries only issuer DIDs, so resolve them to names via `app.bsky.actor.getProfiles`, lazily when the sheet opens.

Two badge tiers in V1: **Verified** (`verifiedStatus == "valid"`) and **Trusted Verifier** (`trustedVerifierStatus == "valid"`), with Trusted Verifier taking precedence.

## Capabilities

### New Capabilities
- `account-verification`: how the app derives a verification badge (verified / trusted-verifier) from the atproto `VerificationState`, where the badge is displayed (feed post authors and profile headers), and the profile-only tap-to-explain sheet that names the issuing verifiers and dates.

### Modified Capabilities
<!-- None. Verification is a new, cohesive capability; the feed/design-system/profile touch points are implementation surfaces, not requirement changes to those capabilities. -->

## Impact

- **New model:** `:data:models` — `VerifiedBadge`, `VerificationUi`, `VerifierUi`, `AuthorUi.verifiedBadge`, fixtures.
- **Mapping:** `:core:feed-mapping` (author mapper), `:feature:profile:impl` (`AuthorProfileMapper`, `ProfileRepository`/VM for lazy issuer resolution via `GetProfilesRequest`).
- **Design system:** `:designsystem` — two icon-font glyphs (`check_circle`, `verified`) added via the careful single-glyph append (no full-font regen), plus the `VerificationBadge` atom; `PostCard` display-name row.
- **Profile UI:** `:feature:profile:impl` — `ProfileHero` badge + a new verification `ModalBottomSheet`, MVI-wired.
- **Dependencies:** none — `atproto-models 9.7.3` already exposes `verification`; no SDK bump.
- **i18n:** new user-facing strings require `values-b+es+419` and `values-pt-rBR` translations.
- **Screenshots:** new baselines for `PostCard`, `ProfileHero`, and the sheet.
- Tracks beads epic `nubecita-vw45` (tasks `.1`–`.4`).
