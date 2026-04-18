# CLAUDE.md

## Project overview

Private GitHub template for bootstrapping Android apps with AGP 9 / Compose / Kotlin DSL / version catalog baseline plus CI, release, pre-commit, and a one-shot rename script.

## Key commands

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew spotlessCheck lint
./gradlew :app:jacocoTestReport

pre-commit run --all-files

python3 scripts/rename.py --help                    # in an unrenamed template clone
RUN_FULL_BUILD=1 python3 -m unittest scripts/test_rename.py
```

## Conventions

- JDK 17 (`.java-version`), auto-downloaded via Foojay.
- Spotless + ktlint 1.4.1 + Compose rules.
- Conventional Commits enforced by commitlint.
- `main` is protected; feature branches + PRs only.

## Rename script

`scripts/rename.py` does the one-shot placeholder rename. Four required flags: `--package`, `--app-name`, `--app-name-pascal`, `--app-name-lower`. Script self-deletes on success (pass `--keep-script` to opt out). Design rationale and ordering are in `docs/superpowers/specs/2026-04-17-android-template-design.md`.
