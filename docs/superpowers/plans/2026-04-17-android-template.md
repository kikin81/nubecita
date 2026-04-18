# Android Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a private GitHub template repo that bootstraps Android apps with AGP 9 / Compose / Kotlin DSL / version catalog baseline, plus CI, release, pre-commit, and a one-shot rename script.

**Architecture:** The repo wraps an unmodified `android create empty-activity` output with CI/release/tooling scaffolding and a `scripts/rename.py` that replaces the placeholder package and app name identifiers. Each derived project starts green and customizes after running the rename.

**Tech Stack:** Android Gradle Plugin 9, Kotlin 2.x (Kotlin DSL), Jetpack Compose, Gradle version catalog, Spotless + ktlint 1.4.1 + Compose rules, Jacoco, pre-commit, commitlint, commitizen, semantic-release via Open Turo, Renovate, GitHub Actions, Python 3 (stdlib only) for the rename script.

**Repo:** `/Users/velazquez/code/android-template` (local; template visibility on GitHub is a later step).

**Source spec:** `docs/superpowers/specs/2026-04-17-android-template-design.md`

**Conventions used in this plan:**
- File paths are relative to the android-template repo root unless absolute.
- All commits use Conventional Commit prefixes (`feat:`, `chore:`, `docs:`, `ci:`, `build:`, `test:`).
- Each task ends with a single commit on the `plan/initial` branch (or a fresh feature branch for each task — the engineer may split if preferred).
- Where a step says "Expected: …", that output is the pass criterion.

---

## Task 1: Generate the baseline Android project

**Files:**
- Create: `app/` tree, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`, `local.properties` (the last is gitignored — see Task 2).

The `android create` command refuses to write into a non-empty directory, so we generate into a tmpdir and copy contents over, preserving the existing `docs/` and `.git/`.

- [ ] **Step 1: Generate the template in a tmpdir**

```bash
rm -rf /tmp/android-template-scaffold
android create empty-activity --name="Nubecita" --output=/tmp/android-template-scaffold
```

Expected: `INFO: Successfully created project 'Empty Activity' at '/tmp/android-template-scaffold'`.

- [ ] **Step 2: Copy generated files into the repo**

```bash
cd /Users/velazquez/code/android-template
rsync -a --exclude='.git' --exclude='.gradle' --exclude='build' --exclude='local.properties' /tmp/android-template-scaffold/ .
```

Expected: the repo now contains `app/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradlew`, `gradlew.bat`.

- [ ] **Step 3: Smoke test the build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If the build fails, stop and investigate — every subsequent task assumes the baseline builds.

- [ ] **Step 4: Commit**

```bash
git add app build.gradle.kts settings.gradle.kts gradle.properties gradle gradlew gradlew.bat
git commit -m "feat: baseline android project from \`android create empty-activity\`"
```

---

## Task 2: Add root-level dotfiles

**Files:**
- Create: `.gitignore`, `.editorconfig`, `.gitattributes`, `.java-version`, `.sdkmanrc`.

- [ ] **Step 1: Write `.gitignore`**

```gitignore
# Gradle
.gradle/
build/

# IDE
.idea/
*.iml
.vscode/
.kotlin/

# Local environment
local.properties
*.keystore
keystore.properties

# OS
.DS_Store
Thumbs.db

# Node (for commitlint/commitizen)
node_modules/
npm-debug.log
yarn-debug.log
yarn-error.log

# Python (for scripts/ venv if used)
__pycache__/
*.pyc
.venv/
```

- [ ] **Step 2: Write `.editorconfig`**

```
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4

[*.{kt,kts}]
ktlint_standard_no-wildcard-imports = enabled
ktlint_standard_max-line-length = disabled
ktlint_function_naming_ignore_when_annotated_with = Composable

