# app-review Specification

## Purpose
TBD - created by archiving change add-in-app-review. Update Purpose after archive.
## Requirements
### Requirement: Post-publish in-app review request

The system SHALL request a Google Play in-app review after a user successfully publishes a post, but only when the eligibility policy is satisfied. The request MUST be issued through the SDK-agnostic `ReviewManager.onPostPublished(activity)` boundary, and no Google Play Core type SHALL be exposed outside the `:core:review` module.

#### Scenario: Eligible user publishes a post
- **WHEN** a post publish succeeds and the eligibility policy is satisfied
- **THEN** the system increments the successful-post counter AND launches the Play in-app review flow

#### Scenario: Ineligible user publishes a post
- **WHEN** a post publish succeeds but the eligibility policy is not satisfied
- **THEN** the system increments the successful-post counter AND does NOT launch the review flow

#### Scenario: Request survives composer dismissal
- **WHEN** the review flow is launched and the composer screen is popped from the back stack immediately after publishing
- **THEN** the review flow continues to completion (it is scoped to the host Activity, not the composer screen)

### Requirement: Conservative eligibility policy

The system SHALL determine eligibility from a pure predicate over locally stored counters, the OS-provided first-install time, and the current time. A user SHALL be eligible only when ALL of the following hold: at least 3 successful posts; at least 3 days elapsed since first install; fewer than 3 lifetime review requests made; and either no prior request or at least 90 days since the last request. Elapsed-time comparisons MUST use exact durations, not calendar-day arithmetic.

#### Scenario: Below the post threshold
- **WHEN** the user has fewer than 3 successful posts
- **THEN** the user is not eligible

#### Scenario: Within the new-user window
- **WHEN** fewer than 3 days (exact duration) have elapsed since first install
- **THEN** the user is not eligible

#### Scenario: Lifetime cap reached
- **WHEN** 3 review requests have already been made
- **THEN** the user is not eligible regardless of other factors

#### Scenario: Within the cooldown window
- **WHEN** fewer than 90 days (exact duration) have elapsed since the last request
- **THEN** the user is not eligible

#### Scenario: All gates satisfied at their boundaries
- **WHEN** the user has exactly 3 posts, exactly 3 days since first install, fewer than 3 prior requests, and no request within the last 90 days
- **THEN** the user is eligible

### Requirement: Quota-aware attempt recording

The system SHALL record a review attempt at the moment the review request succeeds (before the review UI is launched), because Google Play's quota and the absence of any submission signal mean the gate must count requests made, not reviews submitted. A failure to obtain the review request SHALL NOT be recorded; a failure to launch the obtained review UI SHALL be recorded.

#### Scenario: Request obtained then launch fails
- **WHEN** the review request is obtained but launching the review UI throws
- **THEN** the attempt is recorded (request count incremented, last-requested timestamp set) and no error is surfaced

#### Scenario: Review request fails
- **WHEN** obtaining the review request throws (e.g. offline, Play Store unavailable)
- **THEN** the attempt is NOT recorded, so a later eligible publish can retry

### Requirement: Fail-silent and non-blocking behavior

The system SHALL never surface a review-related error to the user and SHALL never block the main thread. All Play and storage interactions MUST run off the main thread and swallow exceptions (debug-logged only). A failure to read eligibility state MUST be treated as not-eligible.

#### Scenario: Any review error occurs
- **WHEN** any exception is thrown while requesting, launching, or reading/writing review state
- **THEN** no user-visible error is shown and normal app flow is uninterrupted

#### Scenario: Eligibility state cannot be read
- **WHEN** reading the stored eligibility counters fails
- **THEN** the user is treated as not eligible and no review flow is launched

### Requirement: Bench-flavor inertness

The system SHALL make zero Google Play or network calls related to reviews in the bench product flavor. The bench flavor MUST bind a no-op review manager.

#### Scenario: Post published in a bench build
- **WHEN** a post is published in a bench/keyless build
- **THEN** no Play in-app review call is made and no review state is required

### Requirement: Manual rate-app entry point

The system SHALL provide a "Rate Nubecita" entry point in Settings that opens the app's Play Store listing directly, rather than invoking the in-app review API (which may render nothing once the quota is hit). The entry point MUST target the release application id and fall back from the Play Store app to the web listing when the Play Store app is unavailable.

#### Scenario: User taps Rate Nubecita with the Play Store app installed
- **WHEN** the user taps the "Rate Nubecita" row and the Play Store app is available
- **THEN** the system opens the Play Store listing via a `market://details?id=<release app id>` intent

#### Scenario: Play Store app not available
- **WHEN** the `market://` intent cannot be resolved
- **THEN** the system falls back to opening `https://play.google.com/store/apps/details?id=<release app id>`
