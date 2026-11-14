package net.kikin.nubecita.wear.bench

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.wear.MainActivity
import net.kikin.nubecita.wear.data.sync.DmSyncObserver

/**
 * Bench only: applies the scenario named in [MainActivity]'s launch intent
 * before the activity is created, so the first frame already shows it.
 *
 * A provider is the one hook that runs in the bench build without touching
 * `src/main`: the production build has no scenario code at all.
 */
internal class BenchScenarioProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(ScenarioFromLaunch(app))
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BenchSurfacesEntryPoint {
    fun dmSyncObservers(): Set<@JvmSuppressWildcards DmSyncObserver>
}

private class ScenarioFromLaunch(
    private val app: Application,
) : Application.ActivityLifecycleCallbacks {
    override fun onActivityPreCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        // A recreated activity keeps the scenario it was launched with, and a
        // launch without the extra (a widget or complication tap) keeps the
        // current one.
        if (activity !is MainActivity || savedInstanceState != null) return
        val wire = activity.intent?.getStringExtra(BenchScenario.EXTRA) ?: return
        BenchScenarios.current.value = BenchScenario.of(wire)
        // The launcher widget and the complication render from the same fakes.
        EntryPointAccessors
            .fromApplication(app, BenchSurfacesEntryPoint::class.java)
            .dmSyncObservers()
            .forEach { it.onDmsChanged() }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
