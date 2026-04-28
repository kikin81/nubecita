## Context

Nubecita has no debug-build distribution channel and no analytics surface. Internal testers can't install in-progress builds without a developer machine; product decisions ship blind. Slabsnap (sibling app, single-module) has a working Firebase + App Distribution setup that nubecita can port — but nubecita is a public repo, which changes the threat model: anyone can clone the source plus reuse `google-services.json` and pollute the analytics dashboard with traffic from forks. App Check with Play Integrity is bundled into this change because the analytics surface we're enabling is corruptible from day one without it.

Existing relevant state:
- `:app` is single-module for plugin application; `nubecita.android.application` convention plugin centralizes Android/Compose/Hilt setup, but `kotlin-serialization` is applied directly in `:app` (precedent for `:app`-only plugins staying out of the convention plugin).
- `NubecitaApplication.onCreate()` already initializes Timber under `BuildConfig.DEBUG`. No other init runs there yet.
- `release.yaml` uses `open-turo/actions-jvm/release@v2` for semantic-release; line 45–48 of the file is a TODO comment for "Firebase App Distribution is deferred to a follow-up design" — this change closes that TODO.
- `versionName = "1.0"` is hardcoded in `:app/build.gradle.kts`. Slabsnap's pattern reads from `project.property("version")`.
- `app/google-services.json` is already placed in the working tree (Firebase project `nubecita-2a4c1`, package `net.kikin.nubecita`).
- AGP 9.2.0 + Kotlin 2.3.21 are bleeding-edge but match the slabsnap-confirmed combo with `firebaseBom = "34.11.0"`, `firebaseAppDistribution = "5.2.1"`, `googleServices = "4.4.4"`.

## Goals / Non-Goals

**Goals:**
- Enable internal-tester distribution of debug-signed APKs through Firebase App Distribution.
- Enable Firebase Analytics so the team has telemetry against the 6-day cycle.
- Install Firebase App Check (Play Integrity in release, debug provider in debug) so the analytics signal is defendable against fork-driven pollution once enforcement is enabled.
- Wire the CI distribution upload to semantic-release publishes, with the APK's `versionName` matching the just-published version.
- Establish credential-handling guardrails so service-account JSONs are never committed.

**Non-Goals:**
- Production release signing (Play App Signing or self-managed release keystore). App Distribution to internal testers stays on debug-signed APKs.
- Crashlytics, Firebase Performance Monitoring, Firebase Remote Config — separate scope.
- Flipping App Check from monitor mode to enforce mode in the Firebase console — separate ticket after telemetry confirms legitimate traffic carries valid tokens.
- Adding `reactivecircus/android-emulator-runner` to CI. `FirebaseInitTest` is ready and will be picked up automatically when that ticket lands; this change does not depend on it.
- Migrating initializers to `androidx.startup`. App Check has `FirebaseInitProvider`-ordering constraints that make `Application.onCreate` the canonical home; revisit when ≥2 unconstrained initializers exist.
- Pre-commit credential scanner (gitleaks). Separate decision (which scanner, FP policy).

## Decisions

### Decision: Apply Firebase plugins directly in `:app/build.gradle.kts`, not in the convention plugin

`nubecita.android.application` already centralizes Android/Compose/Hilt setup. Adding `google-services` and `firebase-appdistribution` to it would teach a generic convention plugin about Firebase, even though `:app` is the only consumer. The repo already has precedent for `:app`-only plugins (kotlin-serialization) staying out of the convention plugin. If `:app:wear` or `:app:tv` is ever introduced, revisit.

**Alternative considered:** Push Firebase plugins into the convention plugin to centralize "this is the application module" identity. Rejected — premature abstraction for a single consumer.

### Decision: Install App Check provider in `Application.onCreate()`, not via `androidx.startup`

`androidx.startup` is the cleaner long-term home for app-startup initializers, but App Check requires Firebase to be initialized first (`installAppCheckProviderFactory` calls `FirebaseAppCheck.getInstance()` which delegates to the default `FirebaseApp`). Firebase auto-initializes through its own `FirebaseInitProvider` ContentProvider, which runs *before* `Application.onCreate` but is in the same lifecycle bracket as androidx.startup's ContentProvider. Forcing "after `FirebaseInitProvider`" requires manifest `initOrder` tweaks or disabling `FirebaseInitProvider` entirely and re-initializing Firebase manually — fragile patterns Firebase docs don't endorse. Firebase's official docs put App Check init in `Application.onCreate()`. Two lines next to the existing Timber init is the right size.

