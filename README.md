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
