# Nubecita

[![CI](https://github.com/kikin81/nubecita/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/kikin81/nubecita/actions/workflows/ci.yaml)
[![Release](https://img.shields.io/github/v/release/kikin81/nubecita?label=release&sort=semver)](https://github.com/kikin81/nubecita/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3_Expressive-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

A fast, lightweight, native Android client for [Bluesky](https://bsky.app) and the AT Protocol. Spanish for "little cloud" — the UI aims to feel weightless to match.

## Features

**Following timeline.** Production-grade feed screen with paginated infinite scroll, pull-to-refresh, and a composition-scoped video player coordinator. 120 Hz scroll target verified on Pixel-class devices via `dumpsys gfxinfo` baselines.

**Native renderers for every Bluesky `app.bsky.embed.*` lexicon variant** — no `Unsupported` chips for current types:

- **`images`** — 1–4 images with deterministic geometry + Compose-native async loading
- **`external`** — link-preview cards with precomputed display domains; tap opens via Custom Tabs
- **`video`** — HLS playback through Media3 / ExoPlayer with autoplay-muted scroll-into-view binding, single-player coordinator (at most one materialized `ExoPlayer`), and tap-to-unmute audio focus handling
- **`record`** — quoted posts at near-parent density with bounded-recursion type system (record-of-record renders a "View thread" chip rather than infinitely nesting)
- **`recordWithMedia`** — composite quote + media embeds; coordinator descends into both for video binding

**OAuth login** with system Custom Tabs handoff and session persistence — no embedded WebView.

**Edge-to-edge rendering** with system-bar inset propagation through every screen state (loading / error / empty / loaded).

**Material 3 Expressive theming** with Material You dynamic color on Android 12+ and a brand fallback palette below.

## Stack

- **Kotlin** + **Jetpack Compose** with **Material 3 Expressive**
- **MVI** architecture (`UiState` / `UiEvent` / `UiEffect`) on `ViewModel` + `StateFlow`
- **Hilt** for DI, **Room** for local persistence, **Coil** for images, **Media3** for video
- **`atproto-kotlin`** SDK for AT Protocol networking
- 100% native — no web views; 120 Hz scrolling is a hard requirement

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

## Project baseline

Bootstrapped from an internal Android template: AGP 9, Kotlin DSL, version catalog (`gradle/libs.versions.toml`), Spotless + ktlint 1.4.1 + Compose rules, Jacoco coverage, CI (`lint`/`test`/`build`/`screenshot`), semantic-release, Renovate, and pre-commit hooks (commitlint, actionlint, gitleaks, spotless, openspec validate).

Specs and design decisions are tracked under [`openspec/`](openspec/) — `specs/<capability>/` for canonical requirements, `changes/<change-name>/` for in-flight proposals. Tasks and dependencies live in [beads](https://github.com/steveyegge/beads) (`bd`) — `bd ready` for available work, `bd show <id>` for issue detail.
