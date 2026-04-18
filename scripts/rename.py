#!/usr/bin/env python3
"""One-shot rename script for the android-template.

Rewrites the placeholder package (``com.example.myapp``) and app-name variants
(``My App`` / ``MyApplication`` / ``MyApp`` / ``myapp``) across the repo
produced by ``android create empty-activity``. Intended to be run exactly once,
immediately after cloning from the template.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

# Placeholders baked into the `android create empty-activity` output.
# Note: `MyApplication` is the actual class/theme prefix the generator emits
# (e.g. `MyApplicationTheme`, `Theme.MyApplication`); bare `MyApp` does not
# appear in current output but is kept as a defensive fallback. `MyApplication`
# must be substituted BEFORE `MyApp` (longest-match-first) or
# `MyApplicationTheme` would become `{pascal}licationTheme`.
OLD_PACKAGE_DOTTED = "com.example.myapp"
OLD_PACKAGE_SLASHED = "com/example/myapp"
OLD_APP_NAME = "My App"
OLD_APPLICATION = "MyApplication"
OLD_PASCAL = "MyApp"
OLD_LOWER = "myapp"

PACKAGE_RE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
PASCAL_RE = re.compile(r"^[A-Z][A-Za-z0-9]*$")
LOWER_RE = re.compile(r"^[a-z][a-z0-9]*$")


def _parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rename the template's placeholder identifiers in one shot.",
    )
    parser.add_argument("--package", required=True, help="e.g. com.acme.widget")
    parser.add_argument("--app-name", required=True, help='e.g. "Acme Widget"')
    parser.add_argument("--app-name-pascal", required=True, help="e.g. AcmeWidget")
    parser.add_argument("--app-name-lower", required=True, help="e.g. acmewidget")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--keep-script", action="store_true")
    args = parser.parse_args(argv)

    if not PACKAGE_RE.match(args.package):
        parser.error("--package must be a dotted lowercase Java package (e.g. com.acme.widget)")
    if not args.app_name.strip():
        parser.error("--app-name must be non-empty")
    if not PASCAL_RE.match(args.app_name_pascal):
        parser.error("--app-name-pascal must start with uppercase letter, then alphanumerics only")
    if not LOWER_RE.match(args.app_name_lower):
        parser.error("--app-name-lower must start with lowercase letter, then lowercase alphanumerics only")

    return args


def _preflight(repo: Path) -> None:
    java_root = repo / "app" / "src" / "main" / "java" / "com" / "example" / "myapp"
    if not java_root.is_dir():
        raise SystemExit(
            f"pre-flight failed: expected directory {java_root} "
            f"(no com.example.myapp template markers — already renamed?)"
        )


REWRITE_SUFFIXES = {".kt", ".kts", ".xml", ".md", ".properties", ".toml"}
SKIP_DIR_NAMES = {".git", ".gradle", ".idea", "build", "node_modules"}
SELF_NAME = "rename.py"

CHECKLIST = """
Next steps (complete these manually):

  1. Set the git remote:
       git remote add origin git@github.com:<you>/<repo>.git
  2. Configure branch protection on main: required checks (lint, test, build),
     require PR before merge, linear history, squash-merge only.
  3. Enable auto-delete of head branches after merge.
  4. Enable Renovate on the repository (renovate.json is committed).
  5. Add any release secrets required by open-turo/actions-jvm/release
     (see .github/workflows/release.yaml).
  6. Replace the placeholder icon in app/src/main/res/mipmap-*/ with your own.
  7. Update the README title/description for your project.
"""


def _finalize(repo: Path, keep_script: bool) -> None:
    print(CHECKLIST)
    if keep_script:
        return
    for name in ("rename.py", "test_rename.py"):
        target = repo / "scripts" / name
        if target.exists():
            target.unlink()
    root_copy = repo / "rename.py"
    if root_copy.exists():
        root_copy.unlink()


def _rewrite_files(repo: Path, args: argparse.Namespace, dry_run: bool = False) -> None:
    dotted_new = args.package
    slashed_new = "/".join(args.package.split("."))
    # Order matters: longest/most-specific patterns first. `MyApplication` must
    # come before `MyApp` so `MyApplicationTheme` doesn't mangle into
    # `{pascal}licationTheme`.
    substitutions = [
        (OLD_PACKAGE_DOTTED, dotted_new),
        (OLD_PACKAGE_SLASHED, slashed_new),
        (OLD_APP_NAME, args.app_name),
        (OLD_APPLICATION, args.app_name_pascal),
        (OLD_PASCAL, args.app_name_pascal),
        (OLD_LOWER, args.app_name_lower),
    ]
    for dirpath, dirs, files in os.walk(repo):
        dirs[:] = [d for d in dirs if d not in SKIP_DIR_NAMES]
        for name in files:
            if name == SELF_NAME:
                continue
            path = Path(dirpath) / name
            if path.suffix not in REWRITE_SUFFIXES:
                continue
            try:
                original = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            updated = original
            for old, new in substitutions:
                updated = updated.replace(old, new)
            if updated == original:
                continue
            if dry_run:
                print(f"[dry-run] would rewrite {path.relative_to(repo)}")
                continue
            path.write_text(updated, encoding="utf-8")


def _move_dirs(repo: Path, package: str, dry_run: bool = False) -> None:
    new_parts = package.split(".")
    for sub in ("main", "test", "androidTest"):
        old_root = repo / "app" / "src" / sub / "java" / "com" / "example" / "myapp"
        if not old_root.exists():
            continue
        new_root = repo / "app" / "src" / sub / "java" / Path(*new_parts)
        if dry_run:
            print(f"[dry-run] would move {old_root} -> {new_root}")
            continue
        new_root.parent.mkdir(parents=True, exist_ok=True)
        old_root.rename(new_root)
    if dry_run:
        return
    # Clean up now-empty `com/example/` stub parents.
    for sub in ("main", "test", "androidTest"):
        stub = repo / "app" / "src" / sub / "java" / "com" / "example"
        if stub.exists() and not any(stub.iterdir()):
            stub.rmdir()
            parent = stub.parent
            if parent.name == "com" and parent.exists() and not any(parent.iterdir()):
                parent.rmdir()


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    repo = Path.cwd()
    _preflight(repo)
    if args.dry_run:
        print("DRY RUN — no changes will be written.")
    _move_dirs(repo, args.package, dry_run=args.dry_run)
    _rewrite_files(repo, args, dry_run=args.dry_run)
    if args.dry_run:
        print("DRY RUN complete.")
        return 0
    _finalize(repo, keep_script=args.keep_script)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
