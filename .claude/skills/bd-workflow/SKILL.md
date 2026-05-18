---
name: bd-workflow
description: Use when starting or finishing a beads (bd) task in this repo. Handles claiming an issue, creating a Conventional-Commit-prefixed branch with the bd id embedded, and opening a PR with a `Closes:` footer. Trigger on phrases like "start nubecita-xxx", "pick up <bd-id>", "open a PR for this", or "finish this task".
---

Automate the bd-driven branch/commit/PR ceremony documented in `CLAUDE.md`'s Workflow section. Two flows: **start** (claim + branch) and **finish** (push + PR). Never close the bd issue here — closure happens after merge.

## When to use

- **Start flow** — user asks to begin work on a bd id: "start nubecita-aew", "let's pick up <id>", "claim this one".
- **Finish flow** — user is done committing and wants to push/open a PR: "open the PR", "ship it", "finish this task".

If the user has not chosen a bd id yet, run `bd ready` and offer the top candidates before starting.

## Start flow

**Preconditions** — verify before doing anything destructive:

1. Run `git branch --show-current` — must be `main` (or whatever base the user specifies). If not, stop and ask.
2. Run `git diff --quiet && git diff --cached --quiet` — tracked tree must be clean. Untracked files are OK.
3. Run `bd show <id> --json` and parse with the Bash tool piped through `jq`. Verify `.[0]` exists, `status != "closed"`, and `issue_type != "epic"`. If it's an epic, refuse and point the user at a child issue.

**Derive branch name:**

- `prefix` (Conventional Commit type) from bd `issue_type`:
  - `feature` → `feat`
  - `bug` → `fix`
  - `chore` or `task` → `chore`
  - `decision` → `docs`
  - User may override (e.g., a bd `task` that's actually a `feat`). Ask if the mapping seems wrong for the issue's nature.
- `slug` = lowercase title, non-alphanumerics → `-`, squeeze repeats, trim, cap at 50 chars.

  ```bash
  printf '%s' "$title" | tr '[:upper:]' '[:lower:]' \
    | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//' \
    | cut -c1-50 | sed -E 's/-+$//'
  ```
- Final: `<prefix>/<bd-id>-<slug>`.

**Execute:**

```bash
git checkout -b <branch>
bd update <id> --claim
```

Then report: branch name, bd id + title, and a suggested first commit subject (`<prefix>: <title>`).

## Finish flow

**Preconditions:**

1. Current branch is not `main`.
2. Tree is clean (`git diff --quiet && git diff --cached --quiet`).
3. At least one commit ahead of base: `git rev-list --count main..HEAD` > 0.
4. Infer the bd id from the branch name (`<type>/<bd-id>-<slug>`) or accept one from the user.

**Pre-PR verification** — run these before pushing. If any fails, stop and fix the underlying issue; never bypass a failing pre-commit hook with `git commit --no-verify`:

1. `./gradlew :app:assembleDebug` — proves the app graph still links. Cheaper than the full build and catches missing deps / Hilt graph breaks the IDE wouldn't flag.
2. `./gradlew <changed-module>:lintDebug` for each Android Gradle module touched. Lint catches Compose-rule violations (stability, unused state, modifier order) and other correctness issues that compilation and unit tests don't. Run on the specific modules rather than the umbrella `lint` task so the loop stays fast. Modules outside the main Android build (e.g. `build-logic`, plain JVM libs) have no `lintDebug` task — skip them here, the convention plugins already gate them at compile time.
3. Pre-commit hook on the commit itself already ran spotless + commitlint + secret scan — no extra step needed here. If the hook reports a failure, fix the underlying issue rather than re-running with `--no-verify`.

**Execute:**

```bash
git push -u origin <branch>
gh pr create --base main \
  --title "<first-commit-subject>" \
  --body  "Closes: <bd-id>"
```

Use the **first** commit on the branch as the PR title (`git log --reverse --format=%s main..HEAD | head -1`) — that's the convention for squash-merges. If the user wants a draft PR, add `--draft`.

**Post-PR — tag Copilot for review:**

```bash
gh api -X POST /repos/<owner>/<repo>/pulls/<pr-number>/requested_reviewers \
  -f 'reviewers[]=Copilot'
```

The GitHub Copilot review bot is added via the literal handle `Copilot` (case-sensitive). `gh pr edit --add-reviewer copilot-pull-request-reviewer` and the GraphQL `requestReviews` mutation both fail — the REST endpoint with the `Copilot` handle is the only path that works for this repo.

**Post-PR — monitor CI status between turns:**

Schedule a recurring poll via `CronCreate` so CI checks run in the background without blocking a shell or stealing the user's attention.

```
CronCreate(cron: "*/3 * * * *", prompt: "Check CI status for PR #<PR-NUMBER>. Run: gh pr checks <PR-NUMBER>. If any check is still pending, say nothing and wait for the next poll. If ALL checks have completed (every line shows pass, fail, skipping, or cancel), cancel this cron job with CronDelete, then report concisely: count of passed/failed checks. If any failed, fetch logs via `gh run view <RUN-ID> --log-failed` and propose a fix.")
```

Tell the user once:

```
👀 Monitoring CI for PR #<pr>. I'll report back when all checks reach a terminal state.
```

Do NOT use `gh pr checks --watch` — reprints the full table each poll, drowns the conversation. Do NOT use a background bash polling loop — blocks a shell and produces noisy output. Do NOT dump the full check list on success: just `✅ CI passed — N/N checks green` (or `❌ N of M failed`, with the failing names).

After `gh pr create` succeeds, print the PR URL and remind the user: "bd issue stays open until the PR merges; run `bd close <id>` after merge."

## Invariants

- **Never** run `bd close` in either flow. The user (or a post-merge automation) decides when.
- **Never** force-push, amend, or rewrite history here.
- **Never** branch from a dirty tree — stop and tell the user to commit or stash first.
- Both flows are idempotent to retry: if the branch already exists, report and stop; if a PR already exists for the branch, `gh pr create` will error cleanly and the user can `gh pr view` instead.

## Commit messages (reminder)

Conventional Commits, one bd issue per branch, bd id in the footer:

```
feat(mvi): add MviViewModel base class and marker interfaces

Short body explaining the why.

Refs: nubecita-aew
```

Use `Refs:` on WIP commits. `Closes: <bd-id>` is set by this skill in the PR body only — don't duplicate it into individual commits, or squash-merge will double-close.