[*.{yml,yaml,json}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false

[Makefile]
indent_style = tab
```

- [ ] **Step 3: Write `.gitattributes`**

```
* text=auto eol=lf
*.bat text eol=crlf
gradlew text eol=lf
*.png binary
*.jpg binary
*.jpeg binary
*.webp binary
*.keystore binary
```

- [ ] **Step 4: Write `.java-version`**

```
17
```

- [ ] **Step 5: Write `.sdkmanrc`**

```
# Enable auto-env with `sdk env install`
java=17.0.13-zulu
```

- [ ] **Step 6: Commit**

```bash
git add .gitignore .editorconfig .gitattributes .java-version .sdkmanrc
git commit -m "chore: add root dotfiles (gitignore, editorconfig, toolchain pins)"
```

---

## Task 3: Configure `gradle.properties`

**Files:**
- Modify: `gradle.properties`

- [ ] **Step 1: Append performance and caching flags**

Open `gradle.properties` and ensure it contains these lines (add them if missing, leave the `android create` defaults like `android.useAndroidX=true` alone):

```properties
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configuration-cache=true
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError
```

- [ ] **Step 2: Verify the build still succeeds with config-cache on**

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleDebug   # second run to exercise the cache
```

Expected: second run reports `Configuration cache entry reused`.

- [ ] **Step 3: Commit**

```bash
git add gradle.properties
git commit -m "build: enable gradle caching, parallel, and configuration cache"
```

---

## Task 4: Verify Foojay toolchain resolver

**Files:**
- Possibly modify: `settings.gradle.kts`

The current `android create` generator (AGP 9) already emits `id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"` in the `plugins { }` block of `settings.gradle.kts`. This task verifies it's present and at an acceptable version — usually no change is needed.

- [ ] **Step 1: Check `settings.gradle.kts` for the plugin**

```bash
grep -n "foojay-resolver-convention" settings.gradle.kts
```

Expected: one match showing a `version "X.Y.Z"` pin with X.Y.Z >= 0.9.0.

If the plugin is NOT present, add it to the `plugins { }` block:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit only if a change was made**

If the generator already had Foojay (typical case), this task is a no-op verification — skip the commit and note "Task 4: verified Foojay 1.0.0 already present — no change needed" in your report.

If you modified `settings.gradle.kts`:

```bash
git add settings.gradle.kts
git commit -m "build: ensure Foojay toolchain resolver is applied"
```

---

## Task 5: Add Spotless + ktlint + Compose rules

**Files:**
- Modify: `build.gradle.kts` (root)
- Create: `config/spotless/header.txt`

- [ ] **Step 1: Add Spotless plugin to the root build script**

In the root `build.gradle.kts`, add to the `plugins { … }` block:

```kotlin
id("com.diffplug.spotless") version "6.25.0" apply false
```

Then below all existing content, add:

```kotlin
allprojects {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**/*.kt", "**/generated/**/*.kt")
            ktlint("1.4.1").customRuleSets(
                listOf("io.nlopez.compose.rules:ktlint:0.4.16")
            )
            licenseHeaderFile(rootProject.file("config/spotless/header.txt"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.4.1")
        }
    }
}
```

Note: check https://plugins.gradle.org/plugin/com.diffplug.spotless and https://mvnrepository.com/artifact/io.nlopez.compose.rules/ktlint for the latest stable versions at implementation time.

- [ ] **Step 2: Create the (blank) copyright header file**

```bash
mkdir -p config/spotless
```

Create `config/spotless/header.txt` with a single newline — keep it blank initially; derived projects can fill it in:

```

```

(One empty line.) If Spotless rejects an empty header file, use `// ` on the first line instead; remove the `licenseHeaderFile` reference if not needed.

- [ ] **Step 3: Run Spotless — auto-apply first, then verify**

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

Expected: `spotlessCheck` passes (`BUILD SUCCESSFUL`). If `spotlessApply` changed any generated files, that's expected on first run — the `android create` output is not guaranteed to be ktlint-clean.

- [ ] **Step 4: Commit both the config and any auto-fixed files**

```bash
git add build.gradle.kts config/spotless/header.txt app/
git commit -m "build: add Spotless with ktlint 1.4.1 and Compose rules"
```

---

## Task 6: Add Jacoco and a combined test-report task

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the Jacoco plugin and report task to `app/build.gradle.kts`**

At the top, in the `plugins { … }` block, add:

```kotlin
id("jacoco")
```

At the bottom of the file, add:

```kotlin
jacoco {
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*\$Lambda$*.*",
        "**/*\$inlined$*.*",
    )

    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    classDirectories.setFrom(debugTree)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(fileTree(layout.buildDirectory.get()).include("**/*.exec"))
}
```

- [ ] **Step 2: Run the coverage report task**

```bash
./gradlew :app:jacocoTestReport
```

Expected: `BUILD SUCCESSFUL` and `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` exists.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add jacoco coverage with jacocoTestReport task"
```

---

## Task 7: Add `.github/workflows/ci.yaml`

**Files:**
- Create: `.github/workflows/ci.yaml`

- [ ] **Step 1: Write the workflow**

```yaml
name: CI

on:
  workflow_dispatch:
  pull_request:
  push:
    branches: [main]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: write
  pull-requests: write

jobs:
  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - name: Action lint
        uses: open-turo/actions-jvm/lint@v2
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          checkout-repo: true

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version-file: .java-version

      - name: Setup Android SDK
        uses: android-actions/setup-android@v4

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Validate Gradle wrapper
        uses: gradle/actions/wrapper-validation@v6

      - name: Spotless + Android Lint
        run: ./gradlew spotlessCheck lint

  test:
    name: Test
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version-file: .java-version

      - name: Setup Android SDK
        uses: android-actions/setup-android@v4

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Unit tests with coverage
        run: ./gradlew :app:jacocoTestReport

      - name: Coverage report
        if: github.event_name == 'pull_request'
        uses: madrapps/jacoco-report@v1.7.2
        with:
          paths: app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
          token: ${{ secrets.GITHUB_TOKEN }}
          min-coverage-overall: 0
          min-coverage-changed-files: 0
          update-comment: true

  build:
    name: Build
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version-file: .java-version

      - name: Setup Android SDK
        uses: android-actions/setup-android@v4

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Build debug APK
        run: ./gradlew assembleDebug
```

- [ ] **Step 2: Validate with actionlint locally (if installed)**

```bash
actionlint .github/workflows/ci.yaml
```

Expected: no output. If `actionlint` is not installed, skip — the pre-commit hook in Task 11 will catch issues.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yaml
git commit -m "ci: add lint/test/build workflow"
```

---

## Task 8: Add `.github/workflows/release.yaml` and `.releaserc.json`

**Files:**
- Create: `.github/workflows/release.yaml`, `.releaserc.json`

- [ ] **Step 1: Write `.releaserc.json`**

```json
{
    "extends": "@open-turo/semantic-release-config/lib/gradle"
}
```

- [ ] **Step 2: Write `.github/workflows/release.yaml`**

```yaml
name: Release

on:
  workflow_dispatch:
  push:
    branches: [main]

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

permissions:
  contents: write
  issues: write
  pull-requests: write

jobs:
  release:
    name: Release
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0
          persist-credentials: false

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version-file: .java-version

      - name: Setup Android SDK
        uses: android-actions/setup-android@v4

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Release
        uses: open-turo/actions-jvm/release@v2
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}

      # TODO: Firebase App Distribution is deferred to a follow-up design.
      # When enabled, add a step here that uploads the release APK using
      # `wzieba/Firebase-Distribution-Github-Action` or the Firebase CLI.
      # Required secrets: FIREBASE_APP_ID, FIREBASE_SERVICE_ACCOUNT.
```

- [ ] **Step 3: Commit**

```bash
git add .releaserc.json .github/workflows/release.yaml
git commit -m "ci: add semantic-release workflow (firebase deferred)"
```

---

## Task 9: Add commitlint and commitizen configs

**Files:**
- Create: `.commitlintrc.yaml`, `.cz.json`

- [ ] **Step 1: Write `.commitlintrc.yaml`**

```yaml
extends:
  - "@open-turo/commitlint-config-conventional"
```

- [ ] **Step 2: Write `.cz.json`**

```json
{
    "commitizen": {
        "name": "cz_conventional_commits",
        "tag_format": "$version",
        "version_scheme": "semver"
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add .commitlintrc.yaml .cz.json
git commit -m "chore: add commitlint and commitizen configs"
```

---

## Task 10: Add Renovate config

**Files:**
- Create: `.github/renovate.json`

- [ ] **Step 1: Write the Renovate config (ported from slabsnap)**

```json
{
    "$schema": "https://docs.renovatebot.com/renovate-schema.json",
    "extends": [
        "config:recommended",
        "github>open-turo/renovate-config:jvm#v1.17.1"
    ],
    "dependencyDashboard": true,
    "prHourlyLimit": 0,
    "labels": ["dependencies"],
    "rebaseWhen": "conflicted",
    "packageRules": [
        {
            "description": "Group Kotlin, KSP, and Compilers. Exclude kotlinx to avoid unneeded version locks.",
            "matchPackagePatterns": [
                "org.jetbrains.kotlin",
                "com.google.devtools.ksp",
                "androidx.compose.compiler",
                "org.jetbrains.kotlin.plugin.compose"
            ],
            "excludePackagePatterns": ["org.jetbrains.kotlinx"],
            "groupName": "kotlin-ecosystem"
        },
        {
            "description": "Group Compose UI and Foundation libraries.",
            "matchPackagePatterns": [
                "^androidx\\.compose\\.ui",
                "^androidx\\.compose\\.foundation",
                "^androidx\\.compose\\.animation",
                "^androidx\\.compose\\.material",
                "^androidx\\.compose\\.runtime"
            ],
            "groupName": "compose-ui"
        },
        {
            "description": "Provide direct links to Gradle release notes in PRs.",
            "matchPackageNames": ["gradle"],
            "prBodyNotes": "[Changelog](https://docs.gradle.org/{{{newVersion}}}/release-notes.html)"
        },
        {
            "matchManagers": ["gradle"],
            "matchUpdateTypes": ["minor", "patch"],
            "automerge": true,
            "platformAutomerge": true
        }
    ]
}
```

- [ ] **Step 2: Commit**

```bash
git add .github/renovate.json
git commit -m "ci: add renovate config with kotlin and compose groupings"
```

---

## Task 11: Add pre-commit config (including gitleaks)

**Files:**
- Create: `.pre-commit-config.yaml`

- [ ] **Step 1: Write the pre-commit config**

```yaml
# Pre-commit config. Install once with: `pre-commit install && pre-commit install --hook-type commit-msg`
# Run across the whole repo with: `pre-commit run --all-files`

exclude: |
  (?x)^(
    .*/build/.*|
    .*/\.gradle/.*|
    node_modules/.*
  )$

repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v6.0.0
    hooks:
      - id: check-json
      - id: check-yaml
      - id: check-toml
      - id: check-merge-conflict
      - id: check-added-large-files
        args: ["--maxkb=500"]
      - id: end-of-file-fixer
      - id: trailing-whitespace
      - id: mixed-line-ending
        args: ["--fix=lf"]
        exclude: '\.bat$'

  - repo: https://github.com/alessandrojcm/commitlint-pre-commit-hook
    rev: v9.24.0
    hooks:
      - id: commitlint
        stages: [commit-msg]
        additional_dependencies:
          - "@open-turo/commitlint-config-conventional"

  - repo: https://github.com/rhysd/actionlint
    rev: v1.7.12
    hooks:
      - id: actionlint

  - repo: https://github.com/gitleaks/gitleaks
    rev: v8.21.2
    hooks:
      - id: gitleaks

  # Spotless + ktlint runs via the existing Gradle wiring so the config lives
  # in one place (build.gradle.kts). pass_filenames is false because Gradle's
  # spotless task already knows what to lint.
  - repo: local
    hooks:
      - id: spotless
        name: spotless (ktlint)
        entry: ./gradlew spotlessApply
        language: system
        files: '\.(kt|kts)$'
        pass_filenames: false
```

- [ ] **Step 2: Install and run pre-commit**

```bash
pre-commit install
pre-commit install --hook-type commit-msg
pre-commit run --all-files
```

Expected: all hooks pass. If a hook flags issues, fix them and re-run. For gitleaks: it should scan the repo and find no secrets in the template content.

- [ ] **Step 3: Commit**

```bash
git add .pre-commit-config.yaml
# plus any files fixed by the EOF/trailing-whitespace hooks
git commit -m "chore: add pre-commit hooks (commitlint, actionlint, gitleaks, spotless)"
```

---

## Task 12: Add CODEOWNERS, PR template, and issue templates

**Files:**
- Create: `.github/CODEOWNERS`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/ISSUE_TEMPLATE/bug_report.md`, `.github/ISSUE_TEMPLATE/feature_request.md`

- [ ] **Step 1: Write `.github/CODEOWNERS`**

```
# Default reviewer for every path — derived projects should replace @kikin81
# with their own team/handle after running the rename script.
* @kikin81
```

- [ ] **Step 2: Write `.github/PULL_REQUEST_TEMPLATE.md`**

```markdown
## Summary

<!-- What does this PR change and why? -->

## Test plan

- [ ] Unit tests updated / added
- [ ] `./gradlew spotlessCheck lint testDebugUnitTest assembleDebug` passes locally
- [ ] Manual smoke test on a device / emulator (if applicable)

## Screenshots / recordings

<!-- If UI changed, attach before/after. Otherwise delete this section. -->
```

- [ ] **Step 3: Write `.github/ISSUE_TEMPLATE/bug_report.md`**

```markdown
---
name: Bug report
about: Report something that isn't working
labels: ["bug"]
---

**Describe the bug**

**Steps to reproduce**
1.
2.
3.

**Expected behavior**

**Screenshots**

**Environment**
- App version:
- Android version / device:
```

- [ ] **Step 4: Write `.github/ISSUE_TEMPLATE/feature_request.md`**

```markdown
---
name: Feature request
about: Suggest an idea
labels: ["enhancement"]
---

**What problem are you trying to solve?**

**What have you considered?**

**Additional context**
```

- [ ] **Step 5: Commit**

```bash
git add .github/CODEOWNERS .github/PULL_REQUEST_TEMPLATE.md .github/ISSUE_TEMPLATE/
git commit -m "chore: add CODEOWNERS, PR template, issue templates"
```

---

## Task 13: Write the rename script — argument parsing and pre-flight

**Files:**
- Create: `scripts/rename.py`
- Create: `scripts/test_rename.py`

This is the first task for the rename script. We work TDD: write a test case that asserts argument parsing and pre-flight behavior, watch it fail, then implement.

The self-test uses only the Python stdlib (`unittest`, `subprocess`, `tempfile`, `shutil`, `pathlib`) so no extra install step is required.

- [ ] **Step 1: Create `scripts/test_rename.py` with the first test**

```python
"""Self-test for scripts/rename.py.

Runs against a fresh `android create empty-activity` output in a tmpdir and
asserts the rename pipeline produces the expected state. Use:

    python -m unittest scripts/test_rename.py

Set RUN_FULL_BUILD=1 to also run `./gradlew :app:assembleDebug` after the
rename; CI always sets this.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RENAME_SCRIPT = REPO_ROOT / "scripts" / "rename.py"


def _scaffold(dest: Path) -> None:
    """Generate a fresh `android create` project at `dest`."""
    dest.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["android", "create", "empty-activity", "--name=Nubecita", f"--output={dest}"],
        check=True,
    )
    shutil.copy2(RENAME_SCRIPT, dest / "rename.py")


def _run_rename(project: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(project / "rename.py"), *args],
        cwd=project,
        capture_output=True,
        text=True,
    )


class RenameCliTest(unittest.TestCase):
    def test_rejects_invalid_package(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "Com.Bad.Case",   # uppercase not allowed
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertNotEqual(result.returncode, 0, msg=result.stderr)
            self.assertIn("--package", result.stderr)

    def test_preflight_fails_when_no_template_markers(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            project.mkdir()
            shutil.copy2(RENAME_SCRIPT, project / "rename.py")
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertNotEqual(result.returncode, 0, msg=result.stderr)
            self.assertIn("net.kikin.nubecita", result.stderr)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Create an empty `scripts/rename.py` so the tests can find it**

```bash
mkdir -p scripts
cat > scripts/rename.py <<'PY'
#!/usr/bin/env python3
"""One-shot rename script for the android-template. Implementation in progress."""
import sys
sys.exit("rename.py not yet implemented")
PY
chmod +x scripts/rename.py
```

- [ ] **Step 3: Run the tests — expect failure**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: both tests fail. `test_rejects_invalid_package` fails because the stub exits `1` but without `--package` in stderr; `test_preflight_fails_when_no_template_markers` fails for the same reason.

- [ ] **Step 4: Implement CLI parsing + pre-flight in `scripts/rename.py`**

```python
#!/usr/bin/env python3
"""One-shot rename script for the android-template.

Rewrites the placeholder package (`net.kikin.nubecita`) and app-name variants
(`Nubecita` / `Nubecita` / `nubecita`) across the repo produced by
`android create empty-activity`. Intended to be run exactly once, immediately
after cloning from the template.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

# Placeholders baked into the `android create empty-activity` output.
# Note: `Nubecita` is the actual class/theme prefix the generator emits (e.g.
# `NubecitaTheme`, `Theme.Nubecita`); bare `Nubecita` does not appear in
# current output but is kept as a defensive fallback. `Nubecita` must be
# substituted BEFORE `Nubecita` (longest-match-first) or `NubecitaTheme` would
# become `{pascal}licationTheme`.
OLD_PACKAGE_DOTTED = "net.kikin.nubecita"
OLD_PACKAGE_SLASHED = "net/kikin/nubecita"
OLD_APP_NAME = "Nubecita"
OLD_APPLICATION = "Nubecita"
OLD_PASCAL = "Nubecita"
OLD_LOWER = "nubecita"

PACKAGE_RE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
PASCAL_RE = re.compile(r"^[A-Z][A-Za-z0-9]*$")
LOWER_RE = re.compile(r"^[a-z][a-z0-9]*$")


def _parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rename the template's placeholder identifiers in one shot.",
    )
    parser.add_argument("--package", required=True, help="e.g. com.acme.widget")
    parser.add_argument("--app-name", required=True, help='e.g. "Acme Widget"')
    parser.add_argument("--app-name-pascal", required=True, help="e.g. AcmeWidget")
    parser.add_argument("--app-name-lower", required=True, help="e.g. acmewidget")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--keep-script", action="store_true")
    args = parser.parse_args(argv)

    if not PACKAGE_RE.match(args.package):
        parser.error("--package must be a dotted lowercase Java package (e.g. com.acme.widget)")
    if not args.app_name.strip():
        parser.error("--app-name must be non-empty")
    if not PASCAL_RE.match(args.app_name_pascal):
        parser.error("--app-name-pascal must start with uppercase letter, then alphanumerics only")
    if not LOWER_RE.match(args.app_name_lower):
        parser.error("--app-name-lower must start with lowercase letter, then lowercase alphanumerics only")

    return args


