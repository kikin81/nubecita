## Why

Settings has no "About" surface, so the app exposes nothing about itself: there is no link to the open-source repository, no attribution for the third-party libraries it ships, and no place to credit the people who helped build it. Adding an About sub-screen fills that gap and gives the project a home for source, licenses, and acknowledgements.

Tracked by bd `nubecita-jkg8` (single feature, one PR).

## What Changes

- Add an **"About"** row to the Settings screen that opens a dedicated **About** sub-screen.
- The About screen shows: app name + version header, a **"Source on GitHub"** link (opens `https://github.com/kikin81/nubecita` in a Custom Tab), a **Special Thanks** section, and an **"Open source licenses"** entry.
- **Special Thanks** rows are *live*: a static list of contributor DIDs + blurbs is hydrated at runtime via the existing `ActorRepository` (avatar, display name, handle). Tapping a row deep-links to that user's Profile. A failed avatar fetch falls back to handle + blurb + a placeholder.
- New **Open source licenses** sub-screen lists the app's libraries, generated at build time by the `com.mikepenz.aboutlibraries` Gradle plugin and rendered through the design system's own row components (custom `libraryRow` slot). Tapping a library opens its URL.
- New build tooling: the `aboutlibraries` Gradle plugin + `aboutlibraries-compose` runtime, wired into `:feature:settings:impl`.

No breaking changes. All navigation reuses the established `@MainShell` sub-route + `onNavigateTo` pattern; external links reuse the existing `LaunchUri`/Custom Tabs handler.

## Capabilities

### New Capabilities
- `feature-settings-about`: the About sub-screen (app/version header, GitHub source link, live-avatar Special Thanks with profile deep-links, and a custom-rendered open-source licenses screen backed by the aboutlibraries plugin).

### Modified Capabilities
<!-- None. New @MainShell routes are added via the existing EntryProviderInstaller/navigation pattern, which does not change the requirements of `app-navigation-shell` or `core-common-navigation`. The Settings screen has no existing capability spec to amend. -->

## Impact

- `:feature:settings:api` — new `About` and `AboutLicenses` `NavKey` types.
- `:feature:settings:impl` — `AboutScreen` + `AboutViewModel`, `AboutLicensesScreen`, new `SettingsRow.Action` ("About") + effect, `SettingsNavigationModule` registers the two new `@MainShell` entries; new module deps on `:core:actors` and `aboutlibraries-compose`; applies the `aboutlibraries` Gradle plugin.
- `gradle/libs.versions.toml` — add the `aboutlibraries` plugin + library coordinates (pin `15.0.0-b03`; verify the beta builds against current AGP/Kotlin/Compose).
- Reuses (no change): `Profile` NavKey for deep-links, `ActorRepository.getActor`, the Settings `LaunchUri`/`CustomTabsIntent` handler, the MVI base.
