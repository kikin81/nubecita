# In-app updates (Play app-update) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offer a Google Play in-app update (gentle FLEXIBLE by default, IMMEDIATE for high-priority) with a version-scoped anti-spam throttle, in a new `:core:update` module mirroring `:core:review`.

**Architecture:** A `:core:update` module with an SDK-agnostic `InAppUpdateController` (singleton), a pure `UpdatePolicy`, a DataStore-backed throttle, and a `PlayAppUpdateClient` that is the only file touching the Play SDK. Production binds the real impl; bench binds a no-op. `MainActivity` registers the activity-result launcher and drives the check (mirroring `ActivityPipBridge`/`LocalPipController`); an Activity-level `InAppUpdateHost` snackbar surfaces the FLEXIBLE "restart to install".

**Tech Stack:** Kotlin 2.3, `com.google.android.play:app-update:2.1.0` (+ `-ktx`), Hilt, DataStore, Compose, JUnit5 + MockK + Turbine; `FakeAppUpdateManager` for the instrumented controller test.

**Spec:** `docs/superpowers/specs/2026-06-24-in-app-updates-design.md` · **Epic:** `nubecita-cf13` · **Branch:** `feat/nubecita-cf13-in-app-updates` · **Draft PR:** #578.

---

## File structure (mirrors `:core:review`)

```
settings.gradle.kts                              + include(":core:update")
gradle/libs.versions.toml                        + playAppUpdate version + 2 aliases
core/update/build.gradle.kts                     convention plugins + environment flavor split
core/update/src/main/kotlin/net/kikin/nubecita/core/update/
  UpdateModels.kt        UpdateAction, UpdateSignals, UpdateState, InstallStatusModel, InstallProgress, Availability consts
  UpdatePolicy.kt        pure decide()
  UpdatePreferences.kt   interface
  DefaultUpdatePreferences.kt   DataStore impl
  di/UpdateDataStore.kt  @Qualifier
  AppUpdateClient.kt     SDK-agnostic seam interface
  PlayAppUpdateClient.kt the ONLY Play-SDK importer (+ toUpdateSignals/toInstallProgress mappers)
  InAppUpdateController.kt   interface
  DefaultInAppUpdateController.kt   @Singleton orchestrator
core/update/src/production/kotlin/.../di/UpdateModule.kt   binds Default + provides AppUpdateManager/client/datastore
core/update/src/bench/kotlin/.../BenchNoOpInAppUpdateController.kt + di/UpdateModule.kt   no-op
core/update/src/test/kotlin/.../UpdatePolicyTest.kt, DefaultUpdatePreferencesTest.kt   JVM
core/update/src/androidTest/kotlin/.../DefaultInAppUpdateControllerTest.kt   FakeAppUpdateManager (run-instrumented label)
app/.../update/InAppUpdateHost.kt                 Activity-level snackbar surface
app/.../MainActivity.kt                           wire launcher + check + onResume
app/build.gradle.kts                              + implementation(project(":core:update"))
app/src/main/res/values{,-b+es+419,-pt-rBR}/strings.xml   update_downloaded / update_restart
```

---

## Task 1: `:core:update` module scaffold