def _preflight(repo: Path) -> None:
    java_root = repo / "app" / "src" / "main" / "java" / "com" / "example" / "nubecita"
    if not java_root.is_dir():
        raise SystemExit(
            f"pre-flight failed: expected directory {java_root} "
            f"(no net.kikin.nubecita template markers — already renamed?)"
        )


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    repo = Path.cwd()
    _preflight(repo)
    # Directory move + file rewrites happen in later tasks.
    raise SystemExit("rename.py: cli + preflight only so far — further tasks pending")


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Re-run the tests — expect both to pass**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: both tests pass.

- [ ] **Step 6: Commit**

```bash
git add scripts/rename.py scripts/test_rename.py
git commit -m "feat(scripts): rename.py skeleton with CLI validation and preflight"
```

---

## Task 14: Rename script — directory moves

**Files:**
- Modify: `scripts/rename.py`
- Modify: `scripts/test_rename.py`

- [ ] **Step 1: Add the directory-move test**

Append to `scripts/test_rename.py`:

```python
class RenameDirectoriesTest(unittest.TestCase):
    def test_moves_java_dirs_to_new_package(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            for sub in ("main", "test", "androidTest"):
                old = project / "app" / "src" / sub / "java" / "com" / "example" / "nubecita"
                new = project / "app" / "src" / sub / "java" / "com" / "acme" / "widget"
                self.assertFalse(old.exists(), f"{old} should have been removed")
                self.assertTrue(new.is_dir(), f"{new} should exist")
```

