---
name: promote-to-production
description: Promote an already-built AAB from the Play internal track to production by dispatching the "Promote to Production" GitHub Action, then guiding the gated approval. Use when the user wants to ship the latest internal build to production, advance a staged rollout, or set an in-app-update priority.
license: MIT
compatibility: Requires the authenticated `gh` CLI, `.github/workflows/promote.yaml`, and the `production` GitHub environment (required reviewers).
metadata:
  author: nubecita
  version: "1.0"
---

Dispatch the **Promote to Production** workflow (`.github/workflows/promote.yaml`) with the right inputs, surface the resolved target, then hand the gated approval back to the human.

**What the workflow does (context):** promotes an AAB **already uploaded to the Play internal track** to the production track — no rebuild. Two jobs: `resolve` (ungated, prints the target versionCode + all three changelogs to the run summary) → `promote` (gated by the `production` environment's required-reviewer **"Review deployments → Approve and deploy"** button). `concurrency: promote-production` serializes runs.

**Input**: optional hints from the user (rollout %, priority, a specific versionCode). If absent, gather them interactively below.

## Steps

1. **Preflight.** Confirm the tooling and that you're promoting the intended build:
   ```bash
   gh auth status >/dev/null && echo "gh: authed"
   gh workflow list | grep -i "Promote to Production" || echo "workflow missing"
   git rev-parse --abbrev-ref HEAD   # workflow_dispatch runs from main by default
   ```
   The promotion targets a Play **versionCode** (independent of the git branch), but the **changelogs/fastlane come from the dispatched ref** — so dispatch from `main` (the default) unless the user explicitly wants another ref. If they're on a feature branch, note that `--ref main` is used.

2. **Gather the inputs (work together).** Use the **AskUserQuestion tool** for `rollout` and `in_app_update_priority` (they're `choice` inputs — present the real options). Ask for `version_code` only if they want a specific build; otherwise leave it blank (= latest on internal).

   - **`rollout`** (required, default `0.1`) — staged fraction. Options: `0.01 / 0.05 / 0.1 / 0.2 / 0.5 / 1.0`. A **fresh** promote starts low (e.g. `0.1`); to **advance** an in-flight rollout, re-run with a **higher** fraction and the **same versionCode**.
   - **`in_app_update_priority`** (required, default `0`) — options `0–5`. `0` = default (no nudge); `1–3` = flexible in-app-update nudge; **`>=4` forces IMMEDIATE** (blocking) update. Most releases use `0`; reserve `4–5` for critical fixes.
   - **`version_code`** (optional) — blank resolves to the **latest build on the internal track**. Only set it to promote/advance a specific older build.

3. **Assemble + confirm the command.** Show the exact command and get a yes before running — this publishes to **production**:
   ```bash
   gh workflow run "Promote to Production" --ref main \
     -f rollout=<rollout> \
     -f in_app_update_priority=<priority>
     # add only if a specific build was requested:
     # -f version_code=<versionCode>
   ```
   (`gh workflow run promote.yaml …` works too.)

4. **Dispatch + locate the run.** `gh workflow run` doesn't return the run id, so dispatch then poll for the new run:
   ```bash
   gh workflow run "Promote to Production" --ref main -f rollout=<r> -f in_app_update_priority=<p>   # [+ -f version_code=…]
   # poll until the just-created run appears (a few seconds):
   for i in $(seq 1 10); do
     id=$(gh run list --workflow=promote.yaml --limit 1 --json databaseId,status --jq '.[0].databaseId')
     [ -n "$id" ] && break; sleep 2
   done
   gh run view "$id" --json url --jq '.url'
   ```

5. **Watch `resolve`, then surface the target.** Wait for the `resolve` job to finish and point the user at the run **Summary** (it renders the resolved versionCode, the rollout/priority, and the three localized changelogs — the review surface before approval):
   ```bash
   gh run watch "$id" --exit-status || true   # resolve runs first; promote will then sit pending on the gate
   echo "Review the target + changelogs: $(gh run view "$id" --json url --jq '.url')"
   ```
   Tell the user to read the **Promote target** summary block and the changelogs.

6. **Hand off the approval (manual — never auto-approve).** The `promote` job is now **waiting on the `production` environment gate**. Direct the user to: the run → **Review deployments** → check `production` → **Approve and deploy**. Do **not** approve it programmatically — the reviewer gate exists so a human consciously authorizes the production push. Give them the run URL.

7. **(Optional) confirm completion.** After they approve, offer to watch the `promote` job to success:
   ```bash
   gh run watch "$id" --exit-status
   ```

## Notes & guardrails

- **Never approve the gate for the user** (no `gh api … /pending_deployments` auto-approve) — surface the "Review deployments" button only.
- **Advancing a rollout** is the same skill re-run with a higher `rollout` and the **same `version_code`** (set it explicitly to avoid resolving a newer internal build).
- Confirm before dispatch — it's an outward-facing, production-facing action.
- If `resolve` fails (no version code resolved), the run errors before the gate; read the `resolve` job log and check the internal track has a build.
- `concurrency: promote-production` means a second dispatch queues behind an in-flight one rather than racing.
