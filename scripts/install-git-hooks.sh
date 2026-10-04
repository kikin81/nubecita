#!/usr/bin/env bash
# Installs repo-tracked git hooks into the repo's configured hooks directory
# (honors core.hooksPath; works under linked worktrees).
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
hooks_path="$(git config --get core.hooksPath || true)"
if [ -n "$hooks_path" ]; then
  case "$hooks_path" in
    /*) hooks_dir="$hooks_path" ;;
    *)  hooks_dir="$repo_root/$hooks_path" ;;
  esac
else
  hooks_dir="$(git rev-parse --git-path hooks)"
  case "$hooks_dir" in
    /*) ;;
    *) hooks_dir="$repo_root/$hooks_dir" ;;
  esac
fi

mkdir -p "$hooks_dir"

# 1. Install pre-commit hooks when pre-commit is available
if command -v pre-commit >/dev/null 2>&1; then
  echo "Installing pre-commit hooks..."
  pre-commit install --hook-type pre-commit
  pre-commit install --hook-type commit-msg
  pre-commit install --hook-type pre-push
fi

# 2. Install repository-tracked custom hooks
src="$repo_root/scripts/git-hooks/prepare-commit-msg"
dst="$hooks_dir/prepare-commit-msg"
chmod +x "$src"
ln -sf "$src" "$dst"
echo "installed: $dst -> $src"