- [ ] **Step 2: Run the tests — expect the new one to fail**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: new test fails because `main()` still raises `SystemExit`; the two earlier tests still pass.

- [ ] **Step 3: Implement `_move_dirs` in `scripts/rename.py`**

Replace the `main()` body in `scripts/rename.py` so it calls a new `_move_dirs` helper. Add `_move_dirs` above `main`:

```python
def _move_dirs(repo: Path, package: str) -> None:
    new_parts = package.split(".")
    for sub in ("main", "test", "androidTest"):
        old_root = repo / "app" / "src" / sub / "java" / "com" / "example" / "nubecita"
        if not old_root.exists():
            continue
        new_root = repo / "app" / "src" / sub / "java" / Path(*new_parts)
        new_root.parent.mkdir(parents=True, exist_ok=True)
        old_root.rename(new_root)
    # Clean up now-empty `com/example/` stub parents.
    for sub in ("main", "test", "androidTest"):
        stub = repo / "app" / "src" / sub / "java" / "com" / "example"
        if stub.exists() and not any(stub.iterdir()):
            stub.rmdir()
            parent = stub.parent
            if parent.name == "com" and parent.exists() and not any(parent.iterdir()):
                parent.rmdir()
```

Update `main()` to call it and return 0 for now (no rewrite yet):

