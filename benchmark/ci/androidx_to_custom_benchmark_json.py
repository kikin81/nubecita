#!/usr/bin/env python3
"""Convert an AndroidX Macrobenchmark ``benchmarkData.json`` into the
``customSmallerIsBetter`` array format consumed by
``benchmark-action/github-action-benchmark``.

The action has no ``androidx`` tool parser (despite the crmi.6 spec
assuming one), so the Macrobench CI workflow runs this first and feeds
the action the converted file with ``tool: customSmallerIsBetter``.

One data point is emitted per (benchmark, metric), using the metric's
``median`` (the per-cell metric of record — see benchmark/README.md).
The coefficient of variation rides along as the displayed ``range`` so
the trend graph shows per-point noise. The converter is intentionally
metric-agnostic: whatever metrics the run produced flow through, so when
a real-hardware runner captures frame durations (which a swiftshader
emulator does not) they appear automatically with no code change.

Usage:
    androidx_to_custom_benchmark_json.py <benchmarkData.json> [out.json]

Writes to ``out.json`` if given, else stdout.
"""

from __future__ import annotations

import json
import sys


def convert(src: dict) -> list[dict]:
    points: list[dict] = []
    for bench in src.get("benchmarks", []):
        simple_class = bench.get("className", "").rsplit(".", 1)[-1]
        name = bench.get("name", "")
        for metric, summary in bench.get("metrics", {}).items():
            median = summary.get("median")
            if median is None:
                continue
            if metric.endswith("Ms"):
                unit = "ms"
            elif metric == "frameCount":
                unit = "frames"
            else:
                unit = ""
            point = {
                "name": f"{simple_class}.{name} / {metric}",
                "unit": unit,
                "value": round(median, 3),
            }
            cov = summary.get("coefficientOfVariation")
            if cov is not None:
                point["range"] = f"+/- {round(cov * 100, 1)}%"
            points.append(point)
    return points


def main(argv: list[str]) -> int:
    if not 2 <= len(argv) <= 3:
        print(__doc__, file=sys.stderr)
        return 2
    with open(argv[1], encoding="utf-8") as fh:
        src = json.load(fh)
    points = convert(src)
    if not points:
        print(
            f"error: no benchmark metrics found in {argv[1]}",
            file=sys.stderr,
        )
        return 1
    payload = json.dumps(points, indent=2)
    if len(argv) == 3:
        with open(argv[2], "w", encoding="utf-8") as fh:
            fh.write(payload + "\n")
    else:
        print(payload)
    print(f"converted {len(points)} metric(s)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