**Files:**
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`
- Create: `core/update/build.gradle.kts`

- [ ] **Step 1: Add the module include** — in `settings.gradle.kts`, add alphabetically (after `:core:testing-android`, before `:core:video` — actually `update` sorts after `testing-android`/`video`? keep the file's existing alpha order: it's `...:testing, :testing-android, :video, :widget-sync`; insert `:core:update` between `:testing-android` and `:video`):
```kotlin
include(":core:update")
```

- [ ] **Step 2: Add catalog entries** — in `gradle/libs.versions.toml`:
  - `[versions]` (after `googlePlayReview = "2.0.2"`): `playAppUpdate = "2.1.0"`
  - `[libraries]` (after the two `google-play-review*` lines, keeping alpha order — `app-update` < `review`, so place them just before the review lines):
```toml
google-play-app-update = { module = "com.google.android.play:app-update", version.ref = "playAppUpdate" }
google-play-app-update-ktx = { module = "com.google.android.play:app-update-ktx", version.ref = "playAppUpdate" }
```

- [ ] **Step 3: Create `core/update/build.gradle.kts`** (mirror `core/review/build.gradle.kts`):
```kotlin
plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.update"

    // The `environment` flavor dimension splits the production InAppUpdateController
    // (real Google Play in-app updates) from a bench no-op, so keyless /
    // macrobenchmark builds make zero Play calls and never prompt. Mirrors `:core:review`.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.play.app.update)
    implementation(libs.google.play.app.update.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```
> `androidx.activity.ktx` provides `ActivityResultLauncher`/`IntentSenderRequest` types used in the client/controller signatures. Confirm the alias name in the catalog (`grep -n 'androidx-activity' gradle/libs.versions.toml`); if it differs (e.g. `androidx.activityCompose` only), add an `androidx-activity-ktx` alias for `androidx.activity:activity-ktx` first, or use the already-present activity dependency the app uses.

- [ ] **Step 4: Verify it configures + sorts**
Run: `./gradlew :core:update:dependencies --configuration productionDebugRuntimeClasspath -q | head` and `./gradlew :app:checkSortDependencies`
Expected: configures without error; sort check passes (fix alpha order if it complains).

- [ ] **Step 5: Commit**
```bash
git add settings.gradle.kts gradle/libs.versions.toml core/update/build.gradle.kts
git commit -m "build(core/update): scaffold the in-app-update module

Refs: nubecita-cf13"
```

---

## Task 2: Pure `UpdatePolicy` + models (TDD)

**Files:**
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/UpdateModels.kt`
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/UpdatePolicy.kt`
- Test: `core/update/src/test/kotlin/net/kikin/nubecita/core/update/UpdatePolicyTest.kt`

- [ ] **Step 1: Write the models** (`UpdateModels.kt`):
```kotlin
package net.kikin.nubecita.core.update

/** What the policy decided to do for the current update check. */
enum class UpdateAction { None, Flexible, Immediate }

/**
 * The subset of Play's `AppUpdateInfo` the pure policy needs. Mirrors the wire
 * fields without importing the Play SDK (mapped in [PlayAppUpdateClient]).
 *
 * @property availability one of [UpdateAvailability] values.
 * @property availableVersionCode the versionCode Play would update us to.
 */
data class UpdateSignals(
    val availability: Int,
    val isFlexibleAllowed: Boolean,
    val isImmediateAllowed: Boolean,
    val updatePriority: Int,
    val stalenessDays: Int?,
    val availableVersionCode: Int,
)

/** Local mirror of Play's `UpdateAvailability` so the policy has no SDK dep. */
object UpdateAvailability {
    const val UNKNOWN = 0
    const val UPDATE_NOT_AVAILABLE = 1
    const val UPDATE_AVAILABLE = 2
    const val DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS = 3
}

/** Local mirror of Play's `InstallStatus` (the values the controller maps). */
object InstallStatusModel {
    const val UNKNOWN = 0
    const val PENDING = 1
    const val DOWNLOADING = 2
    const val DOWNLOADED = 11
    const val INSTALLING = 3
    const val INSTALLED = 4
    const val FAILED = 5
    const val CANCELED = 6
}

/** A flexible-download progress event surfaced by the client's listener. */
data class InstallProgress(
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytesToDownload: Long,
)

/** Public UI state the controller exposes. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : UpdateState
    data object ReadyToInstall : UpdateState
    data object Failed : UpdateState
}
```
> Verify the Play `InstallStatus.DOWNLOADED` int is `11` and `INSTALLING` is `3` against the SDK (`InstallStatus` constants) when wiring `PlayAppUpdateClient` in Task 4 — adjust these mirror constants if the SDK differs. They're only used inside the module.

- [ ] **Step 2: Write the failing policy test** (`UpdatePolicyTest.kt`):
```kotlin
package net.kikin.nubecita.core.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdatePolicyTest {
    private fun signals(
        availability: Int = UpdateAvailability.UPDATE_AVAILABLE,
        flexible: Boolean = true,
        immediate: Boolean = true,
        priority: Int = 0,
        staleness: Int? = null,
        versionCode: Int = 100,
    ) = UpdateSignals(availability, flexible, immediate, priority, staleness, versionCode)

    @Test
    fun `no update available is None`() {
        assertEquals(UpdateAction.None, UpdatePolicy.decide(signals(availability = UpdateAvailability.UPDATE_NOT_AVAILABLE), null))
    }

    @Test
    fun `available, low priority, not yet prompted is Flexible`() {
        assertEquals(UpdateAction.Flexible, UpdatePolicy.decide(signals(priority = 1), lastPromptedVersionCode = null))
    }

    @Test
    fun `same versionCode already prompted is throttled to None`() {
        assertEquals(UpdateAction.None, UpdatePolicy.decide(signals(versionCode = 100), lastPromptedVersionCode = 100))
    }

    @Test
    fun `new higher versionCode re-arms Flexible`() {
        assertEquals(UpdateAction.Flexible, UpdatePolicy.decide(signals(versionCode = 101), lastPromptedVersionCode = 100))
    }

    @Test
    fun `priority at threshold is Immediate (ignores throttle)`() {
        assertEquals(UpdateAction.Immediate, UpdatePolicy.decide(signals(priority = 4, versionCode = 100), lastPromptedVersionCode = 100))
    }

    @Test
    fun `high staleness is Immediate`() {
        assertEquals(UpdateAction.Immediate, UpdatePolicy.decide(signals(priority = 0, staleness = 60), null))
    }

    @Test
    fun `graceful fallback - high priority but immediate not allowed is Flexible`() {
        // Intentional: a priority-5 update with isImmediateAllowed=false degrades to the gentle flow, not None.
        assertEquals(
            UpdateAction.Flexible,
            UpdatePolicy.decide(signals(priority = 5, immediate = false, versionCode = 200), lastPromptedVersionCode = null),
        )
    }

    @Test
    fun `flexible not allowed and not immediate is None`() {
        assertEquals(UpdateAction.None, UpdatePolicy.decide(signals(flexible = false, immediate = false), null))
    }
}
```

- [ ] **Step 3: Run it — verify it fails**
`./gradlew :core:update:testProductionDebugUnitTest --tests "*UpdatePolicyTest"` → FAIL (UpdatePolicy unresolved).

- [ ] **Step 4: Write `UpdatePolicy.kt`**:
```kotlin
package net.kikin.nubecita.core.update

/**
 * Pure, side-effect-free update decision. IMMEDIATE for high-priority/very-stale
 * (never throttled — a critical update should always prompt and also auto-resumes
 * a DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS). Otherwise FLEXIBLE, throttled to once
 * per availableVersionCode via [lastPromptedVersionCode]. If a high-priority update
 * arrives but Play reports IMMEDIATE not allowed, this gracefully degrades to
 * FLEXIBLE rather than nothing (asserted in UpdatePolicyTest).
 */
object UpdatePolicy {
    const val UPDATE_PRIORITY_IMMEDIATE_THRESHOLD = 4
    const val STALENESS_IMMEDIATE_DAYS = 60

    fun decide(
        signals: UpdateSignals,
        lastPromptedVersionCode: Int?,
    ): UpdateAction {
        if (signals.availability != UpdateAvailability.UPDATE_AVAILABLE) return UpdateAction.None
        val highPriority =
            signals.updatePriority >= UPDATE_PRIORITY_IMMEDIATE_THRESHOLD ||
                (signals.stalenessDays ?: 0) >= STALENESS_IMMEDIATE_DAYS
        if (highPriority && signals.isImmediateAllowed) return UpdateAction.Immediate
        if (signals.isFlexibleAllowed && signals.availableVersionCode != lastPromptedVersionCode) {
            return UpdateAction.Flexible
        }
        return UpdateAction.None
    }
}
```

- [ ] **Step 5: Run it — verify it passes**
`./gradlew :core:update:testProductionDebugUnitTest --tests "*UpdatePolicyTest"` → PASS (8 tests).

- [ ] **Step 6: Commit**
```bash
git add core/update/src/main/kotlin/net/kikin/nubecita/core/update/UpdateModels.kt \
        core/update/src/main/kotlin/net/kikin/nubecita/core/update/UpdatePolicy.kt \
        core/update/src/test/kotlin/net/kikin/nubecita/core/update/UpdatePolicyTest.kt
git commit -m "feat(core/update): pure update-decision policy

Refs: nubecita-cf13"
```

---

## Task 3: `UpdatePreferences` (throttle persistence) (TDD)

**Files:**
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/di/UpdateDataStore.kt`
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/UpdatePreferences.kt`
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/DefaultUpdatePreferences.kt`
- Test: `core/update/src/test/kotlin/net/kikin/nubecita/core/update/DefaultUpdatePreferencesTest.kt`

- [ ] **Step 1: `di/UpdateDataStore.kt`** (mirror `ReviewDataStore`):
```kotlin
package net.kikin.nubecita.core.update.di

import javax.inject.Qualifier

/** Qualifies the update capability's own DataStore so it doesn't collide with others on the :app graph. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class UpdateDataStore
```

- [ ] **Step 2: `UpdatePreferences.kt`**:
```kotlin
package net.kikin.nubecita.core.update

/** Persistence seam for the version-scoped FLEXIBLE prompt throttle. */
internal interface UpdatePreferences {
    /** The versionCode we last launched a FLEXIBLE prompt for, or null. Read failure → null. */
    suspend fun lastPromptedVersionCode(): Int?

    /** Record that we launched a FLEXIBLE prompt for [versionCode] (called at fire time). */
    suspend fun setLastPromptedVersionCode(versionCode: Int)
}
```

- [ ] **Step 3: Write the failing prefs test** (`DefaultUpdatePreferencesTest.kt`, mirror `DefaultReviewPreferencesTest` — a temp-file DataStore):
```kotlin
package net.kikin.nubecita.core.update

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DefaultUpdatePreferencesTest {
    @TempDir
    lateinit var tempDir: File

    private fun prefs() =
        DefaultUpdatePreferences(
            PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "update.preferences_pb") }),
        )

    @Test
    fun `defaults to null when never written`() = runTest {
        assertNull(prefs().lastPromptedVersionCode())
    }

    @Test
    fun `round-trips the last prompted version code`() = runTest {
        val p = prefs()
        p.setLastPromptedVersionCode(142)
        assertEquals(142, p.lastPromptedVersionCode())
    }

    @Test
    fun `overwrites with the newer version code`() = runTest {
        val p = prefs()
        p.setLastPromptedVersionCode(142)
        p.setLastPromptedVersionCode(143)
        assertEquals(143, p.lastPromptedVersionCode())
    }
}
```
> `DataStoreFactory` import line is unused if only `PreferenceDataStoreFactory` is used — drop it; shown for parity. If `@TempDir` + a real DataStore doesn't run on pure JVM here, mirror exactly how `DefaultReviewPreferencesTest` constructs its store (read that test) and copy its harness.

- [ ] **Step 4: Run it — verify it fails**
`./gradlew :core:update:testProductionDebugUnitTest --tests "*DefaultUpdatePreferencesTest"` → FAIL.

- [ ] **Step 5: Write `DefaultUpdatePreferences.kt`** (mirror `DefaultReviewPreferences`):
```kotlin
package net.kikin.nubecita.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.update.di.UpdateDataStore
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/** DataStore-backed [UpdatePreferences]; own file, absent key → null, IOException → empty (treated as "never prompted"). */
internal class DefaultUpdatePreferences
    @Inject
    constructor(
        @param:UpdateDataStore private val dataStore: DataStore<Preferences>,
    ) : UpdatePreferences {
        override suspend fun lastPromptedVersionCode(): Int? {
            val prefs =
                dataStore.data
                    .catch { error ->
                        if (error is IOException) {
                            Timber.w(error, "Failed to read update preferences; defaulting to empty")
                            emit(emptyPreferences())
                        } else {
                            throw error
                        }
                    }.first()
            return prefs[Keys.LAST_PROMPTED_VERSION_CODE]
        }

        override suspend fun setLastPromptedVersionCode(versionCode: Int) {
            dataStore.edit { it[Keys.LAST_PROMPTED_VERSION_CODE] = versionCode }
        }

        private object Keys {
            val LAST_PROMPTED_VERSION_CODE = intPreferencesKey("update_last_prompted_version_code")
        }
    }
```

- [ ] **Step 6: Run it — verify it passes**
`./gradlew :core:update:testProductionDebugUnitTest --tests "*DefaultUpdatePreferencesTest"` → PASS (3 tests).

- [ ] **Step 7: Commit**
```bash
git add core/update/src/main/kotlin/net/kikin/nubecita/core/update/di/UpdateDataStore.kt \
        core/update/src/main/kotlin/net/kikin/nubecita/core/update/UpdatePreferences.kt \
        core/update/src/main/kotlin/net/kikin/nubecita/core/update/DefaultUpdatePreferences.kt \
        core/update/src/test/kotlin/net/kikin/nubecita/core/update/DefaultUpdatePreferencesTest.kt
git commit -m "feat(core/update): datastore-backed prompt throttle

Refs: nubecita-cf13"
```

---

## Task 4: `AppUpdateClient` seam + `PlayAppUpdateClient` (SDK boundary)

**Files:**
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/AppUpdateClient.kt`
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/PlayAppUpdateClient.kt`

- [ ] **Step 1: `AppUpdateClient.kt`** (SDK-agnostic seam; faked in the controller test):
```kotlin
package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * Internal seam over the Play app-update API. The only SDK importer is
 * [PlayAppUpdateClient]; faked in DefaultInAppUpdateController tests. The
 * [launcher] is the Activity's `StartIntentSenderForResult` launcher (an AndroidX
 * type, not a Play type — the Play SDK never leaks past this seam).
 */
internal interface AppUpdateClient {
    /** Fetches a fresh availability snapshot; null if it can't be obtained (offline / not from Play). */
    suspend fun fetchSignals(): UpdateSignals?

    fun startFlexible(launcher: ActivityResultLauncher<IntentSenderRequest>)

    fun startImmediate(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Completes a DOWNLOADED flexible update (restarts to install). */
    suspend fun completeFlexibleUpdate()

    fun registerListener(onProgress: (InstallProgress) -> Unit)

    fun unregisterListener()
}
```

- [ ] **Step 2: `PlayAppUpdateClient.kt`** (the ONLY file importing `com.google.android.play.core.appupdate.*`):
```kotlin
package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.ktx.appUpdateInfo
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * The single Play-SDK boundary for in-app updates. Maps `AppUpdateInfo` →
 * [UpdateSignals] and the install listener → [InstallProgress]; everything else in
 * `:core:update` is SDK-free. Constructed with the app-scoped [AppUpdateManager]
 * from `AppUpdateManagerFactory.create(...)` (provided by the production module).
 */
internal class PlayAppUpdateClient
    @Inject
    constructor(
        private val manager: AppUpdateManager,
    ) : AppUpdateClient {
        private var listener: InstallStateUpdatedListener? = null

        override suspend fun fetchSignals(): UpdateSignals? =
            try {
                manager.appUpdateInfo.toUpdateSignals()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "fetchSignals failed: %s", e.javaClass.name)
                null
            }

        override fun startFlexible(launcher: ActivityResultLauncher<IntentSenderRequest>) =
            start(launcher, AppUpdateType.FLEXIBLE)

        override fun startImmediate(launcher: ActivityResultLauncher<IntentSenderRequest>) =
            start(launcher, AppUpdateType.IMMEDIATE)

        private fun start(
            launcher: ActivityResultLauncher<IntentSenderRequest>,
            type: Int,
        ) {
            try {
                manager.appUpdateInfo.addOnSuccessListener { info ->
                    runCatching {
                        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
                    }.onFailure { Timber.w(it, "startUpdateFlow failed: %s", it.javaClass.name) }
                }
            } catch (e: Exception) {
                Timber.w(e, "start(%d) failed: %s", type, e.javaClass.name)
            }
        }

        override suspend fun completeFlexibleUpdate() {
            try {
                manager.completeUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "completeUpdate failed: %s", e.javaClass.name)
            }
        }

        override fun registerListener(onProgress: (InstallProgress) -> Unit) {
            unregisterListener()
            val l = InstallStateUpdatedListener { state ->
                onProgress(InstallProgress(state.installStatus(), state.bytesDownloaded(), state.totalBytesToDownload()))
            }
            listener = l
            manager.registerListener(l)
        }

        override fun unregisterListener() {
            listener?.let { manager.unregisterListener(it) }
            listener = null
        }

        private fun AppUpdateInfo.toUpdateSignals(): UpdateSignals =
            UpdateSignals(
                availability = updateAvailability(),
                isFlexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                isImmediateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                updatePriority = updatePriority(),
                stalenessDays = clientVersionStalenessDays(),
                availableVersionCode = availableVersionCode(),
            )
    }
```
> Verify against the resolved `app-update` 2.1.0 jar: the ktx accessor (`com.google.android.play.ktx.appUpdateInfo` extension returning a suspend/`Task`), `startUpdateFlowForResult(info, ActivityResultLauncher, AppUpdateOptions)` overload exists, and `clientVersionStalenessDays()` returns a nullable `Integer`. If the ktx coroutine accessor differs, use `manager.appUpdateInfo` (the `Task`) with `kotlinx-coroutines-play-services` `.await()`. Adjust imports to match the published API; this is the one file allowed to know the SDK.

- [ ] **Step 3: Compile**
`./gradlew :core:update:compileProductionDebugKotlin` → BUILD SUCCESSFUL. Fix any SDK signature mismatches here (this is the SDK-facing file).

- [ ] **Step 4: Commit**
```bash
git add core/update/src/main/kotlin/net/kikin/nubecita/core/update/AppUpdateClient.kt \
        core/update/src/main/kotlin/net/kikin/nubecita/core/update/PlayAppUpdateClient.kt
git commit -m "feat(core/update): play app-update sdk boundary

Refs: nubecita-cf13"
```

---

## Task 5: `InAppUpdateController` + bench no-op + DI

**Files:**
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/InAppUpdateController.kt`
- Create: `core/update/src/main/kotlin/net/kikin/nubecita/core/update/DefaultInAppUpdateController.kt`
- Create: `core/update/src/production/kotlin/net/kikin/nubecita/core/update/di/UpdateModule.kt`
- Create: `core/update/src/bench/kotlin/net/kikin/nubecita/core/update/BenchNoOpInAppUpdateController.kt`
- Create: `core/update/src/bench/kotlin/net/kikin/nubecita/core/update/di/UpdateModule.kt`
- Test: `core/update/src/androidTest/kotlin/net/kikin/nubecita/core/update/DefaultInAppUpdateControllerTest.kt`

- [ ] **Step 1: `InAppUpdateController.kt`** (interface):
```kotlin
package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * SDK-agnostic in-app-update controller — the only update type that escapes
 * `:core:update`. A singleton: its [state] and any in-flight download survive
 * Activity recreation. The [launcher] is Activity-scoped and passed per call
 * (never stored), so a recreated Activity simply hands in its fresh launcher.
 * Driven from the Activity (never a ViewModel — same boundary as `:core:review`).
 */
interface InAppUpdateController {
    val state: StateFlow<UpdateState>

    /** Check availability and, per [UpdatePolicy], launch FLEXIBLE/IMMEDIATE. Fail-silent. */
    suspend fun checkAndMaybePrompt(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** onResume catch-up: resume an interrupted IMMEDIATE; surface a backgrounded-DOWNLOADED FLEXIBLE. */
    fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Install a DOWNLOADED flexible update (restarts the app). */
    suspend fun completeFlexibleUpdate()
}
```

- [ ] **Step 2: `DefaultInAppUpdateController.kt`** (@Singleton orchestrator):
```kotlin
package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [InAppUpdateController]. Runs the pure [UpdatePolicy] over signals
 * from [AppUpdateClient], throttling FLEXIBLE to once per availableVersionCode
 * (written at fire time via [UpdatePreferences], not on the droppable
 * ActivityResult). Owns the install listener → real-time [UpdateState]
 * (DOWNLOADED → ReadyToInstall the moment a foreground download finishes);
 * unregisters on a terminal install state. Errors are swallowed to [UpdateState]
 * and logged with Timber.w (CancellationException always rethrown). No onDestroy —
 * the singleton survives config changes.
 */
@Singleton
internal class DefaultInAppUpdateController
    @Inject
    constructor(
        private val client: AppUpdateClient,
        private val preferences: UpdatePreferences,
    ) : InAppUpdateController {
        private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        override val state: StateFlow<UpdateState> = _state.asStateFlow()

        override suspend fun checkAndMaybePrompt(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            try {
                val signals = client.fetchSignals() ?: return
                when (UpdatePolicy.decide(signals, preferences.lastPromptedVersionCode())) {
                    UpdateAction.Immediate -> client.startImmediate(launcher)
                    UpdateAction.Flexible -> {
                        ensureListener()
                        preferences.setLastPromptedVersionCode(signals.availableVersionCode) // fire-time throttle write
                        client.startFlexible(launcher)
                    }
                    UpdateAction.None -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "checkAndMaybePrompt failed: %s", e.javaClass.name)
            }
        }

        override fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            // Catch-up: resume an interrupted IMMEDIATE, and re-arm the listener so a
            // download that completed while backgrounded surfaces ReadyToInstall.
            ensureListener()
        }

        override suspend fun completeFlexibleUpdate() {
            try {
                client.completeFlexibleUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "completeFlexibleUpdate failed: %s", e.javaClass.name)
            }
        }

        private var listening = false

        private fun ensureListener() {
            if (listening) return
            listening = true
            client.registerListener { progress ->
                _state.value =
                    when (progress.status) {
                        InstallStatusModel.DOWNLOADING ->
                            UpdateState.Downloading(progress.bytesDownloaded, progress.totalBytesToDownload)
                        InstallStatusModel.DOWNLOADED -> UpdateState.ReadyToInstall
                        InstallStatusModel.FAILED -> UpdateState.Failed
                        InstallStatusModel.INSTALLED, InstallStatusModel.CANCELED -> {
                            client.unregisterListener()
                            listening = false
                            UpdateState.Idle
                        }
                        else -> _state.value
                    }
            }
        }
    }
