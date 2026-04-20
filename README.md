# Nubecita

A fast, lightweight, native Android client for [Bluesky](https://bsky.app) and the AT Protocol. Spanish for "little cloud" — the UI aims to feel weightless to match.

## Stack

- **Kotlin** + **Jetpack Compose** with **Material 3 Expressive**
- **MVI** architecture (`UiState` / `UiEvent` / `UiEffect`) on `ViewModel` + `StateFlow`
- **Hilt** for DI, **Room** for local persistence, **Coil** for images
- **`atproto-kotlin`** SDK for AT Protocol networking
- 100% native — no web views; 120hz scrolling is a hard requirement

## Requirements

- JDK 17 (tracked by `.java-version`, see `.sdkmanrc`)
- Android SDK (API 37+)
- `pre-commit` and `commitizen` (`brew install pre-commit commitizen`); `commitizen` powers the interactive `cz commit` prompt installed by `scripts/install-git-hooks.sh`.

## Local development

```bash
./gradlew :app:assembleDebug     # build
./gradlew testDebugUnitTest      # tests
./gradlew spotlessCheck lint     # lint
pre-commit run --all-files       # run all hooks
```

## Project baseline

Bootstrapped from an internal Android template: AGP 9, Kotlin DSL, version catalog (`gradle/libs.versions.toml`), Spotless + ktlint 1.4.1 + Compose rules, Jacoco coverage, CI (`lint`/`test`/`build`), semantic-release, Renovate, and pre-commit hooks (commitlint, actionlint, gitleaks, spotless).
