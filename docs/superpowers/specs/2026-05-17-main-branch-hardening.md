# Main Branch Hardening — Spec & Operator Runbook

> **Scope:** Lock down `main` so CI must pass before merge, the release path runs in an isolated environment, and fork-PR workflows can't trigger signing in the future. Most steps are GitHub Settings UI changes the repo owner has to apply by hand; this doc is the paste-ready runbook plus the in-repo PR companion (CODEOWNERS verification + two workflow fixes).
>
> **Trigger:** Security audit of `kikin81/nubecita` immediately before adding the upload keystore for Firebase App Distribution signing.

## Current state (audit, 2026-05-17)

- **Classic branch protection on `main`:** disabled (`/branches/main/protection` returns 404).
- **Repository ruleset "Copilot review for default branch":** active, with `non_fast_forward`, `deletion`, `copilot_code_review`, `required_linear_history` rules; `bypass_actors: []`. Force-pushes to `main` are already blocked.
- **Required status checks:** ❌ not enforced. PRs can be merged regardless of CI state.
- **Require PR for direct pushes:** ❌ not enforced. `release.yaml` pushes version-bump commits straight to `main` via `secrets.GITHUB_TOKEN` and that's fine today; will need a bypass actor if we ever add the `pull_request` rule.
- **Workflow permissions:** the `release.yaml` step that decoded the Firebase service account inlined `${{ secrets.FIREBASE_SERVICE_ACCOUNT }}` directly into a shell command. The `update-screenshot-baselines.yaml` workflow's `if:` didn't gate on same-repo PRs. The `lint` job in `ci.yaml` carried `contents: write` + `pull-requests: write` — initially flagged as overly permissive, but those are required by `open-turo/actions-jvm/lint@v2`, which runs a semantic-release dry-run and posts the projected release notes as a PR comment. Keeping both scopes; mitigation is the SHA-pinning follow-up below.
- **Secrets in use:** `GITHUB_TOKEN` (built-in) and `FIREBASE_SERVICE_ACCOUNT` (repository secret). The earlier audit-list item about `RELEASE_PAT` was a false flag — no PAT is referenced anywhere in `.github/workflows/`.
- **`CODEOWNERS`:** already exists at `.github/CODEOWNERS` with `* @kikin81`. Satisfies the "require code-owner review" prerequisite; no PR-side change needed.

## Companion PR scope (this PR)

Two workflow hardening fixes that don't need Settings-UI changes:

1. **`release.yaml`** — replace the inline `${{ secrets.FIREBASE_SERVICE_ACCOUNT }}` interpolation in the "Decode service account key" step with an `env:` block + `echo "$FIREBASE_SA" | base64 -d`. The previous shape was vulnerable to shell-active characters in the secret value.
2. **`update-screenshot-baselines.yaml`** — add `github.event.pull_request.head.repo.full_name == github.repository` to the job's `if:` so fork PRs can't trigger the regenerate-baselines workflow. Fork PRs from outside collaborators currently land with a read-only token, so exfiltration is bounded — but the rule is "deny by default, allow explicitly," and the screenshot job runs Gradle code from the PR head.

The original audit list also called for dropping the `lint` job's `permissions:` block to `contents: read`. **That change was reverted before this PR shipped:** `open-turo/actions-jvm/lint@v2` runs a semantic-release dry-run and posts the projected release notes as a PR comment. `pull-requests: write` is required for the comment; `contents: write` is required for semantic-release's analyze/notes-staging steps. Narrowing these breaks the dry-run comment reviewers rely on. The real mitigation is the SHA-pinning follow-up below — once the action's bytecode is immutable, a compromised tag can't escalate via these scopes.

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
  - Require branches to be up to date before merging: **off** — deliberate tradeoff, not a free lunch. Squash-merge does NOT re-run the required checks against the current `main` tip, so a PR whose CI passed against an older base can merge and still break `main` if `main` advanced in between. For a solo-owner repo with high merge cadence, the cost of rebasing every PR before merge outweighs the small window where a same-day collision could land a regression. Revisit if/when the merge cadence picks up across multiple contributors, or flip to **on** for any release-track branch where breakage cost is higher.

Add bypass actor:
- **`Repository admin`** *(yourself)* — for the cases where you legitimately need to push a fix without a PR (rare).
- **A dedicated GitHub App for semantic-release.** *Don't* add `github-actions[bot]` (it isn't a deploy key, and the built-in `GITHUB_TOKEN` runs under that bot identity — adding it as a bypass actor would silently let *every* workflow run with `GITHUB_TOKEN` push to `main` bypassing PR review, which neuters this whole ruleset). Instead: provision a GitHub App scoped to `contents: write` on this repo, install it on the repo, mint an installation token in `release.yaml` (e.g. via `actions/create-github-app-token`), and add the **integration** (the App, by ID) as the bypass actor. Only `release.yaml`'s explicit `actions/create-github-app-token` step gets the bypass; other workflows that pick up `GITHUB_TOKEN` automatically do not. Track as a follow-up bd issue alongside enabling the PR rule — chicken-and-egg, so enable the rule and the bypass together.

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