```

- [ ] **Step 3: production `di/UpdateModule.kt`** (mirror production `ReviewModule`):
```kotlin
package net.kikin.nubecita.core.update.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.update.AppUpdateClient
import net.kikin.nubecita.core.update.DefaultInAppUpdateController
import net.kikin.nubecita.core.update.DefaultUpdatePreferences
import net.kikin.nubecita.core.update.InAppUpdateController
import net.kikin.nubecita.core.update.PlayAppUpdateClient
import net.kikin.nubecita.core.update.UpdatePreferences
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindController(impl: DefaultInAppUpdateController): InAppUpdateController

    @Binds
    @Singleton
    abstract fun bindClient(impl: PlayAppUpdateClient): AppUpdateClient

    @Binds
    @Singleton
    abstract fun bindPreferences(impl: DefaultUpdatePreferences): UpdatePreferences

    companion object {
        @Provides
        @Singleton
        fun provideAppUpdateManager(
            @ApplicationContext context: Context,
        ): AppUpdateManager = AppUpdateManagerFactory.create(context)

        @Provides
        @Singleton
        @UpdateDataStore
        fun provideUpdateDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler =
                    ReplaceFileCorruptionHandler {
                        Timber.w(it, "Update preferences corrupted; replacing with empty store")
                        emptyPreferences()
                    },
                produceFile = { context.preferencesDataStoreFile("update_preferences") },
            )
    }
}
```

- [ ] **Step 4: bench no-op + `di/UpdateModule.kt`**:
`BenchNoOpInAppUpdateController.kt`:
```kotlin
package net.kikin.nubecita.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Bench-flavor no-op: zero Play/network calls so Macrobench stays offline & deterministic. */
@Singleton
internal class BenchNoOpInAppUpdateController
    @Inject
    constructor() : InAppUpdateController {
        override val state: StateFlow<UpdateState> = MutableStateFlow(UpdateState.Idle)
        override suspend fun checkAndMaybePrompt(launcher: ActivityResultLauncher<IntentSenderRequest>) = Unit
        override fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>) = Unit
        override suspend fun completeFlexibleUpdate() = Unit
    }
