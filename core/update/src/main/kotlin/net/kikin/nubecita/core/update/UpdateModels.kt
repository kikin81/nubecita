package net.kikin.nubecita.core.update

/** What the policy decided to do for the current update check. */
enum class UpdateAction { None, Flexible, Immediate }

/**
 * The subset of Play's `AppUpdateInfo` the pure policy needs. Mirrors the wire
 * fields without importing the Play SDK (mapped in [PlayAppUpdateClient]).
 *
 * @property availability one of [UpdateAvailability] values.
 * @property isFlexibleAllowed whether Play reports the FLEXIBLE update flow is allowed for this update.
 * @property isImmediateAllowed whether Play reports the IMMEDIATE update flow is allowed for this update.
 * @property updatePriority the release's update priority (0–5), set per-release via the Play Developer
 *   API; absent in Internal App Sharing (treated as 0).
 * @property stalenessDays days since the update became available on Play, or null if Play has not
 *   reported it.
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

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : UpdateState

    data object ReadyToInstall : UpdateState

    data object Failed : UpdateState
}
