package net.kikin.nubecita.core.videoupload.internal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The scratch directory lives under `cacheDir`, so it is absent on a fresh
 * install and the OS may reclaim it at any time. The muxer opens its output
 * with `FileOutputStream`, which does not create missing parents — so a
 * missing directory is not a cosmetic detail, it is the difference between
 * video upload working and failing for every user (nubecita-3ug0).
 */
class ScratchDirectoryTest {
    @Test
    fun `creates the directory when it is missing`(
        @TempDir root: File,
    ) {
        val dir = File(root, "video-upload")
        assertFalse(dir.exists(), "precondition: the fresh-install state")

        assertTrue(ensureDirectory(dir))
        assertTrue(dir.isDirectory)
    }

    /** Nested parents too — cacheDir itself can be gone, not just the leaf. */
    @Test
    fun `creates missing parent directories`(
        @TempDir root: File,
    ) {
        val dir = File(root, "cache/video-upload")

        assertTrue(ensureDirectory(dir))
        assertTrue(dir.isDirectory)
    }

    /** Idempotent: the common case is that it already exists. */
    @Test
    fun `succeeds when the directory already exists`(
        @TempDir root: File,
    ) {
        val dir = File(root, "video-upload").apply { mkdirs() }

        assertTrue(ensureDirectory(dir))
    }

    /**
     * A regular file where the directory should be cannot be fixed by
     * `mkdirs()`, and reporting success would send the muxer at a path it
     * cannot open — the same ENOENT-class failure this guards against.
     */
    @Test
    fun `reports failure when the path is a file`(
        @TempDir root: File,
    ) {
        val clash = File(root, "video-upload").apply { writeText("not a directory") }

        assertFalse(ensureDirectory(clash))
    }
}
