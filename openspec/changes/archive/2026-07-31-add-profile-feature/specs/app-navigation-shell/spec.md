## MODIFIED Requirements

### Requirement: Empty tabs render `:app`-side placeholder Composables

Until each of `:feature:search:impl` and `:feature:chats:impl` exists, `:app` SHALL provide internal `@MainShell`-qualified `EntryProviderInstaller` bindings that register placeholder Composables for the corresponding top-level destinations. Each placeholder SHALL identify the destination and indicate that the feature is not yet implemented (e.g. "Search — coming soon").

`:feature:profile:impl` ships in the `add-profile-feature` change and provides its own `@MainShell` `EntryProviderInstaller` bindings for both `Profile(handle: String? = null)` (covering both own profile and other-user profile in a single provider) and the `Settings` sub-route (rendering a one-screen stub with a Sign Out affordance). When `:feature:profile:impl` is present in the build, `:app` MUST NOT register placeholders for `Profile` or `Settings` — both are owned by `:feature:profile:impl`. The placeholder mechanism remains in place for Search and Chats until their respective `:impl` modules graduate.

#### Scenario: Search tab without :impl renders placeholder

- **WHEN** `:feature:search:impl` does not exist in the build and the user activates the Search tab
- **THEN** `MainShell` SHALL render a placeholder Composable provided by `:app` that visually identifies the Search destination

#### Scenario: You tab renders the profile feature, not a placeholder

- **WHEN** `:feature:profile:impl` is present in the build and the user activates the You top-level tab
- **THEN** `MainShell` SHALL render `:feature:profile:impl`'s `Profile(handle = null)` provider; `:app`'s `MainShellPlaceholderModule` MUST NOT register a placeholder `EntryProviderInstaller` for the `Profile` NavKey

#### Scenario: Settings sub-route renders the profile-feature stub, not a placeholder

- **WHEN** any caller pushes the `Settings` `NavKey` onto `LocalMainShellNavState` and `:feature:profile:impl` is present in the build
- **THEN** `MainShell` SHALL render `:feature:profile:impl`'s `Settings` provider (the one-screen stub with Sign Out); `:app`'s `MainShellPlaceholderModule` MUST NOT register a placeholder for the `Settings` NavKey
