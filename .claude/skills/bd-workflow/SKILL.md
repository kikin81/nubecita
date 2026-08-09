---
name: bd-workflow
description: Use when starting or finishing a beads (bd) task in this repo. Handles claiming an issue, creating a Conventional-Commit-prefixed branch with the bd id embedded, and opening a PR with a `Closes:` footer — as a standalone PR for a standalone issue, or as a `gh stack` PR stack for an epic's children. Trigger on phrases like "start nubecita-xxx", "pick up <bd-id>", "open a PR for this", "add this to the stack", "land the stack", or "finish this task".
---

Automate the bd-driven branch/commit/PR ceremony documented in `CLAUDE.md`'s Workflow section. Three flows: **start** (claim + branch), **finish** (push + PR), and **land** (merge an epic's stack). Never close the bd issue here — closure happens after merge.

## Two landing paths

The bd issue's shape picks the path. Determine it in the start flow and carry it through:

- **Standalone issue** (no epic parent) → one branch, one PR to `main`, squash-merge. Unchanged from before.
- **Child of an epic** → one branch and one PR **in the epic's `gh stack`**, stacked on `main`. Nothing merges until the whole epic lands via `gh stack merge`, so the epic cuts exactly one semantic release.

Detect in **two** steps. `parent` is a bare id string (`"nubecita-1fy"`), not a nested object, so the parent's type needs its own lookup — a non-empty `parent` alone does not mean the issue is an epic child:

```bash
parent=$(bd show <id> --json | jq -r '.[0].parent // empty')
[ -n "$parent" ] && bd show "$parent" --json | jq -r '.[0].issue_type'
```

`epic` → stacked path. Empty `parent`, or a parent of any other type → standalone path. If the repo has no stack yet for that epic, the start flow creates it.

## When to use

- **Start flow** — user asks to begin work on a bd id: "start nubecita-aew", "let's pick up <id>", "claim this one".
- **Finish flow** — user is done committing and wants to push/open a PR: "open the PR", "ship it", "finish this task".
- **Land flow** — an epic's stack is complete and green: "land the stack", "merge the epic", "ship the feature".

If the user has not chosen a bd id yet, run `bd ready` and offer the top candidates before starting.

## Start flow

**Preconditions** — verify before doing anything destructive:

1. Run `git diff --quiet && git diff --cached --quiet` — tracked tree must be clean. Untracked files are OK.
2. Run `bd show <id> --json` and parse with the Bash tool piped through `jq`. Verify `.[0]` exists, `status != "closed"`, and `issue_type != "epic"`. If it's an epic, refuse and point the user at a child issue — the epic is the *stack*, never a branch.
3. Determine the path (see **Two landing paths**) and check the starting branch accordingly:
   - **Standalone** → `git branch --show-current` must be `main` (or a base the user specifies). If not, stop and ask.
   - **Epic child, no stack yet** → must also be on `main`; this child becomes the bottom of the stack.
   - **Epic child, stack exists** → must be on the stack's **top** branch; `gh stack top` checks it out for you. The new child stacks on the work already in flight. If the user wants it based on something lower instead, confirm before proceeding — that's a different stack shape.

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

**Execute — standalone:**

```bash
git checkout -b <branch>
bd update <id> --claim
```

**Execute — epic child.** `gh stack init` / `gh stack add` create the branch themselves; do NOT `git checkout -b` first.

```bash
# first child of the epic — starts the stack on main
gh stack init <branch>

# every later child — stacks on the current top branch
gh stack add <branch>

bd update <id> --claim
```

Then report: branch name, bd id + title, a suggested first commit subject (`<prefix>: <title>`), and — for an epic child — the stack's current shape from `gh stack view`.

## Finish flow

**Preconditions:**

1. Current branch is not `main`.
2. Tree is clean (`git diff --quiet && git diff --cached --quiet`).
3. At least one commit ahead of base. The base differs by path: `main` for a standalone branch or the bottom of a stack, otherwise the branch below it in the stack. `git rev-list --count <base>..HEAD` > 0.
4. Infer the bd id from the branch name (`<type>/<bd-id>-<slug>`) or accept one from the user.
5. Read the stack shape with `gh stack view --json` (add `--json` to parse; bare `gh stack view` is for showing the user). It tells you whether the current branch is in a stack at all, which branch is below it (the base for step 3), and whether it is the stack's **top**. Do NOT probe with `gh stack top` / `gh stack down` — those are navigation commands that check out a different branch, not read-only queries.

**Pre-PR verification** — how much to run depends on where the branch sits. Running the full gate on every branch of a stack is wasted work; only the top represents the finished feature.

| Branch | Gate |
|---|---|
| Standalone branch | Steps 1–4 below (full) |
| Stack child, not the top | Step 2 only (`lintDebug` on touched modules — lint compiles, so a broken layer still fails at the layer that broke it) |
| Stack **top**, before landing | Steps 1–4, with step 4's diff taken across the **whole stack** |

