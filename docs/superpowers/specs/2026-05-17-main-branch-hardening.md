# Main Branch Hardening — Spec & Operator Runbook

> **Scope:** Lock down `main` so CI must pass before merge, the release path runs in an isolated environment, and fork-PR workflows can't trigger signing in the future. Most steps are GitHub Settings UI changes the repo owner has to apply by hand; this doc is the paste-ready runbook plus the in-repo PR companion (CODEOWNERS verification + three workflow fixes).
>
> **Trigger:** Security audit of `kikin81/nubecita` immediately before adding the upload keystore for Firebase App Distribution signing.

## Current state (audit, 2026-05-17)

- **Classic branch protection on `main`:** disabled (`/branches/main/protection` returns 404).
- **Repository ruleset "Copilot review for default branch":** active, with `non_fast_forward`, `deletion`, `copilot_code_review`, `required_linear_history` rules; `bypass_actors: []`. Force-pushes to `main` are already blocked.
- **Required status checks:** ❌ not enforced. PRs can be merged regardless of CI state.
- **Require PR for direct pushes:** ❌ not enforced. `release.yaml` pushes version-bump commits straight to `main` via `secrets.GITHUB_TOKEN` and that's fine today; will need a bypass actor if we ever add the `pull_request` rule.
- **Workflow permissions:** the `lint` job in `ci.yaml` had `contents: write` + `pull-requests: write` without needing either. The `release.yaml` step that decoded the Firebase service account inlined `${{ secrets.FIREBASE_SERVICE_ACCOUNT }}` directly into a shell command. The `update-screenshot-baselines.yaml` workflow's `if:` didn't gate on same-repo PRs.
- **Secrets in use:** `GITHUB_TOKEN` (built-in) and `FIREBASE_SERVICE_ACCOUNT` (repository secret). The earlier audit-list item about `RELEASE_PAT` was a false flag — no PAT is referenced anywhere in `.github/workflows/`.
- **`CODEOWNERS`:** already exists at `.github/CODEOWNERS` with `* @kikin81`. Satisfies the "require code-owner review" prerequisite; no PR-side change needed.

## Companion PR scope (this PR)

Three workflow hardening fixes that don't need Settings-UI changes:

1. **`ci.yaml`** — drop the `lint` job's `permissions:` block from `contents: write` + `pull-requests: write` to `contents: read`. The job only runs `./gradlew spotlessCheck lint :app:checkSortDependencies`; no writes happen.
2. **`release.yaml`** — replace the inline `${{ secrets.FIREBASE_SERVICE_ACCOUNT }}` interpolation in the "Decode service account key" step with an `env:` block + `echo "$FIREBASE_SA" | base64 -d`. The previous shape was vulnerable to shell-active characters in the secret value.
3. **`update-screenshot-baselines.yaml`** — add `github.event.pull_request.head.repo.full_name == github.repository` to the job's `if:` so fork PRs can't trigger the regenerate-baselines workflow. Fork PRs from outside collaborators currently land with a read-only token, so exfiltration is bounded — but the rule is "deny by default, allow explicitly," and the screenshot job runs Gradle code from the PR head.

## Settings UI runbook (operator's hands-on work)

### 🚨 Critical — apply before adding the keystore

#### 1. Extend the existing ruleset with PR + status-check rules

Settings → Code & automation → Rules → **Copilot review for default branch** → Edit.

Add these rules to the existing set (`non_fast_forward`, `deletion`, `copilot_code_review`, `required_linear_history` stay):

- **Require pull requests before merging**
  - Required approvals: **1**
  - Dismiss stale approvals on new commits: **on**
  - Require review from Code Owners: **on**
  - Require conversation resolution before merging: **on**
- **Require status checks to pass before merging**
  - Required checks (add each by name from a recent green PR):
    - `Build`
    - `Test`
    - `Lint`
    - `Screenshot tests`
    - `Instrumented tests`
  - Require branches to be up to date before merging: **off** (squash-merge handles this; flipping it on adds rebase friction).

