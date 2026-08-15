#!/usr/bin/env bash
#
# Guard: no bare `runCatching` inside a `suspend` function.
#
# `kotlin.runCatching` catches Throwable, and CancellationException is a
# Throwable. Inside a coroutine that means a CANCELLED call — the user
# navigated away, the ViewModel was cleared — comes back as
# `Result.failure(CancellationException)` instead of unwinding. Three
# consequences, none visible at the call site:
#
#   1. Cooperative cancellation stops working: code after the cancellation
#      point keeps running on a coroutine that is already dead.
#   2. The user is shown an error for a request they themselves ended.
#   3. Repositories log these at WARN, CrashlyticsTree forwards every WARN as a
#      breadcrumb, and the false-positive "failed" entries evict the
#      breadcrumbs that actually precede a crash.
#
# Use `net.kikin.nubecita.core.common.coroutines.runCatchingCancellable`
# instead — same shape, rethrows CancellationException.
#
# This exists because the bug recurred twice in one day (nubecita-mrvv), the
# second time in brand-new code written hours after the issue was filed. A
# comment was not enough.
#
# Scope: production Kotlin (`*/src/main/**/*.kt`). Tests are exempt — the
# helper's own test asserts on stdlib runCatching's behaviour deliberately.
#
# Opt out for a genuinely non-cancellable block with an inline marker on the
# call line or the line directly above it:
#
#     // allow-runcatching: <reason>
#
# Known limitations (both verified against this repo, both fail safe):
#
#   1. A `suspend` modifier on its own line, split from `fun`, would not be
#      seen. Unreachable here: ktlint rejects that formatting outright
#      (`spotlessKotlinCheck` fails on it) and `spotlessApply` rewrites it to
#      `suspend fun` on one line, after which this guard does flag the body.
#      Spotless gates both pre-commit and CI, so such code cannot land. This
#      guard therefore DEPENDS on spotless staying in the pipeline.
#
#   2. Brace nesting is not tracked, so the suspend flag survives past the end
#      of a function until the next `fun` declaration. A `runCatching` in, say,
#      a property initialiser sitting directly below a suspend function is
#      flagged spuriously. Currently zero occurrences in the tree; if one
#      appears it fails loudly with the opt-out marker in the message, which is
#      the right way round — a false positive costs one comment, a false
#      negative ships the bug.
#
# Tracking brace depth in awk would trade those for a subtler class of bug, so
# it stays line-oriented on purpose.
#
# Usage: ./scripts/check_runcatching_cancellation.sh [files...]
#        (no args → scans the whole tracked tree)

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if [ "$#" -gt 0 ]; then
    files=("$@")
else
    # Only `*/src/main/**` — every module lives under a directory, and the
    # path filter below rejects a root-level src/main anyway, so collecting it
    # would silently drop those files rather than scan them.
    mapfile -t files < <(git ls-files '*/src/main/**/*.kt' 2>/dev/null || true)
fi

violations=0

for f in "${files[@]}"; do
    [ -f "$f" ] || continue
    case "$f" in
        */src/main/*) ;;
        *) continue ;;
    esac

    # Track the nearest enclosing `fun` declaration and whether it is suspend.
    # A `runCatching` is flagged only when that nearest declaration is suspend.
    while IFS=: read -r lineno line; do
        [ -n "$lineno" ] || continue
        printf '%s:%s: %s\n' "$f" "$lineno" "$line"
        violations=$((violations + 1))
    done < <(
        awk '
            {
                # Work on a comment-stripped copy so a `// fun fact` cannot be
                # mistaken for a declaration (which would silently clear the
                # suspend flag and disable the check for the rest of the file).
                code = $0
                sub(/[^:]\/\/.*/, "", code)
                if (code ~ /^[ \t]*\/\//) code = ""
            }
            # Track the nearest function declaration. Modifiers may sit between
            # `suspend` and `fun` — `private suspend inline fun` is real Kotlin —
            # so test for `suspend` appearing anywhere before `fun`, not
            # immediately before it.
            code ~ /(^|[^A-Za-z0-9_])fun[ \t(]/ {
                head = code
                sub(/(^|[^A-Za-z0-9_])fun[ \t(].*/, "", head)
                in_suspend = (head ~ /(^|[^A-Za-z0-9_])suspend([^A-Za-z0-9_]|$)/) ? 1 : 0
            }
            # An opt-out marker on this line or the previous one clears the flag.
            {
                allowed = ($0 ~ /allow-runcatching:/ || prev ~ /allow-runcatching:/) ? 1 : 0
            }
            code ~ /(^|[^A-Za-z0-9_])runCatching[ \t]*[{(]/ {
                if (in_suspend == 1 && allowed == 0) {
                    printf "%d:%s\n", NR, $0
                }
            }
            { prev = $0 }
        ' "$f"
    )
done

if [ "$violations" -gt 0 ]; then
    cat >&2 <<EOF

✗ $violations bare runCatching call(s) inside a suspend function.

  runCatching swallows CancellationException, which breaks cooperative
  cancellation, shows the user an error for a request they cancelled, and
  fills Crashlytics breadcrumbs with false-positive failures.

  Replace with:
      import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
      runCatchingCancellable { ... }

  If the block genuinely cannot be cancelled, opt out on the line above:
      // allow-runcatching: <reason>
EOF
    exit 1
fi

echo "✓ no bare runCatching inside a suspend function"
