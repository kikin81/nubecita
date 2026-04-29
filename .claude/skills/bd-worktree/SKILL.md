---
name: bd-worktree
description: Use when starting a beads task in this repo with worktree isolation — phrases like "kick off a worktree for nubecita-X", "start worktree for X", "create worktree for X". Project-local conventions for sibling-path worktrees that avoid spotless/gradle gotchas + the bd dolt-server isolation bug.
---

Project-local skill for spinning up a git worktree against a beads ticket. Companion to the `bd-workflow` skill (which handles the standard non-worktree branch/commit/PR ceremony).

**Announce at start:** "I'm using the bd-worktree skill to set up an isolated worktree for `<bd-id>`."

## When to use

- Parallel work — you want to keep the main repo on `main` while another task progresses isolated
- Dispatching a subagent that needs a clean checkout (see `superpowers:dispatching-parallel-agents`)
- Long-running work whose state should survive repo-wide branch switches

If none apply, prefer the standard `bd-workflow` skill (single checkout, branch in place).

## Path convention

**Always create the worktree as a sibling at `../nubecita-<bd-id>`.**

| Path | Verdict | Why |
|---|---|---|
| `../nubecita-<bd-id>` | ✅ canonical | Terse, stable (bd id never changes), one-level-up sibling |
| `.worktrees/<anything>` | ❌ never | Spotless + gradle walk the filesystem ignoring `.gitignore`; any tree under the repo root breaks the build |
| `worktrees/<anything>` | ❌ never | Same as above |
| `../nubecita-<feature-slug>` | ❌ avoid | Feature names drift; bd id is stable |

The sibling-path rule is documented in user memory `feedback_worktrees_outside_repo` — root cause for the convention.

## Pre-flight

Before creating a worktree:

1. `git branch --show-current` — must be on `main` (or whatever base the user specifies; if not main, confirm).
2. `git diff --quiet && git diff --cached --quiet` — tracked tree must be clean. Untracked files are OK.
3. `bd show <id> --json | jq -r '.[0] | "id=\(.id) status=\(.status) type=\(.issue_type)"'` — verify entry exists, `status != "closed"`, and `type != "epic"`. **If it's an epic, refuse and point at a child issue** (mirrors `bd-workflow`'s precondition).
4. `ls -d ../nubecita-<bd-id> 2>/dev/null` — must NOT exist. If it does, the worktree was created in a prior session; reuse it (`cd ../nubecita-<bd-id>`) or remove first (`bd worktree remove ../nubecita-<bd-id>`).

## Creating the worktree

```bash
# 1. Pre-claim from MAIN repo BEFORE creating the worktree (read the
#    "bd-isolation gotcha" section below — claiming from inside the
#    worktree silently lands the claim in the wrong DB).
bd update <bd-id> --claim

# 2. Derive branch name per the bd-workflow slug rules
#    (lowercase, non-alphanumerics → -, cap 50 chars; manual override OK
#    when the auto-slug is ugly).
TYPE=feat   # or chore/fix/docs/refactor/test/perf/ci/build/style — derive from bd issue_type
SLUG=...
BRANCH="$TYPE/<bd-id>-$SLUG"

# 3. Create the worktree at the canonical sibling path
bd worktree create ../nubecita-<bd-id> --branch="$BRANCH"
```

After `bd worktree create` succeeds, **report the worktree path + branch name to the user and STOP**. Do NOT `cd` into the worktree from the main shell — leave the user in control of when to enter the worktree (typical pattern: open a second terminal there, or dispatch a subagent against that path).

## bd-isolation gotcha (load-bearing)

`bd worktree create` spawns a separate dolt server inside the worktree's `.beads/` directory. Any `bd update`, `bd close`, or other writeback issued from inside the worktree lands in that isolated server, **never reaches the canonical DB**, and is silently lost when the worktree is removed.

**Workaround used by this skill:** all bd state changes happen from the **main repo's working directory**, not from inside the worktree. Concretely:

- **Pre-claim** from main before `bd worktree create` (step 1 above). The `.beads/issues.jsonl` change rides into the worktree's branch via the git checkout.
- **Subsequent updates** (status changes, comments, dep edits) — `cd` back to the main repo first.
- **Close** — `bd close <bd-id>` from main repo after the impl PR merges.
- **Subagents working inside the worktree** should be instructed: "Do NOT invoke `bd` from inside this worktree. The bd state for `<bd-id>` was already updated in main before you started; just commit + push + PR." (See the briefs in the nubecita-8m4 epic for an example.)

This is documented in user memory `feedback_pr_merge_race_misses_followup_commits` and was discovered empirically during nubecita-8m4 epic work.

## Cleanup (after PR merges)

**From the main repo's working directory** (not from inside the worktree — git refuses to delete a worktree's own root):

```bash
bd close <bd-id> --reason="Merged via PR #N (commit <sha>)"
bd worktree remove ../nubecita-<bd-id>
bd dolt push  # publish the close + worktree-removal to dolt remote
```

If the worktree's branch was unpushed (rare), the local branch deletion may fail; resolve manually.

## Common mistakes

- **Path inside the repo** (`./worktrees/foo`, `.worktrees/foo`) — spotless picks it up as a stray Kotlin tree and fails the build. Always `../nubecita-<bd-id>`.
- **Claiming from inside the worktree** — `bd update --claim` lands in the worktree's isolated dolt server, never reaches the canonical DB, lost on removal. Always pre-claim from main.
- **`git worktree add` directly instead of `bd worktree create`** — bypasses the bd-aware setup. Use `bd worktree create`.
- **Removing the worktree from inside it** — `git worktree remove .` errors out. Always remove from the main repo's working directory.
- **Creating two worktrees with the same `<bd-id>`** — bd worktree complains. Run `bd worktree list` first to check, or `bd worktree remove` the stale one.

## Quick reference

| Step | Command |
|---|---|
| Pre-flight (clean tree) | `git diff --quiet && git diff --cached --quiet` |
| Pre-flight (verify ticket) | `bd show <id> --json \| jq -r '.[0] \| "\(.status) \(.issue_type)"'` |
| Pre-flight (path free) | `ls -d ../nubecita-<bd-id> 2>/dev/null && echo EXISTS` |
| Pre-claim | `bd update <bd-id> --claim` |
| Create | `bd worktree create ../nubecita-<bd-id> --branch=<type>/<bd-id>-<slug>` |
| List | `bd worktree list` |
| Remove (after merge) | `bd close <bd-id> --reason="..." && bd worktree remove ../nubecita-<bd-id> && bd dolt push` |

## Pairs with

- `bd-workflow` — full bd-driven branch/commit/PR ceremony. After `bd worktree create` succeeds and the user enters the worktree, the rest of the work (commit messages, PR body, `Closes:` footer) follows that skill's conventions.
- `superpowers:dispatching-parallel-agents` — when the worktree is intended for a subagent, brief the agent that bd commands are off-limits inside the worktree (see "bd-isolation gotcha" above).
- `superpowers:using-git-worktrees` — generic worktree skill; this project-local skill takes precedence for nubecita work because it codifies the spotless avoidance and bd-isolation handling.

## Invariants

- **Never** create worktrees under the repo root (`.worktrees/`, `worktrees/`, etc.)
- **Never** issue `bd update`/`bd close` from inside the worktree
- **Never** `cd` into the worktree from the main shell after creating — report the path and stop
- **Never** delete a worktree from inside the worktree itself
