## ADDED Requirements

### Requirement: Wear bundle ships on its own form-factor tracks
The release pipeline SHALL build the `:wear:app` bundle with a version-code scheme independent of the phone's, unique across form factors and monotonic. It SHALL upload the bundle to the dedicated Wear OS form-factor tracks, whose names are verified against the Play Developer API. A Wear build SHALL have a closed-testing release before the Wear OS opt-in. Promotion SHALL keep the Wear bundle out of production until feed browsing (W2) ships.

#### Scenario: Release before W2
- **WHEN** a release runs before the W2 epic has landed
- **THEN** the Wear bundle is uploaded to the Wear internal or closed track only, and promotion to production skips it

#### Scenario: Version codes don't collide
- **WHEN** phone and Wear bundles are built from the same semantic version
- **THEN** their version codes differ, and each is greater than every earlier code on its form factor
