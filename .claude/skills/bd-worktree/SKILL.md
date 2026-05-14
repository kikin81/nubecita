---
name: bd-worktree
description: Use when starting a beads task in this repo with worktree isolation — phrases like "kick off a worktree for nubecita-X", "start worktree for X", "create worktree for X". Thin wrapper around `bd worktree create` with the project-local sibling-path convention.
---

Project-local skill for spinning up a git worktree against a beads ticket. Companion to `bd-workflow` (standard non-worktree branch/commit/PR ceremony).

**Announce at start:** "I'm using the bd-worktree skill to set up an isolated worktree for `<bd-id>`."

## When to use

- Parallel work — keep main repo on `main` while another task progresses isolated
- Dispatching a subagent that needs a clean checkout (see `superpowers:dispatching-parallel-agents`)
- Long-running work whose state should survive repo-wide branch switches

If none apply, prefer `bd-workflow` (single checkout, branch in place).

## Path convention

**Always create the worktree as a sibling at `../nubecita-<bd-id>`.** Anything under the repo root (`.worktrees/`, `worktrees/`) is forbidden — spotless + gradle walk the filesystem ignoring `.gitignore` and any tree under the repo root breaks the build. `bd worktree create` defaults to `./<name>` (inside the repo), so always pass an explicit `../nubecita-<bd-id>` path. Feature-name slugs drift; bd ids are stable.

## Pre-flight

1. `git branch --show-current` — must be `main` (or a base the user explicitly specifies).
2. `git diff --quiet && git diff --cached --quiet` — tracked tree clean. Untracked OK.
3. `bd show <id> --json | jq -r '.[0] | "\(.status) \(.issue_type)"'` — must exist, `status != "closed"`, `type != "epic"`. **Refuse epics** and point at a child.
4. `ls -d ../nubecita-<bd-id> 2>/dev/null` — must NOT exist. If it does, reuse (`cd ../nubecita-<bd-id>`) or `bd worktree remove` first.

## Create

```bash
# Branch name per bd-workflow slug rules: lowercase, non-alphanum → -, cap 50.
TYPE=feat   # or chore/fix/docs/refactor/test/perf/ci/build/style — derive from bd issue_type
BRANCH="$TYPE/<bd-id>-$SLUG"

bd worktree create ../nubecita-<bd-id> --branch="$BRANCH"
bd update <bd-id> --claim
```

Order doesn't matter — bd 1.0.4 shares the canonical dolt server from the worktree via git common-dir discovery, so `bd update --claim` from either the main repo or the new worktree lands in the same DB. Verify with `cd ../nubecita-<bd-id> && bd dolt status` — the PID and data dir match the main checkout's `bd dolt status`.

### After success

Report path + branch and **STOP**. Do NOT `cd` into the worktree from the main shell — leave the user in control (typical: open a second terminal there, or dispatch a subagent against that path).

## Cleanup (after PR merges)

```bash
bd close <bd-id> --reason="Merged via PR #N (commit <sha>)"
bd worktree remove ../nubecita-<bd-id>
bd dolt push
```

Run from the main checkout — `git`/`bd` refuse to remove a worktree from inside itself. If `bd worktree remove` errors with "worktree has unpushed commits" for a branch that's already been squash-merged upstream, pass `--force` (the squash-merge commit on `main` doesn't share a SHA with the branch's commits, so bd's safety check can't see they're integrated).

## Don'ts

- Don't put the worktree under the repo root — pass a `../nubecita-<bd-id>` path explicitly.
- Don't use `git worktree add` directly — bypasses `bd worktree`'s gitignore + bookkeeping.
- Don't `cd` into the worktree from the main shell after creating — report path and stop.
- Don't try to remove a worktree from inside it.

## Pairs with

- `bd-workflow` — branch/commit/PR ceremony continues there once the user enters the worktree.
- `superpowers:dispatching-parallel-agents` — when dispatching into the worktree, the agent can run `bd` normally; the DB is shared.
