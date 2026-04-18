# Android Template — Design

**Date:** 2026-04-17
**Status:** Design approved, implementation plan pending
**Owner:** Francisco Velazquez (@kikin81)

## Goal

A private GitHub template repository that bootstraps a new Android app with modern baseline tooling (AGP 9, Kotlin DSL, Compose, version catalog) plus the surrounding CI/release/quality scaffolding. Clicking **Use this template** followed by running one rename script should produce a fully renamed, CI-green Android project.

## Non-goals

Explicitly out of scope for this template:

- Hilt, Ktor/Retrofit, Coil, or any other runtime library pre-wiring — each new project picks its own stack.
- `build-logic/` convention plugins — unnecessary for a single-module `:app`.
- Firebase App Distribution — deferred as a follow-up (requires per-project Firebase setup).
- Detekt — ktlint + Compose rules is sufficient for the minimal baseline.
- Dependency Guard / dependency-analysis plugins.
- Screenshot testing (Paparazzi/Roborazzi).
- Macrobenchmark, baseline profiles.

## Architecture

The template repo = **unmodified `android create empty-activity` output** (AGP 9, Compose, Kotlin DSL, `gradle/libs.versions.toml` already wired) **plus** the scaffolding below. The app scaffold itself is not customized — the value is in the surrounding tooling.

```
android-template/
├── app/                                  # unmodified `android create` output
│   ├── build.gradle.kts
│   └── src/{main,test,androidTest}/java/com/example/myapp/...
├── gradle/
│   ├── libs.versions.toml                # from `android create`
│   └── wrapper/
├── build.gradle.kts                      # + Spotless plugin
├── settings.gradle.kts                   # + Foojay toolchain resolver
├── gradle.properties                     # + caching/parallel/config-cache flags
├── scripts/
│   ├── rename.py                         # one-shot rename tool (self-deletes by default)
│   └── test_rename.py                    # self-test for rename.py
├── config/
│   └── spotless/                         # ktlint config, copyright header (if any)
├── .github/
│   ├── workflows/
│   │   ├── ci.yaml
│   │   └── release.yaml
│   ├── renovate.json
│   ├── CODEOWNERS
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── ISSUE_TEMPLATE/
├── .pre-commit-config.yaml
├── .commitlintrc.yaml
├── .cz.json                              # commitizen
├── .releaserc.json                       # semantic-release
├── .editorconfig
├── .gitattributes
├── .gitignore
├── .java-version                         # 17
├── .sdkmanrc
├── README.md                             # "Use this template" checklist + rename instructions
├── CONTRIBUTING.md
├── LICENSE
└── CLAUDE.md                             # agent guidance carried across derived projects
```

### Day-one green constraint

The template repo's CI must pass **in its unrenamed state** (`com.example.myapp`). Clicking **Use this template** gives the user a green build before they run the rename script. The rename must also produce a green build.

## Rename script — `scripts/rename.py`

### CLI

```
python scripts/rename.py \
  --package com.foo.bar \
  --app-name "Foo Bar" \
  --app-name-pascal FooBar \
  --app-name-lower foobar \
  [--dry-run] [--keep-script]
```

### Flags

| Flag | Purpose | Validation regex |
|---|---|---|
| `--package` | Dotted Java package | `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$` |
| `--app-name` | Human-readable display name (goes to `strings.xml` `app_name`) | non-empty; XML-escaped on write |
| `--app-name-pascal` | Kotlin class-name segment (used in theme names, etc.) | `^[A-Z][A-Za-z0-9]*$` |
| `--app-name-lower` | Lowercase identifier (Gradle `rootProject.name`, lowercase refs) | `^[a-z][a-z0-9]*$` |
| `--dry-run` | Print planned directory moves + per-file unified diffs, write nothing | — |
| `--keep-script` | Skip self-deletion at the end | — |

All four content flags are **required**. No derivation from `--app-name` — the user controls casing explicitly (design decision captured in brainstorming).

### Pre-flight guard

Abort with a clear error message if:

- `app/src/main/java/com/example/myapp/` does not exist, OR
- no file in the repo contains `com.example.myapp`.

This prevents double-rename and silent no-ops when the script is run on an already-renamed project.

### Execution order

Order matters — most specific patterns first to avoid collisions:

1. **Move three directory trees**: `app/src/{main,test,androidTest}/java/com/example/myapp/` → `.../java/<package-as-path>/`. Create the new parent with `mkdir(parents=True, exist_ok=True)` before `Path.rename`.
2. **Walk & rewrite files** matching `*.kt *.kts *.xml *.md *.properties *.toml` under the repo, skipping `.git/`, `build/`, `.gradle/`, `node_modules/`, `.idea/`, and `scripts/rename.py` itself. Apply substitutions in this order per file, all via `str.replace` (case-sensitive literal). Order matters — longest/most-specific first:
   1. `com.example.myapp` → new dotted package.
   2. `com/example/myapp` → new path-form package (for stringified paths, if any).
   3. `My App` → `--app-name` (the space makes it unambiguous).
   4. `MyApplication` → `--app-name-pascal` (must run before `MyApp`). The AGP 9 generator emits `MyApplicationTheme` / `Theme.MyApplication`; substituting `MyApp` first would mangle these into `{pascal}licationTheme`.
   5. `MyApp` → `--app-name-pascal` (defensive — not emitted by the current generator, but protects against future template changes).
   6. `myapp` → `--app-name-lower` (defensive — current generator's bare `myapp` occurrences are all inside dotted/slashed package refs, already handled above; this catches future bare occurrences).
3. **Print post-rename checklist** to stdout (see below).
4. **Self-remove** `scripts/rename.py` and `scripts/test_rename.py` unless `--keep-script` was passed.

### Post-rename checklist (printed)

- Set up the git remote (`git remote add origin …`).
- Configure Renovate on the new repo (enable the app, or commit the `renovate.json` if not already).
- Configure branch protection: required checks `lint`, `test`, `build`; squash-merge only; auto-delete branches; linear history.
- If enabling semantic-release: add required secrets (per the release workflow's comments).
- Update the README title/description.
- Remove the placeholder app icon / add your own in `res/mipmap-*`.

### Self-test — `scripts/test_rename.py`

Runs against a fresh `android create` output to guarantee the rename survives template updates:

1. In a tmp directory, run `android create empty-activity --name="My App" --output=<tmp>`.
2. Copy `scripts/rename.py` into the tmp project.
3. Execute it with fixture inputs (e.g., `--package com.acme.widget --app-name "Acme Widget" --app-name-pascal AcmeWidget --app-name-lower acmewidget`).
4. Assert:
   - Zero residual matches for any of: `com.example.myapp`, `com/example/myapp`, `My App`, `MyApplication`, `MyApp`, `myapp`.
   - Expected identifiers present in:
     - `app/build.gradle.kts` (`namespace = "com.acme.widget"`, `applicationId = "com.acme.widget"`).
     - `settings.gradle.kts` (`rootProject.name = "acmewidget"`).
     - `app/src/main/res/values/strings.xml` (`<string name="app_name">Acme Widget</string>`).
     - At least one Kotlin file's `package com.acme.widget...` declaration.
   - (Gated behind `RUN_FULL_BUILD=1` env, always on in CI) `./gradlew :app:assembleDebug` succeeds.
5. A second fixture runs `rename.py` with an `--app-name-lower` that appears as a substring of `--package` (e.g., package `com.foo.myapp`, lower `myapp`) to prove the literal-first ordering holds.

### Edge cases

- **`--app-name-lower` substring collision with `--package`** — handled by ordering: `com.example.myapp` is a literal replacement before `\bmyapp\b` runs.
- **Case-sensitivity on macOS** — if the user's new package path differs from the old one only by case, the directory rename will collide. Validated: we rename *entire* segments, so any case variance is always accompanied by a different spelling.
- **Windows line endings** — `mixed-line-ending` pre-commit hook enforces LF (except `.bat`); not the rename script's concern.

## CI — `.github/workflows/ci.yaml`

Ported from slabsnap's CI with two additions (wrapper validation, Android Lint in the lint job).

- **Triggers:** `push` to `main`, `pull_request`, `workflow_dispatch`.
- **Concurrency:** `${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true`.
- **Permissions:** `contents: write`, `pull-requests: write` (Jacoco PR comment).
- **Jobs** (parallel):
  - `lint` — `open-turo/actions-jvm/lint@v2` + `actions/setup-java@v5` (reads `.java-version`) + `android-actions/setup-android@v4` + `gradle/actions/setup-gradle@v6` + `gradle/actions/wrapper-validation@v6` + `./gradlew spotlessCheck lint` (Spotless and Android Lint in one step).
  - `test` — same setup + `./gradlew testDebugUnitTest jacocoTestReport` + `madrapps/jacoco-report@v1.7.2` (thresholds 0 by default — each derived project can raise them).
  - `build` — same setup + `./gradlew assembleDebug`.

## Release — `.github/workflows/release.yaml`

- **Triggers:** `push` to `main`, `workflow_dispatch`.
- Single `release` job: checkout + setup-java + setup-android + `open-turo/actions-jvm/release@v2` which runs semantic-release with `@open-turo/semantic-release-config/lib/gradle`.
- `.releaserc.json` = `{ "extends": "@open-turo/semantic-release-config/lib/gradle" }`.
- Commented-out Firebase App Distribution step with a TODO link to the follow-up design.

## Pre-commit — `.pre-commit-config.yaml`

Hybrid of kikinlex and slabsnap. Hooks:

- `pre-commit/pre-commit-hooks` — `check-json`, `check-yaml`, `check-toml`, `check-merge-conflict`, `check-added-large-files` (500kb), `end-of-file-fixer`, `trailing-whitespace`, `mixed-line-ending` (`--fix=lf`, excluding `*.bat`).
- `alessandrojcm/commitlint-pre-commit-hook` (commit-msg stage, `@open-turo/commitlint-config-conventional` as the shared config).
- `rhysd/actionlint`.
- `gitleaks/gitleaks` (pinned tag) — blocks commits containing secret-like patterns before they hit GitHub.
- Local `spotless` hook → `./gradlew spotlessApply` on `*.kt`/`*.kts` changes, `pass_filenames: false`.

`exclude` block covers `build/`, `.gradle/`, `node_modules/`, `.idea/`.

## Other config files

- **`.commitlintrc.yaml`** — `extends: ["@open-turo/commitlint-config-conventional"]`.
- **`.cz.json`** — commitizen with cz-conventional adapter.
- **`.github/renovate.json`** — port of slabsnap's: `config:recommended` + `github>open-turo/renovate-config:jvm`, `dependencyDashboard: true`, group rules for the Kotlin ecosystem and Compose UI, automerge Gradle minor/patch.
- **`.editorconfig`** — indent rules + no-wildcard-imports + disable Compose function-naming ktlint rule.
- **`.gitattributes`** — LF everywhere except `*.bat`.
- **`.java-version`** — `17`.
- **`.sdkmanrc`** — `java=17...-zulu` (exact build resolved when the template lands).
- **`gradle.properties`** — `org.gradle.caching=true`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=true`, `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`.

## Build-script additions on top of `android create`

The generated root `build.gradle.kts` and `app/build.gradle.kts` are close to minimal; the template adds:

- **Spotless plugin** (`com.diffplug.spotless`) in root `build.gradle.kts`, applied to all Kotlin/Kotlin-script sources.
  - ktlint `1.4.1` pinned.
  - Compose rules via `io.nlopez.compose.rules:ktlint:<latest>`.
  - Shared style rules in `.editorconfig` (not in the Gradle script).
  - Optional copyright header in `config/spotless/header.txt` (blank by default; derived projects can fill in).
- **Jacoco** — plugin applied in `app/build.gradle.kts`, with a `jacocoTestReport` task depending on `testDebugUnitTest`. Output at `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` so the CI's `madrapps/jacoco-report` step can consume it. Thresholds 0 initially.
- **Foojay toolchain resolver** plugin in `settings.gradle.kts` (mirror of kikinlex commit `d7c4068a`).

## GitHub repo settings — README checklist

These cannot be committed as files; they go in the README "After clicking Use this template" section:

- Template repo side: toggle **Template repository** on.
- Default branch `main`.
- **Branch protection** on `main`:
  - Required status checks: `lint`, `test`, `build`.
  - Require PR before merging (≥1 approval — adjust per project).
  - Require linear history.
  - Require signed commits (optional).
- Merge settings:
  - **Squash-merge only** (disable merge commits and rebase-merge).
  - **Auto-delete head branches** after merge.
- Secrets for `release.yaml`: per Open Turo's semantic-release config (checklist will enumerate them at implementation time).
- Optional: enable GitHub Advanced Security secret scanning on private repos.

## Open/deferred decisions

- **Firebase App Distribution** — follow-up design. Release workflow carries a commented-out stub.
- **Coverage thresholds** — template ships with 0/0; each derived project ratchets them up.
- **Detekt** — not added. Revisit if ktlint + Compose rules prove insufficient in practice.
- **Build-logic convention plugins** — not added for a single-module template. Revisit if the template grows to multi-module.

## Success criteria

1. Clicking **Use this template** on the private repo and cloning the result produces a green `lint`/`test`/`build` run with zero changes.
2. Running `python scripts/rename.py --package com.foo.bar --app-name "Foo Bar" --app-name-pascal FooBar --app-name-lower foobar` against a fresh clone produces a fully renamed project with zero residual `com.example.myapp`/`MyApp`/`myapp`/`My App` matches, and `./gradlew :app:assembleDebug` succeeds.
3. `scripts/test_rename.py` passes in CI, guaranteeing the rename script survives `android create` template changes.
4. Pre-commit hooks run cleanly on the unrenamed template and on a renamed project.
