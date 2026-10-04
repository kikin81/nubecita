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

# 2. Install repository-tracked custom hooks from scripts/git-hooks
for hook in "$repo_root"/scripts/git-hooks/*; do
  if [ -f "$hook" ]; then
    hook_name="$(basename "$hook")"
    dst="$hooks_dir/$hook_name"
    chmod +x "$hook"

    if [ -e "$dst" ] || [ -L "$dst" ]; then
      # Already linked
      if [ "$(readlink "$dst" 2>/dev/null || true)" = "$hook" ]; then
        echo "already installed: $dst -> $hook"
        continue
      fi

      # Do not overwrite pre-commit dispatcher hooks (e.g. pre-push)
      if grep -q "pre-commit" "$dst" 2>/dev/null; then
        echo "preserving pre-commit dispatcher in $dst (hook runs via .pre-commit-config.yaml)"
        continue
      fi

      # Preserve any other pre-existing hook as .legacy
      if [ ! -e "$hooks_dir/$hook_name.legacy" ]; then
        mv "$dst" "$hooks_dir/$hook_name.legacy"
        echo "preserved existing hook as $hooks_dir/$hook_name.legacy"
      fi
    fi

    ln -sf "$hook" "$dst"
    echo "installed: $dst -> $hook"
  fi
done