Run these before pushing. If any fails, stop and fix the underlying issue; never bypass a failing pre-commit hook with `git commit --no-verify`:

1. `./gradlew :app:assembleDebug` — proves the app graph still links. Cheaper than the full build and catches missing deps / Hilt graph breaks the IDE wouldn't flag.
2. `./gradlew :<changed-module>:lintDebug` for each Android Gradle module touched (e.g. `:feature:feed:impl:lintDebug`). Lint catches Compose-rule violations (stability, unused state, modifier order) and other correctness issues that compilation and unit tests don't. Run on the specific modules rather than the umbrella `lint` task so the loop stays fast. Modules outside the main Android build (e.g. `build-logic`, plain JVM libs) have no `lintDebug` task — skip them here, the convention plugins already gate them at compile time.
3. Pre-commit hook on the commit itself already ran spotless + commitlint + secret scan — no extra step needed here. If the hook reports a failure, fix the underlying issue rather than re-running with `--no-verify`.
4. **Compose review gate.** Run the detector below; it decides whether a Compose-specific review is warranted before the PR opens:

   ```bash
   git diff origin/main...HEAD -- '*.kt' | grep -E '^\+' | grep -q '@Composable' \
     && echo "compose-touched" || echo "headless"
   ```

   - `compose-touched` → invoke the **`compose-expert` Review Mode** skill on this branch's diff (it runs the recomposition / stability / modifier / M3-motion / lists-keys checklist) and fold any Critical findings into the branch before pushing. Suggestions are optional.
   - `headless` → **skip it.** A diff with no added `@Composable` lines (repository, mapper, DI, test-only changes) yields zero Compose signal — running the reviewer just burns tokens. `./gradlew :<module>:lintDebug` from step 2 already covers Compose lint rules for any incidental UI touch.

   Rationale: gate the heavyweight Compose lens on the one cheap signal that predicts whether it'll find anything. Empirically, PR #340 (headless send-path) was correctly skipped by this gate.

   On a stack's **top** branch, `origin/main...HEAD` is already the cumulative diff of every branch below — which is what you want. A stack's Compose surface is the union of its children, so review it once, whole, rather than per child PR. Do NOT narrow this to the top PR's own diff (`<branch-below>...HEAD`).

**Execute — standalone:**

```bash
git push -u origin <branch>
gh pr create --base main \
  --title "<first-commit-subject>" \
  --body  "Closes: <bd-id>"
```

**Execute — stack child.** `gh stack submit` pushes every branch, creates any missing PRs, and re-points the base of existing ones in a single call. Never `git push` a stack branch by hand and never `gh pr create` for one — a hand-created PR isn't part of the stack on GitHub and won't be included in the atomic merge.

```bash
gh stack submit --auto --open
```

`--auto` skips the interactive editor; `--open` marks new PRs ready for review instead of draft. Then fix up each newly created PR's title and body, since `--auto` generates them:

```bash
gh pr edit <pr-number> --title "<first-commit-subject>" --body "Closes: <bd-id>"
```

Use the **first** commit on the branch as the PR title (`git log --reverse --format=%s <base>..HEAD | head -1`, where `<base>` is `main` or the branch below) — that's the convention for squash-merges, and in a stack that title becomes the branch's commit subject on `main`. If the user wants drafts, drop `--open` (or add `--draft` on the standalone path).

If review feedback landed on a **lower** branch, restack before submitting:

```bash
gh stack sync
gh stack submit --auto --open
```

Every restack re-fires CI on all branches above, so batch fixes rather than pushing one at a time.

**Post-PR — tag Copilot for review:**

```bash
gh api -X POST /repos/<owner>/<repo>/pulls/<pr-number>/requested_reviewers \
  -f 'reviewers[]=Copilot'
```

The GitHub Copilot review bot is added via the literal handle `Copilot` (case-sensitive). `gh pr edit --add-reviewer copilot-pull-request-reviewer` and the GraphQL `requestReviews` mutation both fail — the REST endpoint with the `Copilot` handle is the only path that works for this repo.

**In a stack this call is mandatory on every PR**, not just a nicety. The `Copilot review for default branch` ruleset only fires on PRs targeting `main` — that's PR 1 alone. Children 2..N target the branch below them and get **no** automatic review unless requested explicitly.

**Post-PR — monitor CI status AND review comments between turns:**

Schedule a recurring poll via `CronCreate` so CI checks run in the background without blocking a shell or stealing the user's attention. The same poll checks for unresolved review threads: CI going green is not the same as the PR being ready, and a review that lands after the last CI poll is otherwise invisible until the user asks.

The poll runs two commands. CI:

```bash
gh pr checks <PR-NUMBER>
```

Unresolved review threads — note the GraphQL query needs **seven** closing braces, and inside the single-quoted shell argument the inner double quotes are NOT escaped:

