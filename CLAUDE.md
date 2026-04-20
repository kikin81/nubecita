# CLAUDE.md

## Project overview

Nubecita — a fast, lightweight, native Android client for [Bluesky](https://bsky.app) and the AT Protocol. Kotlin + Jetpack Compose with Material 3 Expressive, MVI on `ViewModel` + `StateFlow`, Hilt / Room / Coil, `atproto-kotlin` for networking. 100% native; 120hz scrolling is a hard requirement.

## Key commands

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew spotlessCheck lint
./gradlew :app:jacocoTestReport

pre-commit run --all-files
```

## Conventions

- JDK 17 (`.java-version`), auto-downloaded via Foojay.
- Spotless + ktlint 1.4.1 + Compose rules.
- Conventional Commits enforced by commitlint.
- `main` is protected; feature branches + PRs only.