```python
def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    repo = Path.cwd()
    _preflight(repo)
    _move_dirs(repo, args.package)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: Run the tests — expect all three to pass**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: 3/3 pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/rename.py scripts/test_rename.py
git commit -m "feat(scripts): move java directory trees to new package path"
```

---

## Task 15: Rename script — file content substitutions

**Files:**
- Modify: `scripts/rename.py`
- Modify: `scripts/test_rename.py`

- [ ] **Step 1: Add the content-rewrite test**

Append to `scripts/test_rename.py`:

```python
class RenameContentTest(unittest.TestCase):
    def test_rewrites_placeholders_across_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            # No residual placeholders anywhere.
            for dirpath, _dirs, files in os.walk(project):
                if any(skip in dirpath for skip in (".gradle", "build", ".idea")):
                    continue
                for name in files:
                    if name == "rename.py":
                        continue
                    path = Path(dirpath) / name
                    try:
                        text = path.read_text(encoding="utf-8")
                    except UnicodeDecodeError:
                        continue
                    for needle in (
                        "net.kikin.nubecita",
                        "net/kikin/nubecita",
                        "Nubecita",
                        "Nubecita",
                        "Nubecita",
                        "nubecita",
                    ):
                        self.assertNotIn(needle, text, f"{needle!r} still in {path}")

            # Spot-check key files have the new identifiers.
            build_gradle = (project / "app" / "build.gradle.kts").read_text()
            self.assertIn('namespace = "com.acme.widget"', build_gradle)
            self.assertIn('applicationId = "com.acme.widget"', build_gradle)

            settings_gradle = (project / "settings.gradle.kts").read_text()
            self.assertIn('rootProject.name = "Acme Widget"', settings_gradle)

            strings_xml = (project / "app" / "src" / "main" / "res" / "values" / "strings.xml").read_text()
            self.assertIn(">Acme Widget<", strings_xml)

            # Theme-name substitution must produce `AcmeWidgetTheme`, not
            # `AcmeWidgetlicationTheme` (regression guard for Nubecita order).
            theme_kt = (project / "app" / "src" / "main" / "java" / "com" / "acme" / "widget" / "theme" / "Theme.kt").read_text()
            self.assertIn("AcmeWidgetTheme", theme_kt)
            self.assertNotIn("AcmeWidgetlication", theme_kt)
```

- [ ] **Step 2: Run — expect the new test to fail**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: the new test fails (placeholders still present in content).

- [ ] **Step 3: Implement `_rewrite_files` in `scripts/rename.py`**

Add this helper above `main`:

