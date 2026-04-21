# CLAUDE.md

## Project overview

Nubecita — a fast, lightweight, native Android client for [Bluesky](https://bsky.app) and the AT Protocol. Kotlin + Jetpack Compose with Material 3 Expressive, MVI on `ViewModel` + `StateFlow`, Hilt for DI. 100% native; 120hz scrolling is a hard requirement.

Pending: Room (persistence), Coil (images), `atproto-kotlin` (networking) — listed in the README stack, not yet wired.

## Key commands

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew spotlessCheck lint
./gradlew :app:jacocoTestReport

pre-commit run --all-files
```

## Workflow

All work is tracked in [beads](https://github.com/steveyegge/beads) (`bd`). Every change starts from a bd issue and lands through a short-lived branch + PR. When using Claude Code, the `bd-workflow` skill automates the ceremony; without it, the steps below are the convention.

### Loop

1. `bd ready` — pick an unblocked issue.
2. `bd update <id> --claim`.
3. Create a branch named `<type>/<id>-<slug>` off `main`.
4. Commit with Conventional Commits; reference the bd id in the body footer.
5. Open a PR with `Closes: <id>` in the body.
6. `bd close <id>` after merge.

### Branch names

`<type>/<bd-id>-<slug>` where `<type>` is a Conventional Commit type (`feat`, `fix`, `chore`, `refactor`, `docs`, `test`, `perf`, `ci`, `build`, `style`). Infer from the bd issue type: `feature`→`feat`, `bug`→`fix`, `task`/`chore`→`chore`, `decision`→`docs`. Slug = kebab-cased title, capped at 50 chars. Never branch off an epic — work a child issue.

Example: `feat/nubecita-aew-create-mviviewmodel-base-class-and-mar`

### Commits

Conventional Commits via commitizen/commitlint. One bd issue per branch; reference it in the footer:

```
feat(mvi): add MviViewModel base class and marker interfaces

Introduce UiState/UiEvent/UiEffect markers plus abstract
generic MviViewModel<S,E,F> exposing StateFlow + buffered
effects Channel.

Refs: nubecita-aew
```

Use `Refs:` on work-in-progress commits. `Closes: <bd-id>` goes in the **PR body only**, not in every commit — otherwise a squash-merge double-closes.

### Rules of thumb

- One bd issue per branch. If scope grows, spawn a new bd issue.
- PR title should be Conventional — on squash-merge it becomes the commit subject. (Currently enforced only by the local `commit-msg` pre-commit hook, not CI.)
- `bd close` only after the PR merges.

## Conventions

- JDK 17 (`.java-version`), auto-downloaded via Foojay.
- Spotless + ktlint 1.4.1 + Compose rules.
- Conventional Commits enforced by commitlint.
- `main` is protected; feature branches + PRs only.
