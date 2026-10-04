#!/usr/bin/env bash
# Prevent pushing directly to main
set -euo pipefail

block_push() {
  echo "======================================================================" >&2
  echo "ERROR: Direct push to 'main' is forbidden!" >&2
  echo "All changes must go through a feature branch and a pull request." >&2
  echo "To push your changes, push to a feature branch instead:" >&2
  echo "  git push -u origin <branch-name>" >&2
  echo "======================================================================" >&2
  exit 1
}

# 1. When executed under pre-commit, pre-commit exposes destination ref via environment:
if [ -n "${PRE_COMMIT_REMOTE_BRANCH:-}" ]; then
  case "$PRE_COMMIT_REMOTE_BRANCH" in
    refs/heads/main|main)
      block_push
      ;;
  esac
fi

# 2. When run in an interactive terminal (TTY stdin), do not hang waiting for input:
if [ -t 0 ]; then
  exit 0
fi

# 3. When executed as a native Git pre-push hook, inspect ref-update records from stdin:
# <local_ref> <local_sha> <remote_ref> <remote_sha>
while read -r local_ref local_sha remote_ref remote_sha; do
  case "$remote_ref" in
    refs/heads/main|main)
      block_push
      ;;
  esac
done

exit 0
