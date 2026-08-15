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
# Usage: ./scripts/check_runcatching_cancellation.sh [files...]
#        (no args → scans the whole tracked tree)

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if [ "$#" -gt 0 ]; then
    files=("$@")
else
    mapfile -t files < <(git ls-files '*/src/main/**/*.kt' 'src/main/**/*.kt' 2>/dev/null || true)
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
            # Remember the most recent function declaration seen.
            /(^|[^A-Za-z0-9_])fun[ \t]/ {
                in_suspend = ($0 ~ /(^|[^A-Za-z0-9_])suspend[ \t]+fun[ \t]/) ? 1 : 0
            }
            # An opt-out marker on this line or the previous one clears the flag.
            {
                allowed = ($0 ~ /allow-runcatching:/ || prev ~ /allow-runcatching:/) ? 1 : 0
            }
            /(^|[^A-Za-z0-9_])runCatching[ \t]*[{(]/ {
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
