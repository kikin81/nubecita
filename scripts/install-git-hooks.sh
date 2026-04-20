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

src="$repo_root/scripts/git-hooks/prepare-commit-msg"
dst="$hooks_dir/prepare-commit-msg"

mkdir -p "$hooks_dir"
chmod +x "$src"
ln -sf "$src" "$dst"
echo "installed: $dst -> $src"