Add bypass actor:
- **`Repository admin`** *(yourself)* — for the cases where you legitimately need to push a fix without a PR (rare).
- **`Deploy keys: github-actions[bot]`** — so the existing `release.yaml` semantic-release push to `main` keeps working. Without this, the version-bump commit fails the "Require pull requests" rule.

#### 2. Create a `release` deployment environment

Settings → Environments → **New environment** → name `release`.

- **Deployment branches and tags:** "Selected branches and tags" → add `main` (or restrict further to tags matching `v*` if you adopt tag-driven releases).
- **Required reviewers:** add yourself (manual gate before any signing operation).
- **Wait timer:** 0 minutes (optional buffer if you want a cooldown).
- **Environment secrets:**
  - Move `FIREBASE_SERVICE_ACCOUNT` here (delete from repo-level secrets).
  - When the upload keystore lands, add `RELEASE_KEYSTORE_BASE64` + `RELEASE_KEYSTORE_PASSWORD` + `RELEASE_KEY_ALIAS` + `RELEASE_KEY_PASSWORD` to this environment, never to repo-level secrets.

Then patch `release.yaml`'s `distribute` job:

```yaml
distribute:
  name: Distribute Debug APK
  needs: release
  if: needs.release.outputs.new-release-published == 'true'
  runs-on: ubuntu-latest
  environment: release   # <— add this
  steps:
    ...
```

Once the environment exists, every workflow run that reaches the `distribute` job blocks for your manual approval before signing keys are accessible.

#### 3. Fork PR workflow approval gate

Settings → Actions → General → **Fork pull request workflows from outside collaborators** → set to **"Require approval for all outside collaborators"** (or at minimum, **"Require approval for first-time contributors"**).

This is the canonical control that prevents a fork PR from triggering any workflow — including future signing workflows — without an explicit approve click. The fork-PR guard inside `update-screenshot-baselines.yaml` (item 7 in this PR) is defense-in-depth on top of this setting.

### ⚠️ Medium — schedule before public beta

#### 4. SHA-pin third-party actions

Replace floating-tag references like `open-turo/actions-jvm/release@v2` with full commit SHAs. A compromised maintainer of any third-party action can force-push the `v2` tag to point at malicious code, and that code runs with the workflow's permissions on every release. SHA-pinning makes the action's bytecode immutable.

Affected workflows:
- `ci.yaml`: `open-turo/actions-jvm/lint@v2`, `actions/setup-java@v5`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6`, `gradle/actions/wrapper-validation@v6`, plus checkout/upload-artifact actions in other jobs.
- `release.yaml`: `actions/checkout@v6`, `actions/setup-java@v5`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6`, `open-turo/actions-jvm/release@v2`.
- `update-screenshot-baselines.yaml`: `actions/checkout@v6`, `actions/setup-java@v5`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6`, `actions/github-script@v9`.

Best done as a single follow-up PR; Dependabot's `gha` ecosystem keeps the SHAs current.

### 🔵 Low — opportunistic

#### 5. `kikin81/nubecita-web` ruleset

The static-site repo has no branch protection at all. Lower stakes (no secrets, no signing), but add a minimal ruleset for parity:

- `non_fast_forward` (block force-push)
- `deletion` (block branch delete)
- `required_linear_history`
- No status checks (the site has no CI yet)

5-minute Settings UI step on `kikin81/nubecita-web` whenever convenient.

## Acceptance

- This PR's three workflow changes pass CI on the new ruleset (the workflow files compile + the jobs they define still run green).
- Operator has applied steps 1–3 in the runbook before adding the upload keystore.
- A test PR raised against `main` cannot be merged when any of the five required checks is failing.
- The `distribute` job in `release.yaml`, after step 2's environment gate, blocks on manual approval before accessing `FIREBASE_SERVICE_ACCOUNT`.
- A fork PR from an outside collaborator does not trigger any workflow without explicit approval (verified by raising a fork PR and confirming the "approve and run workflows" prompt appears).
