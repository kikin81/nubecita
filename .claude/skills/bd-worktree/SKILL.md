---
name: bd-worktree
description: Use when starting a beads task in this repo with worktree isolation — phrases like "kick off a worktree for nubecita-X", "start worktree for X", "create worktree for X". Project-local conventions for sibling-path worktrees that avoid spotless/gradle gotchas + the bd dolt-server isolation bug.
---

Project-local skill for spinning up a git worktree against a beads ticket. Companion to `bd-workflow` (standard non-worktree branch/commit/PR ceremony).

**Announce at start:** "I'm using the bd-worktree skill to set up an isolated worktree for `<bd-id>`."

## When to use

- Parallel work — keep main repo on `main` while another task progresses isolated
- Dispatching a subagent that needs a clean checkout (see `superpowers:dispatching-parallel-agents`)
- Long-running work whose state should survive repo-wide branch switches

If none apply, prefer `bd-workflow` (single checkout, branch in place).

## Path convention

**Always create the worktree as a sibling at `../nubecita-<bd-id>`.** Anything under the repo root (`.worktrees/`, `worktrees/`) is forbidden — spotless + gradle walk the filesystem ignoring `.gitignore` and any tree under the repo root breaks the build. Feature-name slugs drift; bd ids are stable.

## Pre-flight

1. `git branch --show-current` — must be `main` (or a base the user explicitly specifies).
2. `git diff --quiet && git diff --cached --quiet` — tracked tree clean. Untracked OK.
3. `bd show <id> --json | jq -r '.[0] | "\(.status) \(.issue_type)"'` — must exist, `status != "closed"`, `type != "epic"`. **Refuse epics** and point at a child.
4. `ls -d ../nubecita-<bd-id> 2>/dev/null` — must NOT exist. If it does, reuse (`cd ../nubecita-<bd-id>`) or `bd worktree remove` first.

## Create

```bash
# Pre-claim from MAIN before creating the worktree (see bd-isolation below).
bd update <bd-id> --claim

# Branch name per bd-workflow slug rules: lowercase, non-alphanum → -, cap 50.
TYPE=feat   # or chore/fix/docs/refactor/test/perf/ci/build/style — derive from bd issue_type
BRANCH="$TYPE/<bd-id>-$SLUG"

bd worktree create ../nubecita-<bd-id> --branch="$BRANCH"
```

After success, **report path + branch and STOP**. Do NOT `cd` into the worktree from the main shell — leave the user in control (typical: open a second terminal there, or dispatch a subagent against that path).

## bd-isolation gotcha (load-bearing)

`bd worktree create` spawns a separate dolt server inside the worktree's `.beads/`. Any `bd update` / `bd close` issued from inside the worktree lands in that isolated server, **never reaches the canonical DB**, and is silently lost on removal.

**Workaround:** all bd state changes happen from the **main repo**.

- **Pre-claim** from main before `bd worktree create` (the `.beads/issues.jsonl` change rides into the branch via the checkout).
- **Updates / comments / dep edits** — `cd` back to main first.
- **Close** — `bd close <bd-id>` from main after the impl PR merges.
- **Subagents inside the worktree** — instruct them: "Do NOT invoke `bd` from inside this worktree. State for `<bd-id>` was already updated in main; just commit + push + PR."

Documented in user memory `feedback_pr_merge_race_misses_followup_commits`; discovered empirically during the nubecita-8m4 epic.

## Cleanup (after PR merges)

**From the main repo's working directory** (git refuses to delete a worktree's own root from inside it):

```bash
bd close <bd-id> --reason="Merged via PR #N (commit <sha>)"
bd worktree remove ../nubecita-<bd-id>
bd dolt push
```

## Don'ts

- Don't put the worktree under the repo root.
- Don't run `bd update` / `bd close` from inside the worktree — pre-claim from main, close from main.
- Don't use `git worktree add` directly — bypasses bd-aware setup.
- Don't `cd` into the worktree from the main shell after creating — report path and stop.
- Don't try to remove a worktree from inside it.

## Pairs with

- `bd-workflow` — branch/commit/PR ceremony continues there once the user enters the worktree.
- `superpowers:dispatching-parallel-agents` — when dispatching into the worktree, brief the agent that `bd` is off-limits inside it.
