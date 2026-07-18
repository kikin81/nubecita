---
name: promote-to-production
description: Promote an already-built AAB from the Play internal track to one or more downstream tracks — closed testing (alpha), open testing (beta), and/or production — by dispatching the "Promote to Production" GitHub Action, then guiding the gated approval. Use when the user wants to ship the latest internal build to production or testing tracks, advance a staged rollout, or set an in-app-update priority.
license: MIT
compatibility: Requires the authenticated `gh` CLI, `.github/workflows/promote.yaml`, and the `production` GitHub environment (required reviewers).
metadata:
  author: nubecita
  version: "1.1"
---

Dispatch the **Promote to Production** workflow (`.github/workflows/promote.yaml`) with the right inputs, surface the resolved target, then hand the gated approval back to the human.

**What the workflow does (context):** promotes an AAB **already uploaded to the Play internal track** to any combination of **closed testing (`alpha`)**, **open testing (`beta`)**, and **production** — no rebuild. The three tracks are chosen via boolean inputs (`to_closed` / `to_open` / `to_production`) and folded into a comma list. Two jobs: `resolve` (ungated, prints the target versionCode, the selected tracks, and all three changelogs to the run summary; fails fast if no track is selected) → `promote` (gated by the `production` environment's required-reviewer **"Review deployments → Approve and deploy"** button — one approval covers the whole batch). `concurrency: promote-production` serializes runs.

Per-track behavior handled by the lane: **rollout % and update priority apply to production only**; `alpha`/`beta` always go to **100% of testers** with no priority, and a build already live on a testing track is **skipped** (no redundant re-promote). Only production re-runs to advance its staged rollout.

**Input**: optional hints from the user (which tracks, rollout %, priority, a specific versionCode). If absent, gather them interactively below.

## Steps

1. **Preflight.** Confirm the tooling and that you're promoting the intended build:
   ```bash
   gh auth status >/dev/null && echo "gh: authed"
   gh workflow list | grep -i "Promote to Production" || echo "workflow missing"
   git rev-parse --abbrev-ref HEAD   # workflow_dispatch runs from main by default
   ```
   The promotion targets a Play **versionCode** (independent of the git branch), but the **changelogs/fastlane come from the dispatched ref** — so dispatch from `main` (the default) unless the user explicitly wants another ref. If they're on a feature branch, note that `--ref main` is used.

2. **Gather the inputs (work together).** First use the **AskUserQuestion tool** (multiSelect) to pick the **target track(s)**, then ask the production-only knobs *only if* production is among the picks.

   - **Tracks** (multiSelect, at least one) — **Closed testing (`alpha`)** / **Open testing (`beta`)** / **Production**. Map the picks to the boolean inputs `to_closed` / `to_open` / `to_production`. Default suggestion: Production. (Testing tracks always go to 100% of testers, so they need no rollout/priority.)
   - **`rollout`** (ask **only if Production is selected**; default `0.1`) — staged fraction. Options: `0.01 / 0.05 / 0.1 / 0.2 / 0.5 / 1.0`. A **fresh** production promote starts low (e.g. `0.1`); to **advance** an in-flight rollout, re-run with a **higher** fraction and the **same versionCode**. Ignored for `alpha`/`beta`.
   - **`in_app_update_priority`** (ask **only if Production is selected**; default `0`) — options `0–5`. `0` = default (no nudge); `1–3` = flexible in-app-update nudge; **`>=4` forces IMMEDIATE** (blocking) update. Most releases use `0`; reserve `4–5` for critical fixes. Not applied to testing tracks.
   - **`version_code`** (optional) — blank resolves to the **latest build on the internal track**. Only set it to promote/advance a specific older build.

3. **Assemble + confirm the command.** Show the exact command and get a yes before running — this publishes to real users / testers. Pass a boolean for each track (`true`/`false`); include `rollout`/`in_app_update_priority` only when Production is selected (they default otherwise):
   ```bash
   # include rollout / in_app_update_priority only when to_production=true (they default otherwise);
   # add -f version_code=<versionCode> only if a specific build was requested.
   gh workflow run "Promote to Production" --ref main \
     -f to_closed=<true|false> \
     -f to_open=<true|false> \
     -f to_production=<true|false> \
     -f rollout=<rollout> \
     -f in_app_update_priority=<priority>
   ```
   (`gh workflow run promote.yaml …` works too.)

4. **Dispatch + locate the run.** `gh workflow run` doesn't return the run id. Capture the latest run id **before** dispatching, then poll until a **different** id appears — otherwise `gh run list --limit 1` returns the *previous* promote run (the new one takes a few seconds to register) and you'd track the wrong run:
   ```bash
   old_id=$(gh run list --workflow=promote.yaml --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || echo "")
   gh workflow run "Promote to Production" --ref main -f to_closed=<b> -f to_open=<b> -f to_production=<b> -f rollout=<r> -f in_app_update_priority=<p>   # [+ -f version_code=…]
   for i in $(seq 1 15); do
     id=$(gh run list --workflow=promote.yaml --limit 1 --json databaseId --jq '.[0].databaseId')
     [ -n "$id" ] && [ "$id" != "$old_id" ] && break; sleep 2
   done
   gh run view "$id" --json url --jq '.url'
   ```

5. **Wait for the gate, then surface the target.** Do **not** `gh run watch` — the `promote` job sits in `waiting` on the manual approval gate, so `watch` blocks indefinitely and you'd never hand off. Instead poll until the run is no longer `queued`/`in_progress` (it becomes `waiting` at the gate once `resolve` is done, or `completed` if `resolve` failed), then point the user at the run **Summary** (resolved versionCode, the **selected tracks**, rollout/priority, and the three localized changelogs — the review surface before approval). The run name itself reads e.g. "Promote v1301000 to alpha,beta":
   ```bash
   for i in $(seq 1 30); do
     status=$(gh run view "$id" --json status --jq '.status' 2>/dev/null)
     [ "$status" != "queued" ] && [ "$status" != "in_progress" ] && break
     sleep 5
   done
   echo "status=$status — review the target + changelogs: $(gh run view "$id" --json url --jq '.url')"
   ```
   If `status` is `completed` (not `waiting`), `resolve` likely failed before the gate — read its log. Otherwise tell the user to read the **Promote target** summary block + the changelogs.

6. **Hand off the approval (manual — never auto-approve).** The `promote` job is now **waiting on the `production` environment gate**. Direct the user to: the run → **Review deployments** → check `production` → **Approve and deploy**. Do **not** approve it programmatically — the reviewer gate exists so a human consciously authorizes the production push. Give them the run URL.

7. **(Optional) confirm completion.** After they approve, offer to watch the `promote` job to success:
   ```bash
   gh run watch "$id" --exit-status
   ```

## Notes & guardrails

- **Never approve the gate for the user** (no `gh api … /pending_deployments` auto-approve) — surface the "Review deployments" button only.
- **Which tracks?** Catching up stale closed/open testing = select `to_closed`/`to_open`. Shipping to users = `to_production`. Any combination is one dispatch + one approval. At least one must be checked or `resolve` fails fast (pre-gate).
- **Advancing a rollout** applies to **production only**: re-run with a higher `rollout`, `to_production=true`, and the **same `version_code`** (set it explicitly to avoid resolving a newer internal build). Re-selecting a testing track a build is already on is a safe no-op (the lane skips it).
- Confirm before dispatch — it's an outward-facing, production-facing action.
- If `resolve` fails (no version code resolved), the run errors before the gate; read the `resolve` job log and check the internal track has a build.
- `concurrency: promote-production` means a second dispatch queues behind an in-flight one rather than racing.
