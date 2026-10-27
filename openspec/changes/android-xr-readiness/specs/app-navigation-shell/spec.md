## ADDED Requirements

### Requirement: MainShell projects top-level navigation into spatial Orbiter in XR sessions

`MainShell` SHALL project top-level navigation into a leading spatial `Orbiter` (`androidx.xr.compose.material3`) when running in an active Android XR spatial environment. When XR spatial mode is inactive, `MainShell` SHALL fallback to `NavigationSuiteScaffold` (NavigationBar / NavigationRail).

#### Scenario: XR session renders floating navigation Orbiter
- **WHEN** `MainShell()` is composed within an active Android XR spatial session
- **THEN** top-level navigation items (Feed, Search, Notifications, Chats, You) SHALL be hosted in a floating spatial `Orbiter` outside the window panel, and the window's inner `NavDisplay` SHALL fill the entire panel bounds without an internal navigation bar or rail

#### Scenario: Non-XR session preserves standard NavigationSuiteScaffold
- **WHEN** `MainShell()` is composed on a standard non-XR device or when XR spatial features are absent
- **THEN** `MainShell()` SHALL host `NavigationSuiteScaffold` with `NavigationBar` at compact width and `NavigationRail` at medium/expanded widths
