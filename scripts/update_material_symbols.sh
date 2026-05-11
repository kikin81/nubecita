#!/usr/bin/env bash
#
# Subsets the upstream Material Symbols Rounded variable font down to
# only the codepoints declared in NubecitaIconName.kt, and writes the
# result to designsystem/src/main/res/font/material_symbols_rounded.ttf.
#
# Re-run whenever NubecitaIconName gains or loses an entry. The
# generated font is checked in alongside the enum change as one
# commit so reviewers see the subset font diff.
#
# Why a script and not a Gradle task: requiring `python3` + fonttools
# on every CI runner and contributor machine is fragile. Subsetting
# is a one-shot operation per icon-list change; pre-computing keeps
# the Android build pure-Kotlin.
#
# Requirements:
#   - python3 with fonttools installed (pip install fonttools brotli)
#   - curl (to fetch the upstream font on first run / cache miss)
#
# Usage:
#   ./scripts/update_material_symbols.sh

set -euo pipefail

if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required to fetch the upstream font. Install via your package manager." >&2
    exit 1
fi

if ! command -v pyftsubset >/dev/null 2>&1; then
    cat >&2 <<EOF
pyftsubset (from fonttools) not found. Install with:
    python3 -m pip install --user fonttools brotli
Then re-run this script.
EOF
    exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
ENUM_FILE="$REPO_ROOT/designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt"
OUTPUT_FONT="$REPO_ROOT/designsystem/src/main/res/font/material_symbols_rounded.ttf"
CACHE_DIR="$REPO_ROOT/build/icon-cache"
UPSTREAM_FONT="$CACHE_DIR/material_symbols_rounded_full.ttf"
UPSTREAM_URL='https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.ttf'

if [ ! -f "$ENUM_FILE" ]; then
    echo "Enum file not found at $ENUM_FILE" >&2
    echo "Ensure NubecitaIconName.kt exists on the current branch before running this script." >&2
    exit 1
fi

# Extract \uXXXX codepoints from NubecitaIconName.kt
codepoints=$(grep -oEi '\\u[A-F0-9]{4}' "$ENUM_FILE" | tr 'a-f' 'A-F' | sort -u)
if [ -z "$codepoints" ]; then
    echo "No \\uXXXX codepoints found in $ENUM_FILE" >&2
    exit 1
fi
codepoint_count=$(echo "$codepoints" | wc -l | tr -d '[:space:]')
unicode_args=$(echo "$codepoints" | sed 's/\\u/U+/' | tr '\n' ',' | sed 's/,$//')
echo "Found $codepoint_count codepoints in NubecitaIconName.kt"

# Fetch upstream font into the cache (skip if already cached)
mkdir -p "$CACHE_DIR"
if [ ! -f "$UPSTREAM_FONT" ]; then
    echo "Downloading upstream font (~14.9 MB) into $CACHE_DIR ..."
    tmp_font="$UPSTREAM_FONT.download"
    if ! curl -L --fail -o "$tmp_font" "$UPSTREAM_URL"; then
        rm -f "$tmp_font"
        echo "Download failed. Check your network and retry." >&2
        exit 1
    fi
    mv "$tmp_font" "$UPSTREAM_FONT"
fi

upstream_size=$(stat -f%z "$UPSTREAM_FONT" 2>/dev/null || stat -c%s "$UPSTREAM_FONT")
if [ "$upstream_size" -lt 1000000 ]; then
    echo "Upstream font is suspiciously small ($upstream_size bytes); refusing to subset." >&2
    echo "Delete $UPSTREAM_FONT and re-run." >&2
    exit 1
fi

# Run pyftsubset. Preserve all variable axes (FILL/GRAD/opsz/wght);
# drop hinting (icons render at fixed sizes); desubroutinize for
# reliable rendering across Compose's Skia backend.
echo "Subsetting to $codepoint_count codepoints ..."
pyftsubset "$UPSTREAM_FONT" \
    --output-file="$OUTPUT_FONT" \
    --unicodes="$unicode_args" \
    --layout-features='*' \
    --no-hinting \
    --desubroutinize \
    --recommended-glyphs

output_size=$(stat -f%z "$OUTPUT_FONT" 2>/dev/null || stat -c%s "$OUTPUT_FONT")
output_kb=$((output_size / 1024))
echo "Wrote $OUTPUT_FONT ($output_kb KB)"

if [ "$output_kb" -gt 200 ]; then
    echo "WARNING: subset font is unusually large (>200 KB). Inspect for unexpected glyphs." >&2
fi
