## ADDED Requirements

### Requirement: Standalone paywall module
The paywall SHALL live in its own `:feature:paywall` module (api/impl) with a `@MainShell`
`PaywallRoute` `NavKey`, so the upsell can be triggered from any surface (PiP pop-out, Settings,
and future surfaces) without depending on `:feature:settings`.

#### Scenario: Triggered from multiple entry points
- **WHEN** a non-Pro user taps the PiP pop-out affordance, or opens the Pro row in Settings
- **THEN** the app navigates to `PaywallRoute` from either entry point

### Requirement: Custom Compose paywall rendered with the design system
The paywall SHALL be a custom Compose screen built with the Nubecita design system (not the
provider's drop-in UI), presenting the locked copy, the perk list, and the plan picker. Plan
prices SHALL be rendered from the `SubscriptionOffering` loaded at runtime.

#### Scenario: Paywall content
- **WHEN** the paywall is shown
- **THEN** it displays the headline "Keep Nubecita flying", the open-source sub-line, the perks (Picture-in-Picture, Supporter badge, support the dev), and Monthly + Annual plans with the annual marked best value
- **AND** the CTA reads "Become a Supporter"

#### Scenario: Annual selected by default
- **WHEN** the paywall opens
- **THEN** the Annual plan is pre-selected

### Requirement: Mandatory subscription disclosures
The paywall SHALL display, adjacent to the purchase CTA, the auto-renewal terms, price and
billing period, and how to cancel, plus links to Terms of Service, Privacy Policy, and Restore
Purchases. The CTA copy SHALL reflect reality (no "free" wording, since there is no trial).

#### Scenario: Disclosures present before purchase
- **WHEN** the paywall is shown
- **THEN** an auto-renewal/cancel disclosure and Terms / Privacy / Restore links are visible without scrolling past the CTA

### Requirement: Paywall MVI presenter
The paywall ViewModel SHALL extend the project `MviViewModel` with a flat `PaywallState`
exposing a mutually-exclusive `PaywallStatus` (Loading / Ready / Error), the loaded offering,
and the selected plan. Errors SHALL route through a `PaywallEffect` (e.g. `ShowError`) collected
once in the screen Composable. The Activity required by the Play purchase call SHALL be passed
from the Composable layer, not injected into the ViewModel.

#### Scenario: Purchase error surfaced as effect
- **WHEN** a purchase fails for a reason other than user cancellation
- **THEN** the ViewModel emits a `ShowError` effect and the screen shows a snackbar

#### Scenario: Successful purchase closes the paywall
- **WHEN** a purchase succeeds
- **THEN** the paywall dismisses and the unlock propagates via `EntitlementRepository.isPro`