```python
REWRITE_SUFFIXES = {".kt", ".kts", ".xml", ".md", ".properties", ".toml"}
SKIP_DIR_NAMES = {".git", ".gradle", ".idea", "build", "node_modules"}
SELF_NAME = "rename.py"


def _rewrite_files(repo: Path, args: argparse.Namespace) -> None:
    dotted_new = args.package
    slashed_new = "/".join(args.package.split("."))
    # Order matters: longest/most-specific patterns first. `Nubecita` must
    # come before `Nubecita` so `NubecitaTheme` doesn't mangle into
    # `{pascal}licationTheme`.
    substitutions = [
        (OLD_PACKAGE_DOTTED, dotted_new),
        (OLD_PACKAGE_SLASHED, slashed_new),
        (OLD_APP_NAME, args.app_name),
        (OLD_APPLICATION, args.app_name_pascal),
        (OLD_PASCAL, args.app_name_pascal),
        (OLD_LOWER, args.app_name_lower),
    ]
    for dirpath, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIR_NAMES]
        for name in files:
            if name == SELF_NAME:
                continue
            path = Path(dirpath) / name
            if path.suffix not in REWRITE_SUFFIXES:
                continue
            try:
                original = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            updated = original
            for old, new in substitutions:
                updated = updated.replace(old, new)
            if updated != original:
                path.write_text(updated, encoding="utf-8")
```

Update `main()` to call it:

```python
def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    repo = Path.cwd()
    _preflight(repo)
    _move_dirs(repo, args.package)
    _rewrite_files(repo, args)
    return 0
```

