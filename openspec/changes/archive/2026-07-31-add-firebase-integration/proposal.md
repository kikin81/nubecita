## Why

Internal testers (and eventually beta testers) need a way to install in-progress builds without going through the Play Store, and the team needs telemetry to inform the 6-day cycle. Today there is no distribution channel for debug APKs and no analytics surface — every change ships blind. Bundling App Check with Play Integrity into the same change is a deliberate scope expansion: nubecita is a public repo, so anyone can clone the source plus reuse `app/google-services.json` and pollute the project's analytics dashboard with traffic from their fork. Without App Check enforcement, the analytics signal we're enabling is corruptible from day one.

## What Changes

- **Add Firebase BoM + four runtime deps** to `:app`: `firebase-analytics`, `firebase-appcheck-playintegrity`, and `firebase-appcheck-debug` (debug-only). All versionless under the BoM.
- **Apply two Gradle plugins** (`com.google.gms.google-services`, `com.google.firebase.appdistribution`) directly in `:app/build.gradle.kts` — not in the convention plugin, since `:app` is the only consumer.
- **Configure App Distribution** on the `debug` build type: tester group `internal-testers`, release notes from `APP_DISTRIBUTION_RELEASE_NOTES` env.
- **Initialize App Check** in `NubecitaApplication.onCreate()` using `DebugAppCheckProviderFactory` for debug builds and `PlayIntegrityAppCheckProviderFactory` for release.
- **Wire `versionName`** to `project.property("version")` (semantic-release-managed) and seed `gradle.properties` with `version=1.0.0`. Replaces the hardcoded `versionName = "1.0"`.
- **Extend `release.yaml`** with a `distribute` job gated on `new-release-published == 'true'`. Job explicitly checks out post-bump `main` (fixes the slabsnap-pattern gap where `distribute` would build pre-bump trees), decodes a base64 service-account JSON from secrets, and runs `./gradlew assembleDebug appDistributionUploadDebug`.
- **Commit `app/google-services.json`** (already placed). The API key is package-name + SHA-1-fingerprint scoped — safe in public repos.
- **Add credential-pattern entries to `.gitignore`** to prevent accidental commit of service-account JSONs.
- **Add one instrumented test** (`FirebaseInitTest`) covering Firebase init, App Check availability, and Analytics SDK availability. Runs locally today; runs in CI when the future emulator-runner ticket lands.

No deviation from MVI / Compose / Hilt / Room / Coil baseline. This is pure build, init, and CI infrastructure.

## Capabilities

### New Capabilities
- `firebase-integration`: Firebase Analytics, App Check, and App Distribution wiring — Gradle apply, app initialization, CI upload pipeline, and credential-handling guardrails.

### Modified Capabilities
<!-- None. Purely additive infrastructure. -->

## Impact

**Code:**
- `gradle/libs.versions.toml` — three new versions, four new libraries, two new plugins.
- `build.gradle.kts` (root) — register `google-services` and `firebase-appdistribution` as `apply false`.
- `gradle.properties` (new) — seed `version=1.0.0`.
- `app/build.gradle.kts` — apply Firebase plugins, add Firebase deps, wire `versionName`, configure App Distribution on debug build type.
- `app/src/main/java/net/kikin/nubecita/NubecitaApplication.kt` — install App Check provider factory in `onCreate` ahead of Timber init.
- `app/src/androidTest/java/net/kikin/nubecita/FirebaseInitTest.kt` (new) — three assertions on Firebase singletons.
- `.github/workflows/release.yaml` — replace TODO with `distribute` job depending on `release`; capture release outputs.
- `.gitignore` — exclude `firebase-sa-*.json`, `firebase-service-account*.json`, `google-application-credentials*.json`.

**Already placed (no diff):**
- `app/google-services.json` — Firebase project `nubecita-2a4c1`, package `net.kikin.nubecita`.

**External (manual, runbook in design.md):**
- Firebase console: register debug SHA-1 fingerprint; create `internal-testers` group; register Play Integrity provider in monitor mode; generate service-account JSON.
- GitHub Actions: set `FIREBASE_SERVICE_ACCOUNT` repository secret (base64-encoded service-account JSON).

**Dependencies / system:**
- Build now requires `app/google-services.json` to be present at compile time; missing the file fails `:app:processDebugGoogleServices`. The committed file satisfies this.
- `release.yaml` distribute job adds runtime cost (additional Gradle build per published release) but only fires on semantic-release publishes, not every push.
- App Check enforcement in the Firebase console starts in **monitor mode**; flipping to **enforce** is a follow-up ticket after telemetry confirms legitimate traffic.

## Non-goals

- Production release signing (Play App Signing or self-managed release keystore).
- Crashlytics, Firebase Performance Monitoring, Firebase Remote Config.
- Flipping App Check from monitor mode to enforce mode (separate follow-up).
- Adding `reactivecircus/android-emulator-runner` to CI (separate bd ticket; `FirebaseInitTest` is ready and will be picked up automatically once that lands).
- Pre-commit credential scanner (gitleaks or similar) — separate decision.
- Migrating `Application.onCreate` initializers to `androidx.startup` — App Check has `FirebaseInitProvider` ordering constraints that make `onCreate` the canonical home; revisit when ≥2 unconstrained initializers exist.
- Distributing release-signed APKs (App Distribution stays on debug-signed builds for `internal-testers` until production signing lands).
