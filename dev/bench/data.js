window.BENCHMARK_DATA = {
  "lastUpdate": 1780022592290,
  "repoUrl": "https://github.com/kikin81/nubecita",
  "entries": {
    "Benchmark": [
      {
        "commit": {
          "author": {
            "name": "Francisco Velazquez",
            "username": "kikin81",
            "email": "kikin81@gmail.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "d329267346e4774a8499c56000fc111ffdd26f1c",
          "message": "chore(bench): replace python converter with inline jq (#338)\n\nThe androidx->customSmallerIsBetter conversion (added as a Python script\nin #337) doesn't belong in a Kotlin/Gradle repo. Research confirmed no\naction/fork natively ingests androidx benchmarkData.json, so the\nconversion is unavoidable — but jq (preinstalled on ubuntu-latest, 1.7)\ndoes it inline with zero committed script files.\n\n- Replace the Convert step's `python3 …` call with an inline jq filter\n  producing the same customSmallerIsBetter array (verified byte-for-byte\n  against the real seed-run JSON, modulo a cosmetic 36 vs 36.0).\n- Keep the \"fail loud on zero metrics\" guard via `jq length`.\n- Delete benchmark/ci/androidx_to_custom_benchmark_json.py.\n- Update benchmark/README.md to describe the inline jq step.\n\nA Gradle/Groovy task was considered and rejected as over-engineering\n(+5-20s/run, falls back to Groovy not Kotlin) for a ~15-line transform.\n\nRefs: nubecita-crmi.6.3\n\nCo-authored-by: Francisco Velazquez <francisco@Franciscos-MacBook-Pro.local>",
          "timestamp": "2026-05-29T01:58:20Z",
          "url": "https://github.com/kikin81/nubecita/commit/d329267346e4774a8499c56000fc111ffdd26f1c"
        },
        "date": 1780022591201,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 38,
            "range": "+/- 9.9%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1346.634,
            "range": "+/- 9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1239.714,
            "range": "+/- 7.9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1036.491,
            "range": "+/- 40.8%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1019.598,
            "range": "+/- 12.1%",
            "unit": "ms"
          }
        ]
      }
    ]
  }
}