Settings → Actions → General → **Fork pull request workflows from outside collaborators** → set to **"Require approval for all outside collaborators"**. (The weaker "first-time contributors" option lets repeat outside contributors trigger workflows without re-approval, which doesn't satisfy the acceptance criterion below — don't pick that one.)

This is the canonical control that prevents a fork PR from triggering any workflow — including future signing workflows — without an explicit approve click. The fork-PR guard inside `update-screenshot-baselines.yaml` (companion PR item 2 above) is defense-in-depth on top of this setting.

### ⚠️ Medium — schedule before public beta

#### 4. SHA-pin third-party actions

Replace floating-tag references like `open-turo/actions-jvm/release@v2` with full commit SHAs. A compromised maintainer of any third-party action can force-push the `v2` tag to point at malicious code, and that code runs with the workflow's permissions on every release. SHA-pinning makes the action's bytecode immutable.

Affected workflows (full inventory — every floating-tag reference, not just the third-party-most-suspect ones):
- `ci.yaml`: `open-turo/actions-jvm/lint@v2`, `actions/setup-java@v5`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6`, `gradle/actions/wrapper-validation@v6`, `actions/checkout@v6`, `madrapps/jacoco-report@v1.7.2`, `actions/upload-artifact@v7`, `actions/github-script@v9`, `actions/cache@v5`, `reactivecircus/android-emulator-runner@v2`.
- `release.yaml`: `actions/checkout@v6`, `actions/setup-java@v5`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6`, `open-turo/actions-jvm/release@v2`.
- `update-screenshot-baselines.yaml`: `actions/checkout@v6`, `actions/setup-java@v5`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v6`, `actions/github-script@v9`.

Best done as a single follow-up PR. The repo's existing Renovate config (`.github/renovate.json`) is the right place to add a `github-actions` manager (or extend the existing one) so the pinned SHAs stay current automatically — Renovate's `github-actions` manager will open update PRs the same way Dependabot's `gha` ecosystem would in repos that use Dependabot.

### 🔵 Low — opportunistic

#### 5. `kikin81/nubecita-web` ruleset

The static-site repo has no branch protection at all. Lower stakes (no secrets, no signing), but add a minimal ruleset for parity:

- `non_fast_forward` (block force-push)
- `deletion` (block branch delete)
- `required_linear_history`
- No status checks (the site has no CI yet)

5-minute Settings UI step on `kikin81/nubecita-web` whenever convenient.

### ⚠️ Medium — Firebase auth migration (Workload Identity Federation)

The `release.yaml` `distribute` job currently authenticates to GCP via a long-lived JSON service-account key (`FIREBASE_SERVICE_ACCOUNT` base-64 decoded onto disk; `GOOGLE_APPLICATION_CREDENTIALS` points at the file for the whole job). The `env:` fix in this PR closes the shell-interpolation hole, but the underlying auth model still has problems Google explicitly discourages:

- The key file sits in `/tmp` for the entire job duration; every subsequent step inherits access through `GOOGLE_APPLICATION_CREDENTIALS`.
- JSON keys don't expire — if the base-64 secret leaks, the key stays valid until manually revoked.
- GCP audit logs show "service account X did Y," not "this specific workflow run at this SHA did Y."
- Many org policies now block new SA-key creation entirely; rotation gets harder.

The Google-recommended path is **Workload Identity Federation** via `google-github-actions/auth@v2`. The action exchanges GitHub's OIDC token for a short-lived (1h) GCP access token — no key file on disk, no long-lived secret, and the WIF pool can be scoped to accept tokens only from `kikin81/nubecita` on `main` (or a specific workflow path). Setup is one-time on the GCP side (~30 min: WIF pool + provider + IAM binding); the workflow change is a clean swap:

```yaml
- uses: google-github-actions/auth@v2
  with:
    workload_identity_provider: projects/<PROJECT-NUMBER>/locations/global/workloadIdentityPools/github/providers/github-actions
    service_account: firebase-distributor@<PROJECT-ID>.iam.gserviceaccount.com
```

This is its own bd/PR — pairs naturally with moving secrets into the `release` deployment environment (step 2 above), since WIF removes the secret altogether rather than relocating it. Track under the keystore/release-flow epic when that epic gets created.

## Acceptance

- This PR's two workflow changes pass CI on the new ruleset (the workflow files compile + the jobs they define still run green).
- Operator has applied steps 1–3 in the runbook before adding the upload keystore.
- A test PR raised against `main` cannot be merged when any of the five required checks is failing.
- The `distribute` job in `release.yaml`, after step 2's environment gate, blocks on manual approval before accessing `FIREBASE_SERVICE_ACCOUNT`.
- A fork PR from an outside collaborator does not trigger any workflow without explicit approval (verified by raising a fork PR and confirming the "approve and run workflows" prompt appears).
