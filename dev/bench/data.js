window.BENCHMARK_DATA = {
  "lastUpdate": 1780128450346,
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
      },
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
          "id": "71fc142279ae04729267ce47c12fccac0eb6ebe6",
          "message": "refactor(core): extract :core:actors with DID-keyed cache; migrate search + composer (#348)\n\n* docs(actors): spec for :core:actors extraction + DID-keyed cache\n\nDesign for PR1 of the recipient-picker prep: consolidate search +\ncomposer actor-search into a new :core:actors capability, add a\nDID-keyed Room cache (v2→v3 additive AutoMigration) with always-\nnetwork-refreshed write-through. No user-facing change.\n\nRefs: nubecita-26a6\n\n* docs(actors): implementation plan for :core:actors extraction + cache\n\nRefs: nubecita-26a6\n\n* feat(core/actors): scaffold module + ActorRepository surface\n\nRefs: nubecita-26a6\n\n* feat(core/database): add DID-keyed actors cache table (v2→v3)\n\nActorEntity + ActorDao + asExternalModel/toCacheEntity mappers, additive\n@AutoMigration(2→3), committed 3.json, DAO provider + ActorDaoTest.\n\nRefs: nubecita-26a6\n\n* refactor(core/database): address review on actors DAO test + KDoc\n\nExtend DatabaseTest base, internal + dao-package placement, batch +\nnull round-trip cases, ActorDao KDoc, schema trailing newline.\nAlso apply spotless fix for ActorEntity extension function formatting.\n\nRefs: nubecita-26a6\n\n* feat(core/actors): implement DefaultActorRepository + write-through\n\nImplements DefaultActorRepository:\n- searchTypeahead via ActorService.searchActorsTypeahead, maps\n  ProfileViewBasic → ActorUi (blank displayName → null).\n- searchActors via ActorService.searchActors, maps ProfileView →\n  ActorUi, returns ActorSearchPage with nextCursor.\n- writeThrough: best-effort upsert into ActorDao; skips on empty\n  results; swallows non-cancellation exceptions so a cache failure\n  never fails the caller's Result.\n- getActor: Flow<ActorUi?> backed by ActorDao.getActor.\n\nAdds ActorsModule (@Binds DefaultActorRepository → ActorRepository).\n\nUnit tests (MockEngine harness + MockK relaxed ActorDao): 10 tests,\nall passing — success/empty/failure/blank-displayName/cache-write-fail\npaths for both searchTypeahead and searchActors.\n\nAlso applies spotless reformatting to ActorRepository.kt (long params\nsplit to match ktlint max-line rule).\n\nClock import: kotlinx.datetime.Clock (deprecated warning only; matches\nActorEntity.lastSeenAt: kotlinx.datetime.Instant in :core:database).\n\nRefs: nubecita-26a6\n\n* refactor(core/actors): address review on DefaultActorRepository\n\nClass KDoc, limit-guard + cancellation + wire-format tests, private TAG,\nsibling-shaped error log.\n\nRefs: nubecita-26a6\n\n* refactor(core/actors): make ActorsModule a public abstract class\n\nSo downstream feature-module instrumented tests can @TestInstallIn\n(replaces = [ActorsModule::class]). Mirrors PostingModule/AuthBindingsModule.\n\nRefs: nubecita-26a6\n\n* refactor(search): consume :core:actors ActorRepository for People tab\n\nReplaces the feature-local SearchActorsRepository with the shared\nActorRepository; SearchActorsPage -> ActorSearchPage; androidTest swaps\nActorsModule. No behavior change.\n\nRefs: nubecita-26a6\n\n* refactor(composer,search): move @-mention typeahead to :core:actors\n\nMigrate ComposerViewModel and SearchTypeaheadViewModel off the\n:core:posting ActorTypeaheadRepository onto the shared ActorRepository;\ndelete ActorTypeaheadRepository + impl + test; drop PostingModule binding.\n\nRefs: nubecita-26a6\n\n* refactor(search): drop dead :core:posting dep + typeahead tests\n\nSearch no longer uses :core:posting after the typeahead migration; restore\nthe term=-absent guard and missing-fields normalization cases from the\ndeleted posting repo test.\n\nRefs: nubecita-26a6\n\n* build(core/actors): add empty consumer-rules.pro\n\nThe nubecita.android.library plugin declares consumerProguardFiles;\nevery :core module ships this file. Was missing on the new module.\n\nRefs: nubecita-26a6\n\n* docs(search): fix stale doclinks to deleted SearchActors* types\n\nRe-point/drop KDoc references left dangling after the :core:actors\nextraction.\n\nRefs: nubecita-26a6\n\n* docs(search): re-point remaining stale doclinks to live repos\n\nThe :core:actors extraction deleted DefaultSearchActorsRepository(Test);\nremaining prose mentions now point at DefaultSearchPostsRepository (same\nshape) and the ActorUi mapper note at :core:actors DefaultActorRepository.\nAddresses Copilot review on #348.\n\nRefs: nubecita-26a6",
          "timestamp": "2026-05-30T05:11:45Z",
          "url": "https://github.com/kikin81/nubecita/commit/71fc142279ae04729267ce47c12fccac0eb6ebe6"
        },
        "date": 1780128449272,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 38,
            "range": "+/- 8.8%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1413.859,
            "range": "+/- 11.3%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1286.601,
            "range": "+/- 3.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1020.37,
            "range": "+/- 23.4%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1160.194,
            "range": "+/- 20.6%",
            "unit": "ms"
          }
        ]
      }
    ]
  }
}