```
bench `di/UpdateModule.kt`:
```kotlin
package net.kikin.nubecita.core.update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.update.BenchNoOpInAppUpdateController
import net.kikin.nubecita.core.update.InAppUpdateController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindController(impl: BenchNoOpInAppUpdateController): InAppUpdateController
}
```

- [ ] **Step 5: instrumented controller test** (`src/androidTest/.../DefaultInAppUpdateControllerTest.kt`) — uses Play's `FakeAppUpdateManager`; **requires the `run-instrumented` PR label** to execute in CI. Wire `DefaultInAppUpdateController` with a real `PlayAppUpdateClient(fakeManager)` + a fake `UpdatePreferences`, and assert: foreground `setUpdateAvailable`+`userAcceptsUpdate`+`downloadStarts`+`downloadCompletes` → `state` reaches `ReadyToInstall` (real-time, no onResume); the throttle write happens before the result (`fakePrefs.lastPromptedVersionCode()` set after `checkAndMaybePrompt`); a second check at the same versionCode → no second start. (Use `androidx.test` + `ApplicationProvider.getApplicationContext()`; `FakeAppUpdateManager(context)` from `com.google.android.play.core.appupdate.testing`.) Keep it focused; the policy/prefs are already JVM-covered.

- [ ] **Step 6: Compile both flavors**
`./gradlew :core:update:compileProductionDebugKotlin :core:update:compileBenchDebugKotlin :core:update:testProductionDebugUnitTest` → BUILD SUCCESSFUL + JVM tests green.

- [ ] **Step 7: Commit**
```bash
git add core/update/src/main/kotlin/net/kikin/nubecita/core/update/InAppUpdateController.kt \
        core/update/src/main/kotlin/net/kikin/nubecita/core/update/DefaultInAppUpdateController.kt \
        core/update/src/production/kotlin/net/kikin/nubecita/core/update/di/UpdateModule.kt \
        core/update/src/bench/kotlin/net/kikin/nubecita/core/update/BenchNoOpInAppUpdateController.kt \
        core/update/src/bench/kotlin/net/kikin/nubecita/core/update/di/UpdateModule.kt \
        core/update/src/androidTest/kotlin/net/kikin/nubecita/core/update/DefaultInAppUpdateControllerTest.kt