```bash
gh api graphql -f query='{ repository(owner:"<OWNER>",name:"<REPO>"){ pullRequest(number:<PR-NUMBER>){ reviewThreads(last:50){ nodes { id isResolved comments(first:1){ nodes { databaseId author{login} path body } } } } } } }' --jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved==false)] | length'
```

Pass both to `CronCreate` with this decision logic in the prompt (write the commands into the prompt verbatim from the blocks above — do not add backslash escaping, which reaches the shell as literal backslashes and fails to parse):

- Checks still pending **and** the unresolved count unchanged since the last poll → say nothing, wait for the next poll.
- All checks terminal (pass / fail / skipping / cancel) **and** zero unresolved threads → cancel the cron with `CronDelete` and report `✅ CI passed — N/N green, no open review threads`.
- Any check failed → fetch logs via `gh run view <RUN-ID> --log-failed` and propose a fix. Do **not** cancel.
- Unresolved threads present → report how many, from whom, and a one-line summary of each. Do **not** cancel; the PR is not ready.

Cancel the cron only when CI is terminal **and** every thread is resolved. A green PR with open threads still blocks merge (see the merge rule above — every thread counts, bots included), so a poll that stops at green trains the wrong reflex.

Tell the user once:

```
👀 Monitoring PR #<pr> — CI checks and review threads. I'll report back when both are clear.
```

Do NOT use `gh pr checks --watch` — reprints the full table each poll, drowns the conversation. Do NOT use a background bash polling loop — blocks a shell and produces noisy output. Do NOT dump the full check list on success: just `✅ CI passed — N/N checks green` (or `❌ N of M failed`, with the failing names).

In a stack, poll **every** PR in it (`gh stack view` lists them), not just the one you just submitted — a restack re-fires CI on every branch above, so a lower PR going red is the common failure and it's invisible if you only watch the top.

After the PR exists, print its URL and remind the user: "bd issue stays open until the PR merges; run `bd close <id>` after merge." For a stack child, add: "nothing merges until the whole epic lands — see the land flow."

## Land flow

Only for an epic's stack. Runs once, when every child is written and reviewed.

**Preconditions** — all must hold; do not merge past a failure:

1. Every PR in `gh stack view` is open, non-draft, and CI-green.
2. Zero unresolved review threads on **every** PR in the stack (same GraphQL query as above, run per PR). Bots count.
3. The full pre-PR gate has been run on the stack's **top** branch — `:app:assembleDebug`, touched-module `lintDebug`, and the compose-expert review over `origin/main...HEAD`.
4. Every child PR's title is a valid Conventional Commit, and at least one is `feat:` if this epic should reach Play (the release type is the maximum across the stack; an all-`chore:`/`refactor:` stack cuts no version).

**Execute:**

```bash
gh stack merge --squash --yes
```

This is atomic: every PR in the stack merges into `main` in one all-or-nothing operation, or none do. The whole epic reaches `main` in a single push → one `Release` run → **one** semantic version.

**Never** merge a child PR individually — not with `gh pr merge`, not from the GitHub UI. A lone merge lands on `main` by itself and cuts its own release, which is the exact regression stacks exist to prevent.

**Post-merge:**

```bash
gh run list --workflow=release.yaml --limit 3   # confirm ONE run, not N
gh stack sync --prune                           # drop local branches for the merged PRs
bd close <child-1> <child-2> ... <epic-id>
```

If that run count is **N instead of 1**, the atomic merge is landing sequential ref updates and the whole one-release premise is broken. Stop, tell the user, and switch the next epic to the integration-branch fallback documented in `CLAUDE.md` (stack trunk = `feat/<epic-id>-<slug>`, landed with one rebase-merge PR to `main`).

Then archive the openspec change for the feature.

## Invariants

- **Never** run `bd close` in the start or finish flow. The user (or a post-merge automation) decides when. The land flow is the one place closure is proposed, and only after the merge succeeded.
- **Never** force-push, amend, or rewrite history by hand. `gh stack sync` / `gh stack rebase` do rewrite the branches above — that is the tool's job and is expected; doing it manually with `git rebase` + `git push --force` desynchronizes the stack on GitHub.
- **Never** branch from a dirty tree — stop and tell the user to commit or stash first.
- **Never** branch off an epic. The epic is the stack; its children are the branches.
- **Never** merge a stack's PR outside `gh stack merge`, and never `gh pr create` a branch that belongs to a stack.
- All three flows are idempotent to retry: if the branch already exists, report and stop; `gh pr create` errors cleanly if a PR already exists; `gh stack submit` is inherently re-runnable and just updates what's there.

## Commit messages (reminder)

Conventional Commits, one bd issue per branch, bd id in the footer:

```
feat(mvi): add MviViewModel base class and marker interfaces

Short body explaining the why.

Refs: nubecita-aew
```

Use `Refs:` on WIP commits. `Closes: <bd-id>` is set by this skill in the PR body only — don't duplicate it into individual commits, or squash-merge will double-close.
