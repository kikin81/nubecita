## ADDED Requirements

### Requirement: Derive a verification badge from the atproto verification state

The system SHALL derive a single `VerifiedBadge` value (`None`, `Verified`, or `TrustedVerifier`) from an account's atproto `VerificationState`. A `TrustedVerifier` badge takes precedence over a `Verified` badge. Only a status value of exactly `"valid"` produces a badge; `"none"`, `"invalid"`, absent, or any unrecognized value produces `None`.

#### Scenario: Trusted verifier takes precedence
- **WHEN** an account's `verification.trustedVerifierStatus` is `"valid"`
- **THEN** its derived badge is `TrustedVerifier`, regardless of `verifiedStatus`

#### Scenario: Verified account
- **WHEN** `trustedVerifierStatus` is not `"valid"` and `verifiedStatus` is `"valid"`
- **THEN** its derived badge is `Verified`

#### Scenario: No badge for non-valid or missing status
- **WHEN** an account has no `verification` object, or both statuses are `"none"`, `"invalid"`, or an unrecognized value
- **THEN** its derived badge is `None` and no badge is rendered

### Requirement: Show a verification badge for feed post authors

The system SHALL render a small, non-interactive verification badge next to the post author's display name wherever a post card appears (home feed, custom feeds, thread clusters, and post detail). The badge glyph reflects the derived `VerifiedBadge` and is tinted a consistent verified-blue distinct from the theme accent.

#### Scenario: Verified author in the feed
- **WHEN** a post's author has a derived badge of `Verified` or `TrustedVerifier`
- **THEN** the matching badge glyph appears immediately after the author's display name in the post card

#### Scenario: Unverified author shows no badge
- **WHEN** a post's author has a derived badge of `None`
- **THEN** no badge glyph is rendered and the display-name row layout is unchanged

#### Scenario: Feed badge is not tappable
- **WHEN** the user taps the badge on a feed post card
- **THEN** the tap is treated as a tap on the post card (no verification sheet opens in the feed)

### Requirement: Show a tappable verification badge on the profile header

The system SHALL render a larger verification badge next to the display name in the profile header, reflecting the account's derived `VerifiedBadge`. When a badge is present, tapping it SHALL open a verification explanation sheet.

#### Scenario: Verified profile header
- **WHEN** the viewed profile has a derived badge of `Verified` or `TrustedVerifier`
- **THEN** the matching badge appears next to the display name in the profile header

#### Scenario: Tapping the profile badge opens the sheet
- **WHEN** the user taps a present profile-header badge
- **THEN** a modal bottom sheet explaining the verification opens

#### Scenario: No badge, no affordance
- **WHEN** the viewed profile has a derived badge of `None`
- **THEN** no badge is shown and there is no tap target

### Requirement: Explain verification in a bottom sheet naming the verifiers

The verification sheet SHALL present client-authored explanatory copy describing what the badge means (distinct copy for `Verified` vs `TrustedVerifier`), and SHALL list the accounts that verified this account together with the verification date. Because the protocol response contains no explanatory text and only issuer DIDs, the system SHALL resolve issuer DIDs to display names/handles via a batched profile lookup, and SHALL perform that lookup lazily when the sheet opens (not on every profile view). Only verifications with `isValid == true` are listed.

#### Scenario: Sheet lists resolved verifiers
- **WHEN** the sheet opens for an account with one or more valid verifications
- **THEN** the sheet resolves the issuer DIDs and lists each verifier's name/handle and verification date beneath the explanatory copy

#### Scenario: Loading state while resolving issuers
- **WHEN** the issuer profile lookup is in flight
- **THEN** the sheet shows the explanatory copy immediately and a loading indicator in place of the verifier list

#### Scenario: Issuer resolution fails
- **WHEN** the issuer profile lookup fails
- **THEN** the sheet still shows the explanatory copy and a non-blocking message that verifier details are unavailable, without crashing

#### Scenario: Distinct copy per tier
- **WHEN** the badge is `TrustedVerifier`
- **THEN** the sheet's title and explanation describe a trusted verifier, not a plain verified account

### Requirement: Accessibility of verification badges

Every rendered verification badge SHALL expose an accessible description (e.g., "Verified account" or "Trusted verifier") to screen readers. The tappable profile-header badge SHALL additionally expose its action (open verification details) to assistive technologies.

#### Scenario: Screen reader announces the badge
- **WHEN** a screen reader focuses a verification badge
- **THEN** it announces the badge's meaning ("Verified account" or "Trusted verifier")

#### Scenario: Localized strings
- **WHEN** any user-facing verification string is added (badge descriptions, sheet copy)
- **THEN** it is provided in English, `values-b+es+419`, and `values-pt-rBR`

### Requirement: Verification data is advisory and independent

The system SHALL treat absence of verification data as "no badge," not as a claim that an account is unverified, because verification reflects the connected AppView's opinion and may be unpopulated via other AppViews. Verification SHALL remain independent of moderation labels and never gate or alter moderation behavior.

#### Scenario: Missing verification is not "unverified"
- **WHEN** a profile or author arrives with no `verification` field
- **THEN** the system renders no badge and makes no negative authenticity claim

#### Scenario: Verification does not affect moderation
- **WHEN** an account carries both a verification badge and moderation labels
- **THEN** the badge display and the label/moderation behavior are computed independently
