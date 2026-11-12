package net.kikin.nubecita.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifies the harness's own Preferences file (nubecita-zh7p).
 *
 * Deliberately NOT `@AuthTelemetryDataStore`: the harness stashes a live
 * access token, and that must not land in a file production code reads or
 * writes. This file exists only in a build that passed `-PauthTestHarness`,
 * so on any other build there is nothing to clean up.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AuthHarnessDataStore

/**
 * Holds one stashed access token between the two steps of
 * [AuthTestHarness.Scenario.StashAccessToken] and
 * [AuthTestHarness.Scenario.ExpiredAccessToken].
 *
 * The token is stored in the clear. That is acceptable only because this file
 * is unreachable from a shippable build: the source set is flag-gated, and the
 * flag is refused on any machine holding release signing material. It is still
 * a live credential until its `exp` passes, which is the whole point — so a
 * harness run leaves it on disk for exactly as long as the wait takes, and
 * [clear] runs as soon as it has been applied.
 */
internal class AuthHarnessStore
    @Inject
    constructor(
        @param:AuthHarnessDataStore private val dataStore: DataStore<Preferences>,
    ) {
        suspend fun stash(
            accessToken: String,
            expEpochSeconds: Long,
        ) {
            dataStore.edit {
                it[KEY_STASHED_ACCESS_TOKEN] = accessToken
                it[KEY_STASHED_EXP] = expEpochSeconds
            }
        }

        suspend fun stashed(): StashedAccessToken? {
            val prefs = dataStore.data.firstOrNull() ?: return null
            val token = prefs[KEY_STASHED_ACCESS_TOKEN] ?: return null
            val exp = prefs[KEY_STASHED_EXP] ?: return null
            return StashedAccessToken(accessToken = token, expEpochSeconds = exp)
        }

        suspend fun clear() {
            dataStore.edit {
                it.remove(KEY_STASHED_ACCESS_TOKEN)
                it.remove(KEY_STASHED_EXP)
            }
        }

        private companion object {
            val KEY_STASHED_ACCESS_TOKEN = stringPreferencesKey("stashed_access_token")
            val KEY_STASHED_EXP = longPreferencesKey("stashed_access_token_exp")
        }
    }

internal data class StashedAccessToken(
    val accessToken: String,
    val expEpochSeconds: Long,
)

@Module
@InstallIn(SingletonComponent::class)
internal object AuthHarnessStoreModule {
    private const val HARNESS_PREFS_NAME = "auth_harness"

    @Provides
    @Singleton
    @AuthHarnessDataStore
    fun provideAuthHarnessDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(HARNESS_PREFS_NAME) },
        )
}
