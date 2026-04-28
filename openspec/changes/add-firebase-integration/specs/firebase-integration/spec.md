## ADDED Requirements

### Requirement: Firebase SDK bootstrap from committed configuration

The application SHALL initialize the Firebase SDK from a committed `app/google-services.json` matching the Firebase project `nubecita-2a4c1` and Android package `net.kikin.nubecita`. The Firebase Gradle plugin (`com.google.gms.google-services`) SHALL be applied to `:app`.

#### Scenario: Firebase app available after process start
- **WHEN** the application process is started and `Application.onCreate()` has returned
- **THEN** `FirebaseApp.getInstance()` returns a non-null instance
- **AND** `FirebaseApp.getInstance().options.projectId` equals `"nubecita-2a4c1"`

#### Scenario: Build fails when google-services.json is missing
- **WHEN** `app/google-services.json` is absent
- **THEN** `:app:processDebugGoogleServices` fails the Gradle build with a missing-file error

### Requirement: App Check provider factory installation

The application SHALL install a Firebase App Check provider factory in `Application.onCreate()` BEFORE any other Firebase service call. The factory SHALL be `DebugAppCheckProviderFactory` when `BuildConfig.DEBUG` is true, and `PlayIntegrityAppCheckProviderFactory` otherwise.

#### Scenario: Debug build installs debug factory
- **WHEN** the application is built with `BuildConfig.DEBUG == true` and `Application.onCreate()` runs
- **THEN** `FirebaseAppCheck.getInstance()` is called with the debug provider factory and returns without throwing

#### Scenario: Release build installs Play Integrity factory
- **WHEN** the application is built with `BuildConfig.DEBUG == false` and `Application.onCreate()` runs
- **THEN** `FirebaseAppCheck.getInstance()` is called with the Play Integrity provider factory and returns without throwing

#### Scenario: App Check installs before other Firebase work
- **WHEN** `Application.onCreate()` executes
- **THEN** the App Check provider factory is installed before Timber initialization and before any caller can issue a Firebase API request from application code

### Requirement: Firebase Analytics SDK availability

The application SHALL make `FirebaseAnalytics.getInstance(context)` available after `Application.onCreate()` so subsequent telemetry calls succeed without explicit per-call initialization.

#### Scenario: Analytics instance reachable post-onCreate
- **WHEN** `Application.onCreate()` has returned
- **AND** any caller invokes `FirebaseAnalytics.getInstance(applicationContext)`
- **THEN** a non-null `FirebaseAnalytics` instance is returned

### Requirement: Debug build type uploads to Firebase App Distribution

The `:app` `debug` build type SHALL be configured with Firebase App Distribution targeting tester group `internal-testers` and SHALL pass release notes from the `APP_DISTRIBUTION_RELEASE_NOTES` environment variable.

#### Scenario: appDistributionUploadDebug Gradle task is registered
- **WHEN** Gradle configuration completes for `:app`
- **THEN** task `:app:appDistributionUploadDebug` exists and is invokable

#### Scenario: Release notes flow from env to upload
- **WHEN** `APP_DISTRIBUTION_RELEASE_NOTES` is set in the environment at Gradle invocation time
- **AND** `:app:appDistributionUploadDebug` runs
- **THEN** the uploaded build's release notes equal the env value

#### Scenario: internal-testers group receives upload
- **WHEN** the App Distribution upload completes
- **THEN** members of the `internal-testers` group are notified via Firebase

### Requirement: Application versionName tracks semantic-release version

The `:app` module's `versionName` SHALL be derived from the Gradle `version` property (managed by semantic-release in `gradle.properties`). The hardcoded `versionName = "1.0"` is replaced.

#### Scenario: versionName mirrors gradle.properties
- **WHEN** `gradle.properties` contains `version=X.Y.Z`
- **AND** `assembleDebug` runs
- **THEN** the produced APK's `versionName` equals `X.Y.Z`

#### Scenario: gradle.properties is seeded
- **WHEN** the change lands
- **THEN** `gradle.properties` exists at the repo root and contains a `version=` entry

### Requirement: CI distributes debug APK on semantic-release publish

The `release.yaml` workflow SHALL run a `distribute` job that builds and uploads a debug APK to Firebase App Distribution if and only if the preceding `release` job's `new-release-published` output is `'true'`. The `distribute` job SHALL build from post-bump `main` so the uploaded APK's `versionName` reflects the just-published version.

#### Scenario: distribute job runs only on new release
- **WHEN** the `release` job completes with `new-release-published == 'true'`
- **THEN** the `distribute` job runs
- **AND** when `release` completes with `new-release-published != 'true'`, the `distribute` job is skipped

#### Scenario: distribute checks out post-bump main
- **WHEN** the `distribute` job's checkout step runs
- **THEN** it checks out `ref: main` (not the workflow's triggering SHA), so semantic-release's version-bump commit is present in the working tree

#### Scenario: distribute decodes service-account secret
- **WHEN** the `distribute` job starts the build step
- **THEN** the `FIREBASE_SERVICE_ACCOUNT` repository secret is base64-decoded to `/tmp/firebase-sa-key.json`
- **AND** the decoded path is exposed as `GOOGLE_APPLICATION_CREDENTIALS` to the Gradle invocation

#### Scenario: Release notes carry version and SHA
- **WHEN** the `distribute` job invokes Gradle
- **THEN** `APP_DISTRIBUTION_RELEASE_NOTES` env equals `"v<new-release-version> — <github.sha>"`

### Requirement: Service-account credentials are never committed

The repository SHALL gitignore Firebase service-account JSON file name patterns to prevent accidental commit of long-lived credentials.

#### Scenario: Service-account JSONs are ignored
- **WHEN** a file named `firebase-sa-key.json`, `firebase-service-account.json`, or `google-application-credentials.json` exists in the working tree
- **THEN** `git status` reports the file as ignored, not untracked

#### Scenario: google-services.json remains tracked
- **WHEN** `app/google-services.json` is present
- **THEN** it is tracked by git (not ignored), because the API key inside is package-name + SHA-1-fingerprint scoped and safe in a public repo

### Requirement: Instrumented test verifies Firebase wiring

The `:app` module SHALL include an instrumented test (`FirebaseInitTest`) asserting that Firebase init, App Check availability, and Analytics SDK availability all succeed after `Application.onCreate()`. The test SHALL run via `./gradlew :app:connectedDebugAndroidTest` against any emulator with Google Play Services.

#### Scenario: FirebaseApp init assertion
- **WHEN** `FirebaseInitTest.firebaseApp_isInitializedAfterApplicationOnCreate` runs
- **THEN** the test passes by observing a non-null `FirebaseApp.getInstance()` with `projectId == "nubecita-2a4c1"`

#### Scenario: App Check availability assertion
- **WHEN** `FirebaseInitTest.firebaseAppCheck_isAvailableAfterApplicationOnCreate` runs
- **THEN** the test passes by observing a non-null `FirebaseAppCheck.getInstance()`

#### Scenario: Analytics availability assertion
- **WHEN** `FirebaseInitTest.firebaseAnalytics_isAvailable` runs
- **THEN** the test passes by observing a non-null `FirebaseAnalytics.getInstance(applicationContext)`
