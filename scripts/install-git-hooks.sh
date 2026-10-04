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

for hook in "$repo_root"/scripts/git-hooks/*; do
  if [ -f "$hook" ]; then
    hook_name="$(basename "$hook")"
    dst="$hooks_dir/$hook_name"
    chmod +x "$hook"
    ln -sf "$hook" "$dst"
    echo "installed: $dst -> $hook"
  fi
done
