# Promote-to-production Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow + Fastlane lane that promotes an already-built, internal-tested AAB from the Play internal track to production (no rebuild), at a staged rollout, with committed localized release notes and an optional in-app update priority, gated by a GitHub deployment approval.

**Architecture:** A pure-promotion `promote_production` Fastlane lane (uses the Play Developer API via supply; never runs Gradle) plus a two-job `promote.yaml` (`resolve` echoes the target → `promote` is gated by a new `production` environment). Localized notes live as committed `default.txt` files per locale. A small edit also lets the existing `internal` lane carry an optional priority.

**Tech Stack:** Fastlane (`upload_to_play_store`/supply), GitHub Actions (`workflow_dispatch`, environments, WIF auth via `google-github-actions/auth@v3`), Ruby.

**Spec:** `docs/superpowers/specs/2026-06-24-promote-to-production-design.md`. **Issue:** `nubecita-heij`. **Branch:** `feat/nubecita-heij-promote-to-production` (already checked out; draft PR #581).

**Testing note (read before starting):** This is CI/Fastlane *config*, not app code — there is **no TDD red/green, no Gradle/JVM/screenshot tests**. Each task's "verify" step is a **syntax/parse check** (`bundle exec fastlane lanes` for the Fastfile; a YAML parse + `actionlint` when available for the workflows; `pre-commit run --files …`). The only behavioral validation is a **real gated promote at a tiny rollout** that you immediately halt in Console (Task 8) — fastlane has no offline Play fake.

**Commit discipline:** lowercase-leading Conventional Commit subjects, footer `Refs: nubecita-heij`, never `--no-verify`. If a commit fails on a gpg/ssh signing error (remote session), run `git config --local commit.gpgsign false`, retry, then `git config --local --unset commit.gpgsign`.

---

## File structure

| File | Responsibility | Task |
|---|---|---|
| `fastlane/metadata/android/{en-US,es-419,pt-BR}/changelogs/default.txt` | Committed production release notes per locale (overwritten per release) | 1 |
| `fastlane/Fastfile` (module helpers + 2 new lanes) | `nubecita_blank`, `nubecita_resolve_promote_version_code`; `resolve_promote_target`; `promote_production` | 2 |
| `fastlane/Fastfile` (`internal` lane edit) | Read optional `IN_APP_UPDATE_PRIORITY` | 3 |
| `.github/workflows/promote.yaml` | `workflow_dispatch`; `resolve` → `promote` (gated) | 4 |
| `.github/workflows/release.yaml` (edit) | Optional `in_app_update_priority` dispatch input → `internal` step env | 5 |
| `fastlane/README.md` (edit) | Document the promote lane + the one-time GitHub/GCP setup | 6 |

---

## Task 1: Committed localized changelog files

**Files:**
- Create: `fastlane/metadata/android/en-US/changelogs/default.txt`
- Create: `fastlane/metadata/android/es-419/changelogs/default.txt`
- Create: `fastlane/metadata/android/pt-BR/changelogs/default.txt`

- [ ] **Step 1: Create the en-US changelog**

`fastlane/metadata/android/en-US/changelogs/default.txt`:
```
Bug fixes and performance improvements.
```

- [ ] **Step 2: Create the es-419 changelog**

`fastlane/metadata/android/es-419/changelogs/default.txt`:
```
Correcciones de errores y mejoras de rendimiento.
```

- [ ] **Step 3: Create the pt-BR changelog**

`fastlane/metadata/android/pt-BR/changelogs/default.txt`:
```
Correções de bugs e melhorias de desempenho.
```

- [ ] **Step 4: Verify all three exist and are non-empty + within the 500-char cap**

Run:
```bash
for loc in en-US es-419 pt-BR; do
  f="fastlane/metadata/android/$loc/changelogs/default.txt"
  n=$(wc -m < "$f" | tr -d ' ')
  echo "$f -> $n chars"; test -s "$f" && test "$n" -le 500 || echo "BAD: $f"
done
```
Expected: three lines, each well under 500, no `BAD:`.

- [ ] **Step 5: Commit**

```bash
git add fastlane/metadata/android/en-US/changelogs/default.txt \
        fastlane/metadata/android/es-419/changelogs/default.txt \
        fastlane/metadata/android/pt-BR/changelogs/default.txt
git commit -m "$(cat <<'EOF'
chore(fastlane): seed committed production changelogs (en/es/pt)

Refs: nubecita-heij

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `promote_production` + `resolve_promote_target` lanes (+ shared helpers)

**Files:**
- Modify: `fastlane/Fastfile` — add two module-level helpers near `nubecita_metadata_android_dir` (around line 37–43), and two lanes inside the `platform :android do … end` block (after the existing `internal` lane, before `upload_screenshots`).

- [ ] **Step 1: Add the two module-level helpers**

Insert immediately **after** the `nubecita_metadata_android_dir` method (which ends around line 43, before `def nubecita_stage_marketing_screenshots`):
```ruby
# Normalize a CLI/ENV value: blank/whitespace-only → nil (so `x || fallback`
# works; Ruby treats "" as truthy, which would otherwise defeat the fallback).
def nubecita_blank(value)
  s = value.to_s.strip
  s.empty? ? nil : s
end

# The version code promote_production targets: an explicit override if given,
# else the highest code currently on the internal track. Integer, or nil if none.
def nubecita_resolve_promote_version_code(version_code_option)
  (nubecita_blank(version_code_option)&.to_i ||
    google_play_track_version_codes(track: "internal").max)&.to_i
end
```

- [ ] **Step 2: Add the `resolve_promote_target` lane**

Inside `platform :android do`, immediately **after** the `internal` lane's closing `end` (around line 139):
```ruby
  desc "Print the version code promote_production would target (RESOLVED_VERSION_CODE=<n>)."
  lane :resolve_promote_target do |options|
    vc = nubecita_resolve_promote_version_code(options[:version_code]) ||
      UI.user_error!("No internal version code found to resolve")
    # Machine-readable marker the promote workflow greps out of fastlane's log.
    puts "RESOLVED_VERSION_CODE=#{vc}"
  end
```

- [ ] **Step 3: Add the `promote_production` lane**

Immediately after `resolve_promote_target`:
```ruby
  desc "Promote an internal version code to production at a rollout fraction, with localized notes."
  lane :promote_production do |options|
    rollout = nubecita_blank(options[:rollout]) || "0.1"
    UI.user_error!("Rollout must be in (0.0, 1.0] (got: #{rollout})") unless rollout.to_f > 0.0 && rollout.to_f <= 1.0
    priority = nubecita_blank(options[:priority])&.to_i
    UI.user_error!("Priority must be 0..5 (got: #{priority})") if priority && !priority.between?(0, 5)
    vc = nubecita_resolve_promote_version_code(options[:version_code]) ||
      UI.user_error!("No internal version code found to promote")

    # Every locale must have a non-empty default.txt within Play's per-locale cap,
    # else supply ships a partial set or fails the upload mid-request.
    %w[en-US es-419 pt-BR].each do |loc|
      f = File.join(nubecita_metadata_android_dir, loc, "changelogs", "default.txt")
      text = File.exist?(f) ? File.read(f).strip : ""
      UI.user_error!("Missing/empty changelog: #{f}") if text.empty?
      UI.user_error!("Changelog #{loc} exceeds #{PLAY_CHANGELOG_MAX_CHARS} chars (#{text.length})") if text.length > PLAY_CHANGELOG_MAX_CHARS
    end

    already_prod = google_play_track_version_codes(track: "production").map(&:to_i).include?(vc)
    UI.message("#{already_prod ? 'Advancing rollout for' : 'Promoting'} versionCode #{vc} → production @ #{rollout}")

    upload_to_play_store(
      track: already_prod ? "production" : "internal",      # first run promotes; re-run advances rollout
      track_promote_to: already_prod ? nil : "production",
      version_code: vc,
      rollout: rollout,
      # Priority is immutable per release: set it only on the initial promote.
      in_app_update_priority: already_prod ? nil : priority,
      metadata_path: nubecita_metadata_android_dir,         # committed en-US/es-419/pt-BR changelogs
      skip_upload_changelogs: false,                        # the localized-notes upload
      skip_upload_apk: true, skip_upload_aab: true,
      skip_upload_metadata: true,                           # listing text / images untouched
      skip_upload_images: true, skip_upload_screenshots: true,
    )
  end
```

- [ ] **Step 4: Verify the Fastfile parses and the new lanes are registered**

Run:
```bash
bundle exec fastlane lanes 2>&1 | grep -E "promote_production|resolve_promote_target"
```
Expected: both lane names appear (proves the Fastfile parsed without Ruby/DSL errors). If `bundle` isn't set up locally, run `bundle install` first.

- [ ] **Step 5: Run spotless/format-agnostic sanity (Ruby syntax)**

Run:
```bash
ruby -c fastlane/Fastfile
```
Expected: `Syntax OK`.

- [ ] **Step 6: Commit**

```bash
git add fastlane/Fastfile
git commit -m "$(cat <<'EOF'
feat(fastlane): promote_production + resolve_promote_target lanes

Promote an internal version code to production at a rollout fraction (advancing
an in-progress rollout on re-run), uploading the committed localized changelogs
and an optional immutable update priority. resolve_promote_target prints the
auto-detected target for the workflow's confirm step.

Refs: nubecita-heij

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `internal` lane — optional update priority

**Files:**
- Modify: `fastlane/Fastfile` — the `internal` lane's `upload_to_play_store(...)` call (around line 129–137).

- [ ] **Step 1: Add the priority arg to the internal upload**

In the `internal` lane, change the `upload_to_play_store(` call so it reads (add the **`in_app_update_priority:`** line after `track:`):
```ruby
      upload_to_play_store(
        track: "internal",
        # Optional: a manual `release.yaml` dispatch can force a priority for the
        # internal upload; the automatic push path leaves it unset → Play default 0.
        in_app_update_priority: nubecita_blank(ENV["IN_APP_UPDATE_PRIORITY"])&.to_i,
        aab: lane_context[SharedValues::GRADLE_AAB_OUTPUT_PATH],
        metadata_path: metadata_path,
        skip_upload_apk: true,
        skip_upload_metadata: true,
        skip_upload_images: true,
        skip_upload_screenshots: true,
      )
```
(The exact pre-existing keys may differ slightly; **only add the `in_app_update_priority:` line** — leave every other argument exactly as it is.)

- [ ] **Step 2: Verify it parses**

Run:
```bash
ruby -c fastlane/Fastfile && bundle exec fastlane lanes 2>&1 | grep -E "internal"
```
Expected: `Syntax OK` and the `internal` lane still listed.

- [ ] **Step 3: Commit**

```bash
git add fastlane/Fastfile
git commit -m "$(cat <<'EOF'
feat(fastlane): optional in-app update priority on the internal lane

Read IN_APP_UPDATE_PRIORITY (blank → omit → Play default 0) so a manual release
dispatch can carry priority; the automatic semantic-release path stays at 0.

Refs: nubecita-heij

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `promote.yaml` workflow

**Files:**
- Create: `.github/workflows/promote.yaml`

- [ ] **Step 1: Create the workflow**

`.github/workflows/promote.yaml`:
```yaml
name: Promote to Production

on:
  workflow_dispatch:
    inputs:
      rollout:
        description: "Rollout fraction (re-run higher to advance)"
        required: true
        default: "0.1"
        type: choice
        options: ["0.01", "0.05", "0.1", "0.2", "0.5", "1.0"]
      version_code:
        description: "Play versionCode to promote (blank = latest on internal)"
        required: false
        type: string
      in_app_update_priority:
        description: "Production update priority (>=4 forces IMMEDIATE; 0 = default)"
        required: true
        default: "0"
        type: choice
        options: ["0", "1", "2", "3", "4", "5"]

concurrency:
  group: promote-production
  cancel-in-progress: false

permissions:
  contents: read
  id-token: write

jobs:
  resolve:
    name: Resolve version code + notes
    runs-on: ubuntu-latest
    # Reuse the existing release environment for read-only WIF auth; no reviewer
    # gate here, so it can surface the target before the gated promote job.
    environment: release
    outputs:
      version_code: ${{ steps.resolve.outputs.version_code }}
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0          # full history so `git log -1 -- <changelog>` finds the last edit
      - uses: ruby/setup-ruby@v1
        with:
          bundler-cache: true
      - name: Authenticate to Google Cloud (WIF)
        uses: google-github-actions/auth@v3
        with:
          workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}
      - name: Resolve target + echo notes
        id: resolve
        env:
          INPUT_VERSION_CODE: ${{ inputs.version_code }}
        run: |
          set -euo pipefail
          bundle exec fastlane resolve_promote_target version_code:"${INPUT_VERSION_CODE}" | tee /tmp/resolve.log
          vc=$(grep -oE 'RESOLVED_VERSION_CODE=[0-9]+' /tmp/resolve.log | tail -1 | cut -d= -f2)
          if [ -z "$vc" ]; then
            echo "::error::Failed to resolve a version code from fastlane output."
            exit 1
          fi
          echo "version_code=$vc" >> "$GITHUB_OUTPUT"
          {
            echo "## Promote target"
            echo "- versionCode: **$vc**"
            echo "- rollout: **${{ inputs.rollout }}**  ·  priority: **${{ inputs.in_app_update_priority }}**"
            for loc in en-US es-419 pt-BR; do
              f="fastlane/metadata/android/$loc/changelogs/default.txt"
              edited=$(git log -1 --format='last edited %cr (%h: %s)' -- "$f")
              echo "### $loc — _${edited}_"
              echo '```'
              cat "$f"
              echo '```'
            done
          } >> "$GITHUB_STEP_SUMMARY"

  promote:
    name: Promote v${{ needs.resolve.outputs.version_code }} to production
    needs: resolve
    runs-on: ubuntu-latest
    # NEW environment with required reviewers → the "Approve and deploy" gate.
    environment: production
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@v7
      - uses: ruby/setup-ruby@v1
        with:
          bundler-cache: true
      - name: Authenticate to Google Cloud (WIF)
        uses: google-github-actions/auth@v3
        with:
          workload_identity_provider: ${{ secrets.GCP_WORKLOAD_IDENTITY_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}
      - name: Promote to production
        run: |
          bundle exec fastlane promote_production \
            version_code:"${{ needs.resolve.outputs.version_code }}" \
            rollout:"${{ inputs.rollout }}" \
            priority:"${{ inputs.in_app_update_priority }}"
```

- [ ] **Step 2: Validate YAML parses**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/promote.yaml')); print('YAML OK')"
```
Expected: `YAML OK`.

- [ ] **Step 3: Lint the workflow (if `actionlint` is available)**

Run:
```bash
command -v actionlint >/dev/null && actionlint .github/workflows/promote.yaml || echo "actionlint not installed — skipping (CI/pre-commit will catch it)"
```
Expected: no errors (or the skip message). If actionlint reports errors, fix them.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/promote.yaml
git commit -m "$(cat <<'EOF'
ci: promote-to-production workflow (resolve + gated promote)

workflow_dispatch with a resolve job (echoes the target version code + the three
localized changelogs) feeding a promote job gated by the production environment.

Refs: nubecita-heij

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `release.yaml` — optional priority input

**Files:**
- Modify: `.github/workflows/release.yaml` — the `on.workflow_dispatch` block (around line 4) and the `playstore` job's "Upload AAB to Play Console internal track" step `env:` (around line 440–450).

- [ ] **Step 1: Add the dispatch input**

Change the top `on:` block from:
```yaml
on:
  workflow_dispatch:
  push:
    branches: [main]
```
to:
```yaml
on:
  workflow_dispatch:
    inputs:
      in_app_update_priority:
        description: "In-app update priority for this internal upload (0 = default)"
        required: false
        default: "0"
        type: choice
        options: ["0", "1", "2", "3", "4", "5"]
  push:
    branches: [main]   # push events carry no inputs → IN_APP_UPDATE_PRIORITY empty → lane omits (0)
```

- [ ] **Step 2: Wire the input into the internal-upload step env**

In the `playstore` job, the "Upload AAB to Play Console internal track" step has an `env:` block ending with `REVENUECAT_API_KEY: ${{ secrets.REVENUECAT_API_KEY }}`. Add one line to that `env:`:
```yaml
          # Empty on the automatic push path (inputs only exist for workflow_dispatch),
          # so the internal lane omits it → Play default priority 0.
          IN_APP_UPDATE_PRIORITY: ${{ inputs.in_app_update_priority }}
```

- [ ] **Step 3: Validate YAML parses**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yaml')); print('YAML OK')"
```
Expected: `YAML OK`.

- [ ] **Step 4: Lint (if available)**

Run:
```bash
command -v actionlint >/dev/null && actionlint .github/workflows/release.yaml || echo "actionlint not installed — skipping"
```
Expected: no errors (or skip message).

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/release.yaml
git commit -m "$(cat <<'EOF'
ci(release): optional in-app update priority input for internal uploads

Refs: nubecita-heij

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Document the lane + one-time setup in `fastlane/README.md`

**Files:**
- Modify: `fastlane/README.md` (hand-maintained; `skip_docs` keeps fastlane from clobbering it).

- [ ] **Step 1: Read the current README to match its section style**

Run:
```bash
sed -n '1,60p' fastlane/README.md
```

- [ ] **Step 2: Append a "Promote to production" section**

Add this section to `fastlane/README.md` (place it after the existing lane docs, matching the file's heading style — adjust heading level to match neighbors):
```markdown
## Promote to production

`promote_production` promotes an existing internal version code to the production
track (no rebuild) at a rollout fraction, uploading the committed localized
changelogs and an optional immutable update priority.

    # auto-detect latest internal version code, 10% rollout, default priority:
    bundle exec fastlane promote_production
    # explicit code, 50% rollout, force IMMEDIATE:
    bundle exec fastlane promote_production version_code:142 rollout:0.5 priority:5

Re-running with a higher `rollout` advances an in-progress rollout (the lane
detects the code is already on production). Priority is set only on the initial
promote (it is immutable per release).

Release notes are the committed files
`fastlane/metadata/android/<locale>/changelogs/default.txt` (en-US / es-419 /
pt-BR). Edit them via a PR before promoting; generic notes are intentionally
reused across routine releases.

### CI

`.github/workflows/promote.yaml` (`workflow_dispatch`) runs `resolve` (echoes the
target version code + the three changelogs to the run summary) then a `promote`
job gated by the **`production`** GitHub environment (required reviewers →
"Approve and deploy").

### One-time setup (required before first use)

1. Create a **`production`** GitHub environment → **Required reviewers** + a
   deployment-branch policy of `main`.
2. Add `GCP_WORKLOAD_IDENTITY_PROVIDER` + `GCP_SERVICE_ACCOUNT` as **`production`
   environment** secrets (they are otherwise `release`-scoped; `release` keeps its
   copies for the `resolve` job).
3. **GCP side:** ensure the Workload Identity provider's attribute condition / the
   service account's `principalSet` accepts the **`production`** environment, not
   just `release` — otherwise the `promote` job fails authentication.
4. Confirm the service account's Play Console permission can write the
   **production** track (not internal-only).
```

- [ ] **Step 3: Verify it renders (no broken fences) + pre-commit on docs**

Run:
```bash
pre-commit run --files fastlane/README.md || true
```
Expected: passes (or only auto-fixes whitespace; re-add + re-run if so).

- [ ] **Step 4: Commit**

```bash
git add fastlane/README.md
git commit -m "$(cat <<'EOF'
docs(fastlane): document promote_production + production-env setup

Refs: nubecita-heij

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Full local gate + push + review

**Files:** none (verification + ship).

- [ ] **Step 1: Full pre-commit + parse gate**

Run:
```bash
ruby -c fastlane/Fastfile
bundle exec fastlane lanes 2>&1 | grep -E "promote_production|resolve_promote_target|internal"
python3 -c "import yaml; [yaml.safe_load(open(f)) for f in ['.github/workflows/promote.yaml','.github/workflows/release.yaml']]; print('YAML OK')"
command -v actionlint >/dev/null && actionlint .github/workflows/promote.yaml .github/workflows/release.yaml || echo "actionlint not installed"
pre-commit run --all-files || true
```
Expected: `Syntax OK`; the three lanes listed; `YAML OK`; actionlint clean (or skip); pre-commit clean (fix + re-stage anything it auto-formats, then re-run).

- [ ] **Step 2: Push**

```bash
git push
```

- [ ] **Step 3: Request a fresh Gemini review on the implementation**

```bash
gh pr comment 581 --body "/gemini review"
```
(Per the gemini-review-only-on-pr-open behavior: re-request now that the implementation is pushed.) Address any findings (reply + resolve threads), commit fixes, re-push.

- [ ] **Step 4: Watch CI**

```bash
gh pr checks 581 --watch --interval 30
```
Expected: `lint` (pre-commit/actionlint) + the other PR jobs green. The new `promote.yaml` is `workflow_dispatch`-only, so it does **not** run on the PR — it is validated by the steps above, not by a CI run.

---

## Task 8: Manual setup + first real validation (human; after merge)

> Not code — record as the issue's closing checklist. The promote workflow cannot be exercised by CI (it writes to Play); the only real test is a gated promote you control.

- [ ] **Step 1:** Complete the one-time setup from Task 6 Step 2 (the `production` environment, its WIF secrets, the GCP-side attribute condition, the Play track permission).
- [ ] **Step 2:** After a routine internal release exists, run **Actions → Promote to Production** with `rollout: 0.01` (smallest), default priority.
- [ ] **Step 3:** Confirm the `resolve` job's summary shows the expected version code + the three changelogs; **Approve and deploy** the `promote` job.
- [ ] **Step 4:** In the Play Console, confirm versionCode N is on the **production** track at 1% with the en/es/pt notes attached; **halt** the rollout (validation only).
- [ ] **Step 5:** Re-run with `rollout: 0.05` and confirm the lane takes the "advance" path (logs `Advancing rollout for …`, sets the track back to `inProgress`).

---

## Self-review notes

- **Spec coverage:** committed localized notes (T1) ✓; `promote_production` lane incl. auto-detect, advance-on-rerun, rollout/priority validation, 500-char changelog cap, priority-only-on-initial (T2) ✓; `resolve_promote_target` for the confirm echo (T2) ✓; `internal`-lane priority (T3) ✓; `promote.yaml` resolve→gated-promote with the run-summary echo + empty-vc guard (T4) ✓; `release.yaml` priority input (T5) ✓; README + one-time setup incl. the GCP-side WIF attribute-condition + Play-track-permission checks (T6) ✓; gate + `/gemini review` (T7) ✓; manual first-promote validation incl. halt-collision advance path (T8) ✓. Stale-notes guard intentionally omitted (echo-only by design) — spec §Error handling.
- **Type/name consistency:** `nubecita_blank` / `nubecita_resolve_promote_version_code` defined in T2 and reused in T2/T3; lane options `version_code` / `rollout` / `priority` consistent between `promote.yaml` (T4) and the lane (T2); `RESOLVED_VERSION_CODE=` marker emitted by `resolve_promote_target` (T2) and grepped by `promote.yaml` (T4); `IN_APP_UPDATE_PRIORITY` env set in `release.yaml` (T5) and read by the `internal` lane (T3).
- **No placeholders:** every step has concrete file content / exact commands.
- **Conventions:** bd `nubecita-heij`; Conventional Commits (lowercase-leading) + `Refs:`; action versions match the repo standard (`actions/checkout@v7`, `ruby/setup-ruby@v1`, `google-github-actions/auth@v3`); WIF/ADC via the Appfile (no `:json_key`).
