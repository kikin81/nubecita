## Why

Nubecita's `Main` NavEntry currently renders a placeholder Composable (greetings + a refresh button). It has no top-level navigation surface, so feature modules beyond `:feature:feed` and `:feature:login` have nowhere to plug in. The search, chats, and profile epics are all blocked on this. Building the shell adaptive from day one — `NavigationBar` on phones swapping to `NavigationRail` on foldables/tablets — also delivers a tablet-ready UX before any feature has to think about form factor individually.

## What Changes

- **New `MainShell` composable** in `:app` hosting `NavigationSuiteScaffold` (M3 adaptive primitive that auto-swaps `NavigationBar` ↔ `NavigationRail` ↔ `NavigationDrawer` based on `WindowSizeClass`) over an inner `NavDisplay`. Replaces the existing `MainScreen` placeholder in `app/Navigation.kt`'s `Main` entry.
- **Four top-level destinations**: `Feed | Search | Chats | You`. Material Symbols icons: `Home`, `Search`, `ChatBubbleOutline`/`ChatBubble`, `Person`. Initial start tab: Feed.
- **New `MainShellNavState` class** in `:core:common:navigation` modeled on the Nav3 `multiple-backstacks` recipe — Compose-owned (`rememberMainShellNavState(...)`), holds per-tab back stacks plus the active-tab pointer, persists across config + process death via `rememberSerializable` + `rememberNavBackStack`. Exposed to screen composables via `LocalMainShellNavState` `CompositionLocal`. Naturally GC'd on logout because the `Main` NavEntry leaves the outer back stack and the `remember`'d state goes with it.
- **Two new Hilt qualifier annotations** in `:core:common:navigation`: `@OuterShell` (existing outer `NavDisplay`) and `@MainShell` (new inner `NavDisplay`). The existing `EntryProviderInstaller` typealias keeps its shape; its multibinding partitions by qualifier.
- **New api-only stub modules**: `:feature:search:api`, `:feature:chats:api`, `:feature:profile:api`. Each declares its top-level `NavKey` (`Search`, `Chats`, `Profile(handle: String? = null)`). `Settings : NavKey` lives in `:feature:profile:api`. No `:impl` modules — `:app` hosts placeholder Composables for each empty tab until the corresponding feature epic stands up an `:impl`.
- **Migration of existing modules**: `:feature:login:impl` adds `@OuterShell` to its `EntryProviderInstaller` provider; `:feature:feed:impl` adds `@MainShell`. One-line changes each.
- **Tab-internal navigation via MVI Effect**: ViewModels emit `UiEffect.Navigate(target)`; screen composables consume the effect and call `mainShellNavState.add(target)` on the active tab. Cross-tab links (tap a profile inside a Feed post) push onto the *current* tab's stack — never a tab switch. ViewModels do **not** inject `MainShellNavState`; it is reachable only from composables via `CompositionLocal`. This is intentional: it forces tab-internal navigation through the existing MVI Effect boundary.
- **BREAKING (internal only)**: the existing `EntryProviderInstaller` multibinding becomes partitioned by qualifier. Any `:feature:*:impl` providing into the unqualified set must add `@OuterShell` or `@MainShell` to keep working. Two existing modules affected; both migrated in this change.

No deviation from the MVI / Compose / Hilt / Room / Coil baseline. The change reinforces the MVI Effect pattern by routing tab-internal navigation through it.

## Capabilities

### New Capabilities
- `app-navigation-shell`: Adaptive multi-tab navigation shell hosted by the `Main` NavEntry. Defines the four top-level destinations, `NavigationSuiteScaffold` chrome behavior, per-tab back-stack semantics ("exit through home"), process-death persistence, and the placeholder-screen contract for tabs whose feature `:impl` modules do not exist yet.

### Modified Capabilities
- `core-common-navigation`: Adds the `MainShellNavState` Compose-owned state holder, the `LocalMainShellNavState` `CompositionLocal`, and the `@OuterShell` / `@MainShell` Hilt qualifiers. The existing Hilt `Navigator` singleton requirements are unchanged. The existing `EntryProviderInstaller` typealias keeps its shape but its multibinding is now partitioned by qualifier.

## Impact

**Code:**
- `:core:common:navigation/` — new files: `MainShellNavState.kt`, `LocalMainShellNavState.kt`, `NavQualifiers.kt`. `EntryProviderInstaller.kt` unchanged.
- `:app/.../shell/` — new file: `MainShell.kt`. Updated: `app/Navigation.kt` (`Main` entry swap), `app/.../navigation/NavigationEntryPoint.kt` (parallel `@MainShell` accessor).
- `:app/.../ui/main/MainScreen*.kt` — placeholder content removed (Feed tab renders the real `:feature:feed` `Feed` entry).
- `:feature:login:impl/.../LoginNavigationModule.kt` — add `@OuterShell`.
- `:feature:feed:impl/.../FeedNavigationModule.kt` — add `@MainShell`.
- New modules: `:feature:search:api`, `:feature:chats:api`, `:feature:profile:api` — api-only via `nubecita.android.library` convention plugin.

**Dependencies:**
- New: `androidx.compose.material3.adaptive:adaptive-navigation-suite` (the `NavigationSuiteScaffold` primitive) added to `gradle/libs.versions.toml`. Same Compose BoM family as existing `material3`.
- No changes to atproto-kotlin, Hilt, Coil, Room, or other baseline.

**Tickets:**
- Closes **nubecita-3it** (process-death persistence) as a side effect of adopting the recipe's `rememberSerializable` + `rememberNavBackStack` shape.
- Unblocks the search, chats, profile/settings, and post-detail epics by giving each a tab to land in.

## Non-goals

- **Deep linking** (intent handling, URL schemes, start-on-tab semantics) — separate future epic. `Main : NavKey` stays parameterless in this change.
- **Settings as its own module** — sub-route inside `:feature:profile:api` until it grows enough to earn a split.
- **`:feature:search:impl`, `:feature:chats:impl`, `:feature:profile:impl`** — only api stubs land here. Real screens are deferred to each feature's own epic; `:app`-side placeholder Composables fill the gap meanwhile.
- **Adaptive list-detail layouts** (e.g. tablet two-pane post-detail). `NavigationSuiteScaffold` provides chrome only; per-screen list-detail is the post-detail epic's child task.
- **Refactoring `:core:common:Navigator` from `@Singleton` to `@ActivityRetainedScoped`** — the Nav3 recipe uses `@ActivityRetainedScoped`; the repo's existing pattern is `@Singleton`. Not in scope; flag as a separate follow-up.
