window.BENCHMARK_DATA = {
  "lastUpdate": 1780476776323,
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
          "id": "b28f43b7334a930da34b704282afd31694f63a95",
          "message": "ci(release): 1.146.0\n\n## [1.146.0](https://github.com/kikin81/nubecita/compare/v1.145.0...v1.146.0) (2026-05-31)\n\n### Features\n\n* **feeds:** add feeds api nav stub and app placeholder ([#368](https://github.com/kikin81/nubecita/issues/368)) ([aea2dd7](https://github.com/kikin81/nubecita/commit/aea2dd710fd3beaab2ed810ffa06e09e9f2fd488))\n* **preferences:** persist last-selected feed uri ([#367](https://github.com/kikin81/nubecita/issues/367)) ([383c79c](https://github.com/kikin81/nubecita/commit/383c79c0564e2bbcb89bda7e06c8dd62a1d26b13))",
          "timestamp": "2026-05-31T08:03:09Z",
          "url": "https://github.com/kikin81/nubecita/commit/b28f43b7334a930da34b704282afd31694f63a95"
        },
        "date": 1780215446011,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 35,
            "range": "+/- 12.2%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1351.793,
            "range": "+/- 5.8%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1405.684,
            "range": "+/- 6.2%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1018.042,
            "range": "+/- 38.7%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1088.878,
            "range": "+/- 20%",
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
          "id": "170bafdd73055c3971fdba92d238bf948608eb49",
          "message": "build: wire RevenueCat SDK key into release builds + local.properties (#386)\n\n* build: wire RevenueCat SDK key into release builds + local.properties\n\nThe production AAB and FAD internal build need BuildConfig.REVENUECAT_API_KEY\npopulated or RevenueCatInitializer skips configure and Pro is inert. Two\ngaps closed:\n\n- app/build.gradle.kts: the key now resolves from -PrevenueCatApiKey →\n  REVENUECAT_API_KEY env → local.properties (accepts revenueCatApiKey or\n  REVENUECAT_API_KEY). Previously only the gradle property was read, so a\n  local.properties entry was silently ignored.\n- release.yaml: pass REVENUECAT_API_KEY (release-environment secret) as an\n  env var to both the FAD `assembleProductionDebug` step and the fastlane\n  `internal` (`bundleProductionRelease`) step.\n\nIt's the RevenueCat *public* SDK key (ships in the APK; publishable), so a\nsecret or variable both work; it must live in the `release` environment\n(same as the keystore secrets).\n\nRefs: nubecita-q5ge.11\n\n* ci(release): fail fast when RevenueCat key is empty\n\nAddress Copilot review on #386: both the FAD build and the fastlane\nPlay-upload steps now abort with a clear ::error:: if REVENUECAT_API_KEY\nis empty, instead of silently producing a keyless (Pro-inert) artifact.\nMirrors the existing keystore fail-fast and guards against a secret\nmis-scoped to the wrong environment.\n\nRefs: nubecita-q5ge.11",
          "timestamp": "2026-06-01T07:35:10Z",
          "url": "https://github.com/kikin81/nubecita/commit/170bafdd73055c3971fdba92d238bf948608eb49"
        },
        "date": 1780304691675,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 34,
            "range": "+/- 7.4%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1403.258,
            "range": "+/- 6.8%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1227.076,
            "range": "+/- 6.9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1226.677,
            "range": "+/- 24.9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1114.902,
            "range": "+/- 26.2%",
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
          "id": "3c6f88ea839483299e857311d5fdfa4bfbb0dfc6",
          "message": "ci(release): 1.168.0\n\n## [1.168.0](https://github.com/kikin81/nubecita/compare/v1.167.0...v1.168.0) (2026-06-02)\n\n### Features\n\n* **profile:** add bench mock data for own-profile tabs ([#402](https://github.com/kikin81/nubecita/issues/402)) ([c337e2c](https://github.com/kikin81/nubecita/commit/c337e2cbcd704fe316f3391466e88bd378171f3b))",
          "timestamp": "2026-06-02T04:47:48Z",
          "url": "https://github.com/kikin81/nubecita/commit/3c6f88ea839483299e857311d5fdfa4bfbb0dfc6"
        },
        "date": 1780389969312,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 37,
            "range": "+/- 4.7%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1546.573,
            "range": "+/- 7.2%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1524.892,
            "range": "+/- 10.1%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1069.308,
            "range": "+/- 11.3%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1128.537,
            "range": "+/- 12.3%",
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
          "id": "2dc9e0cadd1a799893ce374469bbbdd8fffb0639",
          "message": "chore(screenshots): hide soft keyboard in marketing captures (#421)\n\n* chore(screenshots): hide soft keyboard in marketing captures\n\nThe previous Play Store captures showed the soft keyboard covering the\nlower third of the Search (and any IME-raising) screens. The marketing\njourney now dismisses the IME before each shot: hideKeyboardIfShown()\ndetects the input-method window via the accessibility window list and\nonly then dispatches a single back press, so the guard never pops the\nnav stack when no keyboard is up.\n\nRegenerate all phone + tenInch framed screenshots from the bench flavor\nwith the keyboard hidden.\n\n* chore(screenshots): recycle IME window infos in keyboard check\n\nAddress Copilot review: UiAutomation.windows returns caller-owned pooled\nAccessibilityWindowInfo instances on API 28–32 (our minSdk). Recycle them\nafter the IME check so the journey doesn't leak across its eight captures;\nrecycle() is a no-op from API 33 on, hence the @Suppress.",
          "timestamp": "2026-06-03T07:54:47Z",
          "url": "https://github.com/kikin81/nubecita/commit/2dc9e0cadd1a799893ce374469bbbdd8fffb0639"
        },
        "date": 1780476774188,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 34,
            "range": "+/- 7.5%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1219.275,
            "range": "+/- 6.2%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1199.295,
            "range": "+/- 6.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1015.449,
            "range": "+/- 14.8%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 936.529,
            "range": "+/- 24.5%",
            "unit": "ms"
          }
        ]
      }
    ]
  }
}