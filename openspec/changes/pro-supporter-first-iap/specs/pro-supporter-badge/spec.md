## ADDED Requirements

### Requirement: Self-visible Supporter badge (Tier 1)
When the signed-in user has Pro, the app SHALL render a "Supporter" badge on that user's own
profile header. The badge SHALL be self-visible only: it is rendered locally with no network
write, no AT Protocol record, and no backend, so other users do not see it in v1.

#### Scenario: Pro user sees their badge
- **WHEN** the signed-in user has Pro and views their own profile
- **THEN** a Supporter badge is shown in the profile hero

#### Scenario: Non-Pro user has no badge
- **WHEN** the signed-in user does not have Pro
- **THEN** no Supporter badge is shown

#### Scenario: Badge tracks entitlement
- **WHEN** the user's Pro entitlement is lost
- **THEN** the badge disappears on the next recomposition

### Requirement: Badge wording avoids verification connotation
The badge copy SHALL present a Nubecita supporter mark and SHALL NOT imply official Bluesky
status or use "verified"-style language.

#### Scenario: Neutral supporter wording
- **WHEN** the badge or its tooltip is displayed
- **THEN** the text conveys "Supporter" without implying Bluesky verification or endorsement
