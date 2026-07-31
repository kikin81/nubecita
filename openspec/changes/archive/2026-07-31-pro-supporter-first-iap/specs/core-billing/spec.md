## ADDED Requirements

### Requirement: SDK-agnostic billing boundary
The `:core:billing` module SHALL expose all subscription and entitlement capabilities through
pure-Kotlin interfaces and `:data:models` types only. No third-party billing SDK type (e.g.
RevenueCat `CustomerInfo`, `Offerings`, `Package`, `StoreProduct`) SHALL cross the module's
public boundary, so the billing provider can be replaced by rewriting only the `impl`.

#### Scenario: Consumers depend only on our types
- **WHEN** any module (`:feature:paywall`, `:feature:profile`, `:core:video`, `:app`) consumes billing
- **THEN** it references only `EntitlementRepository`, `BillingRepository`, and `:data:models` subscription types
- **AND** it never imports a RevenueCat (or other provider) class

### Requirement: Pro entitlement state
The system SHALL expose the user's Pro status as `EntitlementRepository.isPro: StateFlow<Boolean>`,
derived from the active `pro` entitlement, and SHALL update it reactively when the entitlement
changes (purchase, restore, renewal, expiry, refund).

#### Scenario: Entitlement reflects active subscription
- **WHEN** the user has an active `pro` subscription
- **THEN** `isPro` emits `true`

#### Scenario: Entitlement loss propagates
- **WHEN** the `pro` entitlement expires or is refunded
- **THEN** `isPro` emits `false` without requiring an app restart

#### Scenario: Non-blocking cold start
- **WHEN** the app cold-starts before the billing provider has resolved cached entitlement
- **THEN** `isPro` emits `false` initially and flips to `true` once resolved, never blocking the UI thread

### Requirement: Google-Play-account identity
The system SHALL bind Pro to the Google Play account using an anonymous provider `appUserId`
and SHALL NOT send the user's Bluesky DID to the billing provider. Restoring purchases SHALL
re-associate an existing Play subscription on a new device.

#### Scenario: Pro survives a deleted Bluesky account
- **WHEN** the user deletes their Bluesky account and signs in with a new DID
- **THEN** their active Pro subscription remains valid (it was never bound to the DID)

#### Scenario: Restore on a new device
- **WHEN** the user installs the app on another device signed into the same Google account and taps Restore
- **THEN** the `pro` entitlement is restored and `isPro` emits `true`

### Requirement: Subscription offerings
`BillingRepository` SHALL load the available plans as a `SubscriptionOffering` (monthly + annual)
populated from the provider at runtime, exposing localized price strings, the annual per-month
equivalent, and the annual savings percentage. Prices SHALL NOT be hardcoded in the UI.

#### Scenario: Plans loaded with localized prices
- **WHEN** the paywall requests plans
- **THEN** it receives a `SubscriptionOffering` with monthly and annual plans carrying store-localized prices
- **AND** the annual plan exposes a per-month equivalent and savings percent

### Requirement: Purchase and restore
`BillingRepository` SHALL launch the Google Play purchase flow for a selected plan and SHALL
support restoring purchases, returning a typed result that distinguishes success, user
cancellation, and error.

#### Scenario: Successful purchase
- **WHEN** the user completes the Play purchase flow for a plan
- **THEN** the result is success and `isPro` emits `true`

#### Scenario: User cancels purchase
- **WHEN** the user dismisses the Play purchase sheet
- **THEN** the result indicates cancellation and is not treated as an error