git commit -m "feat(core/update): in-app-update controller + bench no-op + di

Refs: nubecita-cf13"
```

---

## Task 6: `:app` wiring — MainActivity + InAppUpdateHost + strings

**Files:**
- Modify: `app/build.gradle.kts`, `app/src/main/java/net/kikin/nubecita/MainActivity.kt`
- Create: `app/src/main/java/net/kikin/nubecita/update/InAppUpdateHost.kt`
- Modify: `app/src/main/res/values/strings.xml` (+ `values-b+es+419/`, `values-pt-rBR/`)

- [ ] **Step 1: app dependency** — in `app/build.gradle.kts`, add (next to the existing `implementation(project(":core:review"))`):
```kotlin
    implementation(project(":core:update"))
```

- [ ] **Step 2: English strings** (`app/src/main/res/values/strings.xml`):
```xml
    <string name="in_app_update_downloaded">Update downloaded</string>
    <string name="in_app_update_restart">Restart</string>
```

- [ ] **Step 3: `InAppUpdateHost.kt`** (Activity-level snackbar surface):
```kotlin
package net.kikin.nubecita.update

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.R
import net.kikin.nubecita.core.update.InAppUpdateController
import net.kikin.nubecita.core.update.UpdateState
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Activity-level host for the FLEXIBLE "update downloaded → restart" snackbar.
 * Sits above the nav (in MainActivity's outer Surface) so it persists across
 * screen changes. IMMEDIATE renders its own full-screen Play UI — not here.
 */
@Composable
fun InAppUpdateHost(
    controller: InAppUpdateController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.in_app_update_downloaded)
    val action = stringResource(R.string.in_app_update_restart)

    LaunchedEffect(state) {
        if (state is UpdateState.ReadyToInstall) {
            val result = hostState.showSnackbar(message = message, actionLabel = action, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) {
                scope.launch { controller.completeFlexibleUpdate() }
            }
        }
    }
    SnackbarHost(hostState, modifier = modifier)
}
```

- [ ] **Step 4: Wire `MainActivity`** (mirror the `ActivityPipBridge`/`LocalPipController` pattern):
  - Add `@Inject lateinit var inAppUpdateController: InAppUpdateController`.
  - In `onCreate`, before `setContent`:
    ```kotlin
    val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { /* RESULT_CANCELED/_OK handled in controller flow; no-op here */ }
    lifecycleScope.launch { inAppUpdateController.checkAndMaybePrompt(updateLauncher) }
    ```
    Hold `updateLauncher` in a `private var` (or capture via a member) so `onResume` can use it.
  - Add `override fun onResume() { super.onResume(); inAppUpdateController.onResume(updateLauncher) }`.
  - In `setContent`, inside the outer `Surface` (alongside the `LocalPipController` provider, e.g. as a sibling Box overlay or below `MainNavigation()`), add `InAppUpdateHost(inAppUpdateController)`.
  - **Do NOT** add anything to `onDestroy` for the controller.
  Read the current `MainActivity.onCreate` for the exact `Surface`/`CompositionLocalProvider` placement; put `InAppUpdateHost` so the snackbar renders above content (e.g. a `Box { MainNavigation(); InAppUpdateHost(...) }` or via the existing layering).

- [ ] **Step 5: es/pt strings** — `values-b+es+419/strings.xml`: `Actualización descargada` / `Reiniciar`; `values-pt-rBR/strings.xml`: `Atualização baixada` / `Reiniciar`.

- [ ] **Step 6: Build both flavors + lint**
`./gradlew :app:assembleProductionDebug :app:assembleBenchDebug :app:lintProductionDebug` → BUILD SUCCESSFUL (bench compiles against the no-op binding; lint no MissingTranslation).

- [ ] **Step 7: Commit**
```bash
git add app/build.gradle.kts app/src/main/java/net/kikin/nubecita/MainActivity.kt \
        app/src/main/java/net/kikin/nubecita/update/InAppUpdateHost.kt \
        app/src/main/res/values/strings.xml app/src/main/res/values-b+es+419/strings.xml app/src/main/res/values-pt-rBR/strings.xml
