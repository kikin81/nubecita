#!/usr/bin/env bash
#
# Guard: every indeterminate "content is loading" spinner must route through
# the brand :designsystem component net.kikin.nubecita.designsystem.component
# .NubecitaWavyProgressIndicator (the M3 Expressive wavy ring), NOT a raw
# Material 3 CircularProgressIndicator / CircularWavyProgressIndicator.
#
# Out of scope: determinate LINEAR progress bars (LinearWavyProgressIndicator in
# onboarding + the video scrubber) — a different component with no brand wrapper
# — and the morphing LoadingIndicator reserved for NubecitaPullToRefreshBox.
#
# This catches the call sites the wavy-loader migration (nubecita-fffb) would
# otherwise let regress: a new screen reaching for `CircularProgressIndicator()`
# instead of the wrapper.
#
# Scope: production Kotlin (`*/src/main/**/*.kt`) outside the :designsystem
# module — designsystem is where the wrapper lives and is the one place the raw
# experimental M3 indicators (CircularWavyProgressIndicator, the morphing
# LoadingIndicator used by NubecitaPullToRefreshBox, …) are sanctioned.
#
# Sanctioned raw uses elsewhere (determinate progress that drives `progress`,
# an in-button micro-spinner with a tuned strokeWidth, a sub-16dp inline dot the
# waves cannot render) opt out with an inline marker on the call line OR the
# line directly above it:
#
#     // nubecita-allow-raw-progress: determinate — drives `progress`
#     CircularProgressIndicator(progress = { fraction })
#
# Exit non-zero (with the offending file:line list) if any unmarked raw
# indicator is found.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Production Kotlin sources outside :designsystem and any build output.
mapfile -t files < <(
  find . -type f -name '*.kt' \
    -path '*/src/main/*' \
    ! -path './designsystem/*' \
    ! -path '*/build/*' \
    | sort
)

violations=""
for file in "${files[@]}"; do
  [ -n "$file" ] || continue
  # awk walks the file keeping the previous line, so the opt-out marker is
  # honored whether it sits on the call line or immediately above it. The
  # `(Circular|Linear)` prefix is required, so the brand
  # `NubecitaWavyProgressIndicator(` never matches. The trailing `\(` skips
  # bare prose/KDoc mentions of the type name.
  # `block_marker` stays armed across a contiguous `//` comment block, so the
  # opt-out marker is honored whether it sits on the call line, the line
  # directly above, or anywhere in a multi-line comment block above the call.
  # Any non-comment line clears the arm.
  out="$(awk '
    {
      is_comment   = ($0 ~ /^[ \t]*\/\//)
      has_marker   = ($0 ~ /nubecita-allow-raw-progress/)
      is_indicator = ($0 ~ /Circular(Wavy)?ProgressIndicator[ \t]*\(/)

      if (is_indicator && !(has_marker || block_marker)) {
        printf "  %s:%d: %s\n", FILENAME, NR, $0
      }

      if (is_comment) {
        if (has_marker) block_marker = 1
      } else {
        block_marker = 0
      }
    }
  ' "$file")"
  [ -n "$out" ] && violations+="$out"$'\n'
done

if [ -n "$violations" ]; then
  cat >&2 <<'MSG'
✖ Raw indeterminate progress indicator(s) found outside :designsystem.

Use net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
for "content is loading" spinners — the M3 Expressive wavy ring the whole app
shares. Don't re-import the raw Material 3 CircularProgressIndicator /
CircularWavyProgressIndicator. (Determinate LINEAR bars — onboarding/video
scrubber — are a different component and are not covered by this guard.)

If a raw indicator is genuinely required, add an inline opt-out on the call
line or the line directly above it, with a reason:

    // nubecita-allow-raw-progress: <why this one stays raw>

Sanctioned reasons (see NubecitaWavyProgressIndicator.kt for the full rules):
  • determinate progress that drives a `progress` value
  • an in-button micro-spinner with a tuned strokeWidth
  • a sub-16dp inline dot the waves can't render

Offending call sites:
MSG
  printf '%s' "$violations" >&2
  exit 1
fi
