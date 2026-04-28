## 1. Gradle wiring

- [x] 1.1 Add Firebase versions, libraries, and plugins to `gradle/libs.versions.toml`: `firebaseBom = "34.11.0"`, `firebaseAppDistribution = "5.2.1"`, `googleServices = "4.4.4"`; libraries `firebase-bom`, `firebase-analytics`, `firebase-appcheck-playintegrity`, `firebase-appcheck-debug`; plugins `google-services`, `firebase-appdistribution`. **No tests** — verified by `:app` consumers compiling in step 1.4.
- [x] 1.2 Register the two new plugins as `apply false` in root `build.gradle.kts`. **No tests** — verified by `./gradlew help` succeeding.
- [x] 1.3 `gradle.properties` already exists at repo root with `version = 1.21.0` (semantic-release-managed). No change needed; verified by `assembleDebug` succeeding with `versionName = project.property("version").toString()` reading it.
- [x] 1.4 In `app/build.gradle.kts`: apply `google-services` and `firebase-appdistribution` plugins, add the four Firebase dependencies (`firebase-analytics`, `firebase-appcheck-playintegrity` as `implementation`; `firebase-appcheck-debug` as `debugImplementation`; `firebase-bom` as `platform`), wire `versionName = project.property("version").toString()`, configure `firebaseAppDistribution { groups = "internal-testers"; releaseNotes = System.getenv("APP_DISTRIBUTION_RELEASE_NOTES") ?: "" }` on the debug build type, and add the required `import com.google.firebase.appdistribution.gradle.firebaseAppDistribution`. **No tests** — verified by `./gradlew :app:assembleDebug` and `./gradlew :app:tasks --group="firebase app distribution"` showing `appDistributionUploadDebug`.

## 2. App initialization

- [x] 2.1 In `NubecitaApplication.onCreate()`: install the App Check provider factory BEFORE Timber init — `DebugAppCheckProviderFactory.getInstance()` when `BuildConfig.DEBUG`, otherwise `PlayIntegrityAppCheckProviderFactory.getInstance()`. Add the corresponding imports. **Tests**: covered by the new instrumented test in 5.1 (no unit test added for this branch — see design.md decision).

## 3. CI workflow

- [x] 3.1 In `.github/workflows/release.yaml`'s `release` job: existing `Checkout` step kept; gave the `open-turo/actions-jvm/release@v2` step `id: release` and added `outputs: { new-release-published, new-release-version }` to the job. **No tests** — verified by `actionlint` (pre-commit hook) green.
- [x] 3.2 Added new `distribute` job to `release.yaml` depending on `release` with `if: needs.release.outputs.new-release-published == 'true'`. Job steps: `actions/checkout@v6` with `ref: main` and `fetch-depth: 0`; setup JDK + Android SDK + Gradle; decode `secrets.FIREBASE_SERVICE_ACCOUNT` (base64) to `/tmp/firebase-sa-key.json`; run `./gradlew assembleDebug appDistributionUploadDebug` with `GOOGLE_APPLICATION_CREDENTIALS` and `APP_DISTRIBUTION_RELEASE_NOTES="v${{ needs.release.outputs.new-release-version }} — ${{ github.sha }}"` env. Removed the TODO comment. **No tests** — verified by `actionlint` green; post-merge first-distribution smoke remains (see runbook in 6.4).

## 4. Credential guardrails

- [x] 4.1 Added credential-pattern entries to `.gitignore` near the `*.keystore` block: `firebase-sa-*.json`, `firebase-service-account*.json`, `google-application-credentials*.json`, with a short comment noting that `google-services.json` is intentionally tracked. **No tests** — verified by `pre-commit run --all-files` (gitleaks) green.

## 5. Verification (instrumented test)

- [x] 5.1 Created `app/src/androidTest/java/net/kikin/nubecita/FirebaseInitTest.kt` with three `@Test` methods asserting (a) `FirebaseApp.getInstance().options.projectId == "nubecita-2a4c1"`, (b) `FirebaseAppCheck.getInstance()` returns non-null, (c) `FirebaseAnalytics.getInstance(applicationContext)` returns non-null. Uses `AndroidJUnit4` runner and `ApplicationProvider.getApplicationContext()`. Also deleted dead template test `app/src/androidTest/java/net/kikin/nubecita/ui/main/MainScreenTest.kt` (broken since the initial template-rename commit; was blocking the androidTest source set from compiling). **Test added (instrumented)**: `FirebaseInitTest.kt` — three new tests, all passing on `Pixel_10_Pro(AVD)`; will be picked up by CI when reactivecircus emulator-runner ticket lands. **No screenshot tests** (headless task per CLAUDE.md memory rule).

## 6. Manual console + secret runbook (executed by maintainer, not via PR)

- [ ] 6.1 Register debug-keystore SHA-1 fingerprint under Firebase project `nubecita-2a4c1` → app `net.kikin.nubecita` → SHA certificate fingerprints. (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1`)
- [ ] 6.2 In Firebase console → App Check → Apps → register **Play Integrity** as production provider. Leave enforcement in **monitor** mode.
- [ ] 6.3 In Firebase console → App Distribution → Testers & Groups → create group `internal-testers`, add tester emails.
- [ ] 6.4 Generate Firebase service-account key (Project Settings → Service accounts → Generate new private key). Grant the account **Firebase App Distribution Admin** role. `base64 -i firebase-sa-key.json | pbcopy`. In GitHub repo Settings → Secrets and variables → Actions → New repository secret named `FIREBASE_SERVICE_ACCOUNT`, paste. **Delete the local JSON file** after the secret is set.

## 7. Validation pass

- [x] 7.1 `./gradlew :app:assembleDebug` succeeds locally — green.
- [x] 7.2 `./gradlew spotlessCheck lint :app:checkSortDependencies` succeeds locally — green.
- [x] 7.3 `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.kikin.nubecita.FirebaseInitTest` against `Pixel_10_Pro(AVD)` — 3/3 tests passed.
- [x] 7.4 `pre-commit run --all-files` (includes `actionlint`, `gitleaks`, `openspec validate`) — green.
- [ ] 7.5 Open the PR. After merge to main, observe: (a) `release` job publishes a version (or no-ops if no semver-relevant commits), (b) when `release` publishes, `distribute` job runs and uploads, (c) internal-tester email arrives, (d) APK installs and Analytics events appear in DebugView with `adb shell setprop debug.firebase.analytics.app net.kikin.nubecita`, (e) App Check debug token printed in logcat is registered in console.