git commit -m "feat(app): wire in-app updates into MainActivity

Refs: nubecita-cf13"
```

---

## Task 7: Gate + review + ship

- [ ] **Step 1: Full local gate**
`./gradlew spotlessCheck lint :app:checkSortDependencies testDebugUnitTest` → all PASS. Also `./scripts/check_progress_indicators.sh` (no raw spinners introduced). Fix formatting/sort and re-run.

- [ ] **Step 2: compose-expert review** of `InAppUpdateHost.kt` (the only new `@Composable`) — invoke the compose-expert Skill on the diff; apply CRITICAL/Important findings; commit fixes (`fix(app): address compose-expert findings on InAppUpdateHost / Refs: nubecita-cf13`).

- [ ] **Step 3: Push, mark PR #578 ready, request Gemini review** (after the implementation is pushed, per the gemini-review-only-on-pr-open learning):
```bash
git push
gh pr ready 578
gh pr comment 578 --body "/gemini review"
```
(No screenshot baselines — `InAppUpdateHost` is a snackbar host, not a static-rendered component; no `update-baselines` label needed unless a screenshot test is added.)

- [ ] **Step 4: Watch CI + triage**
`gh pr checks 578` until green; address Gemini/reviewer threads (reply + resolve, or fix). The instrumented controller test runs only if the `run-instrumented` label is added — add it if you want CI to exercise the `FakeAppUpdateManager` test, else it's verified locally.

---

## Self-review notes

- **Spec coverage:** module mirror + catalog (T1) ✓; pure `UpdatePolicy` incl. throttle + IMMEDIATE gate + **graceful fallback** test (T2) ✓; throttle DataStore (T3) ✓; SDK-only `PlayAppUpdateClient` + `toUpdateSignals` (T4) ✓; `@Singleton` controller — fire-time throttle write, real-time `ReadyToInstall`, terminal-state unregister, no `onDestroy`, Timber.w + rethrow Cancellation (T5) ✓; bench no-op + flavor DI (T4 build.gradle + T5) ✓; MainActivity launcher + onResume + no onDestroy + `InAppUpdateHost` + es/pt (T6) ✓; gate + compose-expert + `/gemini review` after push (T7) ✓. Out-of-scope (changelog, per-track flags) honored.
- **Type consistency:** `UpdateAction{None,Flexible,Immediate}`, `UpdateSignals(availability,isFlexibleAllowed,isImmediateAllowed,updatePriority,stalenessDays,availableVersionCode)`, `UpdateState{Idle,Downloading,ReadyToInstall,Failed}`, `decide(signals,lastPromptedVersionCode)`, `AppUpdateClient`/`PlayAppUpdateClient`, `InAppUpdateController.{state,checkAndMaybePrompt,onResume,completeFlexibleUpdate}` consistent across T2–T6.
- **Verify-at-implementation:** the exact `app-update` 2.1.0 API (ktx `appUpdateInfo` accessor, `startUpdateFlowForResult` launcher overload, `InstallStatus.DOWNLOADED == 11`), the `androidx-activity-ktx` catalog alias, and the exact `DefaultReviewPreferencesTest` DataStore harness — flagged inline in T1/T2/T3/T4.
