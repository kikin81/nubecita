package net.kikin.nubecita.wear.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Image files for synced feeds. Each snapshot writes into its own
 * *generation* directory (the snapshot's timestamp), so the UI keeps rendering
 * the previous generation's files until the database switches over; only
 * then are older generations pruned.
 */
interface WatchImageStore {
    /** Writes [bytes] and returns the file's absolute path. */
    suspend fun write(
        accountDid: String,
        feedKey: String,
        generation: Long,
        key: String,
        bytes: ByteArray,
    ): String

    /** Deletes [feedKey]'s generations other than [keepGeneration] (null deletes them all). */
    suspend fun pruneFeed(
        accountDid: String,
        feedKey: String,
        keepGeneration: Long?,
    )

    /** Deletes every account's images but [accountDid]'s (null deletes everything). */
    suspend fun keepOnlyAccount(accountDid: String?)
}

internal class FileWatchImageStore(
    private val root: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WatchImageStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.filesDir, ROOT_DIR))

    override suspend fun write(
        accountDid: String,
        feedKey: String,
        generation: Long,
        key: String,
        bytes: ByteArray,
    ): String =
        withContext(dispatcher) {
            val dir = File(feedDir(accountDid, feedKey), generation.toString()).apply { mkdirs() }
            File(dir, "${safe(key)}.webp").apply { writeBytes(bytes) }.absolutePath
        }

    override suspend fun pruneFeed(
        accountDid: String,
        feedKey: String,
        keepGeneration: Long?,
    ) {
        withContext(dispatcher) {
            feedDir(accountDid, feedKey)
                .listFiles()
                ?.filter { it.name != keepGeneration?.toString() }
                ?.forEach { it.deleteRecursively() }
        }
    }

    override suspend fun keepOnlyAccount(accountDid: String?) {
        withContext(dispatcher) {
            val keep = accountDid?.let(::safe)
            root.listFiles()?.filter { it.name != keep }?.forEach { it.deleteRecursively() }
        }
    }

    private fun feedDir(
        accountDid: String,
        feedKey: String,
    ): File = File(File(root, safe(accountDid)), safe(feedKey))

    private companion object {
        const val ROOT_DIR = "wear_images"
        val UNSAFE = Regex("[^A-Za-z0-9._-]")

        fun safe(segment: String): String = segment.replace(UNSAFE, "_")
    }
}
