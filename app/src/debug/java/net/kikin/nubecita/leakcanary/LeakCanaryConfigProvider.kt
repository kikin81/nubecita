package net.kikin.nubecita.leakcanary

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import net.kikin.nubecita.core.common.mvi.ClearedViewModelObserver

/**
 * Debug-only LeakCanary tuning, installed via a no-op [ContentProvider] so it
 * runs before `Application.onCreate` without touching `NubecitaApplication`.
 *
 * `retainedVisibleThreshold = 1` makes LeakCanary dump the heap as soon as a
 * single watched object is retained while the app is visible (the default is
 * 5). A short automated navigation journey rarely accumulates five retained
 * objects, so the default would let a real leak pass without a dump.
 *
 * It also wires [ClearedViewModelObserver] so screen ViewModels living in
 * Navigation 3 entry stores are watched once their entry is popped.
 */
internal class LeakCanaryConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        LeakCanary.config = LeakCanary.config.copy(retainedVisibleThreshold = 1)
        // LeakCanary doesn't see the per-entry ViewModelStores Navigation 3
        // creates, so watch every cleared screen ViewModel explicitly.
        ClearedViewModelObserver.onCleared = { viewModel ->
            AppWatcher.objectWatcher.expectWeaklyReachable(
                viewModel,
                "${viewModel::class.java.name} received ViewModel#onCleared() callback",
            )
        }
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
