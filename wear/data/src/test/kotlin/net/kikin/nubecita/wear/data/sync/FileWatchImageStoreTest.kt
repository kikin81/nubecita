package net.kikin.nubecita.wear.data.sync

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileWatchImageStoreTest {
    @TempDir
    lateinit var tmp: File

    @Test
    fun a_dot_dot_segment_stays_inside_the_root() =
        runTest {
            val root = File(tmp, "root")
            val store = FileWatchImageStore(root, StandardTestDispatcher(testScheduler))

            val path = store.write(accountDid = "..", feedKey = "..", generation = 1, key = "../escape", bytes = byteArrayOf(1))

            assertTrue(File(path).canonicalPath.startsWith(root.canonicalPath)) { path }
            assertFalse(File(tmp, "escape.webp").exists())
        }

    @Test
    fun pruning_keeps_only_the_given_generation() =
        runTest {
            val root = File(tmp, "root")
            val store = FileWatchImageStore(root, StandardTestDispatcher(testScheduler))
            val old = store.write("did:plc:a", "following", 1, "a0", byteArrayOf(1))
            val new = store.write("did:plc:a", "following", 2, "a0", byteArrayOf(2))

            store.pruneFeed("did:plc:a", "following", keepGeneration = 2)

            assertFalse(File(old).exists())
            assertEquals(2, File(new).readBytes().single().toInt())
        }

    @Test
    fun keeping_one_account_deletes_the_others() =
        runTest {
            val root = File(tmp, "root")
            val store = FileWatchImageStore(root, StandardTestDispatcher(testScheduler))
            val alice = store.write("did:plc:alice", "following", 1, "a0", byteArrayOf(1))
            val bob = store.write("did:plc:bob", "following", 1, "a0", byteArrayOf(1))

            store.keepOnlyAccount("did:plc:bob")

            assertFalse(File(alice).exists())
            assertTrue(File(bob).exists())
        }
}
