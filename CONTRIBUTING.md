# Contributing

Thanks for contributing!

## Branching

- `main` is protected; all changes go through PRs.
- Feature branches: `feat/<short-description>`.
- Fix branches: `fix/<short-description>`.

## Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/), enforced by commitlint. Use `cz commit` (from commitizen) if you'd like a prompt.

## Before opening a PR

- Run `pre-commit run --all-files` and fix anything flagged.
- Run `./gradlew spotlessCheck lint testDebugUnitTest assembleDebug` locally.
- If you changed the rename script, run `RUN_FULL_BUILD=1 python3 -m unittest scripts/test_rename.py`.

## PR expectations

- Fill out the PR template.
- Keep PRs focused; split large changes.
- Tag reviewers in CODEOWNERS.
