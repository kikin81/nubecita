window.BENCHMARK_DATA = {
  "lastUpdate": 1780043493228,
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
      },
      {
        "commit": {
          "author": {
            "name": "semantic-release-bot",
            "username": "semantic-release-bot",
            "email": "semantic-release-bot@martynus.net"
          },
          "committer": {
            "name": "semantic-release-bot",
            "username": "semantic-release-bot",
            "email": "semantic-release-bot@martynus.net"
          },
          "id": "9af58a58c5a35a5005a71740514b3bafa3b13b9a",
          "message": "ci(release): 1.134.0\n\n## [1.134.0](https://github.com/kikin81/nubecita/compare/v1.133.1...v1.134.0) (2026-05-29)\n\n### Miscellaneous\n\n* **bench:** replace python converter with inline jq ([#338](https://github.com/kikin81/nubecita/issues/338)) ([d329267](https://github.com/kikin81/nubecita/commit/d329267346e4774a8499c56000fc111ffdd26f1c)), closes [#337](https://github.com/kikin81/nubecita/issues/337)\n\n### Documentation\n\n* **chats:** add OpenSpec change for Chats V2 composing (text only) ([#339](https://github.com/kikin81/nubecita/issues/339)) ([1d6c5f3](https://github.com/kikin81/nubecita/commit/1d6c5f3caedd0f536d9a41b6ed379e34461f4cf0))\n\n### Features\n\n* **chats:** add sendMessage repository method + send error mapping ([#340](https://github.com/kikin81/nubecita/issues/340)) ([b12fb46](https://github.com/kikin81/nubecita/commit/b12fb465199ae0269b7b06df126b630a8e24e11a))",
          "timestamp": "2026-05-29T06:46:02Z",
          "url": "https://github.com/kikin81/nubecita/commit/9af58a58c5a35a5005a71740514b3bafa3b13b9a"
        },
        "date": 1780043491661,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 37,
            "range": "+/- 11.9%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1202.382,
            "range": "+/- 8.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1271.625,
            "range": "+/- 10.1%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 964.659,
            "range": "+/- 27.9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1154.705,
            "range": "+/- 16.9%",
            "unit": "ms"
          }
        ]
      }
    ]
  }
}