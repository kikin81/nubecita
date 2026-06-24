package net.kikin.nubecita.core.update

/**
 * Persistence seam for the version-scoped FLEXIBLE prompt throttle, owned by
 * `:core:update` (its own DataStore — the capability is self-contained, like
 * `:core:review` owns its eligibility counters). The real implementation is
 * [DefaultUpdatePreferences].
 */
internal interface UpdatePreferences {
    /** The versionCode we last launched a FLEXIBLE prompt for, or `null`; a read failure surfaces `null`. */
    suspend fun lastPromptedVersionCode(): Int?

    /** Records that a FLEXIBLE prompt was launched for [versionCode] (called at fire time). */
    suspend fun setLastPromptedVersionCode(versionCode: Int)
}
