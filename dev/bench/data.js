window.BENCHMARK_DATA = {
  "lastUpdate": 1781338939988,
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
          "id": "e0757c4390dcbe1ce46bd7bd648094f1781d8a15",
          "message": "ci(release): 1.178.0\n\n## [1.178.0](https://github.com/kikin81/nubecita/compare/v1.177.0...v1.178.0) (2026-06-04)\n\n### Features\n\n* **moderation:** precompute media content-warning covers in feed-mapping + models ([#432](https://github.com/kikin81/nubecita/issues/432)) ([fe38bdc](https://github.com/kikin81/nubecita/commit/fe38bdc8484b3a903448307abba98d4ffde80fe9))",
          "timestamp": "2026-06-04T05:43:08Z",
          "url": "https://github.com/kikin81/nubecita/commit/e0757c4390dcbe1ce46bd7bd648094f1781d8a15"
        },
        "date": 1780562551347,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 35,
            "range": "+/- 48.5%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1175.098,
            "range": "+/- 6.6%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1100.994,
            "range": "+/- 8.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 874.327,
            "range": "+/- 18.4%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1061.175,
            "range": "+/- 13.2%",
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
          "id": "5dc76e2c6970f5bdaa7afe0616b0acff71efe329",
          "message": "ci(release): 1.189.0\n\n## [1.189.0](https://github.com/kikin81/nubecita/compare/v1.188.0...v1.189.0) (2026-06-05)\n\n### Features\n\n* **posting:** write threadgate/postgate gates on createPost ([#452](https://github.com/kikin81/nubecita/issues/452)) ([e8ca395](https://github.com/kikin81/nubecita/commit/e8ca395b4c4fef33df30895b055f3ae5cd6da867))",
          "timestamp": "2026-06-05T06:56:05Z",
          "url": "https://github.com/kikin81/nubecita/commit/5dc76e2c6970f5bdaa7afe0616b0acff71efe329"
        },
        "date": 1780648631819,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 34,
            "range": "+/- 5.7%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1396.822,
            "range": "+/- 3.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1391.046,
            "range": "+/- 7.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1426.324,
            "range": "+/- 12.8%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1278.124,
            "range": "+/- 9.7%",
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
          "id": "9cbedcc2254bbfa939fbdb90a2eff7b0707d9814",
          "message": "ci(release): 1.193.4\n\n## [1.193.4](https://github.com/kikin81/nubecita/compare/v1.193.3...v1.193.4) (2026-06-06)\n\n### Performance Improvements\n\n* **baseline:** post-startup journeys + production-generated profiles ([#466](https://github.com/kikin81/nubecita/issues/466)) ([3ff618b](https://github.com/kikin81/nubecita/commit/3ff618b73e9e7657fda1a08a3c61e19f454c87c8))",
          "timestamp": "2026-06-06T07:48:01Z",
          "url": "https://github.com/kikin81/nubecita/commit/9cbedcc2254bbfa939fbdb90a2eff7b0707d9814"
        },
        "date": 1780733405759,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 38,
            "range": "+/- 7.3%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1263.959,
            "range": "+/- 9.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1232.099,
            "range": "+/- 5.6%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1094.981,
            "range": "+/- 22.5%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 973.335,
            "range": "+/- 24.2%",
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
          "id": "e680c6815f384db26f8ae33fbccb7ba3272df6a8",
          "message": "ci(release): 1.195.2\n\n## [1.195.2](https://github.com/kikin81/nubecita/compare/v1.195.1...v1.195.2) (2026-06-07)\n\n### Bug Fixes\n\n* **feed:** scroll the selected chip to the start instead of resetting ([#479](https://github.com/kikin81/nubecita/issues/479)) ([493a2ea](https://github.com/kikin81/nubecita/commit/493a2ea90eb928fc91979e1815ae4eaad6d3f523))",
          "timestamp": "2026-06-07T06:23:47Z",
          "url": "https://github.com/kikin81/nubecita/commit/e680c6815f384db26f8ae33fbccb7ba3272df6a8"
        },
        "date": 1780820541992,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 39,
            "range": "+/- 67.6%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 884.068,
            "range": "+/- 4.7%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 808.125,
            "range": "+/- 8.2%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 719.772,
            "range": "+/- 30.4%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 773.503,
            "range": "+/- 34.6%",
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
          "id": "229b7fc391c2c89509cbfa72d033adf27fbd7881",
          "message": "ci(release): 1.203.0\n\n## [1.203.0](https://github.com/kikin81/nubecita/compare/v1.202.0...v1.203.0) (2026-06-08)\n\n### Features\n\n* **chats,settings:** add message-checking toggle + gate v1 foreground poller ([#493](https://github.com/kikin81/nubecita/issues/493)) ([50f5568](https://github.com/kikin81/nubecita/commit/50f55689d272c543cc72e016661ee2b454565f7b))",
          "timestamp": "2026-06-08T04:47:22Z",
          "url": "https://github.com/kikin81/nubecita/commit/229b7fc391c2c89509cbfa72d033adf27fbd7881"
        },
        "date": 1780908555813,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 35,
            "range": "+/- 5.5%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1364.7,
            "range": "+/- 5.1%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1360.724,
            "range": "+/- 1.9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1088.324,
            "range": "+/- 8.9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 576.201,
            "range": "+/- 37%",
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
          "id": "4405bc98b89836d44c8ed7d9ad9a29256530687b",
          "message": "chore(openspec): archive enrich-push-notification-bodies + sync spec (#500)\n\nMove the completed change to changes/archive/ and fold its single ADDED\nrequirement (reply/mention/quote notifications carry the notifying post's\ntext as the body, + 7 scenarios) into the canonical push-notifications\ncapability spec. Shipped via atproto-push-gateway #3/#4 (deployed) and\nnubecita #499.\n\nRefs: nubecita-1fy.19",
          "timestamp": "2026-06-09T02:24:45Z",
          "url": "https://github.com/kikin81/nubecita/commit/4405bc98b89836d44c8ed7d9ad9a29256530687b"
        },
        "date": 1780993734464,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 31,
            "range": "+/- 5.5%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1613.96,
            "range": "+/- 14.2%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1408.693,
            "range": "+/- 5.7%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1568.4,
            "range": "+/- 8.8%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1349.387,
            "range": "+/- 11.5%",
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
          "id": "00ec33a54f21a2c48a2babc84af9dc3965b796d8",
          "message": "ci(release): 1.211.0\n\n## [1.211.0](https://github.com/kikin81/nubecita/compare/v1.210.0...v1.211.0) (2026-06-10)\n\n### Miscellaneous\n\n* **openspec:** archive add-widget-feed-refresh + sync widget-feed-refresh spec ([#515](https://github.com/kikin81/nubecita/issues/515)) ([a5febee](https://github.com/kikin81/nubecita/commit/a5febee3c5e07c58b03966dba22229b4a0ee0f4a)), closes [512/#513](https://github.com/512/nubecita/issues/513)\n\n### Features\n\n* **widgets:** scaffold :feature:widgets:impl with Glance + Hilt EntryPoint ([#516](https://github.com/kikin81/nubecita/issues/516)) ([650bf54](https://github.com/kikin81/nubecita/commit/650bf5423f61e9968f29c8780d1ba4bdcf4eeb78))",
          "timestamp": "2026-06-10T06:58:52Z",
          "url": "https://github.com/kikin81/nubecita/commit/00ec33a54f21a2c48a2babc84af9dc3965b796d8"
        },
        "date": 1781080663181,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 34,
            "range": "+/- 7.2%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1474.119,
            "range": "+/- 7%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1414.014,
            "range": "+/- 4.4%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1421.268,
            "range": "+/- 23.7%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1168.066,
            "range": "+/- 21.9%",
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
          "id": "9ebd8a0e58330e8b5fb498dea6f91050bb363129",
          "message": "ci(release): 1.218.1\n\n## [1.218.1](https://github.com/kikin81/nubecita/compare/v1.218.0...v1.218.1) (2026-06-11)\n\n### Bug Fixes\n\n* **widgets:** hydrate the session before reading it (widget + worker) ([#527](https://github.com/kikin81/nubecita/issues/527)) ([6111cfc](https://github.com/kikin81/nubecita/commit/6111cfce9b106dff6421772d872ec3a162fb1ea3))",
          "timestamp": "2026-06-11T07:35:40Z",
          "url": "https://github.com/kikin81/nubecita/commit/9ebd8a0e58330e8b5fb498dea6f91050bb363129"
        },
        "date": 1781167690381,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 33,
            "range": "+/- 9.2%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1397.666,
            "range": "+/- 7.6%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1240.243,
            "range": "+/- 4.7%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1109.286,
            "range": "+/- 33.9%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1088.085,
            "range": "+/- 10.5%",
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
          "id": "bb2cd11ad44200990eb558bf0f18bf44f8bf3b7c",
          "message": "ci(release): 1.220.0\n\n## [1.220.0](https://github.com/kikin81/nubecita/compare/v1.219.1...v1.220.0) (2026-06-11)\n\n### Features\n\n* **widgets:** real picker preview via previewLayout (title + sample posts) ([#530](https://github.com/kikin81/nubecita/issues/530)) ([6e60385](https://github.com/kikin81/nubecita/commit/6e60385c82090d04d538691bf73fc18074958565))",
          "timestamp": "2026-06-11T10:27:45Z",
          "url": "https://github.com/kikin81/nubecita/commit/bb2cd11ad44200990eb558bf0f18bf44f8bf3b7c"
        },
        "date": 1781253956951,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 31,
            "range": "+/- 9.8%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1361.03,
            "range": "+/- 6.4%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1276.279,
            "range": "+/- 3.8%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1096.889,
            "range": "+/- 27.4%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1113.898,
            "range": "+/- 54.4%",
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
          "id": "02c96b38bd6fb5348df181438ff07aafbdbf5634",
          "message": "chore(designsystem): add Leave/Mute/Report/Block glyphs (#534)\n\n* chore(designsystem): add Leave/Mute/Report/Block glyphs\n\nAdd four Material Symbols Rounded entries to NubecitaIconName for the\nchat list-management contextual actions: Logout (leave conversation),\nNotificationsOff (mute conversation notifications — bell-slash, the\ncorrect semantic vs the existing speaker VolumeOff), Flag (report), and\nBlock. Codepoints verified against the cached upstream cmap; the subset\nfont was regenerated from the same cached upstream, so all 45 existing\nglyphs are byte-identical (drifted outlines: 0) and only the 4 new\nglyphs are added — the NubecitaIconShowcase baseline is the only one\nthat moves (regenerated on CI via the update-baselines label).\n\nRefs: nubecita-kc17.1\n\n* chore: regenerate screenshot baselines\n\n---------\n\nCo-authored-by: github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>",
          "timestamp": "2026-06-13T05:55:44Z",
          "url": "https://github.com/kikin81/nubecita/commit/02c96b38bd6fb5348df181438ff07aafbdbf5634"
        },
        "date": 1781338938325,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "FeedScrollBenchmark.scrollFeed / frameCount",
            "value": 29,
            "range": "+/- 6.5%",
            "unit": "frames"
          },
          {
            "name": "StartupBenchmark.startup[COLD-None] / timeToInitialDisplayMs",
            "value": 1481.161,
            "range": "+/- 6.3%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[COLD-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1314.066,
            "range": "+/- 5.6%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-None] / timeToInitialDisplayMs",
            "value": 1108.756,
            "range": "+/- 30.2%",
            "unit": "ms"
          },
          {
            "name": "StartupBenchmark.startup[WARM-BaselineProfile] / timeToInitialDisplayMs",
            "value": 1132.49,
            "range": "+/- 21.2%",
            "unit": "ms"
          }
        ]
      }
    ]
  }
}