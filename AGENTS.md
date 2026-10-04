# Agent Instructions

## Non-Interactive Shell Commands

**ALWAYS use non-interactive flags** with file operations to avoid hanging on confirmation prompts.

Shell commands like `cp`, `mv`, and `rm` may be aliased to include `-i` (interactive) mode on some systems, causing the agent to hang indefinitely waiting for y/n input.

**Use these forms instead:**
```bash
# Force overwrite without prompting
cp -f source dest           # NOT: cp source dest
mv -f source dest           # NOT: mv source dest
rm -f file                  # NOT: rm file

# For recursive operations
rm -rf directory            # NOT: rm -r directory
cp -rf source dest          # NOT: cp -r source dest
```

**Other commands that may prompt:**
- `scp` - use `-o BatchMode=yes` for non-interactive
- `ssh` - use `-o BatchMode=yes` to fail instead of prompting
- `apt-get` - use `-y` flag
- `brew` - use `HOMEBREW_NO_AUTO_UPDATE=1` env var

## Branching & Pull Requests (NEVER Push to `main`)

**NEVER push directly to `main`. ALL code changes destined for `main` MUST go through a feature branch and a Pull Request.**

- **Always work on a feature branch**: Never commit or push directly on `main`. Create a descriptive branch before writing code:
  ```bash
  git checkout -b <branch-name> origin/main
  ```
  Branch naming convention (required by repository beads workflow in `CLAUDE.md:122-126`): `<type>/<bd-id>-<slug>`.
  - `<type>`: Conventional Commit type (`feat`, `fix`, `chore`, `refactor`, `docs`, `test`, etc.) inferred from the bd issue type.
  - `<bd-id>`: Beads issue ID (e.g. `nubecita-aew`).
  - `<slug>`: Kebab-cased title, capped at 50 chars.
  - Example: `feat/nubecita-aew-create-mviviewmodel-base-class`
- **If accidentally on `main` with changes**:
  - Immediately move uncommitted or committed changes to a feature branch:
    ```bash
    git checkout -b <branch-name>
    # If commits were already made on local main, reset local main back to remote:
    git branch -f main origin/main
    ```
- **Push feature branches only**:
  ```bash
  git push -u origin <branch-name>
  ```
- **Open a Pull Request**:
  - Use GitHub CLI (`gh`) to open a PR:
    ```bash
    gh pr create --title "<type>(<scope>): <summary>" --body "<details>"
    ```
- **Never bypass branch protections**: Even if your credentials or token allow bypassing branch protections or direct pushes to `main`, NEVER bypass them.
- **Do not merge without review / CI**: Address review comments, ensure all CI checks pass.
  - **Standalone PRs**: Merge with squash:
    ```bash
    gh pr merge <pr-number> --squash --delete-branch
    ```
  - **Epic stacked PRs** (`gh stack`): Never merge individual child PRs from the GitHub UI or via `gh pr merge`. Merge the entire stack atomically:
    ```bash
    gh stack merge --squash --yes
    ```

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until changes are pushed to a remote feature branch and a pull request is open.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH BRANCH & OPEN PR** - This is MANDATORY:
   ```bash
   bd dolt push
   git push -u origin <branch-name>
   gh pr create --title "<title>" --body "<body>"  # if not already created
   ```
5. **Clean up** - Clear stashes, verify working tree is clean
6. **Verify** - All changes committed, pushed to feature branch, and PR opened
7. **Hand off** - Provide PR link and context for next session

**CRITICAL RULES:**
- **NEVER push directly to `main`** — all code changes MUST go through a feature branch and a pull request.
- Work is NOT complete until `git push -u origin <branch-name>` succeeds AND the pull request is opened (or updated).
- NEVER stop before pushing the branch and opening the PR - that leaves work stranded locally
- NEVER say "ready to push when you are" or "ready to create a PR when you are" - YOU must push the branch and open the PR
- If push or PR creation fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->