- [ ] **Step 4: Run — expect all four tests to pass**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: 4/4 pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/rename.py scripts/test_rename.py
git commit -m "feat(scripts): rewrite placeholder strings across repo files"
```

---

## Task 16: Rename script — post-rename checklist + self-delete

**Files:**
- Modify: `scripts/rename.py`
- Modify: `scripts/test_rename.py`

- [ ] **Step 1: Add the test**

Append to `scripts/test_rename.py`:

```python
class RenameFinalizationTest(unittest.TestCase):
    def test_self_deletes_and_prints_checklist(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            self.assertFalse((project / "rename.py").exists(), "rename.py should self-delete")
            self.assertIn("Next steps", result.stdout)
            self.assertIn("git remote add origin", result.stdout)

    def test_keep_script_flag(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
                "--keep-script",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            self.assertTrue((project / "rename.py").exists(), "rename.py should remain with --keep-script")
```

- [ ] **Step 2: Run — expect the two new tests to fail**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: `test_self_deletes_and_prints_checklist` and `test_keep_script_flag` fail. Existing 4 still pass.

- [ ] **Step 3: Implement `_finalize` in `scripts/rename.py`**

Add above `main`:

```python
CHECKLIST = """
Next steps (complete these manually):

  1. Set the git remote:
       git remote add origin git@github.com:<you>/<repo>.git
  2. Configure branch protection on main: required checks (lint, test, build),
     require PR before merge, linear history, squash-merge only.
  3. Enable auto-delete of head branches after merge.
  4. Enable Renovate on the repository (renovate.json is committed).
  5. Add any release secrets required by open-turo/actions-jvm/release
     (see .github/workflows/release.yaml).
  6. Replace the placeholder icon in app/src/main/res/mipmap-*/ with your own.
  7. Update the README title/description for your project.
"""


def _finalize(repo: Path, keep_script: bool) -> None:
    print(CHECKLIST)
    if keep_script:
        return
    for name in ("rename.py", "test_rename.py"):
        target = repo / "scripts" / name
        if target.exists():
            target.unlink()
    # rename.py invoked from the repo root also self-deletes that copy.
    root_copy = repo / "rename.py"
    if root_copy.exists():
        root_copy.unlink()
```

Update `main()`:

```python
def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    repo = Path.cwd()
    _preflight(repo)
    _move_dirs(repo, args.package)
    _rewrite_files(repo, args)
    _finalize(repo, keep_script=args.keep_script)
    return 0
```

- [ ] **Step 4: Run — expect 6/6 pass**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: 6/6 pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/rename.py scripts/test_rename.py
git commit -m "feat(scripts): print checklist and self-delete rename.py"
```

---

## Task 17: Rename script — dry-run mode

**Files:**
- Modify: `scripts/rename.py`
- Modify: `scripts/test_rename.py`

- [ ] **Step 1: Add the test**

Append to `scripts/test_rename.py`:

```python
class RenameDryRunTest(unittest.TestCase):
    def test_dry_run_makes_no_changes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            before = {
                str(p.relative_to(project)): p.read_bytes()
                for p in project.rglob("*")
                if p.is_file()
            }
            result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
                "--dry-run",
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)
            self.assertIn("DRY RUN", result.stdout)
            after = {
                str(p.relative_to(project)): p.read_bytes()
                for p in project.rglob("*")
                if p.is_file()
            }
            self.assertEqual(before, after, "dry run must not modify the filesystem")
```

- [ ] **Step 2: Run — expect this test to fail**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: new test fails (current implementation always mutates).

- [ ] **Step 3: Thread `dry_run` through the pipeline**

Change `_move_dirs` signature to `def _move_dirs(repo: Path, package: str, dry_run: bool = False) -> None` and guard the `rename` + `rmdir` calls:

```python
def _move_dirs(repo: Path, package: str, dry_run: bool = False) -> None:
    new_parts = package.split(".")
    for sub in ("main", "test", "androidTest"):
        old_root = repo / "app" / "src" / sub / "java" / "com" / "example" / "nubecita"
        if not old_root.exists():
            continue
        new_root = repo / "app" / "src" / sub / "java" / Path(*new_parts)
        if dry_run:
            print(f"[dry-run] would move {old_root} -> {new_root}")
            continue
        new_root.parent.mkdir(parents=True, exist_ok=True)
        old_root.rename(new_root)
    if dry_run:
        return
    for sub in ("main", "test", "androidTest"):
        stub = repo / "app" / "src" / sub / "java" / "com" / "example"
        if stub.exists() and not any(stub.iterdir()):
            stub.rmdir()
            parent = stub.parent
            if parent.name == "com" and parent.exists() and not any(parent.iterdir()):
                parent.rmdir()
```

Change `_rewrite_files` to optionally print a short diff summary and skip writes in dry-run:

```python
def _rewrite_files(repo: Path, args: argparse.Namespace, dry_run: bool = False) -> None:
    dotted_new = args.package
    slashed_new = "/".join(args.package.split("."))
    # Order matters: longest/most-specific patterns first (see Task 15 for details).
    substitutions = [
        (OLD_PACKAGE_DOTTED, dotted_new),
        (OLD_PACKAGE_SLASHED, slashed_new),
        (OLD_APP_NAME, args.app_name),
        (OLD_APPLICATION, args.app_name_pascal),
        (OLD_PASCAL, args.app_name_pascal),
        (OLD_LOWER, args.app_name_lower),
    ]
    for dirpath, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIR_NAMES]
        for name in files:
            if name == SELF_NAME:
                continue
            path = Path(dirpath) / name
            if path.suffix not in REWRITE_SUFFIXES:
                continue
            try:
                original = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            updated = original
            for old, new in substitutions:
                updated = updated.replace(old, new)
            if updated == original:
                continue
            if dry_run:
                print(f"[dry-run] would rewrite {path.relative_to(repo)}")
                continue
            path.write_text(updated, encoding="utf-8")
```

Update `main`:

```python
def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    repo = Path.cwd()
    _preflight(repo)
    if args.dry_run:
        print("DRY RUN — no changes will be written.")
    _move_dirs(repo, args.package, dry_run=args.dry_run)
    _rewrite_files(repo, args, dry_run=args.dry_run)
    if args.dry_run:
        print("DRY RUN complete.")
        return 0
    _finalize(repo, keep_script=args.keep_script)
    return 0
```

- [ ] **Step 4: Run — expect 7/7 pass**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: 7/7 pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/rename.py scripts/test_rename.py
git commit -m "feat(scripts): add --dry-run mode for rename.py"
```

---

## Task 18: Rename script — package-substring collision fixture

**Files:**
- Modify: `scripts/test_rename.py`

No implementation change expected — the ordering in `_rewrite_files` already handles this — but we pin it with a regression test so future edits can't break it.

- [ ] **Step 1: Add the collision test**

Append to `scripts/test_rename.py`:

```python
class RenameCollisionTest(unittest.TestCase):
    def test_lower_app_name_substring_of_new_package(self) -> None:
        """New package contains the literal 'nubecita' substring — verify no corruption."""
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            result = _run_rename(
                project,
                "--package", "com.foo.nubecita",    # ends in 'nubecita'
                "--app-name", "Foo Nubecita Project",
                "--app-name-pascal", "FooNubecita",
                "--app-name-lower", "nubecita",     # same as old lower
            )
            self.assertEqual(result.returncode, 0, msg=result.stderr)

            build_gradle = (project / "app" / "build.gradle.kts").read_text()
            self.assertIn('namespace = "com.foo.nubecita"', build_gradle)

            # The new package path directory exists correctly.
            self.assertTrue((project / "app" / "src" / "main" / "java" / "com" / "foo" / "nubecita").is_dir())
```

- [ ] **Step 2: Run — expect all 8 tests pass**

```bash
python3 -m unittest scripts/test_rename.py -v
```

Expected: 8/8 pass. If it fails, investigate the substitution order in `_rewrite_files` — do not change the test to match the implementation.

- [ ] **Step 3: Commit**

```bash
git add scripts/test_rename.py
git commit -m "test(scripts): regression test for lower/package substring collision"
```

---

## Task 19: Rename script — full-build self-test behind env gate

**Files:**
- Modify: `scripts/test_rename.py`

- [ ] **Step 1: Add the build-after-rename test**

Append to `scripts/test_rename.py`:

```python
@unittest.skipUnless(os.environ.get("RUN_FULL_BUILD") == "1", "set RUN_FULL_BUILD=1 to run")
class RenameFullBuildTest(unittest.TestCase):
    def test_assemble_debug_after_rename(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "proj"
            _scaffold(project)
            rename_result = _run_rename(
                project,
                "--package", "com.acme.widget",
                "--app-name", "Acme Widget",
                "--app-name-pascal", "AcmeWidget",
                "--app-name-lower", "acmewidget",
            )
            self.assertEqual(rename_result.returncode, 0, msg=rename_result.stderr)

            gradle_result = subprocess.run(
                ["./gradlew", ":app:assembleDebug", "--no-daemon"],
                cwd=project,
                capture_output=True,
                text=True,
            )
            self.assertEqual(gradle_result.returncode, 0, msg=gradle_result.stderr)
```

- [ ] **Step 2: Run locally with the gate on to confirm it actually passes**

```bash
RUN_FULL_BUILD=1 python3 -m unittest scripts/test_rename.py -v
```

Expected: 9/9 pass. This takes several minutes the first time because Gradle downloads dependencies.

- [ ] **Step 3: Commit**

```bash
git add scripts/test_rename.py
git commit -m "test(scripts): add full assembleDebug check gated by RUN_FULL_BUILD"
```

---

## Task 20: Wire the rename self-test into CI

**Files:**
- Modify: `.github/workflows/ci.yaml`

- [ ] **Step 1: Add a `rename-script` job**

Append to the `jobs:` block in `.github/workflows/ci.yaml`:

```yaml
  rename-script:
    name: Rename script
    runs-on: ubuntu-latest
    env:
      RUN_FULL_BUILD: "1"
    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: "3.12"

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version-file: .java-version

      - name: Setup Android SDK
        uses: android-actions/setup-android@v4

      - name: Install android CLI
        run: |
          echo "TODO: the android CLI isn't on the standard runner — either replace this"
          echo "step with a published action or vendor the minimum artifacts needed."
          echo "For now, skip the rename-script job when the CLI is unavailable."
          which android || exit 0

      - name: Run rename self-test
        run: python3 -m unittest scripts/test_rename.py -v
```

The `android` CLI is not installed on GitHub-hosted runners by default; if the publicly available setup is still TBD at the time this task runs, leave the graceful-skip in place and open an issue to revisit. Do not fake the test by removing the scaffold step.

- [ ] **Step 2: Validate with actionlint**

```bash
actionlint .github/workflows/ci.yaml
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yaml
git commit -m "ci: run rename self-test (graceful-skip when android CLI unavailable)"
```

---

## Task 21: Write README, CONTRIBUTING, LICENSE, CLAUDE.md

**Files:**
- Create: `README.md`, `CONTRIBUTING.md`, `LICENSE`, `CLAUDE.md`

- [ ] **Step 1: Write `README.md`**

```markdown
# Android Template

Private GitHub template for bootstrapping Android apps with modern defaults (AGP 9, Compose, Kotlin DSL, version catalog) plus CI, release, pre-commit, and a one-shot rename script.

## Using the template

1. Click **Use this template** on GitHub to create your new repo.
2. Clone it locally.
3. Run the rename script:

   ```bash
   python3 scripts/rename.py \
     --package com.yourdomain.yourapp \
     --app-name "Your App" \
     --app-name-pascal YourApp \
     --app-name-lower yourapp
   ```

   The script self-deletes after success. Pass `--dry-run` first to preview changes, or `--keep-script` if you need to re-run.

4. Commit the rename:

   ```bash
   git add -A
   git commit -m "chore: rename template placeholders"
   git push
   ```

5. Follow the post-rename checklist the script prints:

   - Configure branch protection on `main`: require `lint`, `test`, `build` checks; require PR; squash-merge only; linear history; auto-delete branches.
   - Enable Renovate on the new repo.
   - Update `.github/CODEOWNERS` reviewers.
   - Replace the launcher icon in `app/src/main/res/mipmap-*`.
   - Configure release secrets (see `.github/workflows/release.yaml`).

## Requirements

- JDK 17 (tracked by `.java-version`, see `.sdkmanrc`)
- Android SDK (API 36+)
- Python 3.10+ (for the rename script; stdlib only, no pip install)
- `pre-commit` and `commitizen` (recommended: `brew install pre-commit commitizen`)

## Local development

```bash
./gradlew :app:assembleDebug     # build
./gradlew testDebugUnitTest      # tests
./gradlew spotlessCheck lint     # lint
pre-commit run --all-files       # run all hooks
```

## What the template gives you

- AGP 9, Compose, Kotlin DSL, version catalog (`gradle/libs.versions.toml`).
- Spotless + ktlint 1.4.1 + Compose rules.
- Jacoco coverage with PR comment.
- CI workflow (`lint`, `test`, `build`, `rename-script`).
- Release workflow via semantic-release (`@open-turo/semantic-release-config/lib/gradle`).
- Renovate config with Kotlin/Compose groupings.
- Pre-commit hooks: commitlint, actionlint, gitleaks, spotless.
- PR + issue templates, CODEOWNERS.

## What's deferred

- Firebase App Distribution (requires per-project Firebase setup).
- Multi-module `build-logic/` convention plugins.
- Hilt / Ktor / Coil / any runtime library pre-wiring.
```

- [ ] **Step 2: Write `CONTRIBUTING.md`**

```markdown
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
```

- [ ] **Step 3: Write `LICENSE`**

Use MIT (private template, but the license placeholder keeps derived projects clean). Copy the standard MIT license text from https://choosealicense.com/licenses/mit/ with the copyright line:

```
MIT License

Copyright (c) 2026 Francisco Velazquez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 4: Write `CLAUDE.md`**

```markdown
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
```

- [ ] **Step 5: Commit**

```bash
git add README.md CONTRIBUTING.md LICENSE CLAUDE.md
git commit -m "docs: add README, CONTRIBUTING, LICENSE, CLAUDE.md"
```

---

## Task 22: Final verification — green baseline

**Files:** none (verification only)

- [ ] **Step 1: Run every local check in order**

```bash
pre-commit run --all-files
./gradlew spotlessCheck lint
./gradlew testDebugUnitTest
./gradlew :app:jacocoTestReport
./gradlew :app:assembleDebug
python3 -m unittest scripts/test_rename.py -v
RUN_FULL_BUILD=1 python3 -m unittest scripts/test_rename.py -v
```

Expected: every command exits 0. If any fails, fix the root cause rather than skipping the step.

- [ ] **Step 2: Confirm git status is clean**

```bash
git status
```

Expected: `nothing to commit, working tree clean`. If there are uncommitted changes, investigate — the hooks may have auto-fixed something that needs to be committed.

- [ ] **Step 3: Review the branch**

```bash
git log --oneline main..plan/initial
```

Expected: one commit per task (22 commits), each with a Conventional Commit message.

- [ ] **Step 4: No commit here** — this is a checkpoint task. The next action is either merging `plan/initial` into `main` or pushing to a newly created remote. See the post-implementation checklist below.

---

## Post-implementation checklist (manual, not automatable)

After the 22 tasks above, these remaining actions require a human + GitHub UI:

1. Create the GitHub repo (private): `gh repo create kikin81/android-template --private --source=. --remote=origin --push`.
2. Toggle **Template repository** in the repo's Settings.
3. Set up branch protection on `main`: required checks `lint`, `test`, `build`, `rename-script`; require PR; require linear history; squash-merge only; auto-delete branches.
4. Confirm the initial CI run is green.
5. Use the template once end-to-end on a throwaway repo and run the rename script to catch anything the self-test missed.
