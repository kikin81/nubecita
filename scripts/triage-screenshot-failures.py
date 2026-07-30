#!/usr/bin/env python3
"""Classify local screenshot-test failures as host-render noise or real regressions.

Why this exists
---------------
Screenshot baselines are generated and validated on CI's Linux runners. Layoutlib
renders slightly differently on a developer's machine, so `validate*ScreenshotTest`
reports failures locally that pass in CI. Those failures are single-LSB rounding
artifacts on anti-aliased edges, gradients, and alpha composites — never a moved,
resized, or recoloured element.

The screenshot plugin's only tuning knob is a threshold on the FRACTION of
differing pixels, and that metric cannot separate the two cases here. Measured in
this repo:

    host noise      :feature:chats:impl JoinLinkCardDisabled  3.2%  maxDelta 1
    real regression :feature:feed:impl  1dp layout shift      0.48% maxDelta 255

Noise can cover a larger fraction than a genuine regression, because a disabled
card's alpha composite shifts every pixel it covers by 1/255. The metric that
DOES separate them is the maximum per-channel delta: host noise is <= 3, while
any real change moves pixels by tens or hundreds of levels.

So rather than loosen the gate — which would mask real regressions while still
not silencing the noise — this script reports the right metric.

Usage
-----
    ./gradlew :feature:feed:impl:validateProductionDebugScreenshotTest || true
    python3 scripts/triage-screenshot-failures.py [module ...]

Exit code is 0 when every difference is host noise, 1 when anything looks real.
Requires Pillow (`pip install pillow`).
"""

import glob
import os
import sys

NOISE_MAX_DELTA = 3

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: pip install pillow")


def compare(rendered, reference):
    """Return (differing_pixel_count, max_channel_delta, size) or None if unreadable."""
    a = Image.open(rendered).convert("RGB")
    b = Image.open(reference).convert("RGB")
    if a.size != b.size:
        return None, None, (a.size, b.size)
    pa, pb = a.load(), b.load()
    w, h = a.size
    count = 0
    worst = 0
    for y in range(h):
        for x in range(w):
            p, q = pa[x, y], pb[x, y]
            d = max(abs(p[0] - q[0]), abs(p[1] - q[1]), abs(p[2] - q[2]))
            if d:
                count += 1
                if d > worst:
                    worst = d
    return count, worst, (w, h)


def modules(argv):
    if argv:
        return argv
    return sorted(
        os.path.dirname(p).split("/build/")[0]
        for p in glob.glob("**/build/outputs/screenshotTest-results/**/rendered", recursive=True)
    )


def main():
    suspicious = []
    noise = []
    for module in dict.fromkeys(modules(sys.argv[1:])):
        rendered_dirs = glob.glob(f"{module}/build/outputs/screenshotTest-results/preview/debug/**/rendered", recursive=True)
        reference_dirs = glob.glob(f"{module}/src/screenshotTest*/reference")
        if not rendered_dirs or not reference_dirs:
            continue
        base, ref_root = rendered_dirs[0], reference_dirs[0]
        for rendered in glob.glob(base + "/**/*.png", recursive=True):
            rel = rendered[len(base) + 1:]
            reference = os.path.join(ref_root, rel)
            name = rel.split("/")[-1]
            if not os.path.exists(reference):
                suspicious.append((module, name, "no committed baseline"))
                continue
            count, worst, size = compare(rendered, reference)
            if count is None:
                suspicious.append((module, name, f"size changed {size[0]} -> {size[1]}"))
            elif count == 0:
                continue
            elif worst <= NOISE_MAX_DELTA:
                noise.append((module, name, f"{count} px ({100 * count / (size[0] * size[1]):.4f}%), maxDelta={worst}"))
            else:
                suspicious.append((module, name, f"{count} px ({100 * count / (size[0] * size[1]):.4f}%), maxDelta={worst}"))

    if noise:
        print(f"Host-render noise — safe to ignore locally, passes in CI ({len(noise)}):")
        for module, name, detail in noise:
            print(f"  {module}  {name[:60]}  {detail}")
    if suspicious:
        print(f"\nLIKELY REAL — inspect these ({len(suspicious)}):")
        for module, name, detail in suspicious:
            print(f"  {module}  {name[:60]}  {detail}")
        return 1
    if not noise:
        print("No differences found.")
    else:
        print("\nAll differences are host-render noise (maxDelta <= 3).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