**Alternative considered:** Disable `FirebaseInitProvider` via manifest meta-data and own init explicitly inside an `Initializer<FirebaseApp>`. Rejected — extra moving parts for no observable benefit; deviates from Firebase docs.

### Decision: Wire `versionName` to `project.property("version")` and seed `gradle.properties` with `version=1.0.0`

Today `versionName = "1.0"` is hardcoded. The CI distribute job needs the APK's `versionName` to match the version semantic-release just cut. Slabsnap solves this by reading `project.property("version").toString()` from gradle.properties, which `open-turo/actions-jvm/release@v2` updates as part of the release commit. Seeding `version=1.0.0` establishes a starting baseline; semantic-release drives bumps from there.

**Alternative considered:** Inject `versionName` via Gradle `-Pversion=` only at CI invocation time and leave the file alone. Rejected — divergent local vs. CI behavior, harder to reason about; a committed `version=` is also useful for release-note generation tooling.

### Decision: `distribute` job checks out `ref: main` to fix the slabsnap pre-bump-checkout gap

Slabsnap's `release.yaml` `distribute` job uses `actions/checkout@v6` without a `ref:`. By default this checks out the SHA that triggered the workflow — i.e., the pre-bump tree. The `release` job runs semantic-release, which pushes a separate commit updating `gradle.properties`'s `version=`. The `distribute` job then builds with the OLD version. The fix is `ref: main`, which fetches the latest main and includes the bump commit.

**Race window:** if a second commit lands on `main` between `release` finishing and `distribute` starting, `distribute` builds *that* tree. `concurrency: cancel-in-progress: false` plus low main-merge cadence makes this rare; flagged in Risks.

**Alternative considered:** Have the `release` job emit the bump commit's SHA as an output and check it out explicitly (e.g., `ref: ${{ needs.release.outputs.commit-sha }}`). Cleaner, but `open-turo/actions-jvm/release@v2`'s output schema doesn't currently expose this. Falls back to `ref: main`.

### Decision: One instrumented test, not Robolectric or unit tests

The bd issue's acceptance criteria are integration-shaped (real device, real Firebase console, real CI run). Robolectric for one `if (BuildConfig.DEBUG)` branch is theatrical. An emulator job in CI is overkill for one shallow assertion (and the emulator-runner is its own ticket). One instrumented test asserts the *survival* of Firebase + App Check + Analytics init after `Application.onCreate()`, runs locally with `./gradlew :app:connectedDebugAndroidTest`, and is automatically picked up by future CI emulator coverage.

**Alternative considered:** No tests, only build-time validation + manual smoke runbook. Rejected — leaves a regression-detection hole for future work that touches `NubecitaApplication.onCreate`.

**Alternative considered:** Add Robolectric to test the `if/else` branch directly. Rejected — pulls a tooling dep into `:app/test` for one branch, doesn't exercise actual Firebase init.

### Decision: App Check enforcement starts in monitor mode

The bd issue's threat model (fork-driven analytics pollution) only kicks in when App Check is in **enforce** mode. Firebase docs and slabsnap's experience both recommend running in **monitor** mode for ~3 days first, observing real-traffic token issuance, and only then flipping to enforce — otherwise legitimate traffic from devices missing a registered debug token gets rejected. The flip is a console setting, not a code change. Tracked as a follow-up bd ticket. This change ships with the wiring complete and the console in monitor mode; the protection is *available* but not *engaged*.

### Decision: Service-account credentials live only in GitHub Actions secret + ephemeral runner /tmp

Long-lived service-account JSON is a higher-blast-radius credential than the package-scoped `google-services.json`. It SHALL never appear in the repo or in a developer's working tree post-secret-setup. The `.gitignore` adds belt-and-braces patterns (`firebase-sa-*.json`, `firebase-service-account*.json`, `google-application-credentials*.json`), but the primary control is "don't have the file on disk after pasting the base64 into the GitHub secret." The CI runner decodes it to `/tmp/firebase-sa-key.json` per job, where it dies with the runner.

