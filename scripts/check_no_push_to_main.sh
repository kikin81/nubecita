#!/usr/bin/env bash
# Prevent pushing directly to main
set -euo pipefail

# Git pre-push hook receives lines on stdin:
# <local_ref> <local_sha> <remote_ref> <remote_sha>
while read -r local_ref local_sha remote_ref remote_sha; do
  if [ "$remote_ref" = "refs/heads/main" ]; then
    echo "======================================================================" >&2
    echo "ERROR: Direct push to 'main' is forbidden!" >&2
    echo "All changes must go through a feature branch and a pull request." >&2
    echo "To push your changes, push to a feature branch instead:" >&2
    echo "  git push -u origin <branch-name>" >&2
    echo "======================================================================" >&2
    exit 1
  fi
done

exit 0
