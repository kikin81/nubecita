#!/usr/bin/env bash
# Installs repo-tracked git hooks into .git/hooks/ (alongside pre-commit framework hooks).
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
src="$repo_root/scripts/git-hooks/prepare-commit-msg"
dst="$repo_root/.git/hooks/prepare-commit-msg"

chmod +x "$src"
ln -sf "../../scripts/git-hooks/prepare-commit-msg" "$dst"
echo "installed: $dst -> scripts/git-hooks/prepare-commit-msg"
