# Nubecita

[![CI](https://github.com/kikin81/nubecita/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/kikin81/nubecita/actions/workflows/ci.yaml)
[![Release](https://img.shields.io/github/v/release/kikin81/nubecita?label=release&sort=semver)](https://github.com/kikin81/nubecita/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3_Expressive-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

A fast, lightweight, native Android client for [Bluesky](https://bsky.app) and the AT Protocol. Spanish for "little cloud" — the UI aims to feel weightless to match.

## Features

- **Following timeline** with paginated infinite scroll, pull-to-refresh, and a composition-scoped video player coordinator. 120 Hz scroll target verified on Pixel-class devices via `dumpsys gfxinfo` baselines.
- **Native renderers for every `app.bsky.embed.*` lexicon** — images, external link cards, HLS video, quoted records, and `recordWithMedia` composites. No `Unsupported` chips for current types.
- **Composer** for new posts — grapheme-accurate counter (ICU), language picker, mention typeahead, and a discard-confirmation flow.
- **Profile** with paginated posts / replies / media tabs and compact-format counts via ICU.
- **Post detail / thread view** with ancestors, focused post, and nested replies.
- **Media viewer** — zoomable images via telephoto and Media3 HLS playback.
- **Direct messages** — conversation list + thread view with day separators and message bubbles, scoped to `chat.bsky.*` via the OAuth `transition:chat.bsky` scope.
- **OAuth login** with system Custom Tabs handoff and session persistence — no embedded WebView.
- **Edge-to-edge rendering** with system-bar inset propagation through every screen state.
- **Material 3 Expressive theming** with Material You dynamic color on Android 12+ and a brand fallback palette below.

<details>
<summary><b>Embed-renderer deep dive</b></summary>

- **`images`** — 1–4 images with deterministic geometry + Compose-native async loading
- **`external`** — link-preview cards with precomputed display domains; tap opens via Custom Tabs
- **`video`** — HLS playback through Media3 / ExoPlayer with autoplay-muted scroll-into-view binding, single-player coordinator (at most one materialized `ExoPlayer`), and tap-to-unmute audio focus handling
- **`record`** — quoted posts at near-parent density with bounded-recursion type system (record-of-record renders a "View thread" chip rather than infinitely nesting)
- **`recordWithMedia`** — composite quote + media embeds; coordinator descends into both for video binding

</details>

## Stack

- **Kotlin** + **Jetpack Compose** with **Material 3 Expressive**
- **MVI** architecture (`UiState` / `UiEvent` / `UiEffect`) on `ViewModel` + `StateFlow`
- **Jetpack Navigation 3** with `EntryProviderInstaller` multibindings split across an outer shell (Splash → Login → Main) and an inner `MainShell` for tabs + sub-routes
- **Hilt** for DI, **Coil 3** for images, **Media3** for video, **DataStore + Tink** for encrypted OAuth session storage (Room is on the roadmap for offline caches / composer drafts but not yet wired)
- **`atproto-kotlin`** SDK for AT Protocol networking
- 100% native — no web views; 120 Hz scrolling is a hard requirement

<details>
<summary><b>Module layout</b></summary>

```
app/                     thin shell; aggregates DI, hosts NavDisplay + MainActivity
build-logic/             composite build with five Gradle convention plugins
core/
  auth/                  OAuth session storage + token refresh
  common/                shared utilities (incl. :core:common:navigation qualifiers)
  feed-mapping/          AT Proto post -> UI model mappers
  posting/               post-creation domain
  posts/                 post fetching / repositories
  post-interactions/     like / repost / follow primitives
  testing/               JVM test helpers
  testing-android/       androidTest helpers (Compose harness, etc.)
data/models/             shared data models
designsystem/            Compose-using library; M3 Expressive tokens + components
feature/
  chats/{api,impl}       conversation list + DM thread
  composer/{api,impl}    post composer with mention typeahead
  feed/{api,impl}        Following timeline
  login/{api,impl}       OAuth login (outer shell)
  mediaviewer/{api,impl} zoomable image / video lightbox
  postdetail/{api,impl}  thread view
  profile/{api,impl}     user profile with tabs
  search/api             api-only stub; :impl ships later
```

Every Android module applies one of five convention plugins (`nubecita.android.library` / `.library.compose` / `.feature` / `.application` / `.hilt`). Plugin roster and "how to add a new module" recipe: [`build-logic/README.md`](build-logic/README.md). Per-module conventions and MVI rules live in [`CLAUDE.md`](CLAUDE.md).

Feature modules use the **api / impl** split required by Navigation 3:

- `:feature:<name>:api` — `NavKey` types only.
- `:feature:<name>:impl` — screens, ViewModels, and Hilt modules that contribute `@Provides @IntoSet EntryProviderInstaller` annotated with **exactly one** of `@OuterShell` or `@MainShell` (qualifiers in `:core:common:navigation`). Unqualified providers are dropped by both shells.

</details>

## Requirements

- JDK 17 (tracked by `.java-version`, see `.sdkmanrc`)
- Android SDK (API 37+)
- `pre-commit` and `commitizen` (`brew install pre-commit commitizen`); `commitizen` powers the interactive `cz commit` prompt installed by `scripts/install-git-hooks.sh`.

## Local development

```bash
./gradlew :app:assembleDebug                              # build
./gradlew testDebugUnitTest                               # tests
./gradlew :designsystem:validateDebugScreenshotTest       # screenshot baselines
./gradlew spotlessCheck lint                              # lint + format
pre-commit run --all-files                                # all hooks
```

For Compose Compiler stability + recomposition reports (used during perf audits and the `nubecita-ppj` macrobench setup):

```bash
./gradlew :app:assembleRelease -PcomposeReports=true -PdebugSignedRelease=true
# reports land at <module>/build/compose_compiler/
```

<details>
<summary><b>Project baseline & tooling</b></summary>

Bootstrapped from an internal Android template: AGP 9, Kotlin DSL, version catalog (`gradle/libs.versions.toml`), Spotless + ktlint 1.4.1 + Compose rules, Jacoco coverage, CI (`lint` / `test` / `build` / `screenshot`), semantic-release, Renovate, and pre-commit hooks (commitlint, actionlint, gitleaks, spotless, openspec validate).

Specs and design decisions are tracked under [`openspec/`](openspec/) — `specs/<capability>/` for canonical requirements, `changes/<change-name>/` for in-flight proposals. Tasks and dependencies live in [beads](https://github.com/steveyegge/beads) (`bd`) — `bd ready` for available work, `bd show <id>` for issue detail.

</details>