## Risks / Trade-offs

- **AGP 9.2.0 plugin compatibility** — `google-services 4.4.4` and `firebase-appdistribution-gradle-plugin 5.2.1` are bleeding-edge alongside AGP 9.x. → Mitigation: slabsnap runs the same combo successfully today, and the implementation tasks include a clean local `assembleDebug` as the first verification. If incompatible, fall back to the latest plugin versions slabsnap is pinned to.
- **Distribute-job race window** — `ref: main` picks up the post-bump SHA, but if a second commit lands on main between `release` finishing and `distribute` starting, the APK builds from that newer tree. → Mitigation: `concurrency: cancel-in-progress: false` plus low main-merge cadence make this rare; documented for future ops awareness.
- **Per-device debug-token registration friction** — each developer's emulator/physical device prints a different App Check debug token on first launch and needs registration in the Firebase console under "Manage debug tokens." → Mitigation: documented in the Migration Plan runbook below; not a one-time setup.
- **App Check in monitor mode does not yet defend analytics** — the threat the bd issue cites (fork-driven analytics pollution) is only mitigated after the console is flipped to enforce mode. This change ships the wiring; the follow-up enforcement ticket actually closes the gap. → Mitigation: follow-up ticket explicitly tracked as a non-goal here; recommend ~3 days of monitor-mode telemetry first.
- **Release-job currently has no checkout step before `actions-jvm/release@v2`** — adding outputs requires the action to be invoked with the tree checked out for semantic-release config to be readable. The current `release.yaml` already has `open-turo/actions-jvm/release@v2` as the only step under `release`; we need to add a `Checkout` step before it. → Mitigation: include the checkout step in the workflow change. Verify the existing release behavior is preserved.

## Migration Plan

**One-time Firebase console setup** (executed by the maintainer, not by code):

1. Confirm Firebase project `nubecita-2a4c1` → Project Settings → General → Android app `net.kikin.nubecita` exists and matches the committed `app/google-services.json`'s `mobilesdk_app_id`.
2. Register debug keystore SHA-1 fingerprint:
   ```bash
   keytool -list -v \
     -keystore ~/.android/debug.keystore \
     -alias androiddebugkey -storepass android -keypass android \
     | grep SHA1
   ```
   Paste under app → "SHA certificate fingerprints."
3. App Check → Apps → register **Play Integrity** as production provider. Leave enforcement in **monitor** mode.
4. App Distribution → Testers & Groups → create group `internal-testers`, add tester emails.
5. Service accounts → Generate new private key → save locally as `firebase-sa-key.json`. Grant the account **Firebase App Distribution Admin** role.
6. `base64 -i firebase-sa-key.json | pbcopy` → GitHub repo Settings → Secrets and variables → Actions → New repository secret named `FIREBASE_SERVICE_ACCOUNT`, paste the base64 value.
7. Delete the local `firebase-sa-key.json` from disk after the secret is set.

**Per-device App Check debug-token registration** (one-time per dev device/emulator):

1. Run a debug build on the device.
2. `adb logcat | grep -i "App Check"` — find the printed debug token.
3. Firebase console → App Check → "Manage debug tokens" → register the token under app `net.kikin.nubecita`.

**Post-merge first-distribution smoke**:

1. Land the change to `main`. Verify `release.yaml` runs both `release` and `distribute` jobs.
2. Internal-tester email arrives from Firebase App Distribution.
3. Install the distributed APK. Open it.
4. `adb shell setprop debug.firebase.analytics.app net.kikin.nubecita`.
5. Navigate the app; confirm events appear in Firebase console → Analytics → DebugView within ~1 minute.
6. After ~3 days of monitor-mode App Check telemetry shows valid tokens, open the follow-up ticket to flip enforcement to enforce mode.

**Rollback:**
- The change is largely additive. Reverting the commit removes the plugins, deps, App Check init, distribute job, and `.gitignore` entries.
- `gradle.properties`'s `version=` and `versionName = project.property("version")` are safe to keep on revert (or to revert by restoring `versionName = "1.0"` if a future ticket needs the old shape).
- Firebase console state (registered SHA-1, internal-testers group, service-account credential) is orthogonal to code; revert leaves them intact (and harmless).
